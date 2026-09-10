# MAX Video Player — Parity Matrix through Step 9

Status vocabulary: `PASS`, `PARTIAL`, `FAIL`, `NOT VERIFIED — DEFERRED TO STEP 10`, `NOT IMPLEMENTED`, `NOT APPLICABLE`.

A `PASS` requires implemented behavior plus appropriate automated evidence. Final Step-9 software rows remain `PARTIAL` while the documentation-complete exact PR head/post-merge exact `main` certification is pending; this prevents an older green revision from being presented as final evidence. Physical/OEM behavior is never inferred from emulator/software tests.

# Step 9 — Private Media, Security, Settings, Sleep Timer and Accessibility

| Area | Capability | Current status | Evidence / boundary |
|---|---|---|---|
| Vault architecture | Real encrypted private storage | PARTIAL | Versioned `MAXVLT01` encrypted container in app-private no-backup storage; final exact-head cert pending |
| Vault crypto | AES-256-GCM authenticated encryption | PARTIAL | Production JCA implementation + round-trip/tamper tests |
| Vault crypto | Random per-file content key | PARTIAL | Per-container random key wrapped by vault master |
| Vault crypto | Chunked random access | PARTIAL | 1 MiB chunks; production range reader/DataSource tests |
| Vault crypto | No whole-media RAM decrypt/encrypt | PARTIAL | Streaming writer + bounded chunk reader; 64 MiB generated streaming fixture |
| Vault crypto | No plaintext temporary playback file | PARTIAL | `EncryptedVaultDataSource` decrypts requested ranges only |
| Vault metadata | Original private metadata encrypted | PARTIAL | Sensitive metadata is authenticated ciphertext; raw-container sentinel test |
| Vault database | No plaintext private filenames/titles | PARTIAL | Room `private_media` stores opaque ID/location/geometry/status only |
| Vault identity | Opaque `maxvault://` media identity | PARTIAL | UUID identity and resolver |
| Vault import | Copy to Private | PARTIAL | Stream to partial → verify/commit; source retained |
| Vault import | Move to Private | PARTIAL | Encrypt/verify/commit before source deletion |
| Vault import | Source delete failure truth | PARTIAL | Explicit encrypted-copy/original-remains result |
| Vault recovery | Partial/orphan cleanup | PARTIAL | Private storage recovery paths; incomplete items not surfaced as valid |
| Large media | `Long`-safe offsets | PARTIAL | Checked math + 2 GiB/3.2+ GiB synthetic geometry |
| Large media | Real sustained 3 GB+ import | NOT VERIFIED — DEFERRED TO STEP 10 | CI intentionally does not allocate multi-GB source merely to prove arithmetic |
| Playback | Production Media3 encrypted DataSource | PARTIAL | `maxvault://` routes through existing source factory/player |
| Playback | Encrypted H.264/AAC coexistence | PARTIAL | Existing redistribution-safe fixture encrypted at test time; service/decoder/audio track initialization asserted |
| Playback | Activity recreation uses same session/player | PARTIAL | Production coexistence instrumentation compares controller identity across recreation |
| Playback | Embedded audio preserved | PARTIAL | Existing Media3 embedded audio groups remain on private fixture |
| Playback | Embedded subtitles architecture preserved | PARTIAL | Same MediaItem/Media3 source path; no alternate subtitle player |
| Playback | External audio privacy policy | PARTIAL | Ordinary external sidecar not silently attached to private source |
| Session privacy | Generic notification/MediaSession metadata | PARTIAL | Repository/service use `Private media` and opaque identity |
| Session privacy | Vault lock pauses/clears private playback | PARTIAL | PlaybackService observes authoritative vault lock |
| Cast | Private media blocked | PARTIAL | `CastSourceResolver` rejects `maxvault` before direct/relay selection |
| Cast | Public Cast unchanged | PARTIAL | Existing Step-8 Cast architecture retained; private rule is source-specific |
| PiP | Private media blocked | PARTIAL | Private source cannot enter PiP |
| PiP | Public PiP unchanged | PARTIAL | Existing public-media path retained |
| External display | Private Presentation output blocked | PARTIAL | Private playback returns output to phone |
| External display | Public output unchanged | PARTIAL | Existing Step-8 Presentation path retained |
| Capture privacy | `FLAG_SECURE` on protected private surfaces | PARTIAL | Activity/window instrumentation; OEM capture behavior deferred |
| Capture privacy | Ordinary screen policy restored | PARTIAL | Secure flag follows private protected-surface state rather than global disable |
| Authentication | PIN/passphrase unlock | PARTIAL | PBKDF2-derived wrapping key + AES-GCM master envelope |
| Authentication | PIN/passphrase not stored | PARTIAL | Preference sentinel/storage assertions; only salt/nonce/ciphertext/iteration metadata persisted |
| Authentication | Wrong credential fails closed | PARTIAL | Authenticated envelope failure + bounded retry tests |
| Authentication | Credential rate limiting | PARTIAL | Bounded monotonic retry delay after repeated failures |
| Authentication | Change PIN without media re-encryption | PARTIAL | Same master rewrapped with fresh salt |
| Authentication | Verify replacement before atomic commit | PARTIAL | New envelope decrypt/authenticate + constant-time master comparison before persistence |
| Biometric | Optional Keystore wrapper | PARTIAL | Android Keystore + platform BiometricPrompt code/API guards |
| Biometric | PIN recovery after biometric unavailable/invalidation | PARTIAL | Credential envelope remains independent; physical invalidation deferred |
| App Lock | Optional whole-app lock | PARTIAL | Separate controller reuses auth, not Compose-only state |
| App Lock | Immediate/timed auto-lock | PARTIAL | elapsedRealtime timeout policy instrumentation |
| Database | Room v7→v8 | PARTIAL | Explicit `MIGRATION_7_8`, migration test, no destructive fallback |
| Settings | Typed advanced settings | PARTIAL | App Lock, capture, biometric, motion, contrast, captions, sleep fade |
| Settings | Safe reset | PARTIAL | Non-sensitive defaults only; private/cloud/network/history/playlists unaffected |
| Settings | Non-sensitive JSON export | PARTIAL | Versioned codec + secret sentinel tests |
| Settings | Validated JSON import | PARTIAL | 256 KiB bound, supported fields/version validation, malformed/unknown handling |
| Diagnostics | Secret redaction | PARTIAL | Authorization/token/password/cookie/userinfo/signed-query policy tests |
| Sleep timer | Service-owned timer | PARTIAL | Controller attached in PlaybackService, not Activity |
| Sleep timer | Duration/end-current/end-queue | PARTIAL | Production modes + fake-clock tests |
| Sleep timer | Monotonic duration | PARTIAL | `SystemClock.elapsedRealtime` |
| Sleep timer | Optional safe fade | PARTIAL | Player-volume-only fade; cancel restores prior player volume |
| Accessibility | Large-font settings | PARTIAL | Compose test at 2.0× font scale with scroll-to assertions |
| Accessibility | Locked TalkBack privacy | PARTIAL | Sentinel private title absent from unmerged semantics tree |
| Accessibility | Keyboard/D-pad | PARTIAL | Step-8 `TvPlayerInputController` mappings exercised |
| Accessibility | New critical touch targets | PARTIAL | Step-9 controls use minimum size constraints around 48 dp |
| Accessibility | High-contrast controls/theme | PARTIAL | Theme-level contrast option |
| Accessibility | Reduce Motion | PARTIAL | Scoped Step-9 theme policy; essential playback progress not disabled |
| Accessibility | Android system caption preferences | PARTIAL | `CaptioningManager` bridge maps supported style/font scale and restores MAX style |
| Accessibility | Audio-description role label | PARTIAL | Only labels Media3 tracks carrying `ROLE_FLAG_DESCRIBES_VIDEO` |
| CI | Dedicated Step-9 unit/security job | PARTIAL | Exact checkout + debug/unit/release/lint; final docs-complete run pending |
| CI | Dedicated Step-9 emulator job | PARTIAL | Full connected suite + named classes with zero-test rejection; final run pending |
| CI | Exact literal PR-head verification | PARTIAL | Workflow asserts `git rev-parse HEAD == pull_request.head.sha`; final run pending |
| CI | API-26/API-28 retained | PARTIAL | Existing lanes untouched; final exact-head result pending |
| CI | Step-7 protocol lane retained | PARTIAL | Samba/FTP/FTPS/authenticated RTSP job retained |
| CI | Step-8 regression retained | PARTIAL | Existing Step-8 certification workflow retained |
| CI | Step-6 decoder regressions retained | PARTIAL | Existing full unit/instrumentation suites remain part of project CI |
| Physical/OEM | Final device matrix | NOT VERIFIED — DEFERRED TO STEP 10 | Reserved for Step 10 |

