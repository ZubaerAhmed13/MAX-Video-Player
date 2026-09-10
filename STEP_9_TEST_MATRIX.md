# MAX Video Player — Step 9 Test Matrix

Status vocabulary used here is intentionally strict:

- `PASS` — automated software/emulator evidence has passed on the certified Step-9 implementation and the behavior is implemented.
- `NOT VERIFIED — DEFERRED TO STEP 10` — requires the physical/OEM/hardware phase reserved for Step 10.

Software-policy and emulator-testable behavior are separated from physical OEM/hardware behavior. A software policy may be `PASS` while the corresponding device/OEM enforcement remains explicitly deferred to Step 10.

| Required area | Status | Automated evidence / boundary |
|---|---|---|
| Vault encryption | PASS | Production AES-256-GCM container unit/instrumentation tests |
| Random-access decryption | PASS | Production container reader + `EncryptedVaultDataSource` range tests |
| Playback | PASS | Redistribution-safe H.264/AAC fixture encrypted by production writer and loaded through existing service/player |
| Seeking | PASS | Start/non-zero/chunk-boundary/cross-chunk/final-byte/DataSpec tests; Media3 source is seekable by range |
| Large logical offsets | PASS | Synthetic `Long` geometry around 2 GiB and 3.2+ GiB without multi-GB allocation |
| Copy to Vault | PASS | Production repository copy transaction + original-retained assertion |
| Move to Vault | PASS | Production repository encrypt → verify → commit → source-delete transaction |
| Failed source deletion semantics | PASS | Dedicated transaction test requires encrypted-copy/original-remains result |
| Storage-full preflight | PASS | Sparse logical source larger than available vault filesystem space rejected before DB/container commit |
| Cancel during encryption | PASS | Cooperative coroutine cancellation observed while `.partial` exists; cancellation propagates and partial/index remain clean |
| Source read failure | PASS | Unreadable source path must fail without valid container/index |
| Vault write failure | PASS | Unwritable vault destination must fail without deleting original or creating valid item |
| Verification mismatch/corruption | PASS | Production decrypt/hash verification plus wrong-master/tamper/chunk-corruption tests; fresh-import mismatch injection not fabricated |
| Partial-file cleanup | PASS | Cancellation cleanup + abandoned `.partial` recovery |
| Database write failure | PASS | Fake DAO commit failure requires completed encrypted artifact rollback and original preservation |
| Post-commit/startup recovery | PASS | Orphan encrypted container removed; indexed containers preserved |
| Corruption detection | PASS | Wrong master/AAD/tamper/chunk swap/truncation/corrupt requested chunk fail closed |
| PIN / passphrase | PASS | Creation/unlock/wrong credential, minimum policy and no-plaintext-PIN assertions |
| Biometric software policy | PASS | Android Keystore wrapper, guarded biometric path, PIN/passphrase recovery path, and invalidation fallback policy are implemented/tested at software level |
| Biometric physical OEM behavior | NOT VERIFIED — DEFERRED TO STEP 10 | Real prompt/enrollment/invalidation behavior across physical OEM devices is not claimed by Step 9 |
| PIN change | PASS | Same master rewrapped; replacement envelope authenticated before atomic commit; old/new credential tests |
| Auto-lock | PASS | Immediate/timed monotonic timeout instrumentation |
| Screenshot/recents software policy | PASS | `FLAG_SECURE` protected-surface window policy is instrumented and verified in software/emulator tests |
| Screenshot/recording/recents physical OEM behavior | NOT VERIFIED — DEFERRED TO STEP 10 | Real screenshot, screen-recorder and recents enforcement varies by OEM/device and is reserved for physical certification |
| Notification privacy software policy | PASS | Generic private MediaSession metadata and lock clear behavior |
| Physical lock-screen / notification OEM presentation | NOT VERIFIED — DEFERRED TO STEP 10 | Device/OEM lock-screen and notification presentation is not physically certified in Step 9 |
| Settings | PASS | Typed persistence, enum handling, section/all reset tests |
| Settings export | PASS | Versioned non-sensitive JSON and sentinel-secret absence |
| Settings import | PASS | Validated/bounded parser, unknown field/version/malformed/oversize handling |
| Secret redaction | PASS | Token/password/cookie/Authorization/signed-query redaction unit tests |
| Sleep timer | PASS | Service-owned duration/end-current/end-queue controller with fake monotonic clock tests |
| Sleep fade | PASS | Player-volume-only progression/cancel/restore tests; does not alter system media volume |
| TalkBack semantics | PASS | Generic lock surface and sentinel private-title absence in Compose semantics |
| Large font | PASS | Representative Settings flow at 2.0× controlled font scale remains scrollable/reachable |
| Keyboard | PASS | Existing TV/player key policy exercised for Enter/Escape/media/navigation actions |
| D-pad | PASS | Existing Step-8 `TvPlayerInputController` policy retained and exercised |
| System caption integration | PASS | Android `CaptioningManager` bridge round-trip/restoration instrumentation |
| Audio-description metadata | PASS | Media3 descriptive-role policy labels only `ROLE_FLAG_DESCRIBES_VIDEO`; JVM test |
| API compatibility | PASS | minSdk 23 code guards + retained API-26/API-28 lanes |
| Step 1–8 regressions | PASS | Android CI, Step-7 protocol, Step-8 certification and decoder regression lanes retained and exact-head checkout hardened |
| Physical-device certification | NOT VERIFIED — DEFERRED TO STEP 10 | No physical claims in Step 9 |

