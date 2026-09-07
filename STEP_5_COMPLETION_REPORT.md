# Step 5 Completion Report — Professional Audio Engine

## Boundary

This report covers Step 5 only. Step 6 is not implemented here.

Step 5 extends the Step-4-certified application while preserving the single service-owned Media3 playback architecture, subtitle system, large-media URI model and earlier regression coverage.

## Merged Step-5 baseline

Step 5 was merged through PR #7.

- merged `main` commit: `bea360164fe69cfc146dc03b55df4da4a0cb153a`
- post-merge Android CI: #163 / run `34158185652`
- debug + JVM/unit tests: **PASS**
- release compilation: **PASS**
- lint: **PASS**
- API-35 full instrumentation: **PASS**
- API-26 regression: **PASS**
- API-28 regression: **PASS**

An independent follow-up audit identified two documentation/certification defects in that repository-complete claim:

1. the canonical root documentation (`README.md`, `ARCHITECTURE.md`, `DEPENDENCIES.md`, `PARITY_MATRIX.md`) still described Step 4;
2. the DSP unit suite did not yet implement every exact signal test required by the original Step-5 specification.

Those findings are addressed by PR #8 / branch `step-5-canonical-docs-dsp-certification`. That corrective change must pass the complete exact-head CI matrix and post-merge `main` CI before this report treats the audit as closed.

## Delivered product capabilities

- embedded audio-track discovery and manual selection
- Auto audio selection with persisted preferred language
- durable external-audio association, relink/remove and explicit selection
- external audio merged into the same Media3 timeline
- actual external Media3 track-selection certification
- Step-4 external subtitle coexistence while external audio is selected
- production-installed custom PCM DSP
- PCM16 and PCM-float paths
- 10-band EQ with presets/custom values
- preamp and digital boost with bounded soft limiting
- deterministic positive/negative audio delay
- separate per-route compensation
- stereo mono/left/right channel modes
- stereo left/right balance
- truthful multichannel behavior: 5.1/7.1 layouts are preserved and stereo-only channel/balance controls are disabled
- pitch control through Media3 playback parameters
- audio-only playback without replacing the media item/session
- background modes: Pause, Continue audio and PiP when possible
- optional background video suppression with foreground restoration
- route classification for speaker, wired, Bluetooth, USB and HDMI families
- persistence across Activity recreation
- Room v4 with explicit `MIGRATION_3_4`

## Review finding: multichannel channel/balance truthfulness

**Closed.** UI/state make channel mode and balance stereo-only. Automated DSP coverage verifies a six-channel PCM frame is not remapped by those controls.

## Review finding: external-audio actual selection

**Closed.** API-35 instrumentation requires both:

1. durable selected external association; and
2. an actually selected merged external Media3 audio track.

It also asserts embedded audio is no longer selected after explicit external-audio selection.

## Review finding: background behavior integration

**Closed.** Dedicated API-35 lifecycle instrumentation verifies:

- Continue-audio session retention and optional video suppression
- real foreground video restoration
- Pause policy
- actual Picture-in-Picture behavior
- stable MediaSession/current-media identity

## Review finding: canonical project documentation

**Corrective source change implemented in PR #8; CI/merge gate pending.**

Canonical files are rewritten through Step 5:

- `README.md`
- `ARCHITECTURE.md`
- `DEPENDENCIES.md`
- `PARITY_MATRIX.md`

The step-specific documents remain supporting evidence, not substitutes for the canonical root files.

## Review finding: exact DSP certification matrix

**Corrective source change implemented in PR #8; CI/merge gate pending.**

The expanded JVM DSP suite now contains explicit tests for the requirements that were previously only partial or implicit:

- EQ-enabled Flat transparency
- +6 dB measured response at 62 Hz, 1 kHz and 8 kHz with defined tolerance
- EQ cross-band selectivity
- Nyquist safety
- preamp −6/0/+6 dB expected gain/attenuation
- real digital boost
- PCM16 and float limiter bounds
- active-DSP NaN/Infinity sanitization
- mathematical stereo balance
- mono/left/right routing without channel inversion
- multichannel preservation
- 0 ms delay
- +500 ms delay at 48 kHz stereo
- −500 ms delay at 48 kHz stereo
- enormous delay clamping
- delay-buffer flush/seek stale-sample rejection
- 44.1 kHz, 48 kHz and 96 kHz processing
- filter-history reset after flush
- live atomic parameter revision without processor recreation
- bounded live-change discontinuity
- DC-offset and numerical-output safety
- 30-second deterministic extreme-settings streaming stability
- truthful unsupported-PCM rejection

See `STEP_5_TEST_MATRIX.md` for exact assertions.

## Clean-room / dependency statement

No MX Player proprietary source, assets or branding are used. Step 5 adds no new third-party runtime dependency; the DSP is project-owned Kotlin code integrated through AndroidX Media3 audio-sink extension points.

## Physical certification boundary

Automated CI does not claim physical acoustic/latency certification for specific Bluetooth, USB DAC or HDMI hardware, subjective listening quality, manufacturer-specific background restrictions, or physical very-large/4K/HDR/battery/thermal behavior.

Those remain:

**NOT VERIFIED — DEFERRED TO STEP 10**

## Corrective merge rule

Do not close the two follow-up audit findings until:

1. PR #8 exact head passes debug/JVM/DSP tests, release compilation, lint, API-35, API-26 and API-28;
2. PR #8 is merged without dropping the corrective changes;
3. `main` runs the same CI on the resulting merge commit and is fully green.

No Step-6 work may be substituted for these Step-5 corrections.