package com.mathgate.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mathgate.core.GateState
import com.mathgate.core.Problem
import com.mathgate.core.Settings
import kotlinx.coroutines.flow.first

private const val DATASTORE_NAME = "mathgate"

private const val STATE_IDLE = 0
private const val STATE_COUNTING = 1
private const val STATE_CHALLENGE_PENDING = 2

private val Context.gateDataStore by preferencesDataStore(name = DATASTORE_NAME)

/**
 * Preferences-DataStore implementation of [GateStore] (D-05).
 * Every write is awaited, so a critical transition is on disk before the engine moves on.
 */
class DataStoreGateStore(private val context: Context) : GateStore {

    override suspend fun readState(): PersistedState {
        val prefs = context.gateDataStore.data.first()
        val accumulated = prefs[KEY_ACCUMULATED] ?: 0L
        val gateState = when (prefs[KEY_STATE_TYPE] ?: STATE_IDLE) {
            STATE_COUNTING -> GateState.Counting(
                accumulatedMs = accumulated,
                segmentStartElapsed = prefs[KEY_SEGMENT_START] ?: accumulated,
            )

            STATE_CHALLENGE_PENDING -> GateState.ChallengePending(
                problem = Problem(
                    text = prefs[KEY_PROBLEM_TEXT].orEmpty(),
                    answer = prefs[KEY_PROBLEM_ANSWER] ?: 0,
                ),
                attempts = prefs[KEY_ATTEMPTS] ?: 0,
                cooldownUntilElapsedMs = prefs[KEY_COOLDOWN_UNTIL] ?: 0L,
            )

            else -> GateState.Idle(accumulated)
        }
        return PersistedState(gateState, prefs[KEY_LAST_SEGMENT_MARKER] ?: 0L, prefs[KEY_STATE_BOOT_COUNT] ?: 0)
    }

    override suspend fun writeState(state: PersistedState) {
        context.gateDataStore.edit { prefs ->
            prefs[KEY_LAST_SEGMENT_MARKER] = state.lastSegmentMarker
            prefs[KEY_STATE_BOOT_COUNT] = state.bootCount
            when (val gate = state.gateState) {
                is GateState.Idle -> {
                    prefs[KEY_STATE_TYPE] = STATE_IDLE
                    prefs[KEY_ACCUMULATED] = gate.accumulatedMs
                }

                is GateState.Counting -> {
                    prefs[KEY_STATE_TYPE] = STATE_COUNTING
                    prefs[KEY_ACCUMULATED] = gate.accumulatedMs
                    prefs[KEY_SEGMENT_START] = gate.segmentStartElapsed
                }

                is GateState.ChallengePending -> {
                    prefs[KEY_STATE_TYPE] = STATE_CHALLENGE_PENDING
                    prefs[KEY_PROBLEM_TEXT] = gate.problem.text
                    prefs[KEY_PROBLEM_ANSWER] = gate.problem.answer
                    prefs[KEY_ATTEMPTS] = gate.attempts
                    prefs[KEY_COOLDOWN_UNTIL] = gate.cooldownUntilElapsedMs
                }
            }
        }
    }

    override suspend fun readSettings(): Settings {
        val prefs = context.gateDataStore.data.first()
        return Settings(
            limitMs = prefs[KEY_LIMIT_MS] ?: Settings.DEFAULT_LIMIT_MS,
            difficultyLevel = prefs[KEY_DIFFICULTY] ?: 1,
            multipleChoice = prefs[KEY_MULTIPLE_CHOICE] ?: false,
            warnBeforeMs = prefs[KEY_WARN_BEFORE] ?: 60_000L,
            resetOnPowerLoss = prefs[KEY_RESET_ON_POWER_LOSS] ?: false,
            watchedPackages = prefs[KEY_WATCHED_PACKAGES]?.toList() ?: Settings.DEFAULT_WATCHED_PACKAGES,
        )
    }

    override suspend fun writeSettings(settings: Settings) {
        context.gateDataStore.edit { prefs ->
            prefs[KEY_LIMIT_MS] = settings.limitMs
            prefs[KEY_DIFFICULTY] = settings.difficultyLevel
            prefs[KEY_MULTIPLE_CHOICE] = settings.multipleChoice
            prefs[KEY_WARN_BEFORE] = settings.warnBeforeMs
            prefs[KEY_RESET_ON_POWER_LOSS] = settings.resetOnPowerLoss
            prefs[KEY_WATCHED_PACKAGES] = settings.watchedPackages.toSet()
        }
    }

    override suspend fun readBootInfo(): BootInfo {
        val prefs = context.gateDataStore.data.first()
        return BootInfo(
            bootCount = prefs[KEY_BOOT_COUNT] ?: -1,
            cleanShutdown = prefs[KEY_CLEAN_SHUTDOWN] ?: false,
        )
    }

    override suspend fun writeBootInfo(info: BootInfo) {
        context.gateDataStore.edit { prefs ->
            prefs[KEY_BOOT_COUNT] = info.bootCount
            prefs[KEY_CLEAN_SHUTDOWN] = info.cleanShutdown
        }
    }

    private companion object {

        val KEY_STATE_TYPE = intPreferencesKey("state_type")
        val KEY_ACCUMULATED = longPreferencesKey("accumulated_ms")
        val KEY_SEGMENT_START = longPreferencesKey("segment_start_elapsed")
        val KEY_PROBLEM_TEXT = stringPreferencesKey("problem_text")
        val KEY_PROBLEM_ANSWER = intPreferencesKey("problem_answer")
        val KEY_ATTEMPTS = intPreferencesKey("attempts")
        val KEY_COOLDOWN_UNTIL = longPreferencesKey("cooldown_until_elapsed")
        val KEY_LAST_SEGMENT_MARKER = longPreferencesKey("last_segment_marker")
        val KEY_STATE_BOOT_COUNT = intPreferencesKey("state_boot_count")

        val KEY_BOOT_COUNT = intPreferencesKey("boot_count")
        val KEY_CLEAN_SHUTDOWN = booleanPreferencesKey("clean_shutdown")

        val KEY_LIMIT_MS = longPreferencesKey("settings_limit_ms")
        val KEY_DIFFICULTY = intPreferencesKey("settings_difficulty")
        val KEY_MULTIPLE_CHOICE = booleanPreferencesKey("settings_multiple_choice")
        val KEY_WARN_BEFORE = longPreferencesKey("settings_warn_before_ms")
        val KEY_RESET_ON_POWER_LOSS = booleanPreferencesKey("settings_reset_on_power_loss")
        val KEY_WATCHED_PACKAGES = stringSetPreferencesKey("settings_watched_packages")
    }
}
