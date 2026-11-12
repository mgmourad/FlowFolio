package com.flowfolio.core.conversion.stage

import com.flowfolio.core.conversion.ir.Line
import com.flowfolio.core.conversion.ir.OBJECT
import com.flowfolio.core.conversion.ir.Page
import com.flowfolio.core.conversion.ir.PageKind
import com.flowfolio.core.conversion.ir.Span
import com.flowfolio.core.conversion.ir.StyleKey
import com.flowfolio.core.conversion.pdf.Box
import com.flowfolio.core.conversion.pdf.FontInfo
import com.flowfolio.core.conversion.pdf.RawPage
import kotlin.math.abs

/**
 * Stages A and B: a [RawPage] as a [Page] of lines. Characters are taken in PDFium's text order and cut into lines
 * where
 * PDFium puts a line break or the text jumps to another line; each line is cut into spans of one style, with a space
 * where characters stand apart without one.
 */
object Extraction {
    fun page(raw: RawPage): Page {
        val lines = lines(raw)
        val source = (0 until raw.length).count { i ->
            !raw.generated[i] && raw.hasBox(i) && !raw.text[i].isWhitespace()
        }
        val pageArea = raw.width * raw.height
        val imageArea = raw.images.sumOf { it.area.toDouble() }.toFloat()
        val kind = when {
            source == 0 && raw.images.isEmpty() && raw.paths.isEmpty() -> PageKind.EMPTY
            source < SCANNED_MAX_CHARS && imageArea > pageArea * SCANNED_IMAGE_SHARE -> PageKind.SCANNED
            else -> PageKind.BORN_DIGITAL
        }
        return Page(raw.index, raw.width, raw.height, kind, lines, raw.images, raw.paths, source)
    }

    // Page geometry: each early return or condition is one case of the layout, kept together to read as one rule.
    @Suppress("CyclomaticComplexMethod", "LoopWithTooManyJumpStatements", "ComplexCondition")
    private fun lines(raw: RawPage): List<Line> {
        val out = ArrayList<Line>()
        val current = ArrayList<Int>()
        fun flush() {
            lineOf(raw, current)?.let(out::add)
            current.clear()
        }
        for (i in 0 until raw.length) {
            val c = raw.text[i]
            if (c == OBJECT) continue // stands for our own inline drawings, never taken from the PDF
            if (c == '\r' || c == '\n') {
                flush()
                continue
            }
            if (!raw.hasBox(i)) {
                if (raw.generated[i] && c == ' ' && current.isNotEmpty()) current += i
                continue
            }
            // A space the PDF wrote never starts a line by itself: some PDFs give them a flat box on the baseline,
            // which overlaps no letter's band (the owner's Arabic book, 2026-11-11); the next letter decides. Spaces
            // PDFium makes up keep cutting the line, then [joinFragments] puts close pieces back: across the wide gap
            // between an unruled table's columns they must stay apart (`technical.pdf`).
            val ownSpace = c.isWhitespace() && !raw.generated[i]
            if (current.isNotEmpty() && !ownSpace && breaksLine(raw, current, i)) flush()
            current += i
        }
        flush()
        return joinFragments(out)
    }

    /** [line] carries on [prev] on its row: it starts where [prev] ends, give or take an em or two. */
    private fun continues(prev: Line, line: Line): Boolean {
        val em = line.style.size.coerceAtLeast(1f)
        val gap = if (TextDirection.rightToLeft(prev.text)) {
            prev.box.left - line.box.right
        } else {
            line.box.left -
                prev.box.right
        }
        return sameRow(prev.box, line.box) && gap > -em && gap < em * JOIN_GAP_EMS
    }

    /**
     * Pieces of one visual line that PDFium put on separate lines (text interrupted by a drawn equation, a change of
     * text object): consecutive lines on the same row, the second starting where the first ends.
     */
    private fun joinFragments(lines: List<Line>): List<Line> {
        val out = ArrayList<Line>()
        for (line in lines) {
            val prev = out.lastOrNull()
            if (prev != null && continues(prev, line)) {
                val space = if (prev.text.endsWith(' ') || line.text.startsWith(' ')) {
                    emptyList()
                } else {
                    listOf(
                        Span(
                            " ",
                            Box(
                                minOf(prev.box.right, line.box.right),
                                prev.box.top,
                                maxOf(prev.box.left, line.box.left),
                                prev.box.bottom
                            ),
                            prev.style,
                            floatArrayOf(minOf(prev.box.right, line.box.right))
                        )
                    )
                }
                out[out.lastIndex] = Line(prev.spans + space + line.spans, prev.box.union(line.box))
            } else {
                out += line
            }
        }
        return out
    }

