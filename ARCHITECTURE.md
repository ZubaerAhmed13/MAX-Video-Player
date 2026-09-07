# MAX Video Player — Architecture through Step 5

## Clean-room boundary

MAX Video Player is an original native Android implementation. MX Player Pro is used only as a behavioral/workflow/feature-depth reference. No proprietary code, decompiled logic, decoder binaries, DSP algorithms, EQ presets, assets, branding, package names, certificates or credentials are reused.

## Authoritative playback ownership

Playback remains service-owned:

```text
Compose UI
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

Steps 1–5 preserve this ownership. Step 5 does not create an Activity-owned or second audio player. Embedded audio, selected external audio, video and Step-4 subtitles share one authoritative Media3 timeline.

## Logical layers through Step 5

- `core.model` — stable media/domain/playback state
- `core.database` — Room entities/DAOs/migrations through version 4
- `core.media` — MediaStore, SAF, metadata and URI availability
- `core.device` — runtime device/codec capability profile
- `playback.engine` — Media3 engine, subtitle-aware source composition and custom audio-sink integration
- `playback.session` — service/session/controller ownership, queues and playback state
- `feature.library` — library/index/queue/thumbnail/file actions
- `feature.player` — player UI, gestures, display transforms, orientation and PiP
- `feature.subtitle` — Step-4 subtitle engine
- `feature.audio` — Step-5 audio tracks, external audio, DSP, sync, routing and professional controls
- `ui` — application theme

# Step-5 professional audio architecture

## Control/data flow

```text
ProfessionalAudioUi
        ↓ user intent
AudioPlaybackController ──────────────┐
        ↓                             │
PlaybackConnection / MediaController  │
        ↓                             │
PlaybackService / MediaSession        │
        ↓                             │
Media3PlaybackEngine                  │
        ↓                             │
DefaultAudioSink + MaxAudioProcessor  │
                                      │
AudioRepository ── Room v4 / prefs ───┘
        ↓
ContentResolver / SAF + AudioRouteMonitor
```

`AudioPlaybackController` translates user intent into commands on the existing service-owned MediaController. It never creates another ExoPlayer.

## Embedded audio tracks

Media3 `currentTracks` is authoritative for real audio groups. Step 5 publishes readable descriptors including available label/language, MIME/codec, channel count, sample rate, bitrate, commentary role, support and selected state.

Manual selection uses `TrackSelectionOverride`. Durable restore stores descriptive track fields rather than unstable Media3 group indexes, allowing reordered groups to be rematched.

Auto mode clears audio overrides and applies the persisted preferred-language list.

## External audio

External audio is loaded through Android `OpenDocument`/SAF and stored as a durable association to the stable media ID.

`ProfessionalMediaSourceFactory` composes the selected external audio with the primary source using `MergingMediaSource`:

```text
primary video + embedded audio + Step-4 subtitles
                     +
selected external audio
                     ↓
              one Media3 timeline
