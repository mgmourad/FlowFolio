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
            if (current.isNotEmpty() && breaksLine(raw, current, i)) flush()
            current += i
        }
        flush()
        return joinFragments(out)
    }

    /** [line] carries on [prev] on its row: it starts where [prev] ends, give or take an em or two. */
    private fun continues(prev: Line, line: Line): Boolean {
        val em = line.style.size.coerceAtLeast(1f)
        val gap = line.box.left - prev.box.right
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
                            Box(prev.box.right, prev.box.top, line.box.left, prev.box.bottom),
                            prev.style,
                            floatArrayOf(prev.box.right)
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

    /** Character [i] starts a new line: it sits beside the line's band, or the text jumps back to the left. */
    private fun breaksLine(raw: RawPage, line: List<Int>, i: Int): Boolean {
        val last = line.lastOrNull { raw.hasBox(it) } ?: return false
        val a = raw.box(last)
        val b = raw.box(i)
        val overlap = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
        val height = minOf(a.height, b.height).coerceAtLeast(1f)
        val backward = b.right < a.left - height
        return overlap < height * MIN_LINE_OVERLAP || backward
    }

    // Page geometry: each early return or condition is one case of the layout, kept together to read as one rule.
    @Suppress("CyclomaticComplexMethod", "ReturnCount", "ComplexCondition")
    private fun lineOf(raw: RawPage, chars: List<Int>): Line? {
        val boxed = chars.filter { raw.hasBox(it) }
        if (boxed.none { !raw.text[it].isWhitespace() }) return null
        val spans = ArrayList<Span>()
        val text = StringBuilder()
        var box: Box? = null
        var style: StyleKey? = null
        var lastRight = Float.NaN
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
            // A gap of a quarter em or more is a word space the PDF left out.
            val gap = b.left - lastRight
            if (!lastRight.isNaN() && gap > raw.fontSizes[i] * WORD_GAP_EMS && text.isNotEmpty() &&
                text.last() != ' '
            ) {
                space()
            }
            // PDFium reports a hyphen that breaks a word at the line's end as 0x02: it is the hyphen the page shows,
            // and Blocks joins the word again ("Func-" "tion" is "Function").
            text.append(if (raw.text[i] == LINE_END_HYPHEN) '-' else raw.text[i])
            lefts += b.left
            box = box?.union(b) ?: b
            lastRight = b.right
        }
        close()
        if (spans.isEmpty()) return null
        val lineBox = spans.map { it.box }.reduce(Box::union)
        return Line(spans, lineBox)
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
    private const val WORD_GAP_EMS = 0.25f
    private const val BOLD_WEIGHT = 600
    private const val SCANNED_MAX_CHARS = 20
    private const val SCANNED_IMAGE_SHARE = 0.5f
    private val BOLD_NAMES = listOf("Bold", "Black", "Heavy", "Semibold", "SemiBold", "Demi")
}
