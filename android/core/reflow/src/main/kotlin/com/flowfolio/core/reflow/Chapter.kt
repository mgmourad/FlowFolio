package com.flowfolio.core.reflow

import android.text.SpannableStringBuilder

/**
 * One spine item as the engine lays it out. Every block has a [Block.start] in the chapter's character offsets: text
 * blocks span their text, images and islands one place each. Anchors, page breaks and locators use these offsets.
 */
class Chapter(
    /** Book-internal path of the XHTML file. */
    val path: String,
    val blocks: List<Block>,
    /** Element ids and where they are (`ch01.xhtml#sec-2` lands on `anchors["sec-2"]`). */
    val anchors: Map<String, Int>,
    /** Footnote bodies by id, kept out of the flow and shown in a popover. */
    val footnotes: Map<String, CharSequence>,
    /** Offset just past the last block. */
    val length: Int
)

sealed interface Block {
    val start: Int
    val length: Int

    /** Text with its spans. [style] says how it sits on the page. */
    class Text(override val start: Int, val text: SpannableStringBuilder, val style: TextStyle) : Block {
        override val length get() = text.length
    }

    /** An image from the book; [path] is book-internal. */
    class Image(override val start: Int, val path: String, val alt: String?) : Block {
        override val length get() = 1
    }

    /** Content laid out by the WebView ([kind] is `math`, `svg` or `table`); [xml] is its markup. */
    class Island(override val start: Int, val id: Int, val kind: String, val xml: String) : Block {
        override val length get() = 1
    }
}

/** How a text block sits on the page, in units of the body text size (em). */
data class TextStyle(
    val scale: Float = 1f,
    val bold: Boolean = false,
    val heading: Boolean = false,
    val indentEm: Float = 0f,
    val spaceBeforeEm: Float = 0f,
    val spaceAfterEm: Float = BODY_SPACE_AFTER_EM,
    val justify: Boolean = false,
    val centered: Boolean = false,
    /** Keep line breaks and spaces as written (`<pre>`). */
    val preformatted: Boolean = false
) {
    companion object {
        const val BODY_SPACE_AFTER_EM = 0.6f
    }
}

/** A place in the book that survives re-pagination: a spine item and a character offset in it. */
data class Locator(val chapter: Int, val offset: Int) : Comparable<Locator> {
    override fun compareTo(other: Locator) =
        if (chapter != other.chapter) chapter.compareTo(other.chapter) else offset.compareTo(other.offset)

    /** Stored with the book (`books.last_locator`). */
    fun encode() = "$chapter:$offset"

    companion object {
        fun decode(s: String?): Locator? {
            val parts = s?.split(':')?.takeIf { it.size == 2 }?.map { it.toIntOrNull() }
            val chapter = parts?.get(0)
            val offset = parts?.get(1)
            if (chapter == null || offset == null) return null
            return Locator(chapter.coerceAtLeast(0), offset.coerceAtLeast(0))
        }
    }
}