    /**
     * Character [i] starts a new line: it sits beside the line's band, or the text jumps back to where lines start --
     * the left for a line that reads left to right, the right for Arabic or Hebrew. PDFium gives right-to-left text in
     * reading order, so each word of an Arabic line lies to the *left* of the one before; read as a jump back, that cut
     * every word into a line of its own and the words came out reversed (the owner's Arabic book, 2026-11-11).
     */
    private fun breaksLine(raw: RawPage, line: List<Int>, i: Int): Boolean {
        // Measured against the last letter, not a space, for the same reason.
        val last = line.lastOrNull { raw.hasBox(it) && !raw.text[it].isWhitespace() }
            ?: line.lastOrNull { raw.hasBox(it) } ?: return false
        val a = raw.box(last)
        val b = raw.box(i)
        val overlap = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
        val height = minOf(a.height, b.height).coerceAtLeast(1f)
        val backward = if (TextDirection.rightToLeft(raw, line)) {
            b.left > a.right + height
        } else {
            b.right <
                a.left - height
        }
        return overlap < height * MIN_LINE_OVERLAP || backward
    }

    // Page geometry: each early return or condition is one case of the layout, kept together to read as one rule.
    @Suppress("CyclomaticComplexMethod", "ReturnCount", "ComplexCondition")
    private fun lineOf(raw: RawPage, chars: List<Int>): Line? {
        val boxed = chars.filter { raw.hasBox(it) }
        if (boxed.none { !raw.text[it].isWhitespace() }) return null
        val typicalGap = typicalGap(raw, chars)
        val spans = ArrayList<Span>()
        val text = StringBuilder()
        var box: Box? = null
        var style: StyleKey? = null
        var lastRight = Float.NaN
        var lastLeft = Float.NaN
        val lefts = ArrayList<Float>()
        fun close() {
            val b = box
            val s = style
            if (b != null && s != null && text.isNotEmpty()) spans += Span(text.toString(), b, s, lefts.toFloatArray())
            text.clear()
            lefts.clear()
            box = null
        }
        fun space() {
            text.append(' ')
            lefts += if (lastRight.isNaN()) box?.right ?: 0f else lastRight
        }
        for (i in chars) {
            if (!raw.hasBox(i)) {
                if (text.isNotEmpty() && text.last() != ' ') space()
                continue
            }
            val b = raw.box(i)
            val key = styleOf(raw, i)
            if (style != null && key != style && !raw.text[i].isWhitespace()) close()
            if (!raw.text[i].isWhitespace()) style = key
            // A quarter em *more than this line's usual gap* is a word space the PDF left out. On ordinary text
            // the usual gap is nil and this is the plain quarter-em rule; [typicalGap] explains the rest.
            val gap = GlyphMetrics.gapBetween(lastLeft, lastRight, b)
            if (!lastRight.isNaN() && gap > typicalGap + GlyphMetrics.emOf(raw.fontSizes[i], b) * WORD_GAP_EMS &&
                text.isNotEmpty() &&
                text.last() != ' ' && !raw.text[i].isWhitespace()
            ) {
                space()
            }
            // PDFium reports a hyphen that breaks a word at the line's end as 0x02: it is the hyphen the page shows,
            // and Blocks joins the word again ("Func-" "tion" is "Function").
            text.append(if (raw.text[i] == LINE_END_HYPHEN) '-' else raw.text[i])
            lefts += b.left
            box = box?.union(b) ?: b
            lastRight = b.right
            lastLeft = b.left
        }
        close()
        if (spans.isEmpty()) return null
        val lineBox = spans.map { it.box }.reduce(Box::union)
        return Line(spans, lineBox)
    }

