package com.mathgate.core

/**
 * Abstraction over monotonic time so `core/` stays free of `android.*` and
 * unit-testable on the JVM (roadmap rule 2, decision D-03).
 */
interface Clock {

    /** Monotonic milliseconds since boot (`SystemClock.elapsedRealtime()`). */
    fun elapsedRealtimeMs(): Long

    /** Global boot counter (`Settings.Global.BOOT_COUNT`), used by D-04. */
    fun bootCount(): Int
}
