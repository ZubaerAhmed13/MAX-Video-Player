# STEP 8 TEST MATRIX — CLOUD, CAST, TV & EXTERNAL DEVICES

Legend:

- **AUTOMATED** — deterministic JVM/instrumentation/CI coverage exists.
- **SOFTWARE PASS REQUIRED** — must be green before Step 8 is merged.
- **HARDWARE DEFERRED** — requires a real external device and is not truthfully certifiable by emulator CI.

## A. Cloud authentication and identity

| Requirement | Test / evidence | Level | Gate |
|---|---|---|---|
| OAuth public-client PKCE verifier/state construction | `CloudPkceTest` | JVM | SOFTWARE PASS REQUIRED |
| PKCE state mismatch rejected | `CloudPkceTest` | JVM | SOFTWARE PASS REQUIRED |
| No embedded client secret | architecture/security review + BuildConfig/provider config | Static | SOFTWARE PASS REQUIRED |
| Token encryption uses Android Keystore AES-GCM | `CloudTokenVault` + security review | Instrumented/static | SOFTWARE PASS REQUIRED |
| Room stores opaque auth reference, not raw token | Step-8 DB schema/migration tests | Instrumented | SOFTWARE PASS REQUIRED |
| Stable cloud identity independent of temporary URL | `CloudUriCodec`/provider integration | Instrumented | SOFTWARE PASS REQUIRED |
| Revision mismatch becomes file-changed error | `CloudDataSource` policy/tests | Instrumented | SOFTWARE PASS REQUIRED |

## B. Provider production adapters

| Provider | Browse + auth | Search/paging code | >2 GiB Range through `CloudDataSource` | Gate |
|---|---|---|---|---|
| Google Drive | `Step8CloudProviderIntegrationTest` | production client exercised; paging parsed | same test, offset 2,147,483,648 | SOFTWARE PASS REQUIRED |
| OneDrive | `Step8CloudProviderIntegrationTest` | production client exercised; nextLink parsed | same test, offset 2,147,483,648 | SOFTWARE PASS REQUIRED |
| Dropbox | `Step8CloudProviderIntegrationTest` | production client exercised; cursor parsed | same test, offset 2,147,483,648 | SOFTWARE PASS REQUIRED |

The provider instrumentation substitutes only the remote host with MockWebServer. The actual provider client, Authorization header creation, JSON parsing, stable identity, playback resolution, `CloudDataSource`, Media3 `DataSpec`, OkHttp Range request and response read are production code.

Live third-party account sign-in remains a manual environment test because CI does not store user OAuth accounts or provider secrets.

## C. Cast source selection and relay

| Requirement | Test / evidence | Level | Gate |
|---|---|---|---|
| Public reachable HTTPS -> direct Cast | `CastSourceResolverTest` | JVM | SOFTWARE PASS REQUIRED |
| content/SAF -> local relay | `CastSourceResolverTest` | JVM | SOFTWARE PASS REQUIRED |
| SMB/private source -> local relay | `CastSourceResolverTest` | JVM | SOFTWARE PASS REQUIRED |
| Authenticated adaptive source -> manifest relay | `CastSourceResolverTest` | JVM | SOFTWARE PASS REQUIRED |
| RTSP truthfully unsupported by Cast path | `CastSourceResolverTest` | JVM | SOFTWARE PASS REQUIRED |
| Relay random session path / traversal rejection | `CastRelayServerTest` / security tests | JVM | SOFTWARE PASS REQUIRED |
| GET and HEAD | `CastRelayServerTest` | JVM | SOFTWARE PASS REQUIRED |
| Byte ranges including open-ended/bounds behavior | `CastRangeParserTest`, `CastRelayServerTest` | JVM | SOFTWARE PASS REQUIRED |
| >2 GiB Long range arithmetic | Cast range/data-source tests | JVM/instrumented | SOFTWARE PASS REQUIRED |
| Bounded progressive streaming, no whole-file cache | implementation + relay tests | JVM/static | SOFTWARE PASS REQUIRED |
| Subtitle SRT/ASS/SSA -> Cast-safe WebVTT | subtitle relay tests/policy | JVM | SOFTWARE PASS REQUIRED |
| HLS child playlist/key/segment rewriting | `CastAdaptiveManifestRewriterTest` | JVM | SOFTWARE PASS REQUIRED |
| DASH BaseURL/SegmentTemplate rewriting | `CastAdaptiveManifestRewriterTest` | JVM | SOFTWARE PASS REQUIRED |
| Preserve DASH `$Number$`/`$Time$` template syntax | `CastAdaptiveManifestRewriterTest` | JVM | SOFTWARE PASS REQUIRED |
| Reject sensitive receiver-visible query secrets | `CastAdaptiveManifestRewriterTest` | JVM | SOFTWARE PASS REQUIRED |
| Actual Google Cast receiver route discovery/transfer | real Cast receiver | Hardware | HARDWARE DEFERRED |
| Position continuity on a physical receiver and transfer-back | real Cast receiver | Hardware | HARDWARE DEFERRED |

