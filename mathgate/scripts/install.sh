#!/usr/bin/env bash
# Builds, installs and configures Math Gate on the TV over adb (phase 10).
# Usage: scripts/install.sh [adb-serial]
set -euo pipefail

cd "$(dirname "$0")/.."

PKG="com.mathgate"
A11Y_COMPONENT="$PKG/com.mathgate.service.GuardAccessibilityService"
SERIAL="${1:-}"

ADB=(adb)
if [[ -n "$SERIAL" ]]; then
  ADB=(adb -s "$SERIAL")
fi

echo "==> Building debug APK"
./gradlew assembleDebug

APK="app/build/outputs/apk/debug/app-debug.apk"

echo "==> Installing $APK"
"${ADB[@]}" install -r "$APK"

echo "==> Granting WRITE_SECURE_SETTINGS (D-08, needed by SelfHealer)"
"${ADB[@]}" shell pm grant "$PKG" android.permission.WRITE_SECURE_SETTINGS || \
  echo "    (failed; grant it manually if SelfHealer must work)"

echo "==> Enabling the accessibility service"
"${ADB[@]}" shell settings put secure enabled_accessibility_services "$A11Y_COMPONENT" || true
"${ADB[@]}" shell settings put secure accessibility_enabled 1 || true

echo "==> Launching the setup wizard"
"${ADB[@]}" shell am start -n "$PKG/.ui.SetupActivity" || true

echo "Done."
