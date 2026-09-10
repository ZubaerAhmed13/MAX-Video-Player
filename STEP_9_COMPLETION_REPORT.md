# MAX Video Player — Step 9 Completion Report

## Certification state

Step 9 has completed its software/emulator implementation, exact-head pull-request certification, merge, and exact post-merge `main` certification. Physical/OEM cases listed below remain explicitly deferred to Step 10 and are not represented as verified.

- Repository: `ZubaerAhmed13/MAX-Video-Player`
- Branch: `step-9-private-security-settings-accessibility`
- Pull request: `#22` — `Step 9: Private media, security, settings, sleep timer and accessibility`
- Step-9 base `main`: `f79078372b03a115208cbb6bb71a54e51eeb260c`
- Certified PR-head SHA: `b34db37e2b3d8bf73f20fa1b4d68e41631e5db13`
- PR-head Android CI run: `34464814229` — PASS
- PR-head retained Step-8 Certification run: `34464814259` — PASS
- PR-head Step-9 Certification run: `34464814293` — PASS
- Merge commit / certified post-merge software `main` SHA: `68658a7f56724b46eed058e53dc5181f54675a36`
- Post-merge Android CI run: `34465748932` — PASS
- Post-merge retained Step-8 Certification run: `34465748898` — PASS
- Post-merge Step-9 Certification run: `34465748934` — PASS
- Documentation-complete final `main` SHA: the commit containing this report. Its literal SHA and its final workflow run IDs are recorded in the merged PR #22 certification record after this documentation-only commit itself is certified. A Git-tracked file cannot contain the SHA of the commit that contains that file without changing the commit hash again; this report therefore records the immutable merge/software SHA directly and uses the merged PR record for the self-referential final documentation SHA.

The earlier red candidate heads were deliberately not accepted. Failures found during Step-9 integration were fixed rather than waived, including biometric permission lint, instrumentation compile issues, private-playback authentication/session setup, stale playback-error correlation, and an accidental `PlaybackConnection.kt` syntax regression. Exact-head checkout assertions were added to the Step-9, Android CI, and retained Step-8 workflows so a synthetic pull-request merge ref alone cannot satisfy certification.

## Final required gates

The certified PR head and certified post-merge software `main` both passed the required software/emulator gates:

- `:app:assembleDebug` — PASS
- `:app:testDebugUnitTest` — PASS
- `:app:assembleRelease` — PASS
- `:app:lintDebug` — PASS
- `:app:connectedDebugAndroidTest` / API-35 full instrumentation — PASS
- API-26 retained thumbnail instrumentation — PASS
- API-28 retained thumbnail instrumentation — PASS
- Step-7 real SMB/FTP/explicit-FTPS/authenticated-RTSP certification — PASS
- retained Step-8 cloud/Cast/USB/TV/external-display certification — PASS
- retained decoder/coexistence regressions — PASS
- strict named-class Step-9 instrumentation — PASS

`.github/scripts/step9-certify-instrumentation.sh` runs the required Step-9 classes by exact name and rejects failures, missing success, and zero-test execution.

## Step-9 implementation

### Private Vault and encrypted storage

Step 9 adds an encrypted Private Vault using opaque `maxvault://<vault-id>` identities. Vault payloads are stored under app-private no-backup storage in versioned `MAXVLT01` containers. AES-256-GCM protects encrypted metadata and media chunks; every media file receives a random content key, and bounded 1 MiB chunk processing supports authenticated random reads/seeks without loading or decrypting the entire video into RAM.

The Room database advances from version 7 to version 8 through explicit `MIGRATION_7_8`. `private_media` stores opaque vault identity/location and non-sensitive structural fields rather than plaintext original titles, filenames, or source paths. No destructive migration fallback was introduced.

`EncryptedVaultDataSource` routes `maxvault://` through the existing `NetworkDataSourceRouter`. Locked resolution authenticates before opaque-item existence checks, reducing information disclosure while locked. All media offsets and container geometry remain `Long`-safe, including synthetic 2 GiB and 3.2+ GiB test geometry.

### Authentication and App Lock

`PrivateVaultSession` is the in-process authority for vault lock state and master-key access. PIN/passphrase authentication derives a wrapping key with PBKDF2-HMAC-SHA256, a random salt, and 310,000 production iterations. The PIN/passphrase is never persisted. Wrong attempts use bounded monotonic retry delay rather than destructive wiping.

Credential changes authenticate the current credential and re-wrap the same vault master secret using a fresh authenticated envelope, so changing the PIN/passphrase does not re-encrypt private media. The replacement envelope is decrypted and verified in memory before atomic replacement of the previous valid envelope.

