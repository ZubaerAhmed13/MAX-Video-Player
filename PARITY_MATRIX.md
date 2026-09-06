# MAX Video Player — Parity Matrix

Status vocabulary: `PASS`, `PARTIAL`, `FAIL`, `NOT VERIFIED`, `NOT IMPLEMENTED`, `NOT APPLICABLE`.

| Area | Capability | Step 1 status | Evidence / note |
|---|---|---|---|
| Player | Local playback architecture | PARTIAL | Real Media3 path implemented; physical playback certification pending CI/device tests |
| Player | Seeking | PARTIAL | Connected UI/controller path implemented; runtime media test pending |
| Player | Pause/resume | PARTIAL | Real controller commands implemented; runtime media test pending |
| Player | Background playback | PARTIAL | MediaSessionService ownership implemented; physical runtime verification pending |
| Player | Notification controls | PARTIAL | Media3 session/service integration implemented; runtime notification verification pending |
| Player | Lock-screen controls | PARTIAL | MediaSession integration; physical runtime verification pending |
| Player | PiP | PARTIAL | Real PiP entry preserving service player; device verification pending |
| Player | Rotation persistence | PARTIAL | Activity allowed to recreate while player remains service-owned; instrumentation smoke test added |
| Decoder | Auto | PARTIAL | Media3 automatic decoder selection implemented; codec-specific runtime matrix pending |
| Decoder | Hardware | PARTIAL | MediaCodec/Media3 path implemented; device-specific verification pending |
| Decoder | Enhanced Hardware | PARTIAL | Shares hardware internals in Step 1; truthfully labeled |
| Decoder | Software | NOT IMPLEMENTED | Planned Step 6; no placebo implementation |
| Media | MediaStore | PARTIAL | Query/UI/runtime permission path implemented; device verification pending |
| Media | SAF | PARTIAL | OpenDocument/persistable grant/metadata path implemented; picker runtime verification pending |
| Media | Large files | NOT VERIFIED | Long-safe/reference architecture present; 3 GB physical asset not yet certified |
| Media | 4K | NOT VERIFIED | Capability-aware codec profiling exists; no physical 3840×2160 certification yet |
| Audio | Multi-track foundation | PARTIAL | Media3 track architecture available; dedicated UI comes later |
| Audio | Audio focus | PARTIAL | Media3 audio focus enabled; competing-app physical test pending |
| Audio | Bluetooth controls | PARTIAL | MediaSession handles media buttons; hardware test pending |
| Audio | Noisy-route handling | PARTIAL | `setHandleAudioBecomingNoisy(true)` implemented; physical route test pending |
| Subtitles | Embedded subtitle foundation | PARTIAL | Media3 can expose embedded text tracks; professional UI/styling not Step 1 |
| Subtitles | External subtitles | NOT IMPLEMENTED | Later subtitle step |
| Subtitles | Advanced styling | NOT IMPLEMENTED | Later subtitle step |
| Library | Media scan | PARTIAL | MediaStore query implemented; runtime verification pending |
| Library | Resume | PARTIAL | Room + resume policy/dialog implemented; runtime media verification pending |
| Library | History | PARTIAL | Persistence implemented; full history UI belongs to Step 2 |
| Library | Playlists | NOT IMPLEMENTED | Queue model exists; playlist UI later |
| Network | HTTPS | PARTIAL | Real Media3 URI path; runtime stream test pending |
| Network | HLS | PARTIAL | Media3 HLS module included; runtime stream test pending |
| Network | DASH | PARTIAL | Media3 DASH module included; runtime stream test pending |
| Network | RTSP | PARTIAL | Media3 RTSP module included; runtime stream test pending |
| Network | SMB | NOT IMPLEMENTED | Step 7 |
| Network | Cloud | NOT IMPLEMENTED | Step 8 |
| Network | Cast | NOT IMPLEMENTED | Step 8 |