## Mandatory crypto/format coverage

The Step-9 unit suite covers production cryptographic/container code for empty content, one byte, one chunk, multiple chunks and a partial final chunk; non-zero and cross-chunk ranges; wrong master/key material; AAD/authentication failure; modified ciphertext/tag effects; chunk swaps; truncation; nonce/chunk-index behavior; KDF known behavior; constant-time equality; and overflow-safe `Long` geometry. A 64 MiB generated streaming fixture exercises multiple production chunks without converting the full source into a giant test `ByteArray`.

The private metadata leak fixture uses the sentinel `TOP_SECRET_PRIVATE_MOVIE_839247.mp4` and checks the raw encrypted container does not expose that plaintext metadata. The private Room entity schema contains opaque identifiers/location/size/timestamp/version/status fields rather than plaintext title/source name.

## `EncryptedVaultDataSource` coverage

Instrumentation exercises position 0, position inside the first chunk, exact chunk boundary, cross-chunk read, final byte, EOF/read-after-EOF, bounded `DataSpec.length`, `C.LENGTH_UNSET`, position beyond EOF, closed source behavior, locked-session rejection before item resolution, and corrupted requested-chunk failure.

2 GiB and 3.2+ GiB address arithmetic is tested synthetically because Step 9 explicitly must not allocate multiple gigabytes in CI merely to demonstrate arithmetic correctness.

## Import transaction coverage

`Step9VaultImportInstrumentedTest` exercises the real `PrivateVaultRepository`/container/storage transaction path for:

- copy success with original retained;
- move success with original deleted only after encrypted verification/commit;
- failed source deletion, which must return `EncryptedCopyCreatedOriginalRemains` and leave the original present;
- insufficient-storage preflight using sparse logical file geometry, with no DB/container creation;
- unreadable source failure;
- unwritable vault destination failure;
- database `upsert` failure after encrypted-file commit, requiring encrypted artifact rollback and source preservation;
- cooperative cancellation after an in-progress `.partial` has been observed, requiring propagated `CancellationException`, no private DB row, no completed container and no new partial;
- startup recovery of abandoned partial and orphan encrypted containers while indexed containers remain untouched.

The actual OS-kill point cannot run a Kotlin cleanup handler; `recoverAbandonedTransactions()` is therefore the restart authority and is tested against abandoned `.partial`/orphan state. Real low-storage exhaustion during sustained physical writes remains Step 10.

## Authentication coverage

`Step9VaultAuthInstrumentedTest` covers vault creation, correct/wrong credentials, no stored plaintext PIN, bounded retry behavior, credential change without changing the underlying master secret, rejection of the previous credential after successful change, acceptance of the replacement credential, and App Lock timeout behavior with an injected monotonic clock.