Optional biometric convenience unlock uses Android Keystore plus platform `BiometricPrompt`; PIN/passphrase remains the recovery path. Biometric-key invalidation disables only the biometric wrapper and does not destroy the PIN recovery path or vault media.

Optional whole-app App Lock is separate from mandatory vault locking and supports immediate, 30-second, one-minute, and five-minute background timeouts using monotonic elapsed time.

### Import, copy, move, rollback and recovery

Vault imports stream into opaque `.partial` containers. Copy preserves the original. Move performs encrypted write, production-path decrypt/hash verification, commit, and only then requests source deletion. If source deletion fails, the result truthfully reports that the encrypted copy exists while the original remains.

Repository failure paths clean incomplete work where execution is available: source-read failure, vault-write failure, database-commit failure, and cooperative coroutine cancellation remove partial output instead of reporting success. Startup recovery handles abandoned `.partial`/orphaned work after process death, where in-process cleanup code cannot run.

Storage preflight rejects impossible imports without exposing a broken vault item. The test suite covers copy, move, denied deletion, storage preflight, read/write failure injection, DB rollback, cancellation, and partial/orphan recovery.

### Playback architecture and coexistence

Private playback reuses the single service-owned production path:

`PlaybackConnection -> PlaybackService -> MediaSession -> Media3PlaybackEngine -> ProfessionalMediaSourceFactory -> NetworkDataSourceRouter -> EncryptedVaultDataSource`

No second private ExoPlayer was introduced. Instrumentation encrypts a redistribution-safe H.264/AAC fixture with the production writer, loads it through the same playback pipeline, verifies video/audio track initialization, and exercises Activity recreation without creating a second controller/player path.

A stale error from a previous source is cleared when an explicit new media item/queue is loaded, while certification correlates playback errors with the media ID that actually owns them. This prevents a deleted earlier fixture from falsely failing a newly loaded private source without suppressing authoritative errors from the current source.

### Private playback restrictions

Private playback exposes generic session metadata (`Private media`) rather than private filenames/titles. Locking the vault pauses and clears private playback/session state and metadata.

Private `maxvault://` media is rejected by Cast source resolution before direct or relay routing. Private playback suppresses PiP and returns external Presentation output to the phone, while ordinary public-media Cast/PiP/external-display behavior remains available. `FLAG_SECURE` is applied to configured protected private surfaces and removed again outside those surfaces.

These are software policy and emulator-verified behaviors; actual OEM screenshot, screen-recording, recents, Cast receiver, HDMI/Miracast, and vendor-specific behavior remains Step-10 physical certification.

### Advanced Settings and privacy

Step 9 adds typed settings for App Lock, lock timeout, biometric convenience unlock, private-screen protection, Reduce Motion, high contrast, Android system caption styling, and sleep-fade duration.

Settings export is explicitly non-sensitive and versioned. Import is bounded to 256 KiB and validates supported fields while safely handling malformed input, unsupported versions, and bounded unknown future fields. Ordinary settings reset restores non-sensitive defaults without deleting Private Vault media/authentication, cloud/network credentials, history, or playlists.

`SecurityRedactor` centralizes masking for credential-bearing headers, cookies, token/password fields, URL user-info, and signed-query material used by Step-9 diagnostics/export surfaces.

### Sleep Timer

The production sleep timer is owned by `PlaybackService`, not an Activity or Compose timer. It supports duration, end of current media, and end of queue. Duration deadlines use `SystemClock.elapsedRealtime`. Expiry pauses playback. Optional fade changes only player output volume and restores the prior player volume on cancel/replace; Android system media volume is not modified.

### Accessibility

Step 9 adds/retains meaningful semantics, approximately 48 dp minimum targets for new critical controls, scrollable Settings/lock/timer surfaces, large-font certification at 2.0×, high-contrast mode, a scoped Reduce Motion preference, deterministic keyboard/D-pad mappings through the retained TV input policy, and protection against leaking private titles through the locked accessibility tree.

Android `CaptioningManager` foreground/background/window/edge/font-scale preferences are bridged into the existing subtitle-renderer state when system-caption styling is enabled, while disabling that option restores the user's MAX custom subtitle style.

Media3 audio tracks are labeled `Audio description` only when their role metadata includes `C.ROLE_FLAG_DESCRIBES_VIDEO`; Step 9 does not auto-select descriptive audio solely because of that label.

## Automated Step-9 coverage

