# MAX Video Player — Step 9 Completion Report

## Certification state

This report is intentionally truthful while PR #22 is still open. It is updated to `PASS` only after the exact documentation-complete PR head passes all required software/emulator gates, the PR is merged, and the exact resulting `main` head passes the required post-merge gates.

- Repository: `ZubaerAhmed13/MAX-Video-Player`
- Branch: `step-9-private-security-settings-accessibility`
- Pull request: `#22` — `Step 9: Private media, security, settings, sleep timer and accessibility`
- Step-9 base `main`: `f79078372b03a115208cbb6bb71a54e51eeb260c`
- Certified PR-head SHA: **PENDING FINAL EXACT-HEAD CERTIFICATION**
- Merge commit: **PENDING**
- Exact final `main` SHA: **PENDING**
- Final Android CI run ID: **PENDING**
- Final Step-9 Certification run ID: **PENDING**
- Final retained Step-8 Certification run ID: **PENDING**

The earlier head `a23136a040998050812c32ddbd7faf8420a33271` was deliberately **not** accepted: its Step-9 run `34415942077`, Step-8 run `34415942093`, and Android CI run `34415942092` were red. Investigation found one legitimate biometric permission lint error and two instrumentation-source compile errors. Those causes were patched rather than waived. The Step-9 workflow was also hardened so PR certification explicitly checks out and verifies the literal PR head instead of accepting only GitHub's synthetic PR merge ref. The retained Android CI and Step-8 workflows were subsequently given the same literal-head checkout assertion without removing any earlier lane.

## Step-9 deliverables

### Private Vault

Step 9 adds an encrypted Private Vault using an opaque `maxvault://<vault-id>` source identity. Media is stored under app-private no-backup storage as versioned `MAXVLT01` containers. The payload is authenticated/encrypted in bounded 1 MiB chunks, enabling random reads/seeks without whole-file decryption or whole-media RAM allocation.

The vault uses AES-256-GCM with a random per-file content key. Sensitive media metadata and the per-file key are encrypted/authenticated. The Room v8 private index stores opaque identity/location, sizes, timestamps, format/status fields and does not intentionally persist plaintext original titles, filenames or source paths.

### Authentication and App Lock

`PrivateVaultSession` is the single in-process authority for vault lock state/master-key access. PIN/passphrase authentication derives a wrapping key with PBKDF2-HMAC-SHA256, a random salt and 310,000 production iterations; the PIN/passphrase itself is never persisted. Wrong attempts use a bounded monotonic retry delay without destructive wiping.

Changing the credential authenticates the current credential, obtains the same master secret, creates a fresh-salt replacement authenticated envelope, decrypts/verifies it in memory, constant-time compares the recovered master, and only then atomically replaces the stored envelope. Private media therefore does not need to be re-encrypted when the PIN changes.

Optional biometric unlock uses an Android Keystore AES key and platform `BiometricPrompt`; PIN/passphrase remains the recovery path. `USE_BIOMETRIC` is declared. Physical/OEM biometric behavior remains Step-10 work.

Optional whole-app App Lock is separate from mandatory vault lock and supports immediate/30-second/one-minute/five-minute background timeout using monotonic elapsed time.

### Import, copy and move

Vault imports stream into opaque `.partial` containers. Copy preserves the original. Move performs encrypted write, production-path decrypt/hash verification and commit before requesting source deletion. If source deletion fails, the result explicitly reports that an encrypted copy was created while the original remains. Partial/orphan cleanup prevents incomplete imports from appearing as valid private items.

Long-running import now observes coroutine cancellation during production source reads and verification chunks. Cancellation is propagated as `CancellationException` rather than being converted to a misleading normal failure result, and an in-progress partial is deleted. The dedicated transaction suite exercises copy success, move success, failed source deletion semantics, storage preflight rejection, source read failure, vault write failure, database commit failure/rollback, cancellation during streaming, and abandoned partial/orphan recovery. A process killed by the OS cannot execute a Kotlin cleanup handler, so restart recovery remains the authority for abandoned on-disk partial/orphan cleanup.

### Production playback integration

Private media reuses the existing single service-owned path:

`PlaybackConnection -> PlaybackService -> MediaSession -> Media3PlaybackEngine -> ProfessionalMediaSourceFactory -> NetworkDataSourceRouter -> EncryptedVaultDataSource`

