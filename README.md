# MAX Video Player

MAX Video Player is an original native Android media-player project built as a clean-room implementation. Commercial players may be used only as behavioral inspiration; proprietary source/decompiled code, decoder implementations, branding, assets, credentials, certificates, package names and protected binaries are not copied.

## Current development status

**Step 10 of 10 — Final QA, Performance, Physical-Device Certification, Security Hardening & Release Readiness**

Step 1–9 software functionality is preserved. Step 10 adds release-candidate certification, exact-SHA CI, package/security audits, physical-device evidence tooling and final release documentation. It does not add unrelated product features or a second playback authority.

Step-10 branch: `step-10-final-certification-release`

Verified Step-10 starting `main`: `476e5416836cb9e75dbe4c97b9a6ed08c9e02bad` (`docs: finalize Step 9 test matrix`). Android CI, Step 8 Certification and Step 9 Certification were green on that baseline before Step-10 work began.

The current execution environment has no connected physical certification hardware. Therefore physical phone/tablet, real >3 GB, 4K/HDR/4K60, Bluetooth/headset, NAS, USB/SD, Cast receiver, Android TV, biometric/OEM privacy, thermal/battery/endurance and external-display claims remain **NOT VERIFIED** until run on real hardware.

Current policy status while those gates are missing:

`STEP 10: PARTIAL — SOFTWARE CERTIFIED, PHYSICAL CERTIFICATION INCOMPLETE`

The software portion of that status is valid only for an exact Step-10 head whose required workflow is green. A final release tag must not be created solely from emulator/software evidence.

## Platform baseline

- Native Android / Kotlin / Jetpack Compose
- Java 17
- Android Gradle Plugin 9.4.0
- Kotlin Compose plugin 2.3.21
- KSP 2.3.7
- CI Gradle 9.6.0
- compileSdk 36
- targetSdk 36
- minSdk 23
- Media3 / ExoPlayer 1.11.0
- Room 2.8.4, schema version 8
- Compose BOM 2026.06.00
- Coroutines 1.10.2
- OkHttp 5.1.0
- SMBJ 0.14.0
- Apache Commons Net 3.13.0

`versionName` remains `0.1.0-step1` and `versionCode` remains `1`. Step 10 does not invent a release version before full certification/release policy is satisfied.

## Authoritative playback architecture

```text
Compose/UI
  → PlaybackConnection / MediaController
  → PlaybackService : MediaSessionService
  → MediaSession
  → Media3PlaybackEngine
  → one ExoPlayer
  → ProfessionalMediaSourceFactory / ProfessionalRenderersFactory
```

One service-owned player remains authoritative for local files, network sources, private-vault playback, decoder switching, background playback and all UI controller state. Cast/output-specific integrations may delegate appropriately, but there is no hidden Activity-owned second ExoPlayer.

The only user-facing decoder labels remain `Auto`, `Hardware`, `Enhanced Hardware`, and `Software`. Effective decoder reporting must reflect the actual MediaCodec/runtime selection.

## Preserved Steps 1–9

- **Step 1:** service-owned playback, MediaSession, URI/reference-based media, resume/history and long-safe foundations.
- **Step 2:** MediaStore/SAF library, folders, history, favourites, playlists, relink, file actions and bounded thumbnails.
- **Step 3:** controls, seeking, gestures, zoom/pan/aspect/rotation/fullscreen, queue, repeat/shuffle and public PiP.
- **Step 4:** embedded/external subtitles, sidecars, SRT/WebVTT/SSA/ASS/TTML, styling and timing.
- **Step 5:** audio tracks/sidecars, EQ, preamp/boost/limiter, balance/channel mapping, delay, pitch and background/audio-only behavior.
- **Step 6:** Auto/Hardware/Enhanced Hardware/Software decoder policy, real codec diagnostics, fallback and state-preserving reconfiguration.
- **Step 7:** HTTP/HTTPS, HLS, DASH, RTSP, SMB2/3, WebDAV, FTP and explicit FTPS through the Media3 source architecture; credential/TLS policy retained.
- **Step 8:** cloud providers, Cast, USB/OTG, Android TV and external-display paths.
- **Step 9:** encrypted Private Vault, PIN/passphrase and optional biometric wrapper, privacy/output restrictions, advanced settings, sleep timer and accessibility.

See the existing Step 1–9 architecture, security, test-matrix and completion documents for detailed historical evidence.

## Step-10 certification infrastructure

`.github/workflows/step10-certification.yml` verifies the literal expected Git SHA and separates:

- clean debug/release/AAB build, JVM/unit tests and lint;
- full API-35 retained instrumentation;
- explicitly named strict critical instrumentation with non-zero-test checks;
- static release/security checks;
- release APK/AAB metadata, permission, debuggable/testOnly, secret-sentinel and SHA-256 inspection.

`tools/step10/` provides reusable scripts for sanitized device profiles, release-candidate install, launch/lifecycle stress, playback diagnostics, memory/thermal snapshots, evidence sanitization and release-package verification. These scripts never convert execution into a manual/physical PASS by themselves.

## Security and storage policy

- SAF/MediaStore remain the normal user-approved local-media access model; no all-files permission is added.
- HTTP/FTP cleartext capability remains explicit because it is a supported user feature, not because TLS validation is disabled.
- HTTPS uses the Android system trust store and normal hostname verification.
- network/cloud/private-vault credential stores are excluded from backup/device-transfer rules.
- private encrypted media remains below no-backup app-private storage and plays through bounded authenticated range decryption rather than a full plaintext temporary movie.
- production signing keys/passwords are never committed.

## Final Step-10 documents

- `STEP_10_COMPLETION_REPORT.md`
- `STEP_10_DEVICE_MATRIX.md`
- `STEP_10_MEDIA_MATRIX.md`
- `STEP_10_PERFORMANCE_REPORT.md`
- `STEP_10_SECURITY_PRIVACY_REPORT.md`
- `STEP_10_RELEASE_CHECKLIST.md`
- `STEP_10_KNOWN_LIMITATIONS.md`
- `PARITY_MATRIX.md`
- `DEPENDENCIES.md`
- `ARCHITECTURE.md`
- `CHANGELOG.md`

## Contribution principle

Do not solve certification failures by deleting requirements, weakening tests, inserting assumptions/skips, hiding errors, disabling TLS verification, replacing real protocol behavior with mocks, or calling unexecuted physical work a PASS. Any reproducible P0/P1 blocks release.

Do not begin Step 11.
