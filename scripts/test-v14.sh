#!/usr/bin/env bash
set -euo pipefail
mkdir -p dist/checks/screenshots
export ANDROID_USER_HOME="$HOME/.android"
export ANDROID_EMULATOR_HOME="$ANDROID_USER_HOME"
export ANDROID_AVD_HOME="$ANDROID_USER_HOME/avd"
unset ANDROID_SDK_HOME
mkdir -p "$ANDROID_AVD_HOME"
SDKMANAGER="$(find "$ANDROID_HOME/cmdline-tools" -path '*/bin/sdkmanager' | sort -V | tail -1)"
AVDMANAGER="$(dirname "$SDKMANAGER")/avdmanager"
"$SDKMANAGER" 'system-images;android-36;google_apis;x86_64' 'emulator' 'platform-tools'
"$AVDMANAGER" create avd --force --name salarytest --device pixel --path "$ANDROID_AVD_HOME/salarytest.avd" --package 'system-images;android-36;google_apis;x86_64' <<< 'no'
printf 'avd.ini.encoding=UTF-8\npath=%s\npath.rel=avd/salarytest.avd\ntarget=android-36\n' "$ANDROID_AVD_HOME/salarytest.avd" > "$ANDROID_AVD_HOME/salarytest.ini"
if [[ -e /dev/kvm ]]; then sudo chmod 666 /dev/kvm; fi
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
emulator -avd salarytest -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader -memory 2048 -cores 2 > dist/checks/emulator.log 2>&1 &
EMULATOR_PID=$!
trap 'adb emu kill >/dev/null 2>&1 || true' EXIT
sleep 3
if ! kill -0 "$EMULATOR_PID" 2>/dev/null; then cat dist/checks/emulator.log; exit 1; fi
timeout 180 adb wait-for-device
for i in $(seq 1 120); do
  if [[ "$(adb shell getprop sys.boot_completed | tr -d '\r')" == '1' ]]; then break; fi
  sleep 2
done
[[ "$(adb shell getprop sys.boot_completed | tr -d '\r')" == '1' ]]
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
adb shell cmd alarm set-timezone Asia/Seoul || true
adb shell input keyevent 82
adb shell getprop ro.build.version.release > dist/checks/android-version.txt
adb install --no-streaming app/build/outputs/apk/debug/app-debug.apk
adb install --no-streaming app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
timeout 300 adb shell am instrument -w -r com.livingmetal.salarytimer.personal.debug.test/androidx.test.runner.AndroidJUnitRunner | tee dist/checks/instrumentation.txt
adb pull /sdcard/Android/data/com.livingmetal.salarytimer.personal.debug/files/screenshots/. dist/checks/screenshots/ || true
adb logcat -d -b crash > dist/checks/crash-log.txt
grep -q 'OK (6 tests)' dist/checks/instrumentation.txt
if grep -q 'FAILURES!!!\|INSTRUMENTATION_FAILED\|shortMsg=Process crashed' dist/checks/instrumentation.txt; then exit 1; fi
printf '%s\n' 'PASS: Android 16 UI, dark theme, weekend entry, bonus entry, yearly view, settings migration, persistence, optional calendar and Calendar Provider.' > dist/checks/RESULT.txt