No second private ExoPlayer is created. A redistribution-safe H.264/AAC fixture is encrypted by the production writer during instrumentation and loaded through that existing pipeline; the test checks video/audio track initialization and Activity recreation without a second controller.

### Privacy-safe playback restrictions

Private playback uses generic session metadata (`Private media`), and the service clears private playback/session state when the vault locks. Private `maxvault://` media is rejected by Cast source resolution before direct/relay selection, private PiP is blocked, and external Presentation output is returned to the phone before private playback. Ordinary public Cast/PiP/external-display behavior is not globally disabled.

`FLAG_SECURE` is applied to protected private surfaces and removed again outside those surfaces according to the setting. Instrumentation certifies the intended flag state; actual OEM screenshots/recording/recents behavior remains deferred.

### Database migration

Room advances from version 7 to version 8 with explicit `MIGRATION_7_8`, adding `private_media`. No destructive migration fallback was introduced. Migration instrumentation opens a v7 schema with representative earlier rows, migrates to v8, verifies prior data remains and verifies the private table/schema exists.

### Advanced Settings

Step 9 adds a typed advanced-settings layer for App Lock, lock timeout, biometric convenience unlock, private-screen protection, Reduce Motion, high-contrast controls, Android system caption styling, and sleep-fade duration.

Settings export is explicitly non-sensitive and versioned. Import is bounded to 256 KiB and validates supported fields while handling malformed, unsupported-version and bounded unknown-future input safely. Ordinary reset restores non-sensitive settings without deleting Private Vault media/authentication, cloud/network credentials, history or playlists.

`SecurityRedactor` centralizes masking of credential-bearing headers, cookies, token/password fields, URL user-info and signed query material used by Step-9 diagnostics/export surfaces.

### Sleep Timer

The production sleep timer is owned by `PlaybackService`, not by an Activity/Compose timer. It supports duration, end of current media and end of queue, uses `SystemClock.elapsedRealtime` for duration deadlines, pauses playback on expiry, and optionally fades only the player output volume before expiry. Cancel/replace restores the prior player volume; Android system media volume is not modified by the fade.

### Accessibility

Step 9 adds/retains meaningful semantics, approximately 48 dp minimum targets on new critical controls, scrollable Settings/lock/timer surfaces, large-font certification at 2.0×, deterministic keyboard/D-pad mappings through the Step-8 TV input policy, high-contrast theme mode, a scoped Reduce Motion preference, and private accessibility-tree protection while locked.

Android system caption integration reads supported `CaptioningManager` foreground/background/window/edge/font-scale values into the existing subtitle renderer state only when enabled, and restores the user's MAX custom subtitle style when disabled. Existing custom subtitle styling remains available.

Media3 audio tracks are labeled `Audio description` only when the track role metadata includes `C.ROLE_FLAG_DESCRIBES_VIDEO`; Step 9 does not auto-select a descriptive track solely because of that label.

## Automated tests added for Step 9

JVM/unit coverage includes:

- AES-GCM/KDF/nonce/AAD/constant-time and overflow-safe crypto policy;
- versioned container round-trips, empty/one-byte/full/multi/partial chunks;
- wrong master, tamper, swap and truncation failures;
- `Long` geometry at 2 GiB and 3.2+ GiB;
- 64 MiB generated streaming fixture/random reads;
- sentinel metadata absence in raw encrypted container;
- private Cast rejection;
- settings JSON/import/export/redaction safety;
- audio-description role-label policy.

API-35 Step-9 instrumentation explicitly includes:

- `PrivateVaultIntegrationTest`;
- `Step9PrivatePlaybackCoexistenceInstrumentedTest`;
- `Step9VaultAuthInstrumentedTest`;
- `Step9VaultImportInstrumentedTest`;
- `Step9DatabaseMigrationTest`;
- `Step9SettingsPrivacyInstrumentedTest`;
- `Step9SleepTimerInstrumentedTest`;
- `Step9PrivateSurfaceInstrumentedTest`;
- `Step9AccessibilityInstrumentedTest`.

`Step9VaultImportInstrumentedTest` covers production repository copy/move behavior, denied source deletion truthfulness, insufficient-storage preflight with sparse logical source geometry, injected/OS-level read/write failure behavior where feasible, database commit rollback, cooperative cancellation cleanup, and startup partial/orphan recovery.

