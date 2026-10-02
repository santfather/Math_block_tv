package com.mathgate.service

/**
 * Watches `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` via a ContentObserver and
 * re-enables our service if it was switched off (D-08, phase 6).
 * Requires `WRITE_SECURE_SETTINGS`, granted once over adb.
 */
object SelfHealer {

    fun ensureAccessibilityEnabled() {
        TODO("implemented in phase 6")
    }
}
