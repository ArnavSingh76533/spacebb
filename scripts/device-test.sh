#!/usr/bin/env bash
set -euo pipefail
mkdir -p device-results
adb install -r "$(find test-apks -name 'app-debug.apk' -print -quit)"
adb install -r "$(find test-apks -name '*androidTest.apk' -print -quit)"
adb shell am instrument -w com.space.browser.test/android.test.InstrumentationTestRunner | tee device-results/instrumentation.txt
adb logcat -d > device-results/logcat.txt
adb pull /sdcard/Android/data/com.space.browser/files/screenshots device-results/ || true
if ! grep -q 'OK (1 test)' device-results/instrumentation.txt; then
  echo 'Device smoke test failed'
  exit 1
fi
# Also check the minified, non-debuggable APK boots in a clean install.
adb uninstall com.space.browser
adb install release-apk/Space-Browser.apk
adb shell am start -W -n com.space.browser/.MainActivity > device-results/release-launch.txt
adb shell uiautomator dump /sdcard/space-release.xml
adb pull /sdcard/space-release.xml device-results/release-ui.xml
if ! grep -q 'Search or enter address' device-results/release-ui.xml; then
  adb logcat -d > device-results/release-logcat.txt
  echo 'Release browser home did not render'
  exit 1
fi
adb exec-out screencap -p > device-results/release-home.png