    /**
     * The gap this line leaves between characters *within* a word, which a word space has to beat.
     *
     * The plain rule -- a quarter em of empty space means a word space the PDF left out -- reads two common
     * layouts as all spaces. Letter-spaced display text ("w w w . p a c k t . c o m" set with tracking) stands
     * every glyph a quarter em from the next, and monospaced code does the same by accident, because PDFium
     * reports each glyph's tight bounding box while the font advances a full, wider cell: `.` and `i` leave most
     * of their cell empty. Both came out of a Packt PDF as one word per character (owner, 2026-11-03).
     *
     * What separates them from real spacing is the *distribution*: ordinary text has gaps near nil and a few wide
     * ones, while these have nearly every gap wide and alike. The low quartile of the gaps is therefore what a
     * within-word pair costs on this line, and a word space has to clear it by the usual quarter em -- roughly a
     * space's own width, which is what a real space adds on top of any tracking. On ordinary text the quartile is
     * nil and the rule is exactly the old one.
     *
     * The quartile rather than the median, because a line of short words ("I am a cat") is half word gaps, and a
     * median would land on one and merge the lot. A quarter still lands among the within-word gaps unless nearly
     * every word is a single letter, which is indistinguishable from tracking anyway.
     */
    private fun typicalGap(raw: RawPage, chars: List<Int>): Float {
        val gaps = ArrayList<Float>()
        var lastRight = Float.NaN
        var lastLeft = Float.NaN
        for (i in chars) {
            if (!raw.hasBox(i)) {
                lastRight = Float.NaN
                continue
            }
            val b = raw.box(i)
            if (!lastRight.isNaN() &&
                !raw.text[i].isWhitespace()
            ) {
                gaps += GlyphMetrics.gapBetween(lastLeft, lastRight, b)
            }
            lastRight = b.right
            lastLeft = b.left
        }
        // Too few gaps to tell a habit from an accident; fall back to the plain rule.
        if (gaps.size < MIN_GAPS_FOR_QUARTILE) return 0f
        gaps.sort()
        return gaps[gaps.size / QUARTILE].coerceAtLeast(0f)
    }

    /** The style key of character [i]. */
    fun styleOf(raw: RawPage, i: Int): StyleKey {
        val font = raw.fontIds[i].takeIf { it >= 0 }?.let { raw.fonts[it] } ?: FontInfo("", 0)
        val name = font.name.substringAfter('+')
        val bold = raw.fontWeights[i] >= BOLD_WEIGHT || BOLD_NAMES.any { name.contains(it, ignoreCase = true) }
        val size = (raw.fontSizes[i] * 2).toInt() / 2f
        return StyleKey(familyOf(name), size, bold, font.italic, font.monospace)
    }

    /** `ABCDEF+Mulish-BoldItalic` and `Mulish,Bold` are both family `Mulish`. */
    fun familyOf(name: String): String = name.substringAfter('+').split('-', ',').first().trim()
        .removeSuffix("MT").removeSuffix("PS").ifEmpty { "unknown" }

    /** Two lines' tops are close enough to be the same line of text (used by later stages). */
    fun sameRow(a: Box, b: Box): Boolean =
        abs((a.top + a.bottom) - (b.top + b.bottom)) / 2 < minOf(a.height, b.height) / 2

    private const val LINE_END_HYPHEN = '\u0002'
    private const val MIN_LINE_OVERLAP = 0.5f
    private const val JOIN_GAP_EMS = 1f

    /**
     * How much wider than a within-word gap a real word space is. Raised from the plain quarter em (2026-11-06) so a
     * justified line that stretches its within-word gaps unevenly does not read some of them as spaces ("ver y").
     * Untested against the DevOps book the owner reported it from; revisit with that book to hand.
     */
    private const val WORD_GAP_EMS = 0.32f
    private const val MIN_GAPS_FOR_QUARTILE = 3
    private const val QUARTILE = 4
    private const val BOLD_WEIGHT = 600
    private const val SCANNED_MAX_CHARS = 20
    private const val SCANNED_IMAGE_SHARE = 0.5f
    private val BOLD_NAMES = listOf("Bold", "Black", "Heavy", "Semibold", "SemiBold", "Demi")
}
