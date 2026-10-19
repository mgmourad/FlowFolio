package com.flowfolio.feature.reader

import com.flowfolio.core.model.PageRect
import com.flowfolio.core.pdf.PageText
import kotlin.math.abs

/** Selecting text on a page from its character boxes (PDFium's reading order); positions are page fractions. */
internal object TextSelection {
    /**
     * The character at ([x], [y]): one whose box contains the point; else the nearest on the line the point is on
     * (within [reach] of the page's width); else the nearest anywhere within [reach] (a press between lines or just
     * beside the text still finds a word); else none.
     */
    fun charAt(text: PageText, x: Float, y: Float, reach: Float = LINE_REACH): Int? {
        var onLine: Int? = null
        var lineDistance = Float.MAX_VALUE
        var near: Int? = null
        var nearDistance = Float.MAX_VALUE
        for (i in 0 until text.length) {
            if (!text.hasBox(i)) continue
            val dx = gap(x, text.left(i), text.right(i))
            val dy = gap(y, text.top(i), text.bottom(i))
            if (dx == 0f && dy == 0f) return i
            if (dy == 0f && dx < lineDistance) {
                lineDistance = dx
                onLine = i
            }
            val d = dx * dx + dy * dy
            if (d < nearDistance) {
                nearDistance = d
                near = i
            }
        }
        return onLine?.takeIf { lineDistance <= reach } ?: near?.takeIf { nearDistance <= reach * reach }
    }

    /** How far [v] lies outside [from]..[to] (0 inside). */
    private fun gap(v: Float, from: Float, to: Float) = if (v < from) {
        from - v
    } else if (v > to) {
        v - to
    } else {
        0f
    }

    /** The word around character [i]: letters, digits and apostrophes on either side. */
    fun wordAt(text: PageText, i: Int): IntRange {
        if (!isWordChar(text.text[i])) return i..i
        var start = i
        var end = i
        while (start > 0 && isWordChar(text.text[start - 1])) start--
        while (end < text.length - 1 && isWordChar(text.text[end + 1])) end++
        return start..end
    }

    /** One rectangle per line for [range]: consecutive boxes on the same line are joined. */
    fun rects(text: PageText, range: IntRange): List<PageRect> {
        val out = mutableListOf<PageRect>()
        var current: PageRect? = null
        for (i in range) {
            if (i !in 0 until text.length || !text.hasBox(i)) continue
            val box = PageRect(text.left(i), text.top(i), text.right(i), text.bottom(i))
            val line = current
            current = if (line != null && sameLine(line, box)) {
                PageRect(
                    minOf(line.left, box.left),
                    minOf(line.top, box.top),
                    maxOf(line.right, box.right),
                    maxOf(line.bottom, box.bottom)
                )
            } else {
                line?.let(out::add)
                box
            }
        }
        current?.let(out::add)
        return closeGaps(out)
    }

    /**
     * Consecutive lines meet halfway across the leading between them, so a highlighted paragraph is one solid block
     * like a marker stroke, not stripes. Lines further apart (a paragraph break, a column) keep their own band.
     */
    private fun closeGaps(lines: List<PageRect>): List<PageRect> {
        val out = lines.toMutableList()
        for (i in 0 until out.size - 1) {
            val a = out[i]
            val b = out[i + 1]
            val gap = b.top - a.bottom
            val height = minOf(a.bottom - a.top, b.bottom - b.top)
            if (gap > 0f && gap < height * MAX_LEADING) {
                val mid = (a.bottom + b.top) / 2f
                out[i] = a.copy(bottom = mid)
                out[i + 1] = b.copy(top = mid)
            }
        }
        return out
    }

    /** The selected text as it reads: line breaks become spaces, a word broken by a hyphen at a line end is joined. */
    fun quote(text: PageText, range: IntRange): String {
        val raw = text.text.substring(range.first.coerceAtLeast(0), (range.last + 1).coerceAtMost(text.length))
        return raw.replace(HYPHEN_BREAK, "")
            .replace(LINE_BREAK, " ")
            .replace(SPACES, " ")
            .trim()
    }

    /** [a]..[b] in reading order, whichever end was dragged past the other. */
    fun ordered(a: Int, b: Int): IntRange = minOf(a, b)..maxOf(a, b)

    private fun sameLine(a: PageRect, b: PageRect): Boolean {
        val mid = (b.top + b.bottom) / 2f
        return mid in a.top..a.bottom || abs(a.top - b.top) < (a.bottom - a.top) * SAME_LINE_SLACK
    }

    private fun isWordChar(c: Char) = c.isLetterOrDigit() || c == '\'' || c == '’'

    private val HYPHEN_BREAK = Regex("[-­]\r?\n")
    private val LINE_BREAK = Regex("\r?\n")
    private val SPACES = Regex("\\s+")
    private const val LINE_REACH = 0.06f
    private const val SAME_LINE_SLACK = 0.5f
    private const val MAX_LEADING = 0.9f
}
