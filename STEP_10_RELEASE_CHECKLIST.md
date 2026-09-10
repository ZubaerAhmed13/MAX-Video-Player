# Step 10 Release Checklist

This checklist deliberately separates software evidence from physical evidence.

## Software / repository gates

- [x] Exact latest `main` established before Step 10: `476e5416836cb9e75dbe4c97b9a6ed08c9e02bad`.
- [x] Step-10 branch created from that exact SHA.
- [x] Existing Android CI, Step 8 and Step 9 were green on the baseline before Step-10 work.
- [x] Dedicated `.github/workflows/step10-certification.yml` added with exact checked-out SHA verification.
- [x] Clean debug/release/AAB build, JVM/unit, lint, full API-35 instrumentation, strict instrumentation and package/static audit gates defined.
- [x] Safe reusable physical-device evidence scripts added under `tools/step10/`.
- [x] Current dependency/security review completed; SMBJ's transitive Bouncy Castle provider is pinned from requested `1.79` to fixed `1.84`, with release-runtime resolution checked in Step-10 CI.
- [ ] Exact final Step-10 PR/head workflow all green after the security pin.
- [ ] Final retained Android CI + Step 8 + Step 9 + Step 10 all green on the exact merge candidate.
- [ ] Final APK/AAB SHA-256 recorded from the exact merge candidate.

## Physical release gates

- [ ] Flagship-class physical device.
- [ ] Representative mid-range physical device.
- [ ] Substantially different SoC/vendor implementation.
- [ ] Real >3 GB local source.
- [ ] 4K / high bitrate / 4K60 where supported.
- [ ] HDR-capable physical display where available.
- [ ] Hardcore user interaction stress.
- [ ] Decoder Auto/Hardware/Enhanced Hardware/Software truthfulness.
- [ ] Bluetooth/headset/audio focus/media keys.
- [ ] Real network/NAS: HTTPS/HLS/DASH/RTSP/SMB/WebDAV/FTP/FTPS as claimed.
- [ ] Network interruption and recovery.
- [ ] Real Cast receiver.
- [ ] Real Android TV/Google TV.
- [ ] USB-OTG; removable SD where hardware provides it.
- [ ] Biometric/private-vault physical behavior and >3 GB vault fixture.
- [ ] Screenshot/recording/recents/notification privacy.
- [ ] App update/data migration on device.
- [ ] Memory/resource leak, thermal, battery, wakelock, crash/ANR and endurance tiers.
- [ ] TalkBack/accessibility and screen/window/OEM behavior.

Until the physical section is genuinely executed, the release cannot be labeled `STEP 10: PASS — RELEASE CERTIFIED` and must not receive a final release tag on the basis of Step 10.
