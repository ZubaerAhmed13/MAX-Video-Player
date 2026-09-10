# MAX Video Player — Dependency Register through Step 10

Step 10 adds certification scripts/workflows/documentation only and introduces **no new production runtime dependency**. Versions below are taken from the current `gradle/libs.versions.toml` and build configuration, not inferred from older reports.

| Dependency | Exact version | Purpose | License family / packaging note |
| --- | ---: | --- | --- |
| Android Gradle Plugin | 9.4.0 | Android build/lint/package tooling | Android/Apache-style tooling; build-time |
| Kotlin Compose plugin | 2.3.21 | Kotlin/Compose compiler integration | Apache 2.0; build-time |
| KSP | 2.3.7 | Room annotation processing/schema generation | Apache 2.0; build-time |
| Compose BOM | 2026.06.00 | Compose UI alignment | Apache 2.0 |
| AndroidX Core KTX | 1.17.0 | Android Kotlin extensions | Apache 2.0 |
| Activity Compose | 1.11.0 | Activity/lifecycle/PiP/document integration | Apache 2.0 |
| Lifecycle | 2.10.0 | lifecycle-aware state/ViewModel | Apache 2.0 |
| Navigation Compose | 2.9.8 | app navigation | Apache 2.0 |
| AndroidX TV Material | 1.1.0 | TV UI foundation | Apache 2.0 |
| Media3 | 1.11.0 | ExoPlayer, MediaSession, HLS/DASH/RTSP, Cast, UI and OkHttp datasource | Apache 2.0 |
| Room | 2.8.4 | relational persistence and migrations through schema v8 | Apache 2.0 |
| Kotlin Coroutines | 1.10.2 | structured asynchronous I/O/state | Apache 2.0 |
| Material Components | 1.13.0 | Android theme interoperability | Apache 2.0 |
| OkHttp | 5.1.0 | HTTP/HTTPS/WebDAV and Media3 network transport | Apache 2.0 |
| SMBJ | 0.14.0 | SMB2/SMB3 browse/random access | Apache 2.0; pure Java |
| Apache Commons Net | 3.13.0 | FTP and explicit FTPS | Apache 2.0 |
| desugar_jdk_libs | 2.1.5 | Java API desugaring for minSdk 23 | Android/Apache-style tooling/runtime support |
| JUnit 4 | 4.13.2 | JVM tests | EPL 1.0; test only |
| AndroidX Test JUnit | 1.3.0 | instrumentation | Apache 2.0; test only |
| Espresso | 3.7.0 | instrumentation/UI test support | Apache 2.0; test only |
| MockWebServer | 5.1.0 | deterministic network fixtures | Apache 2.0; test only |
| Compose UI Test | BOM-aligned | Compose semantics/accessibility tests | Apache 2.0; test only |

## Build/runtime baseline

- Java 17
- Gradle 9.6.0 in GitHub Actions
- compileSdk 36
- targetSdk 36
- minSdk 23
- applicationId `com.zubaer.maxvideoplayer`
- versionName `0.1.0-step1`
- versionCode `1`
- release minification currently disabled; release build still references the standard optimized ProGuard file plus project rules for future use

The repository currently uses CI-provisioned Gradle rather than a checked-in Gradle wrapper. Step 10 records this fact; it does not add or invent a wrapper during final certification without a concrete need.

## Media3 surface

All Media3 modules use the same `1.11.0` catalog version: ExoPlayer core, HLS, DASH, RTSP, Session, UI, Cast and OkHttp DataSource. Step 10 must not introduce duplicate/conflicting Media3 versions or a second playback engine.

## Network dependencies

OkHttp handles HTTP-family transport; SMBJ handles SMB2/3; Commons Net handles FTP/explicit FTPS. No SFTP library is present, so SFTP must not be claimed as implemented. CI-only Samba, pyftpdlib/PyOpenSSL, MediaMTX, FFmpeg and Android Emulator tooling are certification fixtures and are not APK runtime dependencies.

## Cryptography/private media

Step-9/10 private-vault architecture uses Android/JCA primitives (AES-GCM, PBKDF2-HMAC-SHA256, SecureRandom, Android Keystore) instead of bundling an opaque native crypto/codec payload. No production signing material belongs in the dependency tree or repository.

## Step-10 dependency/license audit policy

For every future production dependency record exact version, upstream, license, purpose, minSdk effect, native/ABI implication and material transitive risk. Do not upgrade a major dependency during final certification without a concrete defect/security reason; any upgrade creates a new regression obligation.

Step 10 package/static checks supplement this register but do not justify a fabricated “zero vulnerabilities” claim. Known-vulnerability disposition requires an actual dependency/security source and an assessment of whether the packaged version/code path is affected.
