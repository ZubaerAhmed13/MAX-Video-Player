# Step 5 — Professional Audio Test Matrix

| Area | Automated evidence | Status before final merge |
|---|---|---|
| Neutral PCM16 | Bit-transparent unit test | PASS target |
| Neutral PCM float | Bit-transparent unit test | PASS target |
| 10-band EQ | Target-frequency gain vs distant-frequency comparison | PASS target |
| EQ presets | Ten-band bounded curves and directional bass/treble checks | PASS target |
| Preamp | Real PCM amplitude increase | PASS target |
| Digital boost | Real PCM amplitude increase | PASS target |
| Limiter | Extreme legal gain remains bounded | PASS target |
| Stereo mono/left/right | Real channel remapping | PASS target |
| Stereo balance | Full-left/full-right attenuation semantics | PASS target |
| Multichannel truthfulness | 5.1 frame preserved under stereo-only channel/balance settings; UI state reports controls unavailable | PASS target |
| Positive audio delay | Leading silence/sample offset semantics | PASS target |
| Negative audio delay | Deterministic leading-frame skip semantics | PASS target |
| Realtime DSP changes | Atomic parameter revision applied without recreating processor | PASS target |
| Embedded multi-audio discovery | API-35 production Media3 instrumentation | PASS target |
| Preferred Auto language | Preference reaches Media3 track-selection parameters and persists | PASS target |
| Manual embedded selection | Selected track confirmed by Media3 state | PASS target |
| External audio association | Durable selected association | PASS target |
| External audio actual selection | Selected external track confirmed in repository track state and Media3 selected track group | PASS target |
| Step-4 subtitle coexistence | External subtitle remains associated after external audio merge | PASS target |
| DSP production installation | Production AudioSink reports DSP installed and realtime parameters update | PASS target |
| Pitch | Media3 PlaybackParameters pitch assertion | PASS target |
| Audio-only | Video track disabled/restored without replacing media item or restarting position | PASS target |
| Activity recreation | Session/audio state/external audio survive recreation | PASS target |
| Continue-audio background | Lifecycle integration suppresses video while retaining same session item | PASS target |
| Foreground restore | Lifecycle integration restores background-suppressed video in the real RESUMED foreground state | PASS target |
| PiP when possible | API-35 integration enters real Picture-in-Picture, keeps the same MediaSession item, preserves playback intent, and keeps video selected | PASS target |
| Non-PiP fallback mechanism | Continue-audio stopped-state certification exercises the same video-suppression/session-preservation path used when PiP cannot be entered | PASS target |
| Pause background policy | Lifecycle integration pauses service-owned player and retains same media item | PASS target |
| Route classification | Speaker/wired/Bluetooth/USB/HDMI/unknown mapping | PASS target |
| API 26 regression | Existing legacy thumbnail instrumentation | PASS target |
| API 28 regression | Existing legacy thumbnail instrumentation | PASS target |
| Debug/JVM | Build + unit tests | PASS target |
| Release compilation | Release compile gate | PASS target |
| Lint | Android lint gate | PASS target |
| API 35 | Full instrumentation suite | PASS target |

## Physical-device items not inferred from CI
The following remain physical/hardware certification items rather than emulator claims:
- actual Bluetooth device latency and route switching quality;
- USB DAC behavior across representative devices;
- HDMI receiver behavior;
- subjective EQ/limiter listening quality;
- manufacturer-specific background restrictions;
- very large/long media physical playback already tracked by the large-media audit.

These do not reduce the implemented Step-5 feature set; they are honest limits on what automated CI can prove.
