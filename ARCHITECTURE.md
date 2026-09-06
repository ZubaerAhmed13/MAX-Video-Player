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

Step 2 does not replace or bypass this model. Folder, all-videos, Continue Watching, and playlist launches create queue requests that feed the existing playback/session path.

## Logical layers

- `core.model` — stable media/domain models, resume policy, queue model, decoder truth status
- `core.database` — Room v2 entities/DAOs/migrations, authoritative playback history, library persistence
- `core.media` — MediaStore discovery, SAF metadata/tree traversal, stable identity, URI availability
- `core.device` — device and MediaCodec capability profiling
- `playback.engine` — application playback abstraction and Media3 implementation
- `playback.session` — MediaSessionService, controller connection, system lifecycle integration
- `feature.library` — library derivation, persistence coordination, thumbnails, file actions, Compose library UI
- `feature.player` — player UI and resume coordination
- `ui` — application theme

The project remains one application module at this stage, while package boundaries keep later module extraction possible without changing domain contracts.

## Step-2 media-library architecture

The professional library separates discovery, persistence, derivation, thumbnails, file actions, and UI.

```text
MediaStoreRepository ─┐
                      ├─> LibraryRepository ─> Room media_index
SafTreeScanner ───────┘          │
                                 ├─ favourites / playlists
PlaybackHistoryRepository ───────┤
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

`LibraryScreen` does not perform MediaStore scans or SAF traversal. `LibraryViewModel` coordinates state and computes derived large-list state on `Dispatchers.Default`. I/O and database work remain in repositories/scanners on I/O-safe paths.

## Source abstraction

Step 2 recognizes indexed MediaStore video sources and user-approved SAF tree sources. The persisted source model stores stable source identity, URI, display name, source type, permission state, availability status, and scan timestamps.

MediaStore folder identity prefers provider/bucket identity, then relative path, then a source-specific root key. SAF folder identity includes source ID plus document-provider parent identity. Folder display name is never the sole primary identity, so two different `Movies` folders can coexist.

SAF traversal uses document-provider APIs and does not assume filesystem paths, known file length, or local/seekable provider behavior. Broad all-files access is not required merely for convenience.

## Media index and source truth

Room `media_index` is a cache/index, not proof that a source still exists. Refresh marks existing rows for a source unavailable, then reconciles newly discovered rows. User metadata such as favourites, playlists, preferences, and authoritative playback history is stored separately so refreshes do not require destructive database recreation.

The app startup can render persisted state while refresh work proceeds asynchronously. MediaStore change notifications are debounced before refresh to avoid event storms during mass file operations.

## Room v2 persistence

Database version 2 retains Step-1 `media_history` and `playback_preferences` and adds:

- `favourites`
- `playlists`
- `playlist_items`
- `library_sources`
- `excluded_folders`
- `media_index`
- `library_preferences`

`MIGRATION_1_2` is explicit. `fallbackToDestructiveMigration()` is not used. Playlist items use relational persistence and foreign-key cascade for playlist deletion. Playlist reorder operations are transaction-oriented and use collision-safe temporary ordering before final indexes are written.

## History, Continue Watching, and resume

There is one authoritative playback-history system. Step 2 does not create a parallel history table. Continue Watching uses the Step-1 `ResumePolicy`, excludes completed/trivial progress, and only launches sources that remain available. Recent and History derive from the same persisted records.

History deletion is independent from media-file deletion.

## Queue integration

Library playback requests contain lightweight `AppMedia` queue entries plus a start index. Selecting a media item from the current visible videos/folder/playlist ordering builds a queue that preserves that ordering. Playlists therefore use the existing previous/next/session architecture rather than launching isolated videos.

Unavailable playlist items may remain represented for recovery/removal rather than crashing queue construction.

## Search, sort, filter, and large lists

Search is normalized case-insensitively and debounced. Sorting supports name, date added, date modified, duration, size, resolution, and last played, with both directions. Current filters include watched, unwatched, in-progress, and favourites.

Derived search/sort/filter/grouping work is performed away from the main thread. Compose rendering uses lazy list/grid containers with stable keys. Deterministic synthetic tests exercise 10,000 media entries without brittle wall-clock assertions.

The current data layer still materializes the reconciled media index as a list rather than using Paging 3; this remains a scalability improvement area and must not be misrepresented as fully paged data access.

## Thumbnail architecture

`ThumbnailRepository` owns thumbnail loading rather than composables performing arbitrary decodes. It bounds requested dimensions, runs work off the UI thread, supports coroutine cancellation, keeps a bounded LRU memory cache, exposes invalidation, and returns null for graceful placeholder rendering on failure.

It does not intentionally decode full-resolution 4K/8K frames for small cards.

## File actions and relink foundation

`MediaFileActionRepository` implements Android-policy-compliant delete/rename operations:

- modern MediaStore operations use system confirmation/write-request mechanisms where required
- SAF operations delegate to the owning document provider
- unsupported operations report an understandable result rather than faking success
- network sources are not treated as local files

`MediaRelinkValidator` checks plausible size, duration, and media type before source replacement. `LibraryRepository.relinkMedia` preserves the original stable media ID and updates history source metadata so favourites/playlists/history can remain linked.

The repository layer exists, but full end-user file-action/relink UI integration must be separately evidenced before those parity rows are marked PASS.

## Large-media design

Normal playback and library operations remain URI/reference based. The project does not copy media on import, load entire sources into RAM, or cast file sizes/durations to `Int`. File sizes and durations are `Long`-safe; tests include values well above `Int.MAX_VALUE`.

Fingerprinting remains bounded and reserved for cases where deeper identity/relink validation is useful rather than every MediaStore scan.

## Device capability and decoder boundary

The Step-1 device capability and decoder truth-status architecture remains intact. Step 2 does not implement the final software decoder, advanced gesture system, professional subtitle engine, or DSP stack; those are later steps.

## Physical certification boundary

Real 3 GB+, 4K/HDR, SD card, USB/OTG, OEM-specific MediaStore/document-provider behavior, Bluetooth hardware, battery, thermal, and broad device-matrix certification are **NOT VERIFIED — DEFERRED TO STEP 10**. Their absence does not justify deleting the architectural requirements, and it does not convert them to PASS by inference.