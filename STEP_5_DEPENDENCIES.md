# Step 5 — Dependency and Licensing Note

## Runtime dependency decision
Step 5 adds **no new third-party runtime dependency**.

The professional audio engine is built on dependencies already present in the Android project:
- AndroidX Media3 ExoPlayer / Session / UI;
- AndroidX Lifecycle / Activity / Compose;
- AndroidX Room;
- Kotlin coroutines;
- Android platform audio/output APIs.

The custom DSP (`MaxAudioProcessor`) is project-owned Kotlin code and is connected to Media3 through the existing audio-sink extension points.

## Why no external DSP SDK was added
The Step-5 requirements can be met with an app-owned PCM processor without introducing a proprietary equalizer, FFmpeg runtime, native audio SDK, or additional binary distribution surface. Avoiding an unnecessary new dependency reduces:
- ABI/native-library risk;
- licensing uncertainty;
- APK size growth;
- device-specific native crashes;
- hidden network/runtime requirements.

## Media fixtures
Step-5 automated fixtures are synthetic test assets created for certification. They are used only to verify playback behavior such as multi-audio discovery, external audio and subtitle coexistence. They are not copied from MX Player or another proprietary application.

## Clean-room boundary
No MX Player code, assets, branding, private APIs or proprietary binaries are used. The target remains behavior/class parity through an original implementation using public Android/Media3 APIs.

## Existing project dependency declaration
The authoritative declarations remain in `app/build.gradle.kts` and the Gradle version catalog. Step 5 does not require an additional Maven repository or runtime package.
