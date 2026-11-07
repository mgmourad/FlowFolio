package com.flowfolio.core.conversion

import com.flowfolio.core.conversion.epub.Sidecar
import com.flowfolio.core.conversion.epub.SourceInfo
import com.flowfolio.core.conversion.ir.Block
import com.flowfolio.core.conversion.ir.BlockType
import com.flowfolio.core.conversion.ir.Chapter
import com.flowfolio.core.conversion.ir.Document
import com.flowfolio.core.conversion.ir.Page
import com.flowfolio.core.conversion.ir.PageKind
import com.flowfolio.core.conversion.pdf.Box
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The sidecar's progressive-delivery fields (spec 6): `complete` and `finishedSpineItems`. */
class SidecarBuilderTest {
    private val json = Json { prettyPrint = false }

    @Test
    fun completeOmitsFinishedSpineItems() {
        val doc = twoChapterDoc()
        val s = SidecarBuilder.build(doc, source(doc), 0.98f)
        assertTrue(s.complete)
        assertEquals(null, s.finishedSpineItems)
        assertFalse(
            "a complete sidecar has no finishedSpineItems",
            json.encodeToString(s).contains("finishedSpineItems")
        )
    }

    @Test
    fun partialListsFinishedSpineItems() {
        val doc = twoChapterDoc()
        val s = SidecarBuilder.build(
            doc,
            source(doc),
            0.98f,
            complete = false,
            finishedSpineItems = listOf("ch001.xhtml")
        )
        assertFalse(s.complete)
        assertEquals(listOf("ch001.xhtml"), s.finishedSpineItems)
    }

    private fun twoChapterDoc(): Document {
        val page = textPage(0, "Chapter One", "First chapter text.")
        val page2 = textPage(1, "Chapter Two", "Second chapter text.")
        val doc = Document("Book", null, listOf(page, page2), emptyList())
        doc.chapters = listOf(
            Chapter("Chapter One", page.blocks, "ch001.xhtml").also { it.pages = listOf(0) },
            Chapter("Chapter Two", page2.blocks, "ch002.xhtml").also { it.pages = listOf(1) }
        )
        return doc
    }

    private fun textPage(index: Int, heading: String, paragraph: String): Page {
        val page = Page(index, 612f, 792f, PageKind.BORN_DIGITAL, emptyList(), emptyList(), emptyList(), 100)
        page.blocks = listOf(
            Block(BlockType.HEADING, index, Box(72f, 72f, 540f, 90f), heading),
            Block(BlockType.PARAGRAPH, index, Box(72f, 100f, 540f, 140f), paragraph)
        )
        page.blocks.forEachIndexed { i, b -> b.id = "p%04d-b%03d".format(index + 1, i) }
        return page
    }

    private fun source(doc: Document) = SourceInfo("book.pdf", "0".repeat(64), doc.pages.size, "Book")
}
