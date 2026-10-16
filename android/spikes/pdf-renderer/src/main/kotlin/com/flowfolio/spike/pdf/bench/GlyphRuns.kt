package com.flowfolio.spike.pdf.bench

import com.flowfolio.spike.pdf.engine.RawPage
import kotlin.math.abs

/** A contiguous run of glyphs sharing font, size and baseline: the unit Stage C/D reason about. */
data class GlyphRun(
    val fontId: Int,
    val size: Float,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val text: String
)

/**
 * Groups raw glyphs into runs. A new run starts when the font or size changes, the baseline moves
 * by more than 40% of the font size (new line), or the pen jumps backwards (new line or column).
 */
fun groupRuns(page: RawPage): List<GlyphRun> {
    val runs = ArrayList<GlyphRun>()
    val text = StringBuilder()
    var font = Int.MIN_VALUE
    var size = 0f
    var l = 0f
    var t = 0f
    var r = 0f
    var b = 0f

    fun flush() {
        if (text.isNotEmpty()) runs += GlyphRun(font, size, l, t, r, b, text.toString())
        text.setLength(0)
    }

    for (i in 0 until page.charCount) {
        val g = i * 6
        val cl = page.geometry[g]
        val ct = page.geometry[g + 1]
        val cr = page.geometry[g + 2]
        val cb = page.geometry[g + 3]
        val cs = page.geometry[g + 4]
        val cp = page.ints[i * 3]
        val cf = page.ints[i * 3 + 1]
        val isSpace = cp == ' '.code || cp == '\r'.code || cp == '\n'.code
        val newRun = text.isEmpty() ||
            (!isSpace && (cf != font || abs(cs - size) > 0.1f || abs(cb - b) > 0.4f * size || cl < r - size))
        if (newRun && !isSpace) {
            flush()
            font = cf
            size = cs
            l = cl
            t = ct
            r = cr
            b = cb
        } else if (!isSpace) {
            r = maxOf(r, cr)
            t = maxOf(t, ct)
        }
        if (cp in 0x20..0x10FFFF) text.appendCodePoint(cp)
    }
    flush()
    return runs
}

/** Lines = runs whose baseline differs from the previous run's by more than 40% of the size. */
fun countLines(runs: List<GlyphRun>): Int {
    var lines = 0
    var lastBottom = Float.NaN
    for (run in runs) {
        if (lastBottom.isNaN() || abs(run.bottom - lastBottom) > 0.4f * run.size) lines++
        lastBottom = run.bottom
    }
    return lines
}
