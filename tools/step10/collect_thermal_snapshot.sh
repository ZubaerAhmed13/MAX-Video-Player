#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/common.sh"
require_adb_device
out="${1:-step10-thermal.txt}"
{
  echo "testedGitSha=$(repo_sha)"
  echo "collectedAt=$(utc_now)"
  adb shell dumpsys thermalservice 2>/dev/null || adb shell dumpsys hardware_properties 2>/dev/null || true
} > "$out"
echo "Wrote $out; interpret values device-by-device rather than applying a universal temperature threshold."
