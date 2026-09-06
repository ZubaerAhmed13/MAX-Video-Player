# MAX Video Player

MAX Video Player is an original, native Android media-player project intended to grow toward MX Player Pro-class feature depth, reliability, and usability through a **clean-room implementation**.

MX Player Pro is used only as a functionality, workflow, interaction, and feature-depth reference. This repository does **not** copy MX Player source code, decompiled code, binaries, proprietary decoder implementations, package names, branding, logos, fonts, certificates, API keys, or copyrighted assets.

## Current development status

**Step 1 of 10 — Professional Android Foundation**

Active development branch: `step-1-professional-foundation`

Step 1 establishes the native playback, lifecycle, persistence, large-media, device-capability, storage, testing, and certification architecture required for later parity work. Step 2 is intentionally out of scope until Step 1 is independently reviewed and approved.

Current certification state: **PARTIAL while CI/runtime verification completes**. Physical 3 GB+, 4K, Bluetooth/headphone-route, and manufacturer-specific codec certification remain `NOT VERIFIED` until representative hardware/assets are tested.

## Platform baseline

- Kotlin
- Jetpack Compose 1.11.x (BOM 2026.06.00)
- AndroidX
- Media3 / ExoPlayer
- MediaSession + MediaSessionService
- Room
- Coroutines + Flow / StateFlow
- minSdk 23
- targetSdk 36
- compileSdk 36
- Java 17
- Android Gradle Plugin 9.4.0
- Gradle 9.6.0

This is a fully native Android application. It does not use WebView, Capacitor, Cordova, React Native, Flutter, or TWA as its application architecture.

## Step-1 capabilities

The current Step-1 implementation includes:

- service-owned Media3 playback engine rather than Activity/Composable-owned ExoPlayer instances
- real MediaSession integration for Android system media controls
- background-playback architecture through MediaSessionService
- audio-focus handling and audio-becoming-noisy protection
- local MediaStore discovery
- Storage Access Framework file opening with persistable URI support
- URI-based large-media access without whole-file copying or whole-file RAM loading
- long-safe file-size, duration, position, and offset handling
- bounded first/middle/end sampled media fingerprinting instead of hashing multi-GB media end-to-end
- metadata extraction for duration, dimensions, rotation, codecs, audio information, and available colour metadata
- Room playback history, completion state, and resume decision logic
- playback queue foundation
- basic professional library and player interfaces
- play, pause, seek, previous/next foundation, playback speed, loading state, and structured errors
- PiP foundation
- HTTPS/HLS/DASH/RTSP Media3 dependency foundation
- device/codec capability profiling using MediaCodecList/MediaCodecInfo
- capability-aware 720p, 1080p, 1440p, and 2160p checks
- explicit decoder-mode model without pretending a software decoder exists
- unit, database instrumentation, and Compose instrumentation tests
- GitHub Actions build/test/lint workflow

## Decoder status policy

The architecture models `AUTO`, `HARDWARE`, `ENHANCED_HARDWARE`, and `SOFTWARE`. Step 1 does **not** claim four independent decoder engines. AUTO and hardware playback use Media3/MediaCodec; Enhanced Hardware currently shares the hardware foundation; Software is architecture-only and remains `NOT IMPLEMENTED` until its dedicated later step. No placebo decoder buttons are counted as implementation.

## Large-media policy

Large-media support is a non-negotiable architectural requirement. Normal playback references source URIs directly, does not duplicate multi-GB files just to play them, does not read entire videos into byte arrays, does not load complete media into RAM, uses `Long` for byte sizes/offsets and playback time values, and bounds optional fingerprint reads to small samples.

The architecture targets 3 GB+ media, but a physical 3 GB+ playback result remains `NOT VERIFIED` until such an asset is actually tested.

## 4K policy

The application does not impose an artificial resolution ceiling. It targets SD through 720p, 1080p, 1440p/2K, and 2160p/4K subject to the device decoder's real capabilities. It never assumes that a recent Android version automatically means 4K support. Physical 3840×2160 playback remains `NOT VERIFIED` until tested on appropriate hardware/media.

## Build

Requirements:

1. JDK 17
2. Android SDK platform 36
3. Android SDK Build Tools 36.0.0 or compatible installed tooling
4. Gradle 9.6.0 (CI installs this explicitly)

From the repository root:

```bash
gradle :app:assembleDebug
gradle :app:testDebugUnitTest
gradle :app:assembleRelease
gradle :app:lintDebug
```

For instrumentation on a connected/emulated Android device:

```bash
gradle :app:connectedDebugAndroidTest
```

The repository intentionally does not rely on hidden signing credentials for Step 1 verification.

## Architecture overview

Playback ownership is deliberately separated from UI lifecycle:

```text
Compose UI
   │
PlaybackConnection / MediaController
   │
MediaSessionService
   │
MediaSession
   │
PlaybackEngine
   │
Media3 / ExoPlayer
```

The source tree is logically separated into `core.model`, `core.database`, `core.media`, `core.device`, `playback.engine`, `playback.session`, `feature.library`, `feature.player`, and `ui`. These package boundaries are designed so later Gradle-module extraction does not require replacing domain contracts.

See `ARCHITECTURE.md` for the detailed ownership and dependency model.

## Verification and documentation

- `ARCHITECTURE.md` — architecture, ownership, lifecycle, decoder expansion, storage, and capability design
- `PARITY_MATRIX.md` — target capability status using PASS/PARTIAL/FAIL/NOT VERIFIED/NOT IMPLEMENTED
- `LARGE_MEDIA_AUDIT.md` — whole-file-loading and integer/offset safety audit
- `DEPENDENCIES.md` — Step-1 dependency register and reasons
- `STEP_1_COMPLETION_REPORT.md` — certification report and executed-gate status

No UI-only stub is allowed to be marked `PASS`.

## Roadmap boundary

The following dedicated areas remain later steps and must not be mistaken for completed Step-1 functionality: full media-library management, advanced gestures, professional subtitle engine/styling, audio DSP, real software-decoder routing, SMB/WebDAV/FTP, cloud/Cast, advanced privacy/security features, and final multi-device/large-media/HDR certification.

## Contribution principle

Do not solve difficult architectural problems by deleting requirements. Prefer correct, maintainable, testable implementations; preserve working behavior; document genuine limitations; and never fabricate verification results.
