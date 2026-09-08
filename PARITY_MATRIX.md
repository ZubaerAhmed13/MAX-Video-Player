# MAX Video Player — Parity Matrix through Step 7

Status vocabulary: `PASS`, `BRANCH PASS` (exact implementation head certified; documentation/main gates pending), `PENDING`, `PARTIAL`, `FAIL`, `NOT VERIFIED`, `NOT IMPLEMENTED`, `NOT APPLICABLE`.

A `PASS` requires implementation, a real product flow, error handling and automated evidence where feasible. Physical-device requirements are never inferred from emulator/software evidence.

## Step-7 professional network playback and sources

| Area | Capability | Status | Evidence / boundary |
|---|---|---|---|
| Architecture | One service-owned player for local and network media | BRANCH PASS | `NetworkDataSourceRouter -> ProfessionalMediaSourceFactory -> PlaybackService`; no protocol player/full download |
| HTTP | Direct progressive playback | BRANCH PASS | Real API-35 Media3/OkHttp production-path fixture |
| HTTP | Byte ranges and seek | BRANCH PASS | Recorded Media3 `Range` request and 206 response; capability remains server-dependent |
| HTTP | Basic/bearer/custom headers | BRANCH PASS | Process-local request registry; exact origin/directory scope |
| HTTP | Redirects and loop bound | BRANCH PASS | Deterministic redirect success and bounded loop failure |
| HTTP | Cross-host auth isolation | BRANCH PASS | Destination test server receives no Authorization |
| HTTPS | TLS verification | BRANCH PASS | Android system trust/hostname verification; no trust-all code |
| Cleartext | HTTP warning/acknowledgement | BRANCH PASS | Compose test requires explicit acknowledgement; saved-source editor excludes HTTP |
| HLS | VOD/master/variants | BRANCH PASS | Real Media3 playback of two synthetic variants |
| HLS | Manual quality and Auto | BRANCH PASS | Real Media3 track override and removal |
| HLS | Live / Go Live | BRANCH PASS | Rolling local playlist, advancing media sequence, DVR-window seek and measured live-offset reduction |
| DASH | MPD VOD / adaptive representations / audio | BRANCH PASS | Static local MPD with two AVC representations and AAC adaptation set |
| RTSP | Production playback | BRANCH PASS | Media3 RTP-over-RTSP/TCP against isolated MediaMTX H.264/AAC server; run #242 |
| RTSP | Credential safety | BRANCH PASS | URL userinfo rejected; authenticated RTSP not falsely claimed |
| SMB | SMB2/SMB3 browse/auth/play | BRANCH PASS | SMBJ against isolated authenticated Samba; SMB1 excluded; run #242 |
| SMB | Random seek / >3 GB offsets | BRANCH PASS | exact ranged bytes and sparse-file read at 3,221,225,472; run #242 |
| SMB | Signing/encryption truth | BRANCH PASS | signing enabled; SMB3 server/share encryption honored, not claimed for SMB2 |
| WebDAV | HTTPS PROPFIND/browse/auth | BRANCH PASS | deterministic authenticated PROPFIND, Depth, Unicode and folders-first mapping |
| WebDAV | Secure XML/root confinement | BRANCH PASS | DTD/XXE and off-origin/off-root response rejection |
| WebDAV | Playback/range | BRANCH PASS | resolved HTTPS entries use the shared Media3 OkHttp range path |
| FTP | Browse/login/binary playback | BRANCH PASS | Commons Net against isolated authenticated pyftpdlib server; run #242 |
| FTP | REST seek / >3 GB offsets | BRANCH PASS | exact ranged bytes and sparse-file read at 3,221,225,472; run #242 |
| FTP | Cleartext warning | BRANCH PASS | domain validation and UI test require explicit acknowledgement |
| FTPS | Explicit TLS implementation | PARTIAL | endpoint checking + `PBSZ 0` + `PROT P`; no real TLS FTP server test yet |
| SFTP | SSH file transfer | NOT IMPLEMENTED | not aliased to FTP/FTPS |
| Credentials | Keystore-backed encrypted vault | BRANCH PASS | AES/GCM ciphertext lifecycle and invalidation recovery test |
| Credentials | No plaintext Room/media/log secret | BRANCH PASS | opaque Room ref, userinfo rejection, sanitized diagnostics/header tests |
| Saved sources | Add/edit/test/rename/remove/forget | BRANCH PASS | Network center and reference-counted repository flows |
| Browser | Breadcrumbs/Up/refresh/search/sort/Unicode | BRANCH PASS | Compose surface + isolated Unicode listings |
| History | Stable network identity/resume | BRANCH PASS | signed tokens removed from canonical ID; sanitized URI persistence |
| Playlist | Bounded HTTP/WebDAV M3U mixed queue | BRANCH PASS | 2 MiB/1,000 item non-recursive parser |
| Sidecars | HTTP/HTTPS subtitle and external audio | BRANCH PASS | existing Step-4/5 repositories and shared MediaSource timeline; secret URLs rejected |
| Diagnostics | Loading/buffering/reconnecting/error | BRANCH PASS | shared player monitor, connectivity state, buffer/bandwidth/retry/redacted URI |
| Database | Room v5→v6 | BRANCH PASS | explicit migration preserves Steps 1–6 and adds secret-free network locations |
| CI | Isolated network protocol job | BRANCH PASS | Samba/pyftpdlib/MediaMTX, run #242, no public media server or personal NAS |
| Physical network/device matrix | NAS/router/WAN/OEM/large remote media | NOT VERIFIED | Deferred to Step 10 |

