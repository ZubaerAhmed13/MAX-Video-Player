# Step 7 Test Matrix — Professional Network Playback

Status terms: `PASS`, `PARTIAL`, `PENDING`, `NOT IMPLEMENTED`, `NOT VERIFIED — DEFERRED TO STEP 10`.

This matrix separates implementation evidence from physical-network certification. The implementation head passed run #242; the exact documentation-complete branch head and exact resulting `main` head must still pass before repository status becomes PASS.

## Deterministic HTTP and security instrumentation

| Area | Automated evidence | Candidate status |
|---|---|---|
| Progressive HTTP | Real Media3 playback through `PlaybackConnection -> PlaybackService -> ExoPlayer -> OkHttp` | PASS |
| HTTP authentication | Production request registry sends Basic authorization; test server records it | PASS |
| Byte range | `OkHttpDataSource` request at offset 5 records `Range: bytes=5-` and consumes a 206 body | PASS |
| Redirect | Same-origin request follows 302 successfully | PASS |
| Cross-origin redirect | Destination server records no Authorization header | PASS |
| Redirect loop | OkHttp terminates within its bounded redirect policy | PASS |
| Stable signed URL identity | Refreshed token/signature produces the same canonical identity while diagnostics redact secrets | PASS |
| Secret URL rejection | URL userinfo is rejected before MediaItem creation | PASS |
| M3U | Relative and mixed HTTP/RTSP entries resolve non-recursively under item/size bounds | PASS |
| Credential vault | save/get/update/delete, ciphertext persistence and malformed-payload recovery | PASS |
| FTP cleartext warning | Domain validation and Compose UI require explicit acknowledgement | PASS |
| WebDAV XML | XXE/DTD rejection, Unicode, 64-bit size and 4 MiB bound | PASS |
| WebDAV root | Authenticated PROPFIND plus rejection of off-root entries | PASS |
| Path traversal | Attempts above the saved root fail | PASS |

## Adaptive streaming instrumentation

The fixtures are repository-owned two-second synthetic media served by in-process MockWebServer; no public network is used.

| Protocol | Fixture / assertion | Candidate status |
|---|---|---|
| HLS VOD | Master playlist, 160×90 and 320×180 variants, real Media3 playback | PASS |
| HLS quality | Real `TrackSelectionOverride` selects a variant; Auto removes the override | PASS |
| HLS live | Wall-clock-driven rolling playlist advances its media sequence, exposes Media3 live state/offset and proves Go Live moves from the DVR-window start toward the live edge | PASS |
| DASH VOD | Static MPD, two AVC representations and AAC adaptation set, real Media3 playback | PASS |
| DASH tracks | Media3 exposes both 90p and 180p representations | PASS |

## Isolated protocol-server lane

`network-protocol-certification` provisions its own API-35 environment:

- Samba 4.19.5 from Ubuntu 24.04, configured on a non-default test port with SMB2 minimum, SMB3 maximum and mandatory signing
- pyftpdlib 1.5.9 with a dedicated test account and passive/binary transfer
- MediaMTX 1.21.0 pinned to image digest `sha256:19fddade8d6110a3d718ac0045681fbeba344ae563a066205fe5929a87f7582f`
- FFmpeg 6.1.1 publishing continuous deterministic lavfi H.264/AAC sources to RTSP over TCP

The emulator reaches only the isolated runner host. Assertions cover:

| Protocol | Browse/auth | Random access / large offset | Production playback | Candidate status |
|---|---|---|---|---|
| SMB | authenticated listing, Unicode directory, rejected wrong password | exact byte comparison and a read at 3,221,225,472 | service-owned Media3 through `SmbDataSource` | PASS — run #242 |
| FTP | authenticated listing, Unicode directory, rejected wrong password | REST-backed exact byte comparison and a read at 3,221,225,472 | service-owned Media3 through `FtpDataSource` | PASS — run #242 |
| RTSP | open isolated server | explicitly configured Media3 RTP-over-RTSP/TCP transport | service-owned Media3 reaches READY and `isPlaying` | PASS — run #242 |

FTPS remains `PARTIAL`: explicit TLS, endpoint checking and private data-channel code are present, but a real automated TLS FTP server test is not yet part of the lane.

## Database migration

`MaxDatabaseMigrationTest.migration5To6PreservesAllPriorDataAndAddsSecretFreeNetworkLocations` starts from Room v5 and verifies preservation of:

- history/resume and `Long` media sizes
- favourites
- playlists and ordered items
- library sources/index/preferences
- subtitle attachments/media state
- external-audio attachments/media state
- decoder media state

It then creates and reads a v6 network-location row containing only configuration, opaque credential reference and username hint. No destructive migration fallback is used.

## UI and production-path coverage

| Flow | Evidence |
|---|---|
| Open HTTP stream | Warning visible; Play disabled until acknowledgement |
| Add FTP | Insecure transport warning visible |
| Progressive playback | service-owned player, real authentication, seekability and sanitized diagnostics |
| Saved locations | Room repository, edit/test/browse/remove/forget flows implemented |
| Browser | breadcrumbs, Up, refresh, search, folders-first ordering and filtered playable types |
| Adaptive quality | real Media3 video track overrides and Auto restore |
| Live | live label, offset and Go Live action derive from Media3 state |
| Diagnostics | protocol, host, connection/phase, transport, buffering, bandwidth, retries and redacted URI |

## Retained regression matrix

Every final branch and main gate retains:

- Step-1 foundation and local service-owned playback tests
- Step-2 library/index/file/thumbnail tests
- Step-3 player, gesture, display and PiP tests
- Step-4 subtitle parser/repository/rendering tests
- Step-5 audio, DSP, delay, external audio and background tests
- Step-6 decoder routing, capability and coexistence tests
- Room migration chain through v6
- API-26 thumbnail regression
- API-28 thumbnail regression
- complete API-35 instrumentation

## Exact-head gates

Implementation certification: exact SHA `4cb661b978de502b99bef864cc99a53c1540939a`, Android CI run #242 (`34239231820`) — PASS. The archived results report API-35 59/59, API-26 2/2, API-28 2/2 and real-protocol 1/1. See `STEP_7_BRANCH_CERTIFICATION.md` for job IDs, artifact IDs and digests.

Required for the documentation-complete branch head:

- `:app:assembleDebug`
- `:app:testDebugUnitTest`
- `:app:assembleRelease`
- `:app:lintDebug`
- API-35 complete `:app:connectedDebugAndroidTest`
- API-26 thumbnail regression
- API-28 thumbnail regression
- API-35 isolated network protocol certification

The exact resulting `main` head must pass the same workflow after merge.

## Physical boundary

The following remain **NOT VERIFIED — DEFERRED TO STEP 10**:

- Synology, QNAP, Windows SMB, consumer Samba and router NAS products
- real WAN redirects, captive portals, VPNs, proxies and cellular/Wi-Fi handoff
- weak Wi-Fi packet loss and long reconnect outages
- physical 3 GB+/5 GB+/10 GB network media
- physical 4K/HDR/high-bitrate/4K60 streaming
- long-play socket/resource stability
- battery and thermal behavior
- OEM background and codec variations
