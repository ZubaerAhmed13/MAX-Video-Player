# Step 7 Branch Certification — Professional Network Playback and Sources

## Scope

This document records the exact pre-merge software/emulator certification evidence for Step 7. It does not certify physical Android devices or external NAS products, and it does not begin Step 8.

## Certified implementation head

- Branch: `step-7-professional-network-playback`
- Exact remote implementation SHA: `4cb661b978de502b99bef864cc99a53c1540939a`
- Exact Git tree: `f4d45f71279e19938b8149731b289913f9bc1013`
- Pull request: [#12](https://github.com/ZubaerAhmed13/MAX-Video-Player/pull/12)
- Android CI run: [#242](https://github.com/ZubaerAhmed13/MAX-Video-Player/actions/runs/34239231820)
- Workflow run ID: `34239231820`
- Workflow conclusion: `success`

Every configured job for this exact implementation SHA completed successfully:

| Job | Job ID | Result |
|---|---:|---|
| debug build, JVM/unit tests, release compilation and Android lint | `102104805518` | PASS |
| API-35 complete instrumentation | `102104805905` | PASS — 59 tests, 0 failures, 0 errors, 0 skipped |
| API-26 thumbnail regression | `102104805830` | PASS — 2 tests, 0 failures, 0 errors, 0 skipped |
| API-28 thumbnail regression | `102104805590` | PASS — 2 tests, 0 failures, 0 errors, 0 skipped |
| isolated API-35 network protocol certification | `102104805929` | PASS — 1 integration test, 0 failures, 0 errors, 0 skipped |

The JVM source set contains 96 declared `@Test` methods across 21 test files, and `:app:testDebugUnitTest` completed successfully as part of the build job. The API-35 counts above come from the archived Gradle XML result, not from log-line inference.

## Immutable artifacts

| Artifact | ID | SHA-256 digest |
|---|---:|---|
| `lint-reports` | `10061608100` | `20da942ec6c5cb6a8f02928d699aeb0d699cff0dc24a22e7cc16277083a1ebbc` |
| `instrumentation-reports` | `10061493133` | `a27ef6109391cd2218238883ace3901ae991bbbb4571a5e5b6190e6eed45133f` |
| `network-protocol-reports` | `10061476272` | `5e556092cdeecf522a950e21225f950b58e83d65f21c5d501857de35aae321fd` |
| `legacy-thumbnail-reports-api-28` | `10061441677` | `eda2105c7f64f5fde2f58244e34b0d3b4509789917806c6ef23b9825aba2abce` |
| `legacy-thumbnail-reports-api-26` | `10061426999` | `3c3729c743bcf071268afcfefc77317134ba98391a582c9b03125ccc701faa7c` |

## Step-7 protocol evidence

The complete API-35 suite passed authenticated progressive HTTP playback, byte ranges, redirect isolation and loop bounds, signed-token canonical identity/redaction, credential-vault lifecycle, secure WebDAV PROPFIND parsing/root confinement, M3U expansion, HTTP/FTP warning UI, HLS VOD/manual quality, rolling HLS live/Go Live, multi-representation DASH with AAC, Room v5→v6 migration and all retained Step-1–6 tests.

The isolated protocol job provisioned deterministic, runner-local servers and passed:

- Samba 4.19.5 with SMB2 minimum, SMB3 maximum and mandatory signing;
- pyftpdlib 1.5.9 with authenticated UTF-8 listings and binary REST transfers;
- MediaMTX 1.21.0 pinned to `sha256:19fddade8d6110a3d718ac0045681fbeba344ae563a066205fe5929a87f7582f`;
- FFmpeg 6.1.1 publishing a continuous deterministic H.264/AAC stream over RTSP/TCP.

The test rejected wrong SMB/FTP passwords, listed Unicode paths, compared exact random-read bytes, read sparse content at offset 3,221,225,472, and required the single service-owned Media3 player to play through the production SMB, FTP and RTSP/TCP paths.

## Documentation-complete head gate

This evidence file and the accompanying canonical-document updates create a new branch head. That exact documentation-complete head must pass the same full workflow before PR #12 may be merged. Run #242 certifies the implementation head; it is not reused as evidence for the later documentation head.

## Remaining completion gates

Before the repository may state `STEP 7: PASS`:

1. the exact documentation-complete PR head must pass every configured job;
2. PR #12 must merge with its expected head locked;
3. the exact resulting `main` SHA must pass the same full workflow, including the isolated protocol job;
4. final completion documentation must record the branch and post-merge evidence without weakening the Step-10 physical boundary.

Until those gates finish, `STEP_7_COMPLETION_REPORT.md` correctly remains **CERTIFICATION PENDING**.

## Physical boundary

Still **NOT VERIFIED — DEFERRED TO STEP 10**: physical NAS/router/OEM interoperability, weak-network and handoff behavior, real 3 GB+ remote media, 4K/HDR/high-bitrate playback, long-play stability, battery and thermal behavior.

