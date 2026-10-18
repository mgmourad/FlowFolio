package com.flowfolio.core.data

import com.flowfolio.core.model.LabelRun
import com.flowfolio.core.model.NumberStyle
import com.flowfolio.core.model.PageLabels
import com.flowfolio.core.pdf.PageText
import com.flowfolio.core.pdf.PdfDocument

/**
 * Finds a book's printed page numbers when the PDF does not say what they are (no page labels): it reads the numbers
 * printed in the top and bottom margins of a sample of pages and finds the numbering that runs through them, so the
 * reader can say "p. 48" where the book says 48. Front matter in roman numerals is found the same way.
 */
object FolioScanner {
    /** A number printed in a page's margin: its style and value. */
    data class Folio(val style: NumberStyle, val value: Int)

    /** Samples every [STEP]th page of [doc] and returns the numbering runs, or none when no numbering is found. */
    suspend fun scan(doc: PdfDocument): List<LabelRun> {
        val samples = (0 until doc.pageCount step STEP).associateWith { page ->
            runCatching { folios(doc.pageText(page)) }.getOrDefault(emptyList())
        }
        return runs(samples, doc.pageCount)
    }

    /** The numbers printed in the top or bottom margin of a page (arabic, or roman as front matter uses). */
    fun folios(text: PageText): List<Folio> {
        val out = mutableListOf<Folio>()
        var i = 0
        while (i < text.length) {
            if (text.text[i].isWhitespace()) {
                i++
                continue
            }
            val start = i
            while (i < text.length && !text.text[i].isWhitespace()) i++
            if (inMargin(text, start until i)) folio(text.text.substring(start, i))?.let(out::add)
        }
        return out
    }

    /**
     * The runs that best explain [samples] (page -> numbers in its margins): for each style, the offset between page
     * and number that most samples agree on, from where its numbering would reach 1 (or the first page) onward.
     */
    fun runs(samples: Map<Int, List<Folio>>, pageCount: Int): List<LabelRun> {
        val votes = HashMap<Pair<NumberStyle, Int>, Int>()
        samples.forEach { (page, folios) ->
            folios.map { it.style to page - it.value }.distinct().forEach { votes.merge(it, 1, Int::plus) }
        }
        val arabic = best(votes, NumberStyle.ARABIC) ?: return emptyList()
        val runs = mutableListOf<LabelRun>()
        // Front matter: roman numbers before the arabic numbering starts.
        val mainStart = (arabic + 1).coerceAtLeast(0)
        val roman = best(votes, NumberStyle.ROMAN_LOWER) ?: best(votes, NumberStyle.ROMAN_UPPER)
        val romanStyle = if (best(votes, NumberStyle.ROMAN_LOWER) !=
            null
        ) {
            NumberStyle.ROMAN_LOWER
        } else {
            NumberStyle.ROMAN_UPPER
        }
        if (roman != null && roman + 1 < mainStart) {
            if (roman + 1 > 0) runs += LabelRun(0, NumberStyle.NONE, "", 0)
            runs += LabelRun((roman + 1).coerceAtLeast(0), romanStyle, "", (roman + 1).coerceAtLeast(0) - roman)
        } else if (mainStart > 0) {
            runs += LabelRun(0, NumberStyle.NONE, "", 0)
        }
        if (mainStart < pageCount) runs += LabelRun(mainStart, NumberStyle.ARABIC, "", mainStart - arabic)
        return runs
    }

    /** The offset of [style] most samples agree on, when enough do. */
    private fun best(votes: Map<Pair<NumberStyle, Int>, Int>, style: NumberStyle): Int? =
        votes.filterKeys { it.first == style }.maxByOrNull { it.value }?.takeIf { it.value >= MIN_VOTES }?.key?.second

    private fun inMargin(text: PageText, range: IntRange): Boolean {
        val boxed = range.filter { text.hasBox(it) }
        if (boxed.isEmpty()) return false
        val top = boxed.minOf { text.top(it) }
        val bottom = boxed.maxOf { text.bottom(it) }
        return bottom < MARGIN || top > 1f - MARGIN
    }

    private fun folio(token: String): Folio? {
        val t = token.replace(EDGE_PUNCTUATION, "")
        val arabic = t.toIntOrNull()?.takeIf { t.length <= MAX_DIGITS && it > 0 }
        // Front matter rarely runs past a few dozen pages; this also keeps words like "mix" (1009) out.
        val roman = PageLabels.parseRoman(t)?.takeIf { it <= MAX_ROMAN }
        val style = if (t == t.lowercase()) NumberStyle.ROMAN_LOWER else NumberStyle.ROMAN_UPPER
        return when {
            arabic != null -> Folio(NumberStyle.ARABIC, arabic)
            roman != null -> Folio(style, roman)
            else -> null
        }
    }

    /** Every 5th page: about 200 pages of a 1,000-page textbook, read once per book in the background. */
    private const val STEP = 5
    private const val MIN_VOTES = 4

    /** Top and bottom 9% of the page: where running heads and folios sit. */
    private const val MARGIN = 0.09f
    private const val MAX_DIGITS = 4
    private val EDGE_PUNCTUATION = Regex("""^[.,()\[\]|–-]+|[.,()\[\]|–-]+$""")
    private const val MAX_ROMAN = 300
}

/** The runs as stored with the book ("" when the book has no numbering of its own). */
fun List<LabelRun>.stored(): String = PageLabels.encode(this)
