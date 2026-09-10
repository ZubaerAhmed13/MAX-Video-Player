# MAX Video Player — Architecture through Step 9

## Clean-room boundary

MAX Video Player is an original native Android implementation. Other commercial media players are used only as behavioral/workflow references. No proprietary source/decompiled code, decoder/DSP implementations, protected assets, branding, package names, certificates, credentials or private APIs are reused.

# Authoritative playback ownership

Step 9 preserves the single service-owned playback graph established by the earlier steps:

```text
Compose UI / PlayerViewModel / feature controllers
        ↓
PlaybackConnection (MediaController client)
        ↓
PlaybackService : MediaSessionService
        ↓
MediaSession
        ↓
Media3PlaybackEngine
        ↓
ExoPlayer
        ↓
ProfessionalMediaSourceFactory
        ├─ local/content/file
        ├─ Step-7 network sources via NetworkDataSourceRouter
        ├─ Step-8 cloud/cast-related source composition
        └─ Step-9 maxvault:// via EncryptedVaultDataSource
        ↓
ProfessionalRenderersFactory
        ├─ video → decoder policy / Android MediaCodec
        └─ audio → DefaultAudioSink + MaxAudioProcessor
```

Private media does not create an Activity-owned player, a second ExoPlayer, a synchronized shadow player or a decrypt-before-playback engine. Embedded audio/subtitle tracks and decoder policy remain on the same Media3 timeline.

# Logical layers through Step 9

- `core.model` — stable media/domain/playback state, source types and decoder mode catalog.
- `core.database` — Room entities/DAOs and explicit migrations through schema v8.
- `core.media` — MediaStore, SAF, metadata and URI availability.
- `core.device` — runtime device/decoder capability inventory.
- `playback.engine` — Media3 engine, renderer/decoder policy, source composition and custom audio sink.
- `playback.session` — PlaybackService, MediaSession, controller connection, queue and service-owned sleep timer.
- `feature.library` — public library/index/queue/thumbnail/file actions; private media is excluded from ordinary mutation routes.
- `feature.player` — player controls, gestures, display/orientation/PiP, decoder UI and diagnostics.
- `feature.subtitle` — embedded/external subtitles, styling/timing plus Step-9 Android system-caption bridge.
- `feature.audio` — tracks, external audio, DSP/sync/routing plus Step-9 descriptive-audio labeling.
- `feature.decoder` — Step-6 decoder classification/policy/persistence/runtime diagnostics.
- `feature.network` — Step-7 HTTP/HLS/DASH/RTSP/SMB/FTP/FTPS/WebDAV sources, credential vault and diagnostics.
- Step-8 cloud/Cast/USB/TV/output layers — preserved and narrowed only for private-content restrictions.
- `feature.privatevault` — Step-9 vault domain, crypto, auth, storage, Room index, repository, DataSource and UI.
- `feature.settings` — Step-9 typed non-sensitive settings/import/export/redaction/caption bridge.
- `feature.sleeptimer` — Step-9 service-owned monotonic timer state/controller/UI.
- `ui` — application theme, high-contrast mode and scoped Reduce Motion policy.

# Step-9 Private Vault architecture

## Identity and storage

Public app/library identity for a private item is an opaque `maxvault://<UUID>` URI. The backing encrypted file uses an opaque UUID filename below `noBackupFilesDir/private_vault`; a `.nomedia` marker reduces accidental media indexing.

The Room v8 `private_media` index deliberately does not hold original title, display name or source path. It retains only the opaque identity/container location, source/encrypted lengths, import/update timestamps, format version and availability/status data needed to operate the vault.

## Versioned encrypted container

The current format is `MAXVLT01` / format version 1:

```text
Fixed public header
  - magic/version
  - chunk size
  - plaintext length (Long)
  - opaque vault UUID
  - random chunk nonce prefix
  - encrypted-region lengths/nonces
        ↓
AES-GCM encrypted sensitive metadata
        ↓
AES-GCM wrapped random per-file content key
        ↓
Authenticated encrypted media chunks
  chunk 0
  chunk 1
  ...
```

The public header exposes only format/geometry needed to find authenticated regions. Original filename/title/MIME/details are stored inside authenticated encrypted metadata.

