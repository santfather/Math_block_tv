package com.mathgate.service

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import com.mathgate.R
import com.mathgate.core.EventLog
import com.mathgate.ui.BlockActivity

/**
 * Platform side of the enforcement (phase 5).
 *
 * Starts and stops [BlockActivity] over the watched app and drives the media transport
 * (D-06). The activity start was verified by a device spike: on Android 12 the
 * accessibility service may start an Activity while YouTube is on the foreground, so the
 * `TYPE_ACCESSIBILITY_OVERLAY` fallback (D-11) is not needed.
 */
class ChallengeEnforcer(
    private val context: Context,
    private val eventLog: EventLog,
) {

    private val audioManager: AudioManager
        get() = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /** Brings the challenge to the front and pauses the video. */
    fun showChallenge() {
        try {
            context.startActivity(
                Intent(context, BlockActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            record("challenge shown")
        } catch (e: Exception) {
            record("showChallenge failed: ${e.message}")
        }
        dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
    }

    /** Resumes the video; the challenge screen closes itself once its state is no longer pending. */
    fun hideChallenge() {
        dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)
        record("challenge hidden")
    }

    /** Quiet warning shortly before the limit, so the child is not cut off mid-scene. */
    fun warnAboutLimit() {
        Toast.makeText(context, context.getString(R.string.block_warning), Toast.LENGTH_LONG).show()
        record("limit warning shown")
    }

    private fun dispatchMediaKey(keyCode: Int) {
        try {
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        } catch (e: Exception) {
            record("media key $keyCode failed: ${e.message}")
        }
    }

    private fun record(message: String) {
        Log.i(GuardAccessibilityService.TAG, message)
        eventLog.record(GuardAccessibilityService.TAG, message)
    }
}
