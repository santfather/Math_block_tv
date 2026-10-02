package com.mathgate.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.mathgate.MathGateApp
import com.mathgate.core.EventLog
import com.mathgate.core.Settings
import com.mathgate.detect.A11yForegroundDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Primary foreground detector and enforcement entry point (phases 3 and 5).
 *
 * Phase 5 spike: whether Android 12 allows starting an Activity from this context;
 * if not, the enforcement falls back to a `TYPE_ACCESSIBILITY_OVERLAY` window (D-11).
 */
class GuardAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var detector: A11yForegroundDetector
    private lateinit var eventLog: EventLog

    /** Watched packages, refreshed from the store when the service connects. */
    @Volatile
    private var watchedPackages: Set<String> = Settings.DEFAULT_WATCHED_PACKAGES.toSet()

    override fun onServiceConnected() {
        super.onServiceConnected()
        val app = application as MathGateApp
        detector = app.a11yForegroundDetector
        eventLog = app.eventLog
        record("accessibility connected")
        app.gateCoordinator.start()
        scope.launch {
            watchedPackages = app.gateStore.readSettings().watchedPackages.toSet()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!this::detector.isInitialized) return
        // Only a newly activated window tells us the real foreground app. `TYPE_WINDOWS_CHANGED`
        // also fires when a window is removed, which made the launcher look like it came back
        // right after YouTube was resumed.
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString()
        val before = detector.watchedForeground.value
        detector.onPackageChanged(packageName, watchedPackages)
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
        scope.cancel()
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
