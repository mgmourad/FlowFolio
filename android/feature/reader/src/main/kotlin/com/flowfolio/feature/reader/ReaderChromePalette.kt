package com.flowfolio.feature.reader

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.flowfolio.core.designsystem.FlowFolioPalette
import com.flowfolio.core.designsystem.GlassStyle

/**
 * The reader's chrome glass (ADR-0056, owner 2026-11-12): dark on every paper. Light glass over a light or sepia
 * page left the tools' text too faint to read. The dark palette's own tint (its surface at 84%) is the scrim; a pale
 * hairline keeps the edge visible on dark papers, where dark glass over a dark page otherwise reads as a hole.
 */
internal fun readerChromeGlass(): GlassStyle = GlassStyle(edge = Color.White.copy(alpha = CHROME_EDGE))

/**
 * The dark palette for the reader's chrome (ADR-0056), its third ink lifted toward the second: the dark palette's
 * own third ink measured 2.8:1 on the panels, under WCAG's 4.5:1 for text; lifted 70% of the way it measures 4.7:1
 * and stays below the second ink (5.7:1), so the order of emphasis holds. Worked out from each theme's own inks.
 */
@Composable
internal fun ReaderChromePalette(content: @Composable () -> Unit) {
    FlowFolioPalette(dark = true) {
        val p = com.flowfolio.core.designsystem.LocalPalette.current
        val lifted = androidx.compose.ui.graphics.lerp(p.ink3, p.ink2, INK3_LIFT)
        androidx.compose.runtime.CompositionLocalProvider(
            com.flowfolio.core.designsystem.LocalPalette provides p.copy(tokens = p.tokens.copy(ink3 = lifted)),
            content = content
        )
    }
}

private const val INK3_LIFT = 0.7f

private const val CHROME_EDGE = 0.16f
