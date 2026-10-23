// Material 3's tone table (0 black to 100 white) and the CIE colour-science constants (sRGB matrix, D65 white,
// L* curve) are read as tables, as they are published, rather than named one by one.
@file:Suppress("MagicNumber")

package com.flowfolio.core.designsystem

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.flowfolio.core.model.MaterialSeed
import com.flowfolio.core.model.ShelfColor
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Material You (ADR-0028): a Material 3 colour scheme, from the wallpaper (Android 12 and later) or generated from a
 * [MaterialSeed]'s hue, mapped onto FlowFolio's palette. Flat tonal surfaces, no glows, no grain.
 */
internal fun materialFamily(context: Context, seed: MaterialSeed): PaletteFamily {
    val wallpaper = seed == MaterialSeed.WALLPAPER && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val light = if (wallpaper) dynamicLightColorScheme(context) else seedScheme(seed.hueOrDefault, dark = false)
    val dark = if (wallpaper) dynamicDarkColorScheme(context) else seedScheme(seed.hueOrDefault, dark = true)
    return PaletteFamily(light.toPalette(dark = false), dark.toPalette(dark = true))
}

/** A seed's hue; the wallpaper choice on phones older than Android 12 falls back to blue. */
private val MaterialSeed.hueOrDefault: Float get() = if (hue < 0) MaterialSeed.BLUE.hue else hue

/** The seed's swatch colour, for the theme picker. */
fun MaterialSeed.swatch(dark: Boolean): Color =
    Tonal.color(hueOrDefault, PRIMARY_CHROMA, if (dark) DARK_ACCENT_TONE else LIGHT_ACCENT_TONE)

/** Material 3's scheme roles at their standard tones, from one hue (key colours as Material's tonal spot). */
private fun seedScheme(hue: Float, dark: Boolean): ColorScheme {
    fun p(t: Float) = Tonal.color(hue, PRIMARY_CHROMA, t)
    fun s(t: Float) = Tonal.color(hue, SECONDARY_CHROMA, t)
    fun t(t: Float) = Tonal.color(hue + TERTIARY_SHIFT, TERTIARY_CHROMA, t)
    fun n(t: Float) = Tonal.color(hue, NEUTRAL_CHROMA, t)
    fun nv(t: Float) = Tonal.color(hue, NEUTRAL_VARIANT_CHROMA, t)
    return if (dark) {
        darkColorScheme(
            primary = p(80f), onPrimary = p(20f), primaryContainer = p(30f), onPrimaryContainer = p(90f),
            secondary = s(80f), onSecondary = s(20f), secondaryContainer = s(30f), onSecondaryContainer = s(90f),
            tertiary = t(80f), onTertiary = t(20f), tertiaryContainer = t(30f), onTertiaryContainer = t(90f),
            background = n(6f), onBackground = n(90f), surface = n(6f), onSurface = n(90f),
            surfaceVariant = nv(30f), onSurfaceVariant = nv(80f), outline = nv(60f), outlineVariant = nv(30f),
            surfaceContainerLowest = n(4f), surfaceContainerLow = n(10f), surfaceContainer = n(12f),
            surfaceContainerHigh = n(17f), surfaceContainerHighest = n(22f), surfaceDim = n(6f), surfaceBright = n(24f)
        )
    } else {
        lightColorScheme(
            primary = p(40f), onPrimary = p(100f), primaryContainer = p(90f), onPrimaryContainer = p(10f),
            secondary = s(40f), onSecondary = s(100f), secondaryContainer = s(90f), onSecondaryContainer = s(10f),
            tertiary = t(40f), onTertiary = t(100f), tertiaryContainer = t(90f), onTertiaryContainer = t(10f),
            background = n(98f), onBackground = n(10f), surface = n(98f), onSurface = n(10f),
            surfaceVariant = nv(90f), onSurfaceVariant = nv(30f), outline = nv(50f), outlineVariant = nv(80f),
            surfaceContainerLowest = n(100f), surfaceContainerLow = n(96f), surfaceContainer = n(94f),
            surfaceContainerHigh = n(92f), surfaceContainerHighest = n(90f), surfaceDim = n(87f), surfaceBright = n(98f)
        )
    }
}

