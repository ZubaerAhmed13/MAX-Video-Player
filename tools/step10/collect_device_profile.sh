#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/common.sh"
require_adb_device
require_cmd python3

out="${1:-step10-device-profile.json}"
manufacturer="$(adb shell getprop ro.product.manufacturer | tr -d '\r')"
model="$(adb shell getprop ro.product.model | tr -d '\r')"
android="$(adb shell getprop ro.build.version.release | tr -d '\r')"
api="$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
abi="$(adb shell getprop ro.product.cpu.abi | tr -d '\r')"
hardware="$(adb shell getprop ro.hardware | tr -d '\r')"
ram_kb="$(adb shell awk '/MemTotal/ {print $2}' /proc/meminfo | tr -d '\r')"
size="$(adb shell wm size | tr -d '\r' | tail -n1)"
sha="$(repo_sha)"

MANUFACTURER="$manufacturer" MODEL="$model" ANDROID="$android" API="$api" ABI="$abi" HARDWARE="$hardware" RAM_KB="$ram_kb" DISPLAY="$size" SHA="$sha" OUT="$out" python3 - <<'PY'
import json, os
record = {
  "manufacturer": os.environ["MANUFACTURER"],
  "model": os.environ["MODEL"],
  "androidVersion": os.environ["ANDROID"],
  "apiLevel": int(os.environ["API"] or 0),
  "cpuAbi": os.environ["ABI"],
  "socOrHardware": os.environ["HARDWARE"],
  "ramKb": int(os.environ["RAM_KB"] or 0),
  "displaySizeRaw": os.environ["DISPLAY"],
  "testBuildSha": os.environ["SHA"],
  "result": "PROFILE_ONLY_NOT_CERTIFIED"
}
with open(os.environ["OUT"], "w", encoding="utf-8") as f:
    json.dump(record, f, indent=2, sort_keys=True)
    f.write("\n")
PY

echo "Wrote sanitized device profile to $out (serial/IMEI/MAC/account identifiers intentionally omitted)."
