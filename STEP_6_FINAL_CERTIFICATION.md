# Step 6 Final Certification — Professional Decoder Engine

## Final result

**STEP 6: PASS**

This document records the completed Step-6 Professional Decoder Engine and its later coexistence hardening. Step 7 is not started here.

## Original Step-6 certification

The original Step-6 implementation was certified before the coexistence hardening work:

- branch: `step-6-professional-decoder-engine`
- exact documentation-complete branch head: `c463ecf526b359833053ac505ebc68598e435b12`
- pre-merge Android CI: run #216 (`34208213180`) — PASS
- pull request: #9 — `Step 6 — Professional Decoder Engine`
- resulting `main` merge commit: `b47c4315cb895269a14a1ef8dc71696423f8fdc0`
- post-merge Android CI: run #218 (`34209217145`) — PASS

The original implementation evidence run #215 (`34207634128`) also passed and exported the API-35 decoder capability report. At that point the connected API-35 suite recorded 39 tests, 0 failures, 0 errors and 0 skipped.

## Coexistence hardening certification

A later review identified cross-step certification gaps around queue continuity, sidecars, DSP, playback parameters, Activity recreation and Audio-only behavior during decoder switching. Those gaps were closed as Step-6 hardening only; Step 7 was not started.

### Certified implementation head

Branch: `step-6-coexistence-hardening`

Exact implementation head:

`1249363cdd9f5843c85dca399650db9a457a192d`

Android CI run #223 (`34215009083`) passed the complete configured matrix on that exact head:

- debug build + JVM/unit tests — PASS
- release compilation — PASS
- Android lint — PASS
- API-35 full instrumentation — PASS
- API-26 thumbnail regression — PASS
- API-28 thumbnail regression — PASS

API-35 run #223 recorded **41 tests, 0 failures, 0 errors and 0 skipped**. The instrumentation artifact was `instrumentation-reports` artifact ID `10051504401`, digest `sha256:62105cf381bc52afe8f2a8acfcc5995dba3a01a32db674ab98f55388524c4de4`.

### Certified documentation-complete PR head

Exact hardening documentation-complete head:

`4a7c1e351dab7e5dc1e6d1acd4e1954cec3ceee8`

Android CI run #225 (`34215510508`) passed the same complete matrix on that exact head.

### Hardening merge evidence

Pull request: #10 — `Step 6 — coexistence hardening certification`

Certified head merged:

`4a7c1e351dab7e5dc1e6d1acd4e1954cec3ceee8`

Resulting exact `main` merge commit:

`4921f43deae9c1b3ff221071a30cc1dab26efa0b`

The merge was performed with an expected-head SHA lock, so GitHub would have rejected the merge if the certified PR head had moved.

### Certified post-hardening `main` head

Android CI run #226 (`34215929984`) completed successfully on exact `main` merge commit:

`4921f43deae9c1b3ff221071a30cc1dab26efa0b`

Required post-merge jobs all completed with `success`:

- debug build + JVM/unit tests — PASS
- release compilation — PASS
- Android lint — PASS
- API-35 full instrumentation — PASS
- API-26 thumbnail regression — PASS
- API-28 thumbnail regression — PASS

This satisfies the Step-6 coexistence hardening merge-completion rule.

## Closed coexistence certification gaps

The API-35 production-path hardening tests now certify all of the following:

| Certification row | Result |
|---|---|
| Queue A/B/C continuity across decoder switch | **PASS** |
| Previous/Next after decoder switch | **PASS** |
| Repeat/shuffle preservation through switch | **PASS** |
| Step-4 external subtitle survives decoder switch | **PASS** |
| Step-5 external audio survives decoder switch | **PASS** |
| EQ/DSP remains active after decoder switch | **PASS** |
| Audio delay survives decoder switch | **PASS** |
| Speed + pitch continuity through switch | **PASS** |
| Activity recreation with selected decoder mode | **PASS** |
| Decoder change while Audio-only is enabled | **PASS** |
| Restore video using newly requested decoder | **PASS** |

The new `Step6CoexistenceIntegrationTest` exercises the real service-owned production graph and does not use a mock player, second ExoPlayer, `Assume`/skip, manual post-switch sidecar reselection or weakened backend assertions.

## Decoder evidence retained

The combined Step-6 software/emulator evidence includes:

- real Auto / Hardware / Enhanced Hardware / Software routing policies
- requested mode tracked separately from actual initialized decoder/backend
- hardware-only and software-only isolation rules
- bounded fallback and failed-codec session blacklist
- state-safe same-ExoPlayer decoder switching
- preserved A/B/C queue, index, position and Previous/Next behavior
- preserved repeat/shuffle and playback parameters, including speed/pitch
- preserved Step-4 external subtitle association/selection/rendering
- preserved Step-5 external audio selection
- preserved Step-5 `MaxAudioProcessor`, EQ/DSP and delay state
- Activity recreation with selected decoder mode and sidecar state
- Audio-only decoder change with newly requested decoder used when video is restored
- explicit Room v4→v5 migration and decoder per-media state
- advanced device-specific decoder capability inventory
- real API-35 production routing instrumentation
- retained Step-1–5 regressions

The API-35 capability evidence recorded 18 real video-decoder entries on the tested emulator: four hardware-classified Goldfish entries, fourteen software entries and zero unknown classifications. This is evidence for that emulator only and is not a universal Android codec-support claim.

## Clean-room / dependency result

Step 6 adds no proprietary MX Player source, assets, package identity, decoder binary or branding. It also adds no bundled FFmpeg/native video decoder, no second player and no new native `.so` decoder payload.

## Physical certification boundary

The following remain **NOT VERIFIED — DEFERRED TO STEP 10**:

- Snapdragon / Exynos / MediaTek / Tensor decoder behavior
- OEM codec quirks across Samsung / Xiaomi / Oppo / OnePlus and other devices
- representative physical H.264 / HEVC / VP9 / AV1 matrices
- physical 3 GB+/5 GB+/10 GB source behavior
- physical 4K60 / high-bitrate / HDR / 10-bit behavior
- battery / thermal / long-play stability
- cross-OEM runtime fallback behavior
- broad phone/tablet matrix

These deferred physical items do not invalidate the Step-6 software/emulator PASS and must not be represented as already certified.

## Final documentation closure rule

This certification file, `README.md`, `STEP_6_COMPLETION_REPORT.md` and `STEP_6_COEXISTENCE_HARDENING.md` are finalized together as documentation-only closure after exact `main` merge commit `4921f43deae9c1b3ff221071a30cc1dab26efa0b` passed run #226.

The documentation-only closure head must itself pass the repository's unchanged full Android CI workflow before merge, and the resulting `main` head must pass the same workflow. Because a Git commit cannot reliably embed its own final SHA without changing that SHA, the exact final documentation-closure commit identity and its CI run remain authoritative in GitHub history rather than being recursively written into this file.

No production source, test, dependency, database schema or workflow gate is weakened by the documentation closure.

## Roadmap boundary

**STEP 6: PASS**

**STEP 7: NOT STARTED**
