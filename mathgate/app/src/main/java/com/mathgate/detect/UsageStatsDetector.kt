package com.mathgate.detect

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Fallback detector that polls [UsageStatsManager] every few seconds (phase 3, D-02).
 *
 * Requires the "Usage access" permission; covers the case when the accessibility service
 * is off or missed a window event. Classification is shared with the primary detector
 * via [ForegroundFilter].
 */
class UsageStatsDetector(
    private val usageStatsManager: UsageStatsManager,
    private val ignoredPackages: Set<String> = ForegroundFilter.DEFAULT_IGNORED_PACKAGES,
    /** How far back to look for the most recent foreground event (wall-clock ms). */
    private val lookbackMs: Long = DEFAULT_LOOKBACK_MS,
) : ForegroundDetector {

    private val _watchedForeground = MutableStateFlow(false)
    override val watchedForeground: StateFlow<Boolean> = _watchedForeground.asStateFlow()

    /** Queries recent usage events and updates [watchedForeground]. */
    fun poll(watchedPackages: Set<String>) {
        val lastPackage = lastForegroundPackage() ?: return
        when (ForegroundFilter.decide(lastPackage, watchedPackages, ignoredPackages)) {
            ForegroundDecision.IGNORE -> Unit
            ForegroundDecision.WATCHED -> _watchedForeground.value = true
            ForegroundDecision.NOT_WATCHED -> _watchedForeground.value = false
        }
    }

    /** Package of the most recent "activity resumed" event, or `null` if none/denied. */
    private fun lastForegroundPackage(): String? {
        // UsageStatsManager works with wall-clock time, not elapsedRealtime.
        val now = System.currentTimeMillis()
        val events = usageStatsManager.queryEvents(now - lookbackMs, now) ?: return null
        var last: String? = null
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                last = event.packageName
            }
        }
        return last
    }

    companion object {
        /** Poll period used by the hosting service; query window is wider to catch the last event. */
        const val DEFAULT_LOOKBACK_MS: Long = 10_000L
    }
}
