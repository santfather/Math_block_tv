package com.mathgate.service

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.mathgate.core.Clock

/** Android implementation of [Clock] (D-03). Kept out of `core/`, which stays pure Kotlin. */
class AndroidClock(private val context: Context) : Clock {

    override fun elapsedRealtimeMs(): Long = SystemClock.elapsedRealtime()

    override fun bootCount(): Int =
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, UNKNOWN_BOOT_COUNT)

    private companion object {
        const val UNKNOWN_BOOT_COUNT = -1
    }
}
