# MAX Video Player

MAX Video Player is an original, native Android media-player project intended to grow toward MX Player Pro-class feature depth, reliability and usability through a **clean-room implementation**.

MX Player Pro is used only as a functionality/workflow reference. This repository does **not** copy MX Player source code, decompiled code, binaries, proprietary decoders, package names, branding, logos, certificates, API keys or copyrighted assets.

## Current development status

**Step 6 of 10 — Professional Decoder Engine: PASS**

Step 6 extends the existing service-owned Media3 player with real, materially distinct video decoder policies:

- **Auto** — hardware-first, with controlled compatible fallback across available backends
- **Hardware** — strict preferred hardware decoder only
- **Enhanced Hardware** — hardware-only multi-candidate fallback
- **Software** — software-only platform decoder candidates, with truthful unavailable state when none exists

The requested mode and the decoder that actually initializes are tracked separately. Step 6 does not add a second ExoPlayer, fake decoder labels, an Activity-owned playback engine, a proprietary decoder pack or a bundled FFmpeg/native video decoder.

Step 6 passed its exact pre-merge gate on branch head `c463ecf526b359833053ac505ebc68598e435b12` in Android CI run #216. PR #9 was then merged without dropping Step-6 source/evidence/documentation changes. The exact resulting `main` merge commit `b47c4315cb895269a14a1ef8dc71696423f8fdc0` passed the complete configured post-merge matrix in Android CI run #218: debug/JVM, release compilation, lint, API-35 full instrumentation, API-26 regression and API-28 regression.

Physical 3 GB+/4K/HDR/device-matrix, Snapdragon/Exynos/MediaTek/Tensor behavior, OEM codec quirks, battery and thermal certification remain **NOT VERIFIED — DEFERRED TO STEP 10**.

## Platform baseline

- Kotlin
- Jetpack Compose
- AndroidX
- Media3 / ExoPlayer 1.11.0
- MediaSession + MediaSessionService
- Room 2.8.4, schema version 5
- Coroutines + Flow / StateFlow
- minSdk 23
- targetSdk 36
- compileSdk 36
- Java 17
- Android Gradle Plugin 9.4.0
- Gradle 9.6.0

This is a fully native Android application. It does not use WebView, Capacitor, Cordova, React Native, Flutter or TWA as its application architecture.

## Step-6 professional decoder engine

### One authoritative playback graph

```text
Compose UI
   ↓ requested decoder policy
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
   ├─ Video → ProfessionalMediaCodecSelector → Android/Media3 MediaCodec
   └─ Audio → DefaultAudioSink → MaxAudioProcessor
```

There is still one service-owned ExoPlayer and one MediaSession. Embedded/external audio, video and Step-4 subtitles remain on the same Media3 timeline.

### Real decoder routing

The four decoder options are policies, not cosmetic state:

- **Auto** exposes the compatible candidate pool in deterministic hardware-first order and allows controlled fallback.
- **Hardware** exposes only the preferred hardware-accelerated candidate, so it cannot silently fall through to software or another hardware decoder.
- **Enhanced Hardware** exposes compatible hardware candidates only, allowing hardware-to-hardware initialization fallback.
- **Software** exposes software-only platform MediaCodec candidates and never intentionally routes to hardware.

On API 29+, Android/Media3 hardware/software/vendor flags are authoritative. Older-API fallback is conservative: known software families can be recognized, while ambiguous codec names remain `UNKNOWN` instead of being guessed as hardware.

### Format-aware capability handling

The selector is queried with the actual MIME, secure-decoder and tunneling requirements. Media3 performs format-support ordering before decoder initialization. The Step-6 device capability inventory additionally reports Android-exposed information per video decoder/MIME, including where available:

- hardware/software/unknown classification
- vendor status
- adaptive playback
- secure playback
- tunneled playback
- low-latency capability
- profile/level pairs
- color formats
- 720p / 1080p / 1440p / 2160p size/rate probes at 30 and 60 fps

The inventory is explicitly device-specific and is not presented as universal Android codec support.

### Runtime switching without a second player

Changing decoder mode reconfigures the same `Media3PlaybackEngine`/ExoPlayer. Before re-prepare the engine snapshots and restores:

- queue/media items
- current media index
- playback position
- play/pause intent
- repeat mode
- shuffle state
- playback parameters, including speed and pitch
- track-selection parameters, preserving Step-4 subtitles and Step-5 audio selection

The Step-5 `MaxAudioProcessor` remains installed in the existing `DefaultAudioSink` regardless of the active video decoder.

### Failure handling and diagnostics

Step 6 observes actual decoder lifecycle/error events from Media3. Failed candidates are session-blacklisted and retry is bounded to candidates that remain valid for the active mode.

The UI can report:

- requested decoder mode
- effective backend/mode
- actual initialized decoder name
- hardware/software/vendor/secure status where known
- input MIME / codec string / resolution / frame rate where known
- decoder initialization duration
- dropped frames
- switching state
- structured last failure
- bounded fallback history/count

This prevents Hardware/Software labels from claiming success merely because the user tapped them.

### Device Decoder Capabilities panel

The Decoder dialog includes an expandable advanced capability panel. `DeviceCapabilityProvider.collectDecoderProfile()` caches an immutable process-level snapshot, and both initial collection and manual refresh run off the main thread.