# Step 8 — preserved

| Area | Capability | Status before Step-9 final regression | Boundary |
|---|---|---|---|
| Cloud | Existing provider/OAuth/browser/playback integration | PASS | Step-8 implementation retained; exact Step-9 regression still must be green |
| Cast | Public-media Cast and signed relay | PASS | Private `maxvault` is the only new blocked class |
| USB/OTG | Existing removable-storage flow | PASS | Physical removable-storage certification remains Step 10 |
| Android TV | Existing TV navigation/remote policy | PASS | Step-9 reuses input controller; physical hardware deferred |
| External display | Existing Presentation output | PASS | Private content returns to phone; public output unchanged |
| Decoder coexistence | Step-6 routing retained through Step 8 | PASS | Existing decoder regression tests retained |

# Step 7 — preserved

| Area | Capability | Status | Boundary |
|---|---|---|---|
| HTTP/HTTPS | Progressive, ranges, redirects, auth scoping | PASS | Existing Media3/OkHttp path retained |
| HLS/DASH | Adaptive playback/manual quality | PASS | Existing deterministic fixtures retained |
| RTSP | Authenticated Media3 RTSP/TCP | PASS | Isolated server lane retained |
| SMB2/3 | Browse/auth/random reads/change detection | PASS | SMBJ and isolated Samba lane retained |
| FTP/FTPS | Browse/auth/REST range and explicit TLS | PASS | Commons Net + isolated protocol lane retained |
| WebDAV | Browse/security/root confinement/playback | PASS | Existing bounded secure XML path retained |
| SFTP | SSH file transfer | NOT IMPLEMENTED | Not aliased to FTP/FTPS |
| Credentials | Keystore-encrypted network credentials | PASS | Step-9 settings/reset/export does not absorb those secrets |
| Network regressions | Protocol certification | PASS | Must be re-green on final Step-9 head/main |

