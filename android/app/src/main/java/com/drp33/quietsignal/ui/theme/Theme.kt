package com.drp33.quietsignal.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Grove botanical scheme. Dynamic colour is intentionally OFF so the warm
 * cream/sage palette is consistent on every device. Dark mode falls back to the
 * same light scheme — Grove is a single warm-light aesthetic by design.
 */
private val GroveColorScheme = lightColorScheme(
    primary = Grove.Accent,
    onPrimary = Grove.Surface,
    secondary = Grove.Foliage,
    onSecondary = Grove.Surface,
    tertiary = Grove.Water,
    background = Grove.Bg,
    onBackground = Grove.Ink,
    surface = Grove.Surface,
    onSurface = Grove.Ink,
    surfaceVariant = Grove.Surface2,
    onSurfaceVariant = Grove.InkSoft,
    outline = Grove.InkFaint,
    error = Color(0xFFD32F2F),
)

@Composable
fun QuietSignalTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = GroveColorScheme,
        typography = Typography,
        content = content,
    )
}
