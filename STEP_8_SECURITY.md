# STEP 8 SECURITY — CLOUD, CAST, TV & EXTERNAL DEVICES

## Security objectives

Step 8 adds cloud OAuth, receiver-visible LAN serving, removable storage and secondary-display rendering. These features add attack surfaces that do not exist in local-only playback. This document records the implemented trust boundaries and non-negotiable security behavior.

## 1. Cloud credentials

### OAuth model

Google Drive, OneDrive and Dropbox use OAuth 2.0 Authorization Code with PKCE as public clients. No provider client secret is shipped in the APK. Authorization is opened in the system browser and callback handling validates the expected redirect, state value and PKCE verifier transaction.

Authorization transactions expire and are process-memory state. A callback without the matching pending transaction is rejected.

### Token storage

Raw access and refresh tokens are never stored in Room. `CloudTokenVault` encrypts token payloads with AES-GCM using a key generated in Android Keystore. Room stores only opaque auth references plus provider/account display metadata.

If an encrypted token cannot be recovered, the account transitions to reauthentication instead of falling back to plaintext or silently using stale material.

### Token refresh

Provider byte/API requests request a currently usable access token. Expiring tokens refresh under a mutex to avoid concurrent refresh races. A provider 401 is retried once after forced refresh. Refresh failure moves the provider to reauthentication-required state.

### URL/log policy

Stable `maxcloud://` identities do not include OAuth tokens. Temporary provider download URLs are not used as media identity. Sensitive query keys and authorization headers are redacted by the existing sensitive-data policy and are not intended for persisted diagnostics.

## 2. Cast relay boundary

### Session isolation

The relay exists only for an active Cast session. It binds to a concrete LAN interface rather than `0.0.0.0`, assigns a cryptographically random per-session token, and uses opaque resource ids. Stopping the Cast session clears registered resources and closes the server.

### Request restrictions

The relay supports only GET and HEAD. It rejects malformed paths, traversal encodings, unknown resources and invalid/unsatisfiable ranges. Request line/header sizes and client concurrency are bounded. Responses disable caching and MIME sniffing.

No directory index, filesystem root, source path, SMB credential, WebDAV credential, OAuth token or network password is exposed by the relay URL.

### Byte streaming

Progressive media is streamed through bounded buffers from the existing credential-aware DataSource. The relay does not construct a whole-file in-memory buffer and does not make a temporary full media copy. Range positions use signed 64-bit values end to end.

### Receiver source classification

Only public, receiver-reachable HTTP(S) is sent directly to Cast. `content://`, file, SMB, FTP/FTPS, private HTTP/WebDAV and private cloud are relayed. RTSP remains truthfully unsupported by this Cast path.

### Authenticated adaptive streaming

Private HLS/DASH manifests are fetched with credentials on-device and rewritten to relay URLs. Child requests are allowed only for resolved HTTP(S) targets and may not contain URI user-info credentials.

The manifest rewriter rejects child URLs with query names that appear to carry secrets such as token/access_token/signature/auth. This avoids leaking a signed provider/CDN URL to the receiver. Such a source must fail explicitly rather than silently reducing credential isolation.

Manifest input/output sizes, encoded child target size and live-manifest cache lifetime are bounded.

### Subtitles

Subtitle relay is bounded to 16 MB. SRT and ASS/SSA conversion to WebVTT occurs in memory only for the bounded subtitle asset. Media remains untranscoded. Advanced ASS styling is not represented as preserved.

## 3. Storage permissions

USB/OTG and user-selected files use Android Storage Access Framework. The application does not request `MANAGE_EXTERNAL_STORAGE`. Broad filesystem traversal is not added for Step 8.

Persistable URI permission is attempted only for the URI returned by the system picker. Loss of permission or device removal produces an unavailable/recoverable source state rather than bypassing Android storage controls.

The >3 GB certification provider is an androidTest-only logical content provider. It validates Long offsets without allocating or embedding a 3 GB test artifact.

## 4. External-display security and ownership

`DisplayManager.DISPLAY_CATEGORY_PRESENTATION` is used to enumerate eligible secondary displays; the phone/default display is not treated as an external target.

The Presentation attaches only the existing service-owned MediaController/player. A second playback engine is never created for the external display. This prevents competing queue/session ownership and reduces accidental duplicate decoder/audio pipelines.

While external output is active, Activity-window `PlayerView` instances are detached so recomposition cannot steal the rendering surface from the Presentation. On disconnect, the surface returns to the local window.

Cast remote output and Presentation output are mutually exclusive.

## 5. TV surface

TV support uses normal application permissions and the existing playback service. It does not expose a remote-control network server. Leanback/touchscreen manifest declarations are optional capability declarations, not additional privilege.

Focus state contains navigation state only and no credential data.

## 6. Secret-handling rules for future changes

Any future Step-8 maintenance must preserve these rules:

1. Never persist raw cloud access/refresh tokens in Room, DataStore, logs, media ids or history.
2. Never add provider client secrets to the APK or repository.
3. Never put SMB/FTP/WebDAV/cloud credentials in Cast relay URLs or Cast metadata.
4. Never expose an unrestricted LAN file server; relay resources must remain active-session scoped.
5. Never downgrade a private/authenticated source to direct Cast merely because the URI scheme is HTTP(S).
6. Never add unrestricted storage permission to make USB support easier.
7. Never create a second ExoPlayer/MediaSession for TV or external-display parity.
8. Never convert `Long` file offsets/sizes to `Int` for media addressing.
9. Fail explicitly when a receiver-safe transformation cannot preserve the security boundary.

## 7. Validation scope

Automated tests cover PKCE construction/state comparison, source classification, Cast range parsing/server behavior, authenticated manifest rewriting, provider authorization headers, cloud Range reads above 2 GiB, SAF large-file reads/removal, TV D-pad focus and DisplayManager controller lifecycle.

Physical Chromecast/Google TV receiver behavior, real HDMI/Miracast adapters and real removable hardware are hardware certification items and must not be represented as automatically proven by emulator CI.