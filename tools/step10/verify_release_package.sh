#!/usr/bin/env bash
set -euo pipefail
root="${1:-app/build/outputs}"
out="step10-package-audit"
mkdir -p "$out"
mapfile -t apks < <(find "$root" -type f -name '*release*.apk' | sort)
mapfile -t aabs < <(find "$root" -type f -name '*release*.aab' | sort)
test "${#apks[@]}" -gt 0 || { echo "No release APK found under $root" >&2; exit 1; }
test "${#aabs[@]}" -gt 0 || { echo "No release AAB found under $root" >&2; exit 1; }

AAPT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}/build-tools/36.0.0/aapt"
test -x "$AAPT" || { echo "aapt not found at $AAPT" >&2; exit 2; }

: > "$out/sha256.txt"
sha256sum "${apks[@]}" "${aabs[@]}" | tee "$out/sha256.txt"

apk="${apks[0]}"
"$AAPT" dump badging "$apk" | tee "$out/apk-badging.txt"
"$AAPT" dump permissions "$apk" | tee "$out/apk-permissions.txt"
"$AAPT" dump xmltree "$apk" AndroidManifest.xml > "$out/apk-manifest-tree.txt"

grep -q "package: name='com.zubaer.maxvideoplayer'" "$out/apk-badging.txt"
if grep -q 'android:debuggable(0x0101000f)=(type 0x12)0xffffffff' "$out/apk-manifest-tree.txt"; then
  echo "Release APK is debuggable." >&2; exit 1
fi
if grep -q 'android:testOnly(0x01010272)=(type 0x12)0xffffffff' "$out/apk-manifest-tree.txt"; then
  echo "Release APK is testOnly." >&2; exit 1
fi

for artifact in "${apks[@]}" "${aabs[@]}"; do
  if unzip -p "$artifact" 2>/dev/null | strings | grep -E 'STEP10_SENTINEL_SECRET|BEGIN PRIVATE KEY|BEGIN RSA PRIVATE KEY' > "$out/secret-scan-hit.txt"; then
    echo "Release package contains forbidden secret sentinel/private-key material." >&2
    exit 1
  fi
done

echo "STEP10_PACKAGE_AUDIT_PASS" | tee "$out/result.txt"
