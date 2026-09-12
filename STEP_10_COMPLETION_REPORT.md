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

## Physical UI Release Hardening

### Rejected previous UI and approved direction

The earlier oversized purple-pill development UI was rejected after physical-phone review and is not an approved release interface. The Step-10 hardening pass therefore uses the approved interaction/layout direction only as a clean-room reference: a light, media-first library; compact app bars; horizontal source chips; thumbnail rows; a safe floating Play action; a video-dominant landscape player; restrained translucent overlays; compact quick controls; progressive disclosure for advanced tools; Audio and Subtitle side panels; a centered Decoder modal; and a right-side More/Tools panel. MAX retains its own source, strings, decoder terminology, architecture, branding and implementation.

Reference screenshots are design references only and are not copied into production resources or shipped in APK/AAB artifacts.

### System-inset and responsive strategy

Library and player presentation now use Compose safe-drawing/window-inset-aware layout instead of treating fixed top/bottom padding as a substitute for system bars. Interactive player chrome is kept inside safe regions while video can remain visually dominant. Horizontal source/tool collections use scrollable lazy/rail presentation so phone-width layouts do not clip later actions. Large or advanced tool sets are progressively disclosed instead of permanently consuming video or library space.

The physical acceptance requirement remains broader than emulator/software coverage: gesture navigation, 3-button navigation, display cutouts, increased Android display size, large font, small/normal portrait phones and landscape must still be checked on the exact final APK before physical UI PASS can be claimed.

### Library redesign

The release library presentation provides:

- compact title/search/view/overflow app-bar actions;
- horizontally scrollable Folders, Videos, Private, Network, Cloud, USB and Playlists sources;
- search that appears only when requested and restores the compact app bar on Back;
- overflow-based network URL, sorting/filtering and secondary-history destinations;
- folder/list/grid presentation backed by the existing library state and callbacks;
- 16:9 video thumbnails, duration badges, bounded multi-line titles and concise metadata;
- row menus using only actions supported by the existing source/action architecture;
- bottom-safe floating Play behavior and polished empty/loading/error states.

Scanning, Room state, thumbnail extraction, reference-based media access, file operations and playback authority remain in the existing production layers rather than being moved into the Compose presentation.

### Player redesign

The release player keeps the service-owned Media3/MediaSession path and rebuilds presentation around:

- compact top chrome and title treatment;
- clean timeline and centered transport controls;
- compact semantic quick actions with generally 48dp minimum targets;
- a horizontally scrollable extended tool rail that is progressively disclosed;
- explicit lock/unlock, fullscreen, PiP, aspect/display, orientation and playback-mode access;
- Cast truthfulness: local decoder/display processing remains disabled/labeled when receiver-owned;
- existing auto-hide, gesture, queue, decoder, subtitle and playback state rather than a parallel UI-only state machine.

The UI pass does not redesign the playback engine and does not intentionally weaken Steps 1–9 behavior.

### Panel architecture

- **Audio:** right-side translucent panel connected to the existing production audio controller, with track/external-audio/synchronization/DSP-EQ paths preserved.
- **Subtitle:** right-side translucent panel retaining embedded/external tracks, local/network loading, relink/removal, encoding, timing, discovery/language and appearance controls.
- **Decoder:** centered dark `Select decoder` modal. MAX names remain exactly `Auto`, `Hardware`, `Enhanced Hardware`, and `Software`; requested/effective diagnostics and device capability information remain available.
- **More / Tools:** right-side scrollable panel exposing real Aspect, Speed, Playback, Gestures, Controls, Decoder preferences, Information, Rotate, Orientation, PiP, tutorial and reset actions rather than decorative placeholders.

### Accessibility and input

Primary player actions expose semantic descriptions and retain compact visual treatment without shrinking the intended touch target below the release accessibility target. Side panels are scrollable so controls remain reachable under constrained height/font conditions. Existing TV/D-pad/media-key handling remains separate from the phone presentation and is covered by retained Step-8 certification rather than forcing the phone reference layout onto TV.

