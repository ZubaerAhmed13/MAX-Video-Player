# Step 7 Completion Report — Professional Network Playback and Sources

## Final result

**STEP 7: PASS**

Implementation and certification are complete for the declared software/emulator scope. The exact implementation head, documentation-complete PR head and resulting `main` merge head passed the full workflow including the isolated network-protocol lane.

## Repository

- Branch: `step-7-professional-network-playback`
- Certified implementation remote SHA: `4cb661b978de502b99bef864cc99a53c1540939a`
- Implementation certification: Android CI run #242 / workflow `34239231820` — PASS
- Pull request: #12
- Documentation-complete branch SHA: `1a89ecc48906e151378373eb4af7f5210b8353c9`
- Documentation-complete certification: Android CI run #243 / workflow `34241083327` — PASS
- Merge SHA / certified implementation `main`: `9d271c4786236577e009f63d65ad7435b31c015b`
- Post-merge certification: Android CI run #244 / workflow `34242218019` — PASS

## Steps 1–6 regression

The workflow retains debug/unit, release, lint, complete API-35 instrumentation, API-26 thumbnail and API-28 thumbnail lanes. The existing Step-1 playback/history, Step-2 library, Step-3 player/PiP, Step-4 subtitle, Step-5 audio/DSP and Step-6 decoder/coexistence suites remain present. Step 7 does not create a parallel player or remove any prior table, feature or test.

## Network architecture

```text
Compose network UI
  → NetworkViewModel
  → NetworkRepository / NetworkLocationRepository
  → HTTP | WebDAV | SMB | FTP protocol client
  → NetworkRequestRegistry + CredentialVault
  → NetworkDataSourceRouter
  → ProfessionalMediaSourceFactory
  → PlaybackService / MediaSession
  → Media3PlaybackEngine / ExoPlayer
```

All local, HTTP-family, RTSP, SMB, WebDAV and FTP-family media feed the same service-owned ExoPlayer. External subtitles and external audio use the same source factory/timeline. No protocol downloads a full movie as its normal playback path.

## HTTP / HTTPS

- OkHttp 5.1.0 and Media3 OkHttp DataSource
- direct progressive streaming with Media3 range requests
- Basic, bearer and custom-header credentials kept outside MediaItem/Room
- same-origin/directory header scoping
- redirect support without cross-origin authorization forwarding
- 15-second connect and 30-second read/write timeouts
- OkHttp connection recovery plus Media3 bounded load retry
- Android system trust and hostname verification for TLS
- structured 401/403/404/416/429/5xx and timeout/TLS error mapping

Cleartext HTTP is available only through a warning-and-acknowledgement direct URL flow. Saved server creation excludes HTTP.

## HLS

Media3 HLS handles master/media playlists on the authoritative player. Deterministic fixtures provide two AVC variants. The quality menu is backed by real Media3 track groups and `TrackSelectionOverride`; Auto removes the override. VOD and a wall-clock-driven rolling live playlist are tested; certification seeks to the start of its DVR window and requires Go Live to reduce the measured Media3 live offset. HTTP authentication/header scoping applies to manifest children.

## DASH

Media3 DASH handles MPDs on the same player. The deterministic static fixture contains two AVC representations and AAC audio, and the test requires both video qualities to be visible. DASH live behavior remains stream-dependent and is not generalized beyond Media3's production integration.

## RTSP

The Media3 RTSP module is included and direct RTSP uses `RtspMediaSource.Factory` on the single player. RTP-over-RTSP/TCP is explicitly selected for NAT and TCP-only server compatibility, with a 15-second inactivity/end-of-stream timeout. The isolated CI server publishes a real H.264/AAC stream over TCP and the test requires Media3 READY plus controller-visible `isPlaying`. UDP-only servers are not claimed. URL userinfo is rejected, so authenticated RTSP is not claimed when doing so would place a password in the MediaItem URI.

## SMB