`.github/scripts/step9-certify-instrumentation.sh` runs every required class by exact name and rejects failure, missing success and zero-test execution.

## CI architecture

`.github/workflows/step9-certification.yml` adds:

- `step9-security-unit`: exact-checkout assertion; debug build; full JVM unit suite; release compile; lint;
- `step9-emulator-certification`: exact-checkout assertion; complete API-35 `connectedDebugAndroidTest`; then strict named-class Step-9 instrumentation.

The retained Android CI/Step-8 workflows are not deleted or weakened. Their jobs now also explicitly check out and assert the literal PR head before execution. They continue to provide API-26/API-28 thumbnail coverage, API-35 full instrumentation, Step-7 real protocol-server certification, Step-8 cloud/Cast/TV/output certification and Step-6 decoder/coexistence regressions.

## Dependencies

Step 9 adds no third-party runtime cryptography dependency and no second playback engine. Vault cryptography uses standard JCA/Android cryptography; biometric wrapping uses Android Keystore/platform biometric APIs; system captions use Android `CaptioningManager`; persistence uses the existing Room/SharedPreferences stack; playback uses the existing Media3 stack. See `DEPENDENCIES.md`.

## Changed-file groups

PR #22 changes Step-9 CI/script files; retained CI checkout hardening; Android manifest/backup rules; app composition/container/activity; Room/model integration; audio role accessibility; Cast resolver; library file-action isolation; source routing; the complete `feature.privatevault` domain/auth/crypto/data-source/persistence/presentation/repository/storage stack; settings/redaction/caption bridge; sleep timer UI/controller; playback engine/service integration; theme accessibility; the four canonical project documents; the three required Step-9 documents; and Step-9 JVM/instrumentation tests.

The canonical changed-file list is the PR's GitHub file list. No earlier Step-1–8 test file was deleted to obtain Step-9 green status.

## Security findings resolved during implementation

1. Initial integration compiler errors after adding `MediaSourceType.PRIVATE` were fixed by making existing source-type `when` expressions exhaustive and rejecting private media from normal public file actions.
2. Locked vault resolution now authenticates before checking opaque item existence, reducing path/existence disclosure while locked.
3. `PrivateVaultRepository.toAppMedia()` itself now emits generic private metadata rather than relying only on a caller to redact title data.
4. Settings redaction tests now check secret values rather than incorrectly requiring sensitive field names themselves to disappear.
5. PIN change was hardened from commit-then-verify to verify-before-atomic-commit, preventing a failed replacement from intentionally destroying the last valid credential envelope.
6. The main manifest now declares the permission required by platform biometric use instead of suppressing `MissingPermission` lint.
7. Step-9, Android CI and retained Step-8 CI now verify the literal PR head checkout; a synthetic merge-ref green run alone is not considered exact-head certification.
8. Vault import was hardened for cooperative coroutine cancellation so cancellation is not swallowed and partially encrypted work is cleaned rather than reported as successful.

## No-sacrifice confirmation

Step 9 has not intentionally obtained green CI by deleting Step-1–8 tests, disabling API-26/API-28, removing Step-7 protocol certification, removing decoder regression tests, making private media merely hidden, persisting plaintext private filenames in Room, storing the PIN, removing GCM authentication, using a whole-media `ByteArray`, disabling random seeking, narrowing media offsets to `Int`, decrypting whole videos to plaintext temporary files, globally disabling Cast/PiP/external display, breaking cloud/subtitles/audio DSP/queue behavior, using destructive Room migration, adding `Assume` skips, or claiming physical hardware evidence that was not run.

## Known limitations and Step-10 deferrals

The following are explicitly **NOT VERIFIED — DEFERRED TO STEP 10**:

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

These do not block the Step-9 software/emulator definition, but they must not be described as physically certified.

## Final gate still required

Before this report may end in `STEP 9: PASS`, the exact documentation-complete branch head must be green for the dedicated Step-9 workflow and retained required regressions, PR #22 must merge, and the exact resulting/final `main` SHA must pass the same required software/emulator gates. Run IDs and hashes will be recorded here once those gates are real.

STEP 9: PARTIAL
