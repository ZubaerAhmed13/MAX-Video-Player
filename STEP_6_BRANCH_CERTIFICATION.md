# Step 6 Branch Certification — Professional Decoder Engine

## Scope

This document records the exact pre-merge software/emulator certification evidence for Step 6. It does not certify physical Android devices and it does not begin Step 7.

## Certified implementation head

- Branch: `step-6-professional-decoder-engine`
- Exact implementation SHA: `9802fe4bf857b98d59490ff290f19e98c1a80785`
- Android CI run: `#215`
- Workflow run ID: `34207634128`
- Workflow URL: `https://github.com/ZubaerAhmed13/MAX-Video-Player/actions/runs/34207634128`
- Instrumentation artifact ID: `10048572067`
- Instrumentation artifact digest: `sha256:387310a5a2d34869912492fbe64a36b6a026311df650cd936638bc73cdb1031d`

All configured jobs for this exact implementation SHA completed successfully:

- debug build + JVM/unit tests — PASS
- release compilation — PASS
- Android lint — PASS
- full API-35 instrumentation — PASS
- API-26 thumbnail regression — PASS
- API-28 thumbnail regression — PASS

The API-35 connected suite reported **39 tests, 0 failures, 0 errors and 0 skipped**. The Step-6 production decoder integration test passed on the real service-owned path, and the capability-report test/export completed successfully.

## API-35 decoder capability evidence

The exported report is:

`app/build/reports/step6/decoder-capability-report-api35.txt`

The report describes only the tested API-35 x86_64 emulator. It must not be generalized to all Android devices.

Observed inventory:

- API: 35
- Device string: `unknown Android SDK built for x86_64`
- ABI: `x86_64`
- video decoder entries: 18
- hardware-classified decoder names: 4
- software-classified decoder names: 14
- unknown-classified decoder names: 0
- video MIME types exposed: `video/3gpp`, `video/av01`, `video/avc`, `video/hevc`, `video/mp4v-es`, `video/x-vnd.on2.vp8`, `video/x-vnd.on2.vp9`

Hardware-classified emulator decoders:

- `c2.goldfish.h264.decoder`
- `c2.goldfish.hevc.decoder`
- `c2.goldfish.vp8.decoder`
- `c2.goldfish.vp9.decoder`

Software-classified emulator decoders:

- `c2.android.h263.decoder`
- `OMX.google.h263.decoder`
- `c2.android.av1-dav1d.decoder`
- `c2.android.av1.decoder`
- `c2.android.avc.decoder`
- `OMX.google.h264.decoder`
- `c2.android.hevc.decoder`
- `OMX.google.hevc.decoder`
- `c2.android.mpeg4.decoder`
- `OMX.google.mpeg4.decoder`
- `c2.android.vp8.decoder`
- `OMX.google.vp8.decoder`
- `c2.android.vp9.decoder`
- `OMX.google.vp9.decoder`

Selected emulator-only size/rate observations from the exported Android capability data:

- Goldfish H.264 hardware decoder reports 720p/1080p/1440p/2160p support at both 30 and 60 fps.
- Goldfish HEVC hardware decoder reports 720p/1080p/1440p/2160p support at both 30 and 60 fps.
- Goldfish VP8 hardware decoder reports through 1440p60, while 2160p30/60 is reported unsupported.
- Goldfish VP9 hardware decoder reports 2160p30 supported and 2160p60 unsupported.
- The emulator exposes software AV1 decoders; their reported probes include 720p60 and 1080p30, while 1080p60 and higher tested targets are reported unsupported.

These are Android API capability reports for this emulator, not physical playback-performance certification.

## Regression result

Step 6 preserved the required prior-step software/emulator coverage. In particular, the production seek regression found during Step-6 certification was corrected in `PlaybackConnection`: a seek intent for the same current media is retained while Media3 temporarily withholds the seek command during transition, then applied once allowed, and discarded when media changes. The exact certified head passes the retained Step-3 integration test together with the Step-6 decoder suite.

## Documentation-complete head gate

This evidence file is created after the certified implementation run, so its commit creates a new branch head. That documentation-complete head must itself pass the full configured CI matrix before the pull request may be merged.

A green implementation run is therefore necessary but not, by itself, the final branch merge gate.

## Remaining completion gates

Before Step 6 can be declared repository-complete:

1. the exact documentation-complete branch head must pass the full configured matrix;
2. the Step-6 pull request must merge without dropping changes;
3. the exact resulting `main` merge head must pass the same configured matrix;
4. final completion documentation on `main` must remain truthful to the exact certified state.

Until those gates are satisfied, `STEP_6_COMPLETION_REPORT.md` correctly retains **CERTIFICATION PENDING**.

## Physical-device boundary

Still **NOT VERIFIED — DEFERRED TO STEP 10**:

- Snapdragon / Exynos / MediaTek / Tensor behavior
- OEM decoder quirks and cross-OEM fallback
- physical H.264 / HEVC / VP9 / AV1 matrices
- physical 3 GB+/5 GB+/10 GB sources
- physical 4K60 / high-bitrate / HDR / 10-bit playback
- battery / thermal / long-play stability

No physical-device PASS is inferred from the emulator evidence.
