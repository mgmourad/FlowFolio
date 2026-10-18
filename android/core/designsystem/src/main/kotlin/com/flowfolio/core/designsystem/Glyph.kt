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
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * FlowFolio's glyphs on a 24-unit grid: the mock's own icons (ADR-0015) plus a few reader ones in the same style.
 * [stroke] is the mock's `stroke-width`; [filled] glyphs are drawn solid.
 */
enum class Glyph(internal val pathData: String, internal val stroke: Float = 2f, internal val filled: Boolean = false) {
    Back("M15 18l-6-6 6-6", stroke = 2.4f),
    Search("M18 11a7 7 0 1 1-14 0a7 7 0 1 1 14 0z M20 20l-3.5-3.5"),
    Add("M12 5v14 M5 12h14", stroke = 2.2f),
    More(
        "M7 12a2 2 0 1 1-4 0a2 2 0 1 1 4 0z M14 12a2 2 0 1 1-4 0a2 2 0 1 1 4 0z M21 12a2 2 0 1 1-4 0a2 2 0 1 1 4 0z",
        filled = true
    ),
    Bookmark("M6 3h12v18l-6-4-6 4V3z", stroke = 2.2f),
    Play("M7 4v16l13-8z", filled = true),
    Library("M4 19V5a1 1 0 0 1 1-1h3v15 M8 19V4h4v15 M13.5 5.2l3.9-1 3.6 14.6-3.9 1z M3 20h18", stroke = 1.9f),
    Shelves("M6 3h12a3 3 0 0 1 3 3v12a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V6a3 3 0 0 1 3-3z M3 10h18 M3 16h18", stroke = 1.9f),
    Settings(
        "M15 12a3 3 0 1 1-6 0a3 3 0 1 1 6 0z " +
            "M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21" +
            "a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1" +
            "a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8" +
            "l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1" +
            "a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9" +
            "a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z",
        stroke = 1.9f
    ),
    Bell("M6 16V11a6 6 0 1 1 12 0v5l2 2H4l2-2z M10 20a2 2 0 0 0 4 0 M12 3v2"),
    Contents("M9 6.5h11 M9 12h11 M9 17.5h11 M4.5 6.5h.01 M4.5 12h.01 M4.5 17.5h.01"),
    GoTo("M6 3.5h8l4.5 4.5v12.5H6z M14 3.5V8h4.5 M9 14h6 M12.5 11.5L15 14l-2.5 2.5"),
    Sort("M7 4.5v15 M4 16.5l3 3 3-3 M17 19.5v-15 M14 7.5l3-3 3 3"),
    Book("M4.5 5.5c2.5-1 5-1 7.5.8 2.5-1.8 5-1.8 7.5-.8v13c-2.5-1-5-1-7.5.8-2.5-1.8-5-1.8-7.5-.8z M12 6.3v13.5")
}

/**
 * Draws [glyph] in [color] (default: the theme's accent) with the mock's effect: `drop-shadow(0 0 7px)` in its own
 * colour in dark mode, `drop-shadow(0 3px 3px)` in light mode.
 */
@Composable
fun FlowFolioGlyph(
    glyph: Glyph,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    color: Color = FlowFolio.palette.glyph,
    size: Dp = 24.dp,
    glow: Boolean = true
) {
    val p = FlowFolio.palette
    val effectGlow = if (glow) p.glyphGlow else Glow.None
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
        val width = glyph.stroke * unit
        if (effectGlow.color.alpha > 0f) {
            // Dark: a glow in the glyph's own colour; light: the palette's soft shadow colour, dropped.
            val effect = if (p.dark) color.copy(alpha = effectGlow.color.alpha) else effectGlow.color
            blurred(effect, effectGlow.blur.toPx() * 2f) { paint ->
                paint.style =
                    if (glyph.filled) android.graphics.Paint.Style.FILL else android.graphics.Paint.Style.STROKE
                paint.strokeWidth = width
                paint.strokeCap = android.graphics.Paint.Cap.ROUND
                paint.strokeJoin = android.graphics.Paint.Join.ROUND
                save()
                translate(0f, effectGlow.offsetY.toPx())
                drawPath(scaled, paint)
                restore()
            }
        }
        val style = if (glyph.filled) Fill else Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawPath(scaled.asComposePath(), color, style = style)
    }
}

private const val GRID = 24f
