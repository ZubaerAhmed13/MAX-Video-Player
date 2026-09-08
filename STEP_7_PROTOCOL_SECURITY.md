# Step 7 Protocol and Security Policy

## Security invariants

- Passwords, bearer tokens, cookies and sensitive custom headers never enter Room, media IDs, public MediaItem metadata, diagnostics or logs.
- URLs containing `userinfo` are rejected. Credentials must be entered separately and stay in the process-local request registry or encrypted vault.
- Room stores only an opaque credential reference and a non-secret username hint.
- Saved secrets are AES/GCM encrypted with a non-exportable Android Keystore key before SharedPreferences persistence.
- Invalid or undecryptable vault payloads are deleted and the UI returns to an authentication-required flow.
- URI diagnostics remove passwords and sensitive query values, including signed URL families such as `X-Amz-*` and `X-Goog-*`.
- HTTP authorization is scoped to the exact scheme, host, effective port and directory boundary. Redirects to another origin do not receive it.
- WebDAV response entries must remain on the saved origin and below the saved root.

## Transport policy

| Protocol | Transport policy | Authentication policy | Certificate / integrity policy |
|---|---|---|---|
| HTTP | Cleartext only after an explicit destination warning and acknowledgement | Optional request headers or credentials are process-local | Not encrypted; UI states this plainly |
| HTTPS | TLS through OkHttp | Basic, bearer or custom headers | Android system trust store and hostname verification; no trust-all path |
| HLS / DASH | HTTP transport rules apply to manifests and every child request | Header scope follows exact origin/directory; cross-origin children require their own authorization policy | HTTPS recommended; HTTP requires the same explicit cleartext acknowledgement |
| RTSP | Media3 RTSP integration with explicit RTP-over-RTSP/TCP | BASIC/DIGEST credentials are injected only into a private source URI, then masked from the public timeline | TCP-capable server required; debug message logging is disabled |
| SMB | SMB 2.0.2 through SMB 3.1.1 only | Guest, anonymous or username/password/domain | Signing enabled. SMB3 server/share-required encryption is honored; encryption is not claimed for SMB2 |
| WebDAV | HTTPS by default; HTTP only after saved-source acknowledgement | Basic, bearer and scoped custom headers | Normal TLS verification for HTTPS plus bounded, DTD-disabled pull parsing |
| FTP | Plain FTP | Anonymous or username/password | Explicit warning and acknowledgement required before saving a password |
| FTPS | Explicit TLS FTP plus protected data channel (`PBSZ 0`, `PROT P`) | Username/password | Endpoint checking enabled; no trust-all or clear data channel |

Android cannot express a runtime, user-selected cleartext hostname allowlist in static Network Security Configuration. The application therefore permits the platform socket capability needed for user-selected HTTP URLs, while direct and saved HTTP/WebDAV-HTTP product flows require explicit warning acknowledgement. The persisted acknowledgement does not claim that HTTP is secure.

## HTTP header and redirect rules

`NetworkRequestRegistry` holds access context only in memory. For HTTP-family sources, `RegistryHeaderInterceptor` re-resolves every request. A manifest or segment receives headers only when it remains under the registered directory on the same scheme, host and effective port. OkHttp may follow normal redirects, but credentials are not blindly copied to the redirected request.

The production client uses:

- 15-second connect timeout
- 30-second read timeout
- 30-second write timeout
- OkHttp connection-failure retry
- normal HTTP/HTTPS redirect handling
- a fixed application User-Agent

## XML and path security

WebDAV XML is capped at 4 MiB before parsing. Declared oversized bodies fail before the response stream is opened; unknown-length bodies are copied in bounded chunks and fail before any oversized chunk is committed to the output allocation. `XmlPullParser` has document declarations disabled, and a `DOCDECL` token is rejected if encountered. No external entity resolver is installed. Returned `href` values are normalized and rejected if they cross origin or escape the saved root. SMB and FTP child paths pass through `safeRemotePath`, which resolves `.` and bounded `..` segments and rejects traversal above the root.

## Bounded content

- remote subtitle reads: 16 MiB maximum
- remote M3U reads: 2 MiB maximum
- expanded M3U entries: 1,000 maximum
- WebDAV XML: 4 MiB maximum
- SMB read call: 1 MiB maximum
- retry/reconnect loops: bounded
- diagnostic event history: 30 entries maximum

Normal video/audio playback remains streaming and random-access. These bounds do not create a full-file download or a media-size ceiling.

## Credential lifecycle

Saving a source can create or update an opaque vault record. Editing with blank secret fields preserves the saved secret; choosing not to remember removes it. `Forget credentials` keeps server configuration and removes the secret when no location references it. Removing a location preserves playback history and playlist rows, and deletes its vault record only after the final reference is gone.

## Test evidence

Instrumentation certifies encryption/update/delete/invalidation, saved-HTTP Room/vault round-trip, signed-query canonical identity, header redaction, cross-origin redirect isolation, credential URL rejection, direct/saved cleartext acknowledgement, RTSP private credential injection, WebDAV HTTP, pre-allocation bounds, XXE rejection, Unicode parsing, root confinement and path traversal defense. The dedicated protocol lane additionally uses isolated authenticated servers and never depends on a personal NAS or a public media endpoint.

## Explicit limitations

- Self-signed/private CA certificates are not accepted unless installed into the Android system trust configuration outside the app.
- Certificate pinning is not configured because arbitrary user-owned servers cannot share a static pin set.
- The repository-owned FTPS certificate/private key and debug CA are non-production CI fixtures. Release builds retain system trust only.
- SFTP is not implemented; it is not aliased to FTP or FTPS.
- SMB encryption is not described as universal because SMB2 has no SMB3 transport encryption.
