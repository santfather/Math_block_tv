package com.mathgate.core

/** Input events for the state machine (roadmap section 4). */
sealed interface GateEvent {
    data object YouTubeForeground : GateEvent
    data object YouTubeBackground : GateEvent
    data object ScreenOff : GateEvent
    data object ScreenOn : GateEvent
    data object Tick : GateEvent
    data class AnswerSubmitted(val value: Int) : GateEvent
    data object ParentOverride : GateEvent

    /** Parent action: zero the accumulated counter (phase 7). */
    data object ResetTimer : GateEvent
    data object Reboot : GateEvent
}

/** Side effects the engine asks the platform layer to perform. */
sealed interface GateEffect {
    /** Flush the current state to disk (required on every critical transition). */
    data object PersistState : GateEffect
    /** Show the blocking challenge surface. */
    data object ShowChallenge : GateEffect
    /** Hide the blocking challenge surface. */
    data object HideChallenge : GateEffect
    /** Emitted once shortly before the limit so the child is not interrupted abruptly. */
    data object WarnAboutLimit : GateEffect
}

/**
 * Pure-Kotlin finite-state machine + time accounting (phase 2).
 *
 * The engine owns the current [GateState] and the environment flags
 * (watched app on the foreground / screen on). Each [onEvent] returns the new
 * state plus the [GateEffect]s the platform layer must execute.
 *
 * Time is measured exclusively with [Clock.elapsedRealtimeMs] so an intentional
 * wall-clock change cannot affect the counter (D-03).
 */
