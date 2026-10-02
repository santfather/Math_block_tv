package com.mathgate.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.mathgate.R
import com.mathgate.service.GuardForegroundService
import com.mathgate.ui.theme.MathGateTheme

/**
 * First-run wizard and the only leanback launcher entry point (phase 7).
 * Currently a placeholder so the project has an installable, navigable skeleton.
 */
class SetupActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startGuardService()
        setContent {
            MathGateTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = stringResource(R.string.setup_placeholder))
                    }
                }
            }
        }
    }

    /** Brings up the keep-alive guard as soon as the app is opened (phase 3). */
    private fun startGuardService() {
        ContextCompat.startForegroundService(
            this,
            Intent(this, GuardForegroundService::class.java),
        )
    }
}
