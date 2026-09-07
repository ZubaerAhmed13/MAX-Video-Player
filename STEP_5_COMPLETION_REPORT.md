# Step 5 Completion Report — Professional Audio Engine

## Boundary
This report covers Step 5 only. Step 6 is not implemented here.

Step 5 builds on the Step-4-certified `main` baseline and preserves the single service-owned Media3 playback architecture, subtitle system, large-media URI model and existing regression coverage.

## Delivered product capabilities
- embedded audio-track discovery and manual selection;
- Auto audio selection with persisted preferred language;
- durable external-audio association, relink/remove and explicit selection;
- external audio merged into the same Media3 timeline;
- Step-4 external subtitle coexistence while external audio is selected;
- production-installed custom PCM DSP;
- 10-band EQ with presets/custom values;
- preamp and digital boost with bounded soft limiting;
- deterministic positive/negative audio delay;
- separate per-route compensation;
- stereo mono/left/right channel modes;
- stereo left/right balance;
- truthful multichannel behavior: 5.1/7.1 layouts are preserved and stereo-only channel/balance controls are disabled;
- pitch control through Media3 playback parameters;
- audio-only playback without replacing the media item/session;
- background modes: pause, continue audio and PiP-when-possible fallback behavior;
- optional background video suppression with foreground restoration;
- route classification for speaker, wired, Bluetooth, USB and HDMI families;
- persistence across Activity recreation.

## Review findings closed
### Multichannel channel/balance truthfulness
Closed. UI and state now explicitly make channel mode and balance stereo-only. Automated DSP coverage verifies a six-channel PCM frame is not remapped by these controls.

### Mandatory DSP test coverage
Closed in source. Unit coverage now includes neutral PCM16, neutral float PCM, EQ selectivity, presets, preamp, boost, limiter, stereo channel mapping, balance, multichannel preservation, positive/negative delay, live parameter revision and long extreme-settings stability.

### External-audio selection certification
Closed in source. API-35 instrumentation now requires both:
1. durable selected external association; and
2. an actually selected external Media3 audio track/group.
It also asserts embedded audio is no longer selected after explicit external-audio selection.

### Background behavior integration
Closed in source. A dedicated lifecycle instrumentation test drives the real `MainActivity`/service-owned player through stopped/started states and verifies continue-audio video suppression, foreground restoration, PiP fallback behavior, pause policy and stable media-item identity.

### Documentation
Closed by:
- `STEP_5_ARCHITECTURE.md`
- `STEP_5_DEPENDENCIES.md`
- `STEP_5_TEST_MATRIX.md`
- `STEP_5_COMPLETION_REPORT.md`

## Automated gate policy
A Step-5 branch is mergeable only when the exact documentation-complete head passes the existing Android CI matrix:
- debug build and JVM tests;
- release compilation;
- lint;
- API-35 full instrumentation;
- API-26 legacy regression instrumentation;
- API-28 legacy regression instrumentation.

The last known green baseline before the final hardening changes was Step-5 head `94e65536ab27c6c734420517310ba2b2bc3696be`, Android CI run `34131831151` (#144), which completed successfully. The final hardening/documentation head must independently pass the same matrix before merge; older green evidence is not substituted for final-head evidence.

## Clean-room / dependency statement
No MX Player proprietary source, assets or branding are used. Step 5 adds no new third-party runtime dependency; the DSP is project-owned Kotlin code integrated through existing AndroidX Media3 audio-sink extension points.

## Physical certification boundary
Automated CI does not claim physical acoustic/latency certification for specific Bluetooth, USB DAC or HDMI hardware, nor manufacturer-specific background-policy behavior. Those remain representative-device checks. The code paths, route policies and automated integration behavior are implemented and tested without pretending emulator evidence proves hardware acoustics.

## Merge rule
Do not mark Step 5 complete on `main` until:
1. final Step-5 branch head is fully green;
2. the Step-5 pull request/branch is merged without dropping commits;
3. `main` CI runs on the merged commit and is fully green.

Only after those gates is Step 5 considered repository-complete and Step 6 eligible to begin.
