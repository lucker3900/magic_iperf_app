package com.luckerlucky.magiciperf.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F62FE),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E2FF),
    onPrimaryContainer = Color(0xFF00174C),
    secondary = Color(0xFF4F5B62),
    onSecondary = Color.White,
    background = Color(0xFFF7F9FB),
    onBackground = Color(0xFF12161A),
    surface = Color.White,
    onSurface = Color(0xFF12161A),
    surfaceVariant = Color(0xFFE1E8F5),
    onSurfaceVariant = Color(0xFF414853)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF93C2FF),
    onPrimary = Color(0xFF00315C),
    primaryContainer = Color(0xFF004882),
    onPrimaryContainer = Color(0xFFD6E2FF),
    secondary = Color(0xFFB3BDC4),
    onSecondary = Color(0xFF1D252A),
    background = Color(0xFF0D1117),
    onBackground = Color(0xFFE6E9EE),
    surface = Color(0xFF161B22),
    onSurface = Color(0xFFE6E9EE),
    surfaceVariant = Color(0xFF2B3138),
    onSurfaceVariant = Color(0xFFC5CCD4)
)

@Composable
fun MagicIperfTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (useDarkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        content = content
    )
}
