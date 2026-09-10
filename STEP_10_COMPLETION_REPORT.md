# Step 10 Completion Report

## Scope and baseline

- Repository: `ZubaerAhmed13/MAX-Video-Player`
- Step-10 starting `main`: `476e5416836cb9e75dbe4c97b9a6ed08c9e02bad`
- Starting commit: `docs: finalize Step 9 test matrix`
- Starting open PRs: none
- Baseline Android CI: PASS
- Baseline Step 8 Certification: PASS
- Baseline Step 9 Certification: PASS
- Branch: `step-10-final-certification-release`
- Initial Step-10 infrastructure SHA: `dee4f54cb2ea92c7fe6e000d6dc116681a7d083d`

## Exact build configuration

applicationId `com.zubaer.maxvideoplayer`; versionName `0.1.0-step1`; versionCode `1`; compileSdk/targetSdk `36`; minSdk `23`; Kotlin Compose plugin `2.3.21`; AGP `9.4.0`; KSP `2.3.7`; CI Gradle `9.6.0`; Media3 `1.11.0`; Room `2.8.4`; Compose BOM `2026.06.00`; Coroutines `1.10.2`; OkHttp `5.1.0`; SMBJ `0.14.0`; Commons Net `3.13.0`; direct Step-10 Bouncy Castle security pin `bcprov-jdk18on 1.84`.

The application version is intentionally not bumped merely to manufacture a final release label.

## Changes grouped by purpose

### CI / release-candidate certification

Added `.github/workflows/step10-certification.yml` and `.github/scripts/step10-certify-instrumentation.sh` for exact-SHA clean build/unit/lint/release/AAB, full API-35 retained instrumentation, strict named critical instrumentation, source security checks and package audit.

Step-10 artifact names and package-audit retrieval are bound to the exact certified PR-head SHA rather than GitHub's synthetic pull-request merge SHA, so evidence labels identify the code that was actually checked out and tested.

### Physical/evidence tooling

Added `tools/step10/` helpers for sanitized device profile, release install, smoke/lifecycle stress, playback diagnostics, memory, thermal, evidence sanitization and package verification. None automatically marks a manual/physical requirement PASS.

### Security dependency hardening

Current advisory review found SMBJ 0.14.0's transitive `bcprov-jdk18on 1.79` within affected Bouncy Castle ranges. MAX does not directly use the disclosed GOST CTR or LDAP helper paths, but Step 10 pins the provider to fixed `1.84` so the affected runtime version is not shipped. Step-10 CI verifies release-runtime conflict resolution selects `1.84` instead of the requested transitive `1.79`.

### Playback lifecycle and external-audio hardening

Exact-head certification exposed two production races that narrower runs had not reliably reproduced. Both were fixed in production code; no certification assertion, timeout, skip rule or test expectation was weakened.

- `b262795c73455b21516c2041980a22855a4a9907` added playback-engine generation ownership. `DecoderRepository` and `AudioRepository` are application-scoped while `Media3PlaybackEngine` is service-owned; rapid `PlaybackService` recreation could otherwise allow stale engine analytics/release callbacks to clear or overwrite the replacement engine's decoder/DSP state. Only the newest engine may now mutate that shared playback state.
- `8b9fb7e1b9a2e1c322ec663fdb85e999b0356580` made external-audio selection deterministic. Selection is no longer initiated from a potentially stale `onMediaItemTransition` track topology, and a pending external-audio request receives one bounded clear-and-reapply parameter edge when Media3 reports the desired override but has not yet reflected the selected track. The retry is keyed to the exact `(media, external-audio)` request and cannot become an unbounded event loop.

### Documentation

Updated README, architecture, dependencies, parity matrix, Step-10 device/media/performance/security/checklist/limitations/completion docs, changelog and certification evidence policy.

## Software certification evidence

The final behavior/CI software candidate before this documentation-only closure is `8b9fb7e1b9a2e1c322ec663fdb85e999b0356580`. On that exact immutable head:

- Android CI #501: PASS.
- Step 8 Certification #212: PASS.
- Step 9 Certification #134: PASS.
- Step 10 Certification #25: PASS.
- Android CI full API-35 instrumentation artifact: 104 tests, 0 failures, 0 errors, 0 skipped.
- JVM/unit artifact: 146 tests, 0 failures, 0 errors, 0 skipped.
- Android CI API-26 legacy-thumbnail instrumentation: PASS.
- Android CI API-28 legacy-thumbnail instrumentation: PASS.
- Real protocol certification: SMB change detection, FTP, explicit FTPS and authenticated RTSP production paths PASS.
- Step-8 cloud/Cast/USB/TV/external-display emulator certification: PASS.
- Step-9 full connected + strict privacy/settings/vault production-path certification: PASS.
- Step-10 strict critical-class API-35 certification: PASS.
- Step-10 full retained API-35 instrumentation: PASS.
- Lint: PASS with no blocking errors; remaining compiler/lint warnings are non-blocking and were not expanded into unrelated Step-10 refactoring.
- Bouncy Castle release runtime resolution: `bcprov-jdk18on 1.84`, with SMBJ's requested `1.79` resolved to `1.84`.
- Static release audit: PASS (`STEP10_STATIC_RELEASE_AUDIT_PASS`).
- Package audit: PASS.
- Production signing/publishing: not performed.

