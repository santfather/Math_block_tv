package com.mathgate.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.util.Log
import com.mathgate.core.Clock
import com.mathgate.core.EventLog
import com.mathgate.core.GateEffect
import com.mathgate.core.GateEngine
import com.mathgate.core.GateEvent
import com.mathgate.core.GateState
import com.mathgate.core.Settings
import com.mathgate.data.GateStore
import com.mathgate.data.PersistedState
import com.mathgate.detect.ForegroundDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Outcome of an answer submitted from the challenge screen. */
enum class AnswerResult { CORRECT, WRONG, IGNORED }

/**
 * Wires the detector, the pure [GateEngine] and the platform actions together (phase 5).
 *
 * It is the single owner of the engine in the process: the accessibility service feeds it
 * foreground changes, the keep-alive service provides screen on/off, and [com.mathgate.ui.BlockActivity]
 * submits answers and observes [gateState]. All side effects are drained through [ChallengeEnforcer].
 */
class GateCoordinator(
    private val context: Context,
    private val store: GateStore,
    private val clock: Clock,
    private val detector: ForegroundDetector,
    private val enforcer: ChallengeEnforcer,
    private val eventLog: EventLog,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _gateState = MutableStateFlow<GateState?>(null)

    /** Current engine state, or `null` until persisted state has been loaded. */
    val gateState: StateFlow<GateState?> = _gateState.asStateFlow()

    private var engine: GateEngine? = null
    private var settings: Settings = Settings()
    private var started = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> dispatch(GateEvent.ScreenOff)
                Intent.ACTION_SCREEN_ON -> dispatch(GateEvent.ScreenOn)
            }
        }
    }

    /** Idempotent; called by both services and by the challenge screen. */
    fun start() {
        if (started) return
        started = true
        context.registerReceiver(
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            },
        )
        scope.launch {
            settings = store.readSettings()
            val persisted = store.readState()
            // A different boot counter means the device rebooted: elapsed times from the
            // previous session are meaningless, so a pending cooldown must be dropped (rule 6).
            val rebooted = persisted.bootCount != clock.bootCount()
            engine = GateEngine(
                clock = clock,
                settings = settings,
                initialState = reconcile(persisted.gateState, rebooted),
                initialWatchedForeground = false,
                initialScreenOn = isInteractive(),
            )
            _gateState.value = engine?.currentState
            // Normalize the stored state and record the current boot counter.
            engine?.let { store.writeState(persistedState(it.currentState)) }
            // The detector may have settled before the engine was ready.
            if (detector.watchedForeground.value) dispatch(GateEvent.YouTubeForeground)
            launchTicker()
        }
        scope.launch {
            detector.watchedForeground.collect { watched ->
                dispatch(if (watched) GateEvent.YouTubeForeground else GateEvent.YouTubeBackground)
            }
        }
        record("coordinator started")
    }

    /** Settings observed at startup (used by the challenge screen for input mode and difficulty). */
    fun currentSettings(): Settings = settings

    /** Applies an answer from the challenge screen and reports how the engine reacted. */
    fun submitAnswer(value: Int): AnswerResult {
        val engine = engine ?: return AnswerResult.IGNORED
        val current = engine.currentState
        if (current !is GateState.ChallengePending) return AnswerResult.IGNORED
        if (clock.elapsedRealtimeMs() < current.cooldownUntilElapsedMs) return AnswerResult.IGNORED

        val (newState, effects) = engine.onEvent(GateEvent.AnswerSubmitted(value))
        _gateState.value = newState
        drainAsync(effects)
        return if (newState is GateState.ChallengePending) AnswerResult.WRONG else AnswerResult.CORRECT
    }

    /** Applies a foreground change discovered by the fallback watchdog (phase 6). */
    fun onFallbackForeground(watched: Boolean) {
        record("watchdog foreground -> $watched")
        dispatch(if (watched) GateEvent.YouTubeForeground else GateEvent.YouTubeBackground)
    }

    private fun dispatch(event: GateEvent) {
        val engine = engine ?: return
        val (newState, effects) = engine.onEvent(event)
        _gateState.value = newState
        drainAsync(effects)
    }

    private fun drainAsync(effects: List<GateEffect>) {
        if (effects.isEmpty()) return
        scope.launch { drain(effects) }
    }

    private suspend fun drain(effects: List<GateEffect>) {
        effects.forEach { effect ->
            when (effect) {
                GateEffect.PersistState -> store.writeState(
                    PersistedState(
                        gateState = engine?.currentState ?: return@forEach,
                        lastSegmentMarker = clock.elapsedRealtimeMs(),
                        bootCount = clock.bootCount(),
                    ),
                )

                GateEffect.ShowChallenge -> enforcer.showChallenge()
                GateEffect.HideChallenge -> enforcer.hideChallenge()
                GateEffect.WarnAboutLimit -> enforcer.warnAboutLimit()
            }
        }
    }

    private fun launchTicker() {
        scope.launch {
            while (isActive) {
                delay(TICK_INTERVAL_MS)
                dispatch(GateEvent.Tick)
            }
        }
    }

    /**
     * A segment cannot survive process death: a persisted [GateState.Counting] is restored as
     * [GateState.Idle] with the accumulated time, losing only the unflushed remainder (A12).
     * After a reboot the elapsed clock restarted, so a pending cooldown is dropped (rule 6).
     */
    private fun reconcile(state: GateState, rebooted: Boolean): GateState = when (state) {
        is GateState.Counting -> GateState.Idle(state.accumulatedMs)
        is GateState.ChallengePending -> if (rebooted) state.copy(cooldownUntilElapsedMs = 0L) else state
        is GateState.Idle -> state
    }

    private fun persistedState(state: GateState) =
        PersistedState(state, clock.elapsedRealtimeMs(), clock.bootCount())

    private fun isInteractive(): Boolean =
        (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive

    private fun record(message: String) {
        Log.i(GuardAccessibilityService.TAG, message)
        eventLog.record(GuardAccessibilityService.TAG, message)
    }

    private companion object {
        /** Roadmap phase 5: check the limit at least once per second while counting. */
        const val TICK_INTERVAL_MS: Long = 1_000L
    }
}
