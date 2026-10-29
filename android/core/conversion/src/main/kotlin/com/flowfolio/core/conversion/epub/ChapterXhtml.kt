package com.flowfolio.core.conversion.epub

import com.flowfolio.core.conversion.ir.BOLD_CLOSE
import com.flowfolio.core.conversion.ir.BOLD_OPEN
import com.flowfolio.core.conversion.ir.Block
import com.flowfolio.core.conversion.ir.BlockType
import com.flowfolio.core.conversion.ir.CODE_CLOSE
import com.flowfolio.core.conversion.ir.CODE_OPEN
import com.flowfolio.core.conversion.ir.Chapter
import com.flowfolio.core.conversion.ir.ITALIC_CLOSE
import com.flowfolio.core.conversion.ir.ITALIC_OPEN
import com.flowfolio.core.conversion.ir.ListItem
import com.flowfolio.core.conversion.ir.Mark
import com.flowfolio.core.conversion.ir.OBJECT
import com.flowfolio.core.conversion.ir.Page
import com.flowfolio.core.conversion.ir.SUB_CLOSE
import com.flowfolio.core.conversion.ir.SUB_OPEN
import com.flowfolio.core.conversion.ir.SUP_CLOSE
import com.flowfolio.core.conversion.ir.SUP_OPEN
import com.flowfolio.core.conversion.ir.plain
import com.flowfolio.core.conversion.stage.Blocks

/**
 * One chapter as XHTML (`docs/conversion-spec.md` section 2): every block carries its id and `data-ff-page`; a page
 * break marker stands where each source page starts; a paragraph continued over a page turn is one `<p>` with the
 * marker inside it; a caption next to its figure becomes the figure's `figcaption`.
 */
