// The AMOLED theme's tokens (owner, M4), retoned for Quiet Library from the mock's `th-amoled` (ADR-0052).
@file:Suppress("MagicNumber")

package com.flowfolio.core.designsystem

import androidx.compose.ui.graphics.Color

/**
 * AMOLED (owner, 2026-11-02): FlowFolio dark with the lights behind it switched off. On an OLED panel a black pixel is
 * an unlit pixel, so the backgrounds and the page are `#000000` exactly; surfaces lift by a few points only, enough to
 * tell a card from the page behind it. Ink and accent are FlowFolio dark's.
 *
 * It is a dark theme in both of [PaletteFamily]'s slots: picking Light while AMOLED is on keeps the black.
 */
val AmoledTokens = DarkTokens.copy(
    bg = Color(0xFF000000),
    bg2 = Color(0xFF000000),
    surface = Color(0xFF0B0B0B),
    surface2 = Color(0xFF151515),
    surface3 = Color(0xFF1F1F1F),
    line = Color(0xFF262626),
    paper = Color(0xFF000000),
    paperInk = Color(0xFFD9D4CC)
)

val AmoledPalette = quietPalette(AmoledTokens, dark = true).copy(
    // A shadow on black can only be read as a shape, so it is nearly opaque.
    shadow = Color(0xCC000000),
    paperShade = Color(0x99000000)
)
