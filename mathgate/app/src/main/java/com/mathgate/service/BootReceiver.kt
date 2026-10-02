package com.mathgate.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import com.mathgate.MathGateApp
import com.mathgate.core.GateState
import com.mathgate.data.BootInfo
import com.mathgate.data.PersistedState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Restores the guard after a reboot (`BOOT_COMPLETED`, D-07, phase 6) and evaluates the
 * `resetOnPowerLoss` heuristic (D-04, rule 7): if no clean `ACTION_SHUTDOWN` was recorded before
 * the device went down, an unclean halt (unplugged TV) is assumed and the state is dropped.
 *
 * `LOCKED_BOOT_COMPLETED` is declared in the manifest but only delivered when the app is
 * direct-boot aware and stores in device-protected storage; a TV has no lock screen, so
 * `BOOT_COMPLETED` is what actually arrives (see DEVICE_NOTES.md).
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        val ctx = context?.applicationContext ?: return
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_LOCKED_BOOT_COMPLETED) return

        val app = ctx as? MathGateApp
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val bootCount = currentBootCount(ctx)
                if (app != null) {
                    val settings = app.gateStore.readSettings()
                    val bootInfo = app.gateStore.readBootInfo()
                    if (settings.resetOnPowerLoss && !bootInfo.cleanShutdown) {
                        app.gateStore.writeState(PersistedState(GateState.Idle(0L), 0L, bootCount))
                        record(app, "power loss detected: gate state reset")
                    }
                    // Arm the marker: the next boot without a shutdown means another power loss.
                    app.gateStore.writeBootInfo(BootInfo(bootCount, cleanShutdown = false))
                }
                ContextCompat.startForegroundService(
                    ctx,
                    Intent(ctx, GuardForegroundService::class.java),
                )
                SelfHealer.ensureAccessibilityEnabled(ctx)
                record(app, "boot receiver: guard service started (boot=$bootCount)")
            } catch (t: Throwable) {
                record(app, "boot receiver failed: ${t.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun currentBootCount(context: Context): Int =
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, UNKNOWN_BOOT_COUNT)

    private fun record(app: MathGateApp?, message: String) {
        Log.i(GuardAccessibilityService.TAG, message)
        app?.eventLog?.record(GuardAccessibilityService.TAG, message)
    }

    private companion object {
        const val UNKNOWN_BOOT_COUNT = -1
    }
}
