# Dependency Register — through Step 4

Steps 3 and 4 deliberately reuse the existing AndroidX/Media3/Room/Coroutines stack. **No new third-party runtime library is required for the professional subtitle engine.**

| Dependency | Version | Purpose through Step 4 | License family |
|---|---:|---|---|
| Android Gradle Plugin | 9.4.0 | Android build tooling | Android SDK / Apache-style tooling terms |
| Kotlin Compose plugin | 2.3.21 | Compose compiler integration | Apache 2.0 |
| KSP | 2.3.7 | Room annotation processing | Apache 2.0 |
| Compose BOM | 2026.06.00 | Compose UI dependency alignment | Apache 2.0 |
| Activity Compose | 1.11.0 | Native Compose Activity, back handling, activity-result/OpenDocument integration | Apache 2.0 |
| Lifecycle | 2.10.0 | Lifecycle-aware StateFlow collection and ViewModels | Apache 2.0 |
| Navigation Compose | 2.9.8 | Navigation foundation | Apache 2.0 |
| Media3 | 1.11.0 | ExoPlayer, MediaSession, HLS/DASH/RTSP, text-track discovery/selection, SubtitleView and subtitle parsers | Apache 2.0 |
| Room | 2.8.4 | History, library state and Step-4 durable subtitle associations/media state with explicit migrations | Apache 2.0 |
| Kotlin Coroutines | 1.10.2 | Structured I/O, sidecar discovery, provider probing, persistence and cancellation | Apache 2.0 |
| Material Components | 1.13.0 | Android theme interoperability | Apache 2.0 |
| JUnit / AndroidX Test / Compose UI test | pinned in version catalog | JVM, migration/database, Compose, Media3 parser and API-35 integration testing | respective open-source licenses |

## Step-4 dependency decisions

- **No external subtitle parser library was added.** SRT, WebVTT, SSA/ASS and TTML parsing uses Media3's own text parser stack. Direct Android instrumentation verifies that those real parsers emit expected cues.
- **No FFmpeg binary was added for subtitles.** Step 4 does not burn subtitles into video or transcode media. Software-decoder/FFmpeg routing remains Step 6.
- **No ICU/third-party charset library was added.** The encoding layer uses Java/Android charset support for UTF-8, UTF-16 LE/BE and Windows-1252, with bounded normalization of external subtitle bytes before Media3 parsing.
- **No new storage abstraction library was added.** Manual subtitle loading uses Android `OpenDocument`, `ContentResolver`, `DocumentsContract` and persisted SAF permissions.
- **No new database system was added.** Subtitle relationships extend the existing Room database to version 3 with explicit `MIGRATION_2_3`.
- **No image or video processing library was added.** Subtitle appearance is rendered by Media3 `SubtitleView`; source media is not re-encoded.
- **No network subtitle provider dependency was added.** `NETWORK_URL`/future provider model values are architectural extension points, not claimed Step-4 provider implementations.

## Platform APIs intentionally used in Step 4

- Android Storage Access Framework `OpenDocument`
- `ContentResolver` metadata/open/probe operations
- `DocumentsContract` sibling traversal for sidecar discovery inside user-approved SAF trees
- Java charset decoders for UTF-8/UTF-16/Windows-1252 handling
- AndroidX Media3 text-track APIs, `SubtitleView`, `SubtitleConfiguration`, `DefaultSubtitleParserFactory` and media-source factories
- Room entities/DAOs/migrations for durable subtitle state
- Coroutines for off-main-thread probing/discovery/persistence

## Preserved prior dependency decisions

- Paging 3 remains unnecessary for the current explicit 512-row Room index paging strategy.
- Coil remains unnecessary for the bounded platform-thumbnail repository.
- DataStore remains unnecessary because current persistent library/subtitle relational state uses Room and lightweight interaction/style preferences use the existing preference store.
- No proprietary decoder binaries are present.

Any future dependency addition must record exact version, purpose and license here before the corresponding capability is considered release-ready.