Each media file receives a random 256-bit content key. The media payload uses 1 MiB plaintext chunks so the player can authenticate/decrypt only the chunks intersecting the requested byte range. The per-file random nonce prefix plus chunk index forms the GCM nonce; chunk-specific AAD binds the ciphertext to its logical location.

All source size, encrypted size, plaintext length, requested position, media offset and chunk geometry use `Long`. Bounded memory buffers use `Int`. Checked add/multiply/ceil-division helpers reject overflow.

## Master secret and credential envelope

`PrivateVaultSession` owns the in-memory vault master secret and publishes the authoritative state:

`UNCONFIGURED → LOCKED → UNLOCKING → UNLOCKED`, with `ERROR` for invalid state/configuration.

The credential itself is never stored. A six-or-more-digit PIN or eight-or-more-character passphrase is combined with a random salt using PBKDF2-HMAC-SHA256 (310,000 production iterations) to derive a 256-bit wrapping key. That key AES-GCM wraps the random vault master secret.

On a credential change:

```text
authenticate current credential
        ↓
copy current master secret in-process
        ↓
derive replacement wrapping key with fresh salt
        ↓
create replacement AES-GCM envelope in memory
        ↓
decrypt/authenticate replacement and constant-time compare master
        ↓
atomically commit replacement preference values
```

The media content keys remain wrapped by the same vault master secret, so changing the PIN does not require rewriting every private video.

Wrong credentials cause authenticated decryption failure and a bounded monotonic retry delay. They do not mutate media or wipe the vault.

## Optional biometric wrapper

`PrivateVaultBiometricKeyManager` uses an Android Keystore AES/GCM key requiring user authentication on supported Android versions. It provides a second wrapper around the same vault master secret. The credential envelope remains independently valid, so biometric invalidation/unavailability falls back to PIN/passphrase instead of destroying the vault.

# Vault transaction architecture

## Copy to Private

```text
SAF/file input stream
        ↓ bounded streaming
opaque .partial container
        ↓ authenticated close/verification
opaque .maxvault container
        ↓
Room private_media row
```

The original remains untouched.

## Move to Private

```text
source
  ↓ encrypt
.partial
  ↓ production decrypt/hash verification
verified encrypted container
  ↓ commit encrypted file + Room row
  ↓ request source deletion
      ├─ success → moved
      └─ failure → encrypted copy exists + ORIGINAL REMAINS
```

The app never reports a complete move when source deletion failed. Commit/database failures attempt to remove the newly created encrypted artifact.

## Cancellation and restart recovery

Import is structured as bounded background I/O. The repository captures the owning coroutine `Job`, wraps source reads with cooperative cancellation checks, checks cancellation between transaction phases, and also checks each verification chunk. A cancellation observed while encryption/verification is in progress is rethrown as `CancellationException` and the active `.partial` is removed rather than being returned as a normal success/failure result.

A hard process kill cannot execute coroutine cleanup. On restart, `recoverAbandonedTransactions()` removes `.partial` files and encrypted containers without a matching Room index row. Indexed containers are preserved. This gives cancellation and process-death two separate recovery paths without inventing a valid-looking item after interrupted work.

The dedicated import instrumentation covers copy, move, failed source deletion, insufficient-storage preflight, read/write failures, database commit rollback, observed in-progress cancellation cleanup, and abandoned partial/orphan recovery.

# Random-access playback

`EncryptedVaultDataSource` is a normal Media3 `DataSource` implementation selected by the existing `NetworkDataSourceRouter` for the `maxvault` scheme. Opening the source first requires an unlocked `PrivateVaultSession`, then resolves the opaque vault item, opens/authenticates the container and maps Media3 `DataSpec.position/length` to bounded authenticated chunk reads.

No plaintext movie file is materialized. Seeking to a new byte range decrypts only affected chunk(s). Corruption in a requested chunk is converted into a controlled authentication/source failure rather than returning unauthenticated bytes.

The production path remains:

```text
MediaSession timeline
        ↓
Media3PlaybackEngine
        ↓
ProfessionalMediaSourceFactory
        ↓
NetworkDataSourceRouter
        ↓ maxvault://
EncryptedVaultDataSource
        ↓
versioned authenticated container reader
```

