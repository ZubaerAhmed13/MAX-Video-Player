# MAX Video Player — Step 10 Final Compatibility Matrix

Allowed final statuses: `PASS`, `FAIL`, `PARTIAL`, `NOT SUPPORTED BY DEVICE`, `NOT APPLICABLE`, `NOT VERIFIED`.

Physical/OEM claims are never inferred from emulator/software evidence.

| Area | Software-CI status | Physical status | Current final status | Evidence / boundary |
| --- | --- | --- | --- | --- |
| Build / unit / lint / release | Step-10 exact-head run required | NOT APPLICABLE | PARTIAL | Dedicated Step-10 workflow added; exact current head must be green |
| API-35 full instrumentation | Step-10 exact-head run required | NOT APPLICABLE | PARTIAL | Full retained `connectedDebugAndroidTest`; no skip/Assume accepted |
| Strict critical instrumentation | Step-10 exact-head run required | NOT APPLICABLE | PARTIAL | Named classes must execute non-zero tests and report success |
| Release package audit | Step-10 exact-head run required | NOT APPLICABLE | PARTIAL | APK/AAB hash, metadata, permissions, debuggable/testOnly and secret scan |
| Local playback | Steps 1–9 retained baseline | NOT VERIFIED | PARTIAL | physical real-user flow not executed here |
| 3 GB+ local source | synthetic/Long-safety coverage retained | NOT VERIFIED | PARTIAL | real >3 GB source required physically |
| 4K / high bitrate / 4K60 | capability logic retained | NOT VERIFIED | PARTIAL | device-specific; unsupported hardware may be `NOT SUPPORTED BY DEVICE` |
| HDR / color | metadata/render path retained | NOT VERIFIED | PARTIAL | HDR-capable physical display required; no lab color claim |
| Decoder Auto | Step-6 regressions retained | NOT VERIFIED | PARTIAL | physical requested/effective codec evidence required |
| Decoder Hardware | Step-6 regressions retained | NOT VERIFIED | PARTIAL | must remain genuinely hardware or report unavailable |
| Decoder Enhanced Hardware | Step-6 regressions retained | NOT VERIFIED | PARTIAL | multi-hardware fallback only where device exposes candidates |
| Decoder Software | Step-6 regressions retained | NOT VERIFIED | PARTIAL | must select an actually software-classified candidate or report unavailable |
| Subtitles | Step-4/6/9 regressions retained | NOT VERIFIED | PARTIAL | embedded/external/delay/style/rotation/PiP physical re-test pending |
| Audio / DSP / sync | Step-5/6 regressions retained | NOT VERIFIED | PARTIAL | speaker/wired/USB/Bluetooth/focus/media-key tests pending |
| HTTP/HTTPS/HLS/DASH | Step-7 deterministic integration retained | NOT VERIFIED | PARTIAL | real-network condition testing pending |
| RTSP | Step-7 server integration retained | NOT VERIFIED | PARTIAL | real RTSP source/auth/reconnect pending |
| SMB2/3 | Step-7 Samba integration retained | NOT VERIFIED | PARTIAL | real NAS browsing/seek/restart/change detection pending |
| WebDAV | Step-7 integration retained | NOT VERIFIED | PARTIAL | real endpoint/range/TLS behavior pending |
| FTP / explicit FTPS | Step-7 integration retained | NOT VERIFIED | PARTIAL | real server/TLS certificate validation pending |
| SFTP | NOT IMPLEMENTED | NOT APPLICABLE | NOT APPLICABLE | not aliased to FTP/FTPS |
| Cast | Step-8 software baseline retained | NOT VERIFIED | PARTIAL | real receiver required |
| Android TV | Step-8 software baseline retained | NOT VERIFIED | PARTIAL | real remote/D-pad-only workflow required |
| USB-OTG / removable SD | Step-8 virtual/software baseline retained | NOT VERIFIED | PARTIAL | real removable media/eject/relink required |
| External display | Step-8 policy retained | NOT VERIFIED | PARTIAL | HDMI/USB-C/Miracast where accessible |
| Private Vault crypto/import/playback | Step-9 baseline retained | NOT VERIFIED | PARTIAL | biometric/OEM privacy and real >3 GB vault test pending |
| Settings / sleep timer | Step-9 baseline retained | NOT VERIFIED | PARTIAL | physical lifecycle/notification behavior pending |
| Accessibility | Step-9 semantics/input baseline retained | NOT VERIFIED | PARTIAL | real TalkBack/large-font/D-pad/window tests pending |
| Migration/update/reboot | Room migration tests retained | NOT VERIFIED | PARTIAL | install/update/device reboot persistence pending |
| Memory/resource leaks | tooling added | NOT VERIFIED | PARTIAL | physical baseline/stress/endurance snapshots required |
| Thermal / battery / wakelock | software architecture retained | NOT VERIFIED | PARTIAL | real-device measurements required |
| Crash / ANR / hardcore UX | automated stress/tooling added | NOT VERIFIED | PARTIAL | physical destructive interaction session required |
| OEM background restrictions | NOT APPLICABLE to deterministic CI | NOT VERIFIED | PARTIAL | at least two OEM implementations where practical |

## Preserved software baseline

Before Step 10 began, exact `main` SHA `476e5416836cb9e75dbe4c97b9a6ed08c9e02bad` had green Android CI, Step 8 Certification and Step 9 Certification. Steps 1–9 production architecture remains the baseline; Step 10 does not convert earlier PASS rows into physical claims.

## Release rule

Full `STEP 10: PASS — RELEASE CERTIFIED` requires all mandatory software gates green on the exact candidate, genuine required physical evidence, no unresolved P0/P1, exact release artifact hashes and required merge/post-merge verification. Missing required hardware produces `PARTIAL`, never PASS.
