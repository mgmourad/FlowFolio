// The Rustic theme's tokens (ADR-0028), retoned for Quiet Library from the mock's `th-rustic` (ADR-0052).
@file:Suppress("MagicNumber")

package com.flowfolio.core.designsystem

import androidx.compose.ui.graphics.Color

/**
 * Rustic, light: parchment and a deep oxblood accent. Values the mock does not override (ok, danger, scrim, the
 * accent's ink) are FlowFolio light's.
 */
val RusticLightTokens = LightTokens.copy(
    bg = Color(0xFFEBD9BB),
    bg2 = Color(0xFFE2CDA8),
    surface = Color(0xFFF6EAD4),
    surface2 = Color(0xFFE7D4B2),
    surface3 = Color(0xFFDCC59D),
    line = Color(0xFFD5BE96),
    ink = Color(0xFF3A1E10),
    ink2 = Color(0xFF6E4A30),
    ink3 = Color(0xFF957559),
    accent = Color(0xFF7A2E14),
    accentSoft = Color(0xFFEAC9AE),
    paper = Color(0xFFF3E3C3),
    paperInk = Color(0xFF3A2412)
)

/** Rustic, dark: walnut wood, cream ink and a honey-brass accent. */
val RusticDarkTokens = DarkTokens.copy(
    bg = Color(0xFF2A140C),
    bg2 = Color(0xFF220F08),
    surface = Color(0xFF3A1E12),
    surface2 = Color(0xFF4A2818),
    surface3 = Color(0xFF5A321E),
    line = Color(0xFF5E3A26),
    ink = Color(0xFFF3DFC8),
    ink2 = Color(0xFFCFB096),
    ink3 = Color(0xFF9C7E66),
    accent = Color(0xFFE3A15E),
    accentSoft = Color(0xFF4E2E1A),
    paper = Color(0xFF3A1610),
    paperInk = Color(0xFFF1D9C4)
)

val RusticLightPalette = quietPalette(RusticLightTokens, dark = false)

val RusticDarkPalette = quietPalette(RusticDarkTokens, dark = true)
