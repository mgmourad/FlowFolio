package com.flowfolio.core.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The fine texture over the app background (ADR-0028). */
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
 * FlowFolio's design tokens, verbatim from the owner's mock (`docs/design/FlowFolio_Mock.html`, ADR-0015). Property
 * names follow the mock's CSS variables; [glyph] and [edge] keep their earlier names (ADR-0014) as aliases of the
 * mock's `accent` and `line`.
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
    val title: Color,
    val item: Color,
    val hint: Color,
    val subItem: Color,
    /** Icons (`--accent`). */
    val glyph: Color,
    /** Body text on app surfaces (`--text`). */
    val text: Color,
    val toggleOffTrack: Color,
    val toggleOffThumb: Color,
    val toggleOnTrack: Color,
    val toggleOnThumb: Color,
    /** Card fill: a 160-degree gradient from [cardStart] to [cardEnd]. */
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
    /** The selected segment / tab indicator fill (`--surface-2`, or warm white in light mode). */
    val selected: Color,
    /** Soft card shadow colour (`--shadow-soft`). */
    val shadow: Color,
    /** Pressed / sunken inner shadow colour (`--shadow-press`). */
    val pressShadow: Color,
    /** Top inner highlight on cards (`inset 0 1px 0`). */
    val highlight: Color,
    /** A green in the palette's own tone (bookmark colours; not in the mock's swatches). */
    val green: Color,
    /** What the app background is laid with (ADR-0028): the mock's grain, or none (Rustic, Material). */
    val texture: SurfaceTexture = SurfaceTexture.GRAIN,
    /** The icon on a primary button: the mock's pink play on orange; the button's ink where the accent would vanish. */
    val buttonGlyph: Color = glyph
) {
    /** The mock's "Neon glow" switch off: no glows or text shadows. */
    fun withoutGlow(): Palette = copy(
        titleGlow = Glow.None,
        itemGlow = Glow.None,
        subGlow = Glow.None,
        hintGlow = Glow.None,
        glyphGlow = Glow.None
    )
}

val DarkPalette = Palette(
    dark = true,
    background = Color(0xFF1D0A47),
    backgroundDeep = Color(0xFF0F0428),
    surface = Color(0xFF26105A),
    surface2 = Color(0xFF311770),
    edge = Color(0x26AA96FF),
    title = Color(0xFFFF8A00),
    item = Color(0xFF00F0FF),
    hint = Color(0xFF9B9AA6),
    subItem = Color(0xFFFF2D5A),
    glyph = Color(0xFFC02F9A),
    text = Color(0xFFECE8FF),
    toggleOffTrack = Color(0xFF8E8E96),
    toggleOffThumb = Color(0xFF3B2F90),
    // On-state colours swapped with the light palette at the owner's request (2026-10-18).
    toggleOnTrack = Color(0xFF2AA3CF),
    toggleOnThumb = Color(0xFFFF7A00),
    cardStart = Color(0xFF2D1568),
    cardEnd = Color(0xFF1F0A4E),
    paper = Color(0xFF1C1042),
    paperInk = Color(0xFFDDD6F3),
    paperShade = Color(0x73000000),
    shelfTop = Color(0xFF3A2394),
    shelfBottom = Color(0xFF22105C),
    tabBar = Color(0xB81A0940),
    buttonTop = Color(0xFFFFA133),
    buttonBottom = Color(0xFFFF7A00),
    buttonInk = Color(0xFF2A0C00),
    titleGlow = Glow(Color(0x8CFF8A00), 18.dp),
    itemGlow = Glow(Color(0x8000F0FF), 16.dp),
    subGlow = Glow(Color(0x73FF2D5A), 12.dp),
    hintGlow = Glow.None,
    glyphGlow = Glow(Color(0xBFC02F9A), 7.dp),
    selected = Color(0xFF311770),
    shadow = Color(0xB3000000),
    pressShadow = Color(0x73000000),
    highlight = Color(0x0FFFFFFF),
    green = Color(0xFF39F28F)
)

val LightPalette = Palette(
    dark = false,
    background = Color(0xFFFDEAC6),
    backgroundDeep = Color(0xFFF1D39C),
    surface = Color(0xFFFFF4DE),
    surface2 = Color(0xFFFBE6C0),
    edge = Color(0x29784614),
    title = Color(0xFF8B4D12),
    item = Color(0xFF0F5F59),
    hint = Color(0xFF4E4842),
    subItem = Color(0xFFC8102E),
    glyph = Color(0xFF4A2BB0),
    text = Color(0xFF2E2219),
    toggleOffTrack = Color(0xFF9B9B9B),
    toggleOffThumb = Color(0xFF7D3326),
    // On-state colours swapped with the dark palette at the owner's request (2026-10-18).
    toggleOnTrack = Color(0xFF2ECC8A),
    toggleOnThumb = Color(0xFFFF8A00),
    cardStart = Color(0xFFFFF8E8),
    cardEnd = Color(0xFFFBE4B9),
    paper = Color(0xFFFFFAF0),
    paperInk = Color(0xFF2C241C),
    paperShade = Color(0x47503214),
    shelfTop = Color(0xFFC88C51),
    shelfBottom = Color(0xFF94602F),
    tabBar = Color(0xC7FFF4DE),
    buttonTop = Color(0xFFFF9A2E),
    buttonBottom = Color(0xFFEC6A00),
    buttonInk = Color(0xFFFFF8EE),
    titleGlow = Glow(Color(0x475A320A), 3.dp, 2.dp),
    itemGlow = Glow(Color(0x400A3C37), 3.dp, 2.dp),
    subGlow = Glow(Color(0x33780A1E), 2.dp, 2.dp),
    hintGlow = Glow(Color(0x1F000000), 2.dp, 1.dp),
    glyphGlow = Glow(Color(0x4D281478), 3.dp, 3.dp),
    selected = Color(0xFFFFFAF0),
    shadow = Color(0x80784614),
    pressShadow = Color(0x4D784614),
    highlight = Color(0xBFFFFFFF),
    green = Color(0xFF2C7A2E)
)

val LocalPalette = staticCompositionLocalOf { LightPalette }
