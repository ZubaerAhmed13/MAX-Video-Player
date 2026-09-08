# Step 6 Test Matrix — Professional Decoder Engine

Status terms: `PASS`, `CAPABILITY-AWARE`, `DEFERRED TO STEP 10`.

This matrix describes automated evidence. It does not convert emulator codec availability into universal Android hardware support.

## JVM / policy tests

| Test area | Evidence |
|---|---|
| Mode catalog | `DecoderModeCatalogTest` requires Auto, Hardware, Enhanced Hardware and Software as distinct persisted values |
| Hardware isolation | `DecoderSelectionPolicyTest` verifies Hardware returns only the preferred hardware candidate and never software |
| Enhanced Hardware isolation | verifies multiple hardware candidates can be returned while software is excluded |
| Software isolation | verifies software-only candidates and rejects hardware leakage |
| Auto ordering | verifies deterministic hardware → unknown → software preference |
| Session blacklist | failed candidate is excluded and fallback terminates when no candidate remains |
| Profile / level | incompatible reported profile/level is rejected while unknown capability remains conservative rather than guessed unsupported |
| Resolution / rate | explicit unsupported size/rate is rejected; unknown capability does not fabricate rejection |
| Secure requirement | non-secure candidate cannot satisfy a secure requirement |
| Legacy classification | known software families are identified; ambiguous legacy names remain unknown |
| Preference decoding | unknown persisted decoder strings safely fall back to Auto |

## Room migration instrumentation

`MaxDatabaseMigrationTest` extends the migration chain to **v5** and verifies explicit `MIGRATION_4_5` preservation of prior data, including:

- playback history/resume
- multi-GB-safe `Long` fields
- favourites
- playlists and playlist items
- library/index/preferences
- Step-4 subtitle state
- Step-5 external-audio associations and audio media state
- new Step-6 `decoder_media_state` independence

No destructive migration fallback is used.

## API-35 production decoder integration

`Step6DecoderIntegrationTest` uses the real production path:

```text
PlaybackConnection
→ MediaController
→ PlaybackService
→ MediaSession
→ Media3PlaybackEngine
→ ProfessionalRenderersFactory
→ ProfessionalMediaCodecSelector
→ Android/Media3 decoder
```

Assertions:

- Auto initializes an actual decoder and reports the actual initialized name/backend.
- Software uses a genuine software-only H.264 decoder when the emulator exposes one; otherwise the test requires a truthful unavailable state.
- Hardware uses a genuine hardware-accelerated decoder when exposed; otherwise the test requires truthful unavailable state.
- Enhanced Hardware never leaks to software and requires an actual hardware decoder when hardware exists.
- switching after a seek preserves the current playback position rather than restarting at zero.
- requested mode remains separate from effective backend diagnostics.
- name/backend readiness requires a settled classified lifecycle state rather than accepting a transient stale initialization callback.
- no `Assume`/skip converts missing backend capability into PASS.

## API-35 decoder coexistence hardening

`Step6CoexistenceIntegrationTest` closes the cross-step decoder-switch certification gaps on the same real service-owned production graph. It does not manually restore the state being tested.

| Coexistence area | Status | Evidence |
|---|---|---|
| Queue A/B/C continuity across decoder switch | **PASS** | exact A/B/C IDs/order/count and B index survive decoder reprepare |
| Previous/Next after decoder switch | **PASS** | Next reaches C and Previous returns to B after switching decoder |
| Repeat/shuffle preservation through switch | **PASS** | Repeat All plus shuffle false/true are asserted across separate decoder switches |
| Step-4 external subtitle survives decoder switch | **PASS** | external SRT remains attached/selected and a real subtitle cue renders after reconfiguration |
| Step-5 external audio survives decoder switch | **PASS** | external-audio association remains and Media3 automatically re-selects the external track after reprepare |
| EQ/DSP remains active after decoder switch | **PASS** | DSP installed, EQ enabled, Vocal preset, preamp and boost remain active |
| Audio delay survives decoder switch | **PASS** | 250 ms media delay + 100 ms route compensation remain 350 ms effective/realtime delay |
| Speed + pitch continuity through switch | **PASS** | playback parameters remain 1.5× speed and 1.2 pitch |
| Activity recreation with selected decoder mode | **PASS** | decoder mode/backend and queue/sidecar/DSP/policy state survive Activity recreation |
| Decoder change while Audio-only is enabled | **PASS** | video remains disabled while the new decoder mode is requested; position and error-free playback are retained |
| Restore video using newly requested decoder | **PASS** | when Audio-only is disabled, video returns using an actual decoder matching the newly requested backend |

