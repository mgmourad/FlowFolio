package com.flowfolio.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.flowfolio.core.model.ThemePreference

/**
 * App theme (ADR-0014, ADR-0015): the mock's palette, fonts and shapes. [glow] is the mock's "Neon glow" switch.
 * Book pages are drawn by the reader and keep their own paper colours.
 */
@Composable
fun FlowFolioTheme(
    preference: ThemePreference = ThemePreference.SYSTEM,
    glow: Boolean = true,
    content: @Composable () -> Unit
) {
    val dark = when (preference) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }
    val base = if (dark) DarkPalette else LightPalette
    val palette = if (glow) base else base.withoutGlow()
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(
            colorScheme = palette.toColorScheme(),
            typography = flowFolioTypography(),
            shapes = RoundedShapes,
            content = content
        )
    }
}

/**
 * The light or dark palette for a part of the screen whatever the app's theme (the reader's controls follow the page
 * colour, so they stay readable on it). Keeps the app's glow setting.
 */
@Composable
fun FlowFolioPalette(dark: Boolean, content: @Composable () -> Unit) {
    val current = LocalPalette.current
    if (current.dark == dark) {
        content()
        return
    }
    val base = if (dark) DarkPalette else LightPalette
    val glowOff = current.titleGlow == Glow.None && current.itemGlow == Glow.None
    val palette = if (glowOff) base.withoutGlow() else base
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(
            colorScheme = palette.toColorScheme(),
            typography = MaterialTheme.typography,
            shapes = MaterialTheme.shapes,
            content = content
        )
    }
}

/** Access to the design language from any composable. */
object FlowFolio {
    val palette: Palette
        @Composable @ReadOnlyComposable
        get() = LocalPalette.current

    /** Titles and menu entries (`.r-title`). */
    val title: TextStyle
        @Composable @ReadOnlyComposable
        get() = role(palette.title, palette.titleGlow)

    /** Primary items (`.r-item`). */
    val item: TextStyle
        @Composable @ReadOnlyComposable
        get() = role(palette.item, palette.itemGlow)

    /** Secondary text (`.r-hint`). */
    val hint: TextStyle
        @Composable @ReadOnlyComposable
        get() = role(palette.hint, palette.hintGlow)

    /** Sub-items and other (`.r-sub`). */
    val subItem: TextStyle
        @Composable @ReadOnlyComposable
        get() = role(palette.subItem, palette.subGlow)

    /** Plain body text on app surfaces (`--text`). */
    val text: TextStyle
        @Composable @ReadOnlyComposable
        get() = TextStyle(color = palette.text)

    /** Text in [color] with [glow] turned into a text shadow. */
    @Composable
    @ReadOnlyComposable
    fun role(color: Color, glow: Glow): TextStyle = TextStyle(color = color, shadow = glow.toShadow())

    /** Text in [color] lit like a title (kept for callers from ADR-0014). */
    @Composable
    @ReadOnlyComposable
    fun lit(color: Color): TextStyle = role(color, palette.titleGlow.copy(color = glowColorFor(color)))

    @Composable
    @ReadOnlyComposable
    private fun glowColorFor(color: Color): Color =
        if (palette.dark) color.copy(alpha = DARK_GLOW_ALPHA) else palette.titleGlow.color

    @Composable
    @ReadOnlyComposable
    fun Glow.toShadow(): Shadow? {
        if (color.alpha == 0f) return null
        val d = LocalDensity.current
        return with(d) { Shadow(color, Offset(0f, offsetY.toPx()), blur.toPx()) }
    }
}

private fun Palette.toColorScheme(): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = title,
        onPrimary = buttonInk,
        primaryContainer = surface2,
        onPrimaryContainer = title,
        secondary = item,
        onSecondary = background,
        secondaryContainer = surface2,
        onSecondaryContainer = item,
        tertiary = glyph,
        onTertiary = background,
        background = background,
        onBackground = text,
        surface = background,
        onSurface = text,
        surfaceVariant = surface,
        onSurfaceVariant = hint,
        surfaceContainerLowest = backgroundDeep,
        surfaceContainerLow = background,
        surfaceContainer = surface,
        surfaceContainerHigh = surface,
        surfaceContainerHighest = surface2,
        surfaceBright = surface2,
        surfaceDim = backgroundDeep,
        outline = edge,
        outlineVariant = edge,
        error = subItem,
        onError = background,
        scrim = Color.Black
    )
}

/** The mock's rounding: 22 dp cards, pills, 28 dp sheets. */
private val RoundedShapes = Shapes(
    extraSmall = RoundedCornerShape(9.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

private const val DARK_GLOW_ALPHA = 0.55f
