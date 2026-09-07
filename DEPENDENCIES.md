# Dependency Register — through Step 5

MAX Video Player remains a clean-room native Android application. Step 5 deliberately extends the existing AndroidX / Media3 / Room / Coroutines stack and adds **no new third-party runtime dependency** for the professional audio engine.

| Dependency | Version | Purpose through Step 5 | License family |
|---|---:|---|---|
| Android Gradle Plugin | 9.4.0 | Android build tooling | Android SDK / Apache-style tooling terms |
| Kotlin Compose plugin | 2.3.21 | Compose compiler integration | Apache 2.0 |
| KSP | 2.3.7 | Room annotation processing | Apache 2.0 |
| Compose BOM | 2026.06.00 | Compose UI dependency alignment | Apache 2.0 |
| Activity Compose | 1.11.0 | Native Activity, PiP/lifecycle and activity-result/OpenDocument integration | Apache 2.0 |
| Lifecycle | 2.10.0 | Lifecycle-aware StateFlow collection and ViewModels | Apache 2.0 |
| Navigation Compose | 2.9.8 | Navigation foundation | Apache 2.0 |
| Media3 | 1.11.0 | ExoPlayer, MediaSession, tracks, playback parameters, MergingMediaSource, subtitle parsing and custom PCM AudioProcessor/AudioSink integration | Apache 2.0 |
| Room | 2.8.4 | History/library/subtitle state plus Step-5 external-audio and per-media audio state; explicit migrations through v4 | Apache 2.0 |
| Kotlin Coroutines | 1.10.2 | Structured I/O, persistence, URI probing, library/subtitle work and service-safe asynchronous operations | Apache 2.0 |
| Material Components | 1.13.0 | Android theme interoperability | Apache 2.0 |
| JUnit / AndroidX Test / Compose UI test | pinned in version catalog | JVM DSP tests, migration tests, UI/integration tests and API-35 certification | respective open-source licenses |

## Step-5 dependency decisions

- **No proprietary audio/DSP library was added.** The 10-band EQ, channel mapping, balance, preamp, boost, limiter and delay path are project-owned clean-room PCM processing implemented through Media3 extension points.
- **No `android.media.audiofx.Equalizer` dependency is used as the required core DSP.** OEM/platform effect variability would make behavior non-deterministic. Platform effects may be future optional enhancements only.
- **No FFmpeg decoder binary was added.** Step 5 keeps Media3's existing decoder path. Software/enhanced decoder routing remains Step 6.
- **No proprietary Dolby/DTS/Atmos binary or license-dependent codec implementation was added.** Step 5 reports only what Media3/device support exposes; it does not claim licensed codec certification.
- **No second playback library/player was added for external audio.** Selected external audio is merged into the existing Media3 timeline and controlled by the service-owned player.
- **No new storage framework was added.** External audio uses Android `OpenDocument`, `ContentResolver`, URI references and persistable read permission where available.
- **No new database system was added.** Room advances from v3 to v4 with explicit `MIGRATION_3_4`.
- **No networking/upload dependency was introduced.** Audio processing is local.
- **No NDK/C++ DSP dependency was introduced.** The current deterministic DSP is Kotlin/Java-side Media3 processing.

## Platform / Media3 APIs intentionally used in Step 5

- Media3 `Player.currentTracks`, `TrackSelectionOverride` and track-selection parameters
- Media3 `PlaybackParameters` for independent pitch while preserving the existing speed path
- Media3 `DefaultRenderersFactory`, `DefaultAudioSink` and custom `AudioProcessor`
- Media3 `MergingMediaSource` for one service-owned video/audio/subtitle timeline with selected external audio
- Media3 `AudioAttributes` with audio-focus handling and `setHandleAudioBecomingNoisy(true)`
- Android `AudioManager`, `AudioDeviceCallback` and `AudioDeviceInfo` for output-route awareness
- Android Activity lifecycle and PiP APIs for Pause / Continue audio / PiP background policy
- Android Storage Access Framework `OpenDocument` and `ContentResolver` for external audio/subtitles
- Room v4 audio entities/DAOs/migrations for relational per-media audio state
- SharedPreferences for lightweight global EQ/background/route-profile settings

## Step-4 dependency decisions retained

- SRT, WebVTT, SSA/ASS and TTML parsing continues to use Media3's text parser stack.
- No external subtitle parser or network subtitle provider is introduced.
- External subtitle encoding normalization continues to use Java/Android charset support.
- Subtitle appearance continues through Media3 `SubtitleView`; video is not burned/transcoded.

## Preserved earlier decisions

- Media/subtitle/audio sources remain URI/reference based; no whole-video copy is introduced.
- Paging 3 remains unnecessary for the current bounded Room index paging strategy.
- Coil remains unnecessary for the bounded platform-thumbnail repository.
- No proprietary decoder binaries are present.
- DataStore remains unnecessary for current persistence architecture: relational state uses Room and lightweight global settings use the existing preference layer.

## License and clean-room policy

Any future dependency must record:

- exact artifact/library name
- exact version
- source
- purpose
- license
- native/ABI implications where relevant
- APK-size impact where relevant

An opaque binary DSP/decoder library must not be introduced simply to avoid implementing or validating required behavior.

## Step-6 boundary

Step 5 does not add custom decoder libraries. Hardware/enhanced-hardware/software decoder control, software codec fallback and any FFmpeg/custom decoder decision belong to Step 6 and require a separate dependency/license review.

Any future dependency addition must be documented here before the corresponding capability is considered complete.