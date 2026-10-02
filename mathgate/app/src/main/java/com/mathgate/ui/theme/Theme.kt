package com.mathgate.ui.theme

import androidx.compose.material3.MaterialTheme as Material3Theme
import androidx.compose.material3.darkColorScheme as material3DarkColorScheme
import androidx.compose.runtime.Composable
import androidx.tv.material3.MaterialTheme as TvMaterial3Theme
import androidx.tv.material3.darkColorScheme as tvDarkColorScheme

private val GateColorScheme = material3DarkColorScheme(
    background = GateBackground,
    onBackground = GateOnBackground,
)

/** Dark scheme for the Compose-for-TV components (D-01). */
private val GateTvColorScheme = tvDarkColorScheme(
    primary = GateAccent,
    background = GateBackground,
    onBackground = GateOnBackground,
    surface = GateSurface,
    onSurface = GateOnSurface,
)

/** Single dark theme used by all screens. */
@Composable
fun MathGateTheme(content: @Composable () -> Unit) {
    Material3Theme(
        colorScheme = GateColorScheme,
        typography = MathGateTypography,
        content = content,
    )
}

/**
 * Theme for the challenge screen: the TV Material components read their defaults
 * from here, so it must wrap every Compose-for-TV surface.
 */
@Composable
fun MathGateTvTheme(content: @Composable () -> Unit) {
    TvMaterial3Theme(colorScheme = GateTvColorScheme, content = content)
}
