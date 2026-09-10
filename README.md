# MAX Video Player

MAX Video Player is an original, native Android media-player project growing toward professional media-player feature depth, reliability and usability through a clean-room implementation.

Commercial players may be used only as behavioral inspiration. This repository does not copy proprietary source/decompiled code, binaries, decoder implementations, artwork, branding, package names, certificates, credentials or protected assets.

## Current development status

**Step 9 of 10 — Private Media · Security · Advanced Settings · Sleep Timer · Accessibility**

PR #22 implements the Step-9 software/emulator scope on top of the certified Step-8 `main` baseline. Final Step-9 PASS is intentionally withheld until the exact documentation-complete PR head is green, the PR is merged, and the exact resulting `main` head is green. See `STEP_9_COMPLETION_REPORT.md` for the authoritative certification record.

Physical/OEM/real-hardware cases remain **NOT VERIFIED — DEFERRED TO STEP 10**.

## Platform baseline

- Kotlin / Jetpack Compose / AndroidX
- Media3 / ExoPlayer 1.11.0
- MediaSession + MediaSessionService
- Room 2.8.4, schema version 8
- Coroutines + Flow / StateFlow
- minSdk 23
- targetSdk 36
- compileSdk 36
- Java 17
- Android Gradle Plugin 9.4.0
- Gradle 9.6.0

This is a native Android application. It does not use WebView, Capacitor, Cordova, React Native, Flutter or a TWA as its application architecture.

# Step 9

## Private Vault

Private media is not merely hidden from the public library. It is stored in app-private no-backup storage as an original versioned encrypted container and accessed through opaque `maxvault://<vault-id>` identities.

The current vault format uses AES-256-GCM authenticated encryption, a random master secret, random per-file content keys, encrypted/authenticated sensitive metadata, and bounded 1 MiB encrypted media chunks. Random access decrypts only the chunks needed for the requested Media3 range. It does not decrypt the full movie into RAM or a whole plaintext temporary video file.

Room v8 adds `private_media` through explicit non-destructive `MIGRATION_7_8`. Its index stores opaque vault identity/location, sizes, timestamps, version/status data—not original plaintext private filenames/titles/source paths.

Imports use an opaque partial-file transaction. Copy leaves the source intact. Move completes encryption and verification before committing/deleting the source; if source deletion fails the UI/domain result says the encrypted copy exists and the original still remains.

## Authentication and App Lock

`PrivateVaultSession` is the single in-process authority for locked/unlocked vault state. PINs/passphrases are never stored. A credential-derived key (PBKDF2-HMAC-SHA256 with a fresh random salt and 310,000 production iterations) protects the random vault master secret with AES-GCM.

Changing the credential rewraps the same master secret rather than re-encrypting all private media. The replacement envelope is authenticated and compared against the current master before atomic persistence.

Optional biometric unlock uses Android Keystore plus platform `BiometricPrompt`; PIN/passphrase remains the recovery route. Whole-app App Lock is separately optional and supports immediate/timed background locking using a monotonic clock.

## Private playback and output policy

Private media still uses the existing single player:

```text
Compose / PlayerViewModel
        ↓
PlaybackConnection
        ↓
PlaybackService : MediaSessionService
        ↓
MediaSession
        ↓
Media3PlaybackEngine / ExoPlayer
        ↓
ProfessionalMediaSourceFactory
        ↓
EncryptedVaultDataSource for maxvault:// media
```

There is no Activity-owned or second private ExoPlayer. A redistribution-safe H.264/AAC fixture is encrypted during instrumentation and played through this production path.

While private media is active:

- MediaSession/notification-facing title is generic `Private media`;
- locking the vault pauses and clears private playback/session metadata;
- `maxvault://` is rejected by Cast before direct/relay resolution;
- PiP is blocked only for private media;
- external Presentation output is returned to the phone before private playback;
- normal public-media Cast, PiP and external-display behavior remains available;
- protected private screens apply Android `FLAG_SECURE` and ordinary screens can restore normal capture policy.

