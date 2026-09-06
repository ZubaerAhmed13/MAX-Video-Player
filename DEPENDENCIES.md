# Dependency Register — through Step 2

Step 2 deliberately reuses the Step-1 AndroidX/Media3/Room/Coroutines stack. **No new third-party runtime library was required** for the professional media-library implementation.

| Dependency | Version | Purpose through Step 2 | License family |
|---|---:|---|---|
| Android Gradle Plugin | 9.4.0 | Android build tooling | Android SDK / Apache-style tooling terms |
| Kotlin Compose plugin | 2.3.21 | Compose compiler integration | Apache 2.0 |
| KSP | 2.3.7 | Room annotation processing | Apache 2.0 |
| Compose BOM | 2026.06.00 | Compose UI dependency alignment | Apache 2.0 |
| Activity Compose | 1.11.0 | Native Compose Activity, back handling, activity-result integration | Apache 2.0 |
| Lifecycle | 2.10.0 | Lifecycle-aware StateFlow collection and ViewModels | Apache 2.0 |
| Navigation Compose | 2.9.8 | Navigation foundation | Apache 2.0 |
| Media3 | 1.11.0 | ExoPlayer, HLS/DASH/RTSP, MediaSession, player/session integration | Apache 2.0 |
| Room | 2.8.4 | History, favourites, playlists, media index, sources/exclusions/preferences, explicit migrations, bounded chunk queries | Apache 2.0 |
| Kotlin Coroutines | 1.10.2 | Structured I/O, scanning, debouncing, derived-list work, cancellation | Apache 2.0 |
| Material Components | 1.13.0 | Android theme interoperability | Apache 2.0 |
| JUnit / AndroidX Test / Compose UI test | pinned in version catalog | JVM, Room migration/database, Compose and API-35 instrumentation testing | respective open-source licenses |

## Step-2 dependency decisions

- **Paging 3 was not added.** Step 2 now uses explicit deterministic Room index reads bounded to 512 rows/query, progressive snapshots, lazy Compose containers, cancellation checks, and 10,000-entry deterministic derivation tests. The ViewModel ultimately retains lightweight O(n) metadata, but the database no longer performs one giant `SELECT *` materialization for the media index. This satisfies the Step-2 incremental-loading requirement without adding a paging dependency solely for appearance.
- **Coil or another image loader was not added.** `ThumbnailRepository` uses Android platform thumbnail APIs plus a bounded 16 MiB `LruCache`, cancellation, request-size policy, and placeholder failure behavior.
- **DataStore was not added.** Library preferences use the existing Room database, avoiding a second preference persistence system.
- **DocumentFile was not required.** SAF tree scanning uses provider-aware `DocumentsContract` queries directly.
- **No FFmpeg or proprietary decoder binaries are present.** Real software-decoder routing remains later Step-6 work.

## Platform APIs intentionally used

Step 2 relies on Android platform APIs rather than unnecessary third-party libraries for:

- MediaStore discovery and modern system-mediated write/delete requests
- Storage Access Framework OpenDocument/OpenDocumentTree and provider operations
- DocumentsContract traversal/rename/delete
- MediaMetadataRetriever/MediaExtractor for optional deep metadata
- platform thumbnail loading
- BackHandler/activity-result flows through AndroidX

Any future dependency addition must record its exact version, purpose, and license here before the corresponding capability is considered release-ready.