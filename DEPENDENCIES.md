# MAX Video Player — Dependency Register through Step 9

MAX Video Player remains a clean-room native Android application. Step 9 adds **no new third-party runtime library**: private-vault cryptography, biometric wrapping, system-caption integration, secure-window policy, settings and sleep timing use platform/JCA APIs plus the already-present application stack.

| Dependency | Version | Purpose through Step 9 | License family | Step-9 security relevance |
|---|---:|---|---|---|
| Android Gradle Plugin | 9.4.0 | Android build/lint/package tooling | Android SDK / Apache-style tooling terms | Lint catches permission/component/API issues |
| Kotlin Compose plugin | 2.3.21 | Compose compiler integration | Apache 2.0 | No cryptographic role |
| KSP | 2.3.7 | Room annotation processing/schema generation | Apache 2.0 | Generates Room v8 model/schema code |
| Compose BOM | 2026.06.00 | Compose UI dependency alignment | Apache 2.0 | Accessibility/lock/settings UI only |
| AndroidX Core KTX | 1.17.0 | Android Kotlin extensions | Apache 2.0 | No key storage role |
| Activity Compose | 1.11.0 | Native Activity, lifecycle, PiP and document pickers | Apache 2.0 | Private PiP/capture lifecycle integration |
| Lifecycle | 2.10.0 | Lifecycle-aware Flow/ViewModel integration | Apache 2.0 | App/vault lock lifecycle coordination |
| Navigation Compose | 2.9.8 | Navigation foundation | Apache 2.0 | No secret storage |
| AndroidX TV Material | 1.1.0 | TV UI foundation | Apache 2.0 | Retained Step-8 D-pad/TV surface |
| Media3 | 1.11.0 | ExoPlayer, MediaSession, HLS/DASH/RTSP, Cast, source composition, audio/subtitle tracks | Apache 2.0 | Private DataSource integration, generic MediaSession metadata, descriptive-track role flags |
| OkHttp | 5.1.0 | HTTP/HTTPS/WebDAV and Media3 network transport | Apache 2.0 | Existing credential scoping/TLS retained |
| SMBJ | 0.14.0 | SMB2/3 browse/random access | Apache 2.0 | Existing Step-7 protocol security retained |
| Apache Commons Net | 3.13.0 | FTP/explicit FTPS browse/REST reads | Apache 2.0 | Existing Step-7 protocol security retained |
| Room | 2.8.4 | App relational persistence; explicit migrations through v8 | Apache 2.0 | Private index stores opaque/non-secret fields; migration is non-destructive |
| Kotlin Coroutines | 1.10.2 | Structured background I/O/state work | Apache 2.0 | Vault import/decrypt work stays out of Compose main-thread logic |
| Material Components | 1.13.0 | Android theme interoperability | Apache 2.0 | No cryptographic role |
| desugar_jdk_libs | 2.1.5 | Java API desugaring for minSdk 23 | Apache 2.0 / Android tooling | Maintains legacy API compatibility |
| MockWebServer | 5.1.0 | Test-only deterministic HTTP/HLS/DASH/WebDAV fixtures | Apache 2.0 | Retained network-security tests |
| JUnit 4 | 4.13.2 | JVM unit tests | EPL 1.0 | Crypto/settings/policy tests |
| AndroidX Test JUnit | 1.3.0 | Instrumentation test runner integration | Apache 2.0 | Migration/vault/security/Accessibility tests |
| Espresso | 3.7.0 | Android instrumentation UI support | Apache 2.0 | Test only |
| Compose UI Test | BOM-aligned | Compose semantics/large-font testing | Apache 2.0 | Locked accessibility-tree/privacy tests |

## Step-9 dependency decision

No cryptography library was added. The vault uses standard platform/JCA primitives:

- `Cipher` with `AES/GCM/NoPadding`;
- `SecretKeyFactory` with `PBKDF2WithHmacSHA256`;
- `SecureRandom`;
- `MessageDigest.isEqual` for constant-time byte-array equality;
- Android Keystore `KeyGenParameterSpec` / `KeyProperties` for optional biometric wrapping.

This keeps authenticated encryption on standard platform implementations and avoids adding an opaque or oversized crypto dependency merely for AES-GCM.

## Step-9 Android platform APIs

Step 9 intentionally uses these platform APIs rather than third-party equivalents:

