# MAX Video Player — Parity Matrix through Step 2

Status vocabulary: `PASS`, `PARTIAL`, `FAIL`, `NOT VERIFIED`, `NOT IMPLEMENTED`, `NOT APPLICABLE`.

A `PASS` requires implementation, integration, a real functional flow, error handling, and automated evidence where feasible. Physical-device requirements are not inferred from emulator/software evidence.

## Step-2 professional media library

| Area | Capability | Status | Evidence / boundary |
|---|---|---|---|
| Library | Videos | PASS | MediaStore/SAF-indexed videos feed persistent library state and lazy Compose list/grid rendering |
| Library | Folders | PASS | Provider/source-aware folder keys, folder browser, folder detail and exclusion workflow implemented |
| Library | Search | PASS | Debounced title/filename/folder search; deterministic 10,000-item JVM test |
| Library | Sorting | PASS | Name, added/modified date, duration, size, resolution, last played; asc/desc and preference persistence |
| Library | Filtering | PARTIAL | Watched, unwatched, in-progress and favourites implemented; resolution/duration filters not yet exposed |
| Library | Continue Watching | PASS | Uses the authoritative Step-1 `ResumePolicy` and history; completed/trivial progress excluded |
| Library | Recently Played | PASS | Derived from persistent history ordered by `lastPlayedAtMs` |
| Library | Full History | PASS | Persistent chronological history, replay/resume source, single-item removal, confirmed clear-history flow |
| Library | Favourites | PASS | Room-backed stable-ID relationship; add/remove/list and database persistence coverage |
| Library | Playlists | PASS | Room-backed create/rename/delete/add/remove/open flows and missing-entry handling |
| Library | Playlist reorder | PASS | Transaction-oriented collision-safe reorder implementation; ordered persistence coverage |
| Library | Folder queue | PASS | Selecting from folder detail builds a queue matching the visible folder ordering |
| Library | Playlist queue | PASS | Selecting a playlist media item builds a real ordered queue and starts at selected item |
| Library | List view | PASS | LazyColumn with stable media keys |
| Library | Grid view | PASS | Adaptive LazyVerticalGrid with stable media keys; preference persists |
| Library | Multi-selection | NOT IMPLEMENTED | Step-2 specification requests professional multi-select/architecture; no integrated selection mode yet |
| Library | Media details | PARTIAL | Useful duration/resolution/size/folder metadata is visible on cards; dedicated full details action with codec/date/source fields is not yet integrated |
| Storage | MediaStore | PASS | Efficient projection, provider IDs/metadata, off-main-thread scan, debounced ContentObserver refresh |
| Storage | SAF file | PASS | Step-1 OpenDocument path remains; persistable read grant requested where supported |
| Storage | SAF folder | PASS | OpenDocumentTree source addition and provider-aware recursive scanning implemented |
| Storage | Persisted tree permission | PASS | Permission result stored with source; permission loss maps to source state |
| Storage | Excluded folders | PASS | Persistent exclusion and restore management implemented |
| Storage | Missing source | PASS | Indexed rows can remain unavailable; lost SAF permission/source does not crash library |
| Storage | Relink | PARTIAL | Stable-ID-preserving repository + plausibility validator/tests exist; complete end-user picker/recovery UI is not integrated |
| Files | Delete | PARTIAL | Android-policy-compliant MediaStore/SAF repository exists with system confirmation results; complete library UI/action-result integration is not yet present |
| Files | Rename | PARTIAL | Provider/MediaStore-safe repository exists and reports unsupported states; complete library UI/action-result integration is not yet present |
| Media | Thumbnails | PARTIAL | Dedicated bounded/cancellable memory-cached repository and placeholder failure path; dedicated thumbnail test matrix remains incomplete |
| Media | Metadata | PARTIAL | Fast MediaStore metadata and Step-1 deep extractor exist; full dedicated media-details integration/deep-cache workflow is incomplete |
| Performance | Large library | PARTIAL | 10,000-entry deterministic derivation test and lazy Compose rendering; data index is still materialized as a list rather than paged/chunked |
| Performance | No main-thread scanning | PASS | MediaStore/SAF I/O and large derived-list work run off UI thread |
| Database | v1 → v2 migration | PASS | Explicit `MIGRATION_1_2`; no destructive fallback; Android migration test protects history/resume values |
| Database | Favourites/playlists/sources/index/preferences | PASS | Relational Room v2 entities/DAOs and persistence tests |
| Privacy | Local metadata/history | PASS | No Step-2 upload path for filenames, history, thumbnails or library inventory |

## Step-1 foundations preserved during Step 2

| Area | Capability | Status | Evidence / boundary |
|---|---|---|---|
| Player | Service-owned playback architecture | PASS | Playback remains `PlaybackConnection -> MediaController -> MediaSessionService -> MediaSession -> PlaybackEngine -> Media3` |
| Player | Local playback / play / pause / seek | PASS | Existing Step-1 automated integration path retained |
| Player | Previous / next queue foundation | PASS | Existing playback queue/session architecture retained and consumed by Step-2 library queues |
| Player | Playback speed | PASS | Step-1 implementation retained |
| Player | PiP foundation | PARTIAL | Architecture retained; physical/device-specific certification deferred |
| Player | Audio focus / noisy-route handling | PARTIAL | Media3 implementation retained; representative hardware routing certification deferred |
| Media | Large-file-safe types | PASS | Long-safe file sizes/durations retained; Step-2 tests include 5–10 GB metadata values |
| Decoder | Software decoder | NOT IMPLEMENTED | Correctly remains later Step 6 work; no fake decoder added |
| Subtitles | Professional external subtitle engine | NOT IMPLEMENTED | Correctly remains later Step 4 work |
| Audio | Equalizer / boost / advanced DSP | NOT IMPLEMENTED | Correctly remains later Step 5 work |

## Physical certification deferred to Step 10

The following remain **NOT VERIFIED — DEFERRED TO STEP 10** and do not block Step-2 software/emulator work:

- real 3 GB+ / 5 GB+ / 10 GB+ playback
- real 3840×2160 4K and HDR playback
- real SD-card and USB/OTG behavior
- OEM/manufacturer-specific MediaStore and document-provider behavior
- physical Bluetooth/headset behavior
- battery, thermal and long-run physical testing
- broad phone/tablet device matrix

## Step-2 overall matrix result

**PARTIAL** until the remaining mandatory software gaps are closed and a final green CI run confirms all automated gates. In particular, the current branch must not be called Step-2 PASS while multi-selection, full media-details/file-action/relink UI integration, dedicated thumbnail coverage, and truly incremental/paged data loading remain incomplete.