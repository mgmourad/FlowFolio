package com.flowfolio.core.conversion.stage

import com.flowfolio.core.conversion.ir.Line
import com.flowfolio.core.conversion.ir.OBJECT
import com.flowfolio.core.conversion.ir.Span
import com.flowfolio.core.conversion.ir.StyleKey
import kotlin.math.abs

/**
 * Which styles are set in a monospaced font, measured on the page instead of taken from the PDF's flags or the font's
 * name, which many code fonts don't give (TeX's `SFTT1000`, `M+ 1mn`): in a monospaced font every character advances
 * by the same width. A style counts when its characters, side by side, nearly all advance by one width, and enough
 * different letters were seen: a style used only for digits is tabular figures, not code.
 */
object Monospace {
    /** Each monospaced style's character width, in points. */
    fun measure(lines: List<Line>): Map<StyleKey, Float> {
        val samples = HashMap<StyleKey, Sample>()
        for (line in lines) line.spans.forEach { samples.getOrPut(it.style) { Sample() }.read(it) }
        return samples.mapNotNull { (key, s) -> s.width(key.size)?.let { key to it } }.toMap()
    }

    /** Advances between neighbouring characters of one style (a few thousand are plenty) and the letters seen. */
    private class Sample {
        private val advances = FloatArray(MAX_PAIRS)
        private var count = 0
        private val letters = HashSet<Char>()
        val full get() = count == MAX_PAIRS

        /** The advances between [span]'s neighbouring characters that are both ink (not spaces, not drawings). */
        fun read(span: Span) {
            val t = span.text
            for (i in 0 until minOf(t.length, span.lefts.size) - 1) {
                if (full) return
                if (ink(t[i]) && ink(t[i + 1])) add(t[i], span.lefts[i + 1] - span.lefts[i])
            }
        }

        private fun ink(c: Char) = !c.isWhitespace() && c != OBJECT

        fun add(c: Char, advance: Float) {
            advances[count++] = advance
            if (c.isLetter()) letters += c.lowercaseChar()
        }

        fun width(size: Float): Float? {
            if (count < MIN_PAIRS || letters.size < MIN_LETTERS) return null
            val all = advances.copyOf(count).apply { sort() }
            val median = all[count / 2]
            val em = size.coerceAtLeast(1f)
            val even = all.count { abs(it - median) <= em * TOLERANCE_EMS }
            return median.takeIf { median / em in MIN_EMS..MAX_EMS && even >= count * MIN_EVEN }
        }
    }

    private const val MAX_PAIRS = 4_000
    private const val MIN_PAIRS = 40
    private const val MIN_LETTERS = 8

    /** Monospaced faces advance between about 0.45 and 0.7 em; outside that is something else spaced evenly. */
    private const val MIN_EMS = 0.4f
    private const val MAX_EMS = 0.75f
    private const val TOLERANCE_EMS = 0.03f

    /** Kerning and ligatures make proportional faces fall far below this; monospaced ones sit near 1. */
    private const val MIN_EVEN = 0.85f
}
