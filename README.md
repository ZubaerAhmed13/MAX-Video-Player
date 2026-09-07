# MAX Video Player

MAX Video Player is an original, native Android media-player project intended to grow toward MX Player Pro-class feature depth, reliability and usability through a **clean-room implementation**.

MX Player Pro is used only as a functionality/workflow reference. This repository does **not** copy MX Player source code, decompiled code, binaries, proprietary decoders, package names, branding, logos, certificates, API keys or copyrighted assets.

## Current development status

**Step 5 of 10 — Professional Audio Engine: REPOSITORY-COMPLETE**

Step 5 was merged through PR #7. The merged `main` commit is:

- `bea360164fe69cfc146dc03b55df4da4a0cb153a`

Post-merge Android CI #163 (`34158185652`) passed on that exact `main` commit:

- debug build + JVM/unit/DSP tests — **PASS**
- release compilation — **PASS**
- Android lint — **PASS**
- API-35 full instrumentation — **PASS**
- API-26 legacy-thumbnail regression — **PASS**
- API-28 legacy-thumbnail regression — **PASS**

A subsequent Step-5 certification-hardening change expands the DSP signal matrix and refreshes these canonical documents; it must pass the same exact-head CI matrix before being merged.

Physical 3 GB+/4K/HDR/device-matrix, Bluetooth/USB/HDMI acoustic latency, OEM background restrictions, battery and thermal certification remain **NOT VERIFIED — DEFERRED TO STEP 10**.

## Platform baseline

- Kotlin
- Jetpack Compose
- AndroidX
- Media3 / ExoPlayer 1.11.0
- MediaSession + MediaSessionService
- Room 2.8.4
- Coroutines + Flow / StateFlow
- minSdk 23
- targetSdk 36
- compileSdk 36
- Java 17
- Android Gradle Plugin 9.4.0
- Gradle 9.6.0

This is a fully native Android application. It does not use WebView, Capacitor, Cordova, React Native, Flutter or TWA as its application architecture.

## Step-5 professional audio engine

Step 5 keeps the single service-owned Media3 player and adds a real audio-processing layer rather than cosmetic controls.

Implemented software/emulator behavior includes:

- embedded audio-track discovery from Media3 `Tracks`
- Off/Auto/manual track policy with persisted preferred audio language
- durable external-audio associations per stable media ID
- multiple external-audio associations, select/relink/remove/recovery behavior
- external audio merged into the same Media3 timeline rather than a second player
- actual external Media3 audio-track selection certification on API 35
- coexistence with Step-4 side-loaded subtitles
- app-owned PCM DSP installed in the production `DefaultAudioSink`
- PCM 16-bit and PCM-float processing paths
- professional 10-band EQ at 31/62/125/250/500 Hz and 1/2/4/8/16 kHz
- original Flat/Bass/Vocal/Treble/Rock/Classical/Electronic presets plus Custom
- ±12 dB EQ bands
- preamp and digital boost with bounded soft limiting
- positive and negative per-media audio delay
- separate output-route compensation; effective sync is media delay + route compensation
- stereo/mono/left/right modes and left/right balance
- truthful multichannel handling: stereo-only controls are disabled for non-stereo selected tracks; 5.1/7.1 layouts are not falsely remapped
- independent pitch control through Media3 playback parameters
- Play as Audio using real video-track disable/restore on the same session
- background policies: Pause, Continue audio and PiP when possible
- optional background video suppression with foreground restoration
- real API-35 PiP certification
- audio focus and becoming-noisy behavior retained from the service-owned Media3 configuration
- route awareness for speaker, wired, Bluetooth, USB and HDMI families
- Room database version 4 with explicit `MIGRATION_3_4`
- preservation of Step-1–4 Room data through migration

## DSP quality and certification policy

The DSP is project-owned Kotlin code running through Media3 audio processing. It does not use an opaque proprietary DSP binary and does not depend on `android.media.audiofx.Equalizer` for the required professional behavior.

Required signal certification covers:

- neutral PCM16 bit transparency
- neutral PCM-float transparency
- EQ-enabled Flat transparency
- measured EQ response at 62 Hz, 1 kHz and 8 kHz
- cross-band selectivity so EQ cannot be a disguised global gain
- Nyquist-safe band handling
- preamp −6/0/+6 dB behavior
- real digital boost
- integer and float limiter bounds
- NaN/Infinity sanitization on the active DSP path
- stereo balance and mono/left/right routing
- multichannel preservation
- zero, positive and negative audio-delay semantics
- delay bounds and flush/seek stale-buffer rejection
- 44.1/48/96 kHz processing
- filter-state reset
- live parameter revision without processor recreation
- DC-offset/numerical-safety checks
- long deterministic extreme-settings stability
- truthful unsupported-PCM rejection

See `STEP_5_TEST_MATRIX.md` for the detailed certification matrix.

## Playback ownership

```text
Compose Player / Library UI
   ↓
AudioPlaybackController / PlaybackConnection
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
DefaultAudioSink + MaxAudioProcessor
   ↓
Android audio output
```

There is no Activity-owned second ExoPlayer. External audio, embedded audio, video and Step-4 subtitles remain on the one authoritative service-owned timeline.

## Persistence and Room v4

Room v4 retains all earlier tables and adds Step-5 audio state:

- `audio_associations` — external audio relationship/URI/metadata/preferred/availability
- `audio_media_state` — Auto/manual selection, selected external or embedded descriptor and per-media audio delay

`MIGRATION_3_4` is explicit. `fallbackToDestructiveMigration()` is not used. Migration instrumentation verifies Step-1 history/large `Long` values, Step-2 favourites/playlists/library state and Step-4 subtitle state survive upgrades.

Global lightweight audio preferences such as EQ/preset/boost/pitch/background/route compensation use the existing preference layer; relational per-media state remains in Room.

## Step-4 subtitle engine — preserved

Step 5 preserves embedded/external subtitle selection, SRT/WebVTT/SSA/ASS/TTML parsing, encoding handling, sidecar discovery, styling, per-media subtitle delay and Room subtitle persistence. Audio delay and subtitle delay are intentionally separate.

Production instrumentation verifies external audio can be attached/selected while an external subtitle relationship remains present.

## Step-3 player — preserved

Controls/auto-hide, seeking, double-tap, brightness, actual Android media-volume gesture, zoom/pan, aspect/resize/rotation/orientation/fullscreen, lock, 0.25×–4× speed, queue controls, repeat/shuffle, PiP and accessibility handling remain preserved.

Seek-frame thumbnail preview remains **PARTIAL — architecture/foundation only**; Step 5 does not fabricate it.

## Step-2 library — preserved

Videos/folders/Continue Watching/Recent/History/Favourites/Playlists, search/sort/filter, MediaStore, user-approved SAF folders, Room index/cache, relink, rename/delete and bounded thumbnails remain preserved. API-26/API-28 thumbnail regressions remain mandatory CI gates.

## Large-media and quality policy

Media, subtitle and external-audio sources remain URI/reference based. Step 5 does not:

- copy whole videos into application storage just to play them
- read entire source media into RAM
- transcode video/audio for playback
- create a second synchronized audio player
- introduce a 3 GB file ceiling
- introduce a 1080p resolution ceiling
- alter video colour/HDR/scaling/decoder selection

Physical large-file/4K/HDR performance remains Step-10 certification rather than inferred PASS.

## Documentation

Canonical project documents now cover Step 5:

- `README.md`
- `ARCHITECTURE.md`
- `DEPENDENCIES.md`
- `PARITY_MATRIX.md`

Step-specific evidence:

- `STEP_5_ARCHITECTURE.md`
- `STEP_5_DEPENDENCIES.md`
- `STEP_5_TEST_MATRIX.md`
- `STEP_5_COMPLETION_REPORT.md`
- earlier Step-1–4 completion reports
- `LARGE_MEDIA_AUDIT.md`

## Roadmap boundary

Step 5 does **not** implement Step 6 decoder modes. Hardware/enhanced-hardware/software decoder control, custom codec fallback and FFmpeg/software-decoder work remain Step 6.

## Contribution principle

Do not solve difficult architectural problems by deleting requirements. Preserve working behavior, implement independently, document genuine limitations, retain user data through schema changes and never fabricate verification results.