The biometric Android Keystore integration and recovery/invalidation policy are software-certified in Step 9. Physical biometric prompt, enrollment, invalidation and OEM-specific behavior remain **NOT VERIFIED — DEFERRED TO STEP 10**.

## Settings/privacy coverage

`SettingsSecurityTest` and `Step9SettingsPrivacyInstrumentedTest` cover known serialization, unknown future fields, malformed/unsupported inputs, oversized-file rejection, non-sensitive reset, persistence, import/export, and sentinel-secret isolation. Reset does not intentionally clear Private Vault authentication/media or the existing cloud/network credential systems.

## Sleep timer coverage

The deterministic timer tests use an injected monotonic clock and exercise duration, remaining time, expiry/pause, cancel, replacement, fade progression, fade cancellation and player-volume restoration. `PlaybackService` owns the production controller, so Activity recreation does not create the timer/player authority.

## Accessibility coverage

The Step-9 accessibility instrumentation exercises 2.0× font scale on a scrollable Settings screen; generic lock-screen semantics with a private sentinel absent; keyboard/D-pad mappings through the retained Step-8 input controller; system caption style bridging/restoration; and descriptive-audio role mapping. Ordinary playback progress remains functional under Reduce Motion; Step 9 does not globally disable Android animation APIs.

## Dedicated Step-9 CI

`.github/workflows/step9-certification.yml` contains:

- `step9-security-unit`: exact checkout verification, `:app:assembleDebug`, `:app:testDebugUnitTest`, `:app:assembleRelease`, `:app:lintDebug`;
- `step9-emulator-certification`: exact checkout verification, complete API-35 `:app:connectedDebugAndroidTest`, then explicitly named Step-9 instrumentation classes;
- `.github/scripts/step9-certify-instrumentation.sh`: fails if a required class reports failures, does not report `OK`, or executes zero tests.

Required explicit Step-9 classes are:

1. `PrivateVaultIntegrationTest`
2. `Step9PrivatePlaybackCoexistenceInstrumentedTest`
3. `Step9VaultAuthInstrumentedTest`
4. `Step9VaultImportInstrumentedTest`
5. `Step9DatabaseMigrationTest`
6. `Step9SettingsPrivacyInstrumentedTest`
7. `Step9SleepTimerInstrumentedTest`
8. `Step9PrivateSurfaceInstrumentedTest`
9. `Step9AccessibilityInstrumentedTest`

The Step-9, retained Android CI and retained Step-8 workflows check out `${{ github.event.pull_request.head.sha }}` for pull requests (or `${{ github.sha }}` otherwise) and assert that `git rev-parse HEAD` equals that value before certification. A green synthetic PR merge ref alone is therefore not accepted as the final exact-head proof.

## Retained regression gates

The existing project workflows remain authoritative for earlier functionality, including API-26/API-28 thumbnail regression, API-35 instrumentation, Step-7 isolated SMB/FTP/FTPS/authenticated-RTSP certification, Step-8 cloud/Cast/TV/output certification, and Step-6 decoder/coexistence regression. Step 9 does not delete or skip these lanes.

## Step-10 physical deferrals

All of the following remain **NOT VERIFIED — DEFERRED TO STEP 10**:

- real biometric prompt/enrollment/invalidation across OEM devices;
- screenshot, screen recorder and recents behavior across OEM builds;
- sustained real 3 GB+ private import and low-storage exhaustion;
- real 4K/HDR/high-bitrate private playback and seeking;
- long-duration playback, thermal and battery behavior;
- SD/USB/removable-storage physical behavior;
- physical Chromecast/receiver behavior;
- Android TV hardware/remote behavior;
- HDMI/Miracast/desktop/external-display behavior;
- aggressive vendor process killing/background restrictions.

## Current overall status

Step 9 software/emulator functionality is certified. This documentation-only synchronization commit does not change production code and must itself pass Android CI, retained Step-8 Certification, and Step-9 Certification on its literal new `main` SHA before the documentation update is treated as fully closed.

**STEP 9 TEST MATRIX: PASS — SOFTWARE / EMULATOR CERTIFIED**
