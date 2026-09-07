# MAX Video Player — Architecture through Step 6

## Clean-room boundary

MAX Video Player is an original native Android implementation. MX Player Pro is used only as a behavioral/workflow/feature-depth reference. No proprietary code, decompiled logic, decoder binaries, DSP algorithms, EQ presets, assets, branding, package names, certificates or credentials are reused.

## Authoritative playback ownership

Playback remains service-owned:

```text
Compose UI
    ↓
PlayerViewModel / AudioPlaybackController / PlaybackConnection
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
    ├─ video → ProfessionalMediaCodecSelector → Android/Media3 decoder
    └─ audio → DefaultAudioSink + MaxAudioProcessor
    ↓
Android video/audio output
```

Steps 1–6 preserve this ownership. Step 6 does not create an Activity-owned player, a second ExoPlayer, or parallel hardware/software players. Embedded/external audio, video and Step-4 subtitles remain on the one authoritative Media3 timeline.

## Logical layers through Step 6

- `core.model` — stable media/domain/playback state and decoder mode catalog
- `core.database` — Room entities/DAOs/migrations through version 5
- `core.media` — MediaStore, SAF, metadata and URI availability
- `core.device` — runtime device and decoder capability inventory
- `playback.engine` — service-owned Media3 engine, source composition, video-decoder renderer policy and custom audio sink
- `playback.session` — service/session/controller ownership, queues and decoder-switch commands
- `feature.library` — library/index/queue/thumbnail/file actions
- `feature.player` — player UI, gestures, decoder controls/diagnostics, display/orientation/PiP
- `feature.subtitle` — Step-4 subtitle engine
- `feature.audio` — Step-5 audio tracks, external audio, DSP, sync, routing and professional controls
- `feature.decoder` — Step-6 classification, policy, persistence, runtime diagnostics and Media3 codec selection
- `ui` — application theme

# Step-6 professional decoder architecture

## Mode policy

The four user-visible modes are materially different routing policies:

### Auto

- discovers available video decoders through Media3 / Android
- prefers hardware candidates
- permits deliberate fallback across remaining compatible candidates, including software
- records actual initialized decoder/backend instead of changing only a label

### Hardware

- exposes only the preferred hardware-accelerated candidate
- no software candidate is visible
- no second hardware candidate is visible, so strict mode does not silently fall back
- failure is surfaced truthfully

### Enhanced Hardware

- exposes the ordered hardware-only candidate set
- Media3 initialization fallback can move to another hardware codec
- software candidates remain excluded

### Software

- exposes software-only platform `MediaCodec` candidates
- hardware candidates remain excluded
- no FFmpeg/native video decoder is bundled in Step 6
- when no software backend exists for the format, the product reports it rather than silently using hardware

## Classification policy

API-29+ platform/Media3 hardware/software/vendor flags are authoritative. Older API fallback is intentionally conservative: only known software codec-name families are classified as software; ambiguous vendor names remain `UNKNOWN` rather than being guessed as hardware.

## Format compatibility

`MediaCodecSelector` defines the backend-visible candidate pool. Media3's `MediaCodecVideoRenderer` then performs format-aware ordering using decoder `isFormatSupported`, which accounts for MIME/profile/level and video size/rate before codec initialization.

The separate device inventory records Android-exposed per-MIME capability evidence:

- hardware/software/unknown classification
- vendor flag where exposed
- adaptive playback
- secure playback
- tunneled playback
- low-latency capability where exposed
- profile/level pairs
- color formats
- 720p / 1080p / 1440p / 2160p size/rate probes at 30/60 fps

The inventory is device-specific and is never treated as universal Android codec support.

## Runtime decoder switching

`PlaybackService` collects decoder-mode requests from `DecoderRepository`. Switching happens inside `Media3PlaybackEngine` and reuses the same ExoPlayer.

Before re-prepare the engine snapshots:

- media queue
- current media index
- current position
- play/pause intent
- repeat mode
- shuffle state
- playback parameters, including Step-3 speed and Step-5 pitch
- track-selection parameters, preserving audio/subtitle/video selection

The engine stops and re-prepares the same media items at the same index/position, restores those values and leaves the Step-5 audio sink/DSP installed.

## Decoder failure and fallback

Actual decoder initialization/release and playback errors are observed from Media3. A failed candidate is recorded and blacklisted for the current session. A retry occurs only when another candidate remains under the active policy. Fallback history is bounded, preventing an unbounded retry loop.

Hardware has only one visible candidate. Enhanced Hardware can retry only hardware. Software can retry only software. Auto can intentionally cross backend classes.

## Requested vs effective diagnostics

Decoder state deliberately separates requested policy from actual decoder state. Diagnostics include:

