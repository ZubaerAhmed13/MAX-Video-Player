# MAX Video Player — Architecture through Step 3

## Clean-room boundary

MAX Video Player is an original Android implementation. MX Player Pro is used only as a behavioral and feature-depth reference. No proprietary code, binaries, assets, package names, branding, certificates, API keys, database schemas, gesture implementations, decoder implementations, or copyrighted layouts are reused.

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

Step 3 does not replace or bypass this model. Player previous/next, speed, repeat, shuffle, PiP continuity, recreation, notification controls and external media-button architecture operate through the existing service/session.

## Logical layers

- `core.model` — stable media/domain models, resume policy, playback UI state, decoder truth status
- `core.database` — Room v2 entities/DAOs/migrations, authoritative playback history, library persistence/index
- `core.media` — MediaStore discovery, SAF metadata/tree traversal, stable identity, deep metadata extraction, URI availability
- `core.device` — device and MediaCodec capability profiling
- `playback.engine` — application playback abstraction and Media3 implementation
- `playback.session` — MediaSessionService, controller connection, queue/repeat/shuffle/speed state and system lifecycle integration
- `feature.library` — library repository/derivation, queue planning, thumbnails, file actions/relink, Compose library UI
- `feature.player` — professional player UI, player coordinator state, interaction policy, gestures, display transforms, orientation, preferences, PiP inputs and seek-preview boundary
- `ui` — application theme

The project remains one application module at this stage; package boundaries keep later module extraction possible without changing domain contracts.

# Step-3 professional player architecture

## State ownership

Step 3 deliberately separates playback state from player-interaction state.

```text
PlaybackService / MediaSession
        ↓
PlaybackConnection.state : PlaybackUiState
        ↓
      PlayerScreen
        ↑
PlayerViewModel.state : PlayerCoordinatorState
        ↑
PlayerPreferences + PlayerInteractionPolicy
```

`PlaybackUiState` reflects the real service-owned player: media ID/title, play/buffer/end state, position/duration, queue availability/index/count, speed, repeat, shuffle and mapped playback error.

`PlayerCoordinatorState` represents interaction/UI state such as:

- controls visible/locked/unlock visible
- interaction active and gesture kind
- current HUD / seek target
- brightness and volume fractions
- zoom / pan
- resize mode and custom aspect
- display rotation
- orientation mode
- fullscreen
- tutorial/accessibility state
- persisted Step-3 preferences

The ViewModel is the authoritative state machine. Composables render state and forward user intent; they do not own independent competing auto-hide timers or duplicate playback engines.

## Player presentation decomposition

Step 3 avoids one giant player composable by separating concerns across:

- `PlayerScreen.kt` — surface/host integration, pointer routing, current queue item, error/recovery and system integration callbacks
- `PlayerControls.kt` — professional overlay, top/bottom controls, seek bar, time labels, buffering indicator and HUD
- `PlayerDialogs.kt` — display, speed, playback mode, orientation, player controls, media information and gesture tutorial surfaces
- `PlayerViewModel.kt` — coordinator state transitions, auto-hide/HUD timers, resume/load logic and preference/application commands
- `PlayerInteractionModels.kt` — immutable state/enums/models
- `PlayerInteractionPolicy.kt` — deterministic pure interaction/display/PiP math
- `PlayerOrientationPolicy.kt` — orientation-mode-to-Android mapping
- `PlayerPreferences.kt` — persisted Step-3 preferences with stable names and safe defaults
- `SeekPreviewProvider.kt` — future bounded asynchronous seek-frame preview contract only

## Control visibility state machine

There is one centralized auto-hide policy. Controls initially appear and may hide only when all of the following are true:

- playback is playing
- controls are visible and not locked
- no gesture/seek interaction is in progress
- no player menu is open
- the gesture tutorial is not open
- accessibility/touch-exploration mode does not require stable controls

