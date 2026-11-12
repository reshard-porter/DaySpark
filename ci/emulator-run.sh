#!/usr/bin/env bash
# Runs the UI test phases on a booted emulator and collects screenshots, the demo recording
# and logs into ci-output/. Used by .github/workflows/android.yml; also works locally with
# one emulator running:  bash ci/emulator-run.sh
set -uo pipefail

APP_ID=com.dayspark.app
RUNNER=$APP_ID.test/androidx.test.runner.AndroidJUnitRunner
APK=app/build/outputs/apk/debug/app-debug.apk
TEST_APK=app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
OUT=ci-output
mkdir -p "$OUT/screenshots" "$OUT/video" "$OUT/logs"
FAILED=0

run_phase() { # name, test class
  local name=$1 cls=$2
  echo "::group::$name"
  adb shell am force-stop $APP_ID
  adb shell am instrument -w -r -e class "$APP_ID.$cls" $RUNNER | tee "$OUT/logs/$name.txt"
  if grep -qE "FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|shortMsg" "$OUT/logs/$name.txt" \
     || ! grep -q "^OK (" "$OUT/logs/$name.txt"; then
    echo "::error::$name failed"; FAILED=1
  fi
  echo "::endgroup::"
}

adb wait-for-device
adb shell settings put system font_scale 1.0
adb logcat -c
adb install -r -t "$APK"
adb install -r -t "$TEST_APK"

# Phase 1, recorded: habit flow + purchase flow on a fresh install
adb shell screenrecord --bit-rate 8000000 --time-limit 180 /sdcard/demo.mp4 &
REC=$!
sleep 1
run_phase 1-habit-and-purchase-flow A1_HabitAndPurchaseFlowTest
adb shell pkill -INT screenrecord || true
wait $REC 2>/dev/null || true
sleep 3
adb pull /sdcard/demo.mp4 "$OUT/video/demo-habit-and-purchase.mp4" || true

# Phase 2: install the same APK over itself (an app update), restart, check everything survived
adb install -r -t "$APK"
run_phase 2-after-update A2_AfterUpdateTest

# Phase 3: largest text size
adb shell settings put system font_scale 2.0
sleep 2
run_phase 3-large-text A3_LargeTextTest
adb shell settings put system font_scale 1.0

# Phase 4: automated accessibility checks
run_phase 4-accessibility A4_AccessibilityChecksTest

# Collect screenshots (external files dir; fall back to run-as)
adb pull /sdcard/Android/data/$APP_ID/files/screenshots/. "$OUT/screenshots/" \
  || adb exec-out run-as $APP_ID sh -c 'cd files/screenshots && tar cf - .' | tar xf - -C "$OUT/screenshots"
adb logcat -d > "$OUT/logs/logcat.txt"
ls -la "$OUT/screenshots" "$OUT/video"
exit $FAILED
