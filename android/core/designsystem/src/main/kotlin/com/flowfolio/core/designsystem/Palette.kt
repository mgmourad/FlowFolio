// The design's colour tables are read as tables (ADR-0052), not named one by one.
@file:Suppress("MagicNumber")

package com.flowfolio.core.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The fine texture over the app background (ADR-0028). Quiet Library (ADR-0052) lays none. */
enum class SurfaceTexture { GRAIN, NONE }

/**
 * A light effect: a glow in dark mode (no offset, the element's own colour), a soft drop shadow in light mode.
 * Values are the mock's CSS (`text-shadow` / `drop-shadow`), in dp.
 */
@Immutable
data class Glow(val color: Color, val blur: Dp, val offsetY: Dp = 0.dp) {
    companion object {
        val None = Glow(Color.Transparent, 0.dp)
    }
}

/**
 * A theme's semantic colours (ADR-0052, the "Quiet Library" mock's CSS variables): warm neutrals, three weights of ink
 * and one accent whose only meanings are the primary action and the selected state.
 */
@Immutable
data class Tokens(
    val bg: Color,
    val bg2: Color,
    val surface: Color,
    val surface2: Color,
    val surface3: Color,
    val line: Color,
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val accent: Color,
    val accentInk: Color,
    val accentSoft: Color,
    val ok: Color,
    val okSoft: Color,
    val danger: Color,
    val dangerSoft: Color,
    val paper: Color,
    val paperInk: Color,
    val scrim: Color
)

/**
 * FlowFolio's design tokens. The semantic set ([ink], [accent], [surface3] ...) is ADR-0052's; the older role
 * fields ([title], [item], [hint], [subItem], [glyph], the toggle and button colours) are kept as aliases derived from
 * it by [quietPalette], so every screen takes the new look at once while it moves to the new names. The aliases go at
 * the end of the overhaul.
 */
@Immutable
data class Palette(
    val dark: Boolean,
    val background: Color,
    val backgroundDeep: Color,
    val surface: Color,
    val surface2: Color,
    /** Hairlines and outlines (`--line`). */
    val edge: Color,
    /** Body text on app surfaces. */
    val text: Color,
    val toggleOffTrack: Color,
    val toggleOffThumb: Color,
    val toggleOnTrack: Color,
    val toggleOnThumb: Color,
    /** Card fill: a 160-degree gradient from [cardStart] to [cardEnd] (flat since ADR-0052). */
    val cardStart: Color,
    val cardEnd: Color,
    val paper: Color,
    val paperInk: Color,
    val paperShade: Color,
    val shelfTop: Color,
    val shelfBottom: Color,
    val tabBar: Color,
    val buttonTop: Color,
    val buttonBottom: Color,
    val buttonInk: Color,
    val titleGlow: Glow,
    val itemGlow: Glow,
    val subGlow: Glow,
    val hintGlow: Glow,
    val glyphGlow: Glow,
    /** The selected segment / tab indicator fill. */
    val selected: Color,
    /** Soft card shadow colour. */
    val shadow: Color,
    /** Pressed / sunken inner shadow colour. */
    val pressShadow: Color,
    /** Top inner highlight on cards. */
    val highlight: Color,
    /** The success colour (`--ok`). */
    val green: Color,
    /** What the app background is laid with (ADR-0028). */
    val texture: SurfaceTexture = SurfaceTexture.NONE,
    /** The icon on a primary button. */
    val buttonGlyph: Color,
    /** The semantic colours everything above is derived from (ADR-0052). */
    val tokens: Tokens = LightTokens
) {
    val bg: Color get() = tokens.bg
    val bg2: Color get() = tokens.bg2
    val surface3: Color get() = tokens.surface3
    val line: Color get() = tokens.line
    val ink: Color get() = tokens.ink
    val ink2: Color get() = tokens.ink2
    val ink3: Color get() = tokens.ink3
    val accent: Color get() = tokens.accent
    val accentInk: Color get() = tokens.accentInk
    val accentSoft: Color get() = tokens.accentSoft
    val ok: Color get() = tokens.ok
    val okSoft: Color get() = tokens.okSoft
    val danger: Color get() = tokens.danger
    val dangerSoft: Color get() = tokens.dangerSoft
    val scrim: Color get() = tokens.scrim

    /** "Night glow" off (the default since ADR-0052): no glows or text shadows. */
    fun withoutGlow(): Palette = copy(
        titleGlow = Glow.None,
        itemGlow = Glow.None,
        subGlow = Glow.None,
        hintGlow = Glow.None,
        glyphGlow = Glow.None
    )
}

