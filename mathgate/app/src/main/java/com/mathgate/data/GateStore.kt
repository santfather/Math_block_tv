package com.mathgate.data

import com.mathgate.core.GateState
import com.mathgate.core.Settings

/**
 * Snapshot persisted across process death, reboots and power loss (D-04, D-05).
 */
data class PersistedState(
    val gateState: GateState,
    /** Monotonic marker of the last flushed segment, used to close it after a reboot. */
    val lastSegmentMarker: Long,
)

/**
 * Persistence boundary backed by Jetpack DataStore (phase 2).
 * Critical transitions are written synchronously (D-05).
 */
interface GateStore {

    suspend fun readState(): PersistedState

    suspend fun writeState(state: PersistedState)

    suspend fun readSettings(): Settings

    suspend fun writeSettings(settings: Settings)
}