Meaningful player interaction cancels/resets the current hide job. Paused state keeps controls sensible. Seek-bar dragging uses local scrubbing state and does not repeatedly seek the service while the thumb moves.

## Gesture state machine and conflict resolution

The surface gesture engine classifies/owns one interaction at a time:

```text
NONE
  ├─ horizontal drag → SEEK
  ├─ left vertical drag → BRIGHTNESS
  ├─ right vertical drag → VOLUME
  ├─ two-finger scale → ZOOM
  └─ two-finger movement while transformed → PAN
```

Classification uses Android touch slop, dominant-direction locking and the actual surface width for left/right zoning. Once a drag has an owner it does not opportunistically switch between seek/brightness/volume mid-gesture.

Surface input is blocked while any of the following owns interaction:

- touchscreen lock
- player menu/dialog
- first-run/manual tutorial
- resume decision
- player preparation
- TalkBack touch exploration for conflicting custom gestures

Blocking exists at both pointer-routing and ViewModel mutation boundaries so a modal cannot accidentally change real brightness/volume behind itself.

Single tap toggles controls. Double tap uses player-relative zones rather than hardcoded pixels: left seeks backward, center toggles play/pause, right seeks forward.

## Seeking and Long safety

Playback positions and seek targets remain `Long`.

The Compose slider necessarily exposes a fractional UI value, but conversion occurs only at the UI boundary using the real `Long` duration. While dragging:

1. local UI state updates the visible target continuously;
2. the seek HUD shows direction/delta/target;
3. no uncontrolled stream of expensive service `seekTo()` calls is emitted;
4. one final clamped `Long` seek commits on release.

Horizontal swipe seek uses viewport width, media duration and persisted Low/Medium/High sensitivity. Math uses saturating/clamped operations for very long media and cannot seek below zero or beyond known duration.

`SeekPreviewProvider` intentionally remains an interface/foundation. Step 3 does not read full media, generate a frame sequence, interrupt playback or display fake frame thumbnails.

## Brightness architecture

Left-side vertical drag modifies only the current Activity window brightness. The gesture initializes from the current explicit window value when present, otherwise from system brightness normalized to a safe fraction. Values are clamped to a valid player range.

The original Activity window brightness is remembered and restored when the player leaves. Step 3 does not permanently modify global system brightness.

## Volume architecture

Right-side vertical drag reads/writes Android `AudioManager.STREAM_MUSIC`. Percentage and index are derived from the runtime `getStreamMaxVolume()` value; the implementation never assumes a universal 15-step device.

The HUD displays the actual post-write media-volume fraction.

## Zoom, pan and display transforms

Zoom is a rendering transformation only. Step 3 does not rescale source frames in software, allocate frame bitmaps, change color characteristics or re-encode media.

Manual zoom is bounded from 1× to 5×. Pan bounds are computed from viewport dimensions plus the **effective rendered X/Y scale**, which includes resize/custom-aspect/original scaling and manual zoom. Translation is clamped independently on each axis so transformed video cannot be dragged completely away.

Changing resize mode or explicit reset returns manual zoom/pan to the defined default state.

The `PlayerView` remains Media3-backed and keeps its efficient video surface. Geometric properties are applied to the video surface view: scale X/Y, translation X/Y and display-only rotation.

## Resize and aspect model

Supported modes:

- Fit
- Fill (explicit stretch)
- Crop
- Original / 100%
- forced 16:9
- forced 4:3
- forced 18:9
- forced 21:9
- validated custom width:height

Fit/Crop preserve source geometry according to their semantics. Fill and forced aspect modes are labeled as intentional display stretch rather than silently distorting the user’s media.

Custom aspect input rejects non-finite, zero, negative and extreme invalid ratios. The validated ratio itself is persisted, not merely the enum `CUSTOM`, so restart does not silently revert a user-defined ratio.

## Queue-aware current media

