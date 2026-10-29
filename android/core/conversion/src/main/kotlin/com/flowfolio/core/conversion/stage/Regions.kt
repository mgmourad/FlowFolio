package com.flowfolio.core.conversion.stage

import com.flowfolio.core.conversion.ir.BlockType
import com.flowfolio.core.conversion.ir.Line
import com.flowfolio.core.conversion.ir.Page
import com.flowfolio.core.conversion.pdf.Box
import com.flowfolio.core.conversion.pdf.PathInfo
import kotlin.math.abs

/** A part of the page that is not running text: a figure, a sidebar or a table, and the lines that fall inside it. */
class Region(val type: BlockType, val box: Box, val lines: List<Line>)

/**
 * Stage D, part one: figures, sidebars and tables. Figures are pictures and clusters of vector paths (drawings and
 * charts); sidebars are shaded boxes with text on them; tables are stacks of horizontal rules with text between.
 * Lines inside a figure or table belong to it (labels, cells); lines inside a sidebar become its paragraphs. A
 * chapter title (isTitle) is never a figure's or a sidebar's: set over its opening photo or on a coloured band, it is
 * still the chapter's heading, and a band left with no text is decoration.
 */
// One stage: its rules belong together.
@Suppress("TooManyFunctions")
object Regions {
    fun find(
        page: Page,
        lines: List<Line>,
        isCode: (Line) -> Boolean = { false },
        isTitle: (Line) -> Boolean = { false }
    ): List<Region> {
        val area = page.width * page.height
        // A drawing mostly covered by one line of text is a highlight behind it (a marker band), not a figure.
        val drawn = drawings(page.paths, area).filter { d -> lines.none { covers(it.box, d) } }
        val figures = merge(pictures(page, area) + drawn)
        val tables = tables(page.paths, lines).filter { t -> figures.none { it.intersects(t) } }
            .map { withRowsBelow(it, lines) }
        // A shaded box of code is a code listing, kept as text (Blocks sets it as code), not a sidebar.
        val sidebars = sidebars(page.paths, area, lines)
            .filter { s -> (figures + tables).none { it.contains(s) } }
            .filter { s -> !mostlyCode(lines.filter { s.containsCentre(it.box) }, isCode) }
        val taken = HashSet<Line>()
        fun claim(type: BlockType, box: Box): Region {
            val inside = lines.filter {
                it !in taken && box.containsCentre(it.box) && (type == BlockType.TABLE || !isTitle(it))
            }
            taken += inside
            return Region(type, box, inside)
        }
        // Figures and tables first: a sidebar holding a figure keeps its text, the figure its labels.
        return figures.map { claim(BlockType.FIGURE, it) } +
            tables.map { claim(BlockType.TABLE, it) } +
            sidebars.map { claim(BlockType.SIDEBAR, it) }.filter { it.lines.isNotEmpty() }
    }

    private fun mostlyCode(lines: List<Line>, isCode: (Line) -> Boolean): Boolean {
        val all = lines.sumOf { it.text.length }
        return all > 0 && lines.filter(isCode).sumOf { it.text.length } >= all * CODE_SHARE
    }

    private fun pictures(page: Page, area: Float) = page.images.filter {
        it.area >= area * MIN_FIGURE_SHARE && it.area <= area * MAX_FIGURE_SHARE
    }

    /** Clusters of paths close together, big enough to be a drawing rather than a rule or a glyph. */
    private fun drawings(paths: List<PathInfo>, area: Float): List<Box> {
        val marks = paths.map { it.bounds }.filter { it.area < area * MAX_FIGURE_SHARE && !isRule(it) }
        return clusters(marks, CLUSTER_REACH).filter { (box, count) ->
            count >= MIN_DRAWING_PATHS && box.area >= area * MIN_DRAWING_SHARE && box.height >= MIN_DRAWING_HEIGHT &&
                !isTypesetMath(box, marks)
        }.map { it.first }
    }

    /**
     * A cluster made only of glyph-sized shapes, no taller than a few lines: an equation set as shapes (a sum over
     * three
     * lines), which [Shapes] makes a display equation, not a picture. Drawings and charts have longer strokes or areas.
     */
    private fun isTypesetMath(box: Box, marks: List<Box>): Boolean = box.height <= MAX_MATH_HEIGHT &&
        marks.filter { box.contains(it) }.all { maxOf(it.width, it.height) <= MAX_GLYPH }

    /** Shaded boxes holding at least two lines of text. */
    private fun sidebars(paths: List<PathInfo>, area: Float, lines: List<Line>): List<Box> = paths
        .filter { it.filled }
        .map { it.bounds }
        .filter { it.area >= area * MIN_SIDEBAR_SHARE && it.area <= area * MAX_FIGURE_SHARE }
        .filter { it.height >= MIN_SIDEBAR_HEIGHT }
        .filter { b -> lines.count { b.containsCentre(it.box) } >= MIN_SIDEBAR_LINES }
        .let(::outermost)

