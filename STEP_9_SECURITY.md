# MAX Video Player — Step 9 Security and Privacy

## Scope

Step 9 adds Private Vault, vault/app authentication, privacy-safe playback restrictions, settings redaction/backup policy, and accessibility privacy. It does not attempt Step-10 physical/OEM certification and does not claim that any software vault is immune to a fully compromised/rooted device.

## Threat model

The Step-9 design protects private media against casual filesystem browsing, ordinary app/library indexing, accidental notification/MediaSession disclosure, ordinary screenshots/recents capture where Android `FLAG_SECURE` is honored, ordinary Cast/external-display/PiP leakage, settings export leakage, backup leakage, and access while the vault session is locked.

Out of scope for Step 9 are a rooted/compromised OS, malicious kernel/hypervisor, memory forensics against a running unlocked process, physical side channels, OEM-specific capture bypasses, and hardware-backed attestation. Those boundaries are not described as solved.

## Vault format and cryptography

Private media is stored as an original project-owned versioned container using the `maxvault://<opaque-id>` identity. The current container magic is `MAXVLT01` and the media payload is split into bounded 1 MiB plaintext chunks.

Security properties:

- AES-256-GCM authenticated encryption through standard Java/Android JCA.
- A random per-vault master secret is protected by a credential-derived authenticated envelope.
- Every media file receives a random per-file content key.
- Sensitive metadata is authenticated/encrypted; the Room index does not store original title, filename, source path, or plaintext private URI material beyond the opaque vault identity/location required to find the encrypted container.
- Each media chunk uses an authenticated nonce/AAD construction bound to the file and chunk position. Chunk indices are `Long`-safe and bounded to the nonce construction's unsigned 32-bit index space.
- Random access decrypts only the chunks required for the requested range. Playback never decrypts the whole movie into RAM or a plaintext temporary video file.
- GCM authentication failures, truncation, malformed versions and corrupted requested chunks fail closed rather than returning unauthenticated plaintext.
- Secret byte arrays are zeroed on a best-effort basis when the owning operation/session releases them.

The public container header intentionally contains only format/geometry information required to locate authenticated encrypted regions. Original media names/titles are not stored there in plaintext.

## Credential envelope

`PrivateVaultAuthenticator` never stores a PIN or passphrase. A PIN must contain at least six digits; a non-numeric passphrase must contain at least eight characters. The credential derives a 256-bit key using PBKDF2-HMAC-SHA256 with a fresh random salt and 310,000 production iterations. That key wraps the random vault master secret with AES-GCM.

Credential changes do not re-encrypt every media file. The currently authenticated master secret is rewrapped. Step-9 hardening constructs and authenticates the replacement envelope in memory first, compares the recovered master secret in constant time, and only then commits the replacement preference envelope. A failed replacement therefore does not intentionally destroy the previous valid envelope.

Repeated wrong credentials use a bounded monotonic in-process retry delay. Failure does not erase the vault.

## Biometric convenience unlock

Biometric unlock is optional and is separate from the PIN/passphrase recovery path. On supported Android versions, a non-exported Android Keystore AES key can wrap the same vault master secret and require biometric authentication for use. If the biometric key is unavailable or permanently invalidated, the biometric wrapper is disabled/treated as unavailable while the credential envelope remains the recovery path.

`android.permission.USE_BIOMETRIC` is declared because the platform `BiometricPrompt` path requires it. No accessibility-service, device-admin, exact-alarm or all-files permission is added by Step 9.

Physical biometric prompt behavior, OEM enrollment/invalidation behavior and hardware security-level behavior are **NOT VERIFIED — DEFERRED TO STEP 10**.

## Authoritative lock state

`PrivateVaultSession` is the single authority for the in-process vault state: `UNCONFIGURED`, `LOCKED`, `UNLOCKING`, `UNLOCKED`, or `ERROR`. The master secret exists in process memory only while legitimately unlocked and is zeroed on lock on a best-effort basis.

New `maxvault://` DataSource opens authenticate the session before resolving an opaque vault ID, preventing the locked path from being used as a simple existence oracle.

When the vault locks during private playback, `PlaybackService` pauses the player, clears the private media item/session metadata, and publishes non-private state. The lock screen replaces the application content rather than remaining as an overlay over an accessible private list.

## App Lock

Whole-application App Lock is optional and distinct from mandatory vault locking. It can reuse the vault credential and supports immediate, 30-second, one-minute and five-minute background timeouts. Timeout decisions use `SystemClock.elapsedRealtime`, not wall-clock time.

App background/foreground lifecycle controls the lock decision outside Compose navigation state. Configuration changes are not treated as a real background event for the mandatory private-vault lock path.

## Import, copy and move safety

Private Vault storage uses `noBackupFilesDir/private_vault`, a `.nomedia` marker and opaque UUID-based container names. Imports stream from the source into an opaque `.partial` container and are committed only after successful encrypted output/verification.

Copy semantics leave the original source untouched. Move semantics are deliberately conservative:

