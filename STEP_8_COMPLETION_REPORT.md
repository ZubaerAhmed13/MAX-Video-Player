# STEP 8 COMPLETION REPORT — CLOUD, CAST, TV & EXTERNAL DEVICES

## Final certification status

**PASS — SOFTWARE / EMULATOR CERTIFIED AND MERGED ON `main`**

The final Step-8 implementation software tree is merged on `main` at:

`bbbb0036a2a4f59a8fce69345d23fb77122b913f`

That implementation was certified twice at the final production state:

### Exact PR-head certification

- **Android CI #411** — run id `34405790695` — **PASS**
- **Step 8 Certification #122** — run id `34405790655` — **PASS**

### Post-merge `main` certification

- **Android CI #412** — run id `34406483776` — **PASS**
- **Step 8 Certification #123** — run id `34406483944` — **PASS**

No failed software gate was bypassed, skipped, converted to an assumption, or weakened to obtain this result.

## Merge and hardening audit trail

Step 8 reached the certified `main` state through the following merged pull requests:

| PR | Purpose | Merge commit |
| --- | --- | --- |
| #16 | Initial Step-8 cloud, Cast, TV and external-device implementation | `b05a9aa4d6dd6ec8813eec5ed966a2678836ac60` |
| #17 | Cast picker, truthful remote-control ownership, signed adaptive relay hardening and TV no-touch hardening | `2dae67550b428115df5eef7775a9de8969dc423c` |
| #18 | Post-merge TV/audio/network certification-harness stabilization | `1e7d5680192becb9027e852de9b7d16db6e3b5c1` |
| #19 | Prevent redundant same-mode decoder reprepare and diagnostics race | `565aa1fa6d48f01232e1f82b3819ca94720ac3aa` |
| #20 | Restore cached per-video decoder overrides synchronously during queue navigation | `bbbb0036a2a4f59a8fce69345d23fb77122b913f` |

PRs #16 through #20 are all merged. The final implementation merge commit is the PR #20 merge shown above.

## Certified software matrix

The final implementation passed all of the following on the exact PR head and again after merge to `main`:

- debug build and JVM/unit tests;
- Step-8 unit and security tests;
- release compilation;
- Android lint;
- full API-35 instrumentation, including Step-1–8 coexistence and regression coverage;
- dedicated Step-8 API-35 cloud / Cast / USB / TV / external-display certification;
- API-26 legacy instrumentation;
- API-28 legacy instrumentation;
- deterministic SMB, FTP, explicit FTPS and authenticated RTSP protocol certification;
- Step-6 decoder/audio/subtitle/queue coexistence regression coverage, including per-video decoder restoration after queue navigation.

The dedicated Step-8 emulator workflow requires every named Step-8 instrumentation class to execute tests successfully; zero-test or failed classes fail the gate.

## Scope delivered

### Cloud

- Google Drive production browse/search/metadata/playback adapter.
- OneDrive production browse/search/metadata/playback adapter.
- Dropbox production browse/search/metadata/playback adapter.
- OAuth 2.0 Authorization Code + PKCE coordination for all three providers.
- System-browser authorization and Activity redirect handling.
- Android-Keystore AES-GCM token vault; Room stores opaque auth references rather than plaintext tokens.
- Refresh-token handling with reauthentication state.
- Cloud account UI, folder navigation, search and paging.
- Stable `maxcloud://` identity independent of temporary provider URLs.
- Seekable cloud DataSource behavior with `Long` range positions and revision checking.
- Deterministic production-adapter instrumentation for authentication/browse paths and range reads beyond 2 GiB.

### Google Cast

- Media3 Cast integrated into the existing service-owned player/session architecture.
- Production Media3 `MediaRouteButton` route picker.
- Explicit playback-target ownership: `LOCAL_DEVICE` versus `CAST_DEVICE`.
- Truthful gating of phone-only decoder/video-processing controls while Cast owns playback.
- Direct/public versus relay/private source classification.
- Session-tokenized LAN relay for SAF/content, local files, SMB, FTP/FTPS, WebDAV/private HTTP and cloud sources.
- GET/HEAD, byte-range, bounded-buffer and >2 GiB-safe relay arithmetic.
- Receiver-safe subtitle relay and bounded SRT/ASS/SSA to WebVTT normalization.
- Authenticated HLS manifest/child rewriting through the credential-aware DataSource path.
- Authenticated DASH BaseURL/SegmentTemplate rewriting while preserving receiver template variables.
- Opaque relay mappings for signed/sensitive HLS and DASH child URLs so receiver-visible URLs do not expose credentials or reject valid signed resources.
- Deterministic local-to-Cast and Cast-to-local continuity coverage for queue, position and session state.
- Transfer-back conversion retaining original MediaItem mappings.

A CI emulator does not claim to be a physical Chromecast receiver. Real receiver discovery and transfer remain hardware-deferred.

### USB / OTG

- Removable-volume observation through Android storage services.
- SAF file/tree picker remains the access mechanism.
- Persisted URI permission handling.
- No unrestricted storage permission.
- Sparse logical >3.2 GB test content without allocating a multi-gigabyte memory fixture.
- Production `content://` DataSource reads beyond the 2 GiB boundary with 64-bit positions.
- Deterministic unavailable/removed-source failure coverage.

### Android TV