class GateEngine(
    private val clock: Clock,
    settings: Settings,
    initialState: GateState = GateState.Idle(0L),
    initialWatchedForeground: Boolean = initialState is GateState.Counting,
    initialScreenOn: Boolean = true,
    initialStats: UsageStats = UsageStats(),
    private val persistIntervalMs: Long = DEFAULT_PERSIST_INTERVAL_MS,
) {

    private var settings: Settings = settings
    private var state: GateState = initialState
    private var stats: UsageStats = initialStats
    private var watchedForeground: Boolean = initialWatchedForeground
    private var screenOn: Boolean = initialScreenOn
    private var lastPersistElapsed: Long = clock.elapsedRealtimeMs()

    /** Phase 5 rule: warn about the limit at most once per counting cycle. */
    private var limitWarned: Boolean = false

    val currentState: GateState get() = state
    val isWatchedForeground: Boolean get() = watchedForeground
    val isScreenOn: Boolean get() = screenOn

    /** Lifetime statistics, persisted alongside the gate state (phase 7). */
    val currentStats: UsageStats get() = stats

    /** Applies a parent change at runtime so it takes effect without a restart (phase 7 DoD). */
    fun updateSettings(settings: Settings) {
        this.settings = settings
    }

    fun onEvent(event: GateEvent): Pair<GateState, List<GateEffect>> {
        val now = clock.elapsedRealtimeMs()
        return when (event) {
            GateEvent.YouTubeForeground -> onWatchedForeground(now)
            GateEvent.YouTubeBackground -> onWatchedBackground(now)
            GateEvent.ScreenOff -> onScreenOff(now)
            GateEvent.ScreenOn -> onScreenOn(now)
            GateEvent.Tick -> onTick(now)
            is GateEvent.AnswerSubmitted -> onAnswer(event.value, now)
            GateEvent.ParentOverride -> onParentOverride(now)
            GateEvent.ResetTimer -> onResetTimer(now)
            GateEvent.Reboot -> onReboot(now)
        }
    }

    private fun onWatchedForeground(now: Long): Pair<GateState, List<GateEffect>> {
        watchedForeground = true
        return when (val current = state) {
            // Rule 3: while pending, YouTube on the foreground means the block comes back.
            is GateState.ChallengePending -> current to listOf(GateEffect.ShowChallenge)
            is GateState.Counting -> current to emptyList()
            is GateState.Idle -> resumeOrChallenge(now, current.accumulatedMs)
        }
    }

    private fun onWatchedBackground(now: Long): Pair<GateState, List<GateEffect>> {
        watchedForeground = false
        val current = state
        if (current !is GateState.Counting) return current to emptyList()
        return closeSegment(current, now)
    }

    private fun onScreenOff(now: Long): Pair<GateState, List<GateEffect>> {
        screenOn = false
        val current = state
        if (current !is GateState.Counting) return current to emptyList()
        return closeSegment(current, now)
    }

    private fun onScreenOn(now: Long): Pair<GateState, List<GateEffect>> {
        screenOn = true
        return when (val current = state) {
            is GateState.ChallengePending -> current to listOf(GateEffect.ShowChallenge)
            is GateState.Idle ->
                if (watchedForeground) resumeOrChallenge(now, current.accumulatedMs) else current to emptyList()

            is GateState.Counting -> current to emptyList()
        }
    }

    private fun onTick(now: Long): Pair<GateState, List<GateEffect>> {
        val current = state
        if (current !is GateState.Counting) return current to emptyList()

        val delta = elapsedSince(current.segmentStartElapsed, now)
        stats = stats.copy(totalWatchedMs = stats.totalWatchedMs + delta)
        val accumulated = current.accumulatedMs + delta
        if (accumulated >= settings.limitMs) return enterChallenge(now)

        state = GateState.Counting(accumulated, now)
        val effects = mutableListOf<GateEffect>()
        // Warn once when the remaining time drops below warnBeforeMs (0 disables the warning).
        if (!limitWarned && settings.warnBeforeMs > 0 &&
            accumulated >= settings.limitMs - settings.warnBeforeMs
        ) {
            limitWarned = true
            effects += GateEffect.WarnAboutLimit
        }
        // Flush at most once per persistIntervalMs; every state change is flushed separately.
        if (now - lastPersistElapsed >= persistIntervalMs) {
            lastPersistElapsed = now
            effects += GateEffect.PersistState
        }
        return state to effects
    }

    private fun onAnswer(value: Int, now: Long): Pair<GateState, List<GateEffect>> {
        val current = state as? GateState.ChallengePending ?: return state to emptyList()
        // Rule 4: submissions during the anti-brute-force pause are ignored.
        if (now < current.cooldownUntilElapsedMs) return current to emptyList()

        if (value == current.problem.answer) {
            // Rule 5: correct answer resets the counter and resumes by current environment.
            state = if (watchedForeground && screenOn) {
                GateState.Counting(0L, now)
            } else {
                GateState.Idle(0L)
            }
            lastPersistElapsed = now
            return state to listOf(GateEffect.HideChallenge, GateEffect.PersistState)
        }

        val attempts = current.attempts + 1
        stats = stats.copy(wrongAnswers = stats.wrongAnswers + 1)
        state = GateState.ChallengePending(
            problem = ProblemGenerator.generate(settings.difficultyLevel, now + attempts, current.problem),
            attempts = attempts,
            cooldownUntilElapsedMs = now + cooldownFor(attempts),
        )
        return persisted(now)
    }

    private fun onParentOverride(now: Long): Pair<GateState, List<GateEffect>> {
        val current = state
        if (current !is GateState.ChallengePending) return current to emptyList()
        state = if (watchedForeground && screenOn) {
            GateState.Counting(0L, now)
        } else {
            GateState.Idle(0L)
        }
        lastPersistElapsed = now
        return state to listOf(GateEffect.HideChallenge, GateEffect.PersistState)
    }

    /** Parent "reset timer": zero the counter; an open challenge is dismissed at the same time. */
    private fun onResetTimer(now: Long): Pair<GateState, List<GateEffect>> {
        val current = state
        val effects = mutableListOf<GateEffect>()
        if (current is GateState.ChallengePending) effects += GateEffect.HideChallenge
        state = if (watchedForeground && screenOn) {
            GateState.Counting(0L, now)
        } else {
            GateState.Idle(0L)
        }
        lastPersistElapsed = now
        limitWarned = false
        effects += GateEffect.PersistState
        return state to effects
    }

    private fun onReboot(now: Long): Pair<GateState, List<GateEffect>> {
        // Rule 6: elapsedRealtime restarted. Only the unflushed remainder (<= persistIntervalMs)
        // is lost; accumulatedMs is already on disk. A pending challenge survives the reboot.
        watchedForeground = false
        screenOn = true
        state = when (val current = state) {
            is GateState.Counting -> GateState.Idle(current.accumulatedMs)
            // Elapsed times are meaningless after a reboot: drop the cooldown, keep attempts.
            is GateState.ChallengePending -> current.copy(cooldownUntilElapsedMs = 0L)
            is GateState.Idle -> current
        }
        lastPersistElapsed = now
        return state to listOf(GateEffect.PersistState)
    }

    private fun closeSegment(current: GateState.Counting, now: Long): Pair<GateState, List<GateEffect>> {
        val delta = elapsedSince(current.segmentStartElapsed, now)
        stats = stats.copy(totalWatchedMs = stats.totalWatchedMs + delta)
        state = GateState.Idle(current.accumulatedMs + delta)
        return persisted(now)
    }

    private fun resumeOrChallenge(now: Long, accumulatedMs: Long): Pair<GateState, List<GateEffect>> {
        if (accumulatedMs >= settings.limitMs) return enterChallenge(now)
        state = GateState.Counting(accumulatedMs, now)
        return persisted(now)
    }

    private fun enterChallenge(now: Long): Pair<GateState, List<GateEffect>> {
        stats = stats.copy(blockedCount = stats.blockedCount + 1)
        state = GateState.ChallengePending(
            problem = ProblemGenerator.generate(settings.difficultyLevel, now),
            attempts = 0,
            cooldownUntilElapsedMs = 0L,
        )
        lastPersistElapsed = now
        // A fresh counter after the challenge lets the next cycle warn again.
        limitWarned = false
        return state to listOf(GateEffect.PersistState, GateEffect.ShowChallenge)
    }

    private fun persisted(now: Long): Pair<GateState, List<GateEffect>> {
        lastPersistElapsed = now
        return state to listOf(GateEffect.PersistState)
    }

    private fun elapsedSince(startElapsed: Long, now: Long): Long = (now - startElapsed).coerceAtLeast(0L)

    private fun cooldownFor(attempts: Int): Long =
        if (attempts <= FAILURES_BEFORE_GROWTH) {
            BASE_COOLDOWN_MS
        } else {
            minOf(BASE_COOLDOWN_MS shl (attempts - FAILURES_BEFORE_GROWTH), MAX_COOLDOWN_MS)
        }

    companion object {
        /** Roadmap rule 1: flush at least every 5-10 seconds while accumulating. */
        const val DEFAULT_PERSIST_INTERVAL_MS: Long = 5_000L

        private const val BASE_COOLDOWN_MS: Long = 3_000L
        private const val MAX_COOLDOWN_MS: Long = 30_000L
        private const val FAILURES_BEFORE_GROWTH = 3
    }
}
