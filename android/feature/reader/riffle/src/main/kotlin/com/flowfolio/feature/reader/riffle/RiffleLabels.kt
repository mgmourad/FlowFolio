package com.flowfolio.feature.reader.riffle

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/** The words the scrubber shows for a unit: its section, and its printed pages. */
object RiffleLabels {
    /** The section [page] is in, or null before the first one. */
    fun sectionOf(book: BookGeometry, page: Int): Section? = book.sections.lastOrNull { it.firstPage <= page }

    /** "Chapter 7 · Membrane Transport", "Glossary", or nothing when the book has no sections there. */
    @Composable
    fun section(book: BookGeometry, unit: Int): String {
        val section = sectionOf(book, book.pagesOf(unit).last) ?: return ""
        val number = section.number
        return if (section.kind == SectionKind.CHAPTER && number != null) {
            stringResource(R.string.riffle_chapter, number, section.title)
        } else {
            section.title
        }
    }

    /** "p. 433": one page by its printed number. */
    @Composable
    fun pageLabel(book: BookGeometry, page: Int): String = stringResource(R.string.riffle_page, book.label(page))

    /** The dock header's numbers: "Page 216 – 217" for a spread, "Page 1" for one page (owner, 2026-10-21). */
    @Composable
    fun header(book: BookGeometry, unit: Int): String {
        val pages = book.pagesOf(unit)
        return if (pages.first == pages.last) {
            stringResource(R.string.riffle_header_page, book.label(pages.first))
        } else {
            stringResource(R.string.riffle_header_pages, book.label(pages.first), book.label(pages.last))
        }
    }

    /** "pp. 216–217" for a spread, "p. 1" for one page, with the book's printed numbers. */
    @Composable
    fun pages(book: BookGeometry, unit: Int): String {
        val pages = book.pagesOf(unit)
        return if (pages.first == pages.last) {
            stringResource(R.string.riffle_page, book.label(pages.first))
        } else {
            stringResource(R.string.riffle_pages, book.label(pages.first), book.label(pages.last))
        }
    }
}
