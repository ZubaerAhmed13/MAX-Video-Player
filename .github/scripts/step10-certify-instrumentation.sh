#!/usr/bin/env bash
set -euo pipefail

PACKAGE="com.zubaer.maxvideoplayer.test"
RUNNER="androidx.test.runner.AndroidJUnitRunner"
OUT="app/build/reports/step10-instrumentation"
mkdir -p "$OUT"

classes=(
  com.zubaer.maxvideoplayer.playback.Step6DecoderIntegrationTest
  com.zubaer.maxvideoplayer.playback.Step6CoexistenceIntegrationTest
  com.zubaer.maxvideoplayer.feature.cast.Step8CastRemoteUiInstrumentedTest
  com.zubaer.maxvideoplayer.feature.tv.Step8TvNoTouchPlaybackInstrumentedTest
  com.zubaer.maxvideoplayer.feature.usb.Step8UsbVirtualProviderTest
  com.zubaer.maxvideoplayer.feature.privatevault.Step9VaultAuthInstrumentedTest
  com.zubaer.maxvideoplayer.feature.privatevault.Step9VaultImportInstrumentedTest
  com.zubaer.maxvideoplayer.feature.privatevault.Step9PrivatePlaybackCoexistenceInstrumentedTest
  com.zubaer.maxvideoplayer.feature.settings.Step9SettingsPrivacyInstrumentedTest
  com.zubaer.maxvideoplayer.feature.settings.Step9AccessibilityInstrumentedTest
  com.zubaer.maxvideoplayer.feature.sleeptimer.Step9SleepTimerInstrumentedTest
)

for klass in "${classes[@]}"; do
  safe="${klass//./_}"
  log="$OUT/$safe.txt"
  echo "=== $klass ==="
  set +e
  adb shell am instrument -w -r -e class "$klass" "$PACKAGE/$RUNNER" | tee "$log"
  rc=${PIPESTATUS[0]}
  set -e
  test "$rc" -eq 0
  grep -q 'INSTRUMENTATION_CODE: -1' "$log"
  grep -Eq '^OK \([1-9][0-9]* tests?\)' "$log"
done

if grep -RInE 'org\.junit\.Assume|Assume\.assume|assumeTrue\(|assumeFalse\(' app/src/androidTest; then
  echo 'Step 10 forbids Assume/assumption-based skipping in retained instrumentation.' >&2
  exit 1
fi

echo "STEP10_STRICT_INSTRUMENTATION_PASS"