/**
 * A theme's [Palette] from its [tokens] (ADR-0052). Titles, items and labels are ink at three weights, never separate
 * hues; the accent is the primary action and the selected state; warnings and removals are [Tokens.danger]. The only
 * glow left is "Night glow"'s soft bloom on display titles in dark mode.
 */
fun quietPalette(tokens: Tokens, dark: Boolean): Palette = with(tokens) {
    Palette(
        dark = dark,
        background = bg,
        backgroundDeep = bg2,
        surface = surface,
        surface2 = surface2,
        edge = line,
        text = ink,
        toggleOffTrack = surface3,
        toggleOffThumb = Color.White,
        toggleOnTrack = accent,
        toggleOnThumb = Color.White,
        cardStart = surface,
        cardEnd = surface,
        paper = paper,
        paperInk = paperInk,
        paperShade = if (dark) Color(0x73000000) else Color(0x47503214),
        shelfTop = surface3,
        shelfBottom = line,
        tabBar = surface.copy(alpha = TAB_BAR_ALPHA),
        buttonTop = accent,
        buttonBottom = accent,
        buttonInk = accentInk,
        titleGlow = if (dark) Glow(accent.copy(alpha = NIGHT_GLOW_ALPHA), 14.dp) else Glow.None,
        itemGlow = Glow.None,
        subGlow = Glow.None,
        hintGlow = Glow.None,
        glyphGlow = Glow.None,
        selected = surface,
        shadow = if (dark) Color(0x8C000000) else Color(0x293C2814),
        pressShadow = if (dark) Color(0x59000000) else Color(0x1F3C2814),
        highlight = Color.Transparent,
        green = ok,
        texture = SurfaceTexture.NONE,
        buttonGlyph = accentInk,
        tokens = tokens
    )
}

/** FlowFolio, light (ADR-0052). */
val LightTokens = Tokens(
    bg = Color(0xFFF6EFE3),
    bg2 = Color(0xFFEFE5D3),
    surface = Color(0xFFFFFBF4),
    surface2 = Color(0xFFF1E7D6),
    surface3 = Color(0xFFE9DCC6),
    line = Color(0xFFE3D6C0),
    ink = Color(0xFF2A2118),
    ink2 = Color(0xFF6B5D4E),
    // The mock's #9A8B79 reaches 2.9:1 on bg; a step darker reaches the 3:1 AA asks of meta text (ADR-0052).
    ink3 = Color(0xFF938574),
    accent = Color(0xFFC2410C),
    accentInk = Color(0xFFFFFFFF),
    accentSoft = Color(0xFFF8E1D2),
    ok = Color(0xFF2F7D5B),
    okSoft = Color(0xFFDCEFE4),
    danger = Color(0xFFB42318),
    dangerSoft = Color(0xFFFBE3DF),
    paper = Color(0xFFFBF7EF),
    paperInk = Color(0xFF2B241C),
    scrim = Color(0x612A2118)
)

/** FlowFolio, dark (ADR-0052). */
val DarkTokens = Tokens(
    bg = Color(0xFF15110E),
    bg2 = Color(0xFF1B1612),
    surface = Color(0xFF211B16),
    surface2 = Color(0xFF2A231D),
    surface3 = Color(0xFF342B24),
    line = Color(0xFF3A3129),
    ink = Color(0xFFF2EADF),
    ink2 = Color(0xFFBDAF9E),
    ink3 = Color(0xFF857767),
    accent = Color(0xFFF59E5B),
    accentInk = Color(0xFF1A120B),
    accentSoft = Color(0xFF3B2718),
    ok = Color(0xFF6FCF97),
    okSoft = Color(0xFF1E3327),
    danger = Color(0xFFFF8A7A),
    dangerSoft = Color(0xFF3A1E1A),
    paper = Color(0xFF1E1A16),
    paperInk = Color(0xFFE6DCCD),
    scrim = Color(0x8C000000)
)

val LightPalette = quietPalette(LightTokens, dark = false)

val DarkPalette = quietPalette(DarkTokens, dark = true)

val LocalPalette = staticCompositionLocalOf { LightPalette }

private const val TAB_BAR_ALPHA = 0.84f
private const val NIGHT_GLOW_ALPHA = 0.55f
