# MAX Video Player — Architecture through Step 4

## Clean-room boundary

MAX Video Player is an original native Android implementation. MX Player Pro is used only as a behavioral and feature-depth reference. No proprietary code, binaries, assets, package names, branding, certificates, API keys, database schemas, gesture implementations, decoder implementations or copyrighted layouts are reused.

## Playback ownership model

Playback remains **service-owned**, not Activity-owned.

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

Steps 1–4 preserve this ownership. Step 4 does not create a second player to handle subtitles.

## Logical layers through Step 4

- `core.model` — stable media/domain models and playback/subtitle UI state
- `core.database` — Room entities/DAOs/migrations, playback history, library state and subtitle persistence
- `core.media` — MediaStore, SAF, metadata and URI availability
- `core.device` — runtime device/codec capability profile
- `playback.engine` — playback abstraction and Media3 implementation
- `playback.session` — service/session/controller integration and queue/player state
- `feature.library` — library/index/queue/thumbnail/file actions
- `feature.player` — player UI, gestures, display transforms, orientation, PiP and subtitle host integration
- `feature.subtitle` — Step-4 subtitle format/encoding policy, persistence, matching, timing, recovery and controls
- `ui` — application theme

# Step-4 professional subtitle architecture

## Data flow

```text
SubtitleDialog / Player UI
        ↓ user intent
PlaybackConnection ────────────────┐
        ↓                          │
MediaController                    │
        ↓                          │
MediaSessionService                │
        ↓                          │
Media3PlaybackEngine               │
        ↓                          │
SubtitleAwareMediaSourceFactory    │
        ↓                          │
Offset/Resilient subtitle parser   │
                                   │
SubtitleRepository ── Room v3 ─────┘
        ↓
SAF / ContentResolver URI references
```

The UI never owns a second ExoPlayer. External subtitle attachment updates the current `MediaItem` through the existing controller and rebuilds the queue entry while retaining active queue index, position and `playWhenReady`.

## Subtitle state ownership

`PlaybackConnection` publishes `SubtitlePlaybackState` as part of `PlaybackUiState`. It includes:

- enabled/off state
- discovered text tracks
- selected track key
- external associations
- selected external association ID
- current delay
- recoverable subtitle error

`SubtitleRepository` owns durable subtitle relationships, preferences and appearance state. Media3 remains authoritative for actual exposed/selected text tracks.

## Embedded track discovery and identity

Media3 `currentTracks` is inspected for `TRACK_TYPE_TEXT`. Track labels use available language/label/forced/default metadata.

For side-loaded tracks, identity first uses the custom subtitle configuration ID. Because Media3 does not guarantee that `SubtitleConfiguration.id` is preserved as `Format.id` in every source path, Step 4 also performs a deterministic descriptor match using label, MIME type and language. This preserves external-track identity without weakening the actual rendering test.

## External subtitle association model

External subtitle files remain URI/reference based. One media item may own multiple associations.

Each association records:

- stable association ID
- stable media ID
- subtitle URI
- display label
- language
- MIME type
- format
- encoding
- preferred flag
- availability
- synchronization delay

The selected external association and per-media delay are stored separately in subtitle media state.

## Room v3

Step 4 advances the application database to version 3.

New tables:

- `subtitle_associations`
- `subtitle_media_state`

`MIGRATION_2_3` is explicit. Existing `MIGRATION_1_2` is retained so v1 → v3 upgrades execute both migrations. Migration instrumentation verifies Step-1 history/large `Long` values and Step-2 favourites/playlists/library/index/preferences survive the upgrade. No `fallbackToDestructiveMigration()` is introduced.

A small in-memory cache mirrors subtitle association/media state because MediaItem construction and track publication are synchronous. Room writes and provider probing run on I/O dispatchers.

## SAF and external-file access

Manual loading uses `OpenDocument` and persistable read permission where supported. Production descriptor probing is asynchronous.

The repository inspects only bounded subtitle metadata/prefix data. It does not copy media files or load whole videos.

Supported text-family policy:

- SRT / SubRip
- WebVTT
- SSA
- ASS
- TTML / DFXP

Provider MIME aliases and extensions are normalized. Generic XML is accepted only when bounded prefix inspection identifies a TTML-like document.

## Automatic sidecar discovery

For media indexed from user-approved SAF folder trees, the repository can query sibling documents in the same parent folder. Candidate subtitles are scored against the video filename by `SubtitleMatcher`.

Matching prefers exact stem/language-suffix matches and rejects generic/unrelated names. Preferred-language ordering is applied before lower-ranked candidates. Discovery is off-main-thread and does not recurse through unrelated storage during playback.

## Language policy

Preferred subtitle languages are persisted as canonical language codes. `Auto` selection passes the ordered language list to Media3 text-track selection and also enables undetermined text when appropriate.

The professional subtitle panel exposes common language choices while repository APIs remain code-based rather than UI-label based.

## Encoding policy

External subtitle text supports:

- Auto
- UTF-8
- UTF-16 LE
- UTF-16 BE
- Windows-1252

`SubtitleEncodingPolicy` detects UTF BOMs, validates UTF-8 and allows a persisted per-association override. External subtitle bytes can be normalized to UTF-8 before delegation to Media3's text parser. Embedded subtitle streams are not rewritten by this layer.