SMBJ 0.14.0 provides real SMB 2.0.2/2.1/3.0/3.0.2/3.1.1 support; SMB1 is not offered. Signing is enabled. Guest, anonymous and username/password/domain authentication are supported. `SmbProtocolClient` browses directories and `SmbDataSource` performs bounded random reads with 64-bit positions and at most three reconnects. The CI lane verifies authentication, Unicode listing, wrong-password failure, exact ranged bytes, a >3 GB sparse-file offset and actual service-owned Media3 playback.

## WebDAV

WebDAV locations are HTTPS-only. OkHttp sends authenticated PROPFIND requests with Depth 0/1. Responses are capped at 4 MiB and parsed by a DTD-disabled pull parser. Same-origin/root confinement prevents malicious href traversal. Entries sort folders first and play through the normal HTTPS OkHttp Media3 path, including server-dependent range support.

## FTP / FTPS

Apache Commons Net 3.13.0 provides directory listing, login, passive/active selection, binary transfers and restart-offset streaming. `FtpDataSource` checks remote size across reconnects and retries at most three times. Plain FTP credentials require explicit acknowledgement.

Explicit FTPS enables endpoint checking, `PBSZ 0` and protected data channel `PROT P`. Its implementation is present but status remains **PARTIAL** because a real automated TLS FTP connection is not in the final candidate matrix. Implicit FTPS and SFTP are not claimed.

## Credentials

`CredentialVault` encrypts a JSON credential payload with AES/GCM and a non-exportable Android Keystore AES key. Room stores an opaque reference and non-secret username hint only. Invalid ciphertext is removed and produces an authentication-required recovery path. Repository deletion is reference-counted so a shared vault record remains until its last location is removed.

## Database

Room advances from v5 to v6 with `network_locations`. `MIGRATION_5_6` is explicit and non-destructive. Migration tests preserve history, favourites, playlists, library/index/preferences, subtitles, external audio and decoder state before creating a secret-free network row.

## Saved locations

The Network center lists saved sources and recent network history. Users can add, edit, test, rename, browse or play a source; remove configuration without deleting history; and forget credentials independently. Connection checks occur on user action rather than aggressive background polling.

## Network browser

SMB, WebDAV, FTP and FTPS clients return typed entries. The UI provides breadcrumbs, Up, refresh, search and folders-first case-insensitive ordering. Unicode names and URI encoding are preserved. Parser/listing reads are bounded and run on I/O dispatchers.

## Playback and persistence

Network `AppMedia` uses stable source/path identity, so refreshed signed tokens do not fragment resume history. Network items use existing history, Continue Watching, playlists and queue mechanics. M3U expansion supports bounded HTTP/WebDAV playlists containing mixed HTTP/HTTPS/RTSP media; it is non-recursive. Background playback, media-session controls, PiP, repeat/shuffle and decoder reprepare remain on the existing session.

## Reconnect and retry

- Media3 load policy: five minimum retries
- OkHttp: connection-failure retry with bounded timeouts
- SMB: three reconnects, 250/500/1,000 ms backoff, reopen at the 64-bit current offset
- FTP/FTPS: three reconnects, 250/500/1,000 ms backoff, REST at current offset, reject changed remote size
- connection and playback failures become structured UI state rather than infinite spinners

## Buffering

Step 7 uses Media3 buffering/load control rather than a fake buffer-size slider or full-file cache. Diagnostics distinguish initial loading, buffering and reconnecting and expose buffered duration, buffered percentage and estimated bandwidth when Media3 provides it. No whole-media temporary copy or network cache is introduced.

## Network subtitles and audio

HTTP/HTTPS external subtitle and audio URLs attach through existing Step-4/5 repositories and the shared MediaSource factory. Remote subtitles are capped at 16 MiB. Secret-bearing URLs are rejected for persistent sidecars; authentication belongs in scoped request context. Track selection and delay/DSP remain in the existing timeline.

## Decoder coexistence

Network AVC reaches the same `ProfessionalRenderersFactory` and `ProfessionalMediaCodecSelector` used by Step 6. Decoder reprepare snapshots the same URI, queue, index, position, track overrides, play intent, repeat/shuffle and speed/pitch. No local conversion or second decoder player is introduced.

## Security