internal class ChapterXhtml(
    private val chapter: Chapter,
    private val pages: Map<Int, Page>,
    private val language: String
) {
    private val sb = StringBuilder()
    private val marked = HashSet<Int>()

    // One case per way a block follows the one before it (continued, captioned, a listing over a page turn).
    @Suppress("CyclomaticComplexMethod")
    fun write(): String {
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        sb.append("<!DOCTYPE html>\n")
        sb.append("""<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" """)
        sb.append("""lang="${esc(language)}" xml:lang="${esc(language)}">""").append('\n')
        sb.append("<head><meta charset=\"UTF-8\"/><title>${esc(chapter.title)}</title>")
        sb.append("""<link rel="stylesheet" type="text/css" href="book.css"/></head>""").append('\n')
        sb.append("""<body><section epub:type="chapter" role="doc-chapter">""").append('\n')
        val blocks = chapter.blocks
        var i = 0
        while (i < blocks.size) {
            val b = blocks[i]
            // A paragraph continued over a page turn stays one paragraph, unless a page without text came between.
            val continues = b.continuesFrom != null && joinsPrevious() &&
                chapter.pages.none { it < b.page && it !in marked }
            val codeGoesOn = continuesCode(blocks, i)
            if (!continues && !codeGoesOn) markPagesUpTo(b.page, inline = false)
            when {
                continues -> continueParagraph(b)

                codeGoesOn -> continueCode(b)

                b.type == BlockType.CAPTION && blocks.getOrNull(i + 1)?.isPicture() == true -> {
                    markPagesUpTo(blocks[i + 1].page, inline = false)
                    picture(blocks[i + 1], b)
                    i++
                }

                b.isPicture() && blocks.getOrNull(i + 1)?.type == BlockType.CAPTION -> {
                    picture(b, blocks[i + 1])
                    i++
                }

                else -> block(b)
            }
            i++
        }
        chapter.pages.forEach { markPage(it, inline = false) }
        sb.append("</section></body></html>\n")
        return sb.toString()
    }

    private fun Block.isPicture() = type in PICTURES

    /** A code listing broken by a page turn stays one listing, as a paragraph does. */
    private fun continuesCode(blocks: List<Block>, i: Int): Boolean {
        val b = blocks[i]
        val before = blocks.getOrNull(i - 1)
        return b.type == BlockType.CODE && sb.endsWith(CODE_END) && before?.type == BlockType.CODE &&
            before.page == b.page - 1 && chapter.pages.none { it < b.page && it !in marked }
    }

    /** Markers for this chapter's pages before [page] that have not had one (pages with no text get one too). */
    private fun markPagesUpTo(page: Int, inline: Boolean) {
        chapter.pages.filter { it <= page }.forEach { markPage(it, inline) }
    }

    private fun markPage(page: Int, inline: Boolean) {
        if (!marked.add(page)) return
        val label = pages[page]?.label ?: (page + 1).toString()
        val span = """<span epub:type="pagebreak" role="doc-pagebreak" id="page-${page + 1}" title="${esc(label)}" """ +
            """data-ff-page="${page + 1}"/>"""
        if (inline) sb.append(span) else sb.append(span).append('\n')
    }

    private fun joinsPrevious(): Boolean = sb.endsWith("</p>\n")

    /** A code listing continued from the previous page: its next lines, inside the previous `<pre>`. */
    private fun continueCode(b: Block) {
        sb.setLength(sb.length - CODE_END.length)
        sb.append('\n')
        markPage(b.page, inline = true)
        sb.append(esc(b.text, keepLines = true)).append(CODE_END)
    }

    /**
     * A paragraph continued from the previous page: inside the previous `<p>`, after the new page's marker. A word
     * broken over the page turn is joined again.
     */
    private fun continueParagraph(b: Block) {
        sb.setLength(sb.length - "</p>\n".length)
        if (Blocks.brokenWord(sb, b.text)) sb.setLength(sb.length - 1) else sb.append(' ')
        markPage(b.page, inline = true)
        sb.append(Rich(b).text(b.text)).append("</p>\n")
    }

    private fun attrs(b: Block) = """id="${b.id}" data-ff-page="${b.page + 1}""""

    private fun block(b: Block) {
        when (b.type) {
            BlockType.HEADING -> {
                val h = "h${b.level.coerceIn(1, MAX_HEADING)}"
                // A label set above the title ("Chapter 5") is the heading's first line. One Rich for both lines: it
                // hands out the heading's drawings in order.
                val rich = Rich(b)
                sb.append("<$h ${attrs(b)}>${b.text.split('\n').joinToString("<br/>") { rich.text(it) }}</$h>\n")
            }

            BlockType.PARAGRAPH -> sb.append("<p ${attrs(b)}>${Rich(b).text(b.text)}</p>\n")

            BlockType.CAPTION -> sb.append(
                """<p class="ff-caption" ${attrs(b)}>${Rich(b).text(b.text)}</p>"""
            ).append('\n')

            BlockType.CODE ->
                sb.append("""<pre class="ff-code" ${attrs(b)}><code>""").append(esc(b.text, keepLines = true))
                    .append(CODE_END)

            BlockType.LIST -> list(b)

            BlockType.SIDEBAR -> {
                sb.append("""<aside epub:type="sidebar" class="ff-sidebar" ${attrs(b)}>""")
                val rich = Rich(b)
                b.items.forEach { sb.append("<p>${rich.text(it)}</p>") }
                sb.append("</aside>\n")
            }

            BlockType.FIGURE, BlockType.TABLE, BlockType.EQUATION, BlockType.FALLBACK -> picture(b, null)
        }
    }

    /**
     * A list, nested by its items' depths: a deeper item opens a list inside the item before it. Numbered and lettered
     * items make an `<ol>` that starts at the first item's number and counts in its style (1, a, A, i, I).
     */
    private fun list(b: Block) {
        val items = b.listItems.ifEmpty { b.items.map { ListItem(it, "•", 0) } }
        val rich = Rich(b)
        val open = ArrayList<String>()
        for (item in items) {
            val level = item.level.coerceAtMost(open.size)
            if (level >= open.size) {
                val tag = if (item.ordered) "ol" else "ul"
                val first = if (open.isEmpty()) " ${attrs(b)}" else ""
                sb.append("<$tag$first${if (item.ordered) numbering(item.marker) else ""}>")
                open += tag
            } else {
                sb.append("</li>")
                while (open.size > level + 1) sb.append("</${open.removeAt(open.lastIndex)}></li>")
            }
            sb.append(if (item.ordered || item.marker in PLAIN_BULLETS) "<li>" else ownMarker(item.marker))
                .append(rich.text(item.text))
        }
        sb.append("</li>")
        while (open.isNotEmpty()) {
            sb.append("</${open.removeAt(open.lastIndex)}>")
            if (open.isNotEmpty()) sb.append("</li>")
        }
        sb.append('\n')
    }

    /** An item drawn with its own glyph (a tick, an emoji): kept as its `list-style-type`. */
    private fun ownMarker(marker: String) = "<li style=\"list-style-type: '${esc(marker).replace("'", "")} '\">"

    /** An ordered list's `type` and `start` from its first marker ("3." starts at 3; "b)" is lettered, from 2). */
    private fun numbering(marker: String): String {
        val m = marker.trim('(', ')', '.')
        val (type, start) = when {
            m.isNotEmpty() && m.all { it.isDigit() } -> null to m.toInt()

            (ROMAN.matches(m) && m.length > 1) || m in SINGLE_ROMAN -> (if (m[0].isUpperCase()) "I" else "i") to
                roman(m)

            m.length == 1 && m[0].isLowerCase() -> "a" to (m[0] - 'a' + 1)

            m.length == 1 -> "A" to (m[0] - 'A' + 1)

            else -> null to 1
        }
        return (type?.let { " type=\"${it}\"" } ?: "") + (if (start != 1) " start=\"${start}\"" else "")
    }

    private fun roman(s: String): Int {
        val values = s.lowercase().map { ROMAN_VALUES.getValue(it) }
        return values.indices.sumOf { i ->
            if (i + 1 < values.size &&
                values[i] < values[i + 1]
            ) {
                -values[i]
            } else {
                values[i]
            }
        }
    }

    /** A figure, table, equation or fallback: the page region as an image, its text as alt, and its caption. */
    private fun picture(b: Block, caption: Block?) {
        val cls = when (b.type) {
            BlockType.TABLE -> "ff-table ff-fallback"
            BlockType.EQUATION -> "ff-equation ff-fallback"
            BlockType.FALLBACK -> "ff-fallback"
            else -> "ff-figure"
        }
        val coverage = pages[b.page]?.coverage ?: 1f
        val extra = if (b.type ==
            BlockType.FALLBACK
        ) {
            """ data-ff-coverage="${"%.2f".format(java.util.Locale.ROOT, coverage)}""""
        } else {
            ""
        }
        sb.append("""<figure class="$cls" ${attrs(b)}$extra>""")
        val src = b.image
        // Equations are line art: the reader draws their ink in the text colour on any paper (class ff-equation).
        // Equations and tables are ink on white: a reader may draw them in its own text colour (section 2.7).
        // A table with colour of its own (icons, shaded cells) keeps it.
        val inked = b.type == BlockType.EQUATION || (b.type == BlockType.TABLE && !b.coloured)
        val imgClass = if (inked) """ class="ff-equation"""" else ""
        if (src != null) sb.append("""<img$imgClass src="${esc(src)}" alt="${esc(plain(b.text).take(MAX_ALT))}"/>""")
        if (caption !=
            null
        ) {
            sb.append("""<figcaption id="${caption.id}">${Rich(caption).text(caption.text)}</figcaption>""")
        }
        sb.append("</figure>\n")
    }

    private companion object {
        val PICTURES = setOf(BlockType.FIGURE, BlockType.TABLE)
        val PLAIN_BULLETS = setOf("•")
        const val MAX_HEADING = 6
        private const val CODE_END = "</code></pre>\n"
        private val ROMAN = Regex("^[ivxlcIVXLC]+$")

        /** One-letter roman numerals that start a list ("i." is roman; "c." is a letter, the third). */
        private val SINGLE_ROMAN = setOf("i", "I")
        private val ROMAN_VALUES = mapOf('i' to 1, 'v' to 5, 'x' to 10, 'l' to 50, 'c' to 100)
        const val MAX_ALT = 2000
    }
}