# Lock lifecycle and privacy clearing

`PrivateVaultSession` is authoritative; other components do not infer lock state from navigation.

On vault lock:

- in-memory master-key material is zeroed on a best-effort basis;
- new private DataSource opens are rejected before opaque item resolution;
- private playback is paused/cleared by `PlaybackService`;
- MediaSession/notification-facing private metadata is removed/generic;
- the UI no longer composes the private-media list underneath a lock screen;
- cached private display state is discarded with the private screen/session lifecycle.

App Lock is separate from vault lock. `AppLockController` records background elapsed time and applies immediate/30-second/one-minute/five-minute policy using `SystemClock.elapsedRealtime` rather than wall time.

# Private output restrictions

Private restrictions are media-specific, not global:

- `CastSourceResolver` rejects `maxvault://` before direct/relay classification.
- `MaxApp` blocks private playback when a Cast target is active rather than relaying decrypted bytes.
- private playback returns an active external `Presentation` to the phone.
- private PiP entry is blocked.
- `FLAG_SECURE` is applied when a protected private surface/playback is visible and removed again when no protected surface is active.
- ordinary public Cast, PiP and external display remain on their Step-8 path.

`PrivateVaultRepository.toAppMedia()` itself emits generic `Private media` metadata so privacy does not rely solely on one navigation caller. Private history likewise uses generic title/opaque identity.

# Room v8 migration

Step 9 advances Room v7 → v8 with explicit `MIGRATION_7_8` creating `private_media`. No destructive migration fallback is configured. Migration instrumentation begins from the prior schema, inserts representative earlier data, applies the migration and verifies both preservation and the new private table/schema.

# Step-9 settings architecture

`SettingsRepository` is a typed SharedPreferences facade for non-sensitive global Step-9 settings. It does not own vault keys, cloud tokens or network credentials.

Settings state includes:

- App Lock enabled/timeout;
- biometric convenience-unlock preference;
- private-screen capture protection;
- Reduce Motion;
- contrast mode;
- system-caption style preference;
- sleep fade duration.

`SettingsJsonCodec` serializes only supported non-sensitive settings. Import is capped at 256 KiB, parsed into a `Ready` object before application, and has explicit malformed/unsupported-version/unknown-field behavior. `resetAllNonSensitive` changes only this settings store.

`SecurityRedactor` centralizes masking for credential-bearing headers/cookies, password/token-like fields, URL user-info and signed query values.

# Sleep Timer architecture

`SleepTimerRepository` exposes app-process state while `SleepTimerController` is attached and owned by `PlaybackService` alongside the authoritative player.

Modes:

- duration;
- end of current media;
- end of queue.

Duration deadlines use monotonic `elapsedRealtime`. Optional fade writes only `Player.volume` while the timer is inside the configured fade window; cancel/replace restores the prior player volume. Expiry restores volume, pauses playback and clears timer state. Android system media volume is not changed.

Because Activity/Compose does not own the timer or player, Activity recreation does not create a second timing/playback authority.

# Accessibility architecture

## Large text and input

Step-9 Settings, lock/vault and sleep-timer surfaces are scrollable where content may exceed the viewport and new critical actions use approximately 48 dp minimum targets. Automated Compose coverage uses a 2.0× font scale on representative settings content.

Keyboard/D-pad playback actions continue through the Step-8 `TvPlayerInputController`, preserving one semantic action path for remote/keyboard and touch-triggered playback operations.

## Screen-reader privacy

The full-screen App Lock branch replaces normal application content. The locked accessibility tree can expose generic lock text but does not leave a private list composed underneath. Tests query the unmerged tree for sentinel private metadata.

## System captions

The Step-4 subtitle repository/render path remains authoritative. `SystemCaptionStyleBridge` is additive:

```text
MAX custom subtitle style
        ↓ user enables system style
snapshot MAX style
        ↓
Android CaptioningManager user style/font scale
        ↓ map only supported fields
SubtitleStyleState
        ↓
existing Media3 SubtitleView
```

Disabling the option restores the snapshotted MAX style. Unsupported Android caption properties are not fabricated.

