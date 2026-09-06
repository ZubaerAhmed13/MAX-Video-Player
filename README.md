# MAX Video Player

MAX Video Player is an original, native Android media-player project intended to grow toward MX Player Pro-class feature depth, reliability, and usability through a **clean-room implementation**.

MX Player Pro is used only as a functionality, workflow, interaction, and feature-depth reference. This repository does **not** copy MX Player source code, decompiled code, binaries, proprietary decoder implementations, package names, branding, logos, fonts, certificates, API keys, or copyrighted assets.

## Current development status

**Step 3 of 10 — Professional Player Experience: SOFTWARE/EMULATOR PASS**

Step-3 implementation branch: `step-3-professional-player-ui`

Final Step-3 implementation SHA before certification documentation: `13411414c29464aaddfe9b5475eed03b16ddb4c2`.

Step 1 remains the playback/lifecycle/storage foundation and Step 2 remains the professional media-library foundation. Step 3 adds the professional touch-first player experience while preserving service-owned MediaSession playback, Room, MediaStore, SAF, queues, history/resume, and large-media safeguards.

Seek-frame preview is intentionally reported **PARTIAL — architecture/foundation only** rather than displaying fake thumbnails. Physical 3 GB+, 4K/HDR, OEM gesture/fullscreen behavior, real Bluetooth/headset routing, physical cutouts/foldables/external displays, battery, thermal, and broad hardware certification remain **NOT VERIFIED — DEFERRED TO STEP 10** by project policy.

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

This is a fully native Android application. It does not use WebView, Capacitor, Cordova, React Native, Flutter, or TWA as its application architecture.

## Step-3 professional player experience

Implemented and automated-test-backed software/emulator behavior includes:

- original professional top/bottom control overlays with title, queue position, seek/time display, previous/play-pause/next and player actions
- one authoritative control-visibility/auto-hide state machine that respects playback, interaction, menus, tutorial, lock and accessibility state
- Long-safe seek math, seek-bar scrubbing with local target preview and final exact seek on release, horizontal swipe seeking and seek HUD
- configurable double-tap zones: left/back, center/play-pause, right/forward, with persisted 5–60 second seek distance
- real left-side window-brightness gesture with safe clamping, system/default initialization and restoration when leaving the player
- real right-side `AudioManager` media-volume gesture using each device/emulator's actual maximum volume rather than assuming a fixed step count
- touch slop, direction locking, gesture ownership and modal/resume/tutorial/preparing-state conflict blocking
- two-finger pinch zoom from 1× to 5×, two-finger pan, rendered-transform-aware bounds and explicit reset
- resize/display modes: Fit, Fill, Crop, Original/100%, 16:9, 4:3, 18:9, 21:9 and validated/persisted custom aspect ratio
- display-only 90° rotation plus Auto/Sensor, Portrait, Landscape, Reverse Portrait, Reverse Landscape and Lock Current orientation modes
- fullscreen/immersive mode using modern system-bar APIs on supported Android versions with safe restoration/fallback
- genuine touchscreen lock that suppresses normal player interactions and exposes only the explicit unlock path while service/system media controls remain independent
- playback speed from 0.25× to 4.0× with common presets and 0.05× fine adjustment, plus optional remembered speed
- MediaSession-owned previous/next queue navigation, repeat and shuffle; current player metadata/display/PiP follows the active queue item
- dynamic PiP ratio derived from current media dimensions and rotation, reduced to a valid rational, clamped to Android-safe bounds, with 16:9 fallback and opt-in automatic PiP
- buffering UI, recovery-oriented playback error UI, media-information dialog and playback-ended replay/next path
- persistent Step-3 interaction preferences with stable enum names, safe clamping and restart/repository-recreation coverage
- first-run/or-menu original gesture tutorial
- accessibility semantics/content descriptions, live TalkBack/touch-exploration awareness, standard clickable controls and scroll-safe/narrow/large-text-friendly option surfaces
- SurfaceView-based Media3 rendering with geometric display transforms only; Step 3 adds no color filter, full-frame bitmap pipeline, source rewrite or re-encode

## Step-2 professional library — preserved

Step 3 preserves the Step-2 professional library, including:

