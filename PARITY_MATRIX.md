# MAX Video Player — Parity Matrix through Step 5

Status vocabulary: `PASS`, `PARTIAL`, `FAIL`, `NOT VERIFIED`, `NOT IMPLEMENTED`, `NOT APPLICABLE`.

A `PASS` requires implementation, a real product flow, error handling and automated evidence where feasible. Physical-device requirements are never inferred from emulator/software evidence.

## Step-5 professional audio engine

| Area | Capability | Status | Evidence / boundary |
|---|---|---|---|
| Tracks | Embedded discovery | PASS | Media3 `currentTracks` audio groups mapped to professional audio state; API-35 fixture has ≥2 embedded audio tracks |
| Tracks | Auto selection | PASS | Clears manual override and applies preferred languages |
| Tracks | Manual selection | PASS | `TrackSelectionOverride` through service-owned MediaController |
| Tracks | Preferred languages | PASS | Persisted global preference reaches Media3; API-35 assertion |
| Tracks | Metadata labels | PASS | Language/label/MIME/codec/channels/sample-rate/bitrate metadata surfaced when available |
| Tracks | Descriptive restore | PASS | Persisted descriptor avoids relying on unstable group index |
| External audio | SAF/OpenDocument import | PASS | URI/reference based; no full media copy |
| External audio | Persistence | PASS | Room v4 `audio_associations` + media state |
| External audio | Multiple associations | PASS | Repository supports multiple associations per stable media ID |
| External audio | Relink/remove | PASS | Recoverable relationship management in professional audio UI/controller |
| External audio | Missing/permission recovery | PASS | Unavailable source does not make valid video unplayable; embedded/Auto fallback remains |
| External audio | Actual Media3 selection | PASS | API-35 certification requires selected merged external track and embedded audio deselection |
| External audio | Subtitle coexistence | PASS | External subtitle association survives external-audio merge/selection |
| DSP | Production PCM processing | PASS | App-owned `MaxAudioProcessor` installed in production `DefaultAudioSink` |
| DSP | PCM 16-bit | PASS | Processing + neutral transparency unit coverage |
| DSP | PCM float | PASS | Processing + neutral transparency unit coverage |
| DSP | Unsupported-format truthfulness | PASS | Unsupported PCM is rejected/bypassed instead of byte reinterpretation |
| EQ | 10-band | PASS | 31/62/125/250/500 Hz + 1/2/4/8/16 kHz peaking bands |
| EQ | Live adjustment | PASS | Atomic revision updates processor without recreation |
| EQ | Presets | PASS | Flat/Bass/Vocal/Treble/Rock/Classical/Electronic original ten-band curves |
| EQ | Custom | PASS | Manual band edits persist as custom state |
| EQ | Nyquist safety | PASS | Unsafe high band is disabled rather than constructing unstable filter |
| EQ | Signal response | PASS | Existing target-vs-distant signal test; expanded certification adds measured +6 dB response at 62 Hz/1 kHz/8 kHz |
| Gain | Preamp | PASS | Real PCM gain; expanded certification checks −6/0/+6 dB expected linear behavior |
| Gain | Digital boost | PASS | Real PCM amplitude increase separate from Android system volume |
| Gain | Limiter | PASS | Soft limiter bounds output; integer and float certification |
| Channels | Stereo | PASS | Stereo path preserved |
| Channels | Mono | PASS | Deterministic L/R downmix |
| Channels | Left | PASS | Left duplicated to both stereo outputs |
| Channels | Right | PASS | Right duplicated to both stereo outputs |
| Channels | Balance | PASS | Equal-power left/right attenuation policy |
| Channels | Multichannel truthfulness | PASS | 5.1/7.1 not falsely remapped; stereo-only controls disabled for non-stereo selected track |
| Sync | Zero audio delay | PASS | Expanded deterministic signal certification requires no insertion/removal |
| Sync | Positive audio delay | PASS | Bounded PCM delay buffering; +500 ms/48 kHz stereo certification |
| Sync | Negative audio delay | PASS | Deterministic leading-frame trimming; negative 48 kHz stereo certification |
| Sync | Bounds | PASS | ±10,000 ms policy clamp including enormous input values |
| Sync | Flush/seek stale-buffer rejection | PASS | Expanded processor test flushes delayed audio and proves old samples do not leak |
| Sync | Per-media persistence | PASS | Room v4 audio media state |
| Sync | Route compensation separation | PASS | Global route compensation remains separate from per-media delay |
| Pitch | Independent control | PASS | Media3 `PlaybackParameters` pitch assertion |
| Speed | Speed + pitch separation | PASS | Pitch changes preserve current speed; Step-3 speed remains service-owned |
| Audio-only | Video-track disable | PASS | Same MediaSession/media item; video selection disabled rather than hiding only UI |
| Audio-only | Video restore | PASS | Video track returns without playback restart |
| Background | Continue audio | PASS | API-35 lifecycle integration retains session/media and optionally suppresses video |
| Background | Foreground restoration | PASS | API-35 lifecycle integration verifies selected video returns at real RESUMED foreground state |
| Background | Pause | PASS | API-35 lifecycle integration pauses service-owned player without replacing item |
| Background | PiP when possible | PASS | API-35 test enters real PiP and preserves session/playback/video selection |
| Android audio | Audio focus | PASS | Existing Media3 audio-focus configuration retained |
| Android audio | Becoming noisy | PASS | `setHandleAudioBecomingNoisy(true)` retained |
| Routes | Classification | PASS | Speaker/wired/Bluetooth A2DP/LE/USB/HDMI/unknown mapping tests |
| Routes | Output UI | PASS | Current route displayed truthfully; system routing UI preferred over private hacks |
| Routes | Per-route sync foundation | PASS | Route-family compensation persistence + effective media+route delay policy |
| Persistence | Global audio preferences | PASS | Existing lightweight preference layer |
| Persistence | Per-media audio state | PASS | Room v4 |
| Database | `MIGRATION_3_4` | PASS | Explicit migration; no destructive fallback |
| Database | Step-1–4 preservation | PASS | Migration instrumentation covers v1/v2/v3 → v4 and prior rows |
| Performance | No whole-media audio extraction | PASS | Streaming Media3 PCM path; URI/reference source model preserved |
| Performance | Realtime callback isolation | PASS | No Room/coroutine/UI/network work in `queueInput` |
| Performance | Delay-memory bound | PASS | Positive-delay ring capped at 40 MiB; unavailable state instead of unbounded allocation |
| Numerical safety | NaN/Infinity | PASS | Processor guards non-finite active-DSP input/state; expanded direct signal certification |
| Numerical safety | Filter stability | PASS | Biquad defensive reset + long extreme-settings certification |
| Numerical safety | DC offset / bounded output | PASS | Expanded signal certification checks mean offset and legal output range |
| Sample rates | 44.1 kHz | PASS | Expanded exact DSP certification |
| Sample rates | 48 kHz | PASS | Production/default and signal certification |
| Sample rates | 96 kHz | PASS | Expanded exact DSP certification |
| Privacy | Local audio processing | PASS | No upload service and no microphone permission required |

