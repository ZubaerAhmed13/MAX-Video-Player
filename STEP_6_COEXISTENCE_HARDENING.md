# Step 6 Coexistence Hardening Certification

## Scope

This document closes the cross-step Step-6 certification gaps discovered after the original Professional Decoder Engine merge. It does **not** begin Step 7.

The hardening tests exercise the real service-owned production graph on API 35:

```text
PlaybackConnection / AudioPlaybackController / DecoderRepository
→ MediaController
→ PlaybackService / MediaSession
→ Media3PlaybackEngine
→ one ExoPlayer
→ ProfessionalRenderersFactory / MediaCodec
```

No mock player, second ExoPlayer, `Assume`/skip, manual post-switch sidecar re-selection, or weakened backend assertion is used.

## Certified implementation head

Branch: `step-6-coexistence-hardening`

Exact implementation head:

`1249363cdd9f5843c85dca399650db9a457a192d`

Android CI run #223 (`34215009083`) completed successfully on that exact head:

- debug build + JVM/unit tests — PASS
- release compilation — PASS
- Android lint — PASS
- API-35 full instrumentation — PASS
- API-26 thumbnail regression — PASS
- API-28 thumbnail regression — PASS

API-35 artifact `instrumentation-reports`:

- artifact ID: `10051504401`
- digest: `sha256:62105cf381bc52afe8f2a8acfcc5995dba3a01a32db674ab98f55388524c4de4`
- tests: **41**
- failures: **0**
- errors: **0**
- skipped: **0**

The new `Step6CoexistenceIntegrationTest` contributed two production-path tests, both PASS:

1. `queueSidecarsDspPoliciesAndRecreationSurviveDecoderSwitch`
2. `decoderChangeWhileAudioOnlyRestoresVideoUsingNewlyRequestedDecoder`

The existing `Step6DecoderIntegrationTest.autoAndExplicitModesReportActualInitializedDecoderAndPreservePosition` also passed in the same API-35 process after its readiness condition was hardened to require a settled decoder-name/backend classification pair.

## Closed certification gaps

| Certification row | Status | Automated evidence |
|---|---|---|
| Queue A/B/C continuity across decoder switch | **PASS** | real A/B/C Media3 queue starts on B; exact IDs/order/count/index are asserted after decoder reconfiguration |
| Previous/Next after decoder switch | **PASS** | after switching decoder, Next reaches C and Previous returns to B on the original queue |
| Repeat/shuffle preservation through switch | **PASS** | Repeat All and both shuffle=false/true states are asserted across separate decoder switches |
| Step-4 external subtitle survives decoder switch | **PASS** | external SRT association/selection restores automatically and its real cue renders after decoder reconfiguration |
| Step-5 external audio survives decoder switch | **PASS** | persisted external WAV association remains selected and Media3 automatically re-selects the external audio track after reprepare; the test performs no manual re-selection |
| EQ/DSP remains active after decoder switch | **PASS** | `dspPipelineInstalled`, EQ enabled, Vocal preset, non-zero preamp and boost are asserted after decoder switches |
| Audio delay survives decoder switch | **PASS** | per-media 250 ms delay + 100 ms route compensation remain 350 ms effective/realtime delay after reconfiguration |
| Speed + pitch continuity through switch | **PASS** | Media3 playback parameters remain speed 1.5× and pitch 1.2 across decoder switches |
| Activity recreation with selected decoder mode | **PASS** | Activity recreation preserves B, A/B/C queue, selected decoder mode/backend, repeat/shuffle, speed/pitch, external subtitle/audio, DSP and delay |
| Decoder change while Audio-only is enabled | **PASS** | video track remains disabled while a new decoder mode is requested; playback remains error-free and position is retained |
| Restore video using newly requested decoder | **PASS** | disabling Audio-only restores video and the actual initialized decoder matches the newly requested hardware/software backend |

## Important end-state semantics

Media3 decoder reprepare is asynchronous. The certification therefore distinguishes transient rebuild state from the required end state:

- external audio must restore automatically through the existing production persistence/listener path before the assertion deadline;
- decoder diagnostics must settle to an actual initialized decoder whose reported backend matches Android/Media3 classification;
- no test manually fixes the state it is supposed to verify.

The first hardening attempt exposed these asynchronous windows. Run #223 certifies the corrected end-state assertions without changing or relaxing the product requirements.

## Existing Step-6 guarantees retained

The hardening pass does not replace the original decoder certification. The following remain required and passed in run #223:

- Auto / Hardware / Enhanced Hardware / Software are distinct routing policies
- actual initialized decoder is reported separately from requested mode
- hardware/software isolation remains enforced
- state-safe same-ExoPlayer reconfiguration remains intact
- Step-4 subtitle architecture remains intact
- Step-5 external audio and DSP architecture remains intact
- Room v5 persistence remains intact
- Step-1–5 regressions remain in the API-35 suite
- API-26 and API-28 regression lanes remain green

## Physical-device boundary

This certification is API-35 emulator software/integration evidence. The following remain **NOT VERIFIED — DEFERRED TO STEP 10**:

- Snapdragon / Exynos / MediaTek / Tensor behavior
- OEM-specific codec quirks
- physical 3 GB+/5 GB+/10 GB source behavior
- physical 4K60 / HDR / 10-bit / high-bitrate playback
- battery / thermal / long-play stability
- broad phone/tablet matrix

## Merge gate

This hardening branch must not be merged solely from the implementation run above. After this evidence is committed, the exact documentation-complete PR head must pass the same complete configured CI matrix. After merge, the resulting exact `main` head must pass that matrix again before the hardening certification is considered repository-complete.

**STEP 7: NOT STARTED**
