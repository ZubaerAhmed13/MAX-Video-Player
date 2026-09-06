# MAX Video Player

MAX Video Player is an original, native Android media-player project intended to grow toward MX Player Pro-class feature depth, reliability, and usability through a **clean-room implementation**.

MX Player Pro is used only as a functionality, workflow, interaction, and feature-depth reference. This repository does **not** copy MX Player source code, decompiled code, binaries, proprietary decoder implementations, package names, branding, logos, fonts, certificates, API keys, or copyrighted assets.

## Current development status

**Step 2 of 10 — Professional Media Library: SOFTWARE/EMULATOR PASS**

Step-2 implementation branch: `step-2-professional-media-library`

Step 1 remains the playback/lifecycle/storage foundation. Step 2 extends it with a persistent, storage-aware, queue-aware professional video library while preserving service-owned MediaSession playback. Physical 3 GB+, 4K/HDR, SD-card/USB/OEM MediaStore, Bluetooth, battery, thermal, and broad hardware certification remain **NOT VERIFIED — DEFERRED TO STEP 10** by project policy.

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

## Step-2 professional library

Implemented and automated-test-backed software flows include:

- Videos, Folders, Continue Watching, Recent, History, Favourites, and Playlists sections
- lazy list and adaptive grid views with stable IDs and persisted preference
- professional search across title, filename, and folder, with normalization, 180 ms derived-query debounce, IME Search, explicit Clear, no-results state, and Android-back-to-clear behavior
- video sorting by name, date added, date modified, duration, size, resolution, and last played, both directions
- folder sorting by name, video count, last modified, and total size
- filters for watched/unwatched/in-progress/favourites, resolution groups, and duration ranges
- MediaStore discovery with a bounded projection and debounced change observation
- user-approved SAF folder sources via `ACTION_OPEN_DOCUMENT_TREE`, persisted permissions/status, exclusions, and provider-aware traversal
- stable source/folder identities that do not depend only on display names
- Room v2 media index/cache, favourites, playlists, sources, exclusions, and library preferences
- explicit `MIGRATION_1_2`, with no destructive migration fallback
- deterministic, cancellable Room media-index loading in 512-row chunks with progressive snapshots
- Continue Watching progress bar, percentage, and time remaining using the existing Step-1 resume/history authority
- Recent and full History with single-item removal and confirmed clear-history flow
- favourites as stable-ID relationships rather than duplicated media rows
- playlist create/rename/delete/add/remove/reorder and real ordered playback queues
- folder/current-visible-list queues and selected-item start index
- professional multi-selection with batch favourite/unfavourite/add-to-playlist actions
- rich media details using available URI, source, MIME, duration, resolution, size, rotation, frame rate, codecs, audio properties, dates, and playback progress
- Android-policy-compliant MediaStore/SAF rename and delete, including provider/system confirmation and truthful unsupported states
- unavailable-source `Locate original` relink with plausibility validation while preserving stable IDs and user relationships
- bounded/cancellable thumbnail loading with a 16 MiB LRU memory cache, request-size policy, invalidation, and placeholder failure path
- 10,000-entry deterministic search/sort/filter tests and Long-safe multi-GB metadata tests

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

Step 2 does not create an Activity-owned player. Library playback requests construct lightweight queues and pass them into the existing service/session path.

## Storage and persistence model

MediaStore and SAF are source truth; Room `media_index` is a recoverable metadata index/cache. Missing or permission-lost sources remain represented as unavailable/recoverable state instead of being silently treated as valid. User relationships and history live separately from the media index.

The app does **not** request `MANAGE_EXTERNAL_STORAGE` merely for convenience. If broad media permission is denied, SAF Open File and Add Folder remain usable.

Room database version 2 retains Step-1 history/playback preferences and adds favourites, playlists/items, library sources, excluded folders, media index rows, and library preferences. `MIGRATION_1_2` is explicit and migration-tested.

## Large-library and large-media policy

Fast discovery avoids opening every video for deep metadata. MediaStore/SAF I/O, thumbnail work, and derived search/sort/filter/grouping run away from the UI thread. Room index reads are deterministic and bounded at 512 rows per query and emit progressive snapshots. Compose renders through lazy containers with stable keys.

The ViewModel ultimately holds lightweight O(n) metadata for the current library rather than using Paging 3, but the database no longer requires one giant index query. Correctness is exercised with 10,000 synthetic entries without brittle wall-clock timing assertions.

File sizes, durations, and positions remain `Long`-safe. Normal library operations do not duplicate multi-GB media or load whole videos into RAM. Real 3 GB+/4K/removable-storage behavior remains Step-10 physical certification.

## Thumbnail policy

`ThumbnailRepository` owns thumbnail work. Requests are dimension-bounded, off-main-thread, cancellable, and cached in a bounded 16 MiB LRU memory cache. Unsupported/missing sources return a placeholder path rather than crashing or blocking playback. The current cache is intentionally memory-only; there is no unbounded bitmap cache.

## Automated build and verification

Authoritative Step-2 implementation gate:

- GitHub Actions run: `34052267617` (#68)
- implementation SHA: `a901f41007643c90cc37a7e0467caa0c3af9bd2f`
- debug build + JVM tests: **PASS**
- release compilation: **PASS**
- lint: **PASS**
- API-35 instrumentation: **PASS**

CI commands:

```bash
gradle --no-daemon :app:assembleDebug :app:testDebugUnitTest
gradle --no-daemon :app:assembleRelease
gradle --no-daemon :app:lintDebug
gradle --no-daemon :app:connectedDebugAndroidTest --stacktrace
```

## Documentation

- `ARCHITECTURE.md` — Step-1 playback ownership plus Step-2 discovery/index/queue/file-action architecture
- `DEPENDENCIES.md` — dependency register and Step-2 dependency decisions
- `PARITY_MATRIX.md` — evidence-backed capability matrix
- `LARGE_MEDIA_AUDIT.md` — large-media/integer safety boundary
- `STEP_1_COMPLETION_REPORT.md` — Step-1 certification record
- `STEP_2_COMPLETION_REPORT.md` — Step-2 A–W certification record

## Roadmap boundary

Step 2 intentionally does **not** implement the final advanced gesture system, professional external subtitle engine, audio DSP/equalizer, real software-decoder routing, later network/cloud/cast features, or final physical-device certification. Those remain later steps. **Step 3 has not been started by this Step-2 completion work.**

## Contribution principle

Do not solve difficult architectural problems by deleting requirements. Preserve working behavior, implement independently, document genuine limitations, keep user data through schema changes, and never fabricate verification results.