@file:Suppress("MagicNumber") // the choices' own values, named by their entries

package com.flowfolio.core.model

/**
 * The reading fonts for EPUBs (owner's choice, ADR-0027; bundled in `:core:designsystem`), and [BOOK]: the fonts the
 * book itself ships (ADR-0030), or Literata when it ships none.
 */
enum class ReadingFont { BOOK, LITERATA, CHARTER, ATKINSON_HYPERLEGIBLE, EB_GARAMOND, SOURCE_SANS, LEXEND, ANDIKA }

/** The distance from one line's baseline to the next, as a multiple of the text size: the same in every font. */
enum class LineSpacing(val factor: Float) {
    TIGHT(1.35f),
    NORMAL(1.5f),
    RELAXED(1.7f),
    LOOSE(1.9f)
}

/** The white space at the sides of an EPUB page: at least [minDp], or [fraction] of the page's width if wider. */
enum class PageMargins(val minDp: Float, val fraction: Float) {
    NARROW(14f, 0.03f),
    NORMAL(28f, 0.06f),
    WIDE(44f, 0.1f)
}

/**
 * How an EPUB's text is set (M3, the "Aa" panel's text settings). Any change lays the book out again at the reader's
 * place. [sizeSp] is kept within [MIN_SIZE_SP] and [MAX_SIZE_SP].
 */
data class ReaderText(
    val font: ReadingFont = ReadingFont.LITERATA,
    val sizeSp: Int = DEFAULT_SIZE_SP,
    val lineSpacing: LineSpacing = LineSpacing.NORMAL,
    val margins: PageMargins = PageMargins.NORMAL,
    /** Paragraphs set flush on both sides, as the book asks; off sets every line ragged right. */
    val justify: Boolean = true,
    val hyphenate: Boolean = true
) {
    companion object {
        const val DEFAULT_SIZE_SP = 18
        const val MIN_SIZE_SP = 12
        const val MAX_SIZE_SP = 36
    }
}
