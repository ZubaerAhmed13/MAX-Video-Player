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

## Physical UI release-hardening matrix

The exact final UI-hardening PR-head APK must be installed on physical hardware before the original screenshot defects can be closed. Use non-sensitive test media and record the exact Git SHA with every result.

| UI scenario | Required checks | Current result |
| --- | --- | --- |
| Main folder screen | status/cutout clearance, compact app bar, source-rail scrolling, folder density, bottom-safe Play action | NOT VERIFIED |
| Video-list screen | 16:9 thumbnails, duration badges, title wrapping/ellipsis, metadata, row menus, smooth vertical scrolling | NOT VERIFIED |
| Player controls | video-dominant layout, compact top bar, safe system regions, clean timeline, centered transport, auto-hide/Back | NOT VERIFIED |
| Expanded tool rail | horizontal scrolling, no clipping, compact targets remain usable, modified state visible | NOT VERIFIED |
| Audio panel | right-side overlay, scrollability, focus/reachability, tracks/external audio/sync/DSP-EQ controls | NOT VERIFIED |
| Subtitle panel | right-side overlay, scrollability, focus/reachability, embedded/external/timing/style controls | NOT VERIFIED |
| Decoder dialog | centered modal, exact Auto/Hardware/Enhanced Hardware/Software labels, diagnostics reachability | NOT VERIFIED |
| More panel | right-side tools surface, scrolling, Aspect/Speed/Playback/Gestures/Controls/Decoder/Info/Rotate/Orientation/PiP actions | NOT VERIFIED |
| Large font | no inaccessible title/tabs/panels/dialog/tools; responsive reflow/scrolling | NOT VERIFIED |
| Increased Android display size | no clipped primary controls or inaccessible panel content | NOT VERIFIED |
| Gesture navigation | no control collision with gesture handle/navigation region | NOT VERIFIED |
| 3-button navigation | no bottom-control/FAB collision with navigation bar | NOT VERIFIED |
| Portrait → landscape → portrait | stable layout/state and no system-bar collision | NOT VERIFIED |

### Required sanitized screenshot set

Capture from the exact candidate APK:

1. main folder screen;
2. video-list screen;
3. player controls;
4. expanded tool rail;
5. Audio panel;
6. Subtitle panel;
7. Decoder dialog;
8. More panel.

Screenshots are certification evidence only. Approved/reference screenshots are design references and must not be copied into production resources or shipped inside APK/AAB output.

Use `tools/step10/collect_device_profile.sh` to start a sanitized profile. The script intentionally omits stable device identifiers and does not mark the device certified.
