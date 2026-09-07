# STEP 4 COMPLETION REPORT

# PROFESSIONAL SUBTITLE ENGINE

## 1. Result

**STEP 4 SOFTWARE/EMULATOR RESULT: PASS**

Implementation branch: `step-4-professional-subtitles`

Authoritative implementation gate before documentation finalization:

- implementation SHA: `94aa7af146e342f8405c6032f8d29bfe9250d780`
- workflow: `Android CI`
- run: `34100525570` (#129)
- debug build + JVM tests: **PASS**
- release compilation: **PASS**
- lint: **PASS**
- API-35 full instrumentation: **PASS**
- API-26 legacy-thumbnail regression: **PASS**
- API-28 legacy-thumbnail regression: **PASS**

The PR must not merge until the documentation-complete head also passes the same CI matrix. This report does not pre-invent that later run number.

Physical-device certification remains **NOT VERIFIED — DEFERRED TO STEP 10**.

## 2. Scope completed

Step 4 adds a professional subtitle engine while preserving the Step-1 service-owned player, Step-2 media library and Step-3 professional player UI/gesture/display architecture.

Completed capability groups:

1. embedded text-track discovery and selection
2. external side-loaded subtitles
3. multiple subtitle associations per media
4. SRT / WebVTT / SSA / ASS / TTML format support policy
5. real Media3 parser certification
6. automatic same-folder SAF sidecar matching
7. preferred languages
8. encoding detection and override
9. positive/negative subtitle synchronization
10. persistent Room v3 subtitle relationships/state
11. missing/permission-lost/malformed recovery and relink
12. professional subtitle appearance controls
13. failure isolation so subtitle errors do not become video errors
14. preserved earlier-step regression gates

## 3. Playback architecture preservation

Playback remains:

```text
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

Step 4 did **not** introduce an Activity-owned or subtitle-only ExoPlayer.

External subtitle changes rebuild the current MediaItem/source through the existing service-owned controller and preserve the queue entry, current position and play state.

## 4. Embedded and external subtitle tracks

### Embedded tracks

Media3 text groups are discovered from `currentTracks`. The UI publishes readable track information including available labels, languages and forced/default flags.

The user can select:

- Off
- Auto
- a specific exposed text track

### External tracks

External subtitle files are loaded through Android `OpenDocument` / SAF and remain URI references. The video is not copied or re-encoded.

A media item can own multiple external subtitle associations. Each association can be selected or removed independently; all external associations can also be cleared.

### Track identity hardening

The first Step-4 implementation CI exposed a real API-35 edge case: Media3 did not always preserve `SubtitleConfiguration.id` as `Format.id` for side-loaded tracks.

The final implementation fixes identity without weakening the real rendering test:

1. prefer the explicit external ID when Media3 preserves it;
2. otherwise resolve the external association deterministically by label/MIME/language descriptor matching.

This fix is included in `94aa7af`, whose API-35 instrumentation suite is green.

## 5. Subtitle formats

Step-4 text subtitle families:

- SRT / SubRip
- WebVTT
- SSA
- ASS
- TTML / DFXP

`SubtitleFormatPolicy` normalizes provider MIME aliases and extensions. Generic XML is accepted only when bounded prefix inspection identifies TTML-like content.

### Direct parser evidence

`SubtitleParserFormatsInstrumentedTest` invokes the real Media3 `DefaultSubtitleParserFactory` and confirms emitted cues for:

- SRT
- WebVTT
- ASS
- SSA
- TTML

The SRT fixture contains multilingual Unicode text including Bangla, English, Arabic and Japanese.

## 6. External subtitle loading and SAF policy

Manual loading uses `OpenDocument` and requests persistable read permission where supported.

The production professional subtitle panel uses asynchronous descriptor/access probing before attachment. Unsupported or unavailable files produce subtitle-specific recovery UI rather than blocking video playback.

File access remains reference-based. Step 4 does not:

- copy the video
- copy a multi-GB source merely to add subtitles
- burn subtitles into frames
- transcode media
- read the entire video into memory

## 7. Multiple subtitle association persistence

Room v3 introduces:

### `subtitle_associations`

Stores:

- association ID
- stable media ID
- subtitle URI
- display name
- language
- MIME type
- format
- encoding
- added timestamp
- preferred state
- availability
- delay

### `subtitle_media_state`

Stores:

- stable media ID
- selected external association ID
- current subtitle delay
- update timestamp

The repository mirrors this relational state into a small synchronous cache required for MediaItem construction and track publication. Durable writes remain Room-backed.

## 8. Database migration

Database version changes from v2 to v3 through explicit `MIGRATION_2_3`.

`MIGRATION_1_2` is retained. A v1 installation therefore upgrades by v1 → v2 → v3 rather than destructive recreation.

Instrumentation verifies:

- Step-1 history survives
- multi-GB `Long` size values survive
- Step-2 favourites survive
- playlists/items survive
- library sources/index/preferences survive
- playback preferences survive
- new subtitle tables are available after migration

No destructive migration fallback was added.

## 9. Automatic matching sidecars

For media originating from a user-approved SAF tree, Step 4 can query sibling documents in the same folder and score candidate subtitle filenames against the video filename.

The matcher supports:

- exact stem matches
- common language suffixes
- deterministic scoring
- preferred-language ordering
- rejection of unrelated/generic candidates below threshold

The process is off-main-thread and bounded to the approved folder relationship rather than scanning arbitrary device storage during playback.

## 10. Preferred languages

Subtitle preferences persist an ordered canonical language list.

The list is used for:

- Media3 Auto text selection
- external sidecar prioritization

The professional control surface exposes common languages while the repository stores canonical language codes.

## 11. Encoding support

Supported external text encoding modes:

- Auto
- UTF-8
- UTF-16 LE
- UTF-16 BE
- Windows-1252

Auto mode recognizes BOMs and valid UTF-8. A per-association override can be stored durably.

External subtitle text can be normalized into UTF-8 before delegation to the real Media3 parser. Embedded streams are not rewritten by this layer.

Instrumentation verifies per-file Windows-1252 override persistence after recovery/relink.

## 12. Subtitle synchronization

`SubtitleTimingPolicy` uses `Long` values and clamps delay to:

- minimum: `-600_000 ms`
- maximum: `+600_000 ms`

Positive delay shows cues later. Negative delay shows them earlier.

If a negative shift would cross zero, the cue start is clipped to zero and finite duration is reduced safely.

The UI provides:

- -500 ms
- -100 ms
- -50 ms
- +50 ms
- +100 ms
- +500 ms
- Reset

A fresh parser factory is created per MediaItem so queue prefetch cannot leak one video's delay into another.

## 13. Subtitle recovery and relink

Availability model:

- AVAILABLE
- MISSING
- PERMISSION_LOST
- UNSUPPORTED
- MALFORMED
- UNKNOWN

Persisted associations are revalidated. Blocked/unavailable subtitle files are not attached to the current MediaItem.

When a subtitle becomes missing or permission is lost:

- video playback remains independent
- the relationship remains visible
- the user can relink a replacement file
- selected/preferred state is preserved
- delay is preserved
- encoding can be updated and persisted

`SubtitleRecoveryInstrumentedTest` certifies missing → recoverable → relink behavior.

## 14. Malformed subtitle isolation

The external subtitle parser path wraps Media3 parsing so external decoding/parsing failure becomes recoverable subtitle state rather than a generic media playback failure.

This prevents a malformed side-loaded subtitle from taking down a healthy video stream.

## 15. Appearance controls

Step 4 exposes real renderer-backed controls for:

- text scale
- text colour
- background colour
- edge style
- edge colour
- bottom/vertical margin
- embedded cue styling on/off
- embedded cue font sizes on/off

These map to Media3 `SubtitleView` / caption style APIs.

An Android system-caption-style toggle is **not** exposed because the current renderer does not apply that setting. Step 4 does not ship a cosmetic fake control.

## 16. Professional subtitle control surface

The panel includes:

- Show subtitles switch
- Off
- Auto
- manual tracks
- Open subtitle file
- multiple external association rows
- Select
- Relink when unavailable
- Remove
- Remove all
- per-file encoding
- sync controls
- auto-load matching-sidecar toggle
- preferred language choices
- default encoding
- appearance controls

Vertical and horizontal scrolling are used where necessary to protect narrow-screen usability.

## 17. Automated evidence

### JVM

- `SubtitleFormatPolicyTest`
- `SubtitleMatcherTest`
- `SubtitleEncodingPolicyTest`
- `SubtitleTimingTest`

### Android instrumentation

- `SubtitleRepositoryInstrumentedTest`
- `SubtitleParserFormatsInstrumentedTest`
- `SubtitleRecoveryInstrumentedTest`
- Room migration tests
- retained player/library instrumentation
- real service-owned side-loaded SRT rendering integration

### Legacy regression

- API 26 thumbnail instrumentation — PASS at implementation gate
- API 28 thumbnail instrumentation — PASS at implementation gate

## 18. Implementation CI record

Android CI run `34100525570` (#129) for implementation SHA `94aa7af146e342f8405c6032f8d29bfe9250d780`:

| Gate | Result |
|---|---|
| Debug build + JVM tests | PASS |
| Release compilation | PASS |
| Lint | PASS |
| API-35 full instrumentation | PASS |
| API-26 legacy thumbnail regression | PASS |
| API-28 legacy thumbnail regression | PASS |

No Step-4 test was skipped or weakened to obtain this result.

## 19. Preserved earlier-step behavior

Step 4 intentionally preserves:

- Step-1 service/session playback and resume/history
- Step-2 media library, Room relationships, MediaStore/SAF, playlists and thumbnails
- Step-3 professional controls, gestures, display modes, orientation, lock, speed, queue/repeat/shuffle, PiP and accessibility behavior

The Step-3 seek-frame preview remains **PARTIAL — architecture/foundation only**. Step 4 does not change that evidence status.

## 20. Performance and large-media boundary

Application-level subtitle work is bounded and reference based.

Step 4 does not add:

- full-video reads
- source duplication
- subtitle burn-in
- media re-encoding
- artificial media file-size ceiling
- artificial media resolution ceiling

Physical validation for 3 GB+, 4K/HDR, long-run battery/thermal and OEM-specific providers remains Step 10.

## 21. Not part of Step 4

Not implemented or certified here:

- Step 5 equalizer / boost / audio delay / advanced DSP
- Step 6 software decoder / FFmpeg routing
- later SMB/WebDAV/FTP/cloud/Cast work
- full advanced settings/security roadmap
- Step-10 physical-device certification

## 22. Final completion rule

Step 4 may merge only after the documentation-complete PR head passes the same Android CI matrix as the implementation gate.

If any required final-head job fails, the status returns to **NOT COMPLETE** until the root cause is fixed and the exact head is green.

Step 5 must not begin automatically as part of this completion task.