JVM/unit coverage includes AES-GCM/KDF/nonce/AAD/constant-time policy, container round-trips across empty/one-byte/full/multi/partial chunks, wrong-master/tamper/swap/truncation handling, `Long` geometry at 2 GiB and 3.2+ GiB, a generated 64 MiB streaming/random-read fixture, raw encrypted-container metadata sentinel checks, private Cast rejection, settings JSON/import/export/redaction safety, and audio-description role-label policy.

API-35 Step-9 instrumentation includes:

- `PrivateVaultIntegrationTest`
- `Step9PrivatePlaybackCoexistenceInstrumentedTest`
- `Step9VaultAuthInstrumentedTest`
- `Step9VaultImportInstrumentedTest`
- `Step9DatabaseMigrationTest`
- `Step9SettingsPrivacyInstrumentedTest`
- `Step9SleepTimerInstrumentedTest`
- `Step9PrivateSurfaceInstrumentedTest`
- `Step9AccessibilityInstrumentedTest`

The dedicated workflow `.github/workflows/step9-certification.yml` contains `step9-security-unit` and `step9-emulator-certification`. The retained Android CI and Step-8 workflow lanes were not removed or weakened.

## Dependencies and architecture impact

Step 9 adds no third-party runtime cryptography dependency and no second playback engine. Vault cryptography uses standard JCA/Android primitives; biometric wrapping uses Android Keystore/platform biometric APIs; captions use Android `CaptioningManager`; persistence uses the existing Room/SharedPreferences stack; playback remains on the existing Media3 architecture.

The Step-9 changes cover CI/scripts, manifest/backup policy, application/container/activity integration, Room/model routing, Private Vault domain/auth/crypto/storage/repository/data-source/UI, Cast isolation, settings/redaction/caption bridging, sleep timer, playback service/engine integration, accessibility theme/UI work, canonical project documentation, and Step-9 JVM/instrumentation tests. No Step-1–8 test file was deleted to obtain a green result.

## Security findings resolved

1. `MediaSourceType.PRIVATE` integration was made exhaustive in legacy source-type branches and private media was rejected from normal public file actions.
2. Locked vault resolution authenticates before checking opaque item existence.
3. `PrivateVaultRepository.toAppMedia()` emits generic private metadata rather than relying only on UI callers to redact it.
4. Settings-redaction tests assert that secret values are absent without incorrectly requiring sensitive field names themselves to disappear.
5. PIN change uses verify-before-atomic-commit so a failed replacement does not destroy the last valid credential envelope.
6. The manifest declares the platform biometric permission instead of suppressing the lint finding.
7. Step-9, Android CI, and retained Step-8 workflows verify literal checked-out head SHAs.
8. Vault import propagates cooperative cancellation and cleans partially encrypted work.
9. Step-9 playback certification now creates a real persisted vault credential through `PrivateVaultAuthenticator`, matching production session restoration behavior.
10. New explicit media loads clear stale errors belonging to prior sources, and certification waits for the intended media ID before treating a playback error as authoritative.

## No-sacrifice confirmation

Step 9 did not obtain green CI by deleting earlier tests, disabling API-26/API-28, removing Step-7 protocol certification, removing decoder/coexistence regressions, making private media merely hidden, persisting plaintext private filenames, storing the PIN, removing GCM authentication, using whole-media `ByteArray` processing, disabling random seeking, narrowing media offsets to `Int`, decrypting whole videos to plaintext temporary files, globally disabling Cast/PiP/external output, breaking cloud/subtitles/audio DSP/queue behavior, using destructive Room migration, adding `Assume` skips, or claiming physical evidence that was not run.

## Known limitations and Step-10 deferrals

The following remain explicitly **NOT VERIFIED — DEFERRED TO STEP 10**:

- biometric prompt/enrollment/invalidation behavior across physical OEM devices;
- screenshot/screen-recording/recents enforcement across OEM devices;
- sustained real 3 GB+ vault import and real low-storage exhaustion;
- real 4K/HDR/high-bitrate private playback and random seek;
- long-duration, thermal and battery behavior;
- physical SD/USB/removable-media behavior;
- real Chromecast/receiver behavior;
- physical Android TV remote/focus behavior;
- HDMI/Miracast/desktop/external Presentation behavior;
- vendor process-killing/background restrictions.

These cases do not block the Step-9 software/emulator definition, but they must not be described as physically certified.

## Final status

The exact PR head passed Android CI, retained Step-8, and dedicated Step-9 certification before merge. PR #22 was merged with an expected-head SHA guard. The exact merge/software `main` SHA then passed all three post-merge workflow families. The documentation-only commit containing this final report must also pass the same repository workflows; its literal self SHA and resulting run IDs are recorded in the merged PR #22 certification record once those checks finish.

STEP 9: PASS
