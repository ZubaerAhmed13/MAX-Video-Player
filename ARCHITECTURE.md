# MAX Video Player — Architecture through Step 2

## Clean-room boundary
MAX Video Player is an original Android implementation. MX Player Pro is used only as a behavioral and feature-depth reference. No proprietary code, binaries, assets, package names, branding, certificates, API keys, database schemas, or decoder implementations are reused.

## Playback ownership model
Playback remains **service-owned**, not Activity-owned. `PlaybackService` (Media3 `MediaSessionService`) owns the Media3 playback engine/session. Compose connects through `PlaybackConnection` / `MediaController`.

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

Step 2 does not replace or bypass this model. Videos, folder, Continue Watching, History, and playlist launches create queue requests that feed the existing playback/session path.

## Logical layers

- `core.model` — stable media/domain models, resume policy, queue model, decoder truth status
- `core.database` — Room v2 entities/DAOs/migrations, authoritative playback history, library persistence/index
- `core.media` — MediaStore discovery, SAF metadata/tree traversal, stable identity, deep metadata extraction, URI availability
- `core.device` — device and MediaCodec capability profiling
- `playback.engine` — application playback abstraction and Media3 implementation
- `playback.session` — MediaSessionService, controller connection, system lifecycle integration
- `feature.library` — library repository/derivation, queue planning, thumbnails, file actions/relink, Compose library UI
- `feature.player` — player UI and resume coordination
- `ui` — application theme

The project remains one application module at this stage; package boundaries keep later module extraction possible without changing domain contracts.

## Step-2 media-library architecture

```text
MediaStoreRepository ─┐
                      ├─> LibraryRepository ─> Room media_index
SafTreeScanner ───────┘          │                  │
                                 │                  └─ count + bounded 512-row pages
PlaybackHistoryRepository ───────┤
                                 ├─ favourites / playlists
                                 ├─ sources / exclusions / preferences
                                 ↓
                         LibraryViewModel
                                 ↓
              search / sort / filter / grouping
                                 ↓
                           LibraryScreen
                                 ↓
                     LibraryPlaybackRequest
                                 ↓
                    service-owned playback
```

`LibraryScreen` never scans MediaStore or SAF directly. Repositories/scanners own source and database I/O. `LibraryViewModel` coordinates flows and performs large derived-list work on `Dispatchers.Default`.

## Source abstraction

Step 2 recognizes indexed MediaStore video sources and user-approved SAF tree sources. Persisted source state includes stable source identity, URI, display name, source type, permission state, availability state, and scan timestamps.

MediaStore folder identity prefers bucket/provider identity, then relative path, then a source-specific root key. SAF folder identity includes source identity plus document-provider parent identity. Folder display name is not the primary key, so different folders named `Movies` can coexist safely.

SAF traversal uses document-provider APIs and does not assume filesystem paths, known file length, or seekable/local provider behavior. Broad all-files access is not required for convenience.

## Media index and incremental loading

Room `media_index` is an index/cache, not proof that the source still exists. Refresh marks prior rows unavailable and reconciles newly discovered source rows. Favourites, playlists, preferences, and authoritative history are separate relationships so a rescan never requires destructive user-data recreation.

The UI can render persisted state while refresh proceeds. MediaStore change notifications are debounced before refresh to avoid event storms.

Step 2 deliberately does not add Paging 3. Instead, `MediaIndexDao` exposes a count plus a deterministic query ordered by title/stable ID using `LIMIT/OFFSET`; `LibraryRepository` reads at most **512 rows per database query**, checks coroutine cancellation between chunks, and emits progressive accumulated snapshots. This removes the previous single giant Room-index query while keeping dependency/architecture complexity appropriate to Step 2.

The ViewModel ultimately holds lightweight O(n) metadata for the current library; that is a documented boundary rather than a claim of infinite-scale paging. Synthetic 10,000-entry tests verify deterministic derivation correctness.

## Room v2 persistence

Database version 2 retains Step-1 `media_history` and `playback_preferences` and adds:

- `favourites`
- `playlists`
- `playlist_items`
- `library_sources`
- `excluded_folders`
- `media_index`
- `library_preferences`

