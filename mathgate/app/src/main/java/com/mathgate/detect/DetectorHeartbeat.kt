package com.mathgate.detect

/**
 * Liveness marker for a detector (phase 6).
 *
 * The accessibility service calls [mark] on every window event; the watchdog in
 * `GuardForegroundService` uses [isStale] to decide whether it must fall back to
 * `UsageStatsManager` because the primary channel went quiet.
 *
 * Pure Kotlin (no `android.*`): the monotonic clock is injected, so this is unit-testable.
 */
class DetectorHeartbeat(private val elapsedRealtimeMs: () -> Long) {

    /** Time of the last event, or `0` when nothing has arrived yet. */
    @Volatile
    var lastEventMs: Long = 0L
        private set

    /** Records that an event just arrived. */
    fun mark() {
        lastEventMs = elapsedRealtimeMs()
    }

    /** True when no event arrived within [thresholdMs], including the "never" case. */
    fun isStale(now: Long, thresholdMs: Long): Boolean =
        lastEventMs == 0L || now - lastEventMs > thresholdMs
}
