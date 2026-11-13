package com.flowfolio.core.conversion.stage

import com.flowfolio.core.conversion.ir.BlockType
import com.flowfolio.core.conversion.ir.Inline
import com.flowfolio.core.conversion.ir.Line
import com.flowfolio.core.conversion.ir.OBJECT
import com.flowfolio.core.conversion.ir.Page
import com.flowfolio.core.conversion.ir.Span
import com.flowfolio.core.conversion.pdf.Box
import com.flowfolio.core.conversion.stage.Regions.containsCentre

/**
 * Text and equations drawn as vector shapes, which PDFium can't read as text (OpenStax sets every equation this way).
 * Small shapes outside the figures, tables and sidebars are grouped into drawings. A drawing on a line of text goes
 * into that line as an [Inline], at its place among the words; any other drawing is a display equation of its own.
 * Both are shown as images of the page this phase; Phase 5 turns them into MathML. Nothing drawn is dropped but
 * bars standing alone, which are page decoration ([isBar]).
 */
// One stage: its rules belong together.
@Suppress("TooManyFunctions")
object Shapes {
    class Placed(val lines: List<Line>, val equations: List<Region>)

    // Page geometry: each early return or condition is one case of the layout, kept together to read as one rule.
    @Suppress("CyclomaticComplexMethod")
    fun place(page: Page, lines: List<Line>, regions: List<Region>): Placed {
        val drawings = drawings(page, regions)
        if (drawings.isEmpty()) return Placed(lines, emptyList())
        val inlineOn = HashMap<Line, MutableList<Box>>()
        // A line reaches as far as its text and the drawings already set in it, so a symbol beyond other symbols
        // ("A + B" drawn before the words start) still finds its line; repeat until nothing more joins a line.
        val reach = HashMap<Line, Box>()
        var pending: List<Box> = drawings
        while (true) {
            val left = ArrayList<Box>()
            for (d in pending) {
                val line = lines.filter {
                    onLine(it, reach[it] ?: it.box, d)
                }.minByOrNull { distance(reach[it] ?: it.box, d) }
                when {
                    line == null -> left += d

                    // A flat bar along the text (a link's highlight or underline) is styling, not content; so is a
                    // shape the line's own characters sit on (the band behind a box's title, the tab behind "3.1").
                    isUnderline(line, d) || isBackdrop(line, d) -> Unit

                    else -> {
                        inlineOn.getOrPut(line) { ArrayList() } += d
                        reach[line] = (reach[line] ?: line.box).union(d)
                    }
                }
            }
            if (left.size == pending.size) break
            pending = left
        }
        val display = pending
        val placed = lines.map { line -> inlineOn[line]?.let { withInlines(line, it) } ?: line }
        // A display equation's parts (a fraction's lines, a sum's limits) are drawn apart: one equation per cluster.
        // Pieces of one row ("D =" set a little apart from what it equals) join first, a little further apart than
        // the lines of an equation stand.
        val grouped = Regions.clusters(joinRows(display, DISPLAY_ROW_GAP), DISPLAY_REACH).map {
            it.first
        }.filterNot(::isBar).filterNot { d -> isEdgeTab(page, d) && placed.none { d.containsCentre(it.box) } }
        val equations = grouped.map { d ->
            val inside = placed.filter { d.containsCentre(it.box) }
            // The equation's number at the end of its row ("2.7") goes with it, not into a paragraph of its own.
            val number = placed.filter { it !in inside && isNumberOf(it, d) }
            val box = (listOf(d) + number.map { it.box }).reduce(Box::union)
            Region(BlockType.EQUATION, box.padded(PAD), inside + number)
        }
        val claimed = equations.flatMap { it.lines }.toSet()
        return Placed(placed.filter { it !in claimed }, equations)
    }

    /** Small shapes outside every region, grouped, and drawings side by side on one row joined into one. */
    private fun drawings(page: Page, regions: List<Region>): List<Box> {
        val area = page.width * page.height
        val marks = page.paths.map { it.bounds }.filter { b ->
            b.height > RULE && b.width > RULE && b.area < area * MAX_SHARE && regions.none { it.box.intersects(b) }
        }
        val groups = Regions.clusters(marks, REACH).map { it.first }.filter { it.area >= MIN_AREA }
        return joinRows(groups)
    }

    private fun joinRows(boxes: List<Box>, gap: Float = ROW_GAP): List<Box> {
        val out = ArrayList<Box>()
        for (b in boxes.sortedWith(compareBy({ it.top }, { it.left }))) {
            val i = out.indexOfFirst { o -> overlapY(o, b) > minOf(o.height, b.height) * HALF && gapX(o, b) < gap }
            if (i >= 0) out[i] = out[i].union(b) else out += b
        }
        return out
    }

    /** The drawing sits on [line]'s row, within a few ems of the ends of what the line [reach]es. */
    private fun onLine(line: Line, reach: Box, d: Box): Boolean {
        val em = line.style.size.coerceAtLeast(1f)
        return overlapY(line.box, d) >= minOf(line.box.height, d.height) * HALF &&
            d.left <= reach.right + em * REACH_EMS && d.right >= reach.left - em * REACH_EMS
    }

