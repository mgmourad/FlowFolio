package com.flowfolio.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.flowfolio.core.model.BookmarkColor

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
