package com.flowfolio.spike.pdf.engine

import android.graphics.Bitmap
import android.util.SizeF
import java.io.Closeable
import java.io.File

/** One PDF library under evaluation. */
interface PdfEngine {
    val name: String

    suspend fun open(file: File): PdfDoc
}

/** An open document. Implementations serialize calls internally; callers may use any thread. */
interface PdfDoc : Closeable {
    val pageCount: Int

    /** Capabilities this engine exposes, used for the feature matrix. */
    val features: Set<Feature>

    /** Page size in PDF points (1/72 inch). */
    suspend fun pageSizePt(index: Int): SizeF

    /** Renders the whole page into a new ARGB_8888 bitmap of exactly [widthPx] x [heightPx], white background. */
    suspend fun render(index: Int, widthPx: Int, heightPx: Int): Bitmap

    /** Document outline (bookmarks); null when the engine cannot read outlines. */
    suspend fun outline(): List<OutlineEntry>?

    /** Printed page label such as "xiv" or "214"; null when absent or unsupported. */
    suspend fun pageLabel(index: Int): String?
}

data class OutlineEntry(val depth: Int, val pageIndex: Int, val title: String)

enum class Feature { OUTLINE, PAGE_LABELS, TEXT, LINKS, GLYPH_GEOMETRY, FONT_INFO, VECTOR_PATHS, SANDBOXED }
