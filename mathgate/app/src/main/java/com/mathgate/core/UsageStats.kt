package com.mathgate.core

/**
 * Lifetime parental statistics (phase 7): how often the gate blocked, how many wrong answers
 * were given, and how long the watched apps were used in total.
 *
 * Accumulated in the pure [GateEngine] so the numbers are unit-testable and persisted together
 * with the gate state.
 */
data class UsageStats(
    val blockedCount: Int = 0,
    val wrongAnswers: Int = 0,
    val totalWatchedMs: Long = 0L,
)
