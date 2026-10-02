package com.mathgate.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.mathgate.MathGateApp
import com.mathgate.core.EventLog
import com.mathgate.detect.A11yForegroundDetector
import com.mathgate.detect.DetectorHeartbeat

/**
 * Primary foreground detector and enforcement entry point (phases 3 and 5).
 *
 * Phase 5 spike: whether Android 12 allows starting an Activity from this context;
 * if not, the enforcement falls back to a `TYPE_ACCESSIBILITY_OVERLAY` window (D-11).
 */
class GuardAccessibilityService : AccessibilityService() {

    private lateinit var detector: A11yForegroundDetector
    private lateinit var eventLog: EventLog
    private lateinit var heartbeat: DetectorHeartbeat
    private lateinit var coordinator: GateCoordinator

    override fun onServiceConnected() {
        super.onServiceConnected()
        val app = application as MathGateApp
        detector = app.a11yForegroundDetector
        eventLog = app.eventLog
        heartbeat = app.a11yHeartbeat
        coordinator = app.gateCoordinator
        record("accessibility connected")
        coordinator.start()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!this::detector.isInitialized) return
        // Any event proves the accessibility channel is alive; the watchdog uses this (phase 6).
        heartbeat.mark()
        // Only a newly activated window tells us the real foreground app. `TYPE_WINDOWS_CHANGED`
        // also fires when a window is removed, which made the launcher look like it came back
        // right after YouTube was resumed.
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString()
        val before = detector.watchedForeground.value
        // Read the live list so a parent change applies without restarting the service (phase 7).
        detector.onPackageChanged(packageName, coordinator.watchedPackages.value)
        val after = detector.watchedForeground.value
        if (after != before) {
            record("foreground -> $after (package=$packageName)")
        }
    }

    override fun onInterrupt() {
        // No-op: interruptions must not change the gate state.
    }

    override fun onDestroy() {
        if (this::eventLog.isInitialized) record("accessibility destroyed")
        super.onDestroy()
    }

    private fun record(message: String) {
        Log.i(TAG, message)
        eventLog.record(TAG, message)
    }

    companion object {
        const val TAG = "MathGate"
    }
}