# Step 6 — preserved

| Area | Capability | Status | Boundary |
|---|---|---|---|
| Decoder modes | Auto / Hardware / Enhanced Hardware / Software | PASS | Existing distinct Media3/MediaCodec routing policies retained |
| Runtime switching | Same player, queue/index/position/play state preserved | PASS | No Step-9 second player |
| Coexistence | Subtitles/audio/DSP/speed/pitch/queue | PASS | Retained regression suite |
| Diagnostics | Actual initialized codec/backend/failures | PASS | Existing device-specific reporting retained |
| API compatibility | Device capability + API guards | PASS | Physical OEM matrix remains Step 10 |

# Step 5 — preserved

| Area | Capability | Status | Boundary |
|---|---|---|---|
| Audio tracks | Embedded/manual/auto/external audio | PASS | Step-9 adds descriptive role label only |
| DSP | EQ/preamp/boost/limiter/channel/balance | PASS | Existing `MaxAudioProcessor` retained |
| Sync/pitch | Delay/route compensation/pitch/speed | PASS | Existing production path retained |
| Background/audio-only | Existing policy | PASS | Not globally changed by Step 9 |

# Step 4 — preserved and extended

| Area | Capability | Status | Boundary |
|---|---|---|---|
| Embedded/external subtitles | Off/Auto/manual/sidecar/relink | PASS | Existing Media3 subtitle architecture retained |
| Formats/encoding | SRT/WebVTT/SSA/ASS/TTML and supported encodings | PASS | Existing parser path retained |
| Styling/timing | MAX custom style + per-media delay | PASS | Custom style remains authoritative when system style off |
| Android system caption style | PARTIAL | Implemented in Step 9; final exact-head certification pending |

# Steps 1–3 — preserved

| Step | Capability group | Status | Boundary |
|---|---|---|---|
| Step 3 | Player controls, seeking, gestures, display/orientation, queue/repeat/shuffle, public PiP | PASS | Private restrictions are narrow; seek-preview limitation retains earlier status |
| Step 2 | Library/folders/history/favourites/playlists/SAF/relink/file actions/thumbnails | PASS | Private media excluded from ordinary public file actions; API-26/28 thumbnail lanes retained |
| Step 1 | Service-owned playback/MediaSession/resume/URI handling/long-safe values | PASS | Still the authority for Step-9 private playback |

# Step-9 final gate

The exact documentation-complete Step-9 PR head must pass:

- `:app:assembleDebug`;
- `:app:testDebugUnitTest`;
- `:app:assembleRelease`;
- `:app:lintDebug`;
- API-35 `:app:connectedDebugAndroidTest`;
- dedicated Step-9 unit/security and named emulator classes;
- retained API-26/API-28 lanes;
- retained Step-7 protocol certification;
- retained Step-8 certification;
- retained Step-6 decoder/coexistence tests.

Then PR #22 must merge and the exact resulting/final `main` head must pass the required software/emulator gates before the Step-9 rows can be finalized as PASS.

# Physical certification deferred to Step 10

**NOT VERIFIED — DEFERRED TO STEP 10**:

- real biometric/OEM prompt and invalidation behavior;
- OEM screenshot/recording/recents behavior;
- sustained real 3 GB+ private imports and low-storage exhaustion;
- real 4K/HDR/high-bitrate private playback/seek;
- long-play, battery and thermal behavior;
- physical SD/USB/removable storage;
- physical Chromecast/receiver behavior;
- real Android TV remote/focus behavior;
- HDMI/Miracast/desktop/external-display behavior;
- aggressive vendor process killing/background restrictions.

The software matrix must not convert these hardware deferrals into PASS.