The player launch item is not assumed to stay current. `PlayerScreen` resolves the active `AppMedia` from `PlaybackUiState.mediaId` through the ViewModel’s queue. Consequently, after MediaSession Previous/Next the following all follow the current queue item:

- display geometry/dimensions/rotation
- player title/fallback metadata
- media information dialog
- explicit PiP ratio inputs
- automatic PiP host state
- zoom/pan bound calculations

This avoids stale metadata from the originally selected video after queue navigation.

## Orientation architecture

`OrientationMode` supports:

- Auto / Sensor
- Portrait
- Landscape
- Reverse Portrait
- Reverse Landscape
- Lock Current

`PlayerOrientationPolicy` maps those stable domain values to Android `ActivityInfo` requested-orientation constants. The Activity applies orientation changes through one host callback rather than on every arbitrary recomposition. The chosen mode is persisted by stable enum name and unknown/future values safely fall back to Auto.

When the player is disposed, orientation policy returns to Auto so the rest of the app is not left locked unexpectedly.

## Fullscreen and immersive mode

Fullscreen is player UI state plus Activity host behavior; it does not create/recreate a second playback engine.

On Android 30+, system bars use `WindowInsetsController`, hide status/navigation system bars and allow transient bars by swipe. Older supported Android versions use the existing compatible system-UI fallback. Leaving fullscreen/player restores system bars.

Player controls use safe drawing padding so critical overlay controls respect cutout/gesture insets at the software layout level. Physical OEM/cutout behavior remains Step-10 certification.

## Touchscreen lock

Lock mode is genuine interaction suppression, not an icon-only state.

When locked:

- ordinary control overlay is hidden
- seek/brightness/volume/zoom/pan/surface controls are rejected
- player menus are closed
- only the explicit unlock affordance can restore normal touchscreen controls

The lock protects the touchscreen UI. The service-owned MediaSession remains available for supported system/notification/headset/Bluetooth controls.

## Playback speed, repeat and shuffle

Playback speed is applied through the service-owned MediaController/MediaSession player. Step 3 exposes 0.25×–4.0×, common presets and 0.05× fine adjustment. Speed is per-session by default; optional remember-speed persists the selected value.

Repeat and shuffle operate on the existing service queue rather than rebuilding a separate Activity queue.

## PiP architecture

PiP reuses the existing Activity and service session; there is no second player.

`PlayerInteractionPolicy.pipRatio()`:

1. uses current media width/height when valid;
2. swaps effective dimensions for 90°/270° source rotation;
3. reduces normal ratios by GCD;
4. clamps extreme ratios to Android-supported safe bounds (2.39:1 and reciprocal);
5. falls back to 16:9 if dimensions are unavailable.

The explicit PiP button uses the current queue item. Automatic PiP is opt-in through a persisted preference. On API 31+ the Activity also enables seamless resize where supported.

## Accessibility

Important player controls expose meaningful semantics/content descriptions. TalkBack/touch-exploration state is observed live through `AccessibilityManager` listeners.

When touch exploration is enabled, conflicting custom surface gestures are suppressed and controls remain visible/clickable. Player dialogs/option rows are scrollable where needed, reducing catastrophic breakage on narrow layouts and larger font scales.

Physical TalkBack/OEM accessibility-device certification remains part of the later device matrix, but software semantics and API-35 behavior are automated-test-backed.

## Step-3 preference persistence

`PlayerPreferences` uses app-private SharedPreferences for Step-3 interaction preferences only. Persisted values include:

- double-tap seek seconds
- gesture sensitivity
- horizontal seek enabled
- brightness gesture enabled
- volume gesture enabled
- pinch zoom/pan enabled
- control auto-hide timeout
- orientation mode
- default resize mode
- custom aspect ratio
- remember playback speed
- remembered playback speed
- automatic PiP
- tutorial-seen state

Enums are stored by stable names rather than raw ordinal values. Reads clamp numeric values and unknown enum names fall back safely.

## Performance and memory behavior

Step 3 keeps player UI work bounded:

