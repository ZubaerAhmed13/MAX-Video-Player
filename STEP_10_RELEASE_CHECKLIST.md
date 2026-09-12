# Step 10 Release Checklist

This checklist deliberately separates static repository work, exact-head software evidence and physical evidence.

## Static software / repository gates

- [x] Exact latest `main` established before Step 10: `476e5416836cb9e75dbe4c97b9a6ed08c9e02bad`.
- [x] Step-10 branch created from that exact SHA.
- [x] Existing Android CI, Step 8 and Step 9 were green on the baseline before Step-10 work.
- [x] Dedicated `.github/workflows/step10-certification.yml` added with exact checked-out SHA verification.
- [x] Clean debug/release/AAB build, JVM/unit, lint, full API-35 instrumentation, strict instrumentation and package/static audit gates defined.
- [x] Safe reusable physical-device evidence scripts added under `tools/step10/`.
- [x] Current dependency/security review completed; SMBJ's transitive Bouncy Castle provider is pinned from requested `1.79` to fixed `1.84`, with release-runtime resolution checked in Step-10 CI.
- [x] Physical-review-driven UI release hardening implemented without redesigning the service-owned playback engine.
- [x] Release library uses safe-drawing/inset-aware layout, compact progressive chrome, scrollable primary-source rail, thumbnail media rows and bottom-safe Play action.
- [x] Release player uses safe interactive regions, compact translucent controls, centered transport, progressive/scrollable tools, Audio/Subtitle side panels, centered Decoder modal and More/Tools panel.
- [x] Release UI instrumentation is retained in Step-10 strict certification and does not rely on zero-test/skip/assumption escape paths.
- [x] Step-10 UI hardening, device matrix, known limitations and completion documentation explicitly preserve the physical verification boundary.

## Exact final software evidence — PR certification record is authoritative

Do **not** permanently check exact-head workflow/hash claims inside this Git-tracked file. Editing the file after certification creates a different Git SHA and would invalidate the claim being recorded.

For the immutable final PR head, the PR certification record must show all of the following before software certification can be called green:

- Android CI: PASS on the exact final SHA.
- Step 8 Certification: PASS on the exact final SHA.
- Step 9 Certification: PASS on the exact final SHA.
- Step 10 Certification: PASS on the exact final SHA.
- Required release/UI instrumentation: non-zero tests, no skips, no failures.
- Fresh debug APK, unsigned release APK and release AAB generated from that exact SHA.
- SHA-256 values recorded from the exact-head Step-10 release evidence.
- Step-10 static release audit/package audit/security-resolution checks: PASS.
- No unresolved reproducible P0/P1 software defect.

Historical pre-UI workflow runs and hashes are regression evidence only after production UI source changes; they are not final evidence for the UI-hardened candidate.

## Physical UI release gates

- [ ] Install the exact software-certified UI-hardening APK on a physical phone.
- [ ] Main folder screen: no status-bar/cutout overlap; compact app bar; primary-source rail scrolls; Play action clears navigation region.
- [ ] Video-list screen: 16:9 thumbnails, duration badges, bounded titles/metadata, accessible row menus, smooth scrolling.
- [ ] Player controls: video-dominant layout, compact safe top chrome, clean timeline, centered transport, auto-hide/Back work.
- [ ] Expanded tool rail: horizontally scrollable with no clipped/inaccessible action.
- [ ] Audio panel: right-side panel usable and scrollable with production track/external-audio/sync/DSP-EQ controls.
- [ ] Subtitle panel: right-side panel usable and scrollable with production embedded/external/timing/style controls.
- [ ] Decoder dialog: centered and usable; labels remain `Auto`, `Hardware`, `Enhanced Hardware`, `Software`.
- [ ] More panel: right-side tools remain reachable and scrollable.
- [ ] Capture sanitized exact-candidate screenshots of all eight UI surfaces above using non-sensitive test media.
- [ ] Gesture-navigation clearance verified.
- [ ] 3-button-navigation clearance verified where available.
- [ ] Large font verified without inaccessible critical controls.
- [ ] Increased Android display size verified, not only `fontScale`.
- [ ] Portrait → landscape → portrait behavior verified without system-bar collision or broken state.
- [ ] TalkBack critical labels/traversal and panel reachability verified.

## Other physical release gates

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

Until the physical sections are genuinely executed, the release cannot be labeled `STEP 10: PASS — RELEASE CERTIFIED` and must not receive a final release tag on the basis of Step 10. PR #23 remains draft/unmerged while those mandatory physical gates are incomplete.