See `STEP_7_PROTOCOL_SECURITY.md`. Automated checks cover URL/query/header redaction, credential encryption/lifecycle, credential-URL rejection, cross-origin redirect isolation, FTP warnings, DTD/XXE rejection, WebDAV origin/root confinement and remote-path traversal. TLS verification is never disabled in production.

## Tests and CI

See `STEP_7_TEST_MATRIX.md`. The final gate consists of:

- debug build and JVM/unit tests
- release compilation
- Android lint
- complete API-35 instrumentation
- API-26 thumbnail regression
- API-28 thumbnail regression
- isolated API-35 Samba/FTP/RTSP protocol certification

The implementation head passed run #242 (`34239231820`), the documentation-complete head passed run #243 (`34241083327`), and exact merged `main` passed run #244 (`34242218019`). Each run passed build/JVM tests, release, lint, API-35, API-26, API-28 and the real Samba/FTP/RTSP lane. Run #242 archived API-35 59/59, API-26 2/2, API-28 2/2 and protocol 1/1. Exact job IDs, artifact IDs and digests are recorded in `STEP_7_BRANCH_CERTIFICATION.md` and `STEP_7_FINAL_CERTIFICATION.md`.

## Dependencies

Runtime additions are OkHttp 5.1.0, Media3 DataSource OkHttp 1.11.0, SMBJ 0.14.0 and Apache Commons Net 3.13.0. MockWebServer 5.1.0 is test-only. Samba, pyftpdlib, MediaMTX and FFmpeg are CI-only servers/tools and are not packaged in the APK. See `DEPENDENCIES.md`.

## Known software limitations

- FTPS is implemented but not fully certified; status is PARTIAL.
- SFTP is NOT IMPLEMENTED.
- Authenticated RTSP is not claimed because credentials are forbidden in persisted/playback URLs.
- WebDAV is HTTPS-only and has no insecure-certificate bypass.
- HTTP and FTP are intentionally labeled insecure and gated by explicit acknowledgement.
- SMB transport encryption is server/share dependent; signing is always enabled.
- Network M3U expansion is currently limited to HTTP/WebDAV M3U files.
- Network external subtitle/audio persistence accepts secret-free HTTP/HTTPS URLs only.
- There is no whole-file network cache or fake cache-size control.
- Local network discovery, cloud providers and casting are outside Step 7.

## Protocol matrix

| Protocol | Browse | Play | Seek | Auth | Security | Status |
|---|---:|---:|---:|---:|---|---|
| HTTP | N/A | Yes | server-dependent ranges | scoped headers | cleartext warning | PASS |
| HTTPS | N/A | Yes | server-dependent ranges | Basic/bearer/custom | system TLS | PASS |
| HLS | N/A | Yes | stream-dependent | scoped HTTP headers | transport-dependent | PASS |
| DASH | N/A | Yes | stream-dependent | scoped HTTP headers | transport-dependent | PASS |
| RTSP | N/A | Yes | server-dependent | unauthenticated in Step 7 | RTP-over-RTSP/TCP | PASS |
| SMB2/3 | Yes | Yes | Yes | guest/anonymous/domain user | signing; SMB3 encryption server-dependent | PASS |
| WebDAV HTTPS | Yes | Yes | server-dependent ranges | Basic/bearer/custom | system TLS + secure XML | PASS |
| FTP | Yes | Yes | REST/server-dependent | anonymous/user | insecure warning | PASS |
| FTPS explicit | Yes | Yes | REST/server-dependent | user | verified TLS + private data channel | PARTIAL — not server-certified |
| SFTP | No | No | No | No | N/A | NOT IMPLEMENTED |

## Not verified — deferred to Step 10

- physical NAS/router interoperability
- physical device/OEM network and decoder matrices
- cellular/Wi-Fi/VPN/captive-portal handoff
- 3 GB+ real remote media and 4K/HDR/high-bitrate playback
- long-play socket stability, battery and thermal behavior
- weak-network recovery on representative hardware

Step 8, Step 9 and Step 10 features are not started by this work.

# STEP 7: PASS
