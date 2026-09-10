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

Current advisory review found SMBJ 0.14.0's transitive `bcprov-jdk18on 1.79` within affected Bouncy Castle ranges. MAX does not directly use the disclosed GOST CTR or LDAP helper paths, but Step 10 pins the provider to fixed `1.84` so the affected runtime version is not shipped. This production dependency change invalidated earlier candidate evidence and required the full relevant regression matrix.

### Documentation

Updated README, architecture, dependencies, parity matrix, Step-10 device/media/performance/security/checklist/limitations/completion docs, changelog and certification evidence policy.

## Software certification evidence

The last behavior/CI candidate before this documentation-only closure was `a185bfc009c622c47e380de7fe0333a4d62162a7`. On that exact head:

- Android CI #493: PASS.
- Step 8 Certification #204: PASS.
- Step 9 Certification #126: PASS.
- Step 10 Certification #9: PASS.
- Full API-35 retained instrumentation: 104 tests, 0 failures, 0 errors, 0 skipped.
- JVM/unit suite: 146 tests passed with no failures/errors/skips.
- Lint: 0 errors; remaining warnings were non-blocking and not expanded into unrelated Step-10 refactoring.
- Bouncy Castle release runtime resolution: `bcprov-jdk18on 1.84`.
- Static release audit: PASS.
- Package audit: PASS.
- Production signing/publishing: not performed.

Reference release hashes from that exact certified head:

- Debug APK: `f8146597d54bb9a0a48d1722f08a8a53bd1c30b05828e7aaa34b295207bf4fb5`
- Unsigned release APK: `a522185214d12a8e61be3442b2d5eadbb6ce4c942c59a92bdc1d1866b278ab45`
- Release AAB: `b69e18128fb0d44aa0514c35c55775223bf9d47a53bddfbf590624eda693b21e`

Because this completion-report update itself changes the PR head, the exact documentation-only closure commit must also pass Android CI + Step 8 + Step 9 + Step 10 before the software-certified status remains valid. The authoritative final-head SHA and its final release hashes are recorded in the PR certification record and matching Step-10 workflow artifacts after that rerun.

## Transient instrumentation failure disposition

An earlier Step-10 full-suite run on SHA `6a1a6a9269c721e0c427a50c1bb88a02911ccf96` ran 104 tests with one failure in `Step6CoexistenceIntegrationTest.queueSidecarsDspPoliciesAndRecreationSurviveDecoderSwitch`: `Auto decoder did not settle`.

The same critical coexistence class passed in the strict instrumentation gate. The failed full-suite job was rerun on the same unchanged SHA and passed, and the subsequent `a185bfc009c622c47e380de7fe0333a4d62162a7` final software candidate passed the complete 104-test suite on its first run. No timeout, assertion, skip rule or application behavior was weakened to obtain green. The failure is therefore retained as a non-reproducible CI/emulator flake, not hidden or reclassified as a product pass without evidence.

## Tooling defects found/fixed

- Added `grep --` protection so a private-key regex beginning with `-` cannot be parsed as a grep option.
- Package verification resolves `aapt` from Android Build Tools 36.0.0 explicitly rather than assuming PATH exposure.
- Step-10 artifact names/download matching now use the exact certified PR-head SHA instead of the synthetic pull-request merge SHA.

## Physical certification

`NOT VERIFIED — PHYSICAL HARDWARE UNAVAILABLE`. No real phone/tablet, >3 GB source, 4K/HDR/4K60, Bluetooth/headset, NAS, USB/SD, Cast receiver, Android TV, biometric/OEM privacy, endurance/thermal/battery or external-display result is fabricated.

## P0/P1

No unresolved P0/P1 product defect is currently established. Any reproducible P0/P1 from exact-head CI or later physical testing blocks release.

## Signing/release

`PRODUCTION SIGNING/PUBLISHING NOT PERFORMED`. No final release tag is authorized while mandatory physical certification is incomplete.

## Status

`STEP 10: PARTIAL — SOFTWARE CERTIFIED, PHYSICAL CERTIFICATION INCOMPLETE`

The software-certified qualifier is evidence-bound to the exact current PR head. Full PASS additionally requires genuine physical evidence and post-merge final-main verification.

Do not begin Step 11.
