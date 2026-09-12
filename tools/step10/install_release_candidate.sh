#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/common.sh"
require_adb_device
require_cmd sha256sum
apk="${1:?Usage: $0 path/to/release.apk}"
test -f "$apk"
echo "Git SHA: $(repo_sha)"
sha256sum "$apk"
adb install -r "$apk"
echo "Installed $apk"
