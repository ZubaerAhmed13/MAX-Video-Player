# MAX Video Player — Parity Matrix through Step 3

Status vocabulary: `PASS`, `PARTIAL`, `FAIL`, `NOT VERIFIED`, `NOT IMPLEMENTED`, `NOT APPLICABLE`.

A `PASS` requires implementation, integration, a real functional flow, error handling, and automated evidence where feasible. Physical-device requirements are never inferred from emulator/software evidence.

## Step-3 professional player experience

| Area | Capability | Status | Evidence / boundary |
|---|---|---|---|
| Player UI | Controls overlay | PASS | Original professional overlay integrated with service-owned playback; API-35 Compose/player tests |
| Player UI | Auto hide | PASS | Single ViewModel timer/state policy respects playing, interaction, menus, tutorial, lock and accessibility |
| Player UI | Top bar | PASS | Back, current queue-aware title, queue position, info/options with ellipsis/safe insets |
| Player UI | Bottom bar | PASS | Previous/play-pause/next, time, seek and horizontally scrollable player actions |
| Player UI | Seek bar | PASS | Long-duration conversion boundary, local scrub target and one final exact seek on release |
| Player UI | Buffering UI | PASS | Buffering indicator remains independent of normal controls; Compose test coverage |
| Player UI | Playback error recovery | PASS | Existing mapped errors surfaced with Retry/Back rather than raw exception text |
| Gestures | Single tap | PASS | Surface tap toggles controls unless lock/modal/preparing/accessibility conflict owns input |
| Gestures | Double tap left | PASS | Configurable backward seek with safe clamp; deterministic JVM coverage |
| Gestures | Double tap center | PASS | Real service play/pause toggle |
| Gestures | Double tap right | PASS | Configurable forward seek with safe clamp; deterministic JVM coverage |
| Gestures | Horizontal seek | PASS | Width/duration/sensitivity-based Long-safe seek policy with direction feedback HUD |
| Gestures | Brightness | PASS | Real Activity window brightness, safe clamp, system/default initialization and restoration |
| Gestures | Volume | PASS | Real `AudioManager.STREAM_MUSIC`, runtime max volume and actual post-write HUD fraction |
| Gestures | Pinch zoom | PASS | Two-finger display zoom, bounded 1×–5×; JVM transform coverage |
| Gestures | Pan | PASS | Two-finger pan while transformed; bounds use effective rendered X/Y scale |
| Gestures | Gesture conflict resolution | PASS | Touch slop + direction ownership; surface and ViewModel guards block modal/resume/tutorial/preparing/lock conflicts |
| Display | Fit | PASS | Media3 fit path + display-transform policy |
| Display | Fill | PASS | Explicitly labeled stretch mode |
| Display | Crop | PASS | Media3 zoom/crop path |
| Display | Original / 100% | PASS | Source/viewport-aware original-size transform |
| Display | 16:9 | PASS | Forced display aspect preset |
| Display | 4:3 | PASS | Forced display aspect preset |
| Display | 18:9 | PASS | Forced display aspect preset |
| Display | 21:9 | PASS | Forced display aspect preset |
| Display | Custom aspect | PASS | Positive finite validated ratio; actual custom ratio persisted/recreated |
| Display | Rotation | PASS | Display-only 0°/90°/180°/270° cycle; source file untouched |
| Display | Orientation modes | PASS | Auto, Portrait, Landscape, Reverse Portrait, Reverse Landscape mapped to Android requested orientation |
| Display | Orientation lock | PASS | `LOCK_CURRENT` maps to `SCREEN_ORIENTATION_LOCKED`; mapping JVM-tested |
| Display | Fullscreen | PASS | Player fullscreen preserves same service session; host integration test |
| Display | Immersive mode | PASS | WindowInsetsController on modern Android, compatible legacy fallback, bars restored on exit |
| Player | Screen lock | PASS | Normal controls/gestures hidden/rejected; explicit unlock path Compose-tested |
| Player | Playback speed UX | PASS | 0.25×–4.0×, common presets, 0.05× fine adjustment, optional remembered speed |
| Player | Previous / Next | PASS | Existing service MediaSession queue; real three-item API-35 queue navigation test |
| Player | Repeat | PASS | MediaSession/Media3 repeat Off/One/All on service-owned player |
| Player | Shuffle | PASS | MediaSession/Media3 shuffle flag on existing service queue |
| Player | Playback-ended actions | PASS | Replay plus Next when queue availability permits |
| PiP | Dynamic aspect ratio | PASS | Current item dimensions + rotation, GCD normalization, Android-safe clamp, 16:9 fallback |
| PiP | Session preservation | PASS | API-35 host integration verifies PiP keeps same service MediaSession queue/item |
| PiP | Automatic entry preference | PASS | Opt-in persisted `autoPip`; no forced automatic PiP |
| Accessibility | Control semantics | PASS | Important controls expose meaningful content descriptions/semantics |
| Accessibility | TalkBack/touch exploration | PASS | Live AccessibilityManager listeners; conflicting custom gestures suppressed, clickable controls retained |
| Accessibility | Large text / narrow surfaces | PASS | Dialogs/options use vertical/horizontal scrolling and non-catastrophic title/action layout; software/emulator status only |
| Settings | Gesture preferences | PASS | Seek seconds, sensitivity and gesture toggles persist with safe defaults/clamps |
| Settings | Display preferences | PASS | Orientation, resize and custom aspect persist by stable values |
| Settings | Playback preferences | PASS | Remember speed/value and auto PiP persist; recreation instrumentation |
| Tutorial | Gesture tutorial | PASS | Original first-use/or-menu tutorial with persisted seen state |
| Performance | Low-frequency playback UI publication | PASS | Position state approximately 500 ms while playing / 1,000 ms otherwise, not frame-by-frame |
| Performance | Display transforms | PASS | Surface geometric scale/translation/rotation; no software re-encode/frame bitmap pipeline |
| Performance | Large-media interaction safety | PASS | Long-safe seek values; no media-byte duplication/full-video read/frame sequence introduced |
| Seek preview | Frame thumbnail preview | PARTIAL | `SeekPreviewProvider` architecture/foundation exists; no fake rendered frame preview is claimed |

