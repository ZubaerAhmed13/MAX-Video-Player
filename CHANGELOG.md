# Changelog

## Unreleased — Step 10 certification work

- Added dedicated exact-SHA Step-10 release-candidate CI covering clean build, unit tests, lint, release APK/AAB, full API-35 instrumentation, strict critical instrumentation, static security checks and package inspection.
- Added sanitized physical-device profiling, install, smoke/stress, diagnostics, memory, thermal and release-package tooling.
- Added Step-10 device/media/performance/security/release/limitations/completion documentation.
- Security hardening: pinned `org.bouncycastle:bcprov-jdk18on` to `1.84` because SMBJ `0.14.0` otherwise requests transitive `1.79`; Step-10 CI verifies the fixed release-runtime resolution and the retained network/SMB regression suite remains mandatory.
- No unrelated product feature was added and no release version/tag was invented.
- Full release certification remains blocked until required physical-device evidence is genuinely completed.
