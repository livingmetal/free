#!/usr/bin/env bash
set -euo pipefail
mkdir -p dist/checks
SDKMANAGER="$(find "$ANDROID_HOME/cmdline-tools" -path '*/bin/sdkmanager' | sort -V | tail -1)"
AVDMANAGER="$(dirname "$SDKMANAGER")/avdmanager"
"$SDKMANAGER" 'system-images;android-36;google_apis;x86_64' 'emulator' 'platform-tools'
echo no | "$AVDMANAGER" create avd --force --name salarytest --package 'system-images;android-36;google_apis;x86_64'
if [[ -e /dev/kvm ]]; then sudo chmod 666 /dev/kvm; fi
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
emulator -avd salarytest -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader_indirect -memory 2048 -cores 2 > dist/checks/emulator.log 2>&1 &
trap 'adb emu kill >/dev/null 2>&1 || true' EXIT
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
adb shell getprop ro.build.version.release > dist/checks/android-version.txt
# Reproduce the old debug-signature problem, then leave the old app installed.
adb install --no-streaming old/v1/app-debug.apk | tee dist/checks/old-install.txt
set +e
adb install -r --no-streaming old/v12/app-debug.apk > dist/checks/old-update.txt 2>&1
OLD_STATUS=$?
set -e
cat dist/checks/old-update.txt
[[ "$OLD_STATUS" -ne 0 ]]
grep -q INSTALL_FAILED_UPDATE_INCOMPATIBLE dist/checks/old-update.txt
# Install the corrected release alongside the old app, without deleting user data.
adb install --no-streaming dist/SalaryTimer-v1.3.0.apk | tee dist/checks/new-install.txt
grep -q Success dist/checks/new-install.txt
APP=com.livingmetal.salarytimer.personal
COMPONENT="$APP/com.livingmetal.salarytimer.MainActivity"
adb shell am start -W -n "$COMPONENT" | tee dist/checks/launch.txt
sleep 4
adb shell pidof "$APP" > dist/checks/app-pid.txt
adb exec-out screencap -p > dist/checks/today.png
adb shell uiautomator dump /sdcard/window.xml
adb pull /sdcard/window.xml dist/checks/today.xml
python3 scripts/tap-text.py dist/checks/today.xml '이번 달'
sleep 2
adb exec-out screencap -p > dist/checks/monthly.png
adb shell uiautomator dump /sdcard/window.xml
adb pull /sdcard/window.xml dist/checks/monthly.xml
python3 - <<'PY'
import xml.etree.ElementTree as E
texts = [n.attrib.get('text','') for n in E.parse('dist/checks/monthly.xml').iter('node')]
assert any('이번 달 쌓인 급여' in s for s in texts), texts
print('Monthly salary screen verified')
PY
# Verify update/reinstallation with this release's retained signing identity.
adb install -r --no-streaming dist/SalaryTimer-v1.3.0.apk | tee dist/checks/new-update.txt
grep -q Success dist/checks/new-update.txt
adb shell am start -W -n "$COMPONENT" > dist/checks/relaunch.txt
sleep 2
adb shell pidof "$APP" >> dist/checks/app-pid.txt
adb logcat -d -b crash > dist/checks/crash-log.txt
if grep -q "Process: $APP," dist/checks/crash-log.txt; then cat dist/checks/crash-log.txt; exit 1; fi
printf '%s\n' 'PASS: old-signature rejection reproduced; corrected release installs alongside old app; launch; monthly tab; same-signer reinstall.' | tee dist/checks/RESULT.txt
