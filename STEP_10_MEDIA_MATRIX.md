# Step 10 Media Certification Matrix

Use generated, redistribution-safe, or openly licensed media only. Do not commit commercial/copyrighted movie files. Record fixture URI/path outside the repository when necessary plus SHA-256, duration, container, codec, resolution, frame rate, bit depth/HDR metadata and expected result.

## Physical media coverage

| Dimension | Required cases | Current physical result |
| --- | --- | --- |
| Resolution | 480p, 720p, 1080p, 1440p/2K where appropriate, 2160p/4K | NOT VERIFIED |
| Frame rate | 23.976/24, 25, 30, 50, 60 fps where hardware supports | NOT VERIFIED |
| Video codecs | H.264/AVC, HEVC/H.265, VP9, AV1 when supported | NOT VERIFIED |
| Bit depth / HDR | SDR 8-bit, HEVC 10-bit, HDR10, HLG when supported | NOT VERIFIED |
| Containers | authoritative advertised set, including MP4/MKV/WebM/MPEG-TS where supported | NOT VERIFIED |
| Audio | AAC, MP3, Opus, FLAC, multichannel where supported | NOT VERIFIED |
| Large local source | at least one real source >3 GB | NOT VERIFIED |
| 4K high bitrate | 4K seek/decoder/subtitle/rotation/fullscreen | NOT VERIFIED |
| 4K60 | only on device that advertises relevant capability | NOT VERIFIED |

A device lacking a codec/profile/4K60/HDR capability is recorded as `NOT SUPPORTED BY DEVICE`, not as an application failure and not as a fabricated PASS.

## Large-file procedure

For a real >3 GB source test import/open, metadata, playback, long and near-end seeks, repeated seeks, pause/resume, Activity recreation, process restart, background playback, queue transitions, subtitle attachment, decoder switching where applicable, restart/resume. Capture memory before/after and verify there is no whole-file RAM load, `Int` truncation, unnecessary local copy, or memory growth proportional to source size.
