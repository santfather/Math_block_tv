#!/usr/bin/env bash
# Builds, installs and configures Math Gate on the TV over adb (phase 10).
#
# Usage:
#   scripts/install.sh [--release|--debug] [adb-serial]
#
# Default is --release (signed APK from `gradlew assembleRelease`; needs keystore.properties,
# see docs/SIGNING.md). Use --debug for local development.
set -euo pipefail

cd "$(dirname "$0")/.."

BUILD_TYPE="release"
SERIAL=""
for arg in "$@"; do
  case "$arg" in
    --release) BUILD_TYPE="release" ;;
    --debug) BUILD_TYPE="debug" ;;
    *) SERIAL="$arg" ;;
  esac
done

PKG="com.mathgate"
A11Y_COMPONENT="$PKG/com.mathgate.service.GuardAccessibilityService"

ADB=(adb)
if [[ -n "$SERIAL" ]]; then
  ADB=(adb -s "$SERIAL")
fi

if [[ "$BUILD_TYPE" == "release" ]]; then
  APK="app/build/outputs/apk/release/app-release.apk"
  echo "==> Building signed release APK"
  ./gradlew assembleRelease
else
  APK="app/build/outputs/apk/debug/app-debug.apk"
  echo "==> Building debug APK"
  ./gradlew assembleDebug
fi

echo "==> Installing $APK (-r keeps existing data)"
"${ADB[@]}" install -r "$APK"

echo "==> Granting WRITE_SECURE_SETTINGS (D-08, needed by SelfHealer)"
"${ADB[@]}" shell pm grant "$PKG" android.permission.WRITE_SECURE_SETTINGS || \
  echo "    (failed; grant it manually if SelfHealer must work)"

echo "==> Granting Usage access (fallback detector, D-02)"
"${ADB[@]}" shell appops set "$PKG" GET_USAGE_STATS allow || \
  echo "    (failed; grant it manually in Settings > Apps > Special access > Usage access)"

echo "==> Enabling the accessibility service"
"${ADB[@]}" shell settings put secure enabled_accessibility_services "$A11Y_COMPONENT" || true
"${ADB[@]}" shell settings put secure accessibility_enabled 1 || true

echo "==> Launching the setup wizard"
"${ADB[@]}" shell am start -n "$PKG/.ui.SetupActivity" || true

echo "Done. Run scripts/check-device.sh to verify the state."