## Step-6 professional decoder engine

| Area | Capability | Status | Evidence / boundary |
|---|---|---|---|
| Architecture | One service-owned ExoPlayer / MediaSession | PASS | Decoder policy extends existing `PlaybackService -> MediaSession -> Media3PlaybackEngine`; no second player |
| Architecture | Step-5 DSP coexistence | PASS | `ProfessionalRenderersFactory` keeps `MaxAudioProcessor` in the production `DefaultAudioSink` |
| Modes | Auto | PASS | Distinct policy; hardware-first candidate ordering with deliberate cross-backend fallback permitted |
| Modes | Hardware | PASS | Strict preferred hardware candidate only; software and additional hardware candidates are excluded |
| Modes | Enhanced Hardware | PASS | Hardware-only multi-candidate chain; software candidates excluded |
| Modes | Software | PASS | Software-only platform MediaCodec candidates; truthful unavailable state when device exposes none |
| Routing | Requested vs effective state | PASS | UI/repository separate requested policy from actual initialized decoder/backend |
| Routing | Actual decoder identity | PASS | Media3 analytics records initialized/released decoder name rather than label-only state |
| Routing | Session blacklist | PASS | Failed codec name is rejected for current session before bounded retry |
| Routing | Fallback termination | PASS | Retry occurs only when an unrejected candidate remains; fallback history bounded to 16 |
| Routing | Hardware no software leak | PASS | Unit policy plus production API-35 capability-aware assertions |
| Routing | Enhanced Hardware no software leak | PASS | Unit policy plus production API-35 capability-aware assertion |
| Routing | Software no hardware leak | PASS | Unit policy plus production API-35 capability-aware assertion |
| Compatibility | MIME-aware discovery | PASS | Media3 selector queried per actual MIME/secure/tunneling request |
| Compatibility | Profile/level handling | PASS | Policy tests plus Media3 format-support ordering before initialization |
| Compatibility | Resolution/frame-rate handling | PASS | Policy tests and device `VideoCapabilities` 720p/1080p/1440p/2160p 30/60 probes |
| Compatibility | Secure decoder requirement | PASS | Secure requirement flows through Media3 selector; policy tests reject non-secure candidate |
| Classification | API-29+ hardware/software/vendor truth | PASS | Android/Media3 platform flags are authoritative |
| Classification | Legacy ambiguity | PASS | Known software families recognized; ambiguous codec names remain `UNKNOWN`, not guessed hardware |
| Switching | Queue/index preservation | PASS | Same-player reprepare snapshots/restores queue and current index |
| Switching | Playback position preservation | PASS | API-35 production test seeks before mode switch and requires non-zero position retention |
| Switching | Play/pause state preservation | PASS | `playWhenReady` snapshotted/restored |
| Switching | Repeat/shuffle preservation | PASS | Current repeat and shuffle snapshotted/restored |
| Switching | Speed/pitch preservation | PASS | Media3 playback parameters snapshotted/restored |
| Switching | Track-selection preservation | PASS | Track-selection parameters snapshotted/restored, preserving Step-4/5 selections |
| Diagnostics | Decoder mode/backend/name | PASS | Requested/effective mode, backend and actual decoder surfaced |
| Diagnostics | Format information | PASS | MIME/codec string/resolution/frame rate captured when Media3 exposes them |
| Diagnostics | Init duration / dropped frames | PASS | Media3 analytics callbacks |
| Diagnostics | Failure/fallback history | PASS | Structured failure state and bounded fallback history |
| Device capability | Cached decoder inventory | PASS | `DeviceCapabilityProvider.collectDecoderProfile()` immutable process cache |
| Device capability | Off-main scanning | PASS | Initial and manual refresh run on `Dispatchers.Default` |
| Device capability | Per-MIME details | PASS | Profiles/levels, color formats, adaptive/secure/tunneled/low-latency when exposed, size/rate targets |
| Device capability | Advanced panel | PASS | Expandable Decoder dialog panel with real manual refresh and device-specific warning |
| Device capability | CI capability report | PASS | API-35 instrumentation writes and CI exports `decoder-capability-report-api35.txt` |
| Persistence | Global default mode | PASS | Existing preference layer |
| Persistence | Remember decoder per video | PASS | Room v5 `decoder_media_state` keyed by stable media ID |
| Persistence | Diagnostics preference | PASS | Existing preference layer |
| Database | `MIGRATION_4_5` | PASS | Explicit non-destructive migration; no destructive fallback |
| Database | Step-1–5 preservation | PASS | Migration instrumentation preserves history/library/subtitle/audio rows while adding decoder state |
| Large media | URI/reference architecture | PASS | Decoder switching reuses MediaItems/URIs; no full media copy/predecode/transcode |
| Large media | No artificial 3 GB / 1080p ceiling | PASS | Long-safe media architecture retained; no Step-6 size/resolution cap introduced |
| Color/HDR | No intentional recolor/transcode | PASS | Video remains on Media3/Android decoder-render path; no Step-6 pixel transform pipeline |
| DRM/security | No secure-decoder bypass | PASS | Secure requirements remain in Media3/Android discovery; no private bypass |
| Dependencies | No new proprietary/native decoder | PASS | No FFmpeg/native decoder/OEM binary/new `.so`; see `STEP_6_DEPENDENCIES.md` |
| Privacy | Local decoder selection/diagnostics | PASS | No media upload/network service introduced by decoder engine |
| Physical OEM matrix | Snapdragon/Exynos/MediaTek/Tensor | NOT VERIFIED | Deferred to Step 10 |
| Physical formats | H.264/HEVC/VP9/AV1 on real devices | NOT VERIFIED | Deferred to Step 10 |
| Physical performance | 4K60/high bitrate/HDR/10-bit | NOT VERIFIED | Deferred to Step 10 |
| Physical endurance | thermal/battery/long play | NOT VERIFIED | Deferred to Step 10 |

