#!/usr/bin/env bash
# Prints the device facts needed by phase 0 / DEVICE_NOTES.md.
# Usage: scripts/check-device.sh [adb-serial]
set -euo pipefail

PKG="com.mathgate"
SERIAL="${1:-}"

ADB=(adb)
if [[ -n "$SERIAL" ]]; then
  ADB=(adb -s "$SERIAL")
fi

echo "==> Device"
"${ADB[@]}" shell getprop ro.product.model
"${ADB[@]}" shell getprop ro.build.version.release
"${ADB[@]}" shell getprop ro.build.version.sdk
"${ADB[@]}" shell getprop ro.build.fingerprint
"${ADB[@]}" shell getprop ro.build.characteristics

echo "==> YouTube / browser packages"
"${ADB[@]}" shell pm list packages | grep -i youtube || true
"${ADB[@]}" shell pm list packages | grep -iE "browser|chrome|smarttube" || true

echo "==> Current foreground window (open YouTube first)"
"${ADB[@]}" shell dumpsys window | grep -E "mCurrentFocus|mFocusedApp" || true

echo "==> Accessibility state"
"${ADB[@]}" shell settings get secure enabled_accessibility_services || true
"${ADB[@]}" shell settings get secure accessibility_enabled || true

echo "==> Math Gate installed?"
"${ADB[@]}" shell pm list packages | grep "$PKG" || echo "    not installed"