Physical TalkBack traversal/focus restoration, large-font behavior and increased display-size behavior remain mandatory physical checks and are not declared PASS here.

### UI test coverage and hardening defects

The retained instrumentation now exercises compact release-library chrome, progressive search, source navigation, playlist creation, network URL disclosure, recreation, player primary actions, control visibility/lock behavior, buffering/HUD behavior, Subtitle/Decoder/More access and progressive extended-player tools.

A certification failure after the redesign exposed a test-harness assumption rather than a missing production feature: `section_usb` is a valid item in a `LazyRow`, but an off-screen lazy item is not guaranteed to be composed. The test was corrected to scroll to the USB and Playlists indices and assert those release-critical destinations visibly. No USB functionality or release requirement was removed to obtain green.

### Release defect classification

The original physical-review defects are retained as release defects until exact-candidate physical evidence closes them:

| Defect | Severity | Software correction | Physical closure |
| --- | --- | --- | --- |
| Status-bar/system-inset overlap or clipped primary controls | P1 | Safe-drawing/inset-aware release layouts implemented | NOT VERIFIED |
| Unusable horizontal navigation/tool overflow | P1/P2 depending reproducibility | Scrollable lazy source rail and progressively disclosed/scrollable player tools implemented | NOT VERIFIED |
| Poor phone visual hierarchy/density | P2 | Media-first compact library/player presentation implemented | NOT VERIFIED |

These defects are **software-corrected but not physically closed**. They must not be marked resolved solely from emulator screenshots or CI.

### Required physical UI evidence

For the exact final candidate APK, capture sanitized physical screenshots of:

1. main folder screen;
2. video-list screen;
3. player controls;
4. expanded tool rail;
5. Audio panel;
6. Subtitle panel;
7. Decoder dialog;
8. More panel.

Also repeat the original failure surfaces under gesture navigation and 3-button navigation where available, increased font/display size, portrait and landscape. Use non-sensitive test media and do not commit personal filenames/credentials.

## Documentation

Updated README, architecture, dependencies, parity matrix, Step-10 device/media/performance/security/checklist/limitations/completion docs, changelog and certification evidence policy during Step 10. The UI-hardening closure additionally records the rejected UI direction, inset/responsive correction, library/player/panel design, accessibility/test strategy and physical UI evidence gate here.

## Software certification evidence

### Historical pre-UI evidence

`8b9fb7e1b9a2e1c322ec663fdb85e999b0356580` was a fully green software candidate before the physical UI release-hardening source changes:

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
- Bouncy Castle release runtime resolution: `bcprov-jdk18on 1.84`.
- Static release audit and package audit: PASS.

Historical reference hashes from that candidate / Step 10 #25 were:

- Debug APK: `d875a6e3e50a5068a35defeb63ce385ca0c6511daf980292289cc8cbb415d04b`
- Unsigned release APK: `12938744a2651a4efdc7802a0414cfc7e9de73003a076d48deaa205b966006a6`
- Release AAB: `612781919b88f43412d35b2f36d496439f7746c1479b06adf6edef7d304797f3`
- Step-10 release evidence ZIP: `0c7a1157e70612e7e4bb6941bf0976d05813215fb6f5cc9c7299a681c7542a72`

**Those hashes and runs are historical only.** Production UI source changes invalidate that release candidate. They must not be quoted as the final Step-10 UI-hardened candidate.

### UI-hardened exact-head certification rule

The authoritative UI-hardened candidate is the exact final PR-head SHA after all source/test/documentation edits stop. That immutable head must independently pass:

- Android CI;
- Step 8 Certification;
- Step 9 Certification;
- Step 10 Certification;
- all newly retained UI instrumentation included in those workflows.

