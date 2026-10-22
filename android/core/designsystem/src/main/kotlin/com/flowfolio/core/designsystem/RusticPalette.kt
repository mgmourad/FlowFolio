// The Rustic theme's tokens (ADR-0028), each value chosen by eye on the reference Fold.
@file:Suppress("MagicNumber")

package com.flowfolio.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Rustic, dark: the classic wood of a bookshelf (#48120C, owner 2026-10-22) and deeper walnut, cream text (#F6E4CA),
 * honey-brass accents, terracotta for warnings, and warm candle-light glows. The background is laid with wood grain.
 */
val RusticDarkPalette = Palette(
    dark = true,
    background = Color(0xFF48120C),
    backgroundDeep = Color(0xFF2A0A06),
    surface = Color(0xFF5A1A10),
    surface2 = Color(0xFF6E2516),
    edge = Color(0x33F6E4CA),
    title = Color(0xFFF2B866),
    item = Color(0xFFF6E4CA),
    hint = Color(0xFFCDAE90),
    subItem = Color(0xFFEB8A62),
    glyph = Color(0xFFE0A85E),
    text = Color(0xFFF6E4CA),
    toggleOffTrack = Color(0xFF7A5442),
    toggleOffThumb = Color(0xFF2E0B07),
    toggleOnTrack = Color(0xFF8E9B55),
    toggleOnThumb = Color(0xFFF2B866),
    cardStart = Color(0xFF66200F),
    cardEnd = Color(0xFF3E0F08),
    paper = Color(0xFF4A1008),
    paperInk = Color(0xFFF6E4CA),
    paperShade = Color(0x80000000),
    shelfTop = Color(0xFF8A4522),
    shelfBottom = Color(0xFF4A1C0B),
    tabBar = Color(0xCC2A0A06),
    buttonTop = Color(0xFFF4C27A),
    buttonBottom = Color(0xFFC98633),
    buttonInk = Color(0xFF2A0A06),
    titleGlow = Glow(Color(0x73F2A64B), 16.dp),
    itemGlow = Glow(Color(0x33F6E4CA), 10.dp),
    subGlow = Glow(Color(0x59EB8A62), 10.dp),
    hintGlow = Glow.None,
    glyphGlow = Glow(Color(0x8CE0A85E), 6.dp),
    selected = Color(0xFF7A2C19),
    shadow = Color(0xCC120302),
    pressShadow = Color(0x99120302),
    highlight = Color(0x1FFFE2BC),
    green = Color(0xFFA9C46C),
    texture = SurfaceTexture.WOOD,
    buttonGlyph = Color(0xFF7A2A18)
)

/**
 * Rustic, light: parchment with walnut ink and the same brass accents; lights are soft warm shadows, as in daylight.
 * The background is laid with faint wood grain.
 */
val RusticLightPalette = Palette(
    dark = false,
    background = Color(0xFFF3E2C4),
    backgroundDeep = Color(0xFFE2C79C),
    surface = Color(0xFFFAEFDA),
    surface2 = Color(0xFFEFD9B2),
    edge = Color(0x3348120C),
    title = Color(0xFF6A1E12),
    item = Color(0xFF48120C),
    hint = Color(0xFF6E5442),
    subItem = Color(0xFFA63A22),
    glyph = Color(0xFF9A5A1C),
    text = Color(0xFF2E120A),
    toggleOffTrack = Color(0xFFB09C88),
    toggleOffThumb = Color(0xFF6E3A24),
    toggleOnTrack = Color(0xFF6E8B3D),
    toggleOnThumb = Color(0xFF48120C),
    cardStart = Color(0xFFFFF7E6),
    cardEnd = Color(0xFFF0DBB4),
    paper = Color(0xFFFBF3E2),
    paperInk = Color(0xFF2E120A),
    paperShade = Color(0x4750280F),
    shelfTop = Color(0xFF8A4A24),
    shelfBottom = Color(0xFF552511),
    tabBar = Color(0xD9FAEFDA),
    buttonTop = Color(0xFF7A2A18),
    buttonBottom = Color(0xFF48120C),
    buttonInk = Color(0xFFF6E4CA),
    titleGlow = Glow(Color(0x4048120C), 3.dp, 2.dp),
    itemGlow = Glow(Color(0x2948120C), 2.dp, 1.dp),
    subGlow = Glow(Color(0x26A63A22), 2.dp, 1.dp),
    hintGlow = Glow(Color(0x14000000), 2.dp, 1.dp),
    glyphGlow = Glow(Color(0x339A5A1C), 3.dp, 2.dp),
    selected = Color(0xFFFFF8EA),
    shadow = Color(0x80502810),
    pressShadow = Color(0x4D502810),
    highlight = Color(0xBFFFFFFF),
    green = Color(0xFF4E7A2C),
    texture = SurfaceTexture.WOOD,
    buttonGlyph = Color(0xFFF2B866)
)
