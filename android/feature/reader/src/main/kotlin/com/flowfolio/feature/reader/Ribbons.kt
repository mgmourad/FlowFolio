package com.flowfolio.feature.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.model.BookmarkColor

/** Ribbons by page, for the page panes. */
internal val LocalRibbons = staticCompositionLocalOf<Map<Int, BookmarkColor>> { emptyMap() }

/** The colour of a ribbon in the current theme (bookmark colours are the app's colour roles). */
internal val BookmarkColor.color: Color
    @Composable @ReadOnlyComposable
    get() {
        val p = FlowFolio.palette
        return when (this) {
            BookmarkColor.SUB_ITEM -> p.subItem
            BookmarkColor.TITLE -> p.title
            BookmarkColor.ITEM -> p.item
            BookmarkColor.ACCENT -> p.glyph
            BookmarkColor.HINT -> p.hint
        }
    }

/**
 * The mock's ribbon (`.ribbon`): a silk ribbon hanging from the top edge of the page with a swallowtail notch, shaded
 * darker at its edges. Drawn as part of the page, so it turns with it.
 */
@Composable
internal fun Ribbon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(RIBBON_WIDTH, RIBBON_HEIGHT)) {
        val w = size.width
        val h = size.height
        val notch = RIBBON_NOTCH.toPx()
        val ribbon = Path().apply {
            moveTo(0f, 0f)
            lineTo(w, 0f)
            lineTo(w, h)
            lineTo(w / 2f, h - notch)
            lineTo(0f, h)
            close()
        }
        // A thin darker copy below it reads as the ribbon's own shadow on the paper.
        drawPath(
            Path().apply { addPath(ribbon, Offset(1.5.dp.toPx(), 2.dp.toPx())) },
            Color.Black.copy(alpha = SHADOW_ALPHA)
        )
        drawPath(
            ribbon,
            Brush.horizontalGradient(
                0f to lerp(color, Color.Black, EDGE_DARKEN),
                CENTRE to color,
                1f to lerp(color, Color.Black, FAR_EDGE_DARKEN)
            )
        )
    }
}

private val RIBBON_WIDTH = 22.dp
private val RIBBON_HEIGHT = 62.dp
private val RIBBON_NOTCH = 9.dp
private const val EDGE_DARKEN = 0.25f
private const val FAR_EDGE_DARKEN = 0.15f
private const val CENTRE = 0.4f
private const val SHADOW_ALPHA = 0.18f
