#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/common.sh"
require_adb_device
out="${1:-step10-smoke}"
mkdir -p "$out"
sha="$(repo_sha)"
echo "$sha" > "$out/tested-git-sha.txt"
adb logcat -c
adb shell am force-stop "$PACKAGE"
adb shell monkey -p "$PACKAGE" -c android.intent.category.LAUNCHER 1 >/dev/null
sleep 3
adb shell pidof "$PACKAGE" > "$out/pid.txt" || { echo "App did not launch" >&2; exit 1; }
adb logcat -d -v threadtime > "$out/logcat.txt"
if grep -E 'FATAL EXCEPTION|ANR in com\.zubaer\.maxvideoplayer' "$out/logcat.txt"; then
  echo "Crash/ANR evidence found during smoke launch." >&2
  exit 1
fi
echo "AUTOMATED_SMOKE_LAUNCH_PASS — manual playback/device workflow still required"
