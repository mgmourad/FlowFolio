package com.flowfolio.core.conversion.stage

import com.flowfolio.core.conversion.ir.Block
import com.flowfolio.core.conversion.ir.BlockType
import com.flowfolio.core.conversion.ir.Document

/**
 * Heading levels from the PDF's outline: a heading that matches an outline entry on its page takes the entry's depth
 * (chapters h1, sections h2...), and headings set in parts ("CHAPTER 2" above "Vectors") that together make one entry's
 * title become one heading, dropping a bare chapter number set between them; a label set above a title ("Chapter 5")
 * becomes the heading's first line. Other headings sit one level below the last outline heading before them.
 */
object Headings {
    // Page geometry: each early return or condition is one case of the layout, kept together to read as one rule.
    @Suppress("CyclomaticComplexMethod", "NestedBlockDepth")
    fun apply(doc: Document) {
        if (doc.outline.isEmpty()) return
        val byPage = doc.outline.filter { it.page >= 0 }.groupBy { it.page }
        var base = 0
        for (page in doc.pages) {
            // Each entry names one heading: once taken, a later heading that merely contains a word of it
            // ("Probability" under "Definitions of Statistics, Probability, and Key Terms") is not that entry.
            val entries = byPage[page.index].orEmpty().toMutableList()
            val out = ArrayList<Block>()
            var i = 0
            while (i < page.blocks.size) {
                val b = page.blocks[i]
                val entry = entries.takeIf { b.type == BlockType.HEADING }
                    ?.firstOrNull { Linking.matches(b.text, it.title) }
                if (entry == null) {
                    if (b.type == BlockType.HEADING) b.level = (base + 1).coerceAtMost(MAX_LEVEL)
                    out += b
                    i++
                    continue
                }
                var j = i + 1
                while (j < page.blocks.size) {
                    val next = page.blocks[j]
                    when {
                        // The chapter's number set apart from its title ("1" on a band) goes: the outline numbers it.
                        isNumber(next) -> Unit

                        // The title's other lines, typed as text when they hold no letters ("1660–1763").
                        (next.type == BlockType.HEADING && Linking.matches(next.text, entry.title)) ||
                            (next.type == BlockType.PARAGRAPH && Linking.isPartOf(next.text, entry.title)) ->
                            b.text = b.text + " " + next.text

                        else -> break
                    }
                    j++
                }
                entries -= entry
                out.lastOrNull()?.takeIf { isLabel(it) }?.let { label ->
                    out.removeAt(out.lastIndex)
                    b.text = label.text.trim() + "\n" + b.text
                }
                b.level = (entry.depth + 1).coerceAtMost(MAX_LEVEL)
                base = b.level
                out += b
                i = j
            }
            page.blocks = out
        }
    }

    private fun isLabel(b: Block) =
        (b.type == BlockType.HEADING || b.type == BlockType.PARAGRAPH) && Linking.LABEL.matches(b.text.trim())

    private fun isNumber(b: Block) =
        (b.type == BlockType.HEADING || b.type == BlockType.PARAGRAPH) && b.text.isNotBlank() &&
            b.text.trim().all { it.isDigit() }

    private const val MAX_LEVEL = 6
}
