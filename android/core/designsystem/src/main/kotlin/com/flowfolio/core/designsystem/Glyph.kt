package com.flowfolio.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * FlowFolio's glyphs: rounded outline icons in the style of the design's bell, on a 24-unit grid. Our own paths, so
 * no icon library is needed (ADR-0014).
 */
enum class Glyph(internal val pathData: String) {
    Bell(
        "M12 3.2a1.3 1.3 0 0 0-1.3 1.3v.6C7.9 5.8 6.4 8.2 6.4 11v4.2L4.6 18h14.8l-1.8-2.8V11" +
            "c0-2.8-1.5-5.2-4.3-5.9v-.6" +
            "A1.3 1.3 0 0 0 12 3.2z M10.2 19.4a1.8 1.8 0 0 0 3.6 0"
    ),
    Back("M15 5l-7 7 7 7"),
    Contents("M9 6.5h11 M9 12h11 M9 17.5h11 M4.5 6.5h.01 M4.5 12h.01 M4.5 17.5h.01"),
    GoTo("M6 3.5h8l4.5 4.5v12.5H6z M14 3.5V8h4.5 M9 14h6 M12.5 11.5L15 14l-2.5 2.5"),
    Settings("M4 7h9 M17 7h3 M15 5v4 M4 17h3 M11 17h9 M9 15v4"),
    Sort("M7 4.5v15 M4 16.5l3 3 3-3 M17 19.5v-15 M14 7.5l3-3 3 3"),
    Add("M12 5v14 M5 12h14"),
    Book("M4.5 5.5c2.5-1 5-1 7.5.8 2.5-1.8 5-1.8 7.5-.8v13c-2.5-1-5-1-7.5.8-2.5-1.8-5-1.8-7.5-.8z M12 6.3v13.5")
}

/**
 * Draws [glyph] in [color] (default: the theme's glyph colour) with the mode's effect: a glow in its own colour in
 * dark mode, a soft drop shadow in light mode.
 */
@Composable
fun FlowFolioGlyph(
    glyph: Glyph,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    color: Color = FlowFolio.palette.glyph,
    size: Dp = 24.dp
) {
    val dark = FlowFolio.palette.dark
    val path = remember(glyph) { PathParser().parsePathString(glyph.pathData).toPath() }
    val described = if (contentDescription != null) {
        Modifier.semantics { this.contentDescription = contentDescription }
    } else {
        Modifier
    }
    Canvas(modifier.then(described).size(size)) {
        val unit = this.size.minDimension / GRID
        val scaled = android.graphics.Path(path.asAndroidPath()).apply {
            transform(android.graphics.Matrix().apply { setScale(unit, unit) })
        }
        val width = STROKE * unit
        drawSoft(color, glow = dark) { paint ->
            paint.style = android.graphics.Paint.Style.STROKE
            paint.strokeWidth = width * (if (dark) GLOW_WIDEN else 1f)
            paint.strokeCap = android.graphics.Paint.Cap.ROUND
            paint.strokeJoin = android.graphics.Paint.Join.ROUND
            save()
            translate(0f, if (dark) 0f else unit * DROP)
            drawPath(scaled, paint)
            restore()
        }
        drawPath(scaled.asComposePath(), color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

private const val GRID = 24f
private const val STROKE = 2f
private const val GLOW_WIDEN = 1.6f
private const val DROP = 0.9f
