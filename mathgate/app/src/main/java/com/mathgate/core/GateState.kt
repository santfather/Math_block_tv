package com.mathgate.core

/**
 * Finite-state machine states (roadmap section 4).
 *
 * `Unlocked` is intentionally not a state: it is the transition
 * `ChallengePending -> Idle(0)` / `Counting(0, ...)`.
 */
sealed interface GateState {

    /** YouTube is not on the foreground (or the screen is off); time is not accumulated. */
    data class Idle(val accumulatedMs: Long) : GateState

    /** YouTube is on the foreground; the current segment is being timed. */
    data class Counting(
        val accumulatedMs: Long,
        val segmentStartElapsed: Long,
    ) : GateState

    /** The limit was reached; waiting for the correct answer. */
    data class ChallengePending(
        val problem: Problem,
        val attempts: Int,
        /** Anti-brute-force pause: submissions are ignored before this elapsed time. */
        val cooldownUntilElapsedMs: Long = 0L,
    ) : GateState
}