## Step-5 professional audio engine — preserved through Step 7

| Area | Capability | Status | Evidence / boundary |
|---|---|---|---|
| Tracks | Embedded discovery | PASS | Media3 `currentTracks` audio groups mapped to professional audio state; API-35 fixture has ≥2 embedded audio tracks |
| Tracks | Auto/manual/preferred-language selection | PASS | Existing Media3 track-selection path retained through decoder switching |
| External audio | SAF import / persistence / multiple associations / recovery | PASS | Room v4 state preserved through Room v5 migration |
| External audio | Actual Media3 selection | PASS | Existing API-35 certification retained |
| DSP | Production PCM processing | PASS | App-owned `MaxAudioProcessor` remains installed in production `DefaultAudioSink` |
| DSP | PCM 16-bit / float / unsupported-format truth | PASS | Existing Step-5 signal tests retained |
| EQ | 10-band / live / presets / custom / Nyquist safety | PASS | Existing deterministic Step-5 DSP retained |
| Gain | Preamp / digital boost / limiter | PASS | Existing exact-DSP tests retained |
| Channels | Stereo/mono/left/right/balance | PASS | Existing deterministic channel path retained |
| Sync | Positive/negative/zero/per-media/route compensation | PASS | Existing Step-5 timing path retained |
| Pitch | Independent pitch + speed separation | PASS | Decoder switch restores Media3 playback parameters |
| Audio-only | Disable/restore video without new player | PASS | Existing track-selection architecture retained |
| Background/PiP | Continue/pause/PiP behavior | PASS | Existing service-owned lifecycle tests retained |
| Android audio | Audio focus / becoming noisy / route classification | PASS | Existing Media3/audio-route handling retained |
| Performance | Realtime callback isolation | PASS | Decoder inventory scans are outside `queueInput` and off-main |
| Numerical safety | NaN/Infinity/filter/DC/output bounds | PASS | Existing Step-5 exact-DSP certification retained |
| Sample rates | 44.1/48/96 kHz | PASS | Existing Step-5 exact-DSP certification retained |

## Step-4 professional subtitle engine — preserved through Step 7

| Area | Capability | Status | Evidence / boundary |
|---|---|---|---|
| Subtitle tracks | Embedded / Off / Auto / manual | PASS | Media3 text-track engine retained; decoder switch restores track-selection parameters |
| External subtitles | SAF load / multiple associations / relink / recovery | PASS | Room v3 state preserved through Room v5 migration |
| Formats | SRT / WebVTT / SSA / ASS / TTML | PASS | Existing parser instrumentation retained |
| Encoding | UTF-8 / UTF-16 LE/BE / Windows-1252 | PASS | Existing normalization/persistence tests retained |
| Sidecars | Same-folder filename/language matching | PASS | Existing Step-4 matcher retained |
| Sync | Positive / negative / per-media subtitle delay | PASS | Separate from audio/decoder state |
| Appearance | Size/colour/background/edge/bottom margin | PASS | Existing Media3 SubtitleView path retained |
| Android system caption style toggle | NOT IMPLEMENTED | Still intentionally not exposed as a fake control |

