# MAX Video Player — Parity Matrix through Step 2

Status vocabulary: `PASS`, `PARTIAL`, `FAIL`, `NOT VERIFIED`, `NOT IMPLEMENTED`, `NOT APPLICABLE`.

A `PASS` requires implementation, integration, a real functional flow, error handling, and automated evidence where feasible. Physical-device requirements are never inferred from emulator/software evidence.

## Step-2 professional media library

| Area | Capability | Status | Evidence / boundary |
|---|---|---|---|
| Library | Videos | PASS | MediaStore/SAF-indexed videos feed persistent library state and lazy list/grid rendering |
| Library | Folders | PASS | Source/provider-aware keys, folder browser/detail, counts/sizes, exclusion/restore, selectable folder sorting |
| Library | Search | PASS | Debounced normalized title/filename/folder search plus IME Search, Clear, no-results and Android-back behavior; 10,000-item JVM test + API-35 UI test |
| Library | Sorting | PASS | Video: name, added/modified, duration, size, resolution, last played; folder: name, count, modified, size; asc/desc persistence |
| Library | Filtering | PASS | watched, unwatched, in-progress, favourites, resolution groups, duration ranges |
| Library | Continue Watching | PASS | Step-1 ResumePolicy/history reused; trivial/completed excluded; progress bar, percentage, time remaining |
| Library | Recently Played | PASS | Derived from persistent history ordered by last played |
| Library | Full History | PASS | Persistent history, replay/resume source, single-item remove, confirmed clear-history distinct from file delete |
| Library | Favourites | PASS | Room-backed stable-ID relation; add/remove/list + persistence coverage |
| Library | Playlists | PASS | Room-backed create/rename/delete/add/remove/open; unavailable-item handling |
| Library | Playlist reorder | PASS | Transactional collision-safe reorder; persistence/order tests |
| Library | All/visible queue | PASS | Selected media starts real queue preserving visible ordering |
| Library | Folder queue | PASS | Folder selection builds queue in current folder ordering |
| Library | Playlist queue | PASS | Ordered playlist queue starts at selected item; previous/next planner tests |
| Library | List view | PASS | LazyColumn, stable IDs |
| Library | Grid view | PASS | Adaptive LazyVerticalGrid, stable IDs, persisted view preference |
| Library | Multi-selection | PASS | Stable-ID selection with batch favourite, unfavourite and add-to-playlist plus cancel flow |
| Library | Media details | PASS | Dedicated dialog exposes available filename/location/URI/duration/resolution/size/MIME/codecs/frame rate/dates/progress and other deep fields |
| Storage | MediaStore | PASS | Efficient projection, stable provider identities, off-main scan, debounced observer refresh; post-rename scans reconcile known provider URIs back to the historical stable ID |
| Storage | SAF file | PASS | Step-1 OpenDocument path retained; persistable read permission where supported |
| Storage | SAF folder | PASS | OpenDocumentTree addition and provider-aware recursive scanning; permission and provider failures consistently mark indexed rows unavailable before returning/throwing |
| Storage | Persisted tree permission | PASS | Permission/source status persisted; permission loss becomes recoverable source state |
| Storage | Excluded folders | PASS | Persistent exclude/restore management |
| Storage | Missing source | PASS | Unavailable rows/source states are represented consistently for permission loss and generic SAF provider failure rather than remaining falsely available |
| Storage | Relink | PASS | `Locate original` picker + plausibility validator + stable-ID-preserving repository update; rename/rescan reconciliation regression tests retain the historical ID |
| Files | Delete | PASS | Explicit app confirmation, Android/provider-aware MediaStore/SAF flow, system confirmation support, relational cleanup tests |
| Files | Rename | PASS | MediaStore/SAF provider-aware flow plus scan-boundary stable-ID reconciliation; rename→rescan regression tests verify favourites/playlists/history identity can remain attached |
| Media | Thumbnails | PASS | API 29+ ContentResolver thumbnails; API 27–28 scaled MediaMetadataRetriever; API 23–26 legacy retriever + bounded downscale; API-26/API-28 emulator tests and API-level JVM policy tests pass |
| Media | Metadata | PASS | Fast MediaStore metadata plus optional deep extractor; details surface deep fields when available without deep-scanning every index row |
| Performance | Large library | PASS | 10,000-entry deterministic derivation tests, lazy Compose, off-main work, 512-row deterministic cancellable Room chunks with progressive snapshots |
| Performance | No main-thread scanning | PASS | MediaStore/SAF I/O, thumbnails and large derived-list work stay off UI thread |
| Database | v1 → v2 migration | PASS | Explicit MIGRATION_1_2, no destructive fallback, Android migration test protects Step-1 history/resume/Long values |
| Database | Collections/sources/index/preferences | PASS | Relational Room v2 entities/DAOs with database instrumentation |
| Privacy | Local metadata/history | PASS | No Step-2 upload path for filenames, history, thumbnails, playlists or inventory |

