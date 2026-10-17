package com.serediuk.stairs.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFFD9480F),
    onPrimary = Color.White,
    secondary = Color(0xFF5C6B7A),
    tertiary = Color(0xFF2E7D32),
    background = Color(0xFFF4F5F7),
    onBackground = Color(0xFF14181F),
    surface = Color.White,
    onSurface = Color(0xFF14181F),
    surfaceVariant = Color(0xFFECEFF3),
    onSurfaceVariant = Color(0xFF5D6675),
    outlineVariant = Color(0xFFDDE1E8),
    error = Color(0xFFC92A2A)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF8A4C),
    onPrimary = Color(0xFF3A1500),
    secondary = Color(0xFF9FB0C2),
    tertiary = Color(0xFF66BB6A),
    background = Color(0xFF0F1218),
    onBackground = Color(0xFFEEF1F6),
    surface = Color(0xFF1A1F29),
    onSurface = Color(0xFFEEF1F6),
    surfaceVariant = Color(0xFF252C39),
    onSurfaceVariant = Color(0xFF98A2B3),
    outlineVariant = Color(0xFF2C3340),
    error = Color(0xFFFF6B6B)
)

@Composable
fun StairTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}