## Step-2 professional media library — preserved through Step 3

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
| Library | Playlist queue | PASS | Ordered playlist queue starts at selected item and feeds service-owned previous/next |
| Library | List view | PASS | LazyColumn, stable IDs |
| Library | Grid view | PASS | Adaptive LazyVerticalGrid, stable IDs, persisted view preference |
| Library | Multi-selection | PASS | Stable-ID selection with batch favourite, unfavourite and add-to-playlist plus cancel flow |
| Library | Media details | PASS | Dedicated dialog exposes available filename/location/URI/duration/resolution/size/MIME/codecs/frame rate/dates/progress and deep fields |
| Storage | MediaStore | PASS | Efficient projection, stable provider identities, off-main scan, debounced observer refresh |
| Storage | SAF file | PASS | Step-1 OpenDocument path retained; persistable read permission where supported |
| Storage | SAF folder | PASS | OpenDocumentTree addition and provider-aware recursive scanning |
| Storage | Persisted tree permission | PASS | Permission/source status persisted; permission loss becomes recoverable source state |
| Storage | Excluded folders | PASS | Persistent exclude/restore management |
| Storage | Missing source | PASS | Unavailable rows/source states represented consistently |
| Storage | Relink | PASS | `Locate original` + plausibility validation + stable-ID-preserving update |
| Files | Delete | PASS | App confirmation + Android/provider-aware MediaStore/SAF flow |
| Files | Rename | PASS | MediaStore/SAF provider-aware flow with stable-ID reconciliation |
| Media | Thumbnails | PASS | API29+ resolver thumbnails plus API23–28 bounded retriever path; API-26/API-28 regressions remain green in Step-3 CI |
| Media | Metadata | PASS | Fast discovery plus optional deep extraction without deep-scanning every row |
| Performance | Large library | PASS | 10,000-entry derivation coverage, lazy Compose, off-main work, 512-row Room chunks |
| Performance | No main-thread scanning | PASS | MediaStore/SAF I/O, thumbnails and large derived-list work off UI thread |
| Database | v1 → v2 migration | PASS | Explicit `MIGRATION_1_2`, no destructive fallback |
| Database | Collections/sources/index/preferences | PASS | Relational Room v2 entities/DAOs with instrumentation |
| Privacy | Local metadata/history | PASS | No upload path for filenames, history, thumbnails, playlists or inventory |