Actual OEM screenshot/recents behavior and physical receiver/display behavior remain Step-10 certification.

## Advanced Settings

Step 9 adds a typed settings layer for:

- App Lock and auto-lock timeout;
- biometric convenience unlock;
- private-screen capture protection;
- Reduce Motion;
- high-contrast controls;
- Android system caption styling;
- sleep-timer fade duration.

Settings export contains supported non-sensitive values only. Import is versioned, validated and capped at 256 KiB. Unknown bounded future fields do not become secrets or arbitrary state. Ordinary reset restores non-sensitive defaults without deleting the Private Vault, cloud/network credentials, history or playlists.

`SecurityRedactor` masks common authorization/token/password/cookie/signed-query material before Step-9 diagnostic/export text is exposed.

## Sleep Timer

The sleep timer is owned by `PlaybackService`, not an Activity. It supports fixed/custom duration, end of current media and end of queue. Duration uses `SystemClock.elapsedRealtime`; wall-clock changes do not move the deadline.

Optional fade changes only ExoPlayer/player output volume and restores the prior player volume on cancel/replace. Timer expiry pauses playback. Android system media volume is not rewritten by this feature.

## Accessibility

Step 9 adds/retains large-text-safe scrollable surfaces, approximately 48 dp minimum targets on new critical controls, meaningful state semantics, deterministic keyboard/D-pad mapping through the retained TV input layer, high-contrast theme mode, scoped Reduce Motion behavior, and locked-screen semantics that do not compose private titles underneath.

When enabled, Android system caption styling reads supported `CaptioningManager` foreground/background/window/edge/font-scale preferences into the existing subtitle renderer state; disabling it restores the user's MAX custom subtitle style. Custom subtitles are not removed.

Media3 audio tracks are labeled `Audio description` only when role metadata contains the descriptive-video role flag. The app does not invent this label and does not auto-select the track merely because it is descriptive.

# Steps 1–8 preserved

Step 9 extends rather than replaces the previously certified architecture.

## Step 8 — Cloud, Cast, USB/OTG, Android TV and external output

Cloud-provider integration, Cast/relay behavior, removable storage, Android TV navigation, external displays, signed adaptive relay behavior and decoder regression hardening remain in the existing Step-8 implementation/certification files. Step 9 applies private-content restrictions narrowly rather than disabling these systems globally.

## Step 7 — Professional network playback

HTTP/HTTPS progressive, HLS, DASH, RTSP, SMB2/3, WebDAV, FTP and explicit FTPS continue through `NetworkRepository`/protocol clients and `NetworkDataSourceRouter` into the same Media3 source factory/player. Credentials remain encrypted/scoped and diagnostics sanitized. The isolated Samba/FTP/FTPS/authenticated-RTSP certification lane remains required.

## Step 6 — Decoder engine

Auto, Hardware, Enhanced Hardware and Software decoder policy, actual Media3 codec identity/diagnostics, per-media persistence, controlled fallback and device capability inventory remain intact. Decoder switching reuses the same ExoPlayer and preserves queue/index/position/repeat/shuffle/speed/pitch/track selections. Step-6 decoder/coexistence tests remain retained gates.

## Step 5 — Professional audio

Embedded/external audio selection, 10-band EQ, preamp, boost/limiter, balance/channel control, delay/route compensation, pitch, audio-only/background behavior and the project-owned `MaxAudioProcessor` remain on the existing `DefaultAudioSink` path. Step 9 adds descriptive-track labeling without replacing audio processing.

## Step 4 — Subtitles

Embedded/external subtitle selection, SRT/WebVTT/SSA/ASS/TTML handling, encoding, sidecar discovery, appearance and per-media timing remain intact. Step 9 adds optional system-caption-style bridging without deleting MAX custom style.

## Step 3 — Player experience

