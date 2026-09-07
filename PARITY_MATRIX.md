# MAX Video Player — Parity Matrix through Step 4

Status vocabulary: `PASS`, `PARTIAL`, `FAIL`, `NOT VERIFIED`, `NOT IMPLEMENTED`, `NOT APPLICABLE`.

A `PASS` requires implementation, integration, a real functional flow, error handling and automated evidence where feasible. Physical-device requirements are never inferred from emulator/software evidence.

## Step-4 professional subtitle engine

| Area | Capability | Status | Evidence / boundary |
|---|---|---|---|
| Subtitle tracks | Embedded text-track discovery | PASS | Media3 `currentTracks` text groups mapped into playback state |
| Subtitle tracks | Off | PASS | Text track type can be disabled through Media3 track-selection parameters |
| Subtitle tracks | Auto | PASS | Clears manual text overrides, applies preferred languages and undetermined-text policy |
| Subtitle tracks | Manual embedded selection | PASS | Real Media3 track-group override by published track key |
| Subtitle tracks | External manual selection | PASS | Durable association + rebuilt MediaItem + Media3 override |
| Subtitle tracks | External identity robustness | PASS | Custom ID when preserved; deterministic label/MIME/language fallback otherwise; API-35 integration gate green |
| External files | OpenDocument / SAF loading | PASS | URI/reference-based external loading with persistable permission where available |
| External files | Asynchronous production probing | PASS | Descriptor/availability probing runs through coroutine/I/O path in professional subtitle panel |
| External files | Multiple subtitle associations per media | PASS | Room-backed list with preferred/selected relationship and instrumentation coverage |
| External files | Select / remove | PASS | Per-association select/remove plus remove-all path |
| External files | Missing-file state | PASS | Availability refresh marks missing source without turning it into video failure |
| External files | Permission-loss state | PASS | Distinct recoverable availability state; unavailable association excluded from MediaItem |
| External files | Relink | PASS | Replacement URI re-associated while selected state and delay are preserved; instrumentation coverage |
| Format | SRT | PASS | Policy + direct real Media3 parser instrumentation + real service-owned MP4/SRT cue integration |
| Format | WebVTT | PASS | Policy + direct real Media3 parser instrumentation |
| Format | SSA | PASS | Policy + direct real Media3 parser instrumentation |
| Format | ASS | PASS | Policy + direct real Media3 parser instrumentation |
| Format | TTML / DFXP | PASS | Policy + TTML content detection + direct real Media3 parser instrumentation |
| Format | Arbitrary XML subtitles | NOT APPLICABLE | Generic XML is rejected unless bounded prefix inspection identifies TTML-like content |
| Language | Preferred languages | PASS | Canonical codes persisted and ordered for Auto/sidecar policy |
| Language | Filename language suffix detection | PASS | Matcher covers common aliases/codes with deterministic JVM tests |
| Language | Multilingual Unicode / RTL | PASS | Parser fixture includes Bangla, English, Arabic and Japanese text |
| Sidecars | Automatic same-folder matching | PASS | User-approved SAF sibling query, filename scoring and language priority off main thread |
| Sidecars | Unrelated/generic false-match protection | PASS | Matcher scoring/threshold/generic-stem policy with JVM tests |
| Encoding | Auto detection | PASS | BOM/valid UTF-8 detection with bounded prefix policy |
| Encoding | UTF-8 | PASS | Supported and directly covered by parser fixtures |
| Encoding | UTF-16 LE | PASS | Detection/override + normalization tests |
| Encoding | UTF-16 BE | PASS | Detection/override + normalization tests |
| Encoding | Windows-1252 | PASS | Per-file override/persistence and normalization tests |
| Encoding | Per-association override | PASS | Room `encoding` state updated and retained; recovery instrumentation |
| Sync | Positive delay | PASS | Long-safe parser timing shift; JVM tests |
| Sync | Negative delay | PASS | Long-safe earlier shift with zero clipping; JVM tests |
| Sync | Bounded adjustment | PASS | ±600,000 ms clamp; UI provides ±50/100/500 ms and reset |
| Sync | Per-media persistence | PASS | `subtitle_media_state` stores selected external ID/delay |
| Sync | Queue isolation | PASS | Fresh per-MediaItem parser factory snapshots delay so prefetch cannot inherit prior item delay |
| Appearance | Text size | PASS | Persistent scale applied through `SubtitleView` |
| Appearance | Text colour | PASS | Persistent foreground colour applied through caption style |
| Appearance | Background | PASS | Persistent background colour applied through caption style |
| Appearance | Edge style | PASS | None/outline/drop-shadow/raised/depressed mapped to Media3 caption style |
| Appearance | Edge colour | PASS | Persistent edge colour applied |
| Appearance | Bottom/vertical margin | PASS | Persistent bottom-padding fraction applied |
| Appearance | Embedded cue styling toggle | PASS | `SubtitleView.setApplyEmbeddedStyles` |
| Appearance | Embedded cue font-size toggle | PASS | `SubtitleView.setApplyEmbeddedFontSizes` |
| Appearance | Android system caption style toggle | NOT IMPLEMENTED | Intentionally not exposed because current renderer does not apply that setting |
| Recovery | Malformed external subtitle isolation | PASS | Resilient external parser path reports recoverable subtitle state instead of generic video failure |
| Recovery | Unsupported external source | PASS | Unsupported format rejected before attachment or represented as blocked availability |
| Persistence | Room v3 subtitle associations | PASS | Dedicated association/media-state tables and DAO instrumentation |
| Persistence | v2 → v3 migration | PASS | Explicit migration; Step-2 favourites/playlists/library/index/preferences preserved |
| Persistence | v1 → v3 migration | PASS | Sequential v1→v2→v3 migration; Step-1 history and multi-GB Long values preserved |
| Performance | No whole-video subtitle processing | PASS | External subtitles are URI references; no source-video copy/re-encode/full-read added |
| Performance | Bounded subtitle probing | PASS | Prefix/metadata size guards; sidecar discovery limited to sibling documents in approved tree |
| Privacy | Local subtitle metadata/state | PASS | No upload/provider network path introduced in Step 4 |

