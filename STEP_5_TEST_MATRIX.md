# Step 5 — Professional Audio Test Matrix

This matrix records the exact software/emulator-verifiable Step-5 certification requirements. A source-level test is not considered certified until the exact PR/final head passes the normal Android CI matrix.

## DSP signal-processing certification

| Requirement | Exact automated evidence | Status on this correction branch |
|---|---|---|
| Neutral PCM16 | Bit-identical valid PCM16 with all controls neutral | REQUIRED CI GATE |
| Neutral PCM float | Bit-identical valid PCM float with all controls neutral | REQUIRED CI GATE |
| EQ-enabled Flat | EQ enabled with all ten bands at 0 dB remains transparent within 1e-6 float tolerance | REQUIRED CI GATE |
| 62 Hz response | +6 dB at the 62 Hz band; measured steady-state output gain must be 5–7 dB | REQUIRED CI GATE |
| 1 kHz response | +6 dB at the 1 kHz band; measured steady-state output gain must be 5–7 dB | REQUIRED CI GATE |
| 8 kHz response | +6 dB at the 8 kHz band; measured steady-state output gain must be 5–7 dB | REQUIRED CI GATE |
| EQ cross-band selectivity | Boost 1 kHz and prove target response exceeds distant 8 kHz response | REQUIRED CI GATE |
| Nyquist safety | 16 kHz band at 32 kHz sample rate is safely bypassed instead of constructing unstable filter | REQUIRED CI GATE |
| Preset curves | Every non-Custom preset has exactly ten bounded bands; Bass/Treble directionality verified | REQUIRED CI GATE |
| Preamp 0 dB | Transparent | REQUIRED CI GATE |
| Preamp +6 dB | RMS ratio approximately `10^(6/20)` before limiter | REQUIRED CI GATE |
| Preamp −6 dB | RMS ratio approximately `10^(-6/20)` | REQUIRED CI GATE |
| Digital boost | Actual PCM amplitude increases | REQUIRED CI GATE |
| PCM16 limiter | Near-full-scale + extreme legal gain remains in signed 16-bit range | REQUIRED CI GATE |
| Float limiter | Extreme legal gain remains finite and within ±1.0 | REQUIRED CI GATE |
| NaN / Infinity safety | Active DSP sanitizes NaN/+Inf/−Inf input so none reaches output | REQUIRED CI GATE |
| Balance | Full-left/full-right plus 0.5 equal-power attenuation semantics | REQUIRED CI GATE |
| Mono | Different L/R input produces intended common downmix | REQUIRED CI GATE |
| Left mode | Left source is duplicated without sign/channel inversion | REQUIRED CI GATE |
| Right mode | Right source is duplicated without sign/channel inversion | REQUIRED CI GATE |
| Multichannel truthfulness | Six-channel frame remains six-channel/unremapped under stereo-only mode/balance; UI state disables controls | REQUIRED CI GATE |
| Delay 0 ms | No sample insertion/removal | REQUIRED CI GATE |
| Delay +500 ms | 48 kHz stereo: exactly 24,000 leading delayed frames before source begins | REQUIRED CI GATE |
| Delay −500 ms | 48 kHz stereo: exactly 24,000 leading frames trimmed without underflow crash | REQUIRED CI GATE |
| Delay bounds | Enormous positive/negative values clamp to ±10,000 ms | REQUIRED CI GATE |
| Delay buffer flush | Buffered pre-seek samples cannot appear after `flush()` | REQUIRED CI GATE |
| 44.1 kHz | Active EQ path produces finite/bounded output and real target gain | REQUIRED CI GATE |
| 48 kHz | Active EQ path produces finite/bounded output and real target gain | REQUIRED CI GATE |
| 96 kHz | Active EQ path produces finite/bounded output and real target gain | REQUIRED CI GATE |
| Filter reset | Post-flush EQ output matches a fresh processor for identical probe input | REQUIRED CI GATE |
| Realtime parameter revision | Atomic revision is consumed without recreating processor | REQUIRED CI GATE |
| Live-change discontinuity bound | EQ revision boundary remains finite/bounded and avoids an extreme sample jump | REQUIRED CI GATE |
| DC-offset safety | Active EQ/preamp output mean stays below bounded DC-offset tolerance | REQUIRED CI GATE |
| Long stability | 30 s deterministic 48 kHz streaming fixture under all +12 dB bands/preamp/boost stays finite/bounded and terminates | REQUIRED CI GATE |
| Unsupported PCM truthfulness | PCM24 request returns `AudioFormat.NOT_SET`, availability false and a reason instead of byte reinterpretation | REQUIRED CI GATE |