Exact implementation evidence:

- branch head: `1249363cdd9f5843c85dca399650db9a457a192d`
- Android CI run #223: `34215009083`
- API-35 tests: **41**
- failures: **0**
- errors: **0**
- skipped: **0**
- instrumentation artifact digest: `sha256:62105cf381bc52afe8f2a8acfcc5995dba3a01a32db674ab98f55388524c4de4`

See `STEP_6_COEXISTENCE_HARDENING.md` for the detailed evidence and Step-10 boundary.

## API-35 decoder capability report

`Step6CodecCapabilityReportTest` scans the emulator's real `MediaCodecList` and validates classification against API-29+ platform flags. CI exports:

`app/build/reports/step6/decoder-capability-report-api35.txt`

The report includes:

- API level / emulator device / ABIs
- codec name
- hardware/software/unknown classification
- MIME
- hardware/software/vendor flags where exposed
- adaptive / secure / tunneled / low-latency features where exposed
- profile/level pairs
- color formats
- 720p / 1080p / 1440p / 2160p support probes at 30 and 60 fps

The report is explicitly device/emulator-specific and is not universal Android codec evidence.

## Codec fixture

| Fixture | Container | Video | Resolution | Duration | SHA-256 | Purpose |
|---|---|---|---:|---:|---|---|
| `AndroidTestMediaFixture.writeShortH264Mp4` | MP4 | H.264 / AVC | 160×90 | 2.0 s | `f636bcf8f6bedbd668888db0e71a199c6b24f56e0c27b455d3d1725ab3165a69` | deterministic real decoder initialization and mode switching |
| `step5_multi_audio.mp4` + generated PCM WAV + `step5_external.srt` | MP4 + WAV + SRT | H.264 / AVC | test asset | short | repository test assets / generated WAV | queue, external subtitle/audio, DSP/delay, speed/pitch and Audio-only coexistence through decoder switching |

## Existing regressions retained

The Step-6 CI does not delete or weaken earlier coverage. The exact branch head must retain:

- Step-1 playback/history/foundation tests
- Step-2 library/index/file/thumbnail tests
- Step-3 gesture/display/PiP tests
- Step-4 subtitle tests
- Step-5 DSP/audio/external-audio/background tests
- Step-6 decoder routing and coexistence tests
- API-26 thumbnail regression lane
- API-28 thumbnail regression lane
- API-35 complete instrumentation lane

## Required final CI gate

The exact final Step-6 hardening branch head must pass:

- `:app:assembleDebug`
- `:app:testDebugUnitTest`
- `:app:assembleRelease`
- `:app:lintDebug`
- `:app:connectedDebugAndroidTest` on API 35
- retained API-26 regression
- retained API-28 regression

After merge, the exact `main` merge head must pass the same configured workflow before the coexistence hardening is called repository-complete.

## Physical device boundary

The following remain **NOT VERIFIED — DEFERRED TO STEP 10**:

- real Snapdragon / Exynos / MediaTek / Tensor behavior
- physical H.264 / HEVC / VP9 / AV1 decoder performance
- 4K60 / high-bitrate / HDR / 10-bit physical playback
- OEM codec crash/fallback quirks
- battery / thermal / long-play stability
