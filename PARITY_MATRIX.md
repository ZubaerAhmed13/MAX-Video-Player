# MAX Video Player — Parity Matrix

Status vocabulary: `PASS`, `PARTIAL`, `FAIL`, `NOT VERIFIED`, `NOT IMPLEMENTED`, `NOT APPLICABLE`.

Step-1 automated evidence is anchored to code SHA `ebf58eb0a95ac6e59429417d990caac212ace58e` and Android CI run `34032102391`. `PASS` below means the Step-1 implementation/automated evidence is sufficient for that row; it does not silently imply hardware certification.

| Area | Capability | Step 1 status | Evidence / note |
|---|---|---|---|
| Player | Local playback architecture | PASS | API-35 real H.264 media test passes through PlaybackConnection -> MediaController -> MediaSessionService -> ExoPlayer |
| Player | Seeking | PASS | Real service/controller media test verifies seek to requested region |
| Player | Pause/resume transport | PASS | Real service/controller test verifies play and pause; resume-policy UX remains separately modeled |
| Player | Background playback | PARTIAL | MediaSessionService owns player; physical/background lifecycle continuation still needs representative-device verification |
| Player | Notification controls | PARTIAL | Media3 session/service integration implemented; runtime notification interaction not independently certified |
| Player | Lock-screen controls | PARTIAL | MediaSession integration implemented; physical lock-screen interaction not independently certified |
| Player | PiP | PARTIAL | Real PiP entry preserving service-owned player implemented; device-specific verification pending |
| Player | Rotation persistence | PASS | API-35 instrumentation recreates MainActivity without crashing the foundation UI; player ownership remains outside Activity |
| Decoder | Auto | PARTIAL | Media3 automatic selection works for certified H.264 emulator fixture; broader codec/device matrix pending |
| Decoder | Hardware | PARTIAL | MediaCodec/Media3 path implemented; manufacturer/device-specific hardware matrix pending |
| Decoder | Enhanced Hardware | PARTIAL | Shares hardware internals in Step 1; intentionally not misrepresented as an independent engine |
| Decoder | Software | NOT IMPLEMENTED | Planned later step; no placebo implementation |
| Media | MediaStore | PARTIAL | Query/UI/runtime permission path implemented; representative-device/provider verification pending |
| Media | SAF | PARTIAL | OpenDocument/persistable grant/metadata path implemented; representative picker/provider verification pending |
| Media | Large files | NOT VERIFIED | Long-safe/reference architecture and >Int.MAX_VALUE persistence evidence exist; real 3 GB+ media not physically certified |
| Media | 4K | NOT VERIFIED | Capability-aware profiling exists; no physical 3840×2160 playback certification |
| Audio | Multi-track foundation | PARTIAL | Media3 track architecture available; dedicated track UX belongs to later work |
| Audio | Audio focus | PARTIAL | Real foreground-eligible Android-15 playback passes with Media3 audio focus enabled; competing-app/focus-loss matrix not physically certified |
| Audio | Bluetooth controls | PARTIAL | MediaSession/media-button foundation implemented; physical Bluetooth hardware test pending |
| Audio | Noisy-route handling | PARTIAL | `setHandleAudioBecomingNoisy(true)` implemented; physical wired/Bluetooth route test pending |
| Subtitles | Embedded subtitle foundation | PARTIAL | Media3 can expose embedded text tracks; professional UI/styling not Step 1 |
| Subtitles | External subtitles | NOT IMPLEMENTED | Later subtitle step |
| Subtitles | Advanced styling | NOT IMPLEMENTED | Later subtitle step |
| Library | Media scan | PARTIAL | MediaStore query implemented; representative-device scanning verification pending |
| Library | Resume | PARTIAL | Room + resume policy/dialog implemented; complete end-to-end resume UX certification remains later |
| Library | History persistence | PASS | Room API-35 instrumentation verifies insert/update/read including a 3.5 GB-sized Long value |
| Library | Playlists | NOT IMPLEMENTED | Queue model exists; playlist management UI later |
| Device | Runtime capability mapping | PASS | API-35 instrumentation verifies capability mapping without inventing support |
| Network | HTTPS | PARTIAL | Real Media3 URI path implemented; live network stream matrix pending |
| Network | HLS | PARTIAL | Media3 HLS module included; runtime stream test pending |
| Network | DASH | PARTIAL | Media3 DASH module included; runtime stream test pending |
| Network | RTSP | PARTIAL | Media3 RTSP module included; runtime stream test pending |
| Network | SMB | NOT IMPLEMENTED | Later step |
| Network | Cloud | NOT IMPLEMENTED | Later step |
| Network | Cast | NOT IMPLEMENTED | Later step |

Physical 3 GB+, 4K/HDR, Bluetooth/headset-route, manufacturer-specific codec behavior, and broad multi-device compatibility remain explicit certification work; none is converted to PASS by inference.
