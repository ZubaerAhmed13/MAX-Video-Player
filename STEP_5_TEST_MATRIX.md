# Step 5 — Professional Audio Test Matrix

This matrix records the exact software/emulator-verifiable Step-5 certification requirements. The expanded signal suite passed on corrective implementation head `826558fdd9ba5eeec1c772aed6307faef2ecb88c`, Android CI #165 / run `34159966506`.

## DSP signal-processing certification

| Requirement | Exact automated evidence | Result |
|---|---|---|
| Neutral PCM16 | Bit-identical valid PCM16 with all controls neutral | PASS |
| Neutral PCM float | Bit-identical valid PCM float with all controls neutral | PASS |
| EQ-enabled Flat | EQ enabled with all ten bands at 0 dB remains transparent within 1e-6 float tolerance | PASS |
| 62 Hz response | +6 dB at the 62 Hz band; measured steady-state output gain must be 5–7 dB | PASS |
| 1 kHz response | +6 dB at the 1 kHz band; measured steady-state output gain must be 5–7 dB | PASS |
| 8 kHz response | +6 dB at the 8 kHz band; measured steady-state output gain must be 5–7 dB | PASS |
| EQ cross-band selectivity | Boost 1 kHz and prove target response exceeds distant 8 kHz response | PASS |
| Nyquist safety | 16 kHz band at 32 kHz sample rate is safely bypassed instead of constructing unstable filter | PASS |
| Preset curves | Every non-Custom preset has exactly ten bounded bands; Bass/Treble directionality verified | PASS |
| Preamp 0 dB | Transparent | PASS |
| Preamp +6 dB | RMS ratio approximately `10^(6/20)` before limiter | PASS |
| Preamp −6 dB | RMS ratio approximately `10^(-6/20)` | PASS |
| Digital boost | Actual PCM amplitude increases | PASS |
| PCM16 limiter | Near-full-scale + extreme legal gain remains in signed 16-bit range | PASS |
| Float limiter | Extreme legal gain remains finite and within ±1.0 | PASS |
| NaN / Infinity safety | Active DSP sanitizes NaN/+Inf/−Inf input so none reaches output | PASS |
| Balance | Full-left/full-right plus 0.5 equal-power attenuation semantics | PASS |
| Mono | Different L/R input produces intended common downmix | PASS |
| Left mode | Left source is duplicated without sign/channel inversion | PASS |
| Right mode | Right source is duplicated without sign/channel inversion | PASS |
| Multichannel truthfulness | Six-channel frame remains six-channel/unremapped under stereo-only mode/balance; UI state disables controls | PASS |
| Delay 0 ms | No sample insertion/removal | PASS |
| Delay +500 ms | 48 kHz stereo: exactly 24,000 leading delayed frames before source begins | PASS |
| Delay −500 ms | 48 kHz stereo: exactly 24,000 leading frames trimmed without underflow crash | PASS |
| Delay bounds | Enormous positive/negative values clamp to ±10,000 ms | PASS |
| Delay buffer flush | Buffered pre-seek samples cannot appear after `flush()` | PASS |
| 44.1 kHz | Active EQ path produces finite/bounded output and real target gain | PASS |
| 48 kHz | Active EQ path produces finite/bounded output and real target gain | PASS |
| 96 kHz | Active EQ path produces finite/bounded output and real target gain | PASS |
| Filter reset | Post-flush EQ output matches a fresh processor for identical probe input | PASS |
| Realtime parameter revision | Atomic revision is consumed without recreating processor | PASS |
| Live-change discontinuity bound | EQ revision boundary remains finite/bounded and avoids an extreme sample jump | PASS |
| DC-offset safety | Active EQ/preamp output mean stays below bounded DC-offset tolerance | PASS |
| Long stability | 30 s deterministic 48 kHz streaming fixture under all +12 dB bands/preamp/boost stays finite/bounded and terminates | PASS |
| Unsupported PCM truthfulness | PCM24 request returns `AudioFormat.NOT_SET`, availability false and a reason instead of byte reinterpretation | PASS |

## Production / integration certification

| Area | Automated evidence | Result |
|---|---|---|
| Embedded multi-audio discovery | API-35 production Media3 instrumentation | PASS |
| Preferred Auto language | Preference reaches Media3 and persists | PASS |
| Manual embedded selection | Selected embedded track confirmed by Media3 state | PASS |
| External audio association | Durable selected association | PASS |
| External audio actual selection | Selected merged external track confirmed through controller-visible Media3 state; embedded audio deselected | PASS |
| External subtitle coexistence | Step-4 external subtitle remains associated after external-audio merge | PASS |
| DSP production installation | Production AudioSink reports DSP installed and realtime parameters update | PASS |
| Pitch | Media3 PlaybackParameters pitch assertion | PASS |
| Audio-only | Video track disabled/restored without replacing media item or restarting position | PASS |
| Activity recreation | Session/audio state/external audio survive recreation | PASS |
| Continue-audio background | Lifecycle integration suppresses video while retaining same session item | PASS |
| Foreground restore | Background-suppressed video returns in real RESUMED foreground state | PASS |
| PiP when possible | API-35 enters real PiP, keeps same session/media, preserves playback and video selection | PASS |
| Pause background policy | Service-owned player pauses without replacing media item | PASS |
| Route classification | Speaker/wired/Bluetooth/USB/HDMI/unknown | PASS |
| Room v3→v4 | Step-1–4 data survives and new audio tables are usable | PASS |
| API 26 regression | Legacy thumbnail instrumentation | PASS |
| API 28 regression | Legacy thumbnail instrumentation | PASS |

## Corrective implementation gate

Exact source/evidence head:

- SHA: `826558fdd9ba5eeec1c772aed6307faef2ecb88c`
- Android CI: #165
- run: `34159966506`
- debug build + all JVM/unit/DSP tests — **PASS**
- release compilation — **PASS**
- lint — **PASS**
- API-35 full instrumentation — **PASS**
- API-26 legacy regression — **PASS**
- API-28 legacy regression — **PASS**

Because this file and the completion report record those results after the gate, the resulting documentation-complete PR head must pass the identical matrix once more before merge. Older green evidence is not substituted for that final-head run.

## Merged Step-5 baseline before corrective audit

- merge commit `bea360164fe69cfc146dc03b55df4da4a0cb153a`
- Android CI #163 / run `34158185652`
- all four jobs passed

## Physical-device items not inferred from CI

The following remain physical/hardware certification rather than emulator claims:

- actual Bluetooth latency and route-switch quality
- USB DAC behavior across representative hardware
- HDMI receiver behavior
- subjective EQ/limiter listening quality and crackle perception
- manufacturer-specific background restrictions
- physical very-large/4K/HDR playback, battery and thermal behavior

These remain **NOT VERIFIED — DEFERRED TO STEP 10**.