- playback position publication is approximately 500 ms while playing and 1,000 ms while not playing, not frame-by-frame
- seek scrubbing uses local UI state and one final seek
- gesture math uses small immutable values/pure calculations rather than frame bitmaps
- no full-media reads or full-video frame sequence exists in the interaction layer
- zoom/resize/rotation are display transforms rather than software re-encoding
- timers are single cancellable coroutine jobs owned by the ViewModel
- Accessibility listeners and player host state are removed/restored on disposal

## Error and lifecycle resilience

The player preserves the Step-1 mapped playback error model and exposes Retry/Back instead of raw exceptions. Buffering is surfaced independently of control visibility.

Activity recreation reconnects the Compose host to the same service-owned session. Step-3 instrumentation verifies queue identity and seek position survive recreation. Fullscreen transitions and PiP do not create a new player.

# Step-2 media-library architecture — preserved

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

## Source abstraction and media index

Step 2 recognizes indexed MediaStore video sources and user-approved SAF tree sources. Persisted source state includes stable source identity, URI, display name, source type, permission state, availability state and scan timestamps.

Room `media_index` is an index/cache, not proof that the source still exists. Refresh reconciles source rows while favourites, playlists, preferences and authoritative history stay separate so rescans do not destructively recreate user relationships.

Room index loading uses deterministic `LIMIT/OFFSET` chunks of at most 512 rows, cancellation checks and progressive snapshots. The ViewModel ultimately holds lightweight O(n) metadata for the current library; synthetic 10,000-entry tests protect deterministic derivation behavior.

## Room v2 persistence

Database version 2 retains Step-1 `media_history` and `playback_preferences` and adds favourites, playlists/items, library sources, excluded folders, media index and library preferences. `MIGRATION_1_2` is explicit and `fallbackToDestructiveMigration()` is not used.

## History, favourites, playlists and queues

Continue Watching reuses Step-1 `ResumePolicy`. Favourites persist stable media IDs. Playlists use ordered relational items. `LibraryQueuePlanner` preserves visible/folder/playlist ordering and starts at the selected item; Step 3 consumes those real queues through MediaSession Previous/Next.

## Thumbnail architecture

`ThumbnailRepository` owns bounded, off-main-thread, cancellable thumbnail work with a 16 MiB LRU memory cache and graceful placeholder path. API-26 and API-28 emulator regression jobs remain part of Step-3 CI so player work cannot silently regress the legacy library path.

## File actions and relink

MediaStore/SAF rename/delete continue to follow Android/provider policy. Unavailable local media exposes stable-ID-preserving `Locate original` relink. Step 3 does not replace these flows.

# Large-media design

Normal playback and library operations remain URI/reference based. The project does not copy media on import, load entire sources into RAM, cast file sizes/durations/positions to `Int`, or generate full-video frame sequences for player gestures.

Step-3 seek/display logic remains `Long`-safe and resolution-agnostic at the application level. Real 3 GB+, 4K/HDR and device-specific performance certification is not inferred from emulator tests.

# Later-step boundaries

Step 3 does not implement:

- Step 4 professional external subtitle/ASS-style engine and advanced sync/style management
- Step 5 equalizer, boost, audio delay, Bluetooth sync and advanced DSP
- Step 6 real software decoder/FFmpeg routing or fake decoder menu states
- Step 7/8 SMB/WebDAV/FTP/cloud/Cast work
- Step 9 full advanced settings/security system
- Step 10 physical certification

# Physical certification boundary

Real 3 GB+, 4K/HDR, SD card, USB/OTG, OEM-specific MediaStore/document-provider behavior, OEM gesture/fullscreen behavior, physical Bluetooth/headset routing, real display cutouts/foldables/external displays, high-refresh behavior, battery, thermal and broad device-matrix certification are **NOT VERIFIED — DEFERRED TO STEP 10**.

This is an explicit project boundary and does not convert those physical claims to PASS by inference.