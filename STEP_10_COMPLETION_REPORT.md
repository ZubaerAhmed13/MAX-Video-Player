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

### Physical/evidence tooling

Added `tools/step10/` helpers for sanitized device profile, release install, smoke/lifecycle stress, playback diagnostics, memory, thermal, evidence sanitization and package verification. None automatically marks a manual/physical requirement PASS.

### Security dependency hardening

Current advisory review found SMBJ 0.14.0's transitive `bcprov-jdk18on 1.79` within affected Bouncy Castle ranges. MAX does not directly use the disclosed GOST CTR or LDAP helper paths, but Step 10 pins the provider to fixed `1.84` so the affected runtime version is not shipped. This production dependency change invalidates earlier candidate evidence and requires the full relevant regression matrix.

### Documentation

Updated README, architecture, dependencies, parity matrix, Step-10 device/media/performance/security/checklist/limitations/completion docs, changelog and certification evidence policy.

## Tooling defects found/fixed

- Added `grep --` protection so a private-key regex beginning with `-` cannot be parsed as a grep option.
- Package verification resolves `aapt` from Android Build Tools 36.0.0 explicitly rather than assuming PATH exposure.

## Physical certification

`NOT VERIFIED — PHYSICAL HARDWARE UNAVAILABLE`. No real phone/tablet, >3 GB source, 4K/HDR/4K60, Bluetooth/headset, NAS, USB/SD, Cast receiver, Android TV, biometric/OEM privacy, endurance/thermal/battery or external-display result is fabricated.

## P0/P1

No unresolved P0/P1 product defect is currently established. Any reproducible P0/P1 from exact-head CI or later physical testing blocks release.

## Signing/release

`PRODUCTION SIGNING/PUBLISHING NOT PERFORMED`. No final release tag is authorized while mandatory physical certification is incomplete.

## Status

`STEP 10: PARTIAL — SOFTWARE CERTIFIED, PHYSICAL CERTIFICATION INCOMPLETE`

The software-certified qualifier is valid only after the exact final Step-10 PR head and required retained software gates are green. Full PASS additionally requires genuine physical evidence and post-merge final-main verification.

Do not begin Step 11.
