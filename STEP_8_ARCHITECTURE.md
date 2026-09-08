# STEP 8 ARCHITECTURE — CLOUD, CAST, TV & EXTERNAL DEVICES

## Status

Step 8 extends the existing single service-owned playback architecture. It does **not** replace the Step 1–7 playback stack and does not introduce a second ExoPlayer/MediaSession for cloud, Cast, TV, USB, or external displays.

The authoritative playback path remains:

`UI -> PlaybackConnection -> PlaybackService -> MediaSession -> CastPlayer(local Media3 player + remote Cast player)`

Local, network, cloud, SAF/USB, subtitle and Cast-relay reads converge on Media3 `DataSource` abstractions so queue state, resume/history identity, track selection, decoder selection, audio processing and error mapping remain shared.

## 1. Cloud architecture

### Providers

Production provider adapters are implemented for:

- Google Drive
- Microsoft OneDrive
- Dropbox

Every adapter implements `CloudProviderClient` and exposes browse, search, metadata lookup, and seekable playback-resource resolution. Provider API roots default to the real production endpoints. Endpoint constructor parameters exist only so deterministic instrumentation can exercise the same production adapters against MockWebServer.

### Identity

Cloud history and queue identity never depends on a signed or temporary download URL. `CloudFileIdentity` is encoded into a process-safe `maxcloud://` URI by `CloudUriCodec`. The identity contains provider, application account id, provider file id and, where required, drive id. Optional revision information is carried separately to detect remote-file changes.

### Authentication

`CloudOAuthCoordinator` implements public-client OAuth 2.0 Authorization Code + PKCE for all three providers. The system browser performs authorization. State and PKCE verifier/challenge checks protect the redirect transaction. No client secret is embedded in the APK.

`CloudTokenVault` stores access and refresh tokens only inside Android-Keystore AES-GCM encrypted storage. Room stores only an opaque auth reference and non-secret account metadata. `VaultBackedAccessTokenProvider` refreshes expiring tokens and surfaces reauthentication when refresh is no longer possible.

### Playback

`CloudDataSource` decodes stable `maxcloud://` identities, resolves the currently valid provider resource, preserves expected revision, then opens the real resolved HTTP resource through Media3 OkHttp. `DataSpec.position` and `DataSpec.length` remain `Long`; Range seeking does not truncate to 32-bit values.

Google Drive and Dropbox keep provider authorization headers process-local and add them only at the resolved byte request. OneDrive uses authenticated Microsoft Graph metadata calls and the provider-issued temporary download URL without copying Graph credentials into that URL.

## 2. Cast architecture

### One playback session

`PlaybackService` owns a Media3 `CastPlayer` that combines the existing local player with a Media3 remote Cast player. The service remains the single MediaSession owner. Route transfer therefore does not create an unrelated playback engine or a second queue/history owner.

### Direct versus relay decision

`CastSourceResolver` classifies each source as:

- `DIRECT_CAST` — public receiver-reachable HTTP(S)
- `LOCAL_RELAY` — SAF/content, file, SMB, FTP/FTPS, private HTTP/WebDAV, private cloud, or HTTP(S) not safely receiver-reachable
- `MANIFEST_RELAY` — authenticated/private HLS or DASH
- `UNSUPPORTED_CAST` — currently RTSP and unknown uncertified schemes

The classification is deterministic pure policy and has JVM tests.

### Progressive relay

`CastRelayServer` is session scoped and binds to a concrete LAN interface. Receiver URLs contain a random session token and opaque resource id, never credentials. The server supports GET, HEAD and HTTP byte ranges, uses bounded 64 KiB copy buffers, keeps all offsets as `Long`, has bounded client/header/request limits, rejects traversal and unknown resources, and exposes no directory listing.

`DataSourceCastRelayResource` opens the existing production DataSource stack at the requested Long offset, so SAF, SMB, FTP/FTPS, WebDAV/HTTP and cloud reads are relayed without a whole-file cache or transcoding.

