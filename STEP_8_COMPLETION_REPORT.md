# STEP 8 COMPLETION REPORT — CLOUD, CAST, TV & EXTERNAL DEVICES

## Certification status

**PASS — HARDENED IMPLEMENTATION + POST-MERGE STABILIZATION SOFTWARE / EMULATOR CERTIFIED**

The original Step-8 implementation was merged through PR #16. A subsequent hardening audit identified remaining Cast-routing, truthful remote-control, signed adaptive-relay, transfer-continuity and Android-TV certification gaps; PR #17 closed those gaps and was merged to `main` at commit `2dae67550b428115df5eef7775a9de8969dc423c`.

Post-merge Android CI then exposed certification-harness instability rather than a missing Step-8 product feature. PR #18 (`step-8-post-merge-tv-back-stability`) corrects those harness/CI issues without removing product behavior:

- the full-suite TV test no longer depends on process-global or Espresso Android-window Back injection in a synthetic TV `ComponentActivity` that intentionally has no Android window focus;
- TV player Back behavior is still exercised with real Compose key events, while subtitle/audio/queue overlay state dismissal is verified through each production dialog's `Done` action, which calls the same app-owned `onDismiss` callback used by `onDismissRequest`;
- professional embedded-audio selection certification now derives the target from Media3's live track topology and verifies Media3's authoritative selected track instead of relying on a stale repository projection;
- network-protocol CI scopes APT to Ubuntu sources so an unrelated preinstalled third-party Chrome repository cannot block SMB/FTP/FTPS/RTSP server provisioning.

The stabilized implementation/test head `5f1a5609add772b7b75a89763d9a3ea0c8c95bdb` passed both required exact-head workflows:

- **Android CI #407** — run id `34402083201` — PASS
- **Step 8 Certification #118** — run id `34402083264` — PASS

This report commit is documentation-only. It must itself pass both workflows before PR #18 is merged. The resulting `main` merge commit must then pass the same post-merge verification before Step 8 is declared final on `main`.

## Certified software matrix

The stabilized exact head above passed:

- debug build and unit tests;
- Step-8 unit/security tests;
- release compilation;
- Android lint;
- full API-35 instrumentation, including Step-1–8 coexistence/regression coverage;
- dedicated Step-8 API-35 cloud / Cast / USB / TV / external-display certification;
- Android-TV no-touch player workflow using D-pad/media keys, deterministic focus restoration, player Back hierarchy, overlay open/dismiss state transitions and return to the TV library;
- API-26 legacy instrumentation;
- API-28 legacy instrumentation;
- Step-7 SMB, FTP, explicit FTPS and authenticated RTSP protocol certification.

No software gate was skipped, suppressed or converted to success after failure.

## Scope delivered

### Cloud

- Google Drive production browse/search/metadata/playback adapter.
- OneDrive production browse/search/metadata/playback adapter.
- Dropbox production browse/search/metadata/playback adapter.
- OAuth 2.0 Authorization Code + PKCE coordinator for all three providers.
- System-browser authorization and Activity redirect handling.
- Android-Keystore AES-GCM token vault; Room stores opaque auth references only.
- Refresh-token handling with reauthentication state.
- Cloud account UI, folder navigation, search and paging.
- Stable `maxcloud://` identity independent of temporary provider URLs.
- Seekable `CloudDataSource` with `Long` Range positions and revision checking.
- Deterministic production-adapter instrumentation for browse/auth and Range reads beyond 2 GiB.

### Google Cast

- Media3 Cast integrated into the existing service-owned session/player architecture.
- Production Media3 `MediaRouteButton` route picker.
- Explicit playback target ownership (`LOCAL_DEVICE` / `CAST_DEVICE`).
- Truthful gating of phone-only decoder/video-processing controls while Cast owns playback.
- Direct/public versus relay/private source classification.
- Session-tokenized LAN relay for SAF/content, local files, SMB, FTP/FTPS, WebDAV/private HTTP and cloud.
- GET/HEAD, byte-range, bounded-buffer and >2 GiB-safe relay arithmetic.
- Receiver-safe subtitle relay and bounded SRT/ASS/SSA → WebVTT normalization.
- Authenticated HLS manifest/child rewriting through the same credential-aware DataSource path.
- Authenticated DASH BaseURL/SegmentTemplate rewriting with receiver template variables preserved.
- Opaque relay mapping for signed/sensitive HLS/DASH child URLs so receiver-visible URLs do not expose credentials or reject valid signed resources.
- Deterministic local → Cast and Cast → local transfer-continuity tests for queue, position and session state.
- Transfer-back conversion retains original MediaItem mapping.

Physical Cast receiver route discovery/transfer behavior remains an explicit hardware certification item; emulator CI does not claim to be a Chromecast.

### USB / OTG

- Removable-volume observation through Android storage services.
- SAF file/tree picker remains the actual access mechanism.
- Persisted URI permission handling.
- No unrestricted storage permission.
- Test-only sparse logical >3.2 GB content source without allocating a 3 GB memory fixture.
- Real production `content://` DataSource read beyond the 2 GiB boundary using 64-bit positions.
- Deterministic unavailable/removed-source failure coverage.