The authoritative SHA, run numbers and regenerated release hashes belong in the PR certification record and exact-SHA workflow artifacts after the final rerun. Hard-coding them into this tracked file after certification would itself create another SHA and invalidate the claim.

No production signing/publishing has been performed.

## Certification failures and resolved root causes

Step 10 deliberately retained failures until their root causes were understood rather than masking them with retries or relaxed tests.

1. Earlier decoder/coexistence failures, including `Auto decoder did not settle`, were initially intermittent across different suite orderings. Later exact-head strict/full runs established that service recreation could overlap application-scoped decoder state. The production fix was engine-generation ownership in `Media3PlaybackEngine`, committed as `b262795c73455b21516c2041980a22855a4a9907`.
2. On `b262795c73455b21516c2041980a22855a4a9907`, Step 8, Step 9 and Step 10 were green, but Android CI's independent full API-35 suite ran 104 tests and failed exactly one: `ProfessionalAudioIntegrationTest.productionAudioPathSupportsTracksExternalAudioDspSyncAudioOnlyAndSubtitleCoexistence`, at `External audio did not become the selected Media3 audio track`.
3. That failure exposed a separate external-audio selection race. `onMediaItemTransition` could see stale track topology, and a Media3 override could be accepted before its selected bit became visible, leaving the controller passively pending with no guaranteed future state-changing event. Production commit `8b9fb7e1b9a2e1c322ec663fdb85e999b0356580` waits for authoritative track topology and uses a bounded one-time override reassertion for the exact pending request.
4. The same Android CI full API-35 environment that failed on `b262795...` passed all 104 tests on `8b9fb7e...`, while Step 8 #212, Step 9 #134 and Step 10 #25 also passed on that exact SHA.
5. During UI-hardening recertification, `MainActivityTest.releaseLibraryChromeRendersWithoutPermanentSearchOrLegacyTopStrip` initially failed because it expected the off-screen USB item in a virtualized `LazyRow` to already exist in the Compose semantics tree. Production code already contained the required USB chip. The test now scrolls to USB and Playlists before visibility assertions; the product requirement and USB path were not removed or bypassed.

No timeout, assertion, skip, application behavior requirement or production feature is intentionally weakened to manufacture a green certification result.

## Tooling defects found/fixed

- Added `grep --` protection so a private-key regex beginning with `-` cannot be parsed as a grep option.
- Package verification resolves `aapt` from Android Build Tools 36.0.0 explicitly rather than assuming PATH exposure.
- Step-10 artifact names/download matching use the exact certified PR-head SHA instead of the synthetic pull-request merge SHA.
- Release UI tests account for Compose lazy-container virtualization by scrolling to off-screen release-critical destinations before asserting visible semantics.

## Physical certification

`NOT VERIFIED — PHYSICAL HARDWARE UNAVAILABLE`. No real phone/tablet, >3 GB source, 4K/HDR/4K60, Bluetooth/headset, NAS, USB/SD, Cast receiver, Android TV, biometric/OEM privacy, endurance/thermal/battery, external-display, physical system-inset, navigation-mode, large-font/display-scale or final UI screenshot result is fabricated.

## P0/P1

No unresolved P0/P1 product defect may be accepted for release. The original system-inset/clipping P1 and horizontal-overflow P1/P2 are software-corrected by the UI-hardening implementation but remain physically unclosed until the exact final candidate is retested on hardware. Any reproducible failure in CI or physical testing blocks release.

## Signing/release

`PRODUCTION SIGNING/PUBLISHING NOT PERFORMED`. No final release tag is authorized while mandatory physical certification is incomplete.

## Status

`STEP 10: PARTIAL — SOFTWARE/CI STATUS IS EXACT-HEAD-DEPENDENT; PHYSICAL CERTIFICATION INCOMPLETE`

Full Step-10 PASS requires both a green exact final PR-head workflow matrix and genuine physical-device evidence for the UI and other mandatory hardware-only requirements. Do not merge based only on historical pre-UI certification.

Do not begin Step 11.
