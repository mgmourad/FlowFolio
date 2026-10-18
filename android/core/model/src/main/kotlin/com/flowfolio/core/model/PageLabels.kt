package com.flowfolio.core.model

/** How a run of pages is numbered. [NONE] pages have no printed number (covers, blank pages). */
enum class NumberStyle { ARABIC, ROMAN_LOWER, ROMAN_UPPER, LITERAL, NONE }

/**
 * Pages from [start] (0-based) up to the next run are numbered [prefix] + the number [first] counting up in [style]
 * ("i", "ii"...; "A-1", "A-2"...). A [NumberStyle.LITERAL] run is one page labelled [prefix].
 */
data class LabelRun(val start: Int, val style: NumberStyle, val prefix: String, val first: Int)

/**
 * A book's printed page numbers as compact runs, stored with the book (owner, 2026-10-18: page numbers follow the
 * book's own numbering, front matter in roman numerals, appendices with their prefixes; with none, the cover is
 * page 1).
 */
object PageLabels {
    /** "start|STYLE|first|prefix" runs joined by ";". */
    fun encode(runs: List<LabelRun>): String =
        runs.joinToString(";") { "${it.start}|${it.style.name}|${it.first}|${it.prefix.replace(SEPARATORS, "")}" }

    fun decode(text: String?): List<LabelRun> = text.orEmpty().split(';').mapNotNull { part ->
        val f = part.split('|', limit = FIELDS)
        val start = f.getOrNull(0)?.toIntOrNull()
        val style = NumberStyle.entries.firstOrNull { it.name == f.getOrNull(1) }
        val first = f.getOrNull(2)?.toIntOrNull()
        if (start == null || style == null ||
            first == null
        ) {
            null
        } else {
            LabelRun(start, style, f.getOrElse(FIELDS - 1) { "" }, first)
        }
    }

    /** The printed label of [page], or null when the book has none for it. */
    fun label(runs: List<LabelRun>, page: Int): String? {
        val run = runs.lastOrNull { it.start <= page } ?: return null
        val n = run.first + (page - run.start)
        return when (run.style) {
            NumberStyle.ARABIC -> run.prefix + n
            NumberStyle.ROMAN_LOWER -> run.prefix + roman(n).lowercase()
            NumberStyle.ROMAN_UPPER -> run.prefix + roman(n)
            NumberStyle.LITERAL -> run.prefix.takeIf { page == run.start }
            NumberStyle.NONE -> null
        }
    }

    /** All labels for a book of [pageCount] pages. */
    fun expand(runs: List<LabelRun>, pageCount: Int): List<String?> = List(pageCount) { label(runs, it) }

    /** Runs that reproduce [labels] exactly (each null starts a run of unnumbered pages). */
    fun compress(labels: List<String?>): List<LabelRun> {
        val runs = mutableListOf<LabelRun>()
        labels.forEachIndexed { page, text ->
            val last = runs.lastOrNull()
            if (last != null && label(listOf(last), page) == text) return@forEachIndexed
            runs += if (text == null) LabelRun(page, NumberStyle.NONE, "", 0) else parse(text, page)
        }
        return runs
    }

    /** A run starting at [page] whose first label is [text]. */
    private fun parse(text: String, page: Int): LabelRun {
        val digits = TRAILING_DIGITS.find(text)
        if (digits != null && digits.value.length <= MAX_DIGITS) {
            return LabelRun(page, NumberStyle.ARABIC, text.removeSuffix(digits.value), digits.value.toInt())
        }
        val r = parseRoman(text)
        return when {
            r != null && text == text.lowercase() -> LabelRun(page, NumberStyle.ROMAN_LOWER, "", r)
            r != null -> LabelRun(page, NumberStyle.ROMAN_UPPER, "", r)
            else -> LabelRun(page, NumberStyle.LITERAL, text, 0)
        }
    }

    /** "xiv" -> 14; null unless [text] is a well-formed roman numeral (so words like "mix" are not numbers). */
    fun parseRoman(text: String): Int? {
        val s = text.uppercase()
        if (s.isEmpty() || s.any { it !in ROMAN_DIGITS }) return null
        var total = 0
        for (i in s.indices) {
            val v = ROMAN_DIGITS.getValue(s[i])
            val next = s.getOrNull(i + 1)?.let { ROMAN_DIGITS.getValue(it) } ?: 0
            total += if (v < next) -v else v
        }
        return total.takeIf { it in 1..MAX_ROMAN && roman(it) == s }
    }

    /** 14 -> "XIV". */
    fun roman(n: Int): String {
        var rest = n
        return buildString {
            for ((value, symbol) in ROMAN) {
                while (rest >= value) {
                    append(symbol)
                    rest -= value
                }
            }
        }
    }

    private const val FIELDS = 4
    private const val MAX_DIGITS = 6
    private const val MAX_ROMAN = 3999
    private val SEPARATORS = Regex("[|;]")
    private val TRAILING_DIGITS = Regex("\\d+$")
    private val ROMAN_DIGITS = mapOf('I' to 1, 'V' to 5, 'X' to 10, 'L' to 50, 'C' to 100, 'D' to 500, 'M' to 1000)
    private val ROMAN = listOf(
        1000 to "M", 900 to "CM", 500 to "D", 400 to "CD", 100 to "C", 90 to "XC",
        50 to "L", 40 to "XL", 10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I"
    )
}

/** [page]'s printed number in this book, or its position counting the cover as 1 when the book prints none. */
fun Book.pageLabel(page: Int): String = PageLabels.label(PageLabels.decode(pageLabels), page) ?: (page + 1).toString()

/** The book's last printed page number when it is a plain number, else its page count (for "p. 48 of 922"). */
fun Book.printedPages(): String =
    PageLabels.label(PageLabels.decode(pageLabels), pageCount - 1)?.takeIf { it.all(Char::isDigit) }
        ?: pageCount.toString()
