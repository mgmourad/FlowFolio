package com.flowfolio.feature.reader.riffle

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.flow.Flow

/**
 * The book as the riffle scrubber sees it (ADR-0022): its pages, the reader's units (single pages or spreads) and the
 * pages each shows, its sections and printed page numbers. Pages are 0-based.
 */
interface BookGeometry {
    val pageCount: Int

    /** Units the reader turns through: pages in single layout, spreads in spread layout. */
    val unitCount: Int

    /** The pages [unit] shows, in reading order. */
    fun pagesOf(unit: Int): IntRange

    fun unitOf(page: Int): Int

    /** Chapters and other sections from the contents, in page order; empty when the book has none. */
    val sections: List<Section>

    /** The page's printed number ("xiv", "216"), or its position counting from 1 when the book prints none. */
    fun label(page: Int): String

    /** The page carrying printed number [label], or null when no page does. */
    fun pageOfLabel(label: String): Int?

    /** Right-to-left books are bound on the right: their front cover is at the strip's right end. */
    val rightToLeft: Boolean
}

/** A section starting at [firstPage]; [number] counts chapters from 1, and is null for other kinds. */
data class Section(val firstPage: Int, val title: String, val kind: SectionKind, val number: Int? = null)

enum class SectionKind { CHAPTER, GLOSSARY, INDEX, OTHER }

/** A bookmark shown as a flag on top of the fore edge; [color] is ARGB. */
data class RiffleFlag(val id: Long, val page: Int, val color: Int, val note: String? = null)

/** The book's bookmarks, as they change. */
fun interface RiffleFlags {
    fun flags(): Flow<List<RiffleFlag>>
}

/** Small page images for the loupe (ADR-0022): cached, prefetched, rendered after the reader's own pages. */
interface PageThumbnailProvider {
    /** The thumbnail if it is ready now (for the next frame), else null. */
    fun cached(page: Int): ImageBitmap?

    /** Renders the thumbnail if needed; null when the page cannot be drawn. */
    suspend fun load(page: Int): ImageBitmap?

    /** Asks for [pages] ahead of time, nearest first; earlier requests not in [pages] may be dropped. */
    fun prefetch(pages: List<Int>)
}
