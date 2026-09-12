#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/common.sh"
require_adb_device
out="${1:-step10-diagnostics}"
mkdir -p "$out"
echo "$(repo_sha)" > "$out/tested-git-sha.txt"
adb shell dumpsys media_session > "$out/media-session.txt"
adb shell dumpsys media.codec > "$out/media-codec.txt" 2>/dev/null || true
adb shell dumpsys activity processes "$PACKAGE" > "$out/process.txt" 2>/dev/null || true
adb shell dumpsys package "$PACKAGE" > "$out/package.txt"
"$(dirname "$0")/sanitize_device_report.sh" "$out"
echo "Collected sanitized playback diagnostics in $out"
