# Step 6 Dependency Report — Professional Decoder Engine

## Result

**No new decoder dependency was required for Step 6.**

The implementation uses the already-declared Media3 1.11.0 stack plus Android platform codec APIs. No FFmpeg/native video decoder, proprietary codec pack, OEM binary, second playback library or new `.so` payload is included.

## Decoder backends used

- **Hardware / Enhanced Hardware:** Android/Media3 `MediaCodec` decoders classified as hardware accelerated.
- **Software:** Android/Media3 decoders classified as software-only. This is device-dependent; when none is exposed for a requested format, the product reports Software unavailable instead of silently using hardware.
- **Auto:** the same discovered platform candidate pool, ordered by the Step-6 policy with deliberate cross-backend fallback permitted.

## Existing dependencies exercised by Step 6

| Component | Version | Step-6 use |
|---|---:|---|
| AndroidX Media3 / ExoPlayer | 1.11.0 | codec selector, renderer factory, format-support ordering, decoder lifecycle analytics, service-owned playback |
| Android platform APIs | device API | `MediaCodecList`, `MediaCodecInfo`, codec capabilities and video size/rate queries |
| Room | 2.8.4 | v5 per-media decoder override state |
| Kotlin Coroutines | 1.10.2 | off-main-thread device capability inventory refresh |
| Compose / Lifecycle | existing pinned versions | decoder controls, diagnostics and lifecycle-safe state |

## Native / ABI impact

None. Step 6 adds no native decoder artifact and therefore no new ABI packaging matrix, NDK build, native license or native symbol surface.

## Licensing

No new third-party license is introduced by Step 6. Existing Media3/AndroidX licensing remains recorded in `DEPENDENCIES.md`.

## Deferred boundary

A future bundled native software-video backend would require a separate explicit dependency/license/ABI review and its own native initialization/decode/resource tests. Step 6 does not claim such a backend exists.
