package com.flowfolio.core.conversion.stage

import com.flowfolio.core.conversion.ir.Line

/**
 * Spike (Phase 4 M1): geometric verification — does the converter's *model* of a page agree with the glyph geometry
 * it actually read? Character coverage counts characters; this checks their sizes and positions, which is what the
 * Packt bug escaped on: every character present, every glyph in the wrong place.
 *
 * The Packt DevOps PDF sets its fonts at size 1 and scales them through the text matrix. Every style therefore reached
 * the converter as `size=1.0`, while the glyph boxes PDFium reports sit at their rendered ~10pt. A code listing laid
 * out on `size * TYPICAL_MONO_EMS` = 0.6pt cells then became columns of single letters — and text coverage never
 * noticed. [sizeRatio] is the check that would have: it compares a line's declared style size against the actual
 * height of its glyph boxes.
 */
object GeometricVerify {
    /**
     * A style whose size is this many times off its glyphs is a model that does not describe the page. Wide enough to
     * pass over symbol and math fonts (whose glyph boxes can sit 2-3x off their reported size) while still catching a
     * genuinely misread size — the Packt book's fonts report size 1 against ~10pt glyphs, a ratio of 10.
     */
    const val SIZE_TOLERANCE = 5f

    /**
     * The median ratio of a line's glyph-box height to its style's declared size. About 1 for a healthy book, about
     * 10 for the Packt book (10pt glyphs against a `size=1.0` style). NaN when there is nothing to measure.
     */
    fun sizeRatio(line: Line): Float {
        val size = line.style.size
        val heights = line.spans
            .mapNotNull { span -> if (span.text.isBlank() || span.box.height <= 0f) null else span.box.height }
            .sorted()
        val median = heights.getOrNull(heights.size / 2)
        return when {
            size <= 0f || median == null -> Float.NaN
            else -> median / size
        }
    }

    /** True when the line's glyphs sit at roughly the size its style claims; false when they are far off. */
    fun sizeIsConsistent(line: Line): Boolean {
        val ratio = sizeRatio(line)
        return ratio.isNaN() || ratio in (1f / SIZE_TOLERANCE)..SIZE_TOLERANCE
    }

    /** The share of text in [lines] whose glyphs agree with their declared size; 1 when there is none to measure. */
    fun coverage(lines: List<Line>): Float {
        val total = lines.sumOf { l -> l.text.count { !it.isWhitespace() } }
        if (total <= 0) return 1f
        val good = lines.filter { sizeIsConsistent(it) }.sumOf { l -> l.text.count { !it.isWhitespace() } }
        return good.toFloat() / total
    }
}
