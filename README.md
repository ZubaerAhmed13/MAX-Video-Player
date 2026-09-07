# MAX Video Player

MAX Video Player is an original, native Android media-player project intended to grow toward MX Player Pro-class feature depth, reliability and usability through a **clean-room implementation**.

MX Player Pro is used only as a functionality, workflow and feature-depth reference. This repository does **not** copy MX Player source code, decompiled code, binaries, proprietary decoder implementations, package names, branding, logos, fonts, certificates, API keys or copyrighted assets.

## Current development status

**Step 4 of 10 — Professional Subtitle Engine: SOFTWARE/EMULATOR PASS**

Step-4 branch: `step-4-professional-subtitles`

Authoritative Step-4 implementation gate before documentation finalization:

- implementation SHA: `94aa7af146e342f8405c6032f8d29bfe9250d780`
- GitHub Actions workflow: `Android CI`
- run: `34100525570` (#129)
- debug build + JVM tests: **PASS**
- release compilation: **PASS**
- lint: **PASS**
- API-35 full instrumentation: **PASS**
- API-26 legacy-thumbnail regression: **PASS**
- API-28 legacy-thumbnail regression: **PASS**

The documentation-complete PR head must also pass the same CI matrix before merge. Physical 3 GB+, 4K/HDR, OEM/provider, hardware-routing, cutout/foldable, battery, thermal and broad device-matrix certification remains **NOT VERIFIED — DEFERRED TO STEP 10**.

## Platform baseline

- Kotlin
- Jetpack Compose
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

This is a fully native Android application. It does not use WebView, Capacitor, Cordova, React Native, Flutter or TWA as its application architecture.

## Step-4 professional subtitle engine

Step 4 adds a real subtitle layer without replacing the Step-1 service-owned player or the Step-2/3 library/player architecture.

Implemented software/emulator behavior includes:

- embedded Media3 text-track discovery and manual selection
- explicit subtitle **Off**, **Auto** and manual track selection
- external side-loaded subtitle loading through Android `OpenDocument` / SAF by URI reference
- multiple external subtitle associations per stable media ID
- SRT, WebVTT, SSA/ASS and TTML/DFXP text subtitle policy
- direct Android instrumentation proving the real Media3 subtitle parsers emit cues for SRT, WebVTT, ASS, SSA and TTML
- multilingual Unicode fixture coverage including English, Bangla, Arabic/RTL and Japanese text
- deterministic external-track identity: custom Media3 ID when preserved, with label/MIME/language fallback when `Format.id` is not propagated
- persisted per-media preferred external subtitle and subtitle delay
- positive/negative subtitle synchronization using `Long` timing with a bounded ±600,000 ms policy
- automatic matching sidecar discovery for user-approved SAF folders using filename scoring and preferred-language ordering
- preferred subtitle language settings
- encoding detection/override for Auto, UTF-8, UTF-16 LE, UTF-16 BE and Windows-1252; external text can be normalized to UTF-8 before Media3 parsing
- durable subtitle associations, selection, availability, encoding and delay in Room v3
- explicit database `MIGRATION_2_3` while preserving Step-1/2 data and without destructive migration fallback
- availability/recovery states for missing, permission-lost, unsupported and malformed external subtitle sources
- external subtitle relinking while preserving the media relationship, selected state and synchronization delay
- malformed/unavailable external subtitle isolation so a subtitle failure does not become a video-playback failure
- asynchronous production subtitle-file probing; no whole-video read, copy or re-encode
- subtitle appearance controls for text size, text colour, background, edge style/colour, bottom margin and embedded cue styling/font-size behavior
- scroll-safe subtitle controls suitable for narrow player surfaces

The Android system-caption-style toggle is intentionally **not** exposed as a fake control because Step 4 does not yet apply that setting through `SubtitleView`.

## Playback ownership — preserved

```text
Compose Player / Library UI
   ↓
PlaybackConnection
   ↓
MediaController
   ↓
MediaSessionService
   ↓
MediaSession
   ↓
PlaybackEngine
   ↓
Media3 / ExoPlayer
```

Step 4 does not create an Activity-owned ExoPlayer. External subtitles are attached by rebuilding the current MediaItem/source through the existing service-owned controller while preserving queue index, position and play state.

`Media3PlaybackEngine` uses a subtitle-aware media-source factory so synchronization offsets are applied by a subtitle parser wrapper rather than by rewriting video/audio media.

## Subtitle storage and recovery model

Room database version 3 adds:

- `subtitle_associations` — stable media relationship, URI, label, language, MIME/format, encoding, preferred flag, availability and per-association delay
- `subtitle_media_state` — selected external association and per-media delay

The subtitle repository keeps a small synchronous cache for MediaItem construction while persistence and file/provider probing stay on I/O dispatchers. Existing MediaStore/SAF media and large files remain reference-based.

Persisted external subtitle access is revalidated. If a subtitle disappears or permission is lost, the association remains recoverable, the unavailable subtitle is not attached to Media3, and the video path remains independent. `Relink` binds a replacement subtitle file back to the same media relationship.

## Format and encoding policy

Supported Step-4 text subtitle families:

- SRT / SubRip
- WebVTT
- SSA
- ASS
- TTML / DFXP

Provider MIME aliases and filename extensions are normalized by `SubtitleFormatPolicy`. XML is accepted only when bounded prefix inspection identifies TTML-like content.

`SubtitleEncodingPolicy` recognizes UTF-8/UTF-16 BOMs, validates UTF-8 and exposes Windows-1252 fallback/override. Encoding normalization is applied only to external subtitle text; embedded tracks are left to Media3.

## Synchronization policy

Subtitle delay is `Long`-safe and clamped to ±600 seconds. Positive values show cues later and negative values show cues earlier. Negative shifts crossing time zero are clipped safely rather than producing invalid negative cue time.

A fresh subtitle parser factory is created per MediaItem so queue prefetching cannot accidentally inherit another media item's delay.

## Step-3 professional player — preserved

Step 4 retains the Step-3 player experience: controls/auto-hide, seek/double-tap/brightness/volume/zoom/pan gestures, resize/aspect/rotation/orientation/fullscreen, lock, 0.25×–4.0× speed, queues, repeat/shuffle, PiP, accessibility handling, tutorial and Long-safe display/seek math.

Seek-frame thumbnail preview remains **PARTIAL — architecture/foundation only**; Step 4 does not fabricate that capability.

## Step-2 professional library — preserved

Videos, folders, Continue Watching, Recent, History, Favourites, Playlists, search/sort/filter, MediaStore, user-approved SAF folders, Room media index/cache, relink, rename/delete and bounded thumbnails remain preserved. API-26/API-28 thumbnail regression jobs stay in the Step-4 CI matrix.

## Large-media and quality policy

Normal playback, library and subtitle operations remain URI/reference based. File sizes, durations, positions, subtitle timing and seek targets remain `Long`-safe where applicable.

Step 4 does not:

- copy or transcode the video to add subtitles
- load whole videos into RAM
- generate full-video frame sequences
- alter source colour characteristics
- add an artificial media resolution/file-size limit

Real 3 GB+, 4K/HDR and OEM/device performance remain Step-10 physical certification items rather than inferred PASS claims.

## Verification

Step-4 automated evidence includes:

- JVM tests for format policy, filename/language matching, encoding and subtitle timing
- Room migration/persistence instrumentation, including v1 → v3 and v2 → v3 preservation
- repository tests for multiple associations, selection and delay persistence
- parser instrumentation for SRT/WebVTT/ASS/SSA/TTML and multilingual Unicode
- recovery instrumentation for missing subtitle → recoverable state → relink, delay preservation and encoding persistence
- real service-owned MP4 + side-loaded SRT cue integration on API 35
- retained Step-1/2/3 integration and API-26/API-28 regression suites

Core CI commands:

```bash
gradle --no-daemon :app:assembleDebug :app:testDebugUnitTest
gradle --no-daemon :app:assembleRelease
gradle --no-daemon :app:lintDebug
gradle --no-daemon :app:connectedDebugAndroidTest --stacktrace
```

## Documentation

- `ARCHITECTURE.md` — architecture through Step 4
- `DEPENDENCIES.md` — dependency/license decisions through Step 4
- `PARITY_MATRIX.md` — evidence-backed capability matrix through Step 4
- `LARGE_MEDIA_AUDIT.md` — large-media/integer safety boundary
- `STEP_1_COMPLETION_REPORT.md`
- `STEP_2_COMPLETION_REPORT.md`
- `STEP_3_COMPLETION_REPORT.md`
- `STEP_4_COMPLETION_REPORT.md`

## Roadmap boundary

Step 4 does **not** begin Step 5 audio DSP/equalizer work, Step 6 software-decoder/FFmpeg routing, later network/cloud/cast work or Step-10 physical certification.

## Contribution principle

Do not solve difficult architectural problems by deleting requirements. Preserve working behavior, implement independently, document genuine limitations, keep user data through schema changes and never fabricate verification results.