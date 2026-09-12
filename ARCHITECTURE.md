# MAX Video Player — Architecture through Step 10

## Clean-room and no-sacrifice boundary

MAX Video Player is a native clean-room Android implementation. Step 10 is a certification/hardening layer over Steps 1–9; it does not replace the established product architecture and does not add unrelated features.

## Single authoritative playback graph

```text
Compose UI / PlayerViewModel / feature controllers
        ↓
PlaybackConnection (MediaController client)
        ↓
PlaybackService : MediaSessionService
        ↓
MediaSession
        ↓
Media3PlaybackEngine
        ↓
ExoPlayer (single service-owned authority)
        ↓
ProfessionalMediaSourceFactory
        ├─ local/content/file
        ├─ network via NetworkDataSourceRouter
        ├─ cloud/source composition
        └─ maxvault:// via EncryptedVaultDataSource
        ↓
ProfessionalRenderersFactory
        ├─ video → decoder policy / Android MediaCodec
        └─ audio → DefaultAudioSink + MaxAudioProcessor
```

No Step-10 test, private playback path, decoder mode, local/network source, Cast integration or UI convenience may create a hidden second Activity/UI ExoPlayer to bypass this graph.

## Functional layers retained

- `core.model`: media/domain/playback state, source types, decoder mode catalog.
- `core.database`: Room schema and explicit non-destructive migrations through v8.
- `core.media`: MediaStore, SAF, URI metadata/availability.
- `core.device`: device and codec capability inventory.
- `playback.engine`: Media3 engine, custom source factory, renderers, decoder policy and audio processing.
- `playback.session`: service, MediaSession, controller connection, queue and service-owned sleep timer.
- `feature.library`: public library/index/history/playlists/file actions.
- `feature.player`: controls, display/orientation/PiP, decoder UI/diagnostics.
- `feature.subtitle`: embedded/external subtitles, styling/timing/system-caption bridge.
- `feature.audio`: embedded/external audio, DSP, sync and route behavior.
- `feature.decoder`: Auto/Hardware/Enhanced Hardware/Software candidate policy and actual runtime diagnostics.
- `feature.network`: HTTP/HLS/DASH/RTSP/SMB/WebDAV/FTP/FTPS clients, credential vault, request registry and DataSource routing.
- Step-8 cloud/Cast/USB/TV/output layers remain intact.
- `feature.privatevault`: versioned encrypted container, authentication/biometric wrapper, Room index, bounded random-access DataSource and privacy UI.
- `feature.settings`: typed non-sensitive settings/import/export/redaction/caption bridge.
- `feature.sleeptimer`: service-owned monotonic timer.

## Decoder invariant

The public labels are immutable: `Auto`, `Hardware`, `Enhanced Hardware`, `Software`. Selection is capability-driven and actual/effective codec reporting must remain truthful. Decoder switching reconfigures the existing ExoPlayer and preserves the coherent queue/timeline/session state rather than moving playback to another player.

## Large-media invariant

Media source sizes, offsets and playback positions use `Long` where total media geometry is involved. Network/local/private media must remain streaming/range/reference based; a >3 GB file must not be loaded wholly into memory or automatically copied just to be played. Bounded buffers may use `Int` only when the bound itself is safe.

## Network/security invariant

HTTP/FTP may remain because they are explicit product features with insecurity communicated to the user. HTTPS/FTPS security must not be weakened: system trust/hostname validation remains enabled. Credentials/tokens stay outside logs, settings export, committed evidence and release artifacts.

## Private Vault invariant

Private media remains an opaque `maxvault://` item backed by a versioned authenticated encrypted container in app-private no-backup storage. Playback performs bounded authenticated random-access decryption through the same Media3 source graph. Move-to-vault ordering remains encrypt → verify → database commit → source delete; failure/cancellation cannot be converted into a false move success.

Private output policy remains media-specific: private content is not relayed to Cast, cannot enter private PiP, is returned from external Presentation output, uses generic session metadata and applies protected-window policy where configured. PIN/passphrase recovery stays independent from optional biometric convenience state.

## Step-10 certification architecture

Step 10 adds a separate evidence layer, not a new playback layer:

```text
exact Git SHA
   ├─ Step10 build/unit/lint/release job
   ├─ API-35 full retained instrumentation
   ├─ strict named critical instrumentation
   ├─ static/package security audit
   └─ physical-device evidence (outside CI when hardware is required)
          ↓
certification/step10/<candidate-sha>/ sanitized summaries
          ↓
final compatibility matrix / release decision
```

Any production-code fix found during certification creates a new candidate SHA and invalidates affected evidence. Documentation-only closure may reuse unaffected evidence only when the binary/source behavior under test is unchanged and the report states that distinction.

## Physical evidence boundary

Emulator/software results do not establish physical behavior. Real phone/tablet, SoC diversity, real >3 GB source, 4K/HDR/high bitrate, Bluetooth/headset, real NAS/protocol equipment, USB/SD, Cast receiver, Android TV, biometric/privacy, thermal/battery/endurance and OEM lifecycle behavior remain `NOT VERIFIED` until executed against real hardware and the exact tested candidate is recorded.

## Release boundary

A Step-10 software-green head can be `PARTIAL` when required physical categories are unavailable. Full `PASS — RELEASE CERTIFIED` additionally requires the physical matrix, no unresolved P0/P1, exact artifact hashes, required merge/post-merge retained gates, and the final release decision. Production signing/publishing is a separate credential/deployment action and is not simulated in repository source.

Do not begin Step 11.