/** A Material scheme as FlowFolio's tokens: primary for titles, icons and buttons, the surfaces for the rest. */
private fun ColorScheme.toPalette(dark: Boolean): Palette = Palette(
    dark = dark,
    background = surface,
    backgroundDeep = if (dark) surfaceContainerLowest else surfaceContainer,
    surface = surfaceContainer,
    surface2 = surfaceContainerHigh,
    edge = outlineVariant,
    title = primary,
    item = onSurface,
    hint = onSurfaceVariant,
    subItem = tertiary,
    glyph = primary,
    text = onSurface,
    toggleOffTrack = surfaceContainerHighest,
    toggleOffThumb = outline,
    toggleOnTrack = primary,
    toggleOnThumb = onPrimary,
    cardStart = surfaceContainerLow,
    cardEnd = surfaceContainerLow,
    paper = surfaceContainerLowest,
    paperInk = onSurface,
    paperShade = Color.Black.copy(alpha = if (dark) DARK_SHADE else LIGHT_SHADE),
    shelfTop = secondaryContainer,
    shelfBottom = surfaceContainerHighest,
    tabBar = surfaceContainer.copy(alpha = TAB_BAR_ALPHA),
    buttonTop = primary,
    buttonBottom = primary,
    buttonInk = onPrimary,
    titleGlow = Glow.None,
    itemGlow = Glow.None,
    subGlow = Glow.None,
    hintGlow = Glow.None,
    glyphGlow = Glow.None,
    selected = secondaryContainer,
    shadow = Color.Black.copy(alpha = if (dark) DARK_SHADOW else LIGHT_SHADOW),
    pressShadow = Color.Black.copy(alpha = if (dark) DARK_PRESS else LIGHT_PRESS),
    highlight = Color.Transparent,
    green = Tonal.color(GREEN_HUE, PRIMARY_CHROMA, if (dark) DARK_ACCENT_TONE else LIGHT_ACCENT_TONE),
    texture = SurfaceTexture.NONE,
    buttonGlyph = onPrimary
)

/**
 * Colours at a hue, chroma and tone, with CIE L* as the tone and LCh(ab) chroma: close to Material's HCT for picking
 * tonal palettes, without a library. Chroma is reduced until the colour fits in sRGB.
 */
internal object Tonal {
    fun color(hue: Float, chroma: Float, tone: Float): Color {
        var c = chroma
        while (c > 0f) {
            lch(tone, c, hue)?.let { return it }
            c -= CHROMA_STEP
        }
        return lch(tone, 0f, hue) ?: Color.Gray
    }

    private fun lch(l: Float, c: Float, hueDegrees: Float): Color? {
        val h = Math.toRadians(hueDegrees.toDouble())
        val a = c * cos(h)
        val b = c * sin(h)
        val fy = (l + 16.0) / 116.0
        val fx = fy + a / 500.0
        val fz = fy - b / 200.0
        val x = WHITE_X * inverse(fx)
        val y = if (l > KAPPA_EPSILON) fy * fy * fy else l / KAPPA
        val z = WHITE_Z * inverse(fz)
        val r = gamma(3.2404542 * x - 1.5371385 * y - 0.4985314 * z)
        val g = gamma(-0.9692660 * x + 1.8760108 * y + 0.0415560 * z)
        val bl = gamma(0.0556434 * x - 0.2040259 * y + 1.0572252 * z)
        val fits = listOf(r, g, bl).all { it in -GAMUT_SLACK..1.0 + GAMUT_SLACK }
        return if (fits) {
            Color(
                r.toFloat().coerceIn(0f, 1f),
                g.toFloat().coerceIn(0f, 1f),
                bl.toFloat().coerceIn(0f, 1f)
            )
        } else {
            null
        }
    }

    private fun inverse(t: Double): Double = if (t * t * t > EPSILON) t * t * t else (116.0 * t - 16.0) / KAPPA

    private fun gamma(v: Double): Double = if (v <= LINEAR_LIMIT) 12.92 * v else 1.055 * v.pow(1.0 / 2.4) - 0.055
}

private const val PRIMARY_CHROMA = 48f
private const val SECONDARY_CHROMA = 16f
private const val TERTIARY_CHROMA = 24f
private const val TERTIARY_SHIFT = 60f
private const val NEUTRAL_CHROMA = 4f
private const val NEUTRAL_VARIANT_CHROMA = 8f
private const val DARK_ACCENT_TONE = 80f
private const val LIGHT_ACCENT_TONE = 40f
private const val GREEN_HUE = 140f
private const val CHROMA_STEP = 1f
private const val DARK_SHADE = 0.45f
private const val LIGHT_SHADE = 0.22f
private const val TAB_BAR_ALPHA = 0.94f
private const val DARK_SHADOW = 0.55f
private const val LIGHT_SHADOW = 0.18f
private const val DARK_PRESS = 0.35f
private const val LIGHT_PRESS = 0.12f
private const val WHITE_X = 0.95047
private const val WHITE_Z = 1.08883
private const val EPSILON = 216.0 / 24389.0
private const val KAPPA = 24389.0 / 27.0
private const val KAPPA_EPSILON = 8.0
private const val LINEAR_LIMIT = 0.0031308
private const val GAMUT_SLACK = 0.0005

/** A shelf's colour (owner, 2026-10-22) at the tone of Material You's accents in light or dark. */
fun ShelfColor.tint(dark: Boolean): Color = MaterialSeed.valueOf(name).swatch(dark)

/** A shelf's colour in the current light or dark; null (no colour chosen) is the theme's title colour. */
val ShelfColor?.color: Color
    @androidx.compose.runtime.Composable @androidx.compose.runtime.ReadOnlyComposable
    get() = this?.tint(FlowFolio.palette.dark) ?: FlowFolio.palette.title
