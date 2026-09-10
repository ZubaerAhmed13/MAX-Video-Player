# Step 10 Performance Report

## Status

Physical performance certification has not been executed because no physical certification device is connected to this environment. No device-specific metric is invented below.

| Metric | Physical result |
| --- | --- |
| Startup timing | NOT VERIFIED |
| Media start latency | NOT VERIFIED |
| Seek settle behavior | NOT VERIFIED |
| Baseline / stressed Java heap | NOT VERIFIED |
| Native memory | NOT VERIFIED |
| File descriptors / threads / sockets | NOT VERIFIED |
| Codec instances / surfaces | NOT VERIFIED |
| Dropped frames | NOT VERIFIED |
| CPU / network throughput | NOT VERIFIED |
| Thermal state | NOT VERIFIED |
| Battery change | NOT VERIFIED |
| 60-minute SoC endurance tier | NOT VERIFIED |
| ~4-hour primary-device endurance tier | NOT VERIFIED |

## Collection tooling

Use `collect_memory_snapshot.sh`, `collect_thermal_snapshot.sh`, and `collect_playback_diagnostics.sh` against the exact release-candidate SHA. Capture baseline, after one video, after repeated media switches, after 100+ seeks, decoder switches, subtitle/audio-sidecar churn, network reconnect, Cast cycles, vault playback and endurance playback.

Do not infer universal performance from one device or invent a universal thermal threshold. A >3 GB source must not cause RAM usage proportional to total file size.
