package com.flowfolio.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// App-chrome palette. Reading themes (sepia, true black, custom) are separate and arrive in Phase 1-2.
private val Ink = Color(0xFF1F4E79)
private val InkLight = Color(0xFF9CC3EA)

private val LightColors = lightColorScheme(primary = Ink, secondary = Color(0xFF52606D))
private val DarkColors = darkColorScheme(primary = InkLight, secondary = Color(0xFFB8C4CF))

@Composable
fun FlowFolioTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Material You wallpaper colors on Android 12+; off by default to keep a consistent brand.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColors

        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