## Production / integration certification

| Area | Automated evidence | Status before this correction merge |
|---|---|---|
| Embedded multi-audio discovery | API-35 production Media3 instrumentation | PASS on merged Step-5 baseline |
| Preferred Auto language | Preference reaches Media3 and persists | PASS on merged Step-5 baseline |
| Manual embedded selection | Selected embedded track confirmed by Media3 state | PASS on merged Step-5 baseline |
| External audio association | Durable selected association | PASS on merged Step-5 baseline |
| External audio actual selection | Selected merged external track confirmed through controller-visible Media3 state; embedded audio deselected | PASS on merged Step-5 baseline |
| External subtitle coexistence | Step-4 external subtitle remains associated after external-audio merge | PASS on merged Step-5 baseline |
| DSP production installation | Production AudioSink reports DSP installed and realtime parameters update | PASS on merged Step-5 baseline |
| Pitch | Media3 PlaybackParameters pitch assertion | PASS on merged Step-5 baseline |
| Audio-only | Video track disabled/restored without replacing media item or restarting position | PASS on merged Step-5 baseline |
| Activity recreation | Session/audio state/external audio survive recreation | PASS on merged Step-5 baseline |
| Continue-audio background | Lifecycle integration suppresses video while retaining same session item | PASS on merged Step-5 baseline |
| Foreground restore | Background-suppressed video returns in real RESUMED foreground state | PASS on merged Step-5 baseline |
| PiP when possible | API-35 enters real PiP, keeps same session/media, preserves playback and video selection | PASS on merged Step-5 baseline |
| Pause background policy | Service-owned player pauses without replacing media item | PASS on merged Step-5 baseline |
| Route classification | Speaker/wired/Bluetooth/USB/HDMI/unknown | PASS on merged Step-5 baseline |
| Room v3→v4 | Step-1–4 data survives and new audio tables are usable | PASS on merged Step-5 baseline |
| API 26 regression | Legacy thumbnail instrumentation | PASS on merged Step-5 baseline |
| API 28 regression | Legacy thumbnail instrumentation | PASS on merged Step-5 baseline |

Merged Step-5 baseline evidence:

- merge commit `bea360164fe69cfc146dc03b55df4da4a0cb153a`
- Android CI #163 / run `34158185652`
- all four jobs passed

## Required CI for this certification-hardening change

The exact PR/final head must pass:

```text
:app:assembleDebug
:app:testDebugUnitTest
:app:assembleRelease
:app:lintDebug
:app:connectedDebugAndroidTest
API-26 legacy thumbnail instrumentation
API-28 legacy thumbnail instrumentation
```

Do not weaken/remove tests, suppress lint or rely on an older green run if the exact final head fails.

## Physical-device items not inferred from CI

The following remain physical/hardware certification rather than emulator claims:

- actual Bluetooth latency and route-switch quality
- USB DAC behavior across representative hardware
- HDMI receiver behavior
- subjective EQ/limiter listening quality and crackle perception
- manufacturer-specific background restrictions
- physical very-large/4K/HDR playback, battery and thermal behavior

These remain **NOT VERIFIED — DEFERRED TO STEP 10**.