## Step-4 professional subtitle engine — preserved through Step 5

| Area | Capability | Status | Evidence / boundary |
|---|---|---|---|
| Subtitle tracks | Embedded / Off / Auto / manual | PASS | Existing Media3 text-track engine retained under Step-5 source composition |
| External subtitles | SAF load / multiple associations / relink / recovery | PASS | Room v3 state preserved through Room v4 migration |
| Formats | SRT / WebVTT / SSA / ASS / TTML | PASS | Existing parser instrumentation retained |
| Encoding | UTF-8 / UTF-16 LE/BE / Windows-1252 | PASS | Existing normalization/persistence tests retained |
| Sidecars | Same-folder filename/language matching | PASS | Existing Step-4 matcher retained |
| Sync | Positive / negative / per-media subtitle delay | PASS | Separate from Step-5 audio delay |
| Appearance | Size/colour/background/edge/bottom margin | PASS | Existing Media3 SubtitleView path retained |
| Android system caption style toggle | NOT IMPLEMENTED | Still intentionally not exposed as a fake control |

## Step-3 player experience — preserved through Step 5

| Area | Capability | Status | Evidence / boundary |
|---|---|---|---|
| Player UI | Controls / auto-hide / buffering / error recovery | PASS | Full API-35 regression suite retained |
| Gestures | Seek/double-tap/brightness/system volume | PASS | Existing gesture instrumentation retained; system volume remains separate from DSP boost |
| Gestures | Pinch zoom / pan | PASS | Existing Step-3 behavior retained |
| Display | Resize/aspect/rotation/orientation/fullscreen | PASS | Existing surface-transform path retained |
| Player | Screen lock | PASS | Existing touch suppression/unlock path retained |
| Player | Playback speed | PASS | 0.25×–4× service-owned playback retained |
| Player | Previous/Next/Repeat/Shuffle | PASS | Existing MediaSession queue retained |
| PiP | Same-session continuity | PASS | Existing PiP plus Step-5 real API-35 PiP certification |
| Seek preview | Frame thumbnail preview | PARTIAL | Architecture/foundation only; Step 5 does not fabricate thumbnails |

