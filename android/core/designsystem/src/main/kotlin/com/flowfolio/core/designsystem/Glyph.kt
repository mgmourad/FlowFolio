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
    Book("M4.5 5.5c2.5-1 5-1 7.5.8 2.5-1.8 5-1.8 7.5-.8v13c-2.5-1-5-1-7.5.8-2.5-1.8-5-1.8-7.5-.8z M12 6.3v13.5"),
    Folder("M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z M3 10h18", stroke = 1.9f),
    Edit("M4 20h4L18.5 9.5a2.1 2.1 0 0 0-3-3L5 17v3z M13.5 8.5l2 2"),
    Trash("M4 7h16 M10 11v6 M14 11v6 M6 7l1 13h10l1-13 M9 7V4h6v3"),

    /** A highlighter over a line of text (annotations). */
    Highlight("M14.5 4.5l5 5L11 18H6v-5z M12.5 6.5l5 5 M4 21h16"),

    /** A large and a small letter A: text settings. */
    TextSize("M3 19L8 5l5 14 M4.8 14.5h6.4 M14 19l3.5-9 3.5 9 M15.2 16h4.6"),

    /** A sheet of paper with a folded corner: page settings. */
    Page("M6 3h8.5L19 7.5V21H6z M14.5 3v4.5H19 M9 12h7 M9 15.5h7"),

    /** Three sliders: controls and sound. */
    Sliders("M4 6h9 M17 6h3 M4 12h3 M11 12h9 M4 18h11 M19 18h1 M15 4v4 M9 10v4 M17 16v4"),

    /** A tick in a circle: select mode. */
    Select("M12 3a9 9 0 1 1 0 18a9 9 0 1 1 0-18z M8 12.4l2.8 2.8L16.2 9.6"),

    /** A tick: chosen. */
    Check("M5 12.5l4.5 4.5L19 7.5", stroke = 2.6f),

    /** An hourglass: books in progress. */
    Hourglass(
        "M6 3h12 M6 21h12 M7.5 3v2.5c0 3 4.5 4.5 4.5 6.5s-4.5 3.5-4.5 6.5V21 " +
            "M16.5 3v2.5c0 3-4.5 4.5-4.5 6.5s4.5 3.5 4.5 6.5V21"
    ),

    /** A page whose lines flow into waves: Dynamic Flow, the book reflowed to fit the screen (ADR-0045). */
    DynamicFlow(
        "M6 3h8.5L19 7.5V21H6z M14.5 3v4.5H19 M8.5 7.5h3.5 " +
            "M8.5 11.5c1.3-1 2.7-1 4 0s2.7 1 4 0 M8.5 15.5c1.3-1 2.7-1 4 0s2.7 1 4 0"
    ),

    /** A stopwatch: time spent reading. */
    Stopwatch("M19 14a7 7 0 1 1-14 0a7 7 0 1 1 14 0z M12 14v-4 M10 2.5h4 M12 2.5V7 M17.5 7.5l1.5-1.5"),

    /** A clock face: read time. */
    Clock("M21 12a9 9 0 1 1-18 0a9 9 0 1 1 18 0z M12 7v5l3.2 2"),

    /** An open book seen from the front, its pages fanned (the owner's "Progress" picture). */
    OpenBook(
        "M12 7.5c-2.8-2-6-2.3-9-1.2V18.5c3-1.1 6.2-.8 9 1.2 2.8-2 6-2.3 9-1.2V6.3c-3-1.1-6.2-.8-9 1.2z M12 7.5v12.2 " +
            "M1.5 8.5v12c3.5-1.1 7.3-.6 10.5 1 3.2-1.6 7-2.1 10.5-1v-12"
    ),

    /** Lines with arrows between them: line spacing. */
    LineSpacing("M10 6h10 M10 12h10 M10 18h10 M5 4v16 M3 6.5L5 4l2 2.5 M3 17.5L5 20l2-2.5"),

    /** A page's edges with text inside: margins. */
    Margins("M4 3v18 M20 3v18 M8 7h8 M8 11h8 M8 15h8 M8 19h5"),

    /** Lines of the same length: justified text. */
    Justify("M4 6h16 M4 10h16 M4 14h16 M4 18h16"),

    /** A word broken by a hyphen at a line's end: hyphenation. */
    Hyphenate("M3 6h18 M3 11h11 M17 11h3 M3 16h18 M3 21h9"),

    /** A painter's palette: the paper. */
    Paper(
        "M12 3a9 9 0 1 0 0 18c1.4 0 2-1 2-2s-.6-1.4-.6-2.2c0-1 .8-1.8 1.8-1.8H17a4 4 0 0 0 4-4c0-4.4-4-8-9-8z " +
            "M7.5 12h.01 M9.5 7.5h.01 M14.5 7.5h.01",
        stroke = 2.2f
    ),

    /** A page with its corner turning: how pages turn. */
    PageTurn("M5 3h9l5 5v13H5z M14 3c0 3 1 5 5 5 M9 17c2.5 0 4.5-1 5.5-3"),

    /** Arrows each way: which way the pages run. */
    Direction("M4 8.5h14 M15 5.5l3 3-3 3 M20 15.5H6 M9 12.5l-3 3 3 3"),

    /** Two pages side by side: spreads. */
    Spread("M3 5.5c3-1 6-1 9 .8v13c-3-1.8-6-1.8-9-.8z M21 5.5c-3-1-6-1-9 .8v13c3-1.8 6-1.8 9-.8z"),

    /** A closed book: the cover on its own. */
    Cover("M6 3h12a1 1 0 0 1 1 1v14H8a2 2 0 0 0-2 2V5a2 2 0 0 1 2-2 M6 20a2 2 0 0 0 2 2h11v-4 M10 7.5h5"),

    /** An eye struck through: the controls hide. */
    AutoHide("M3 12s3.5-6 9-6 9 6 9 6-3.5 6-9 6-9-6-9-6z M14.5 12a2.5 2.5 0 1 1-5 0a2.5 2.5 0 1 1 5 0z M4 4l16 16"),

    /** A speaker: the page turn's sound. */
    Sound("M4 9.5h3.5L12 5.5v13l-4.5-4H4z M15.5 9a4 4 0 0 1 0 6 M18 6.5a7.5 7.5 0 0 1 0 11"),

    /** Pages fanning out: the riffle's sound. */
    Riffle("M4 20h16 M12 20L6 6 M12 20L9.5 5 M12 20l1-15.5 M12 20l3.5-14.5 M12 20l6-12"),

    /** A phone shaking: haptics. */
    Haptics("M8.5 4h7v16h-7z M5 8v8 M19 8v8 M2 10v4 M22 10v4 M11 17h2"),

    /** A star: a favourite. */
    Star("M12 3.5l2.6 5.3 5.9.9-4.3 4.1 1 5.8L12 16.8l-5.2 2.8 1-5.8-4.3-4.1 5.9-.9z")
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
