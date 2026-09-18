#!/usr/bin/env bash
set -euo pipefail
mkdir -p dist/checks
BT="$(find "$ANDROID_HOME/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -1)"
"$BT/apksigner" verify --verbose --print-certs dist/SalaryTimer-v1.4.0.apk | tee dist/checks/signature.txt
"$BT/zipalign" -c -P 16 -v 4 dist/SalaryTimer-v1.4.0.apk > dist/checks/alignment.txt
"$BT/aapt2" dump badging dist/SalaryTimer-v1.4.0.apk > dist/checks/package.txt
"$BT/aapt2" dump permissions dist/SalaryTimer-v1.4.0.apk > dist/checks/permissions.txt
grep -q db0f4ce9e54b59070c34d9aa81767b5e228bc2661db05b5fdd975af97b60496c dist/checks/signature.txt
! grep -q android.permission.INTERNET dist/checks/permissions.txt
sha256sum dist/SalaryTimer-v1.4.0.apk > dist/checks/SHA256SUMS
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
adb shell input keyevent 82
APP=com.livingmetal.salarytimer.personal
COMPONENT="$APP/com.livingmetal.salarytimer.MainActivity"
dump_ui() { adb shell uiautomator dump /sdcard/window.xml >/dev/null; adb pull /sdcard/window.xml "$1" >/dev/null; }
# Persist settings through the actual previous non-debuggable release UI.
adb install --no-streaming old/SalaryTimer-v1.3.0.apk | tee dist/checks/old-install.txt
adb shell am start -W -n "$COMPONENT" > dist/checks/old-launch.txt
sleep 3
dump_ui dist/checks/old-home.xml
python3 scripts/tap-text.py dist/checks/old-home.xml '설정'
sleep 1
dump_ui dist/checks/old-settings.xml
python3 scripts/tap-text.py dist/checks/old-settings.xml '저장'
sleep 1
# The exact delivered release must upgrade over v1.3, not a debug surrogate.
adb install -r --no-streaming dist/SalaryTimer-v1.4.0.apk | tee dist/checks/upgrade.txt
grep -q Success dist/checks/upgrade.txt
adb shell am start -W -n "$COMPONENT" | tee dist/checks/launch.txt
sleep 3
adb shell pidof "$APP" > dist/checks/pid.txt
dump_ui dist/checks/new-home.xml
python3 scripts/tap-text.py dist/checks/new-home.xml '설정'
sleep 1
dump_ui dist/checks/new-settings.xml
python3 - <<'PY'
import xml.etree.ElementTree as E
texts=[n.attrib.get('text','') for n in E.parse('dist/checks/new-settings.xml').iter('node')]
assert '100000000' in texts, texts
print('Existing annual salary setting preserved through release upgrade.')
PY
python3 scripts/tap-text.py dist/checks/new-settings.xml '타이머'
sleep 1
dump_ui dist/checks/timer.xml
python3 scripts/tap-text.py dist/checks/timer.xml '다크'
sleep 1
adb exec-out screencap -p > dist/checks/release-dark.png
adb install -r --no-streaming dist/SalaryTimer-v1.4.0.apk | tee dist/checks/reinstall.txt
grep -q Success dist/checks/reinstall.txt
adb shell am start -W -n "$COMPONENT" > dist/checks/relaunch.txt
sleep 2
adb shell pidof "$APP" >> dist/checks/pid.txt
adb logcat -d -b crash > dist/checks/crash-log.txt
if grep -q "Process: $APP," dist/checks/crash-log.txt; then cat dist/checks/crash-log.txt; exit 1; fi
printf '%s\n' 'PASS: exact final signed v1.4.0 APK; signature; 16-KB alignment; v1.3 release upgrade; salary data retained; launch; dark screen; same-signer reinstall.' | tee dist/checks/RESULT.txt
