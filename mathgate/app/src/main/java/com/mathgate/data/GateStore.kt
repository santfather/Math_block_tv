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
    /** Boot counter at the time of the write; lets startup detect that the device rebooted (phase 6). */
    val bootCount: Int = 0,
)

/**
 * Session-survival markers for the `resetOnPowerLoss` heuristic (D-04, rule 7, phase 6).
 * The heuristic is deliberately simple and documented as unreliable on "fast start" TVs.
 */
data class BootInfo(
    /** `Settings.Global.BOOT_COUNT` observed at the previous boot. */
    val bootCount: Int,
    /** `true` only when a clean `ACTION_SHUTDOWN` was observed before the last power-off. */
    val cleanShutdown: Boolean,
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

    suspend fun readBootInfo(): BootInfo

    suspend fun writeBootInfo(info: BootInfo)
}
