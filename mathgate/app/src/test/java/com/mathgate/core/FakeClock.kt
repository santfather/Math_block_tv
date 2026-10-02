package com.mathgate.core

/**
 * Test double for [Clock].
 *
 * [wallTimeMs] exists only so tests can prove that wall-clock changes are irrelevant:
 * the engine never reads it (D-03).
 */
class FakeClock(
    var elapsedMs: Long = 0L,
    var bootCountValue: Int = 1,
) : Clock {

    var wallTimeMs: Long = 1_700_000_000_000L

    override fun elapsedRealtimeMs(): Long = elapsedMs

    override fun bootCount(): Int = bootCountValue

    fun advance(ms: Long) {
        elapsedMs += ms
    }
}
