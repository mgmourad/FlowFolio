package com.flowfolio.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.flowfolio.core.model.ThemePreference

/** App theme (ADR-0014). Book pages are drawn by the reader and keep their printed colours unless Night pages is on. */
@Composable
fun FlowFolioTheme(preference: ThemePreference = ThemePreference.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (preference) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }
    val palette = if (dark) DarkPalette else LightPalette
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(
            colorScheme = palette.toColorScheme(),
            typography = palette.toTypography(Typography()),
            shapes = RoundedShapes,
            content = content
        )
    }
}

/** Access to the design language from any composable. */
object FlowFolio {
    val palette: Palette
        @Composable @ReadOnlyComposable
        get() = LocalPalette.current

    /** Titles and menu entries. */
    val title: TextStyle
        @Composable @ReadOnlyComposable
        get() = lit(palette.title)

    /** Primary items. */
    val item: TextStyle
        @Composable @ReadOnlyComposable
        get() = lit(palette.item)

    /** Secondary text. Hints are quiet: a softer glow, a lighter shadow. */
    val hint: TextStyle
        @Composable @ReadOnlyComposable
        get() = lit(palette.hint, strength = HINT_STRENGTH)

    /** Sub-items and other. */
    val subItem: TextStyle
        @Composable @ReadOnlyComposable
        get() = lit(palette.subItem)

    /** Text in [color] with the mode's effect: a glow in its own colour (dark) or a soft drop shadow (light). */
    @Composable
    @ReadOnlyComposable
    fun lit(color: Color, strength: Float = 1f): TextStyle = TextStyle(color = color, shadow = effect(color, strength))

    @Composable
    @ReadOnlyComposable
    fun effect(color: Color, strength: Float = 1f): Shadow = if (palette.dark) {
        Shadow(color.copy(alpha = GLOW_ALPHA * strength), Offset.Zero, GLOW_BLUR)
    } else {
        Shadow(Color.Black.copy(alpha = SHADOW_ALPHA * strength), Offset(0f, SHADOW_DROP), SHADOW_BLUR)
    }
}

private fun Palette.toColorScheme(): ColorScheme {
    // Raised surfaces (menus, cards) sit a step away from the background, toward white in light, lighter in dark.
    val raised = lerp(background, Color.White, if (dark) RAISE_DARK else RAISE_LIGHT)
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = title,
        onPrimary = background,
        primaryContainer = raised,
        onPrimaryContainer = title,
        secondary = item,
        onSecondary = background,
        secondaryContainer = raised,
        onSecondaryContainer = item,
        tertiary = glyph,
        onTertiary = background,
        background = background,
        onBackground = item,
        surface = background,
        onSurface = item,
        surfaceVariant = raised,
        onSurfaceVariant = hint,
        surfaceContainerLowest = background,
        surfaceContainerLow = background,
        surfaceContainer = background,
        surfaceContainerHigh = raised,
        surfaceContainerHighest = raised,
        surfaceBright = raised,
        surfaceDim = background,
        outline = edge,
        outlineVariant = edge,
        error = subItem,
        onError = background,
        scrim = Color.Black
    )
}

/** Material's type scale, with titles and headlines lit like the design's "title". */
private fun Palette.toTypography(base: Typography): Typography {
    val titleLit = { s: TextStyle -> s.copy(shadow = glowOrShadow(title)) }
    return base.copy(
        headlineLarge = titleLit(base.headlineLarge),
        headlineMedium = titleLit(base.headlineMedium),
        headlineSmall = titleLit(base.headlineSmall),
        titleLarge = titleLit(base.titleLarge),
        titleMedium = titleLit(base.titleMedium)
    )
}

private fun Palette.glowOrShadow(color: Color): Shadow = if (dark) {
    Shadow(color.copy(alpha = GLOW_ALPHA), Offset.Zero, GLOW_BLUR)
} else {
    Shadow(Color.Black.copy(alpha = SHADOW_ALPHA), Offset(0f, SHADOW_DROP), SHADOW_BLUR)
}

/** The design's generous rounding. */
private val RoundedShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(40.dp)
)

private const val GLOW_ALPHA = 0.85f
private const val GLOW_BLUR = 18f
private const val SHADOW_ALPHA = 0.32f
private const val SHADOW_DROP = 3f
private const val SHADOW_BLUR = 6f
private const val HINT_STRENGTH = 0.5f
private const val RAISE_DARK = 0.07f
private const val RAISE_LIGHT = 0.35f
