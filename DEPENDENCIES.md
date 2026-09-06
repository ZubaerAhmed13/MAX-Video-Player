# Step 1 Dependency Register

The Step-1 baseline uses the newest stable versions that are compatible with the reproducible API-36 CI toolchain. Compose 1.12+ and several newer Compose integrations require compileSdk 37; the hosted SDK repository used by CI did not expose `platforms;android-37` during verification, so Step 1 stays on the stable API-36-compatible line instead of depending on an unavailable SDK package.

| Dependency | Version | Purpose | License family |
|---|---:|---|---|
| Android Gradle Plugin | 9.4.0 | Android build tooling | Android SDK / Apache-style tooling terms |
| Kotlin Compose plugin | 2.3.21 | Compose compiler integration | Apache 2.0 |
| KSP | 2.3.7 | Room annotation processing | Apache 2.0 |
| Compose BOM | 2026.06.00 | Stable Compose 1.11.x dependency alignment compatible with API 36 | Apache 2.0 |
| Activity Compose | 1.11.0 | Native Compose Activity integration; compiled with API 36 | Apache 2.0 |
| Lifecycle | 2.10.0 | Lifecycle-aware state/runtime on API-36-compatible line | Apache 2.0 |
| Navigation Compose | 2.9.8 | Stable API-36-compatible navigation foundation | Apache 2.0 |
| Media3 | 1.11.0 | ExoPlayer, HLS/DASH/RTSP, MediaSession, PlayerView | Apache 2.0 |
| Room | 2.8.4 | Playback history/preferences persistence | Apache 2.0 |
| Kotlin Coroutines | 1.10.2 | Structured async/IO work | Apache 2.0 |
| Material Components | 1.13.0 | Android theme interoperability | Apache 2.0 |
| JUnit / AndroidX Test / Espresso / Compose UI test | pinned in version catalog | Test infrastructure | respective open-source licenses |

No FFmpeg dependency is added in Step 1. Software decoding remains Step 6 work.
