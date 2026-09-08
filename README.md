# MAX Video Player

MAX Video Player is an original, native Android media-player project intended to grow toward MX Player Pro-class feature depth, reliability and usability through a **clean-room implementation**.

MX Player Pro is used only as a functionality/workflow reference. This repository does **not** copy MX Player source code, decompiled code, binaries, proprietary decoders, package names, branding, logos, certificates, API keys or copyrighted assets.

## Current development status

**Step 7 of 10 — Professional Network Playback and Sources: PASS**

Step 7 adds real HTTP/HTTPS progressive playback, HLS, DASH, RTSP, SMB2/3, WebDAV, FTP and explicit FTPS integration to the existing single service-owned Media3 player. It includes direct streams, authenticated saved locations, server browsing, stable remote history, network playlists, remote subtitle/audio attachment, adaptive quality, live controls, bounded reconnect, secure credential storage and redacted diagnostics.

The original Step-7 chain passed runs #242–#244. Review-gap implementation head `3addcee62754afb61479415d67e8e654cf171e16` passed every build, release, lint, emulator and expanded Samba/FTP/explicit-FTPS/authenticated-RTSP test step in run #248. PR #14's documentation-complete head and resulting `main` gate are authoritative in GitHub history and the final handoff. See `STEP_7_FINAL_CERTIFICATION.md`.

Explicit FTPS is fully automated with required control/data TLS and hostname verification. SFTP is not implemented. Physical NAS, weak-network, large remote media, 4K/HDR, long-play, battery, thermal and OEM certification remain **NOT VERIFIED — DEFERRED TO STEP 10**.

## Platform baseline

- Kotlin
- Jetpack Compose
- AndroidX
- Media3 / ExoPlayer 1.11.0
- MediaSession + MediaSessionService
- Room 2.8.4, schema version 6
- Coroutines + Flow / StateFlow
- minSdk 23
- targetSdk 36
- compileSdk 36
- Java 17
- Android Gradle Plugin 9.4.0
- Gradle 9.6.0

This is a fully native Android application. It does not use WebView, Capacitor, Cordova, React Native, Flutter or TWA as its application architecture.

## Step-7 professional network playback

Network sources flow through `NetworkRepository`, protocol-specific clients and `NetworkDataSourceRouter` into the existing `ProfessionalMediaSourceFactory`, `PlaybackService`, MediaSession and ExoPlayer. SMB and FTP use bounded random-access DataSources; HTTP/WebDAV/HLS/DASH use the shared OkHttp-backed Media3 path; RTSP uses Media3's RTSP module with RTP-over-RTSP/TCP selected explicitly. No normal protocol path copies a full movie before playback.

The Network center supports saved HTTP/HTTPS, WebDAV over HTTPS or explicitly acknowledged HTTP, SMB, FTP and FTPS locations with add/edit/test/browse/remove/forget flows. Direct and saved cleartext transports require explicit acknowledgement. Authenticated RTSP BASIC/DIGEST uses credentials entered separately from the URL.

Credentials are AES/GCM encrypted with an Android Keystore key. Room stores only an opaque reference and username hint. Authorization is scoped by origin and directory; cross-host redirects do not receive it. RTSP user-info exists only inside a private Media3 source and is masked from the session timeline. Diagnostics redact userinfo and sensitive query/header values. WebDAV XML rejects DTD/XXE and off-root entries, and its 4 MiB response cap is enforced while streaming before oversized allocation.

Adaptive controls are real Media3 track overrides. The UI reports live state and Go Live from Media3, and buffering diagnostics distinguish initial loading, buffering, reconnecting and failure. Network subtitles and external audio remain on the Step-4/5 source-composition path, while network video uses the Step-6 decoder policy without a second player.

See `STEP_7_COMPLETION_REPORT.md`, `STEP_7_FINAL_CERTIFICATION.md`, `STEP_7_TEST_MATRIX.md` and `STEP_7_PROTOCOL_SECURITY.md` for the exact protocol matrix, certification evidence and limitations.

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

The API-35 coexistence hardening now proves the full runtime state survives real decoder reconfiguration: A/B/C queue continuity, Previous/Next, repeat/shuffle, external subtitle rendering, external audio selection, EQ/DSP, audio delay, speed/pitch, Activity recreation, decoder changes during Audio-only and restoring video with the newly requested decoder all pass automated production-path assertions.

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

The original decoder implementation and the coexistence hardening both passed exact-head and post-merge gates.

Original certification:

- pre-merge branch head `c463ecf526b359833053ac505ebc68598e435b12` — Android CI run #216 — PASS
- PR #9 merge commit `b47c4315cb895269a14a1ef8dc71696423f8fdc0` — Android CI run #218 — PASS

Coexistence hardening certification:

- implementation head `1249363cdd9f5843c85dca399650db9a457a192d` — Android CI run #223 — PASS, API-35 **41/41 tests**
- documentation-complete PR head `4a7c1e351dab7e5dc1e6d1acd4e1954cec3ceee8` — Android CI run #225 — PASS
- PR #10 resulting `main` merge commit `4921f43deae9c1b3ff221071a30cc1dab26efa0b` — Android CI run #226 — PASS

The configured gates include:

- `:app:assembleDebug` — PASS
- `:app:testDebugUnitTest` — PASS
- `:app:assembleRelease` — PASS
- `:app:lintDebug` — PASS
- complete API-35 `connectedDebugAndroidTest` — PASS
- real Auto/Software/Hardware/Enhanced-Hardware production routing assertions — PASS
- A/B/C queue + Previous/Next decoder-switch coexistence assertions — PASS
- repeat/shuffle preservation — PASS
- Step-4 external subtitle preservation/rendering — PASS
- Step-5 external audio preservation — PASS
- EQ/DSP and audio-delay preservation — PASS
- speed/pitch preservation — PASS
- Activity recreation with selected decoder state — PASS
- Audio-only decoder change + video restore using requested backend — PASS
- API-35 decoder-capability inventory/export — PASS
- Room v4→v5 migration preservation — PASS
- retained Step-1–5 instrumentation — PASS
- API-26 thumbnail regression — PASS
- API-28 thumbnail regression — PASS

No `Assume`/skip is used to convert a missing emulator backend into a decoder-mode pass. Capability-aware tests require a truthful unavailable state when that backend is absent.

## Documentation

Canonical documents through Step 7:

- `README.md`
- `ARCHITECTURE.md`
- `DEPENDENCIES.md`
- `PARITY_MATRIX.md`

Step-specific evidence:

- `STEP_7_COMPLETION_REPORT.md`
- `STEP_7_TEST_MATRIX.md`
- `STEP_7_PROTOCOL_SECURITY.md`
- `STEP_6_ARCHITECTURE.md`
- `STEP_6_DEPENDENCIES.md`
- `STEP_6_TEST_MATRIX.md`
- `STEP_6_BRANCH_CERTIFICATION.md`
- `STEP_6_COEXISTENCE_HARDENING.md`
- `STEP_6_FINAL_CERTIFICATION.md`
- `STEP_6_COMPLETION_REPORT.md`
- earlier Step-1–5 completion reports and certification documents
- `LARGE_MEDIA_AUDIT.md`

## Roadmap boundary

Step 7 stops at professional network playback and sources. It does not add cloud-provider OAuth, casting/DLNA, final Android TV/USB workflows, a private vault, sleep timer, child mode or Step-10 physical certification.

## Contribution principle

Do not solve difficult architectural problems by deleting requirements. Preserve working behavior, implement independently, document genuine limitations, retain user data through schema changes and never fabricate verification results.
