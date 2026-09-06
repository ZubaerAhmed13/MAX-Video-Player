# MAX Video Player — Step 1 Architecture

## Clean-room boundary
MAX Video Player is an original Android implementation. MX Player Pro is used only as a behavioral/feature-depth reference. No proprietary code, binaries, assets, package names, branding, certificates, API keys, or decoder implementations are reused.

## Ownership model
The playback instance is **not Activity-owned**. `PlaybackService` (a Media3 `MediaSessionService`) owns one `Media3PlaybackEngine`, which owns one ExoPlayer instance and one `MediaSession`. UI code connects through `PlaybackConnection` / `MediaController`.

This makes rotation, Activity recreation, fullscreen transitions, PiP, background playback, lock-screen controls, Bluetooth/headset media buttons, and notification controls compatible with one continuous session rather than recreating the player from Compose.

## Logical layers
Although Step 1 remains one Gradle application module to keep the initial build surface controlled, code is split into explicit architecture packages:

- `core.model` — stable app/domain models, decoder truth-status, resume policy, queue foundation
- `core.database` — Room entities, DAOs, database, playback history repository
- `core.media` — MediaStore, SAF metadata, bounded sampled identity, URI availability
- `core.device` — MediaCodec/device capability profiling
- `playback.engine` — application playback abstraction and Media3 implementation
- `playback.session` — MediaSessionService, MediaController connection, structured playback error mapping
- `feature.library` — Step-1 local library/open-file/network entry UI
- `feature.player` — Step-1 player UI and resume coordination
- `ui` — Compose theme

The package boundaries are intentionally compatible with later extraction into Gradle modules without changing domain contracts.

## Playback engine abstraction
`PlaybackEngine` prevents ExoPlayer calls from being scattered across the UI. Step 1 implements the Media3/MediaCodec path. Decoder modes are modeled as AUTO, HARDWARE, ENHANCED_HARDWARE, and SOFTWARE, but status is explicit: software decoding is planned rather than faked, and enhanced hardware currently shares the hardware implementation until Step 6.

## Audio focus and noisy-route behavior
`Media3PlaybackEngine` configures media audio attributes with audio-focus handling enabled and enables `setHandleAudioBecomingNoisy(true)`, preventing route loss from unexpectedly switching playback to speakers.

## Local media and URI persistence
MediaStore provides indexed local videos when runtime permission is granted. `ACTION_OPEN_DOCUMENT`/OpenDocument is used for individual files without broad storage access. Persistable read grants are requested and stale/unavailable URIs are mapped to source states rather than assumed valid forever.

## Large-media design
Normal playback uses content/network URIs and Media3 data sources. No source video is copied or loaded into a byte array. File sizes, durations, positions, and offsets use `Long`. Optional media fingerprinting reads bounded first/middle/end samples through a seekable descriptor and never hashes a multi-GB source from beginning to end.

## Persistence
Room database version 1 stores media history and playback preferences. No destructive migration fallback is enabled. Playback snapshots are throttled by the service (5-second cadence while playing plus lifecycle/state events), avoiding per-frame database writes.

## Device capability
`DeviceCapabilityProvider` enumerates MediaCodec entries and records MIME types, hardware-acceleration visibility where Android exposes it, adaptive/secure features, profile/level values, and explicit 720p/1080p/1440p/2160p@30 checks. It never infers 4K support from API level alone.

## Network foundation
Media3 HLS, DASH, and RTSP modules are present. The Step-1 UI accepts secure HTTPS/HLS/DASH and RTSP URLs; app-wide cleartext HTTP is disabled. SMB/WebDAV/FTP/cloud/Cast remain later-step work.

## Future decoder path
A later native/software decoder can implement `PlaybackEngine` or sit behind a decoder router without rebuilding the Compose screens or persistence architecture. Step 1 deliberately does not ship a placebo software decoder.