- Optional Leanback launcher support and touchscreen-not-required declaration.
- Dedicated Compose-for-TV home and library surfaces.
- Local, Network, Cloud and USB/OTG destinations.
- Centralized TV player D-pad/media-key policy.
- TV-native Material3 shortcut controls with deterministic focus restoration.
- Remote-only workflow coverage spanning TV home → library → playback → media play/pause → seek/rewind/fast-forward → subtitle panel → professional audio panel → queue panel → Back hierarchy → library return.
- Player-level Back behavior in the synthetic API-35 TV host is exercised with focused TV `Key.Escape` input.
- Subtitle/audio/queue dialog state is certified through each production dialog's app-owned dismissal path (`Done` / `onDismiss`).
- The synthetic TV host intentionally does **not** claim to reproduce a physical AndroidX dialog-window Back dispatch path when no real Android window owns focus.
- Playback continues through the same service/session implementation used on phone; there is no second TV player/session.

Physical Android/Google TV dialog-window Back routing and vendor-specific remote/focus behavior remain explicit real-hardware certification items.

### External display

- Activity-owned `DisplayManager` / `Presentation` controller.
- Presentation-capable display enumeration with the default display excluded.
- The same service-owned player attaches to Presentation output; no second player/session is created.
- Phone-window surface guard prevents Compose recomposition from stealing the renderer during external output.
- Display removal and return-to-phone routing.
- Cast remote output and Presentation output are mutually exclusive.
- Instrumented controller lifecycle against Android `DisplayManager` APIs.

## Regression hardening completed during final certification

### PR #19 — redundant same-mode decoder reprepare

Final regression testing found that requesting the same decoder mode already active for the same media unnecessarily reset decoder candidates/diagnostics and emitted a reprepare. Recreating an identically named codec could then allow a late release callback from the old instance to make diagnostics appear inactive.

The production fix keeps the live decoder/candidate state when the effective request has not changed while still updating and persisting per-video override ownership. Genuine mode changes continue to reconfigure the player.

### PR #20 — per-video decoder restoration during queue navigation

Post-merge regression testing then exposed a separate B → C → B navigation race: a saved per-video decoder override could temporarily fall back to the global mode while an asynchronous Room lookup completed.

The final production fix adds an in-session per-media override cache. User selections enter the cache immediately, cached overrides restore synchronously during media activation, a renderer reconfigure is emitted when the restored mode differs from the previous item's policy, and Room remains the cold-start persistence fallback. A stale asynchronous lookup cannot overwrite a newer cached user selection.

The existing Step-6 coexistence assertions were not weakened. The full API-35 suite passed on PR #20 and again on the merged `main` implementation.

## Dedicated Step-8 CI

`.github/workflows/step8-certification.yml` provides two mandatory jobs:

- `step8-unit-security`
- `step8-emulator-certification`

The emulator lane certifies cloud provider range/auth behavior, Cast remote/truthful-control behavior, USB large/removal behavior, TV D-pad/focus/no-touch behavior, external-display lifecycle, and the Step-8 database migration.

The required Step-8 instrumentation classes are:

- `com.zubaer.maxvideoplayer.feature.cloud.Step8CloudProviderIntegrationTest`
- `com.zubaer.maxvideoplayer.feature.cast.Step8CastRemoteUiInstrumentedTest`
- `com.zubaer.maxvideoplayer.feature.usb.Step8UsbVirtualProviderTest`
- `com.zubaer.maxvideoplayer.feature.tv.Step8TvFocusInstrumentedTest`
- `com.zubaer.maxvideoplayer.feature.tv.Step8TvNoTouchPlaybackInstrumentedTest`
- `com.zubaer.maxvideoplayer.feature.output.Step8ExternalDisplayInstrumentedTest`
- `com.zubaer.maxvideoplayer.core.database.Step8DatabaseMigrationTest`

## Security conclusions

The certified implementation retains these boundaries:

- no cloud client secrets in the APK;
- no plaintext persisted OAuth access/refresh tokens;
- no credentials in stable cloud media IDs;
- no credentials in Cast relay URLs/metadata;
- signed adaptive-stream resources represented through opaque receiver-safe relay mappings;
- no unrestricted LAN directory server;
- no unrestricted Android storage permission;
- no whole-file buffering for video relay or large-media certification;
- no second ExoPlayer/MediaSession for TV or external-device output;
- no intentional 32-bit media-offset conversion;
- no silent direct-Cast downgrade for authenticated sources;
- phone-only video/decoder controls do not pretend to affect a Cast receiver.

See `STEP_8_SECURITY.md` for the detailed security model.

## Hardware-deferred items

The following are intentionally deferred to the final physical-device certification step and are **not** represented as emulator-tested hardware:

- real Chromecast / Google TV receiver discovery and route transfer;
- receiver-specific codec/container behavior and physical transfer-back timing;
- real HDMI, Miracast and desktop-mode adapters;
- real Android/Google TV remotes, physical dialog-window Back routing and vendor-specific focus/input quirks;
- real USB OTG controllers, filesystems and vendor/device combinations.

These items do not invalidate the Step-8 software/emulator certification, but they must still be physically certified before making claims about those hardware combinations.

## Documentation-only finalization

The certified implementation software tree is `bbbb0036a2a4f59a8fce69345d23fb77122b913f`.

This report update is documentation-only; it does not change the certified implementation code. Its own future merge SHA is intentionally not embedded in this file, avoiding an impossible self-referential certification loop. The pull-request/check history is the authoritative evidence for this report head and for the final documentation-only `main` verification.

## Declaration

**Step 8 implementation: SOFTWARE / EMULATOR PASS AND MERGED ON `main` at `bbbb0036a2a4f59a8fce69345d23fb77122b913f`.**

All Step-8 implementation and regression-hardening PRs (#16–#20) are merged. Physical hardware certification remains deferred to the final overall device-testing step. **Step 9 has not been started.**
