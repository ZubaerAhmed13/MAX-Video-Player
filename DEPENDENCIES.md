# MAX Video Player — Dependency Register through Step 10

Step 10 adds one deliberate production dependency override for security: `org.bouncycastle:bcprov-jdk18on` is pinned to **1.84** because SMBJ 0.14.0 requests 1.79 and the 1.79 line is affected by disclosed 2026 Bouncy Castle vulnerabilities. MAX source does not directly call the affected GOST CTR or LDAP helper APIs, but the vulnerable provider classes would otherwise be packaged transitively, so the fixed version is selected and must pass full SMB/network regression.

| Dependency | Exact version | Purpose | License family / packaging note |
| --- | ---: | --- | --- |
| Android Gradle Plugin | 9.4.0 | Android build/lint/package tooling | Android/Apache-style tooling; build-time |
| Kotlin Compose plugin | 2.3.21 | Kotlin/Compose compiler integration | Apache 2.0; build-time |
| KSP | 2.3.7 | Room processing/schema generation | Apache 2.0; build-time |
| Compose BOM | 2026.06.00 | Compose UI alignment | Apache 2.0 |
| AndroidX Core KTX | 1.17.0 | Android Kotlin extensions | Apache 2.0 |
| Activity Compose | 1.11.0 | Activity/lifecycle/PiP/document integration | Apache 2.0 |
| Lifecycle | 2.10.0 | lifecycle-aware state/ViewModel | Apache 2.0 |
| Navigation Compose | 2.9.8 | app navigation | Apache 2.0 |
| AndroidX TV Material | 1.1.0 | TV UI foundation | Apache 2.0 |
| Media3 | 1.11.0 | ExoPlayer, MediaSession, HLS/DASH/RTSP, Cast, UI and OkHttp datasource | Apache 2.0 |
| Room | 2.8.4 | relational persistence/migrations through schema v8 | Apache 2.0 |
| Kotlin Coroutines | 1.10.2 | asynchronous I/O/state | Apache 2.0 |
| Material Components | 1.13.0 | Android theme interoperability | Apache 2.0 |
| OkHttp | 5.1.0 | HTTP/HTTPS/WebDAV and Media3 transport | Apache 2.0 |
| SMBJ | 0.14.0 | SMB2/SMB3 browse/random access | Apache 2.0; pure Java |
| Bouncy Castle bcprov-jdk18on | 1.84 | explicit security override of SMBJ 0.14.0 transitive 1.79 | Bouncy Castle license; pure Java runtime |
| Apache Commons Net | 3.13.0 | FTP and explicit FTPS | Apache 2.0 |
| desugar_jdk_libs | 2.1.5 | Java API desugaring for minSdk 23 | Android/Apache-style tooling/runtime support |
| JUnit 4 | 4.13.2 | JVM tests | EPL 1.0; test only |
| AndroidX Test JUnit | 1.3.0 | instrumentation | Apache 2.0; test only |
| Espresso | 3.7.0 | UI test support | Apache 2.0; test only |
| MockWebServer | 5.1.0 | deterministic network fixtures | Apache 2.0; test only |

## Build/runtime baseline

Java 17; CI Gradle 9.6.0; compileSdk/targetSdk 36; minSdk 23; applicationId `com.zubaer.maxvideoplayer`; versionName `0.1.0-step1`; versionCode `1`. Release minification is currently disabled.

## Bouncy Castle security disposition

- SMBJ 0.14.0 depends at runtime on `bcprov-jdk18on 1.79`.
- CVE-2025-14813 affects the GOST 28147 CTR implementation in affected BC versions and is fixed in 1.84 (with some backports on selected earlier lines).
- CVE-2026-0636 affects the BC LDAP certificate-store helper and is fixed in 1.84.
- MAX does not directly import/invoke Bouncy Castle APIs; the provider enters via SMBJ. The specific disclosed GOST/LDAP paths are therefore not known MAX code paths, but final release hardening removes the affected transitive package version anyway.
- `bcprov-jdk18on 1.84` is declared directly so Gradle's version conflict resolution selects the fixed provider while retaining SMBJ 0.14.0 API behavior. Full Step-7 SMB/FTPS/network regression is required before accepting the change.

This is not a blanket “0 vulnerabilities” claim. Future advisories still require package/version/code-path review.

## Other dependency boundaries

All Media3 modules stay aligned at 1.11.0 and use the single service-owned player. OkHttp supplies HTTP-family transport; SMBJ supplies SMB2/3; Commons Net supplies FTP/explicit FTPS. No SFTP library is present, so SFTP is not claimed. Private-vault crypto continues to use Android/JCA/Keystore primitives; the Bouncy Castle pin is for the SMBJ runtime graph and does not replace the vault cryptography design.

Any further dependency upgrade during Step 10 requires a concrete compatibility/security reason plus regression testing.