The default external encoding is a user preference; a selected external association can override it.

## Parser resilience

The Step-4 parser chain composes three responsibilities:

1. optional external-text encoding normalization;
2. subtitle timing offset;
3. delegation to Media3's actual subtitle parser.

External parser failures are converted into recoverable subtitle state rather than escalating into a video playback failure. The video/audio media source remains independently usable.

## Synchronization architecture

`SubtitleTimingPolicy` clamps delay to ±600,000 ms and keeps timing in `Long` microseconds/milliseconds.

Positive delay shifts cues later; negative delay shifts earlier. If a negative shift crosses zero, cue start is clipped to zero and finite duration is reduced safely.

`SubtitleAwareMediaSourceFactory` creates a fresh subtitle parser factory per `MediaItem`, snapshotting the correct delay for that media. This prevents a prefetched queue item from inheriting a previous item's synchronization value.

## Missing/permission-lost subtitle recovery

Persisted external associations are re-probed. Availability can become:

- `AVAILABLE`
- `MISSING`
- `PERMISSION_LOST`
- `UNSUPPORTED`
- `MALFORMED`
- `UNKNOWN`

Blocked/unavailable associations are not attached to the current MediaItem. The association remains visible for recovery.

`Relink` accepts a replacement supported subtitle URI and preserves the media relationship, selected/preferred state and delay. The underlying association ID may change because identity includes the URI.

## Appearance state

`SubtitleStyleState` controls rendering through Media3 `SubtitleView`:

- text scale
- foreground colour
- background colour
- window colour in the state model
- edge style
- edge colour
- bottom padding/margin
- whether embedded cue styling is applied
- whether embedded cue font sizes are applied

The UI intentionally does not expose an Android system-caption-style switch unless that setting is actually applied by the rendering path.

## Step-4 control surface

The subtitle panel exposes:

- Off
- Auto
- manual embedded/external track selection
- load external subtitle
- multiple external associations
- select / relink / remove
- per-file text encoding
- ±50/100/500 ms synchronization adjustments and reset
- matching-sidecar auto-load toggle
- preferred language ordering
- default external encoding
- appearance sliders/choices

The panel is vertically/horizontally scrollable where needed to remain usable on narrow player surfaces.

## Error isolation

Subtitle-specific file/provider/parser failures are not mapped into the generic video playback error overlay when the video source itself is healthy. Instead the subtitle state publishes a recoverable message and the user can relink/remove/change encoding.

# Step-3 player architecture — preserved

Step 4 preserves the Step-3 player state machine and interaction policy: controls/auto-hide, seek/double-tap, brightness, volume, zoom/pan, resize/aspect, display rotation, orientation, fullscreen, lock, speed, repeat/shuffle, PiP and accessibility handling.

Display transforms remain geometric surface transforms. Step 4 does not re-encode video or add a frame-bitmap pipeline.

# Step-2 library architecture — preserved

MediaStore/SAF discovery, Room media index/cache, favourites, playlists, folder exclusions, relink, rename/delete, bounded thumbnail loading and queue planning remain unchanged in ownership.

API-26 and API-28 thumbnail regression jobs remain mandatory CI gates for Step 4.

# Large-media design

Media and external subtitle sources remain reference based. Step 4 adds no artificial media-size or resolution ceiling.

Large media is not copied on subtitle attachment. Subtitle processing is bounded to subtitle files/prefixes and timing metadata rather than video bytes. File sizes, durations, positions, seek values and subtitle timing remain `Long`-safe where applicable.

Physical 3 GB+, 4K/HDR and OEM/device-specific performance are **NOT VERIFIED — DEFERRED TO STEP 10**.

# Verification architecture

Automated Step-4 coverage includes:

- JVM format-policy tests
- filename/language matching tests
- encoding tests
- subtitle timing tests
- Room v1 → v3 and v2 → v3 migration tests
- multiple-association/selection/delay repository persistence tests
- real Media3 parser tests for SRT, WebVTT, ASS, SSA and TTML
- multilingual Unicode/RTL fixture checks
- missing-source/relink/encoding persistence instrumentation
- real service-owned MP4 + side-loaded SRT cue integration on API 35
- retained Step-1/2/3 instrumentation
- API-26/API-28 thumbnail regressions

Implementation gate `94aa7af146e342f8405c6032f8d29bfe9250d780`, Android CI run `34100525570` (#129), passed debug/JVM, release, lint, API-35 instrumentation, API-26 and API-28 jobs.

The documentation-complete head must pass the same CI matrix before merge.

# Later-step boundaries

Step 4 does not implement:

- Step 5 equalizer, boost, audio delay, Bluetooth sync or advanced DSP
- Step 6 software decoder/FFmpeg routing
- Step 7/8 network/cloud/Cast work
- Step 9 full advanced settings/security system
- Step 10 physical certification

# Physical certification boundary

Real 3 GB+/5 GB+/10 GB playback, 4K/HDR colour/performance, SD/USB/OTG, OEM provider behavior, physical Bluetooth/headset routing, cutouts/foldables/external displays, battery, thermal and broad device-matrix testing remain **NOT VERIFIED — DEFERRED TO STEP 10**.