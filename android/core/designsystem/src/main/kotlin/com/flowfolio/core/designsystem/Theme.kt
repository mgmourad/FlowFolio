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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.flowfolio.core.model.AppStyle
import com.flowfolio.core.model.MaterialSeed
import com.flowfolio.core.model.ThemePreference

/** A theme's light and dark palettes (ADR-0028). */
@Immutable
data class PaletteFamily(val light: Palette, val dark: Palette) {
    fun of(dark: Boolean): Palette = if (dark) this.dark else light
}

/** The theme's palettes, for parts of the screen that pick light or dark themselves ([FlowFolioPalette]). */
val LocalPaletteFamily = staticCompositionLocalOf { PaletteFamily(LightPalette, DarkPalette) }

/** The app's theme (ADR-0028), for the few places that differ beyond colour (the reader's Auto paper). */
val LocalAppStyle = staticCompositionLocalOf { AppStyle.FLOWFOLIO }

/**
 * App theme (ADR-0014, ADR-0015, ADR-0028): the [style]'s palette in light or dark ([preference]), with the fonts and
 * shapes. [glow] is the "Night glow" switch (off by default, ADR-0052); [seed] picks Material You's colours. Book
 * pages are drawn by the reader and
 * keep their own paper colours.
 */
@Composable
fun FlowFolioTheme(
    preference: ThemePreference = ThemePreference.SYSTEM,
    glow: Boolean = false,
    style: AppStyle = AppStyle.FLOWFOLIO,
    seed: MaterialSeed = MaterialSeed.WALLPAPER,
    content: @Composable () -> Unit
) {
    val dark = when (preference) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }
    val family = paletteFamily(style, seed)
    val base = family.of(dark)
    val palette = if (glow) base else base.withoutGlow()
    CompositionLocalProvider(
        LocalPalette provides palette,
        LocalPaletteFamily provides family,
        LocalAppStyle provides style,
        LocalReducedMotion provides rememberSystemReducedMotion()
    ) {
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
    val same = current.dark == dark
    val palette = if (same) {
        current
    } else {
        val base = LocalPaletteFamily.current.of(dark)
        val glowOff = current.titleGlow == Glow.None && current.itemGlow == Glow.None
        if (glowOff) base.withoutGlow() else base
    }
    // One shape whichever way round, so content keeps its state when the paper flips between light and dark (an early
    // return here put it in another group: the reader's bars and panels were rebuilt mid-reveal, tabs reset).
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(
            colorScheme = if (same) MaterialTheme.colorScheme else palette.toColorScheme(),
            typography = MaterialTheme.typography,
            shapes = MaterialTheme.shapes,
            content = content
        )
    }
}

/** The [style]'s light and dark palettes; Material You's are made once per seed (and wallpaper). */
@Composable
fun paletteFamily(style: AppStyle, seed: MaterialSeed): PaletteFamily = when (style) {
    AppStyle.FLOWFOLIO -> PaletteFamily(LightPalette, DarkPalette)

    AppStyle.RUSTIC -> PaletteFamily(RusticLightPalette, RusticDarkPalette)

    AppStyle.MATERIAL -> {
        val context = LocalContext.current
        remember(seed, context) { materialFamily(context, seed) }
    }

    // Both slots are the same black palette: choosing Light under AMOLED keeps the black (owner, M4).
    AppStyle.AMOLED -> PaletteFamily(AmoledPalette, AmoledPalette)
}

/** Access to the design language from any composable. */
object FlowFolio {
    val palette: Palette
        @Composable @ReadOnlyComposable
        get() = LocalPalette.current

    /** Titles and menu entries (`.r-title`). */
    val title: TextStyle
        @Composable @ReadOnlyComposable
        get() = role(palette.ink, palette.titleGlow)

    /** Primary items (`.r-item`). */
    val item: TextStyle
        @Composable @ReadOnlyComposable
        get() = role(palette.ink, palette.itemGlow)

    /** Secondary text (`.r-hint`). */
    val hint: TextStyle
        @Composable @ReadOnlyComposable
        get() = role(palette.ink2, palette.hintGlow)

    /**
     * Sub-items and other secondary lines: ink at its second weight since ADR-0052.
     */
    val subItem: TextStyle
        @Composable @ReadOnlyComposable
        get() = role(palette.ink2, Glow.None)

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
        primary = accent,
        onPrimary = accentInk,
        primaryContainer = accentSoft,
        onPrimaryContainer = accent,
        secondary = ink2,
        onSecondary = bg,
        secondaryContainer = surface2,
        onSecondaryContainer = ink,
        tertiary = accent,
        onTertiary = accentInk,
        background = bg,
        onBackground = ink,
        surface = bg,
        onSurface = ink,
        surfaceVariant = surface2,
        onSurfaceVariant = ink2,
        surfaceTint = Color.Transparent,
        surfaceContainerLowest = bg2,
        surfaceContainerLow = bg,
        surfaceContainer = surface,
        surfaceContainerHigh = surface,
        surfaceContainerHighest = surface2,
        surfaceBright = surface,
        surfaceDim = bg2,
        inverseSurface = ink,
        inverseOnSurface = bg,
        inversePrimary = accentSoft,
        outline = line,
        outlineVariant = line,
        error = danger,
        onError = if (dark) bg else Color.White,
        errorContainer = dangerSoft,
        onErrorContainer = danger,
        scrim = scrim
    )
}

/** The mock's rounding (ADR-0052): 22 dp cards, 28 dp sheets, pills fully round. */
private val RoundedShapes = Shapes(
    extraSmall = RoundedCornerShape(9.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

private const val DARK_GLOW_ALPHA = 0.55f
