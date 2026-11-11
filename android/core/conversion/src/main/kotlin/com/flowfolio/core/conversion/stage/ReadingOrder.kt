package com.flowfolio.core.conversion.stage

import com.flowfolio.core.conversion.ir.Line
import com.flowfolio.core.conversion.ir.Page
import com.flowfolio.core.conversion.pdf.Box

/** Something placed on the page: a line of running text, or a [Region]. */
sealed interface Item {
    val box: Box

    class Text(val line: Line) : Item {
        override val box: Box get() = line.box
    }

    class Area(val region: Region) : Item {
        override val box: Box get() = region.box
    }
}

/**
 * Stage D, part two: reading order. A page in two columns has a gutter, an empty vertical band near the middle that
 * narrow items keep clear of. Items crossing it (a title, a wide figure) cut the page into bands; each band reads its
 * left column, then its right. A page in one column reads top to bottom, and items side by side left to right.
 * A page written mostly right to left (Arabic, Hebrew) mirrors both: its right column first, and items side by side
 * right to left.
 */
object ReadingOrder {
    fun order(page: Page, items: List<Item>): List<Item> {
        val rtl = rightToLeft(items)
        val gutter = gutter(page, items) ?: return byRows(items, rtl)
        val wide = items.filter { it.box.left < gutter && it.box.right > gutter }.sortedBy { it.box.top }
        val narrow = items - wide.toSet()
        val out = ArrayList<Item>()
        var top = Float.NEGATIVE_INFINITY
        for (cut in wide + listOf(null)) {
            val bottom = cut?.box?.top ?: Float.POSITIVE_INFINITY
            val band = narrow.filter { centreY(it) >= top && centreY(it) < bottom }
            val left = byRows(band.filter { it.box.right <= gutter }, rtl)
            val right = byRows(band.filter { it.box.left >= gutter }, rtl)
            out += if (rtl) right + left else left + right
            if (cut != null) out += cut
            top = cut?.box?.bottom ?: top
        }
        return out
    }

    /**
     * The x of a gutter between two columns, or null: a band at least [MIN_GUTTER] points wide in the middle of the
     * page that no narrow item covers, with enough items on both sides.
     */
    // Page geometry: each early return or condition is one case of the layout, kept together to read as one rule.
    @Suppress("CyclomaticComplexMethod", "ReturnCount")
    private fun gutter(page: Page, items: List<Item>): Float? {
        val narrow = items.filter { it.box.width < page.width * NARROW_SHARE }
        if (narrow.size < MIN_COLUMN_ITEMS * 2) return null
        val covered = BooleanArray(page.width.toInt() + 1)
        narrow.forEach { i ->
            for (x in i.box.left.toInt().coerceAtLeast(0)..i.box.right.toInt().coerceAtMost(covered.lastIndex)) {
                covered[x] = true
            }
        }
        val from = (page.width * GUTTER_FROM).toInt()
        val to = (page.width * GUTTER_TO).toInt()
        var best: IntRange? = null
        var start = -1
        for (x in from..to) {
            if (!covered[x] && start < 0) start = x
            if ((covered[x] || x == to) && start >= 0) {
                val run = start until x
                if (run.count() >= MIN_GUTTER && (best == null || run.count() > best.count())) best = run
                start = -1
            }
        }
        val mid = best?.let { (it.first + it.last) / 2f } ?: return null
        val left = narrow.count { it.box.right <= mid }
        val right = narrow.count { it.box.left >= mid }
        return mid.takeIf { left >= MIN_COLUMN_ITEMS && right >= MIN_COLUMN_ITEMS }
    }

    /** Most of the page's strongly directional letters read right to left. */
    private fun rightToLeft(items: List<Item>): Boolean {
        var rtl = 0
        var ltr = 0
        for (item in items) {
            if (item !is Item.Text) continue
            for (c in item.line.text) {
                when (TextDirection.direction(c)) {
                    true -> rtl++
                    false -> ltr++
                    null -> Unit
                }
            }
        }
        return rtl > ltr
    }

    /** Top to bottom; items on the same row left to right, or right to left when [rtl]. */
    private fun byRows(items: List<Item>, rtl: Boolean): List<Item> {
        val rows = ArrayList<MutableList<Item>>()
        for (item in items.sortedBy { it.box.top }) {
            val row = rows.lastOrNull()
            if (row != null &&
                Extraction.sameRow(row.first().box, item.box)
            ) {
                row += item
            } else {
                rows += mutableListOf(item)
            }
        }
        return rows.flatMap { row ->
            if (rtl) row.sortedByDescending { it.box.right } else row.sortedBy { it.box.left }
        }
    }

    private fun centreY(i: Item) = (i.box.top + i.box.bottom) / 2

    private const val NARROW_SHARE = 0.55f
    private const val MIN_COLUMN_ITEMS = 3
    private const val GUTTER_FROM = 0.3f
    private const val GUTTER_TO = 0.7f
    private const val MIN_GUTTER = 8
}