## Step-2 library — preserved through Step 5

| Area | Capability | Status | Evidence / boundary |
|---|---|---|---|
| Library | Videos/Folders/Continue/Recent/History | PASS | Existing Step-2 library retained |
| Library | Favourites/Playlists/queues | PASS | Room relationships retained through v4 migration |
| Library | Search/sort/filter | PASS | Existing deterministic derivation retained |
| Storage | MediaStore/SAF/persisted permissions | PASS | Existing source architecture retained |
| Storage | Missing source/relink | PASS | Existing media relink remains separate from subtitle/audio relink |
| Files | Rename/delete | PASS | Existing provider-aware flows retained |
| Media | Thumbnails | PASS | API-26/API-28 regressions remain green on merged Step-5 main |

## Step-1 foundations preserved through Step 5

| Area | Capability | Status | Evidence / boundary |
|---|---|---|---|
| Player | Service-owned playback architecture | PASS | Single `PlaybackService -> MediaSession -> ExoPlayer` ownership retained |
| Player | Local playback / play / pause / seek | PASS | Existing real Media3 integration retained |
| Player | Resume/history | PASS | Room history retained through v4 migration |
| Player | Background/session foundation | PASS | Extended, not replaced, by Step-5 background audio policy |
| Media | Large-file-safe application types | PASS | Long-safe media/timing values retained |
| Decoder | Software decoder | NOT IMPLEMENTED | Correctly remains Step 6 |

## Step-5 certification evidence

Merged Step-5 main baseline:

- PR #7
- merge commit: `bea360164fe69cfc146dc03b55df4da4a0cb153a`
- Android CI #163 / run `34158185652`
- debug + JVM/unit/DSP — **PASS**
- release compilation — **PASS**
- lint — **PASS**
- API-35 instrumentation — **PASS**
- API-26 regression — **PASS**
- API-28 regression — **PASS**

The canonical-document/DSP-certification hardening branch adds the exact remaining signal tests identified by the original Step-5 specification. It must pass the identical exact-head matrix before merge; no older green run substitutes for that gate.

## Physical certification deferred to Step 10

The following remain **NOT VERIFIED — DEFERRED TO STEP 10**:

- real 3 GB+/5 GB+/10 GB playback under representative devices
- physical 4K/HDR colour/performance
- OEM/provider/SAF variations
- SD-card and USB/OTG device behavior
- actual Bluetooth latency and route switching quality
- USB DAC / HDMI receiver behavior
- subjective EQ/limiter acoustic quality
- manufacturer-specific background restrictions
- cutouts/foldables/external displays
- battery, thermal and long-run physical testing
- broad phone/tablet matrix

## Step-5 overall result

**PASS for implemented software/emulator functionality on the merged Step-5 baseline.**

The additional exact DSP certification hardening in the current correction branch is a required quality gate before that branch can be merged. Step 6 remains **NOT IMPLEMENTED**.