## Step-1 foundations preserved through Step 3

| Area | Capability | Status | Evidence / boundary |
|---|---|---|---|
| Player | Service-owned playback architecture | PASS | `PlaybackConnection -> MediaController -> MediaSessionService -> MediaSession -> PlaybackEngine -> Media3` retained |
| Player | Local playback / play / pause / seek | PASS | Existing deterministic H.264 local playback integration remains in API-35 suite |
| Player | Resume/history | PASS | Step-1 resume policy remains authoritative; Step-3 player does not bypass resume decision |
| Player | Background/session architecture | PASS | Service ownership retained across Activity/player UI lifecycle |
| Player | Previous / next queue foundation | PASS | Extended by Step-3 real queue host integration |
| Player | Playback speed foundation | PASS | Extended into Step-3 professional UX without replacing service path |
| Player | PiP foundation | PASS | Step-3 dynamic ratio and API-35 same-session integration now software/emulator verified |
| Player | Audio focus / noisy-route handling | PARTIAL | Media3 architecture retained; representative physical hardware routing certification deferred |
| Media | Large-file-safe types | PASS | Long-safe sizes/durations/positions; Step-3 interaction math remains Long-safe |
| Decoder | Software decoder | NOT IMPLEMENTED | Correctly remains Step-6 work; no fake decoder added |
| Subtitles | Professional external subtitle engine | NOT IMPLEMENTED | Correctly remains Step-4 work |
| Audio | Equalizer / boost / advanced DSP | NOT IMPLEMENTED | Correctly remains Step-5 work |

## Automated Step-3 certification

Authoritative implementation gate:

- workflow: `Android CI`
- CI run `34058821634` (#103)
- SHA `13411414c29464aaddfe9b5475eed03b16ddb4c2`
- debug build + JVM tests — **PASS**
- release compilation — **PASS**
- lint — **PASS**
- API-35 full instrumentation — **PASS**
- API-26 legacy-thumbnail regression — **PASS**
- API-28 legacy-thumbnail regression — **PASS**

The API-35 suite includes the retained Step-1 real local H.264 playback test plus Step-3 Compose controls/preferences and a real service-owned three-item queue test covering Previous/Next, seek, fullscreen, Activity recreation and PiP session continuity.

A final CI gate is also required on the documentation-complete PR head before merge.

## Physical certification deferred to Step 10

The following remain **NOT VERIFIED — DEFERRED TO STEP 10** and do not block the Step-3 software/emulator result:

- real 3 GB+ / 5 GB+ / 10 GB+ playback
- physical 3840×2160 4K and HDR playback/color certification
- OEM gesture/fullscreen behavior and high-refresh behavior
- real SD-card and USB/OTG behavior
- OEM/manufacturer-specific MediaStore/document-provider behavior
- physical Bluetooth/headset controls and routing
- real display cutouts, foldables and external displays
- battery, thermal and long-run physical testing
- broad phone/tablet device matrix

## Step-3 overall matrix result

**PASS — all required Step-3 software/emulator exit criteria are implemented and green at implementation SHA `13411414c29464aaddfe9b5475eed03b16ddb4c2`.**

The seek-frame preview row remains intentionally `PARTIAL — architecture/foundation only`, which is an explicitly permitted Step-3 boundary rather than a fake completion claim. Later-step subtitle/audio/decoder/network/security work and Step-10 physical certification remain outside this result.