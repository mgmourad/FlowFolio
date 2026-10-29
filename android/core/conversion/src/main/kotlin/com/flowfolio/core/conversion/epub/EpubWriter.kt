package com.flowfolio.core.conversion.epub

import com.flowfolio.core.conversion.ir.Block
import com.flowfolio.core.conversion.ir.BlockType
import com.flowfolio.core.conversion.ir.Document
import com.flowfolio.core.conversion.stage.Linking
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Stage I: the EPUB 3 package (`docs/conversion-spec.md` section 2): `mimetype` stored first, the container, the
 * package
 * document with accessibility metadata, `nav.xhtml` with the contents, page list and landmarks, one XHTML file per
 * chapter, `book.css` and the images.
 */
class EpubWriter(private val doc: Document, private val bookId: String, private val modified: String) {
    private val language = "en"

    fun write(out: OutputStream, images: Map<String, ByteArray>) {
        ZipOutputStream(out).use { zip ->
            stored(zip, "mimetype", "application/epub+zip".toByteArray())
            text(zip, "META-INF/container.xml", CONTAINER)
            text(zip, "OEBPS/content.opf", opf(images.keys))
            text(zip, "OEBPS/nav.xhtml", nav())
            text(zip, "OEBPS/book.css", CSS)
            val pages = doc.pages.associateBy { it.index }
            doc.chapters.forEach { text(zip, "OEBPS/${it.file}", ChapterXhtml(it, pages, language).write()) }
            images.forEach { (name, bytes) -> stored(zip, "OEBPS/$name", bytes) }
        }
    }

    private fun opf(images: Set<String>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        append("""<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid" """)
        append("""xml:lang="$language" """)
        append("""prefix="flowfolio: https://flowfolio.app/ns#">""").append('\n')
        append("""<metadata xmlns:dc="http://purl.org/dc/elements/1.1/">""").append('\n')
        append("""<dc:identifier id="bookid">urn:flowfolio:$bookId</dc:identifier>""").append('\n')
        append("<dc:title>${esc(doc.title)}</dc:title>\n")
        doc.author?.let { append("<dc:creator>${esc(it)}</dc:creator>\n") }
        append("<dc:language>$language</dc:language>\n")
        append("""<meta property="dcterms:modified">$modified</meta>""").append('\n')
        append("""<meta property="flowfolio:specVersion">$SPEC_VERSION</meta>""").append('\n')
        listOf("textual", "visual").forEach { append("""<meta property="schema:accessMode">$it</meta>""").append('\n') }
        listOf("tableOfContents", "printPageNumbers", "readingOrder").forEach {
            append("""<meta property="schema:accessibilityFeature">$it</meta>""").append('\n')
        }
        append("""<meta property="schema:accessibilityHazard">none</meta>""").append('\n')
        append(
            """<meta property="schema:accessibilitySummary">Converted from a PDF by FlowFolio. Figures, tables and """
        )
        append("equations are images of the original page with their text as alternative text.</meta>\n")
        append("</metadata>\n<manifest>\n")
        append("""<item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>""").append('\n')
        append("""<item id="css" href="book.css" media-type="text/css"/>""").append('\n')
        doc.chapters.forEachIndexed { n, c ->
            append("""<item id="c${n + 1}" href="${c.file}" media-type="application/xhtml+xml"/>""").append('\n')
        }
        images.sorted().forEachIndexed { n, name ->
            val type = if (name.endsWith(".webp")) "image/webp" else "image/png"
            append("""<item id="i${n + 1}" href="$name" media-type="$type"/>""").append('\n')
        }
        append("</manifest>\n<spine>\n")
        doc.chapters.indices.forEach { append("""<itemref idref="c${it + 1}"/>""").append('\n') }
        append("</spine>\n</package>\n")
    }