## Step-1 foundations preserved during Step 2

| Area | Capability | Status | Evidence / boundary |
|---|---|---|---|
| Player | Service-owned playback architecture | PASS | Playback remains `PlaybackConnection -> MediaController -> MediaSessionService -> MediaSession -> PlaybackEngine -> Media3` |
| Player | Local playback / play / pause / seek | PASS | Existing Step-1 local playback integration test retained in final API-35 suite |
| Player | Previous / next queue foundation | PASS | Existing session queue architecture retained and consumed by Step-2 queues |
| Player | Playback speed | PASS | Step-1 implementation retained |
| Player | PiP foundation | PARTIAL | Architecture retained; physical/device-specific certification remains later |
| Player | Audio focus / noisy-route handling | PARTIAL | Media3 implementation retained; representative physical hardware routing certification deferred |
| Media | Large-file-safe types | PASS | Long-safe sizes/durations/positions; tests include multi-GB metadata values |
| Decoder | Software decoder | NOT IMPLEMENTED | Correctly remains later Step-6 work; no fake decoder added |
| Subtitles | Professional external subtitle engine | NOT IMPLEMENTED | Correctly remains later Step-4 work |
| Audio | Equalizer / boost / advanced DSP | NOT IMPLEMENTED | Correctly remains later Step-5 work |

The two Step-1 rows marked PARTIAL above are physical/later-step certification boundaries, not missing Step-2 library requirements.

## Automated Step-2 certification

Original Step-2 implementation gate:

- CI run `34052267617` (#68)
- SHA `a901f41007643c90cc37a7e0467caa0c3af9bd2f`
- debug build + JVM tests — PASS
- release compilation — PASS
- lint — PASS
- API-35 instrumentation — PASS

Post-certification hardening gate for rename/rescan identity, Android 23–28 thumbnails, and SAF failure availability consistency:

- CI run `34054268096` (#73)
- SHA `7d2170f4003f7f6a1b60b843583bac623395e730`
- debug build + JVM tests — PASS
- release compilation — PASS
- lint — PASS
- API-35 full instrumentation — PASS
- API-26 targeted legacy-thumbnail instrumentation — PASS
- API-28 targeted legacy-thumbnail instrumentation — PASS

## Physical certification deferred to Step 10

The following remain **NOT VERIFIED — DEFERRED TO STEP 10** and do not block the Step-2 software/emulator result:

- real 3 GB+ / 5 GB+ / 10 GB+ playback
- real 3840×2160 4K and HDR playback
- real SD-card and USB/OTG behavior
- OEM/manufacturer-specific MediaStore/document-provider behavior
- physical Bluetooth/headset behavior
- battery, thermal and long-run physical testing
- broad phone/tablet device matrix

## Step-2 overall matrix result

**PASS — software/emulator Step-2 requirements and the three post-certification hardening defects are resolved with green automated evidence.**

Later-step capabilities and Step-10 physical certification remain explicitly outside this result. Step 3 has not been started.
