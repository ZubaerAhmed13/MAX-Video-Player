# MAX Video Player — Step 9 Test Matrix

Status vocabulary used here is intentionally strict:

- `PASS` — automated evidence has passed on a relevant implementation revision and the behavior is implemented.
- `PARTIAL` — implementation/evidence exists but the final documentation-complete exact PR head or post-merge `main` certification is still pending.
- `NOT VERIFIED — DEFERRED TO STEP 10` — requires the physical/OEM/hardware phase reserved for Step 10.

Until the exact documentation-complete PR head and exact post-merge `main` head are green, the overall Step-9 result remains `PARTIAL` even when individual tests have already passed on earlier revisions.

| Required area | Status before final exact-head certification | Automated evidence / boundary |
|---|---|---|
| Vault encryption | PARTIAL | Production AES-256-GCM container unit/instrumentation tests; final docs-complete head pending |
| Random-access decryption | PARTIAL | Production container reader + `EncryptedVaultDataSource` range tests |
| Playback | PARTIAL | Redistribution-safe H.264/AAC fixture encrypted by production writer and loaded through existing service/player |
| Seeking | PARTIAL | Start/non-zero/chunk-boundary/cross-chunk/final-byte/DataSpec tests; Media3 source is seekable by range |
| Large logical offsets | PARTIAL | Synthetic `Long` geometry around 2 GiB and 3.2+ GiB without multi-GB allocation |
| Copy to Vault | PARTIAL | Production repository copy transaction + original-retained assertion |
| Move to Vault | PARTIAL | Production repository encrypt → verify → commit → source-delete transaction |
| Failed source deletion semantics | PARTIAL | Dedicated transaction test requires encrypted-copy/original-remains result |
| Storage-full preflight | PARTIAL | Sparse logical source larger than available vault filesystem space rejected before DB/container commit |
| Cancel during encryption | PARTIAL | Cooperative coroutine cancellation observed while `.partial` exists; cancellation propagates and partial/index remain clean |
| Source read failure | PARTIAL | Unreadable source path must fail without valid container/index |
| Vault write failure | PARTIAL | Unwritable vault destination must fail without deleting original or creating valid item |
| Verification mismatch/corruption | PARTIAL | Production decrypt/hash verification plus wrong-master/tamper/chunk-corruption tests; fresh-import mismatch injection not fabricated |
| Partial-file cleanup | PARTIAL | Cancellation cleanup + abandoned `.partial` recovery |
| Database write failure | PARTIAL | Fake DAO commit failure requires completed encrypted artifact rollback and original preservation |
| Post-commit/startup recovery | PARTIAL | Orphan encrypted container removed; indexed containers preserved |
| Corruption detection | PARTIAL | Wrong master/AAD/tamper/chunk swap/truncation/corrupt requested chunk fail closed |
| PIN / passphrase | PARTIAL | Creation/unlock/wrong credential, minimum policy and no-plaintext-PIN assertions |
| Biometric abstraction | PARTIAL | Keystore wrapper/fallback code is guarded; physical prompt/OEM behavior deferred |
| PIN change | PARTIAL | Same master rewrapped; replacement envelope authenticated before atomic commit; old/new credential tests |
| Auto-lock | PARTIAL | Immediate/timed monotonic timeout instrumentation |
| Screenshot security | PARTIAL | `FLAG_SECURE` window-policy instrumentation; real OEM capture behavior deferred |
| Notification privacy | PARTIAL | Generic private MediaSession metadata and lock clear behavior; physical lock-screen UI deferred |
| Settings | PARTIAL | Typed persistence, enum handling, section/all reset tests |
| Settings export | PARTIAL | Versioned non-sensitive JSON and sentinel-secret absence |
| Settings import | PARTIAL | Validated/bounded parser, unknown field/version/malformed/oversize handling |
| Secret redaction | PARTIAL | Token/password/cookie/Authorization/signed-query redaction unit tests |
| Sleep timer | PARTIAL | Service-owned duration/end-current/end-queue controller with fake monotonic clock tests |
| Sleep fade | PARTIAL | Player-volume-only progression/cancel/restore tests; does not alter system media volume |
| TalkBack semantics | PARTIAL | Generic lock surface and sentinel private-title absence in Compose semantics |
| Large font | PARTIAL | Representative Settings flow at 2.0× controlled font scale remains scrollable/reachable |
| Keyboard | PARTIAL | Existing TV/player key policy exercised for Enter/Escape/media/navigation actions |
| D-pad | PARTIAL | Existing Step-8 `TvPlayerInputController` policy retained and exercised |
| System caption integration | PARTIAL | Android `CaptioningManager` bridge round-trip/restoration instrumentation |
| Audio-description metadata | PARTIAL | Media3 descriptive-role policy labels only `ROLE_FLAG_DESCRIBES_VIDEO`; JVM test |
| API compatibility | PARTIAL | minSdk 23 code guards + retained API-26/API-28 lanes; final exact-head results pending |
| Step 1–8 regressions | PARTIAL | Android CI, Step-7 protocol, Step-8 certification and decoder regression lanes retained and exact-head checkout hardened; final results pending |
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

Biometric Android Keystore integration remains software-reviewed/guarded in Step 9. Physical biometric prompt behavior and OEM invalidation are not promoted to PASS until Step 10.

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

Final exact-head certification and post-merge `main` certification are intentionally still required before changing the software rows and completion report to final PASS.

**STEP 9 TEST MATRIX: PARTIAL — FINAL EXACT-HEAD CERTIFICATION PENDING**
