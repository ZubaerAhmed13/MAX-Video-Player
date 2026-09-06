# MAX Video Player

MAX Video Player is an original, native Android media-player project intended to grow toward MX Player Pro-class feature depth, reliability, and usability through a **clean-room implementation**.

MX Player Pro is used only as a functionality, workflow, interaction, and feature-depth reference. This repository does **not** copy MX Player source code, decompiled code, binaries, proprietary decoder implementations, package names, branding, logos, fonts, certificates, API keys, or copyrighted assets.

## Current development status

**Step 2 of 10 — Professional Media Library**

Active development branch: `step-2-professional-media-library`

Step 1 remains the playback/lifecycle/storage foundation. Step 2 extends that foundation with a persistent, storage-aware, queue-aware video library without moving playback ownership back into an Activity or replacing the MediaSession architecture.

Step-2 completion is evidence-based: a screen or table existing by itself is not counted as PASS. Physical 3 GB+, 4K/HDR, SD-card/USB/OEM MediaStore, Bluetooth, battery, thermal, and broad hardware certification remain **NOT VERIFIED — DEFERRED TO STEP 10**.

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

## Step-2 library capabilities

The Step-2 branch currently implements the following software foundations and integrated flows:

- Videos, Folders, Continue Watching, Recent, History, Favourites, and Playlists sections
- list and grid library views with persisted preference
- debounced search across title, filename, and folder
- video sorting by name, added/modified date, duration, size, resolution, and last played
- watched/unwatched/in-progress/favourites filtering
- MediaStore discovery using a bounded projection rather than opening every source file
- MediaStore change observation with debouncing
- user-approved SAF directory sources through `ACTION_OPEN_DOCUMENT_TREE`
- persisted SAF tree permission state and provider-aware traversal
- stable source/folder identities that do not depend only on display names
- excluded-folder persistence and restore workflow
- Room-backed media index/cache, favourites, playlists, sources, exclusions, and library preferences
- explicit Room database `MIGRATION_1_2`; no destructive migration fallback
- Continue Watching and Recent based on the existing authoritative Step-1 playback history/resume policy
- playlist create/rename/delete/add/remove/reorder persistence
- folder/list/playlist playback requests that construct real queues for the existing service-owned player path
- missing playlist media represented as unavailable instead of crashing
- stable-ID-preserving relink validation/repository foundation
- Android-policy-compliant delete/rename repository foundation for MediaStore/SAF
- dedicated bounded in-memory thumbnail repository with cancellation and failure fallback
- 10,000-entry deterministic search/sort verification
- Long-safe multi-GB size handling throughout the library model/database

Some Step-2 requirements still require explicit evidence or final integration before they may be marked PASS; see `PARITY_MATRIX.md` and `STEP_2_COMPLETION_REPORT.md`.

## Playback ownership — preserved from Step 1

```text
Compose UI
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

Library work does not create an Activity-owned player. Selecting media from a folder, sorted video list, or playlist passes lightweight queue state into the existing playback/session architecture.

## Storage model

The library distinguishes source truth from cached/indexed metadata. MediaStore is queried for fast indexed metadata. User-approved SAF trees are traversed through Android document-provider APIs without assuming filesystem paths. Missing or permission-lost sources are retained as recoverable/unavailable state rather than silently treated as valid or immediately erased.

The app does **not** request `MANAGE_EXTERNAL_STORAGE` merely for convenience. If broad media permission is denied, SAF Open File and Add Folder remain usable.

## Persistence

Room database version 2 adds persistent favourites, playlists and playlist items, library sources, excluded folders, media index rows, and library preferences while retaining Step-1 history and playback preferences. `MIGRATION_1_2` is explicit and is covered by an Android migration test intended to prove Step-1 history/resume values survive the upgrade.

## Large-library and large-media policy

Library scans, SAF traversal, metadata work, thumbnail work, and large derived-list operations run off the UI thread. Compose uses lazy list/grid containers with stable media IDs as keys. Search/sort correctness is exercised with 10,000 synthetic media rows; the project avoids brittle nanosecond timing assertions.

File sizes, durations, positions, and relevant counters remain `Long`-safe. Normal library operations do not read entire videos, calculate thumbnails from full 4K/8K frames, or duplicate multi-GB media. Real 3 GB+, 5 GB+, 10 GB+, 4K, and removable-storage behavior still require Step-10 physical certification.

## Thumbnail policy

`ThumbnailRepository` centralizes thumbnail loading. Requests are dimension-bounded, executed off the main thread, cancellable, and cached in a bounded LRU memory cache. Thumbnail failure returns a placeholder and never blocks playback. No unbounded bitmap cache is allowed.

## Build and verification

From the repository root:

```bash
gradle :app:assembleDebug
gradle :app:testDebugUnitTest
gradle :app:assembleRelease
gradle :app:lintDebug
gradle :app:connectedDebugAndroidTest
```

GitHub Actions runs the same Step-2 gate, with API-35 instrumentation and KVM acceleration where available. A failed gate is fixed at the root cause; tests are not disabled, weakened, or ignored merely to make CI green.

## Documentation

- `ARCHITECTURE.md` — Step-1 playback ownership plus Step-2 media-library/source/cache/queue architecture
- `DEPENDENCIES.md` — dependency register and licenses/purposes
- `PARITY_MATRIX.md` — evidence-backed capability status
- `LARGE_MEDIA_AUDIT.md` — large-media/integer safety boundary
- `STEP_1_COMPLETION_REPORT.md` — Step-1 certification record
- `STEP_2_COMPLETION_REPORT.md` — Step-2 implementation/test/CI report

## Roadmap boundary

Step 2 intentionally does **not** implement the final advanced gesture system, professional external subtitle engine, audio DSP/equalizer, real software-decoder routing, later network/cloud/cast features, or final physical-device certification. Those remain later steps.

## Contribution principle

Do not solve difficult architectural problems by deleting requirements. Preserve working behavior, implement independently, document genuine limitations, keep user data through schema changes, and never fabricate verification results.