| Platform API | Purpose | Security/privacy note |
|---|---|---|
| Android Keystore | Optional biometric-bound AES key | PIN/passphrase envelope remains recovery path |
| `android.hardware.biometrics.BiometricPrompt` | Optional biometric authentication | API-gated; requires `USE_BIOMETRIC`; physical OEM behavior deferred |
| `WindowManager.LayoutParams.FLAG_SECURE` | Private-screen capture policy | Software flag state tested; OEM capture behavior deferred |
| `SystemClock.elapsedRealtime()` | App Lock and sleep timer deadlines | Monotonic; independent of wall-clock changes |
| `CaptioningManager` | Read user system-caption preferences | Maps only supported style fields; MAX custom style is retained/restored |
| `ContentResolver` / SAF | Source reads and user-approved source deletion/restore | No broad all-files permission added |
| `noBackupFilesDir` | Encrypted private-container location | Private encrypted media excluded from normal backup path |

## No new Step-9 runtime dependency

The Step-9 PR does not add a new entry to `gradle/libs.versions.toml` or a new `implementation(...)` dependency in `app/build.gradle.kts`. It reuses the existing Media3, Room, Compose, AndroidX and coroutine stack. In particular it adds no:

- proprietary/private-vault SDK;
- bundled native crypto library;
- SQLCipher dependency;
- second playback engine/player;
- FFmpeg/native decoder payload;
- biometric wrapper library;
- background scheduler/exact-alarm library;
- analytics/cloud service for private media.

## Existing Step-8 additions retained

Media3 Cast (`androidx.media3:media3-cast`) and AndroidX TV Material are present in the current dependency catalog for the already-implemented Step-8 Cast/TV architecture. Step 9 does not replace these. Private media is blocked at the source/output policy boundary while public Cast/TV behavior remains intact.

## Step-7 network dependencies retained

### OkHttp 5.1.0

One HTTP-family client supplies progressive HTTP/HTTPS, WebDAV, HLS/DASH transport and sidecar traffic. Existing origin/directory credential scoping, TLS hostname validation and redirect policy remain unchanged.

### SMBJ 0.14.0

Pure-Java SMB2/3 support provides authenticated listing and bounded random reads. SMB1 remains excluded. Existing signing/encryption behavior remains governed by the Step-7 security policy.

### Apache Commons Net 3.13.0

FTP and explicit FTPS use binary transfers and REST offsets; FTPS enables protected TLS control/data behavior according to the existing Step-7 policy.

No SFTP library is present; SFTP is not mislabeled as FTP/FTPS.

## CI-only protocol tools

These are not APK/runtime dependencies:

| Tool | Tested source/version | Purpose | License family |
|---|---|---|---|
| Samba | Ubuntu 24.04 package / Step-7 pinned environment | Isolated authenticated SMB2/3 browse/random read/playback | GPLv3, CI only |
| pyftpdlib + PyOpenSSL | Step-7 pinned CI environment | Isolated FTP and TLS-required explicit-FTPS servers | MIT / Apache 2.0, CI only |
| MediaMTX | Step-7 pinned container digest | Isolated authenticated RTSP server | MIT, CI only |
| FFmpeg | Ubuntu CI package | Publishes repository-owned synthetic H.264/AAC fixture to test RTSP | Distribution build license terms; CI only |
| Android Emulator / SDK 35 | GitHub Actions CI | Step-9 full connected and named-class certification | Android SDK terms |

None of these CI tools is packaged into the application by Step 9.

## Earlier dependency architecture preserved

### Step 6 decoder

No bundled FFmpeg/native/OEM decoder was added. Decoder modes continue to use Android/Media3 codec discovery/classification and the existing ExoPlayer.

### Step 5 audio

No proprietary DSP library is required. The 10-band EQ, preamp/boost/limiter, channel mapping, balance and delay path remains the project-owned Media3 audio-processor implementation.

### Step 4 subtitles

SRT/WebVTT/SSA/ASS/TTML parsing remains on Media3 text support; no external subtitle parser/service was introduced.

### Storage/persistence

Media/subtitle/audio/network/private media remains URI/reference or bounded-stream based. Room stores relational state; lightweight global settings use SharedPreferences. Step 9 does not introduce DataStore solely to duplicate this architecture.

## Security and license policy

Any future dependency must record:

- exact artifact/library name and version;
- source/purpose;
- license;
- security relevance;
- native/ABI implications;
- APK-size/runtime-service implications where material.

An opaque binary decoder, DSP, crypto or vault library must not be introduced simply to avoid implementing and validating the required behavior.

## Step-10 boundary

Physical/OEM biometric behavior, screenshot/recents behavior, real 3 GB+ private imports, real 4K/HDR/high-bitrate private playback, thermal/battery endurance, SD/USB, Chromecast/TV hardware, HDMI/Miracast/desktop output and vendor background-killing behavior remain **NOT VERIFIED — DEFERRED TO STEP 10**. No new runtime dependency is added in Step 9 to pretend those hardware cases are solved.