Media3 route/transfer production wiring is compiled and exercised through the service architecture, but CI does not fabricate a physical Cast receiver and therefore does not claim hardware certification.

## D. USB / OTG / removable storage

| Requirement | Test / evidence | Level | Gate |
|---|---|---|---|
| SAF remains access boundary | permission/static review | Static | SOFTWARE PASS REQUIRED |
| No `MANAGE_EXTERNAL_STORAGE` | manifest review | Static | SOFTWARE PASS REQUIRED |
| Logical source >3.2 GB without allocating whole file | `Step8LargeVirtualContentProvider` | Instrumented | SOFTWARE PASS REQUIRED |
| Read correct bytes beyond 2 GiB | `Step8UsbVirtualProviderTest` | Instrumented | SOFTWARE PASS REQUIRED |
| Provider removal/disconnect fails cleanly | `Step8UsbVirtualProviderTest` | Instrumented | SOFTWARE PASS REQUIRED |
| Removable volume observation lifecycle | `RemovableStorageController` + app lifecycle | Instrumented/static | SOFTWARE PASS REQUIRED |
| Real OTG controller/filesystem/vendor behavior | physical device + drive | Hardware | HARDWARE DEFERRED |

## E. Android TV

| Requirement | Test / evidence | Level | Gate |
|---|---|---|---|
| Leanback launcher / touchscreen optional | manifest build review | Static | SOFTWARE PASS REQUIRED |
| TV-specific Compose Material surface | `TvHomeScreen` | Build/static | SOFTWARE PASS REQUIRED |
| Initial D-pad focus | `Step8TvFocusInstrumentedTest` | Instrumented | SOFTWARE PASS REQUIRED |
| Directional D-pad movement | `Step8TvFocusInstrumentedTest` | Instrumented | SOFTWARE PASS REQUIRED |
| Enter activates focused destination | `Step8TvFocusInstrumentedTest` | Instrumented | SOFTWARE PASS REQUIRED |
| Focus restored to previous destination | `Step8TvFocusInstrumentedTest` | Instrumented | SOFTWARE PASS REQUIRED |
| Vendor remote/key quirks / overscan | physical Android/Google TV | Hardware | HARDWARE DEFERRED |

## F. External displays

| Requirement | Test / evidence | Level | Gate |
|---|---|---|---|
| Uses actual `DisplayManager` presentation category | `Step8ExternalDisplayInstrumentedTest` + controller | Instrumented | SOFTWARE PASS REQUIRED |
| Default display never listed as external | `Step8ExternalDisplayInstrumentedTest` | Instrumented | SOFTWARE PASS REQUIRED |
| Same service-owned player is used | architecture/static review | Static | SOFTWARE PASS REQUIRED |
| No second ExoPlayer/MediaSession created | architecture/static review | Static | SOFTWARE PASS REQUIRED |
| Phone surface cannot reclaim renderer during Presentation | `ExternalDisplayController` pre-draw surface guard | Build/static | SOFTWARE PASS REQUIRED |
| Display removal returns output locally | controller state logic | Instrumented/static | SOFTWARE PASS REQUIRED |
| Cast and Presentation mutually exclusive | player device-info listener logic | Instrumented/static | SOFTWARE PASS REQUIRED |
| Real HDMI/Miracast/desktop-mode adapter | physical hardware | Hardware | HARDWARE DEFERRED |

## G. Regression / coexistence

The existing Android CI remains mandatory and continues to run:

- debug build + all JVM unit tests;
- release compilation;
- lint;
- full API-35 instrumentation;
- deterministic SMB/FTP/FTPS/RTSP Step-7 protocol certification;
- legacy API-26/API-28 thumbnail instrumentation.

Step 8 must not replace or disable any earlier gate.

## H. Dedicated Step-8 workflow

`.github/workflows/step8-certification.yml` adds two explicit jobs:

1. `step8-unit-security`
   - debug compile;
   - all JVM tests (including Cast resolver/relay/manifest and PKCE);
   - release compile;
   - lint.

2. `step8-emulator-certification`
   - API 35 emulator;
   - `Step8CloudProviderIntegrationTest`;
   - `Step8UsbVirtualProviderTest`;
   - `Step8TvFocusInstrumentedTest`;
   - `Step8ExternalDisplayInstrumentedTest`;
   - `Step8DatabaseMigrationTest`.

## I. Final merge rule

Step 8 may be marked complete only when:

1. the exact final branch head has green `Android CI`;
2. the exact final branch head has green `Step 8 Certification`;
3. no known Step-8 software gate remains skipped or falsely represented;
4. the PR is merged without bypassing failed checks;
5. post-merge `main` CI is checked and the result recorded.

Physical-device items may remain explicitly **HARDWARE DEFERRED** for the project’s final physical-device certification step; they may not be mislabeled PASS.