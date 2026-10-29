package com.flowfolio.core.conversion.pdf

/**
 * Whether a drawn picture is plain ink on paper, which a reader may draw in its own text colour, or has colour of its
 * own to keep: coloured marks (icons, highlighted cells), or solid shaded areas (a header row filled grey or black).
 * Thin dark strokes (text, rules, borders) and their anti-aliased edges are ink.
 */
object Ink {
    /** [argb] holds a [width] x [height] picture, row by row. */
    fun coloured(argb: IntArray, width: Int, height: Int): Boolean {
        if (width <= 0 || height <= 0 || argb.size < width * height) return false
        var sampled = 0
        var tinted = 0
        var solid = 0
        for (y in 0 until height step STEP) {
            for (x in 0 until width step STEP) {
                val p = argb[y * width + x]
                sampled++
                if (chroma(p) > MAX_INK_CHROMA) tinted++
                if (shaded(p) && shadedAround(argb, width, height, x, y)) solid++
            }
        }
        return tinted > sampled * MIN_TINTED_SHARE || solid > sampled * MIN_SOLID_SHARE
    }

    /** A pixel and its neighbours [REACH] pixels away on all four sides are all shaded: a fill, not a stroke. */
    private fun shadedAround(argb: IntArray, width: Int, height: Int, x: Int, y: Int): Boolean =
        x >= REACH && y >= REACH && x + REACH < width && y + REACH < height &&
            shaded(argb[y * width + x - REACH]) && shaded(argb[y * width + x + REACH]) &&
            shaded(argb[(y - REACH) * width + x]) && shaded(argb[(y + REACH) * width + x])

    private fun shaded(p: Int) = luma(p) < PAPER_LUMA

    private fun chroma(p: Int): Int {
        val r = p shr RED_SHIFT and BYTE
        val g = p shr GREEN_SHIFT and BYTE
        val b = p and BYTE
        return maxOf(r, g, b) - minOf(r, g, b)
    }

    private fun luma(p: Int): Int {
        val r = p shr RED_SHIFT and BYTE
        val g = p shr GREEN_SHIFT and BYTE
        val b = p and BYTE
        return (r * LUMA_R + g * LUMA_G + b * LUMA_B) / LUMA_SCALE
    }

    /** Every other pixel each way is plenty to tell, and four times quicker. */
    private const val STEP = 2

    /** A fill is shaded for this many pixels each way around a point (thicker than any text stroke at our scales). */
    private const val REACH = 6

    /** Ink and its anti-aliasing are grey; a colour differs between its channels by more than this. */
    private const val MAX_INK_CHROMA = 48
    private const val MIN_TINTED_SHARE = 0.003f
    private const val MIN_SOLID_SHARE = 0.02f

    /** Lighter than this is paper (white, or nearly). */
    private const val PAPER_LUMA = 235

    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8
    private const val BYTE = 0xFF
    private const val LUMA_R = 299
    private const val LUMA_G = 587
    private const val LUMA_B = 114
    private const val LUMA_SCALE = 1000
}
