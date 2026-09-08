# Step 6 Completion Report — Professional Decoder Engine

## Boundary

This report covers Step 6 only. Step 7 is not implemented here.

Step 6 extends the Step-5-certified application while preserving the single service-owned Media3 playback architecture, Step-5 DSP, Step-4 subtitles, Step-3 player behavior, Step-2 library and Step-1 large-media/reference foundations.

## Final implementation and certification status

**STEP 6: PASS**

All required Step-6 repository gates are satisfied:

1. exact documentation-complete branch head `c463ecf526b359833053ac505ebc68598e435b12` passed the configured matrix in Android CI run #216;
2. PR #9 merged that exact head into `main` without dropping Step-6 source/evidence/documentation changes;
3. exact resulting `main` merge commit `b47c4315cb895269a14a1ef8dc71696423f8fdc0` passed the same configured matrix in Android CI run #218.

The configured matrix covers debug/JVM tests, release compilation, lint, complete API-35 instrumentation, API-26 thumbnail regression and API-28 thumbnail regression.

The Step-6 implementation is therefore repository-complete in its declared software/emulator scope. Physical OEM/SoC, large-media, 4K/HDR/high-bitrate, battery/thermal and broad device certification remain explicitly deferred to Step 10 and are not implied by this PASS.

## Delivered decoder modes

### Auto

- real Media3/Android decoder discovery
- deterministic hardware-first ordering
- deliberate fallback across remaining compatible candidates when necessary
- actual initialized decoder/backend recorded separately from the requested label

### Hardware

- preferred hardware-accelerated candidate only
- no software candidate is visible
- no second hardware candidate is visible
- failure is surfaced truthfully instead of silently relaxing the policy

### Enhanced Hardware

- ordered compatible hardware-only candidate set
- Media3 initialization fallback can move between hardware candidates
- software candidates remain excluded

### Software

- software-only platform MediaCodec candidates
- hardware candidates remain excluded
- when no compatible software decoder is exposed by the device, the mode reports unavailable rather than silently using hardware
- Step 6 does not bundle FFmpeg/native video decoding and does not claim universal software-codec coverage

## Architecture preservation

Step 6 keeps one authoritative playback graph:

```text
Compose UI
  ↓ requested policy
PlayerViewModel / DecoderRepository / PlaybackConnection
  ↓
MediaController
  ↓
PlaybackService : MediaSessionService
  ↓
MediaSession
  ↓
Media3PlaybackEngine
  ↓
ExoPlayer
  ↓
ProfessionalRenderersFactory
  ├─ video → ProfessionalMediaCodecSelector → Android/Media3 MediaCodec
  └─ audio → DefaultAudioSink → MaxAudioProcessor
```

There is no Activity-owned player, second ExoPlayer, second synchronized decoder player, or parallel hardware/software playback engine.

## Step-5 DSP coexistence

The production `DefaultAudioSink` still contains the project-owned `MaxAudioProcessor`. Decoder routing changes only video decoder selection and does not remove or bypass:

- embedded/external audio-track selection
- 10-band EQ
- preamp
- digital boost
- limiter
- channel controls/balance
- per-media audio delay
- route compensation
- pitch/speed behavior
- audio-only/background/PiP policies

## Runtime switching preservation

Before decoder reconfiguration, `Media3PlaybackEngine` snapshots and restores:

- queue/media items
- current media index
- current position
- play/pause intent
- repeat mode
- shuffle state
- playback parameters, including speed/pitch
- track-selection parameters, preserving audio/subtitle/video selections

The same ExoPlayer instance is stopped/re-prepared at the same item/position rather than replaced.

## Decoder failure handling

- actual decoder initialization/release/errors are observed from Media3
- failed codec candidates are session-blacklisted
- retry occurs only while another candidate remains valid under the active mode
- fallback history is bounded
- Hardware cannot silently fall through to software
- Enhanced Hardware cannot silently fall through to software
- Software cannot silently fall through to hardware
- Auto is the only policy intentionally allowed to cross backend classes

## Diagnostics

The product separates requested policy from actual decoder state and can report:

- requested mode
- effective mode/backend
- actual initialized decoder name
- hardware/software/vendor/secure classification where available
- MIME / codec string
- resolution / frame rate where available
- decoder initialization duration
- dropped frames
- switching state
- structured last failure
- bounded fallback history/count

## Device Decoder Capabilities inventory

`DeviceCapabilityProvider.collectDecoderProfile()` provides a cached immutable per-device video-decoder inventory. Initial collection and manual refresh run off the UI thread.

The advanced Decoder panel reports Android-exposed per-MIME information including where available:

- hardware/software/unknown classification
- vendor status
- adaptive playback
- secure playback
- tunneled playback
- low-latency capability
- profile/level pairs
- color formats
- 720p / 1080p / 1440p / 2160p size/rate probes at 30 and 60 fps

The UI and documentation explicitly state that the inventory describes the current device only and is not a universal Android codec-support claim.

## Persistence / Room v5

Step 6 advances Room from v4 to v5 and adds:

