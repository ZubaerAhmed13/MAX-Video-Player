# STEP 8 COMPLETION REPORT — CLOUD, CAST, TV & EXTERNAL DEVICES

## Certification status

**PASS — IMPLEMENTATION SOFTWARE / EMULATOR CERTIFIED**

The Step-8 implementation commit `d63f256d6003b23c11d4e793d472f6f9f483eeed` passed both required software/emulator workflows without bypassing a failed gate:

- **Android CI #342** — run id `34321902079` — PASS
- **Step 8 Certification #52** — run id `34321902080` — PASS

This report update is documentation-only. PR #16 remains unmerged until both workflows also pass on the documentation-only report head. Post-merge `main` must then be verified separately before Step 8 is declared merged/final on `main`.

## Certified software matrix

The implementation commit above passed:

- debug build and unit tests;
- 120 Step-8 unit/security tests;
- release compilation;
- Android lint;
- full API-35 instrumentation, including Step-1–8 coexistence/regression coverage;
- dedicated Step-8 API-35 cloud / USB / TV / external-display certification;
- API-26 legacy instrumentation;
- API-28 legacy instrumentation;
- Step-7 SMB, FTP, explicit FTPS and authenticated RTSP protocol certification.

No Step-8 software gate was skipped or marked successful by assumption.

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
- Direct/public versus relay/private source classification.
- Session-tokenized LAN relay for SAF/content, local files, SMB, FTP/FTPS, WebDAV/private HTTP and cloud.
- GET/HEAD, byte-range, bounded-buffer and >2 GiB-safe relay arithmetic.
- Receiver-safe subtitle relay and bounded SRT/ASS/SSA → WebVTT normalization.
- Authenticated HLS manifest/child rewriting through the same credential-aware DataSource path.
- Authenticated DASH BaseURL/SegmentTemplate rewriting with receiver template variables preserved.
- Sensitive child query credentials rejected instead of leaked to receiver URLs.
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
- Dedicated Compose-for-TV home surface.
- Local, Network, Cloud and USB/OTG destinations.
- D-pad focus movement, activation and focus-restoration instrumentation.
- Playback continues through the same service/session implementation as phone.

### External display

- Activity-owned `DisplayManager` / `Presentation` controller.
- Presentation-capable display enumeration; default display excluded.
- Same service-owned player attached to Presentation; no second player/session.
- Phone-window surface guard prevents Compose recomposition from stealing the video renderer during external output.
- Display removal/return-to-phone routing.
- Cast remote output and Presentation output are mutually exclusive.
- Instrumented controller lifecycle against the actual Android `DisplayManager` API.

## Regression preservation

Step 8 does not intentionally remove or replace Step 1–7 behavior. Android CI #342 certified the retained unit/build/lint, broad API-35 instrumentation, API-26/API-28 compatibility and Step-7 network protocol paths on the same implementation SHA.

The final broad API-35 regression also includes the Step-6 decoder/audio-only coexistence scenario with explicit MediaController seek-readiness synchronization; the behavioral position-preservation assertions remain intact.

## Dedicated Step-8 CI

`.github/workflows/step8-certification.yml` provides:

- `step8-unit-security`
- `step8-emulator-certification`

The emulator lane explicitly certifies cloud provider Range/auth paths, USB large/removal behavior, TV D-pad/focus behavior, external-display lifecycle and the Step-8 database migration.

## Security conclusions

Implemented boundaries include:

- no cloud client secrets in the APK;
- no plaintext persisted OAuth access/refresh tokens;
- no credentials in stable cloud media IDs;
- no credentials in Cast relay URLs/metadata;
- no unrestricted LAN directory server;
- no unrestricted Android storage permission;
- no whole-file buffering for video relay/large-media certification;
- no second ExoPlayer/MediaSession for TV/external devices;
- no intentional 32-bit media offset conversion;
- no silent direct-Cast downgrade for authenticated sources.

See `STEP_8_SECURITY.md` for details.

## Final merge gate

The implementation itself is software/emulator certified. The remaining release actions are procedural rather than missing Step-8 implementation:

1. Run `Android CI` and `Step 8 Certification` on this documentation-only report commit.
2. Do not merge if either exact-head workflow is red.
3. When both are green, mark PR #16 ready and merge using the certified exact head.
4. Verify both workflows again on the resulting `main` merge commit.
5. Only after green post-merge `main` verification record Step 8 as merged/final on `main`.

The PR/check history is the authoritative evidence for the documentation-head and post-merge gates, avoiding an impossible self-referential report commit that would need to contain its own future SHA.

## Hardware-deferred items

The following remain explicitly deferred to the final physical-device certification step and are **not** represented as emulator-tested hardware:

- real Chromecast / Google TV receiver discovery and route transfer;
- receiver-specific codec/container behavior and physical transfer-back timing;
- real HDMI/Miracast/desktop-mode adapters;
- real Android/Google TV remote/vendor focus quirks;
- real USB OTG controller/filesystem/vendor combinations.

These hardware-deferred items do not invalidate the Step-8 software/emulator PASS, but they must still be physically certified later.

## Declaration

**Step 8 implementation: SOFTWARE / EMULATOR PASS on `d63f256d6003b23c11d4e793d472f6f9f483eeed`.**

PR #16 is intentionally not yet declared merged/final in this report. The documentation-only exact-head gate and post-merge `main` verification remain mandatory. Step 9 has not been started.