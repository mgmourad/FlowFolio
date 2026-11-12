package com.flowfolio.core.conversion.stage

import com.flowfolio.core.conversion.pdf.Box

/** Distances between glyphs, in the units the line builder ([Extraction]) measures word spaces in. */
object GlyphMetrics {
    /**
     * The empty space between the previous character (from [lastLeft] to [lastRight]) and [b], whichever side [b]
     * lies on: text reading right to left steps leftwards, and a Latin word inside an Arabic line steps rightwards.
     */
    fun gapBetween(lastLeft: Float, lastRight: Float, b: Box): Float = maxOf(b.left - lastRight, lastLeft - b.right)

    /**
     * The em a gap is measured in: the font size, unless the PDF reports a size far too small for the glyph it drew.
     * Some PDFs set every font at size 1 and scale it through the text matrix (known issue 46); a quarter em was then a
     * quarter of a point, and the kerning between "r" and "y" read as a word space ("necessar y", known issue 51).
     * A glyph box is about an em tall, so it stands in when the reported size is less than a third of it.
     */
    fun emOf(size: Float, glyph: Box): Float =
        if (size * TINY_SIZE_SHARE < glyph.height) glyph.height * BOX_EM_SHARE else size

    private const val TINY_SIZE_SHARE = 3f
    private const val BOX_EM_SHARE = 0.75f
}
