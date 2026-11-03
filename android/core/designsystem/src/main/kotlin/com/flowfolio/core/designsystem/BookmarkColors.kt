@file:Suppress("MagicNumber") // Each marker colour is its own value; naming them would only repeat them.

package com.flowfolio.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.flowfolio.core.model.BookmarkColor
import com.flowfolio.core.model.HighlightColor

/**
 * The colour of a ribbon: the original FlowFolio design's colour roles (plus a green) in light or dark, whatever the
 * app's theme (owner, 2026-10-22), so a ribbon keeps its colour when the theme changes.
 */
val BookmarkColor.color: Color
    @Composable @ReadOnlyComposable
    get() {
        val p = if (FlowFolio.palette.dark) DarkPalette else LightPalette
        return when (this) {
            BookmarkColor.SUB_ITEM -> p.subItem

            BookmarkColor.TITLE -> p.title

            BookmarkColor.ITEM -> p.item

            BookmarkColor.ACCENT -> p.glyph

            BookmarkColor.GREEN -> p.green

            // The four added in M4b are fixed hues rather than palette roles: the five above are the design's own
            // colours, and there are no more roles to borrow without two ribbons coming out the same.
            BookmarkColor.TEAL -> Color(0xFF26C6DA)

            BookmarkColor.AMBER -> Color(0xFFFFB300)

            BookmarkColor.ROSE -> Color(0xFFFF4081)

            BookmarkColor.VIOLET -> Color(0xFF9575CD)
        }
    }

/** The highlighter colours: soft marker shades, the same in both themes (pages keep their own paper). */
val HighlightColor.marker: Color
    get() = when (this) {
        HighlightColor.YELLOW -> Color(0xFFFFD54F)
        HighlightColor.GREEN -> Color(0xFF81C784)
        HighlightColor.BLUE -> Color(0xFF64B5F6)
        HighlightColor.PINK -> Color(0xFFF48FB1)
        HighlightColor.PURPLE -> Color(0xFFB39DDB)
        HighlightColor.ORANGE -> Color(0xFFFFB74D)
        HighlightColor.TEAL -> Color(0xFF4DD0E1)
        HighlightColor.MINT -> Color(0xFFA5D6A7)
        HighlightColor.CORAL -> Color(0xFFFF8A65)
    }