Controls, seeking, gestures, zoom/pan, aspect/resize/rotation/orientation/fullscreen, player control lock, speed, queue, repeat/shuffle and public-media PiP remain intact. Seek-preview thumbnails remain limited by their earlier roadmap status; Step 9 does not fabricate them.

## Step 2 — Library

Videos/folders/Continue Watching/Recent/History/Favourites/Playlists, search/sort/filter, MediaStore/SAF, relink, rename/delete and bounded thumbnails remain intact. Private media is separated from normal file-action handling rather than disguised as an ordinary public library item.

## Step 1 — Foundations

Service-owned playback, MediaSession, resume/history, URI/reference-based media, long-safe sizes/timing and device-capability foundations remain authoritative.

# Security, backup and large-media policy

Step 9 does not request broad all-files storage, exact alarm, device-admin or accessibility-service privileges. `USE_BIOMETRIC` is declared only for the optional platform biometric flow. The existing Step-7 cleartext-network allowance is preserved because intentionally acknowledged HTTP/FTP/WebDAV behavior must not be broken by unrelated private-vault hardening.

Private encrypted containers live below no-backup storage. Backup/data-extraction rules exclude private authentication/biometric preference files and retain cloud/network credential exclusions.

Media/file positions and sizes remain `Long`; only bounded chunk buffers use `Int`. CI proves 2 GiB/3.2+ GiB geometry synthetically and exercises a generated 64 MiB streaming encrypted fixture. Sustained real 3 GB+ vault import, real 4K/HDR, low-storage, thermal and long-duration behavior are Step-10 physical tests.

# Step-9 certification

The dedicated `.github/workflows/step9-certification.yml` has two gates:

- `step9-security-unit` — exact checkout assertion, debug build, full JVM tests, release build and lint;
- `step9-emulator-certification` — exact checkout assertion, complete API-35 connected suite, then explicitly named Step-9 integration classes through a script that rejects failures and zero-test execution.

The Step-9 workflow explicitly checks out the PR's literal head SHA for pull-request runs and verifies `git rev-parse HEAD` before executing the gates. A green synthetic PR merge ref alone is not accepted as the Step-9 exact-head proof.

Retained Android CI/Step-8 workflows continue to provide API-26/API-28 thumbnail regression, API-35 integration, Step-7 real protocol servers and Step-8/Step-6 regressions.

See:

- `STEP_9_COMPLETION_REPORT.md`
- `STEP_9_SECURITY.md`
- `STEP_9_TEST_MATRIX.md`
- `STEP_8_COMPLETION_REPORT.md`
- `STEP_8_SECURITY.md`
- `STEP_8_TEST_MATRIX.md`
- `STEP_7_COMPLETION_REPORT.md`
- `STEP_7_FINAL_CERTIFICATION.md`
- `STEP_7_PROTOCOL_SECURITY.md`
- `STEP_7_TEST_MATRIX.md`
- `STEP_6_FINAL_CERTIFICATION.md`
- earlier step completion/architecture/test documents
- `LARGE_MEDIA_AUDIT.md`

# Step-10 boundary

The following remain **NOT VERIFIED — DEFERRED TO STEP 10**:

- physical biometric/OEM prompt and invalidation behavior;
- OEM screenshot/screen-recording/recents behavior;
- sustained real 3 GB+ private imports and actual low-storage exhaustion;
- real 4K/HDR/high-bitrate private playback/seek;
- long playback, battery and thermal behavior;
- physical SD/USB/removable storage;
- physical Chromecast/receiver behavior;
- real Android TV remote/focus behavior;
- HDMI/Miracast/desktop/external-display behavior;
- aggressive vendor process killing/background restrictions.

Do not start Step 10 from this branch. After Step 9 passes, stop for independent review.

## Contribution principle

Do not solve difficult architectural problems by deleting requirements. Preserve working behavior, implement independently, retain user data through schema changes, keep private/security status truthful, and never fabricate verification results.
