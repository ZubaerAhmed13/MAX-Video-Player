# Step 6 Architecture — Professional Decoder Engine

## Ownership invariant

Step 6 preserves the one authoritative playback graph:

```text
Compose UI
  ↓ user decoder policy
PlayerViewModel / DecoderRepository
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
  ├─ video: ProfessionalMediaCodecSelector → Android/Media3 codecs
  └─ audio: DefaultAudioSink → MaxAudioProcessor (Step 5 DSP preserved)
```

No Activity-owned player, second ExoPlayer or parallel hardware/software playback engine is introduced.

## Decoder modes

### Auto

Exposes the compatible candidate pool in deterministic order: hardware first, then conservatively unclassified candidates, then software. Media3 performs format-specific sorting for profile/level/resolution/frame-rate support before initialization. Decoder initialization fallback is enabled; runtime codec failures are recorded and the failed candidate is session-blacklisted before bounded reconfiguration.

### Hardware

Exposes only the preferred hardware-accelerated candidate. With one visible candidate, Media3 cannot silently fall through to software or another hardware codec. Failure is surfaced truthfully.

### Enhanced Hardware

Exposes the ordered hardware-only candidate set. Media3 decoder fallback can move to another hardware codec on initialization failure; software candidates are never visible in this mode.

### Software

Exposes software-only platform `MediaCodec` candidates. Hardware candidates are never visible. Step 6 does not bundle FFmpeg/native video decoding; if the device exposes no compatible software decoder, Software mode reports unavailable rather than faking success.

## Classification

API-29+ platform/Media3 classification flags are authoritative for hardware/software/vendor status. Legacy codec-name logic is deliberately conservative and recognizes known software families only; ambiguous names remain `UNKNOWN` instead of being guessed as hardware.

## Format capability handling

The selection layer records MIME, secure requirement and candidate metadata. Media3's video renderer performs format-aware ordering with decoder `isFormatSupported`, covering codec profile/level and video size/rate before initialization. The app's device capability inventory separately reports per-MIME profile/level, adaptive/secure/tunneled/low-latency flags, color formats and 720p/1080p/1440p/2160p 30/60-fps capability targets where Android exposes them.

## Runtime switching

A decoder mode change is collected by `PlaybackService` and applied to the existing `Media3PlaybackEngine`.

Before re-prepare, the engine snapshots:

- queue/media items
- current queue index
- current position
- play/pause intent
- repeat mode
- shuffle state
- playback parameters (speed/pitch)
- track-selection parameters (audio/subtitle/video selections)

It then stops/re-prepares the **same ExoPlayer instance** and restores those values. The existing Step-5 `MaxAudioProcessor` remains installed in the same `DefaultAudioSink`; Step-4 subtitle/external-audio source composition is not replaced.

## Runtime fallback and loop protection

Decoder failures are classified and recorded with the active candidate. A failed codec is blacklisted for the current playback session. Retry is attempted only when another candidate remains for the active mode. Fallback history is bounded. Changing mode clears the relevant session rejection state so an explicit user choice starts a fresh policy attempt.

## Diagnostics

The engine records actual Media3 decoder lifecycle events rather than only requested labels:

- requested mode
- effective mode/backend
- actual initialized decoder name
- hardware/software/vendor/secure classification where available
- input MIME/codec string/resolution/frame rate
- decoder initialization duration
- dropped frames
- switching state
- structured last failure
- bounded fallback history/count

The Decoder dialog shows requested and active state separately, avoiding cosmetic mode reporting.

## Device decoder inventory

`DeviceCapabilityProvider.collectDecoderProfile()` scans Android's codec inventory and returns an immutable `DeviceDecoderProfile`. The player loads it on `Dispatchers.Default`; Compose never scans codecs during recomposition. The advanced decoder panel also supports an explicit off-main-thread manual refresh.

The report is device-specific and must never be interpreted as universal Android codec support.

## Persistence and Room v5

Global decoder preferences use the existing lightweight preference layer:

- default decoder mode
- remember decoder per video
- diagnostics visibility

Per-media decoder override uses Room v5 `decoder_media_state`, keyed by stable media ID. `MIGRATION_4_5` is explicit and non-destructive; existing Step-1–5 rows remain intact.

## Large-media boundary

Step 6 does not copy, transcode or pre-decode whole media. Sources remain MediaStore/SAF/network URI references consumed by Media3. Decoder switching reuses the existing media items rather than copying source bytes. No 3 GB or 1080p ceiling is added.

## Color/HDR boundary

Decoder selection does not intentionally transform video pixels, color space, HDR metadata, resolution or frame rate. HDR/10-bit capability can only be reported from actual format/capability metadata. Physical HDR/color-fidelity certification remains Step 10.

## DRM/security boundary

Secure decoder requirements are passed through Media3/Android codec discovery. Step 6 does not bypass DRM, downgrade secure playback requirements or expose private codec control APIs.

## Software backend boundary

Step 6 uses platform software `MediaCodec` only. There is no bundled native software video backend, no new ABI payload and no new decoder license.

## Physical certification boundary

The following are **NOT VERIFIED — DEFERRED TO STEP 10**:

- Snapdragon/Exynos/MediaTek/Tensor codec behavior
- Samsung/Xiaomi/Oppo/OnePlus codec quirks
- representative physical H.264/HEVC/VP9/AV1 performance
- 4K60/high-bitrate/HDR/10-bit physical behavior
- long-play thermal/battery stability
- cross-OEM runtime fallback behavior