- requested mode
- effective mode/backend
- actual initialized decoder name
- hardware/software/vendor/secure flags where known
- input MIME and codec string
- resolution and frame rate where known
- initialization duration
- dropped frames
- switching state
- structured last failure
- bounded fallback history/count

The UI therefore cannot claim Software or Hardware merely because the user tapped that label.

## Device decoder capability cache

`DeviceCapabilityProvider.collectDecoderProfile()` produces an immutable codec inventory. `PlayerViewModel` loads it on `Dispatchers.Default`, not during Compose recomposition or the Step-5 realtime audio callback. The Decoder dialog exposes an advanced per-codec panel and an explicit off-main-thread manual refresh.

## Room v5 and decoder persistence

Step 6 advances Room from v4 to v5.

New table:

- `decoder_media_state` — stable media ID to optional per-media decoder-mode override plus update time

`MIGRATION_4_5` is explicit. `fallbackToDestructiveMigration()` is not used. Migration instrumentation preserves Step-1 history, multi-GB-safe values, Step-2 favourites/playlists/library state, Step-4 subtitle rows and Step-5 audio associations/state.

Global decoder settings remain lightweight preferences:

- default decoder mode
- remember decoder per video
- show decoder diagnostics

## DRM / secure-decoder boundary

Secure requirements are passed into Media3 codec discovery. Step 6 does not bypass DRM, downgrade secure-decoder requirements or invoke private OEM codec APIs.

## Color / HDR / resolution boundary

Decoder selection does not intentionally transcode, resize, recolor or alter HDR metadata. Video output stays in the Media3/Android decoder/render path. Capability inventory reports only exposed metadata. Physical HDR/10-bit/color-fidelity certification remains Step 10.

## Large-media policy

Step 6 remains URI/reference based. It does not:

- copy whole source videos into app storage
- read whole media into RAM
- transcode before playback
- add a 3 GB ceiling
- add a 1080p ceiling
- decode entire files ahead of playback

Decoder switching reuses MediaItems/URIs and the same playback service.

# Step-5 professional audio architecture — preserved

`Media3PlaybackEngine` still installs project-owned `MaxAudioProcessor` in `DefaultAudioSink`. The deterministic PCM chain remains:

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

The realtime audio callback performs no Room, codec inventory, storage, network, Compose or coroutine work. Immutable DSP parameter snapshots are published outside the callback. Step 6 changes only video decoder routing and preserves audio tracks, external audio, EQ, channel controls, pitch, sync, audio-only/background behavior, audio focus and route handling.

# Step-4 subtitle coexistence — preserved

Embedded/external subtitles, SRT/WebVTT/SSA/ASS/TTML parsing, encoding handling, sidecar discovery, appearance, per-media delay and Room persistence remain on the existing media-source/parser path. Decoder switching restores track-selection parameters instead of replacing subtitle architecture.

# Step-3 player experience — preserved

Controls/auto-hide, seeking, double-tap, brightness, Android media-volume gesture, zoom/pan, aspect/resize/rotation/orientation/fullscreen, lock, 0.25×–4× speed, previous/next/repeat/shuffle, PiP and accessibility remain on the same player/session.

# Step-2 library — preserved

Videos/folders/Continue Watching/Recent/History/Favourites/Playlists, search/sort/filter, MediaStore/SAF, Room index/cache, relink, rename/delete and bounded thumbnails remain unchanged by decoder routing.

# Step-1 foundations — preserved

Service-owned playback, MediaSession background foundation, resume/history, URI-based media, long-safe values and device capability foundation remain authoritative.

# Verification architecture

Step-6 software/emulator certification adds:

- four-mode policy/unit tests
- hardware/software backend isolation
- Enhanced Hardware multi-candidate hardware-only behavior
- Auto ordering and fallback termination
- session blacklist behavior
- profile/level, secure and size/rate policy checks
- persisted enum fallback
- Room v4→v5 plus earlier-chain preservation instrumentation
- API-35 real service-owned Auto/Software/Hardware/Enhanced Hardware routing test with actual initialized codec identity
- playback-position preservation across decoder switches
- API-35 real codec inventory/classification report
- retained Step-1–5 unit/instrumentation suites
- retained API-26/API-28 thumbnail regressions

See `STEP_6_TEST_MATRIX.md` for exact evidence.

# Physical certification boundary

The following remain:

**NOT VERIFIED — DEFERRED TO STEP 10**

- Snapdragon/Exynos/MediaTek/Tensor decoder behavior
- OEM codec quirks and runtime crash recovery
- representative physical H.264/HEVC/VP9/AV1 performance
- physical 4K60/high-bitrate/HDR/10-bit color behavior
- battery, thermal and long-play stability
- broad phone/tablet decoder matrix