## Audio description

Audio tracks remain Media3 tracks. `AudioPlaybackController` recognizes `C.ROLE_FLAG_DESCRIBES_VIDEO` and appends `Audio description` to the user label only when Media3 metadata provides that role. The role does not force auto-selection.

## Contrast and motion

High contrast stays inside the project theme rather than hard-coded per-widget colors. Reduce Motion is a scoped accessibility policy for Step-9/new UI; Step 9 does not globally disable animation APIs or interfere with essential playback progress/state transitions.

# Backup and manifest boundary

Encrypted vault media is below `noBackupFilesDir`. Backup/data-extraction rules exclude private authentication/biometric preference files and preserve Step-7/8 credential exclusions.

The app declares `USE_BIOMETRIC` for the optional platform biometric flow. Step 9 does not add `MANAGE_EXTERNAL_STORAGE`, exact alarms, device-admin or accessibility-service permissions. Existing exported launcher/deep-link/OAuth behavior, TV launcher behavior and exported MediaSession service contract remain preserved. The Step-7 cleartext-network setting remains because intentionally acknowledged HTTP/FTP/WebDAV support must not be broken by Step-9 privacy work.

# Earlier architecture preserved

## Step 8

Cloud-provider state, Cast and signed relay, USB/OTG, Android TV and external-display architecture remain intact. Private content is restricted at the output boundary; public-media behavior is not globally disabled.

## Step 7

`NetworkRepository`, protocol clients, process-local request registry, `NetworkDataSourceRouter`, OkHttp, SMBJ, Commons Net and Media3 RTSP remain the professional network path for HTTP/HTTPS/HLS/DASH/RTSP/SMB/FTP/FTPS/WebDAV. Credential scoping, TLS policy, bounded XML/M3U parsing and isolated protocol certification remain unchanged.

## Step 6

Decoder policy still supplies Auto/Hardware/Enhanced Hardware/Software candidate pools to Media3/Android `MediaCodec`, tracks actual initialized backend, bounds fallback, persists per-media choices and re-prepares the same ExoPlayer while restoring queue/index/position/play state/repeat/shuffle/speed/pitch/track selections.

## Step 5

`MaxAudioProcessor` remains in the existing `DefaultAudioSink`, preserving tracks/external audio/EQ/preamp/boost/limiter/channel/balance/sync/pitch/audio-only/background behavior.

## Step 4

Embedded/external subtitles, SRT/WebVTT/SSA/ASS/TTML, encoding, sidecar discovery, appearance and per-media delay remain on the existing Media3 subtitle path.

## Steps 1–3

Service-owned playback/MediaSession, public library/SAF/history/playlists, player controls/gestures/seek/display/orientation/queue/repeat/shuffle/public PiP and long-safe URI/reference media foundations remain authoritative.

# Verification architecture

Step-9 adds `.github/workflows/step9-certification.yml`:

```text
step9-security-unit
  exact SHA checkout assertion
  assembleDebug
  testDebugUnitTest
  assembleRelease
  lintDebug

step9-emulator-certification (API 35)
  exact SHA checkout assertion
  connectedDebugAndroidTest
  install debug + androidTest APKs
  run explicit Step-9 classes
  fail on failure / missing OK / zero tests
```

The existing Android CI and Step-8 certification workflows are retained and also verify the literal PR-head checkout. They continue to cover API-26/API-28 thumbnail regressions, API-35 full instrumentation, Step-7 isolated protocol servers, Step-8 cloud/Cast/TV/output paths and Step-6 decoder regressions.

# Physical certification boundary

The following are **NOT VERIFIED — DEFERRED TO STEP 10**:

- physical biometric/OEM prompt/invalidation behavior;
- OEM screenshot/recording/recents enforcement;
- sustained real 3 GB+ private import and low-storage behavior;
- real 4K/HDR/high-bitrate private playback/seek;
- long-duration, battery and thermal behavior;
- SD/USB/removable storage on hardware;
- real Chromecast/receiver behavior;
- Android TV hardware/remote behavior;
- HDMI/Miracast/desktop/external-display behavior;
- aggressive vendor process killing/background restrictions.

Step 9 stops before this hardware phase.
