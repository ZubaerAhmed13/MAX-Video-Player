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

An independent follow-up audit then identified two defects in the repository-complete claim:

1. the canonical root documentation (`README.md`, `ARCHITECTURE.md`, `DEPENDENCIES.md`, `PARITY_MATRIX.md`) still described Step 4;
2. the DSP unit suite did not yet implement every exact signal test required by the original Step-5 specification.

Both source-level findings are now corrected in PR #8 / branch `step-5-canonical-docs-dsp-certification`.

## Corrective implementation gate

The exact corrective implementation/evidence head `826558fdd9ba5eeec1c772aed6307faef2ecb88c` passed Android CI #165 / run `34159966506`:

- debug build + all JVM/unit/DSP tests — **PASS**
- release compilation — **PASS**
- lint — **PASS**
- API-35 full instrumentation — **PASS**
- API-26 legacy regression — **PASS**
- API-28 legacy regression — **PASS**

The final documentation commits that record this evidence necessarily move the PR head. Therefore the resulting documentation-complete head must pass the identical matrix once more before PR #8 is merged, followed by post-merge `main` CI.

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

**CLOSED.** UI/state make channel mode and balance stereo-only. Automated DSP coverage verifies a six-channel PCM frame is not remapped by those controls.

## Review finding: external-audio actual selection

**CLOSED.** API-35 instrumentation requires both a durable selected association and an actually selected merged external Media3 audio track. It also asserts embedded audio is no longer selected after explicit external-audio selection.

## Review finding: background behavior integration

**CLOSED.** Dedicated API-35 lifecycle instrumentation verifies Continue-audio session retention/video suppression, foreground video restoration, Pause policy, actual Picture-in-Picture behavior and stable MediaSession/current-media identity.

## Review finding: canonical project documentation

**CLOSED IN SOURCE; FINAL-HEAD CI/MERGE PENDING.**

Canonical files are now genuinely through Step 5:

- `README.md`
- `ARCHITECTURE.md`
- `DEPENDENCIES.md`
- `PARITY_MATRIX.md`

The Step-specific files remain supporting evidence rather than substitutes for the canonical root documents.

## Review finding: exact DSP certification matrix

**CLOSED IN SOURCE AND PASSED CI #165; FINAL DOCUMENTATION-HEAD CI/MERGE PENDING.**

The expanded JVM DSP suite directly verifies:

- neutral PCM16 bit transparency
- neutral PCM-float transparency
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

See `STEP_5_TEST_MATRIX.md` for exact assertions and evidence.

## Clean-room / dependency statement

No MX Player proprietary source, assets or branding are used. Step 5 adds no new third-party runtime dependency; the DSP is project-owned Kotlin code integrated through AndroidX Media3 audio-sink extension points.

## Physical certification boundary

Automated CI does not claim physical acoustic/latency certification for specific Bluetooth, USB DAC or HDMI hardware, subjective listening quality, manufacturer-specific background restrictions, or physical very-large/4K/HDR/battery/thermal behavior.

Those remain:

**NOT VERIFIED — DEFERRED TO STEP 10**

## Final corrective merge rule

PR #8 may merge only when:

1. the exact documentation-complete PR head passes debug/JVM/DSP tests, release compilation, lint, API-35, API-26 and API-28;
2. PR #8 is merged without dropping the corrective changes;
3. `main` runs the same CI on the resulting merge commit and is fully green.

No Step-6 work may be substituted for these Step-5 corrections.