```

No second synchronized player, extraction or transcode is used. Missing/permission-lost/unsupported external audio remains recoverable; embedded/default audio stays available as a safe fallback.

Across the MediaSession boundary, merged-child identity is resolved using preserved merged `Format` identity as well as direct group identity where available, because MediaSession may rewrite controller-visible group IDs.

## Room v4

Step 5 advances Room from v3 to v4.

New tables:

- `audio_associations` — external-audio URI relationship, name/language/MIME, preferred flag and availability
- `audio_media_state` — selection mode, selected external ID or embedded descriptor fields, per-media audio delay and timestamp

`MIGRATION_3_4` is explicit and `fallbackToDestructiveMigration()` is not used. Migration instrumentation covers v1→v4, v2→v4 and v3→v4 while preserving history, multi-GB `Long` values, favourites, playlists, library/index/preferences and Step-4 subtitle rows.

## Deterministic PCM DSP

`Media3PlaybackEngine` installs `MaxAudioProcessor` into a custom `DefaultAudioSink`. User-defined EQ/gain/channel/delay behavior therefore occurs in the production ExoPlayer audio path rather than only in UI state.

Conceptual order:

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

Supported app-owned processing formats:

- PCM 16-bit
- PCM float

Unsupported PCM/output formats are rejected/bypassed truthfully instead of reinterpreting bytes.

### Real-time policy

The audio callback performs no Room, storage, network, Compose or coroutine work. UI/repository changes publish a small immutable `AudioDspParameters` snapshot through an `AtomicReference`.

Processing/filter/delay arrays are allocated or reconfigured at format/flush/parameter boundaries, not per sample. Filter history is reset after flush/reconfiguration. EQ coefficients use short ramps to reduce discontinuity when settings change live.

### Numerical safety

- invalid/non-finite float input on the active DSP path is sanitized
- non-finite/unstable filter output resets defensively
- EQ/preamp/boost/balance/delay values are bounded by policy
- the soft limiter prevents invalid output range
- positive-delay storage is bounded to 40 MiB
- excessive high-rate/high-channel delay requests report DSP unavailability rather than allocating unbounded memory
- neutral PCM16 uses a transparent copy path
- neutral float uses a transparent copy path for valid PCM

## 10-band equalizer

Center frequencies:

`31, 62, 125, 250, 500, 1k, 2k, 4k, 8k, 16k Hz`

Gain range is ±12 dB. A band at/above the safe Nyquist boundary is disabled instead of constructing an unstable filter.

Original presets: Flat, Bass, Vocal, Treble, Rock, Classical, Electronic, plus Custom.

The certification suite measures real output response at 62 Hz, 1 kHz and 8 kHz rather than relying only on coefficient assertions.

## Gain and channel controls

Preamp and digital boost are independent of Android system media volume. The Step-3 right-side gesture continues to control actual Android media volume rather than DSP boost.

Stereo input supports:

- Stereo
- Mono downmix
- Left duplicated to both stereo outputs
- Right duplicated to both stereo outputs
- left/right balance

### Multichannel truthfulness

Channel mode and balance are explicitly stereo-only. For a selected non-stereo track (for example 5.1/7.1), the UI disables those controls and states that the multichannel layout is preserved.

The DSP does not reinterpret six/eight-channel frames as stereo. EQ/gain may still process each accessible PCM channel where supported.

## Audio synchronization

Per-media audio delay is a `Long` and is clamped to ±10,000 ms.

- positive delay = bounded PCM delay buffering
- negative delay = deterministic leading-frame trimming
- zero delay = no sample insertion/removal
- changing delay seeks/flushes at the current position so stale delayed PCM is not retained

Route compensation is separate global state keyed by route family. Effective timing is:

```text
per-media delay + current-route compensation
```

The final effective value is clamped by the same professional delay range.

## Pitch and Step-3 speed

Step-3 speed remains 0.25×–4×. Step 5 adds independent pitch through real Media3 `PlaybackParameters`; setting one preserves the other.

## Audio-only mode

`Play as audio` disables Media3 video-track selection while keeping the same player, media item, queue and timeline. Restoring video reenables selection without restarting playback from zero.

## Background policy

Global leave-player policy:

- Pause
- Continue audio
- PiP when possible

Optional `Disable video while playing in background` uses the same service-owned track-selection path. Lifecycle-only suppression is distinct from user-selected Audio-only mode.

Foreground return restores video after lifecycle suppression. API-35 instrumentation certifies the real PiP path rather than forcing an impossible lifecycle transition for an Activity that successfully entered PiP.

The existing MediaSession/foreground-service notification remains authoritative for background controls.

## Android audio integration

`Media3PlaybackEngine` retains media `AudioAttributes`, Media3 audio-focus handling and `setHandleAudioBecomingNoisy(true)`.

`AudioRouteMonitor` classifies speaker, wired headset/headphones, Bluetooth A2DP/LE where exposed, USB, HDMI and unknown. The app reports truthful route labels from Android where available and does not force private Bluetooth routing.

## Step-4 subtitle coexistence

External-audio composition preserves the Step-4 subtitle media-source/parser path. Audio delay and subtitle delay are separate repositories and controls.

API-35 production integration attaches an external subtitle, then external audio, and verifies the subtitle association remains intact while external audio becomes the actually selected Media3 track.

# Preserved Step-1–4 architecture

Step 5 preserves:

- Step 1 service-owned MediaSession playback, history/resume and background service
- Step 2 library, favourites, playlists, MediaStore/SAF, relink/file operations and thumbnails
- Step 3 gestures, speed, display transforms, PiP and accessibility behavior
- Step 4 embedded/external subtitles, SRT/VTT/SSA/ASS/TTML, timing, styling, sidecars and Room subtitle persistence

No Step-5 code adds a whole-video copy, full-file RAM read, transcode, artificial 3 GB ceiling or 1080p ceiling. Video colour/HDR/scaling/decoder selection are not modified.

# Verification architecture

Step-5 software/emulator certification includes:

- PCM16 and PCM-float neutral transparency
- EQ-enabled Flat transparency
- 62 Hz / 1 kHz / 8 kHz measured response with known gain
- cross-band EQ selectivity
- Nyquist-safety handling
- −6/0/+6 dB preamp behavior
- boost and integer/float limiter tests
- NaN/Infinity sanitization on active DSP
- mono/left/right/balance mapping and channel-inversion checks
- 5.1 preservation under stereo-only controls
- zero/+500 ms/negative delay semantics
- delay clamping and delay-buffer flush/seek reset
- 44.1/48/96 kHz processing
- filter reset and live parameter revision
- DC-offset/numerical safety
- long deterministic extreme-settings streaming stability
- truthful unsupported-PCM rejection
- Room v1/v2/v3 → v4 migration instrumentation
- API-35 service-owned track/external-audio/DSP/audio-only/background/PiP/subtitle coexistence tests
- API-26/API-28 legacy regressions

# Later-step boundaries

Step 5 does **not** claim Step 6 decoder work. Hardware/enhanced-hardware/software decoder selection, codec fallback and FFmpeg/custom software decoding remain Step 6.

# Physical certification boundary

Real Bluetooth/headset/USB/HDMI latency and acoustic quality, OEM audio effects/offload, physical 3 GB+/4K/HDR playback, battery, thermal and broad phone/tablet behavior remain:

**NOT VERIFIED — DEFERRED TO STEP 10**.