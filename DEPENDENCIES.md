# Dependency Register — through Step 6

MAX Video Player remains a clean-room native Android application. **Step 6 adds no new third-party runtime or native decoder dependency.** The professional decoder engine is implemented with the Android platform `MediaCodec`/`MediaCodecList` capability surface and the Media3 / ExoPlayer stack already present through Step 5.

| Dependency | Version | Purpose through Step 6 | License family |
|---|---:|---|---|
| Android Gradle Plugin | 9.4.0 | Android build tooling | Android SDK / Apache-style tooling terms |
| Kotlin Compose plugin | 2.3.21 | Compose compiler integration | Apache 2.0 |
| KSP | 2.3.7 | Room annotation processing | Apache 2.0 |
| Compose BOM | 2026.06.00 | Compose UI dependency alignment | Apache 2.0 |
| Activity Compose | 1.11.0 | Native Activity, PiP/lifecycle and activity-result/OpenDocument integration | Apache 2.0 |
| Lifecycle | 2.10.0 | Lifecycle-aware StateFlow collection and ViewModels | Apache 2.0 |
| Navigation Compose | 2.9.8 | Navigation foundation | Apache 2.0 |
| Media3 | 1.11.0 | ExoPlayer, MediaSession, MediaCodec renderer/selector extension points, decoder analytics, tracks, playback parameters, source composition, subtitles and custom PCM AudioProcessor/AudioSink integration | Apache 2.0 |
| Room | 2.8.4 | History/library/subtitle/audio state plus Step-6 per-media decoder override; explicit migrations through v5 | Apache 2.0 |
| Kotlin Coroutines | 1.10.2 | Structured I/O, persistence, codec-inventory scans, URI probing and service-safe asynchronous operations | Apache 2.0 |
| Material Components | 1.13.0 | Android theme interoperability | Apache 2.0 |
| JUnit / AndroidX Test / Compose UI test | pinned in version catalog | JVM policy tests, migration tests, production playback integration and API-35 certification | respective open-source licenses |

## Step-6 dependency decision

- **No FFmpeg binary or bundled native video decoder was added.** Software mode uses only genuine software-only decoders that the device/platform exposes to Media3. If none exists for the requested format, the app reports that truthfully rather than silently using hardware.
- **No proprietary OEM decoder library was added.** Hardware and Enhanced Hardware route only through Android/Media3 codec discovery and classification.
- **No second playback engine or second ExoPlayer was added.** Decoder policy is injected into the existing service-owned Media3 renderer path.
- **No codec-name hardcoding is used as the primary API-29+ classifier.** Android/Media3 hardware/software flags are authoritative; conservative name heuristics are only a legacy fallback where platform classification is unavailable.
- **No networking/upload dependency was introduced.** Decoder selection and capability diagnostics are local.
- **No new NDK ABI payload was introduced.** There are therefore no new decoder `.so` artifacts, ABI packaging rules or native licenses to audit in Step 6.
- **No licensed Dolby/DTS/Atmos video/audio decoder binary was added.** The app only reports what the current device exposes and does not claim universal licensed-codec support.

## Platform / Media3 APIs intentionally used in Step 6

- Media3 `MediaCodecSelector` and `DefaultRenderersFactory`
- Media3 `MediaCodecVideoRenderer` format-support ordering and decoder fallback behavior through existing ExoPlayer internals
- Media3 decoder analytics callbacks for actual initialized/released decoder identity, input format, initialization duration and dropped frames
- Android `MediaCodecList` / `MediaCodecInfo` / `CodecCapabilities` / `VideoCapabilities`
- API-29+ hardware-accelerated, software-only and vendor classification flags
- adaptive, secure, tunneled and available low-latency capability flags
- profile/level, color-format and size/rate capability reporting
- Room v5 for stable per-media decoder override state
- SharedPreferences for global decoder default, remember-per-video and diagnostics preferences

## Step-5 dependency decisions retained

- **No proprietary audio/DSP library was added.** The 10-band EQ, channel mapping, balance, preamp, boost, limiter and delay path are project-owned clean-room PCM processing implemented through Media3 extension points.
- **No `android.media.audiofx.Equalizer` dependency is used as the required core DSP.** OEM/platform effect variability would make behavior non-deterministic.
- **No second playback library/player was added for external audio.** Selected external audio is merged into the existing Media3 timeline and controlled by the service-owned player.
- **No new storage framework was added.** External audio uses Android `OpenDocument`, `ContentResolver`, URI references and persistable read permission where available.

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

## Later-step boundary

Step 6 does not bundle a universal software codec backend. Cross-OEM decoder certification, physical 4K/HDR/high-bitrate/thermal/battery behavior and any future decision to bundle a native software video decoder remain separate work requiring an explicit dependency/license review and physical certification in Step 10 where specified.
