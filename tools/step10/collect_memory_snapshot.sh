#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/common.sh"
require_adb_device
out="${1:-step10-memory.txt}"
{
  echo "testedGitSha=$(repo_sha)"
  echo "collectedAt=$(utc_now)"
  adb shell dumpsys meminfo "$PACKAGE"
} > "$out"
echo "Wrote $out"
