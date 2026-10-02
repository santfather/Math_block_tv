package com.mathgate.service

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import com.mathgate.MathGateApp

/**
 * Watches `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` and `ACCESSIBILITY_ENABLED` and
 * re-enables our service if it was switched off (D-08, phase 6). Requires
 * `WRITE_SECURE_SETTINGS`, granted once over adb.
 *
 * The write draws another `onChange`, but [ensureAccessibilityEnabled] only writes when a value
 * actually differs, so the observer converges instead of looping.
 */
object SelfHealer {

    private var observer: ContentObserver? = null

    /** Registers the watcher and repairs the setting once. Idempotent. */
    fun start(context: Context) {
        val app = context.applicationContext
        if (observer != null) return
        if (!canWriteSecureSettings(app)) {
            record(app, "self-heal unavailable: WRITE_SECURE_SETTINGS not granted")
            return
        }
        val contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                ensureAccessibilityEnabled(app)
            }
        }
        // Watch both the per-service list and the master switch: turning accessibility off
        // in Settings may clear only one of them.
        app.contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
            false,
            contentObserver,
        )
        app.contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.ACCESSIBILITY_ENABLED),
            false,
            contentObserver,
        )
        observer = contentObserver
        ensureAccessibilityEnabled(app)
    }

    /** Unregisters the watcher. */
    fun stop(context: Context) {
        observer?.let { context.applicationContext.contentResolver.unregisterContentObserver(it) }
        observer = null
    }

    /** Re-adds our service to the enabled list and turns accessibility on if needed. */
    fun ensureAccessibilityEnabled(context: Context) {
        val app = context.applicationContext
        if (!canWriteSecureSettings(app)) return
        val resolver = app.contentResolver
        val component = ComponentName(app, GuardAccessibilityService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(
            resolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        val services = enabled.split(':').filter { it.isNotBlank() }.toMutableList()
        if (services.none { it.equals(component, ignoreCase = true) }) {
            services.add(component)
            Settings.Secure.putString(
                resolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                services.joinToString(":"),
            )
            record(app, "accessibility service re-added")
        }

        // The master switch can be off while our service is still listed; restore it too.
        if (Settings.Secure.getInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) != 1) {
            Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            record(app, "accessibility master switch re-enabled")
        }
    }

    private fun canWriteSecureSettings(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    private fun record(context: Context, message: String) {
        Log.i(GuardAccessibilityService.TAG, message)
        (context as? MathGateApp)?.eventLog?.record(GuardAccessibilityService.TAG, message)
    }
}