## Step-3 player experience — preserved through Step 7

| Area | Capability | Status | Evidence / boundary |
|---|---|---|---|
| Player UI | Controls / auto-hide / buffering / error recovery | PASS | Full API-35 regression suite retained |
| Gestures | Seek/double-tap/brightness/system volume | PASS | Real PlayerScreen gesture instrumentation retained and updated for Step-6 dependencies |
| Gestures | Pinch zoom / rendered-aware pan | PASS | Existing Step-3 behavior retained |
| Display | Resize/aspect/rotation/orientation/fullscreen | PASS | Existing surface-transform path retained |
| Player | Screen lock | PASS | Existing touch suppression/unlock path retained |
| Player | Playback speed | PASS | 0.25×–4× service-owned playback retained and preserved across decoder switches |
| Player | Previous/Next/Repeat/Shuffle | PASS | Existing MediaSession queue retained |
| PiP | Same-session continuity | PASS | Existing PiP certification retained |
| Seek preview | Frame thumbnail preview | PARTIAL | Architecture/foundation only; Step 6 does not fabricate thumbnails |

## Step-2 library — preserved through Step 7

| Area | Capability | Status | Evidence / boundary |
|---|---|---|---|
| Library | Videos/Folders/Continue/Recent/History | PASS | Existing Step-2 library retained |
| Library | Favourites/Playlists/queues | PASS | Room relationships retained through v5 migration |
| Library | Search/sort/filter | PASS | Existing deterministic derivation retained |
| Storage | MediaStore/SAF/persisted permissions | PASS | Existing source architecture retained |
| Storage | Missing source/relink | PASS | Existing media relink remains separate from subtitle/audio/decoder state |
| Files | Rename/delete | PASS | Existing provider-aware flows retained |
| Media | Thumbnails | PASS | API-26/API-28 regression lanes retained |

## Step-1 foundations — preserved through Step 7

| Area | Capability | Status | Evidence / boundary |
|---|---|---|---|
| Player | Service-owned playback architecture | PASS | Single `PlaybackService -> MediaSession -> ExoPlayer` ownership retained |
| Player | Local playback / play / pause / seek | PASS | Existing real Media3 integration retained |
| Player | Resume/history | PASS | Room history retained through v5 migration |
| Player | Background/session foundation | PASS | Extended, not replaced, by Steps 3–6 |
| Media | Large-file-safe application types | PASS | Long-safe media/timing values retained |
| Device | Capability foundation | PASS | Extended into Step-6 decoder inventory rather than replaced |

## Step-7 certification gate

The Step-7 branch requires the exact documentation-complete head to pass:

- debug build
- JVM/unit tests
- release compilation
- lint
- complete API-35 instrumentation including Steps 1–7 integration and decoder capability report
- API-26 thumbnail regression
- API-28 thumbnail regression
- isolated API-35 SMB/FTP/RTSP protocol certification

After the branch is green, PR #12 must be merged with an expected-head lock and the exact `main` merge head must pass the same configured workflow before the overall result can be declared complete.

See:

- `STEP_7_COMPLETION_REPORT.md`
- `STEP_7_TEST_MATRIX.md`
- `STEP_7_PROTOCOL_SECURITY.md`

## Physical certification deferred to Step 10

The following remain **NOT VERIFIED — DEFERRED TO STEP 10**:

- real 3 GB+/5 GB+/10 GB playback under representative devices
- physical 4K/HDR colour/performance
- Snapdragon/Exynos/MediaTek/Tensor codec behavior
- Samsung/Xiaomi/Oppo/OnePlus codec quirks
- physical H.264/HEVC/VP9/AV1 matrices
- OEM/provider/SAF variations
- SD-card and USB/OTG device behavior
- actual Bluetooth latency and route switching quality
- USB DAC / HDMI receiver behavior
- manufacturer-specific background restrictions
- cutouts/foldables/external displays
- battery, thermal and long-run physical testing
- broad phone/tablet matrix

## Overall result

Steps 1–6 software/emulator functionality remains `PASS` as previously certified.

**Step 7 implementation is complete in its declared software/emulator scope, but the overall Step-7 result remains certification pending until the exact documentation-complete branch head and exact resulting `main` head pass every required CI job. FTPS remains PARTIAL and physical network/device certification remains deferred to Step 10.**
