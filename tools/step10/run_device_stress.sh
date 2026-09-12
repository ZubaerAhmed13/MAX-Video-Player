#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/common.sh"
require_adb_device
out="${1:-step10-stress}"
mkdir -p "$out"
echo "$(repo_sha)" > "$out/tested-git-sha.txt"
adb logcat -c
for i in $(seq 1 20); do
  adb shell monkey -p "$PACKAGE" -c android.intent.category.LAUNCHER 1 >/dev/null
  adb shell input keyevent KEYCODE_HOME
  adb shell monkey -p "$PACKAGE" -c android.intent.category.LAUNCHER 1 >/dev/null
done
adb logcat -d -v threadtime > "$out/logcat.txt"
if grep -E 'FATAL EXCEPTION|ANR in com\.zubaer\.maxvideoplayer' "$out/logcat.txt"; then
  echo "Crash/ANR evidence found during lifecycle stress." >&2
  exit 1
fi
echo "AUTOMATED_LIFECYCLE_STRESS_PASS — this does not replace manual hardcore playback stress"
