package com.flowfolio.feature.reader.riffle

import kotlin.math.ceil
import kotlin.math.tanh

/**
 * Where each of the reader's units sits on the fore edge (ADR-0022), measured in sheets from the front cover:
 * `(firstPage + lastPage + 1) / 4`. A spread sits exactly between its two sheets, a single page within its sheet, and
 * positions always follow reading order. Right-to-left books are mirrored, with the front cover at the right end.
 */
class StripMapping(private val book: BookGeometry) {
    /** Sheets of paper in the book: two pages each. */
    val sheetCount: Int = ((book.pageCount + 1) / 2).coerceAtLeast(1)

    private val positions = FloatArray(book.unitCount) { u ->
        val pages = book.pagesOf(u)
        (pages.first + pages.last + 1) / UNITS_PER_SHEET
    }

    val unitCount: Int get() = positions.size

    /** [unit]'s place along the strip, 0 at the left end and 1 at the right. */
    fun fractionOf(unit: Int): Float {
        if (positions.isEmpty()) return 0f
        val f = positions[unit.coerceIn(0, positions.lastIndex)] / sheetCount
        return if (book.rightToLeft) 1f - f else f
    }

    /** The unit nearest [fraction] of the strip (clamped to the book). */
    fun unitAt(fraction: Float): Int {
        if (positions.size <= 1) return 0
        val f = fraction.coerceIn(0f, 1f).let { if (book.rightToLeft) 1f - it else it }
        val target = f * sheetCount
        var lo = 0
        var hi = positions.lastIndex
        while (lo < hi) {
            val mid = (lo + hi) / 2
            if (positions[mid] < target) lo = mid + 1 else hi = mid
        }
        // lo is the first unit at or after the target; the one before may be nearer.
        return if (lo > 0 && target - positions[lo - 1] <= positions[lo] - target) lo - 1 else lo
    }

    /** The strip's x for [unit], between the ends of the page block [x0] and [x1]. */
    fun xOf(unit: Int, x0: Float, x1: Float): Float = x0 + fractionOf(unit) * (x1 - x0)

    fun unitAtX(x: Float, x0: Float, x1: Float): Int = unitAt(if (x1 > x0) (x - x0) / (x1 - x0) else 0f)

    /** The sheet [page] is printed on (0 = the first, behind the front cover). */
    fun sheetOf(page: Int): Int = (page / 2).coerceIn(0, sheetCount - 1)

    /**
     * How many sheets one drawn leaf stands for in a strip [widthPx] wide: 1 until leaves would be closer than
     * [MIN_LEAF_PX], then enough to keep them apart. Thinning changes the picture, never the mapping.
     */
    fun sheetsPerLeaf(widthPx: Float): Int {
        val fit = (widthPx / MIN_LEAF_PX).coerceAtLeast(1f)
        return if (sheetCount <= fit) 1 else ceil(sheetCount / fit).toInt()
    }

    fun leafCount(widthPx: Float): Int {
        val per = sheetsPerLeaf(widthPx)
        return (sheetCount + per - 1) / per
    }

    companion object {
        /** Pages per sheet, times two: positions are averaged over a unit's first and last page. */
        private const val UNITS_PER_SHEET = 4f

        /** Closest two drawn leaves may be, in pixels (spec 4.1). */
        const val MIN_LEAF_PX = 1.6f
    }
}

/**
 * The gap the pin opens in the fore edge (spec 5.1): leaves near the pin move away from it, both covers stay put.
 * Engagement is 0 at rest, 0.55 while travelling after a flag tap and 1 while dragging; [density] is pixels per dp.
 * [prepare] it once per frame, then ask for each leaf: nothing is allocated per frame.
 */
class Gap(private val density: Float) {
    private var size = 0f
    private var radius = 1f
    private var pin = 0f
    private var x0 = 0f
    private var span = 1f
    private var startEnd = 0f
    private var endEnd = 0f

    fun prepare(engagement: Float, pin: Float, x0: Float, x1: Float): Gap {
        size = (BASE_DP + SPREAD_DP * engagement) * density
        radius = (RADIUS_DP + RADIUS_SPREAD_DP * engagement) * density
        this.pin = pin
        this.x0 = x0
        span = (x1 - x0).coerceAtLeast(1f)
        startEnd = tanh((x0 - pin) / radius)
        endEnd = tanh((x1 - pin) / radius)
        return this
    }

    /** How far the leaf at rest position [x] moves, with the linear correction that keeps both ends still. */
    fun displacement(x: Float): Float {
        val u = (x - x0) / span
        return size * (tanh((x - pin) / radius) - (startEnd * (1 - u) + endEnd * u))
    }

    private companion object {
        const val BASE_DP = 4f
        const val SPREAD_DP = 30f
        const val RADIUS_DP = 20f
        const val RADIUS_SPREAD_DP = 14f
    }
}
