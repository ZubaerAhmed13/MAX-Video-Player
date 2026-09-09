#!/bin/sh
set -eu

REPORT_DIR="app/build/reports/step8-instrumentation"
mkdir -p "$REPORT_DIR"

TEST_CLASSES="
com.zubaer.maxvideoplayer.feature.cloud.Step8CloudProviderIntegrationTest
com.zubaer.maxvideoplayer.feature.cast.Step8CastRemoteUiInstrumentedTest
com.zubaer.maxvideoplayer.feature.usb.Step8UsbVirtualProviderTest
com.zubaer.maxvideoplayer.feature.tv.Step8TvFocusInstrumentedTest
com.zubaer.maxvideoplayer.feature.tv.Step8TvNoTouchPlaybackInstrumentedTest
com.zubaer.maxvideoplayer.feature.output.Step8ExternalDisplayInstrumentedTest
com.zubaer.maxvideoplayer.core.database.Step8DatabaseMigrationTest
"

for test_class in $TEST_CLASSES; do
    safe_name=$(printf '%s' "$test_class" | tr '.' '_')
    output="$REPORT_DIR/$safe_name.txt"
    echo "=== Running $test_class ==="
    adb shell am instrument -w -e class "$test_class" \
        com.zubaer.maxvideoplayer.test/androidx.test.runner.AndroidJUnitRunner >"$output"
    cat "$output"
    if ! grep -Eq '^OK \([1-9][0-9]* tests?\)$' "$output"; then
        echo "Step 8 certification failed or executed zero tests: $test_class" >&2
        exit 1
    fi
done

echo "All required Step 8 instrumentation classes passed."
