# Changelog

## Unreleased — Step 10 certification work

- Added dedicated exact-SHA Step-10 release-candidate CI covering clean build, unit tests, lint, release APK/AAB, full API-35 instrumentation, strict critical instrumentation, static security checks and package inspection.
- Added sanitized physical-device profiling, install, smoke/stress, diagnostics, memory, thermal and release-package tooling.
- Added Step-10 device/media/performance/security/release/limitations/completion documentation.
- Security hardening: pinned `org.bouncycastle:bcprov-jdk18on` to `1.84` because SMBJ `0.14.0` otherwise requests transitive `1.79`; Step-10 CI verifies the fixed release-runtime resolution and the retained network/SMB regression suite remains mandatory.
- Playback lifecycle hardening retained exact ownership for application-scoped decoder/audio state across service recreation, and external-audio selection remains service-player authoritative.
- Rebuilt the rejected development-style phone presentation as a clean-room Step-10 UI release-hardening pass without redesigning the playback engine: safe-drawing library/player layout, compact media-first library chrome, scrollable primary-source rail, thumbnail media rows, bottom-safe Play action, translucent player controls, progressive/scrollable player tools, right-side Audio and Subtitle panels, centered Decoder modal and right-side More/Tools panel.
- Expanded release UI instrumentation for compact library chrome, progressive search/source navigation, player primary actions, progressive tool rail, visibility/lock behavior and Subtitle/Decoder/More entry points. Lazy source-rail tests scroll to virtualized off-screen destinations rather than assuming every lazy child is precomposed.
- Added explicit physical UI evidence gates for status/cutout/navigation clearance, large font/display size and sanitized screenshots of the folder screen, video list, player controls, extended tool rail, Audio, Subtitle, Decoder and More surfaces.
- Historical pre-UI workflow runs and APK/AAB hashes are no longer treated as final evidence after UI source changes; final software evidence must be bound to the exact immutable UI-hardening PR head.
- No unrelated product feature was added and no release version/tag was invented.
- Full release certification remains blocked until required physical-device evidence is genuinely completed; production signing/publishing has not been performed.