- Videos, Folders, Continue Watching, Recent, History, Favourites, and Playlists
- lazy list/adaptive grid views, professional search, sorting and filtering
- MediaStore plus user-approved SAF folder sources and persisted permissions
- Room v2 media index/cache, favourites, playlists, sources, exclusions and preferences with explicit `MIGRATION_1_2`
- deterministic 512-row index loading with progressive snapshots
- playlist/folder/visible-list queues with selected-item start index
- rename/delete/relink flows with stable-ID relationship preservation
- bounded/cancellable thumbnail loading with a 16 MiB LRU memory cache and API-26/API-28 legacy regression coverage
- 10,000-entry deterministic search/sort/filter coverage and Long-safe multi-GB metadata tests

## Playback ownership — preserved from Step 1

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

Step 3 does not create an Activity-owned ExoPlayer. Queue navigation, speed, repeat, shuffle, PiP continuity, notification/headset/Bluetooth media control architecture and background playback remain anchored to the same service/session owner.

## Step-3 player state and interaction ownership

`PlayerViewModel` owns immutable coordinator state for control visibility, lock, gesture ownership, HUD, seek target, zoom/pan, resize/custom aspect, rotation, orientation, fullscreen, tutorial/accessibility mode and Step-3 preferences. `PlaybackConnection` separately exposes service-owned playback state. `PlayerInteractionPolicy` contains deterministic pure math/policy for seek, double-tap, brightness, volume, zoom/pan, transforms and PiP ratio.

Surface gestures are disabled while a player menu, tutorial, resume dialog or preparation state owns input. TalkBack touch exploration also disables potentially conflicting surface gestures while keeping standard clickable controls available.

## Storage, large-library and large-media policy

MediaStore and SAF remain source truth; Room `media_index` remains a recoverable metadata index/cache. The app does not request `MANAGE_EXTERNAL_STORAGE` merely for convenience.

Normal playback and library operations stay URI/reference based. File sizes, durations, positions and seek targets remain `Long`-safe. Step 3 does not duplicate media, load whole videos into RAM, generate full-video frame sequences, or add an artificial media-size/resolution limit.

## Seek-preview boundary

`SeekPreviewProvider` defines the architecture boundary for a future bounded, asynchronous, cancellable frame-preview implementation. Step 3 does **not** claim rendered seek thumbnails because a safe frame-extraction engine was not introduced here.

Status: **PARTIAL — architecture/foundation only**.

## Automated build and verification

Authoritative Step-3 implementation gate:

- GitHub Actions workflow: `Android CI`
- run: `34058821634` (#103)
- implementation SHA: `13411414c29464aaddfe9b5475eed03b16ddb4c2`
- debug build + JVM tests: **PASS**
- release compilation: **PASS**
- lint: **PASS**
- API-35 full instrumentation / Compose / real playback host integration: **PASS**
- API-26 legacy-thumbnail regression: **PASS**
- API-28 legacy-thumbnail regression: **PASS**

Core CI commands:

```bash
gradle --no-daemon :app:assembleDebug :app:testDebugUnitTest
gradle --no-daemon :app:assembleRelease
gradle --no-daemon :app:lintDebug
gradle --no-daemon :app:connectedDebugAndroidTest --stacktrace
gradle --no-daemon :app:connectedDebugAndroidTest --stacktrace \
  -Pandroid.testInstrumentationRunnerArguments.class=com.zubaer.maxvideoplayer.feature.library.ThumbnailRepositoryInstrumentedTest
```

The final documentation head must pass the same pull-request CI before Step 3 is merged to `main`.

## Documentation

- `ARCHITECTURE.md` — Step-1 playback ownership, Step-2 library architecture and Step-3 player interaction/display architecture
- `DEPENDENCIES.md` — dependency register
- `PARITY_MATRIX.md` — evidence-backed capability matrix through Step 3
- `LARGE_MEDIA_AUDIT.md` — large-media/integer safety boundary
- `STEP_1_COMPLETION_REPORT.md` — Step-1 certification record
- `STEP_2_COMPLETION_REPORT.md` — Step-2 certification record
- `STEP_3_COMPLETION_REPORT.md` — Step-3 A–Z software/emulator certification record

## Roadmap boundary

Step 3 intentionally does **not** implement the Step-4 professional subtitle engine, Step-5 audio DSP/equalizer stack, Step-6 software decoder/FFmpeg routing, later network/cloud/cast work, or Step-10 physical-device certification. Step 4 must not begin as part of Step-3 completion.

## Contribution principle

Do not solve difficult architectural problems by deleting requirements. Preserve working behavior, implement independently, document genuine limitations, keep user data through schema changes, and never fabricate verification results.