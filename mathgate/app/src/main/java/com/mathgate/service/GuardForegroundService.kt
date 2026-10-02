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
import android.os.SystemClock
import android.provider.Settings as AndroidSettings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.mathgate.MathGateApp
import com.mathgate.R
import com.mathgate.core.EventLog
import com.mathgate.data.BootInfo
import com.mathgate.detect.UsageStatsDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Keep-alive service (D-07) hosting the fallback detector (D-02), the accessibility self-healer
 * (D-08) and the clean-shutdown marker for the `resetOnPowerLoss` heuristic (D-04) — phase 6.
 */
class GuardForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var app: MathGateApp
    private lateinit var eventLog: EventLog
    private lateinit var usageStatsDetector: UsageStatsDetector

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> record("screen ON (interactive=true)")
                Intent.ACTION_SCREEN_OFF -> record("screen OFF (interactive=false)")
            }
        }
    }

    /** A clean power-off records the marker so the next boot is not treated as a power loss (D-04). */
    private val shutdownReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != Intent.ACTION_SHUTDOWN) return
            scope.launch {
                app.gateStore.writeBootInfo(BootInfo(currentBootCount(), cleanShutdown = true))
                record("clean shutdown recorded")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        app = application as MathGateApp
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
        registerReceiver(shutdownReceiver, IntentFilter(Intent.ACTION_SHUTDOWN))
        SelfHealer.start(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        record("guard service started (interactive=${isInteractive()})")
        app.gateCoordinator.start()
        startWatchdog()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        unregisterReceiver(screenReceiver)
        unregisterReceiver(shutdownReceiver)
        scope.cancel()
        record("guard service destroyed")
        super.onDestroy()
    }

    /**
     * Fallback/watchdog channel: polls UsageStats while the primary accessibility channel is
     * unusable — either because the service is off or because it went quiet for
     * [HEARTBEAT_STALE_MS] (phase 6). Pushes the result into the coordinator so counting continues.
     */
    private fun startWatchdog() {
        scope.launch {
            var fallbackActive = false
            var lastWatched = false
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                val a11yEnabled = isGuardAccessibilityEnabled()
                val primaryStale = app.a11yHeartbeat.isStale(SystemClock.elapsedRealtime(), HEARTBEAT_STALE_MS)
                if (a11yEnabled && !primaryStale) {
                    fallbackActive = false
                    continue
                }

                // No usable reading (permission denied / no recent transition): keep the state the
                // primary channel last reported instead of forcing "not watched".
                if (!usageStatsDetector.poll(app.gateCoordinator.watchedPackages.value)) continue
                val watched = usageStatsDetector.watchedForeground.value
                if (!fallbackActive || watched != lastWatched) {
                    fallbackActive = true
                    lastWatched = watched
                    record("watchdog fallback -> $watched (a11yEnabled=$a11yEnabled, stale=$primaryStale)")
                    app.gateCoordinator.onFallbackForeground(watched)
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

    private fun currentBootCount(): Int =
        AndroidSettings.Global.getInt(contentResolver, AndroidSettings.Global.BOOT_COUNT, -1)

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

        /** Roadmap phase 3/6: poll the fallback channel every 2-3 seconds at most. */
        private const val POLL_INTERVAL_MS: Long = 3_000L

        /** Treat the accessibility channel as silent after this long without an event (phase 6). */
        private const val HEARTBEAT_STALE_MS: Long = 60_000L
    }
}