    /** At least three horizontal rules of about the same width, stacked, with text between them. */
    private fun tables(paths: List<PathInfo>, lines: List<Line>): List<Box> {
        val rules = paths.map { it.bounds }.filter { it.height <= RULE_THICKNESS && it.width >= MIN_RULE_LENGTH }
        // Rules of about the same span, wherever they fall against a grid.
        val groups = ArrayList<MutableList<Box>>()
        for (r in rules.sortedBy { it.left }) {
            val g = groups.firstOrNull { g ->
                abs(g[0].left - r.left) <= RULE_ALIGN && abs(g[0].right - r.right) <= RULE_ALIGN
            }
            if (g != null) g += r else groups += mutableListOf(r)
        }
        // Word draws each cell's borders, so each column's rules make a stack of their own: columns side by side
        // are one table.
        val verticals = paths.map { it.bounds }.filter { it.width <= RULE_THICKNESS && it.height >= RULE_THICKNESS * 2 }
        val stacks = groups.flatMap { bordered(it, verticals) }.filter { it.size >= MIN_TABLE_RULES }
            .map { it.reduce(Box::union) }
        val bands = paths.filter { it.filled }.map { it.bounds }.filter { it.height <= MAX_BAND_HEIGHT }
        return merge(stacks).map { withEdges(it, bands, rules, lines) }
            .filter { b -> lines.count { b.containsCentre(it.box) } >= MIN_TABLE_LINES }
    }

    /**
     * A stack of rules cut where its cells' side borders stop: two tables one under the other, with a line of text
     * between, line up but are not one. A stack with no side borders is cut where no row could span the gap (fraction
     * bars of the same length down a column are not a table).
     */
    private fun bordered(rules: List<Box>, verticals: List<Box>): List<List<Box>> {
        val sorted = rules.sortedBy { it.top }
        val span = sorted.reduce(Box::union)
        val sides = verticals.filter { v ->
            v.left >= span.left - RULE_THICKNESS && v.right <= span.right + RULE_THICKNESS
        }
        // A border touching the rules' ends is the table's own.
        val sided = sides.any { span.grown(RULE_THICKNESS).intersects(it) }
        val out = ArrayList<MutableList<Box>>()
        for (r in sorted) {
            val last = out.lastOrNull()?.last()
            val joined = last != null && if (sided) {
                sides.any { v -> v.top <= last.bottom + RULE_THICKNESS && v.bottom >= r.top - RULE_THICKNESS }
            } else {
                r.top - last.bottom <= MAX_ROW_HEIGHT
            }
            if (joined) out.last() += r else out += mutableListOf(r)
        }
        return out
    }

    /**
     * [table] grown over what closes it above and below: a shaded band along its edge (a header row filled dark, no
     * rule on it), and a rule across its whole width up to a row away (Word draws the outer border in one stroke and
     * the cells' borders column by column).
     */
    private fun withEdges(table: Box, bands: List<Box>, rules: List<Box>, lines: List<Line>): Box {
        val starts = lines.filter { table.containsCentre(it.box) }.map { it.box.left }

        // The strip a rule would add holds only cells: no line outside the table's columns (a bullet between two
        // tables, the next table's title).
        fun onlyCells(strip: Box) = lines.filter { strip.containsCentre(it.box) }
            .all { l -> starts.any { abs(it - l.box.left) <= CELL_ALIGN } }
        var box = table
        var grown = true
        while (grown) {
            grown = false
            val bandAt = bands.firstOrNull { b ->
                !box.contains(b) && minOf(b.right, box.right) - maxOf(b.left, box.left) >= b.width * BAND_OVERLAP &&
                    (abs(b.bottom - box.top) <= MERGE_REACH || abs(b.top - box.bottom) <= MERGE_REACH)
            }
            val ruleAt = rules.firstOrNull { r ->
                !box.contains(r) && r.left <= box.left + RULE_ALIGN && r.right >= box.right - RULE_ALIGN &&
                    nearEdge(r, box) &&
                    onlyCells(box.union(r))
            }
            (bandAt ?: ruleAt)?.let {
                box = box.union(it)
                grown = true
            }
        }
        return box
    }

