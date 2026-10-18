package com.flowfolio.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.flowfolio.core.model.BookmarkColor
import com.flowfolio.core.model.HighlightColor

/** The colour of a ribbon in the current theme (bookmark colours are the app's colour roles, plus a green). */
val BookmarkColor.color: Color
    @Composable @ReadOnlyComposable
    get() {
        val p = FlowFolio.palette
        return when (this) {
            BookmarkColor.SUB_ITEM -> p.subItem
            BookmarkColor.TITLE -> p.title
            BookmarkColor.ITEM -> p.item
            BookmarkColor.ACCENT -> p.glyph
            BookmarkColor.GREEN -> p.green
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
    }
