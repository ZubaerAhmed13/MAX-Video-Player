#!/bin/sh
set -eu

REPORT_DIR="app/build/reports/step9-instrumentation"
mkdir -p "$REPORT_DIR"

TEST_CLASSES="
com.zubaer.maxvideoplayer.feature.privatevault.PrivateVaultIntegrationTest
com.zubaer.maxvideoplayer.core.database.Step9DatabaseMigrationTest
com.zubaer.maxvideoplayer.feature.settings.Step9SettingsPrivacyInstrumentedTest
com.zubaer.maxvideoplayer.feature.sleeptimer.Step9SleepTimerInstrumentedTest
com.zubaer.maxvideoplayer.feature.privatevault.Step9PrivateSurfaceInstrumentedTest
com.zubaer.maxvideoplayer.feature.settings.Step9AccessibilityInstrumentedTest
"

for test_class in $TEST_CLASSES; do
    safe_name=$(printf '%s' "$test_class" | tr '.' '_')
    output="$REPORT_DIR/$safe_name.txt"
    echo "=== Running $test_class ==="
    adb shell am instrument -w -r -e class "$test_class" \
        com.zubaer.maxvideoplayer.test/androidx.test.runner.AndroidJUnitRunner >"$output"
    cat "$output"
    if grep -q 'FAILURES' "$output"; then
        echo "Step 9 instrumentation reported failures: $test_class" >&2
        exit 1
    fi
    if ! grep -Eq '^OK \([1-9][0-9]* tests?\)$' "$output"; then
        echo "Step 9 certification failed or executed zero tests: $test_class" >&2
        exit 1
    fi
done

echo "All required Step 9 instrumentation classes passed."
