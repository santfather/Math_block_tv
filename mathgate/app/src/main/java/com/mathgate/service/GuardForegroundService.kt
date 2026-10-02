package com.mathgate.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.mathgate.MathGateApp
import com.mathgate.R
import com.mathgate.core.EventLog
import com.mathgate.core.Settings
import com.mathgate.detect.UsageStatsDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Keep-alive service hosting the fallback detector and screen-power listener
 * (phase 3; full self-healing lands in phase 6, decisions D-07 and D-11).
 */
class GuardForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var eventLog: EventLog
    private lateinit var usageStatsDetector: UsageStatsDetector

    @Volatile
    private var watchedPackages: Set<String> = Settings.DEFAULT_WATCHED_PACKAGES.toSet()

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> record("screen ON (interactive=true)")
                Intent.ACTION_SCREEN_OFF -> record("screen OFF (interactive=false)")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val app = application as MathGateApp
        eventLog = app.eventLog
        usageStatsDetector = UsageStatsDetector(
            getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager,
        )
        createNotificationChannel()
        registerReceiver(
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            },
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        record("guard service started (interactive=${isInteractive()})")
        val app = application as MathGateApp
        app.gateCoordinator.start()
        scope.launch {
            watchedPackages = app.gateStore.readSettings().watchedPackages.toSet()
        }
        startFallbackPolling()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        unregisterReceiver(screenReceiver)
        scope.cancel()
        record("guard service destroyed")
        super.onDestroy()
    }

    /** Polls UsageStats only while the primary accessibility detector is unavailable. */
    private fun startFallbackPolling() {
        scope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                if (isGuardAccessibilityEnabled()) continue
                val before = usageStatsDetector.watchedForeground.value
                usageStatsDetector.poll(watchedPackages)
                val after = usageStatsDetector.watchedForeground.value
                if (after != before) {
                    record("fallback foreground -> $after")
                }
            }
        }
    }

    private fun isGuardAccessibilityEnabled(): Boolean {
        val enabled = AndroidSettings.Secure.getString(
            contentResolver,
            AndroidSettings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val component = "$packageName/${GuardAccessibilityService::class.java.name}"
        return enabled.split(':').any { it.equals(component, ignoreCase = true) }
    }

    private fun isInteractive(): Boolean =
        (getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive

    private fun createNotificationChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.guard_channel_name),
                NotificationManager.IMPORTANCE_MIN,
            ),
        )
    }

    private fun buildNotification() =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_app)
            .setContentTitle(getString(R.string.guard_notification_title))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

    private fun record(message: String) {
        Log.i(GuardAccessibilityService.TAG, message)
        eventLog.record(GuardAccessibilityService.TAG, message)
    }

    companion object {
        private const val CHANNEL_ID = "mathgate_guard"
        private const val NOTIFICATION_ID = 1

        /** Roadmap phase 3: poll the fallback detector every 2-3 seconds. */
        private const val POLL_INTERVAL_MS: Long = 3_000L
    }
}