### Authenticated HLS/DASH relay

`CastAdaptiveManifestRelayResource` is request-aware. The top-level authenticated manifest is fetched through the existing credential-aware DataSource router and rewritten to the tokenized LAN endpoint. Child HLS playlists, keys and segments, and DASH BaseURL/SegmentTemplate URL references are also routed back through the same relay.

The receiver never receives Authorization headers, cookies, stored credentials, OAuth tokens, or app-private source URLs. Manifest reads are bounded; live manifests use a short cache and refresh. DASH `$Number$`, `$Time$`, `$RepresentationID$` and related receiver template substitutions are preserved.

Sensitive signed-query child URLs are deliberately rejected rather than copying secret query parameters into a receiver-visible URL. This is a security boundary, not a silent fallback.

### Subtitles

Receiver-safe external subtitles are relayed as session resources. SRT and ASS/SSA are bounded and normalized to WebVTT where needed. This preserves text/timing for Cast but does not falsely claim advanced ASS styling is preserved by the receiver.

## 3. USB / OTG / removable storage

Android Storage Access Framework remains the file-access boundary. There is no MANAGE_EXTERNAL_STORAGE or unrestricted filesystem permission.

`RemovableStorageController` observes removable mounted volumes through `StorageManager`/storage callbacks for UI status. Actual user file/folder access is still obtained through `ACTION_OPEN_DOCUMENT` / `ACTION_OPEN_DOCUMENT_TREE` and persisted URI permission when granted.

Playback remains reference based. Large removable media is not copied into app storage or loaded wholly into RAM. The Step-8 virtual provider test exposes a logical file above 3.2 GB and verifies reads beyond 2 GiB plus clean failure after source removal.

## 4. Android TV

The manifest advertises optional Leanback support and a TV launcher entry without requiring a touchscreen. TV entry uses a dedicated Compose-for-TV home surface rather than treating phone controls as TV parity.

`TvHomeScreen` uses `androidx.tv.material3`, D-pad focusable destination controls, deterministic `FocusRequester` handling, and focus restoration. TV destinations include local library, Network, Cloud and USB/OTG. Playback remains the same service/session/player used by phone.

## 5. External displays

`ExternalDisplayController` is Activity owned because Android `Presentation` requires an Activity/display context. It obtains presentation-capable displays from `DisplayManager.DISPLAY_CATEGORY_PRESENTATION` and explicitly excludes the default display.

External video is rendered by a `PlayerView` attached to the **same** service-owned MediaController/player. No second player is created. While the Presentation owns video, an Activity-window pre-draw guard keeps phone-window `PlayerView` instances detached so Compose recomposition cannot steal the video surface back. On display loss or Return to phone, the same player is reattached locally and queue/position/audio/session state is preserved.

Cast and external-display video output are mutually exclusive. If Media3 reports remote Cast playback, Presentation output is dismissed and local surface reattachment is suppressed until playback becomes local again.

## 6. Source/error invariants

Across cloud, USB, network and external-device features:

- stable media identity is distinct from temporary transport URL;
- credentials do not enter media ids, persisted URIs, Room rows, logs or receiver URLs;
- byte positions and sizes use `Long`;
- source removal/permission loss is surfaced as an explicit recoverable/unavailable state instead of undefined reads;
- no new path intentionally bypasses decoder, subtitle, audio, queue, resume/history, network diagnostics, or PlaybackService ownership.

## 7. Hardware certification boundary

Step 8 provides production code and deterministic JVM/emulator certification for the software paths. CI cannot honestly prove behavior against every physical Chromecast/Google TV, HDMI adapter, Miracast implementation, USB controller or vendor TV remote. Physical receiver/display/USB-device certification remains a final-device test activity (the project’s later physical-device step). The software implementation must still compile and pass all Step-8 deterministic tests before Step 8 can be marked complete.