/**
 * A block's text as XHTML: escaped, with each [OBJECT] replaced by its drawing as an inline image, in order. The image
 * is as many ems high as the drawing was over the line's text size, so it scales with the reader's text.
 */
private class Rich(block: Block) {
    private val inlines = block.inlines.iterator()

    fun text(raw: String): String {
        val parts = balanced(raw).split(OBJECT)
        val sb = StringBuilder(marks(esc(parts.first())))
        for (part in parts.drop(1)) {
            val inline = if (inlines.hasNext()) inlines.next() else null
            val src = inline?.image
            if (inline != null && src != null) {
                fun em(v: Float) = "%.2f".format(java.util.Locale.ROOT, v)
                val h = em(inline.ems.coerceIn(MIN_EMS, MAX_EMS))
                val w = em(inline.widthEms.coerceIn(MIN_EMS / 2, MAX_WIDTH_EMS))
                val drop = em(-inline.dropEms.coerceIn(-MAX_EMS, MAX_EMS))
                sb.append("""<img class="ff-inline" src="${esc(src)}" alt="" """)
                sb.append("""style="height:${h}em;width:${w}em;vertical-align:${drop}em"/>""")
            }
            sb.append(marks(esc(part)))
        }
        return sb.toString()
    }

    /**
     * [s] with its marks paired, whatever the stages before left: a close with no open goes, as does an open inside
     * one of its own kind, and what is still open at the end is closed. One broken tag would leave the whole chapter
     * unreadable to EPUB reading systems.
     */
    private fun balanced(s: String): String {
        val open = ArrayList<Char>()
        val out = StringBuilder(s.length)
        for (c in s) {
            when (c) {
                in PAIRS -> if (c !in open) {
                    open += c
                    out.append(c)
                }

                in OPENERS -> close(c, open, out)

                else -> out.append(c)
            }
        }
        while (open.isNotEmpty()) out.append(PAIRS.getValue(open.removeAt(open.lastIndex)))
        return out.toString()
    }

