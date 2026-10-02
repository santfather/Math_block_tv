package com.mathgate.ui

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
import com.mathgate.R
import com.mathgate.ui.theme.MathGateTheme

/**
 * Parent settings, protected by a PIN (phase 7).
 * Also registered as the accessibility service `settingsActivity`.
 */
class ParentActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MathGateTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = stringResource(R.string.parent_placeholder))
                    }
                }
            }
        }
    }
}
