# STEP 8 COMPLETION REPORT — CLOUD, CAST, TV & EXTERNAL DEVICES

## Certification status

**CANDIDATE — FINAL CI REQUIRED**

This report is intentionally not marked final PASS until both required workflows succeed on the exact final Step-8 commit and post-merge `main` is verified.

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
- Seekable `CloudDataSource` with Long Range positions and revision checking.
- Deterministic production-adapter instrumentation for browse/auth and Range reads beyond 2 GiB.

### Google Cast

- Media3 Cast integrated into the existing service-owned session/player architecture.
- Direct/public versus relay/private source classification.
- Session-tokenized LAN relay for SAF/content, local files, SMB, FTP/FTPS, WebDAV/private HTTP and cloud.
- GET/HEAD, byte-range, bounded-buffer and >2 GiB-safe relay arithmetic.
- Receiver-safe subtitle relay and bounded SRT/ASS/SSA -> WebVTT normalization.
- Authenticated HLS manifest/child rewriting through the same credential-aware DataSource path.
- Authenticated DASH BaseURL/SegmentTemplate rewriting with receiver template variables preserved.
- Sensitive child query credentials rejected instead of leaked to receiver URLs.
- Transfer-back conversion retains original MediaItem mapping.

Physical Cast receiver route-discovery/transfer behavior remains an explicit hardware certification item; emulator CI does not claim to be a Chromecast.

### USB / OTG

- Removable-volume observation through Android storage services.
- SAF file/tree picker remains the actual access mechanism.
- Persisted URI permission handling.
- No unrestricted storage permission.
- Logical >3.2 GB content-provider certification without allocating a 3 GB fixture.
- Real DataSource read beyond 2 GiB and clean failure after source removal.

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

Step 8 does not intentionally remove or replace Step 1–7 behavior. Existing Android CI remains enabled, including unit tests, release compile, lint, full API-35 instrumentation, network protocol certification and legacy thumbnail tests.

## New dedicated CI

`.github/workflows/step8-certification.yml` adds:

- `step8-unit-security`
- `step8-emulator-certification`

The emulator lane explicitly runs cloud provider Range/auth tests, USB large/removal tests, TV D-pad tests, external-display lifecycle tests and Step-8 DB migration tests.

## Security conclusions

Implemented boundaries include:

- no cloud client secrets in the APK;
- no plaintext persisted OAuth access/refresh tokens;
- no credentials in stable cloud media ids;
- no credentials in Cast relay URLs/metadata;
- no unrestricted LAN directory server;
- no unrestricted Android storage permission;
- no whole-file buffering for video relay/large-media certification;
- no second ExoPlayer/MediaSession for TV/external devices;
- no intentional 32-bit media offset conversion;
- no silent direct-Cast downgrade for authenticated sources.

See `STEP_8_SECURITY.md` for details.

## Remaining completion actions

Before changing this report to **PASS**, the following must happen:

1. Run `Android CI` on the exact final branch head and obtain green conclusion.
2. Run `Step 8 Certification` on that same exact head and obtain green conclusion.
3. Fix any compile/unit/instrumentation/lint failure and repeat until both workflows are green.
4. Update this report with the certified commit SHA and workflow run ids/conclusions.
5. Merge PR #16 to `main` without bypassing a failed software gate.
6. Verify the post-merge `main` workflow runs and record their green result.

## Hardware-deferred items

The following are not blockers for the software Step-8 branch only when they remain clearly labeled for later physical-device certification:

- real Chromecast / Google TV receiver discovery and route transfer;
- receiver-specific codec/container behavior and physical transfer-back timing;
- real HDMI/Miracast/desktop-mode adapters;
- real Android/Google TV remote/vendor focus quirks;
- real USB OTG controller/filesystem/vendor combinations.

These items must not be represented as automatically tested by emulator CI.

## Final declaration

**Not yet final PASS at the time this candidate report is authored.** The implementation is ready for exact-head CI certification; the final declaration will be made only from workflow evidence.