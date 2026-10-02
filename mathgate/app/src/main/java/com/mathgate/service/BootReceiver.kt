package com.mathgate.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Restores the guard after a reboot (`BOOT_COMPLETED` / `LOCKED_BOOT_COMPLETED`, D-07, phase 6).
 * Also the place to evaluate the `resetOnPowerLoss` heuristic (D-04).
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        TODO("implemented in phase 6")
    }
}
