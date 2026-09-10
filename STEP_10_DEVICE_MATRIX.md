# Step 10 Physical Device Matrix

Step 10 requires real-device evidence. This repository must never turn emulator output into a physical-device PASS.

## Current execution status

No physical Android phone/tablet, Android TV/Google TV target, Cast receiver, USB-OTG device, removable SD target, biometric-capable test device, Bluetooth audio device, or external display is connected to the current automation environment.

**Physical certification status: `NOT VERIFIED — PHYSICAL HARDWARE UNAVAILABLE`.**

## Required device record

For every real device used later, record a sanitized row or JSON record containing manufacturer, model, Android version, API level, SoC family, CPU ABI, RAM, display resolution, refresh rate, HDR capability when known, storage capability, removable-storage capability, USB-OTG capability, Wi-Fi capability, Bluetooth capability, hardware decoder/encoder capabilities where relevant, tested Git SHA, test date, and result.

Never commit IMEI, hardware serial, MAC address, Google account identifiers, Wi-Fi credentials, NAS credentials, OAuth tokens, PINs or passphrases.

| Device class | SoC diversity target | Required evidence | Current result |
| --- | --- | --- | --- |
| Flagship phone/tablet | Modern flagship | codec, 4K/HDR where capable, endurance, memory, thermal, battery | NOT VERIFIED |
| Mid-range phone/tablet | Representative mid-range | playback, seeking, background, subtitles/audio, lifecycle | NOT VERIFIED |
| Different vendor/SoC | Snapdragon/Exynos/MediaTek/Tensor diversity where accessible | decoder and OEM behavior | NOT VERIFIED |
| Android TV / Google TV | Real TV hardware | D-pad-only end-to-end workflow | NOT VERIFIED |
| Cast receiver | Real supported receiver | discovery/handoff/control/recovery | NOT VERIFIED |

Use `tools/step10/collect_device_profile.sh` to start a sanitized profile. The script intentionally omits stable device identifiers and does not mark the device certified.
