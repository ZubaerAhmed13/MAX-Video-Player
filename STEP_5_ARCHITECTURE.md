# Step 5 — Professional Audio Engine Architecture

## Scope
Step 5 extends the existing single service-owned Media3 playback session. It does not create a second player, duplicate the timeline, copy large source media, or replace Step 4 subtitle behavior.

## Authoritative playback path
`MainActivity / Compose UI -> AudioPlaybackController -> PlaybackConnection -> MediaController -> PlaybackService -> ExoPlayer -> DefaultAudioSink -> MaxAudioProcessor -> Android audio output`

The player remains owned by `PlaybackService`. UI controls are commands/state views over that one session.

## Track selection
- Embedded audio tracks are discovered from Media3 `Tracks`.
- Human-readable labels include language/label plus codec, channel count, sample rate and bitrate when available.
- Auto mode clears manual audio overrides and applies persisted preferred audio language.
- Manual embedded selection uses `TrackSelectionOverride`.
- Preferred language is persisted globally and reapplied to Media3.

## External audio
External audio is stored as a durable association to the stable media ID. The original media URI remains the source of truth.

The selected external source is merged with the primary source by `ProfessionalMediaSourceFactory` using `MergingMediaSource`. Step 4 external subtitle configuration remains on the primary `MediaItem`. Selection is then made through the same Media3 audio-track selection path.

Failure behavior is recoverable: unavailable/missing external audio is surfaced as state and playback can fall back to embedded/Auto audio rather than destroying the session.

## DSP
`MaxAudioProcessor` is an app-owned PCM processor installed in the production `DefaultAudioSink`.

Supported processing path:
- PCM 16-bit
- PCM float
- 10-band peaking EQ
- presets and custom bands
- preamp
- digital boost
- soft limiting
- stereo mono/left/right mapping
- stereo left/right balance
- positive and negative audio delay

Realtime parameters are published through an `AtomicReference<AudioDspParameters>` so the audio callback path does not perform Room I/O, coroutine work, UI work or blocking persistence.

### Multichannel truthfulness
Channel mode (`Stereo`, `Mono`, `Left`, `Right`) and left/right balance are **stereo-only controls**.

For selected tracks whose reported channel count is not 2 (for example 5.1 or 7.1), the UI disables these controls and explicitly states that the multichannel layout is preserved. The DSP does not reinterpret a six- or eight-channel frame as stereo. EQ/gain processing may still operate per channel where the PCM format is supported.

This is deliberate: the product does not claim fake 5.1/7.1 remapping semantics for a two-channel control.

## Audio synchronization
Two offsets are kept separate:
1. per-media audio delay;
2. per-output-route compensation.

The effective delay is clamped to the supported range. Positive delay means audio is emitted later; negative delay skips the corresponding leading audio frames. Changing delay flushes at the current playback position rather than rebuilding a second timeline.

## Pitch
Pitch is applied using Media3 `PlaybackParameters` while retaining the current playback speed. Pitch state is persisted globally.

## Audio-only mode
Audio-only mode disables the video track through Media3 track-selection parameters. It keeps the same media item, position, MediaSession and queue. Re-enabling restores the video track.

## Background policy
`MainActivity` applies one of three explicit policies when the player leaves foreground:
- `PAUSE`: pause the service-owned player.
- `CONTINUE_AUDIO`: preserve the MediaSession; optionally suppress video while backgrounded.
- `PIP_WHEN_POSSIBLE`: request PiP through normal Android behavior; if PiP is not active when stopped, preserve the audio session and optionally suppress video.

If video was suppressed only because of background policy, `onStart()` restores video. User-selected audio-only remains authoritative and separate.

## Output routes
`AudioRouteMonitor` classifies speaker, wired headset/headphones, Bluetooth A2DP/LE, USB, HDMI and unknown routes. Route compensation is stored separately per route family.

Automated tests certify route classification. Physical acoustic/latency quality for specific Bluetooth, USB or HDMI hardware remains a physical-device certification item, not something inferred from an emulator.

## Persistence
- Global audio preferences: SharedPreferences.
- Per-media audio selection, external association and media delay: Room.
- Source media: referenced by URI; no full-file import/copy is introduced.

## Step 4 coexistence
External audio merging preserves the Step 4 subtitle association and subtitle parser path. Step 5 instrumentation explicitly attaches an external subtitle, then external audio, and verifies the subtitle association remains present.

## Large-media invariants
Step 5 does not introduce file-size-dependent buffering or whole-file reads. Existing URI-based, long-safe playback architecture remains unchanged. Audio processing operates on bounded PCM buffers delivered by Media3.
