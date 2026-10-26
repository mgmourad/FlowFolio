package com.flowfolio.feature.reader

import androidx.compose.ui.geometry.Offset
import com.flowfolio.core.pdf.PdfLink
import com.flowfolio.core.reflow.ReflowBook

/** What following a link does. */
internal sealed interface LinkTarget {
    /** Jump to [page] of the book. */
    data class Page(val page: Int) : LinkTarget

    /** Offer to open [uri] outside the app (confirmed first; see [PageFrames.isOpenable]). */
    data class Web(val uri: String) : LinkTarget

    /** Show the footnote or endnote [href] points at in a popover (EPUBs). */
    data class Note(val href: String) : LinkTarget

    /** Show island [id] (an equation or table drawn smaller than its size to fit the column) zoomable (EPUBs). */
    data class Island(val id: Int) : LinkTarget
}

/** A link on a page, its box as fractions of the page image (0 to 1), from a PDF's links or an EPUB's text. */
internal data class ReaderLink(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val target: LinkTarget
) {
    fun contains(x: Float, y: Float): Boolean = x in left..right && y in top..bottom
}

/** A footnote reference that was tapped: the note's [href], and where on screen it was tapped. */
internal data class ShownNote(val href: String, val at: Offset)

/**
 * What an EPUB link [href] does in [book]: an absolute address is a web link, a footnote or endnote opens in a popover,
 * a place in the reading order is a jump; anything else (a file outside the reading order) does nothing.
 */
internal fun linkTarget(book: ReflowBook, href: String): LinkTarget? = when {
    ":" in href.substringBefore('/') -> LinkTarget.Web(href)
    book.footnote(href) != null -> LinkTarget.Note(href)
    else -> book.locatorOf(href)?.let { LinkTarget.Page(book.pageOf(it)) }
}

/** A PDF link as the reader's, or null when it leads nowhere. */
internal fun PdfLink.toReaderLink(): ReaderLink? {
    val target = targetPage?.let { LinkTarget.Page(it) } ?: uri?.let { LinkTarget.Web(it) } ?: return null
    return ReaderLink(left, top, right, bottom, target)
}
