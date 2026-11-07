package com.flowfolio.core.conversion.epub

import com.flowfolio.core.conversion.ir.Block
import com.flowfolio.core.conversion.ir.BlockType
import com.flowfolio.core.conversion.ir.Chapter
import com.flowfolio.core.conversion.ir.Document
import com.flowfolio.core.conversion.ir.Page
import com.flowfolio.core.conversion.ir.PageKind
import com.flowfolio.core.conversion.pdf.Box
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Stage I writes a partial EPUB: only the given chapters, their spine, manifest, page-list and contents. */
class EpubWriterTest {
    private val modified = "2026-11-07T00:00:00Z"

    @Test
    fun writesOnlyTheGivenChapters() {
        val doc = threeChapterDoc()
        val bytes = write(doc, doc.chapters.subList(0, 1))
        val entries = zipEntries(bytes)
        assertTrue(entries.containsKey("OEBPS/ch001.xhtml"))
        assertFalse(entries.containsKey("OEBPS/ch002.xhtml"))
        assertFalse(entries.containsKey("OEBPS/ch003.xhtml"))
        val opf = entries.getValue("OEBPS/content.opf")
        assertEquals(1, Regex("""<itemref""").findAll(opf).count())
        assertEquals(1, Regex("""<item id="c\d+"""").findAll(opf).count())
        val nav = entries.getValue("OEBPS/nav.xhtml")
        assertFalse("the contents list only finished chapters", nav.contains("ch002.xhtml"))
    }

    @Test
    fun fullWriteMatchesTheDefault() {
        val doc = threeChapterDoc()
        fun defaultWrite() = write(doc, doc.chapters)
        fun explicitWrite(): ByteArray = ByteArrayOutputStream().use { out ->
            EpubWriter(doc, "0123456789abcdef0123456789abcdef", modified).write(out, emptyMap())
            out.toByteArray()
        }
        assertArrayEquals(defaultWrite(), explicitWrite())
    }

    private fun write(doc: Document, chapters: List<Chapter>): ByteArray = ByteArrayOutputStream().use { out ->
        EpubWriter(doc, "0123456789abcdef0123456789abcdef", modified).write(out, emptyMap(), chapters)
        out.toByteArray()
    }

    private fun threeChapterDoc(): Document {
        val titles = listOf("Chapter One", "Chapter Two", "Chapter Three")
        val pages = titles.mapIndexed { i, title ->
            Page(i, 612f, 792f, PageKind.BORN_DIGITAL, emptyList(), emptyList(), emptyList(), 100).also { page ->
                page.blocks = listOf(
                    Block(BlockType.HEADING, i, Box(72f, 72f, 540f, 90f), title),
                    Block(BlockType.PARAGRAPH, i, Box(72f, 100f, 540f, 140f), "Body of $title.")
                )
                page.blocks.forEachIndexed { j, b -> b.id = "p%04d-b%03d".format(i + 1, j) }
            }
        }
        val doc = Document("Book", null, pages, emptyList())
        doc.chapters = titles.mapIndexed { i, title ->
            Chapter(title, pages[i].blocks, "ch%03d.xhtml".format(i + 1)).also { it.pages = listOf(i) }
        }
        return doc
    }

    private fun zipEntries(bytes: ByteArray): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                out[e.name] = zip.readBytes().toString(Charsets.UTF_8)
                zip.closeEntry()
                e = zip.nextEntry
            }
        }
        return out
    }
}