CI also generates a real API-35 emulator capability report. That report is evidence for the tested emulator only, not a universal Android compatibility claim.

## Persistence and Room v5

Room v5 retains all earlier tables and adds:

- `decoder_media_state` — stable media ID, requested per-media decoder mode and update time

`MIGRATION_4_5` is explicit and non-destructive. Migration instrumentation verifies prior history, favourites, playlists, library data, subtitle state and Step-5 audio state survive the upgrade.

Global lightweight decoder preferences include:

- default decoder mode
- remember decoder per video
- show decoder diagnostics

## Step-5 professional audio engine — preserved

The production audio path remains project-owned and deterministic:

```text
Decoded PCM
   ↓
Stereo channel mode / balance
   ↓
10-band peaking EQ
   ↓
Preamp + digital boost
   ↓
Soft limiter / numerical protection
   ↓
Per-media + route audio delay
   ↓
Media3 AudioSink
```

Step 6 does not replace or bypass embedded/external audio selection, the 10-band EQ, channel controls, preamp, digital boost, limiter, audio delay, pitch, route compensation, audio-only mode, background audio, audio focus or becoming-noisy behavior.

## Step-4 subtitle engine — preserved

Embedded/external subtitle selection, SRT/WebVTT/SSA/ASS/TTML parsing, encoding handling, sidecar discovery, styling, per-media subtitle delay and Room subtitle persistence remain intact. Decoder switching restores track-selection parameters rather than creating a parallel subtitle/player path.

## Step-3 player experience — preserved

Controls/auto-hide, seeking, double-tap, brightness, Android media-volume gesture, zoom/pan, aspect/resize/rotation/orientation/fullscreen, lock, 0.25×–4× speed, queue controls, repeat/shuffle, PiP and accessibility handling remain preserved.

Seek-frame thumbnail preview remains **PARTIAL — architecture/foundation only**; Step 6 does not fabricate it.

## Step-2 library — preserved

Videos/folders/Continue Watching/Recent/History/Favourites/Playlists, search/sort/filter, MediaStore, user-approved SAF folders, Room index/cache, relink, rename/delete and bounded thumbnails remain preserved. API-26/API-28 thumbnail regressions remain mandatory CI gates.

## Step-1 foundations — preserved

Service-owned playback, MediaSession background foundation, resume/history, URI-based source handling, long-safe media/timing values and the original device-capability foundation remain authoritative.

## Large-media, quality and security policy

Media, subtitle and external-audio sources remain URI/reference based. Step 6 does not:

- copy whole videos into application storage just to play them
- read entire source media into RAM
- pre-decode whole media
- transcode video/audio for playback
- create a second synchronized player
- introduce a 3 GB file ceiling
- introduce a 1080p resolution ceiling
- intentionally recolor, resize or rewrite HDR metadata
- bypass secure-decoder/DRM requirements
- upload media for decoder selection or diagnostics

Physical large-file/4K/HDR/high-bitrate performance remains Step-10 certification rather than inferred PASS.

## Step-6 software/emulator certification — PASS

The exact certified Step-6 branch head and exact `main` merge commit passed the configured gates:

- `:app:assembleDebug` — PASS
- `:app:testDebugUnitTest` — PASS
- `:app:assembleRelease` — PASS
- `:app:lintDebug` — PASS
- complete API-35 `connectedDebugAndroidTest` — PASS
- real Auto/Software/Hardware/Enhanced-Hardware production routing assertions — PASS
- API-35 decoder-capability inventory/export — PASS
- Room v4→v5 migration preservation — PASS
- retained Step-1–5 instrumentation — PASS
- API-26 thumbnail regression — PASS
- API-28 thumbnail regression — PASS

Pre-merge certification: branch head `c463ecf526b359833053ac505ebc68598e435b12`, Android CI run #216.

Post-merge certification: `main` merge commit `b47c4315cb895269a14a1ef8dc71696423f8fdc0`, Android CI run #218.

No `Assume`/skip is used to convert a missing emulator backend into a decoder-mode pass. Capability-aware tests require a truthful unavailable state when that backend is absent.

## Documentation

Canonical documents through Step 6:

- `README.md`
- `ARCHITECTURE.md`
- `DEPENDENCIES.md`
- `PARITY_MATRIX.md`

Step-specific evidence:

- `STEP_6_ARCHITECTURE.md`
- `STEP_6_DEPENDENCIES.md`
- `STEP_6_TEST_MATRIX.md`
- `STEP_6_BRANCH_CERTIFICATION.md`
- `STEP_6_FINAL_CERTIFICATION.md`
- `STEP_6_COMPLETION_REPORT.md`
- earlier Step-1–5 completion reports and certification documents
- `LARGE_MEDIA_AUDIT.md`

## Roadmap boundary

Step 6 is complete. Step 7 has **not** been started by the Step-6 work or its certification/finalization commits.

A future bundled native software-video decoder would require a separate explicit dependency/license/ABI review. Step 6 deliberately does not claim one exists.

## Contribution principle

Do not solve difficult architectural problems by deleting requirements. Preserve working behavior, implement independently, document genuine limitations, retain user data through schema changes and never fabricate verification results.
