package com.flowfolio.tools.convert

import com.flowfolio.core.conversion.ir.Block
import com.flowfolio.core.conversion.ir.BlockType
import com.flowfolio.core.conversion.ir.Document
import com.flowfolio.core.conversion.stage.Linking
import kotlinx.serialization.Serializable

/**
 * A hand-labelled page (`samples/golden/labels/<id>.json`), checked by eye against the PDF: its [headings] with their
 * levels (1 a chapter title), and [order], the opening words of its text blocks in reading order (columns, sidebars and
 * captions included). [page] is the 1-based physical page.
 */
@Serializable
data class PageLabel(
    val page: Int,
    val headings: List<LabelledHeading> = emptyList(),
    val order: List<String> = emptyList()
)

@Serializable
data class LabelledHeading(val text: String, val level: Int)

/** Scores a converted [doc] against hand labels. */
object Labels {
    /** Share of labelled headings found on their page at their level, or -1 when there are none. */
    fun headings(doc: Document, labels: List<PageLabel>): Float {
        val all = labels.flatMap { l -> l.headings.map { l.page to it } }
        if (all.isEmpty()) return -1f
        val found = all.count { (page, h) ->
            blocks(doc, page).any {
                it.type == BlockType.HEADING && it.level == h.level &&
                    Linking.matches(it.text, h.text)
            }
        }
        return found.toFloat() / all.size
    }

    /**
     * Share of consecutive labelled openings that come out in order on their page, or -1 when there are none. An
     * opening is found where its words start in the page's blocks; one that is missing counts both its pairs as wrong.
     */
    fun order(doc: Document, labels: List<PageLabel>): Float {
        var pairs = 0
        var right = 0
        for (label in labels) {
            if (label.order.size < 2) continue
            val text = blocks(doc, label.page).map { normal(it.text) }
            val at = label.order.map { opening -> position(text, normal(opening)) }
            for (i in 0 until at.size - 1) {
                pairs++
                val a = at[i]
                val b = at[i + 1]
                if (a != null && b != null && compareValues(a, b) < 0) right++
            }
        }
        return if (pairs == 0) -1f else right.toFloat() / pairs
    }

    /** A paragraph continued over a page turn keeps each page's part in that page's block, so one page is enough. */
    private fun blocks(doc: Document, page: Int): List<Block> = doc.pages.getOrNull(page - 1)?.blocks.orEmpty()

    /** Where [opening] starts in [blocks]: (block, character), so two openings within one block still compare. */
    private fun position(blocks: List<String>, opening: String): Long? {
        blocks.forEachIndexed { i, t ->
            val c = t.indexOf(opening)
            if (c >= 0) return i * PER_BLOCK + c
        }
        return null
    }

    /** Positions within a block run below this, so (block, character) orders as one number. */
    private const val PER_BLOCK = 1_000_000L

    private fun normal(s: String) = s.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
}