    private fun nav(): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        append("<!DOCTYPE html>\n")
        append(
            """<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" """ +
                """lang="$language" xml:lang="$language">"""
        )
        append("<head><meta charset=\"UTF-8\"/><title>${esc(doc.title)}</title></head><body>\n")
        append("""<nav epub:type="toc" id="toc" role="doc-toc"><h1>Contents</h1>""").append('\n')
        append(tocList())
        append("</nav>\n")
        append("""<nav epub:type="page-list" id="page-list" hidden=""><h2>Pages</h2><ol>""")
        val chapterOfPage = doc.chapters.flatMap { c -> c.pages.map { it to c.file } }.toMap()
        doc.pages.forEach { p ->
            chapterOfPage[p.index]?.let { file ->
                append(
                    """<li><a href="$file#page-${p.index + 1}">${esc(p.label ?: (p.index + 1).toString())}</a></li>"""
                )
            }
        }
        append("</ol></nav>\n")
        append("""<nav epub:type="landmarks" id="landmarks" hidden=""><h2>Landmarks</h2><ol>""")
        doc.chapters.firstOrNull()?.let {
            append("""<li><a epub:type="bodymatter" href="${it.file}">${esc(it.title)}</a></li>""")
        }
        append("</ol></nav>\n</body></html>\n")
    }

    /** The outline as nested lists, each entry linked to its heading (or its page); the chapters when there is none. */
    private fun tocList(): String {
        // Reading systems expect the contents in reading order, so no entry may point before the one above it.
        var last = -1L
        val entries = doc.outline.filter { it.page >= 0 }.mapNotNull { e ->
            target(e.page, e.title, last)?.let { (at, href) ->
                last = maxOf(last, at)
                Triple(e.depth, e.title, href)
            }
        }.ifEmpty { doc.chapters.map { Triple(0, it.title, it.file) } }
        val sb = StringBuilder("<ol>")
        var depth = 0
        entries.forEachIndexed { n, (d0, title, href) ->
            val d = d0.coerceAtMost(depth + 1)
            if (n > 0) {
                if (d > depth) sb.append("<ol>") else sb.append("</li>")
                repeat(depth - d) { sb.append("</ol></li>") }
            }
            sb.append("""<li><a href="${esc(href)}">${esc(title.ifBlank { "Untitled" })}</a>""")
            depth = d
        }
        if (entries.isNotEmpty()) sb.append("</li>")
        repeat(depth) { sb.append("</ol></li>") }
        sb.append("</ol>\n")
        return sb.toString()
    }

    /**
     * Where an outline entry for [page] titled [title] lands, at or after reading position [after]: its heading block,
     * else the page's marker, else (when that marker is behind) the same place as the entry before. Positions count
     * the page marker before each block's position, so a heading follows the marker of its page.
     */
    // Each return is one fallback, best first.
    @Suppress("ReturnCount")
    private fun target(page: Int, title: String, after: Long): Pair<Long, String>? {
        val c = doc.chapters.indexOfFirst { page in it.pages }
        if (c < 0) return null
        val chapter = doc.chapters[c]
        fun at(block: Int, heading: Boolean) = c * POSITIONS + block * 2L + if (heading) 1 else 0
        val heading = chapter.blocks.withIndex().firstOrNull { (i, b) ->
            b.page == page && b.type == BlockType.HEADING && at(i, true) >= after && Linking.matches(b.text, title)
        }
        if (heading != null) return at(heading.index, true) to "${chapter.file}#${heading.value.id}"
        val first = chapter.blocks.indexOfFirst { it.page >= page }.let { if (it < 0) chapter.blocks.size else it }
        val marker = at(first, false)
        if (marker >= after || after < 0) return marker to "${chapter.file}#page-${page + 1}"
        return hrefAt(after)?.let { after to it }
    }

    /** The link for reading position [at], as [target] numbers them. */
    // Each return is one fallback, best first.
    @Suppress("ReturnCount")
    private fun hrefAt(at: Long): String? {
        val chapter = doc.chapters.getOrNull((at / POSITIONS).toInt()) ?: return null
        val i = ((at % POSITIONS) / 2).toInt()
        val block = chapter.blocks.getOrNull(i) ?: return chapter.file
        return if (at % 2 == 1L) "${chapter.file}#${block.id}" else "${chapter.file}#page-${block.page + 1}"
    }

    private fun text(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    /** Stored without compression: `mimetype` must be (OCF), and PNGs gain nothing from deflate. */
    private fun stored(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        val entry = ZipEntry(name).apply {
            method = ZipEntry.STORED
            size = bytes.size.toLong()
            compressedSize = bytes.size.toLong()
            crc = CRC32().also { it.update(bytes) }.value
        }
        zip.putNextEntry(entry)
        zip.write(bytes)
        zip.closeEntry()
    }

    companion object {
        const val SPEC_VERSION = "1.2.0"
        private const val POSITIONS = 10_000_000L

        private const val CONTAINER = """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
<rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
</container>
"""

        private const val CSS = """body { margin: 0; }
p { text-align: justify; margin: 0 0 0.6em; }
h1, h2, h3, h4, h5, h6 { text-align: left; }
figure { margin: 1em 0; text-align: center; }
figure img { max-width: 100%; }
figcaption, .ff-caption { font-size: 0.9em; font-style: italic; }
.ff-sidebar { margin: 1em 0; padding: 0.6em 0.8em; border-left: 3px solid #888; }
pre { white-space: pre; font-family: monospace; overflow-x: auto; }
pre.ff-code { font-size: 0.85em; line-height: 1.3; padding: 0.6em 0.8em; background: rgba(127, 127, 127, 0.1);
  border-radius: 6px; }
li { margin: 0 0 0.3em; }
"""
    }
}