`MIGRATION_1_2` is explicit. `fallbackToDestructiveMigration()` is not used. Playlist items use relational persistence and foreign-key cascade for playlist deletion. Reorder is transaction-oriented with collision-safe temporary indexes before final ordering.

## Search, sort, filter, and navigation

Search state updates immediately; expensive derivation uses a short debounce. It matches normalized title, filename, and folder. UI behavior includes IME Search, explicit Clear, a no-results state, and Android back clearing an active query before leaving the screen.

Video sorting supports name, date added, date modified, duration, size, resolution, and last played in both directions. Folder sorting supports name, video count, last modified, and total size. Filters cover watched/unwatched/in-progress/favourites plus resolution and duration groups. Preferences are persisted through the Room library-preference store.

Derived work runs away from the main thread. Compose uses `LazyColumn`, `LazyVerticalGrid`, and `LazyRow` with stable identifiers/test semantics.

## History, Continue Watching, and resume

There is one authoritative playback-history system. Continue Watching reuses the Step-1 `ResumePolicy`, excludes completed/trivial progress, and exposes a progress bar, percentage, and time remaining. Recent and History derive from the same persisted records. History deletion is independent of actual media-file deletion.

## Favourites, playlists, and queues

Favourites persist stable media IDs rather than duplicating full media records. Playlists use `PlaylistEntity` + ordered `PlaylistItemEntity` rows and support create, rename, delete, add, remove, and reorder.

`LibraryQueuePlanner` and playback requests preserve the selected visible/folder/playlist ordering and start at the selected media item. Unavailable entries do not crash queue construction. The requests feed the existing previous/next MediaSession path rather than launching isolated Activity-owned playback.

## Multi-selection and details

Selection state is UI-scoped and stable-ID-based. Batch actions include favourite, unfavourite, and add-to-playlist while single-media destructive actions stay explicit.

The details dialog exposes available title/filename/folder/source/URI/MIME, duration, resolution, size, rotation, frame rate, video/audio codecs, audio sample rate/channel count, dates, and playback progress. Fast discovery does not deep-extract every file merely to populate optional fields; deep fields are displayed when available in the domain model.

## Thumbnail architecture

`ThumbnailRepository` owns thumbnail loading. It bounds requested dimensions, executes off the UI thread, supports coroutine cancellation, uses a bounded **16 MiB** LRU memory cache, exposes invalidation/clear, and returns null for graceful placeholder rendering. Tests cover size policy and instrumented failure/cancellation boundaries.

No full-resolution 4K/8K decode is intentionally performed for small cards, and the cache is not unbounded. A disk thumbnail cache is not required for Step 2 and is not currently present.

## File actions and relink

`MediaFileActionRepository` and the library UI integrate Android-policy-compliant operations:

- MediaStore delete uses direct/system confirmation mechanisms as Android requires
- SAF delete delegates to the document provider
- MediaStore/SAF rename respects provider/permission behavior
- unsupported operations report truthful failure/unsupported state
- network sources are never treated as local files
- destructive delete receives explicit app confirmation before any Android system confirmation

Unavailable local media exposes `Locate original`. `MediaRelinkValidator` compares plausible media type, size, and duration. On accepted replacement, `LibraryRepository.relinkMedia` preserves the original stable ID and updates source/history metadata so favourites, playlists, and history remain linked.

## Large-media design

Normal playback and library operations remain URI/reference based. The project does not copy media on import, load entire sources into RAM, or cast file sizes/durations to `Int`. Fast library discovery does not run expensive deep extraction or fingerprinting across every item.

## Device capability and later-step boundary

Step-1 device capability and decoder truth-status architecture remains intact. Step 2 does not implement final software decoder routing, the advanced gesture system, professional subtitle engine, DSP/equalizer stack, or later network/cloud/cast feature work.

## Physical certification boundary

Real 3 GB+, 4K/HDR, SD card, USB/OTG, OEM-specific MediaStore/document-provider behavior, Bluetooth hardware, battery, thermal, and broad device-matrix certification are **NOT VERIFIED — DEFERRED TO STEP 10**. This is an explicit project boundary and does not convert those physical claims to PASS by inference.