Reference release hashes from exact software candidate `8b9fb7e1b9a2e1c322ec663fdb85e999b0356580` / Step 10 #25:

- Debug APK: `d875a6e3e50a5068a35defeb63ce385ca0c6511daf980292289cc8cbb415d04b`
- Unsigned release APK: `12938744a2651a4efdc7802a0414cfc7e9de73003a076d48deaa205b966006a6`
- Release AAB: `612781919b88f43412d35b2f36d496439f7746c1479b06adf6edef7d304797f3`
- Step-10 release evidence ZIP: `0c7a1157e70612e7e4bb6941bf0976d05813215fb6f5cc9c7299a681c7542a72`

Because this completion-report update itself changes the PR head, this documentation-only closure commit is not automatically software-certified by the evidence above. It must independently pass Android CI + Step 8 + Step 9 + Step 10 on its own exact SHA. The authoritative final-head SHA, final workflow runs and final release hashes are recorded in the PR certification record and matching Step-10 workflow artifacts after that rerun; recording them here would itself create another SHA and an endless recertification loop.

## Certification failures and resolved root causes

Step 10 deliberately retained failures until their root causes were understood rather than masking them with retries or relaxed tests.

1. Earlier decoder/coexistence failures, including `Auto decoder did not settle`, were initially intermittent across different suite orderings. Later exact-head strict/full runs established that service recreation could overlap application-scoped decoder state. The production fix was engine-generation ownership in `Media3PlaybackEngine`, committed as `b262795c73455b21516c2041980a22855a4a9907`.
2. On `b262795c73455b21516c2041980a22855a4a9907`, Step 8, Step 9 and Step 10 were green, but Android CI's independent full API-35 suite ran 104 tests and failed exactly one: `ProfessionalAudioIntegrationTest.productionAudioPathSupportsTracksExternalAudioDspSyncAudioOnlyAndSubtitleCoexistence`, at `External audio did not become the selected Media3 audio track`.
3. That failure exposed a separate external-audio selection race. `onMediaItemTransition` could see stale track topology, and a Media3 override could be accepted before its selected bit became visible, leaving the controller passively pending with no guaranteed future state-changing event. Production commit `8b9fb7e1b9a2e1c322ec663fdb85e999b0356580` waits for authoritative track topology and uses a bounded one-time override reassertion for the exact pending request.
4. The same Android CI full API-35 environment that failed on `b262795...` passed all 104 tests on `8b9fb7e...`, while Step 8 #212, Step 9 #134 and Step 10 #25 also passed on that exact SHA.

In hindsight, the earlier decoder/coexistence failures are not classified here as harmless emulator flakes. The later reproduction matrix exposed real lifecycle races, and the final evidence is bound to the production fixes above. No timeout, assertion, skip, test order or application requirement was weakened to obtain green.

## Tooling defects found/fixed

- Added `grep --` protection so a private-key regex beginning with `-` cannot be parsed as a grep option.
- Package verification resolves `aapt` from Android Build Tools 36.0.0 explicitly rather than assuming PATH exposure.
- Step-10 artifact names/download matching use the exact certified PR-head SHA instead of the synthetic pull-request merge SHA.

## Physical certification

`NOT VERIFIED — PHYSICAL HARDWARE UNAVAILABLE`. No real phone/tablet, >3 GB source, 4K/HDR/4K60, Bluetooth/headset, NAS, USB/SD, Cast receiver, Android TV, biometric/OEM privacy, endurance/thermal/battery or external-display result is fabricated.

## P0/P1

No unresolved P0/P1 product defect is established by the exact software-candidate certification matrix. Any reproducible P0/P1 from the documentation-only final-head rerun or later physical testing blocks release.

## Signing/release

`PRODUCTION SIGNING/PUBLISHING NOT PERFORMED`. No final release tag is authorized while mandatory physical certification is incomplete.

## Status

`STEP 10: PARTIAL — SOFTWARE CERTIFIED, PHYSICAL CERTIFICATION INCOMPLETE`

The software-certified qualifier remains valid only after the documentation-only final PR head passes the complete retained exact-SHA workflow matrix. Full PASS additionally requires genuine physical evidence and post-merge final-main verification.

Do not begin Step 11.