1. encrypt source to a partial container;
2. verify by decrypting through the production container path and comparing the source/content hash;
3. commit the encrypted container and private database row;
4. request deletion of the original;
5. if source deletion fails, report `Encrypted copy created — original remains` rather than claiming a successful move.

Database/commit failure removes the just-created vault artifact where possible. Startup recovery removes incomplete partials/orphans rather than presenting them as valid private media.

Real storage-exhaustion and process-kill behavior on physical storage is **NOT VERIFIED — DEFERRED TO STEP 10**; automated tests cover bounded failure/recovery contracts and no-valid-looking-partial policy where feasible.

## Private playback privacy

Private media still uses the existing single `PlaybackService -> MediaSession -> Media3PlaybackEngine -> ExoPlayer` architecture. `EncryptedVaultDataSource` is inserted as another source type; there is no second private player.

While private media is active:

- the MediaSession-facing title is generic `Private media`;
- original filename/title/path/artwork is not intentionally published to notification/lock-screen controls;
- Cast resolution rejects `maxvault://` before relay/direct-cast selection;
- PiP entry is blocked only for private media;
- active external `Presentation` output is returned to the phone before private playback;
- normal public-media Cast, PiP and external-display behavior remains unchanged;
- private items are rejected by normal library file-action paths;
- private history is stored with generic metadata and opaque identity.

Embedded audio/subtitle tracks remain in the encrypted media and use the existing Media3 timeline. Step 9 intentionally does not attach an ordinary external-audio sidecar to private media, preventing a private association from silently becoming a public-storage dependency.

## Screen capture and recents

When the configured protection setting is enabled and a private vault surface/private playback is visible, `MainActivity` applies Android `FLAG_SECURE`. It removes the flag again when the protected surface is no longer active so ordinary media is not globally capture-disabled.

Instrumentation verifies the intended window flag. Actual screenshot, screen-recorder and recents-thumbnail behavior across OEM builds is **NOT VERIFIED — DEFERRED TO STEP 10**.

## Settings and diagnostics

The Step-9 settings JSON contains only explicitly supported non-sensitive fields. The parser is versioned, bounds the file to 256 KiB, ignores bounded unknown future fields, validates known values and applies only a fully parsed `Ready` object. Ordinary settings reset does not erase Private Vault media, cloud accounts, network credentials, history or playlists.

`SecurityRedactor` removes/obscures common credential-bearing headers, cookies, token/password keys, URL user-info and signed query material before Step-9 diagnostic text is exposed. Settings tests use sentinel secrets to prove export does not contain private-vault credential stores.

## Backup boundary

Private encrypted media is stored below `noBackupFilesDir`. Backup/data-extraction rules exclude the private-vault authentication and biometric preference files and retain the Step-7/8 credential exclusions. Step 9 does not copy keystore material into a generic backup payload.

## Accessibility privacy

When App Lock is active, the private content tree is not composed underneath the lock screen. Automated Compose instrumentation checks the generic lock screen is visible and a sentinel private filename is absent from the merged/unmerged semantics tree. Large-text and keyboard/D-pad checks reuse production Compose/TV input policies.

System caption integration reads Android `CaptioningManager` preferences only when the user enables the option, maps supported foreground/background/window/edge/font-scale values into the existing Media3 subtitle renderer state, and restores the user's MAX custom subtitle style when disabled. Unsupported platform caption properties are not invented.

Media3 audio tracks are labeled `Audio description` only when role metadata contains `C.ROLE_FLAG_DESCRIBES_VIDEO`; descriptive tracks are not auto-selected merely because of that role.

## Android component/permission audit

Step 9 preserves the existing exported launcher/deep-link behavior, OAuth redirect, Android TV launcher and `PlaybackService` MediaSession integration. It does not request `MANAGE_EXTERNAL_STORAGE`, exact alarm, device-admin or accessibility-service privileges. Cleartext network allowance remains because Step 7 deliberately supports explicitly acknowledged HTTP/FTP/WebDAV behavior; Step 9 does not globally disable legitimate earlier protocol support under a privacy-hardening label.

## No-sacrifice security check

Step 9 does not obtain security by deleting earlier CI lanes or globally disabling playback features. It does not store the PIN, remove GCM authentication, use a whole-media plaintext `ByteArray`, convert media offsets to `Int`, decrypt an entire movie to a temporary plaintext file, use destructive Room migration, or mark physical/OEM behavior as tested when it is not.

## Step-10 security/physical deferrals

**NOT VERIFIED — DEFERRED TO STEP 10**:

- physical biometric enrollment/authentication and OEM invalidation behavior;
- actual OEM screenshot/screen-recording/recents behavior;
- sustained real 3 GB+ vault import and low-storage exhaustion behavior;
- real 4K/HDR/private playback and seek performance;
- long-duration, thermal and battery behavior;
- physical SD/USB/removable-storage interactions;
- physical Chromecast/receiver behavior;
- real Android TV remote/focus behavior;
- HDMI/Miracast/desktop/external-display behavior;
- aggressive OEM process killing/background restrictions.

These deferrals are intentional Step-10 work and are not hidden under a Step-9 software PASS.