    /**
     * [table] grown over the rows just under it that its borders missed (a last row drawn without them): lines in two
     * or more of the table's columns, each starting where a column's cells start, a line's height or less below.
     */
    private fun withRowsBelow(table: Box, lines: List<Line>): Box {
        val starts = lines.filter { table.containsCentre(it.box) }.map { it.box.left }
        var box = table
        while (true) {
            val row = rowBelow(box, lines)
            val inColumns = row.isNotEmpty() && row.all { l -> starts.any { abs(it - l.box.left) <= CELL_ALIGN } } &&
                row.map { l -> starts.minBy { abs(it - l.box.left) } }.toSet().size >= 2 &&
                row.all { it.box.right <= box.right + MERGE_REACH }
            if (!inColumns) return box
            box = row.fold(box) { b, l -> b.union(l.box) }
        }
    }

    /** The row of lines starting a line's height or less under [box], or none. */
    private fun rowBelow(box: Box, lines: List<Line>): List<Line> {
        val below = lines.filter { it.box.top >= box.bottom - MERGE_REACH && !box.containsCentre(it.box) }
        val first = below.minByOrNull { it.box.top }
        if (first == null || first.box.top - box.bottom > first.box.height) return emptyList()
        return below.filter { Extraction.sameRow(it.box, first.box) || it.box.top < first.box.bottom }
    }

    /** [r] lies up to a row above [box]'s top or below its bottom. */
    private fun nearEdge(r: Box, box: Box) =
        r.bottom in box.top - MAX_BAND_HEIGHT..box.top || r.top in box.bottom..box.bottom + MAX_BAND_HEIGHT

    /** [text] covers most of [drawing]. */
    private fun covers(text: Box, drawing: Box): Boolean {
        val w = minOf(text.right, drawing.right) - maxOf(text.left, drawing.left)
        val h = minOf(text.bottom, drawing.bottom) - maxOf(text.top, drawing.top)
        return w > 0 && h > 0 && w * h >= drawing.area * HIGHLIGHT_SHARE
    }

    private fun isRule(b: Box) = b.height <= RULE_THICKNESS || b.width <= RULE_THICKNESS

    /** Boxes not inside another of the list. */
    private fun outermost(boxes: List<Box>) = boxes.filter { b -> boxes.none { it !== b && it.contains(b) } }

    /**
     * Boxes joined when any two are within [reach] of each other (union-find over a sweep from left to right, so a page
     * of thousands of chart paths stays quick); each cluster's bounds and how many boxes it holds.
     */
    fun clusters(boxes: List<Box>, reach: Float): List<Pair<Box, Int>> {
        val sorted = boxes.sortedBy { it.left }
        val parent = IntArray(sorted.size) { it }
        fun root(i: Int): Int {
            var r = i
            while (parent[r] != r) r = parent[r]
            parent[i] = r
            return r
        }
        for (i in sorted.indices) {
            val grown = sorted[i].grown(reach)
            var j = i + 1
            while (j < sorted.size && sorted[j].left <= grown.right) {
                if (grown.intersects(sorted[j])) parent[root(j)] = root(i)
                j++
            }
        }
        return sorted.indices.groupBy(::root).values.map { members ->
            members.map { sorted[it] }.reduce(Box::union) to members.size
        }
    }

    private fun merge(boxes: List<Box>): List<Box> = clusters(boxes, MERGE_REACH).map { it.first }

    private fun Box.grown(by: Float) = Box(left - by, top - by, right + by, bottom + by)

    private fun Box.contains(o: Box) = o.left >= left && o.right <= right && o.top >= top && o.bottom <= bottom

    /** The centre of [o] lies inside this box. */
    fun Box.containsCentre(o: Box) = contains((o.left + o.right) / 2, (o.top + o.bottom) / 2)

    private const val MIN_FIGURE_SHARE = 0.012f
    private const val MAX_FIGURE_SHARE = 0.9f
    private const val MIN_DRAWING_SHARE = 0.02f
    private const val MIN_DRAWING_PATHS = 6
    private const val MIN_DRAWING_HEIGHT = 30f
    private const val CLUSTER_REACH = 6f
    private const val MAX_GLYPH = 16f
    private const val MAX_MATH_HEIGHT = 120f
    private const val MERGE_REACH = 4f
    private const val MIN_SIDEBAR_SHARE = 0.04f
    private const val MIN_SIDEBAR_HEIGHT = 36f
    private const val MIN_SIDEBAR_LINES = 2
    private const val CODE_SHARE = 0.7f
    private const val RULE_THICKNESS = 2.5f
    private const val MIN_RULE_LENGTH = 40f
    private const val RULE_ALIGN = 12f
    private const val MIN_TABLE_RULES = 3
    private const val MIN_TABLE_LINES = 2
    private const val MAX_BAND_HEIGHT = 40f
    private const val CELL_ALIGN = 4f

    /** A drawing this much covered by one line of text is a highlight behind it. */
    private const val HIGHLIGHT_SHARE = 0.6f
    private const val MAX_ROW_HEIGHT = 100f
    private const val BAND_OVERLAP = 0.8f
}