## Step-3 professional player experience — preserved through Step 4

| Area | Capability | Status | Evidence / boundary |
|---|---|---|---|
| Player UI | Controls / auto-hide / buffering / error recovery | PASS | Retained Step-3 Compose/service integration; Step-4 full API-35 suite green |
| Gestures | Single/double tap, horizontal seek, brightness, volume | PASS | Retained Step-3 implementation and tests |
| Gestures | Pinch zoom / pan / conflict ownership | PASS | Retained pure-policy + integration architecture |
| Display | Fit / Fill / Crop / Original / aspect presets / custom | PASS | Retained Step-3 display-transform path |
| Display | Rotation / orientation / fullscreen / immersive | PASS | Retained host/player integration |
| Player | Screen lock | PASS | Retained genuine touch suppression/unlock path |
| Player | Playback speed | PASS | 0.25×–4.0× retained through service-owned player |
| Player | Previous / Next / Repeat / Shuffle | PASS | Existing MediaSession queue retained |
| PiP | Dynamic ratio / same-session continuity | PASS | Retained Step-3 behavior |
| Accessibility | Semantics / touch-exploration protection | PASS | Retained Step-3 behavior |
| Seek preview | Frame thumbnail preview | PARTIAL | Architecture/foundation only; Step 4 does not fabricate rendered thumbnails |

## Step-2 professional media library — preserved through Step 4

| Area | Capability | Status | Evidence / boundary |
|---|---|---|---|
| Library | Videos / Folders / Continue / Recent / History | PASS | Existing Step-2 library retained |
| Library | Favourites / Playlists / queues | PASS | Room relationships and queue planner retained |
| Library | Search / sort / filter | PASS | Existing deterministic derivation retained |
| Storage | MediaStore / SAF folders / persisted permissions | PASS | Existing source architecture retained |
| Storage | Missing source / relink | PASS | Existing media relink remains separate from subtitle relink |
| Files | Rename / delete | PASS | Existing provider-aware flows retained |
| Media | Thumbnails | PASS | API-26/API-28 legacy regression jobs green in Step-4 implementation gate |
| Performance | Large library | PASS | Existing lazy UI, bounded Room chunks and off-main work retained |
| Database | v1 → v2 migration | PASS | Retained and additionally exercised through v1 → v3 migration path |

## Step-1 foundations preserved through Step 4

| Area | Capability | Status | Evidence / boundary |
|---|---|---|---|
| Player | Service-owned playback architecture | PASS | `PlaybackConnection -> MediaController -> MediaSessionService -> MediaSession -> PlaybackEngine -> Media3` retained |
| Player | Local playback / play / pause / seek | PASS | Existing API-35 real local media integration retained |
| Player | Resume/history | PASS | Existing policy/Room history retained through v3 migration |
| Player | Background/session architecture | PASS | Service ownership unchanged by subtitle work |
| Media | Large-file-safe application types | PASS | Long-safe media/timing values retained |
| Decoder | Software decoder | NOT IMPLEMENTED | Correctly remains Step 6 work |
| Audio | Equalizer / boost / advanced DSP | NOT IMPLEMENTED | Correctly remains Step 5 work |

## Automated Step-4 certification

Authoritative implementation gate before final documentation commit:

- workflow: `Android CI`
- CI run: `34100525570` (#129)
- implementation SHA: `94aa7af146e342f8405c6032f8d29bfe9250d780`
- debug build + JVM tests — **PASS**
- release compilation — **PASS**
- lint — **PASS**
- API-35 full instrumentation — **PASS**
- API-26 legacy-thumbnail regression — **PASS**
- API-28 legacy-thumbnail regression — **PASS**

The API-35 suite contains the Step-4 repository/parser/recovery tests and the real service-owned MP4 + side-loaded SRT cue path in addition to retained earlier-step coverage.

A final identical CI gate is required on the documentation-complete PR head before merge.

## Physical certification deferred to Step 10

The following remain **NOT VERIFIED — DEFERRED TO STEP 10**:

- real 3 GB+ / 5 GB+ / 10 GB+ playback with subtitles
- physical 3840×2160 4K and HDR playback/colour certification with subtitle overlay
- OEM document-provider/SAF permission behavior
- real SD-card and USB/OTG behavior
- physical Bluetooth/headset controls/routing
- cutouts, foldables and external displays
- battery, thermal and long-run physical testing
- broad phone/tablet device matrix

## Step-4 overall matrix result

**PASS — all required Step-4 software/emulator subtitle-engine criteria are implemented and the implementation gate is green.**

This result does not upgrade Step-10 physical certification, the Step-3 seek-preview PARTIAL row, or later Step-5+ audio/decoder/network/security work.