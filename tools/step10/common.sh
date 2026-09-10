#!/usr/bin/env bash
set -euo pipefail

PACKAGE="${MAX_PACKAGE:-com.zubaer.maxvideoplayer}"

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || { echo "Required command missing: $1" >&2; exit 2; }
}

require_adb_device() {
  require_cmd adb
  local count
  count="$(adb devices | awk 'NR>1 && $2=="device" {n++} END {print n+0}')"
  test "$count" -eq 1 || { echo "Exactly one authorized physical/test device is required; found $count." >&2; exit 3; }
}

repo_sha() {
  git rev-parse HEAD 2>/dev/null || echo UNKNOWN
}

utc_now() {
  date -u +'%Y-%m-%dT%H:%M:%SZ'
}

json_escape() {
  python3 -c 'import json,sys; print(json.dumps(sys.stdin.read().rstrip("\n")))'
}