    /** Closes [mark]'s run, and first the runs opened inside it, so tags nest; a close with no open is dropped. */
    private fun close(mark: Char, open: MutableList<Char>, out: StringBuilder) {
        val opener = OPENERS.getValue(mark)
        if (opener !in open) return
        while (open.last() != opener) out.append(PAIRS.getValue(open.removeAt(open.lastIndex)))
        open.removeAt(open.lastIndex)
        out.append(mark)
    }

    /** Superscript, subscript and code marks ([com.flowfolio.core.conversion.ir.Line.marked]) as XHTML. */
    private fun marks(s: String) = s.replace(SUP_OPEN.toString(), "<sup>").replace(SUP_CLOSE.toString(), "</sup>")
        .replace(SUB_OPEN.toString(), "<sub>").replace(SUB_CLOSE.toString(), "</sub>")
        .replace(CODE_OPEN.toString(), "<code>").replace(CODE_CLOSE.toString(), "</code>")
        .replace(BOLD_OPEN.toString(), "<strong>").replace(BOLD_CLOSE.toString(), "</strong>")
        .replace(ITALIC_OPEN.toString(), "<em>").replace(ITALIC_CLOSE.toString(), "</em>")

    private companion object {
        const val MIN_EMS = 0.5f
        const val MAX_EMS = 12f
        const val MAX_WIDTH_EMS = 60f
        val PAIRS = Mark.entries.associate { it.open to it.close } + mapOf(SUP_OPEN to SUP_CLOSE, SUB_OPEN to SUB_CLOSE)
        val OPENERS = PAIRS.entries.associate { (open, close) -> close to open }
    }
}
