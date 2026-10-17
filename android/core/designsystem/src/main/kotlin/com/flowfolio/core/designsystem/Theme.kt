package com.flowfolio.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.flowfolio.core.model.ThemePreference

// App-chrome palette. Reading themes (sepia, true black, custom) for page content are separate (Phase 2).
private val Ink = Color(0xFF1F4E79)
private val InkLight = Color(0xFF9CC3EA)

private val LightColors = lightColorScheme(primary = Ink, secondary = Color(0xFF52606D))
private val DarkColors = darkColorScheme(primary = InkLight, secondary = Color(0xFFB8C4CF))

@Composable
fun FlowFolioTheme(preference: ThemePreference = ThemePreference.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (preference) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}