### Android TV

- Optional Leanback launcher support and touchscreen-not-required declaration.
- Dedicated Compose-for-TV home and library surfaces.
- Local, Network, Cloud and USB/OTG destinations.
- Centralized TV player D-pad/media-key policy.
- TV-native Material3 shortcut controls with deterministic focus restoration.
- No-touch emulator workflow covering TV home → library → playback → media play/pause → seek/rewind/fast-forward → subtitle panel → professional audio panel → queue panel → player Back hierarchy → library return.
- Player Back behavior is certified through focused `Key.Escape` events for controls hiding and final return to the TV library.
- Subtitle/audio/queue overlays certify their production app-owned `onDismiss` state transition through the explicit `Done` action. AndroidX owns the separate dialog window and its platform Back routing; the synthetic TV `ComponentActivity` intentionally lacks Android window focus, so CI does not falsely claim physical-window Back certification.
- Playback continues through the same service/session implementation as phone; no second TV player/session is introduced.

### External display

- Activity-owned `DisplayManager` / `Presentation` controller.
- Presentation-capable display enumeration; default display excluded.
- Same service-owned player attached to Presentation; no second player/session.
- Phone-window surface guard prevents Compose recomposition from stealing the video renderer during external output.
- Display removal/return-to-phone routing.
- Cast remote output and Presentation output are mutually exclusive.
- Instrumented controller lifecycle against the actual Android `DisplayManager` API.

## Regression preservation

PR #18 changes certification/CI behavior only; it does not intentionally remove or replace Step 1–8 product functionality. Android CI #407 certified retained build/unit/lint behavior, broad API-35 instrumentation, API-26/API-28 compatibility and Step-7 network protocol paths on the same exact head.

The broad API-35 suite also re-certifies professional audio track selection against Media3's live topology, decoder/audio-only coexistence, playback-position preservation and the full Step-8 TV flow in the same regression process.

## Dedicated Step-8 CI

`.github/workflows/step8-certification.yml` provides:

- `step8-unit-security`
- `step8-emulator-certification`

The emulator lane explicitly certifies cloud provider Range/auth paths, Cast remote/truthful-control behavior, USB large/removal behavior, TV D-pad/focus/no-touch behavior, external-display lifecycle and the Step-8 database migration. The certification script requires every named Step-8 instrumentation class to execute at least one test and report success; zero-test or failed classes fail the job.

## Security conclusions

Implemented boundaries include:

- no cloud client secrets in the APK;
- no plaintext persisted OAuth access/refresh tokens;
- no credentials in stable cloud media IDs;
- no credentials in Cast relay URLs/metadata;
- signed adaptive-stream resources are represented through opaque receiver-safe relay mappings;
- no unrestricted LAN directory server;
- no unrestricted Android storage permission;
- no whole-file buffering for video relay/large-media certification;
- no second ExoPlayer/MediaSession for TV/external devices;
- no intentional 32-bit media offset conversion;
- no silent direct-Cast downgrade for authenticated sources;
- phone-only video/decoder controls do not pretend to affect a Cast receiver.

See `STEP_8_SECURITY.md` for additional security details.

## Final merge gate

The stabilized software/emulator head is certified on `5f1a5609add772b7b75a89763d9a3ea0c8c95bdb`. Remaining actions are procedural and must not be skipped:

1. Run `Android CI` and `Step 8 Certification` on this documentation-only report head.
2. Do not merge PR #18 if either exact-head workflow is red.
3. When both are green, merge the certified PR #18 exact head into `main`.
4. Verify both workflows again on the resulting `main` merge commit.
5. Only after green post-merge `main` verification record Step 8 as final on `main`.

The PR/check history is authoritative evidence for documentation-head and post-merge gates, avoiding an impossible self-referential report commit that would need to contain its own future SHA.

## Hardware-deferred items

The following remain explicitly deferred to the final physical-device certification step and are **not** represented as emulator-tested hardware:

- real Chromecast / Google TV receiver discovery and route transfer;
- receiver-specific codec/container behavior and physical transfer-back timing;
- real HDMI/Miracast/desktop-mode adapters;
- real Android/Google TV remote/vendor focus quirks;
- real AndroidX dialog-window Back routing on a physical TV/remote;
- real USB OTG controller/filesystem/vendor combinations.

These hardware-deferred items do not invalidate the Step-8 software/emulator PASS, but they must still be physically certified later.

## Declaration

**Step 8 hardened + stabilized implementation: SOFTWARE / EMULATOR PASS on `5f1a5609add772b7b75a89763d9a3ea0c8c95bdb`.**

PR #16 and PR #17 are already merged. PR #18 remains intentionally unmerged until this documentation-only head passes both exact-head workflows and the resulting `main` merge commit passes post-merge verification. Step 9 has not been started.