    /** [line] with [drawings] placed among its words: each before the first character starting right of it. */
    // Page geometry: each early return or condition is one case of the layout, kept together to read as one rule.
    @Suppress("NestedBlockDepth")
    private fun withInlines(line: Line, drawings: List<Box>): Line {
        val spans = ArrayList(line.spans)
        val inlines = ArrayList<Pair<Float, Inline>>()
        val em = line.style.size.coerceAtLeast(1f)
        for (d in drawings.sortedBy { it.left }) {
            val at = spans.indexOfFirst { it.box.right > d.left && it.text != OBJECT.toString() }
            // The baseline sits about a fifth of an em above the bottom of the line's loose character boxes.
            val baseline = line.box.bottom - em * DESCENT_EMS
            val inline = Inline(d, d.height / em, d.width / em, (d.bottom - baseline) / em)
            val marker = Span(OBJECT.toString(), d, line.style, floatArrayOf(d.left))
            if (at < 0) {
                spans += marker
            } else {
                val s = spans[at]
                val cut = s.indexAt(d.left)
                if (cut <= 0) {
                    spans.add(at, marker)
                } else {
                    val (before, after) = s.cut(cut)
                    spans[at] = before
                    spans.add(at + 1, marker)
                    if (after.text.isNotEmpty()) spans.add(at + 2, after)
                }
            }
            inlines += d.left to inline
        }
        val ordered = inlines.sortedBy { it.first }.map { it.second }
        return Line(spans, drawings.fold(line.box, Box::union), ordered)
    }

    private fun isUnderline(line: Line, d: Box): Boolean {
        val em = line.style.size.coerceAtLeast(1f)
        return d.height <= em * UNDERLINE_EMS && d.width >= em * UNDERLINE_MIN_EMS
    }

    /**
     * [d] lies under some of [line]'s characters: a backdrop. Shapes drawn as content (an equation set as shapes) sit
     * between the words, never under them.
     */
    private fun isBackdrop(line: Line, d: Box): Boolean {
        val inner = d.padded(-BACKDROP_INSET)
        val em = line.style.size.coerceAtLeast(1f)
        return line.spans.any { s ->
            val y = (s.box.top + s.box.bottom) / 2
            s.text.indices.any { k ->
                !s.text[k].isWhitespace() && s.text[k] != OBJECT &&
                    inner.contains((s.lefts.getOrNull(k) ?: s.box.left) + em * CHAR_MIDDLE_EMS, y)
            }
        }
    }

    /** [line] is only an equation number ("2.7", "(3.12a)") on the row of drawing [d], to its right. */
    private fun isNumberOf(line: Line, d: Box) = EQUATION_NUMBER.matches(line.text.trim()) &&
        line.box.left >= d.right - HALF && overlapY(line.box, d) >= line.box.height * NUMBER_OVERLAP

    /**
     * A bar standing alone: a section rule or a coloured strip beside a box, filled rather than stroked. It is page
     * decoration, not an equation, and the reader spaces sections itself, so it goes out as nothing. Fraction bars are
     * far thinner and always cluster with their numerator and denominator, so they never stand alone.
     */
    private fun isBar(b: Box): Boolean {
        val thin = minOf(b.width, b.height)
        return thin in BAR_MIN..BAR_MAX && maxOf(b.width, b.height) >= BAR_LENGTH
    }

    /**
     * A shape that runs off the page's edge: a thumb-index tab or a printer's bleed (the coloured tabs down the side of
     * *Color Atlas of Genetics*, 2026-11-12). With no text inside it is decoration; set as an "equation" it landed in
     * the middle of a sentence.
     */
    private fun isEdgeTab(page: Page, d: Box): Boolean =
        d.left < EDGE || d.top < EDGE || d.right > page.width - EDGE || d.bottom > page.height - EDGE

    private fun overlapY(a: Box, b: Box) = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)

    private fun gapX(a: Box, b: Box) = maxOf(b.left - a.right, a.left - b.right)

    private fun distance(a: Box, b: Box) = maxOf(0f, gapX(a, b))

    private fun Box.padded(by: Float) = Box(left - by, top - by, right + by, bottom + by)

    private const val EDGE = 0.5f
    private const val RULE = 0.6f
    private const val BACKDROP_INSET = 0.5f

    /** A character's middle, about a quarter em right of where it starts. */
    private const val CHAR_MIDDLE_EMS = 0.25f
    private const val MAX_SHARE = 0.02f
    private const val REACH = 3f
    private const val MIN_AREA = 4f
    private const val ROW_GAP = 8f
    private const val HALF = 0.5f
    private const val REACH_EMS = 3f
    private const val PAD = 2f
    private const val DESCENT_EMS = 0.21f
    private const val DISPLAY_REACH = 8f
    private const val DISPLAY_ROW_GAP = 14f
    private const val BAR_MIN = RULE
    private const val BAR_MAX = 8f
    private const val BAR_LENGTH = 100f
    private const val UNDERLINE_EMS = 0.6f
    private const val NUMBER_OVERLAP = 0.25f
    private const val UNDERLINE_MIN_EMS = 2f
    private val EQUATION_NUMBER = Regex("""\(?\d+(\.\d+)*[a-z]?\)?""")
}
