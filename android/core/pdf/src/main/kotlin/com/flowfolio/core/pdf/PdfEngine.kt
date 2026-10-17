package com.flowfolio.core.pdf

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import com.flowfolio.core.model.OutlineEntry
import com.flowfolio.core.model.PageSize
import java.io.Closeable

/** Opens PDF documents. One implementation (PDFium, ADR-0010) serves both the reader and the converter. */
interface PdfEngine {
    /**
     * Opens a document from [fd]. The engine duplicates the descriptor, so the caller may close its own copy
     * right after this returns. Works for app-private files and Storage Access Framework URIs alike.
     *
     * @throws PdfException.PasswordRequired when the file is encrypted and [password] is missing or wrong.
     */
    suspend fun open(fd: ParcelFileDescriptor, password: String? = null): PdfDocument
}

/** An open document. All calls are safe from any thread; they are serialized internally. */
interface PdfDocument : Closeable {
    val pageCount: Int

    /** Page size in points after the page's own rotation. @throws PdfException.PageError for unreadable pages. */
    suspend fun pageSize(index: Int): PageSize

    /**
     * Renders page [index] into [target] as described by [region]. [target] must be a mutable ARGB_8888 bitmap;
     * it is filled white before drawing. Defaults to the whole page scaled to the target's size.
     */
    suspend fun render(
        index: Int,
        target: Bitmap,
        region: RenderRegion = RenderRegion.fullPage(target.width, target.height)
    )

    /** The outline (bookmarks) in document order; empty when the PDF has none. */
    suspend fun outline(): List<OutlineEntry>

    /** The printed page label (e.g. "xiv", "214"), or null when the PDF defines none for this page. */
    suspend fun pageLabel(index: Int): String?

    /** The document's Title metadata, trimmed; null when absent or blank. */
    suspend fun title(): String?

    /** Links on page [index] (internal jumps and web links); empty when there are none or the page is unreadable. */
    suspend fun links(index: Int): List<PdfLink>

    /** Closes the document. Further calls throw [PdfException.Closed]. Safe to call more than once. */
    override fun close()
}

/**
 * A link on a page. The box is a fraction (0..1) of the page as displayed, top-left origin. Exactly one of
 * [targetPage] (0-based, inside this document) or [uri] (as written in the PDF, not validated) is set.
 */
data class PdfLink(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val targetPage: Int?,
    val uri: String?
) {
    fun contains(x: Float, y: Float): Boolean = x in left..right && y in top..bottom
}

/**
 * Which part of a page lands in the target bitmap: the page is scaled to [pageWidthPx] x [pageHeightPx] and the page
 * pixel ([offsetX], [offsetY]) is drawn at the target's top-left. A full page uses offset 0; a zoom tile uses the
 * tile's position within the zoomed page.
 */
data class RenderRegion(val pageWidthPx: Int, val pageHeightPx: Int, val offsetX: Int = 0, val offsetY: Int = 0) {
    init {
        require(pageWidthPx > 0 && pageHeightPx > 0) { "page size must be positive: $pageWidthPx x $pageHeightPx" }
    }

    companion object {
        fun fullPage(widthPx: Int, heightPx: Int) = RenderRegion(widthPx, heightPx)
    }
}