- `decoder_media_state` — stable media ID, requested per-media decoder mode and update time

`MIGRATION_4_5` is explicit and non-destructive. `fallbackToDestructiveMigration()` is not used.

Migration instrumentation preserves prior:

- playback history/resume
- multi-GB-safe `Long` fields
- favourites
- playlists/items
- library/index/preferences
- subtitle associations/media state
- external-audio associations/media state

Global decoder default, remember-per-video and diagnostics visibility use the existing lightweight preference layer.

## Automated certification evidence

### Exact pre-merge branch certification

Documentation-complete branch head:

`c463ecf526b359833053ac505ebc68598e435b12`

Android CI run #216 (`34208213180`) passed:

- debug build + JVM/unit tests
- release compilation
- lint
- API-35 full instrumentation
- API-26 regression
- API-28 regression

The preceding implementation evidence run #215 (`34207634128`) also passed the full configured matrix and exported the API-35 decoder capability report. Its connected API-35 suite recorded 39 tests, 0 failures, 0 errors and 0 skipped.

### Exact post-merge `main` certification

PR #9 merged the certified branch head using a normal merge commit.

Exact `main` merge commit:

`b47c4315cb895269a14a1ef8dc71696423f8fdc0`

Android CI run #218 (`34209217145`) passed:

- debug build + JVM/unit tests
- release compilation
- lint
- API-35 full instrumentation
- API-26 regression
- API-28 regression

This satisfies the final Step-6 merge rule.

### JVM / policy

Step-6 unit coverage includes:

- four distinct decoder modes
- Hardware isolation
- Enhanced Hardware hardware-only fallback
- Software isolation
- Auto ordering
- session blacklist behavior
- fallback termination
- profile/level handling
- secure-decoder handling
- size/rate handling
- unknown-capability conservatism
- legacy classification conservatism
- persisted enum fallback

### API-35 production integration

`Step6DecoderIntegrationTest` exercises the real service-owned path and requires:

- Auto to report the actually initialized decoder/backend
- Software to use a genuine software-only decoder when exposed, otherwise report truthful unavailability
- Hardware to use a genuine hardware-accelerated decoder when exposed, otherwise report truthful unavailability
- Enhanced Hardware to remain hardware-only when exposed, otherwise report truthful unavailability
- current playback position to survive a decoder mode switch
- requested policy to remain separate from effective backend

No `Assume`/skip turns missing backend capability into a PASS.

### API-35 capability report

`Step6CodecCapabilityReportTest` validates codec inventory against Android API-29+ classification flags and writes a device-specific decoder capability report. CI exports it as:

`app/build/reports/step6/decoder-capability-report-api35.txt`

The certified emulator evidence recorded 18 real video-decoder entries: four hardware-classified Goldfish video decoders, fourteen software video decoder entries, and zero unknown classifications for that tested emulator. This is device/emulator-specific evidence only.

### Regression matrix

Step 6 retains:

- debug build
- all JVM/unit tests
- release compilation
- Android lint
- full API-35 instrumentation
- API-26 thumbnail regression
- API-28 thumbnail regression
- earlier Step-1–5 product tests

See `STEP_6_TEST_MATRIX.md` for the detailed matrix.

## Dependency / clean-room result

Step 6 adds no proprietary decoder pack, no FFmpeg/native video decoder, no OEM decoder binary, no second playback library and no new native `.so` payload.

Decoder routing uses the already-declared AndroidX Media3 stack plus Android `MediaCodecList`/`MediaCodecInfo` capability APIs. See `STEP_6_DEPENDENCIES.md` and `DEPENDENCIES.md`.

No MX Player proprietary source, assets, binaries, decoder code, branding or package identity are reused.

## Large-media / color / DRM boundaries

Step 6 remains URI/reference based and does not:

- copy whole source media into app storage for playback
- read entire media into RAM
- pre-decode the entire file
- transcode before playback
- add a 3 GB ceiling
- add a 1080p ceiling
- intentionally recolor/resize video or rewrite HDR metadata
- bypass secure-decoder/DRM requirements
- upload media for decoder selection or diagnostics

## Physical certification boundary

The following remain:

**NOT VERIFIED — DEFERRED TO STEP 10**

- Snapdragon / Exynos / MediaTek / Tensor decoder behavior
- Samsung / Xiaomi / Oppo / OnePlus OEM codec quirks
- representative physical H.264 / HEVC / VP9 / AV1 matrices
- physical 3 GB+/5 GB+/10 GB source behavior
- physical 4K60 / high-bitrate / HDR / 10-bit behavior
- battery / thermal / long-play stability
- cross-OEM runtime fallback behavior
- broad phone/tablet matrix

## Final Step-6 merge rule — satisfied

- [x] exact documentation-complete `step-6-professional-decoder-engine` head passed debug/JVM tests, release compilation, lint, API-35, API-26 and API-28
- [x] Step-6 PR #9 merged without dropping source/evidence/documentation changes
- [x] exact resulting `main` merge head passed the same configured CI matrix

Final status:

**STEP 6: PASS**

Step 7 has not been started.
