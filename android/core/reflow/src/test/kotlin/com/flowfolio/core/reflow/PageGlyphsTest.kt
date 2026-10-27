package com.flowfolio.core.reflow

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flowfolio.core.epub.EpubArchive
import com.flowfolio.core.epub.EpubParser
import java.io.File
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** A page's text for selecting and placing highlights (M4): characters, their chapter offsets and boxes. */
@RunWith(AndroidJUnit4::class)
class PageGlyphsTest {
    private val made = ArrayList<File>()
    private val archives = ArrayList<EpubArchive>()

    @After
    fun tearDown() {
        archives.forEach { it.close() }
        made.forEach { it.delete() }
    }

    @Test
    fun everyCharacterOfThePageKeepsItsChapterOffset() {
        val book = book(paragraphs = 2)
        val paginator = book.paginate(SPEC, DENSITY)
        val page = book.pages.first()
        val glyphs = book.pageGlyphs(page, paginator)
        val text = book.chapters[0].blocks.filterIsInstance<Block.Text>()
        for (i in 0 until glyphs.length) {
            val c = glyphs.text[i]
            if (c == PageGlyphs.BREAK) continue
            val block = text.first { glyphs.offsets[i] in it.start until it.start + it.length }
            assertEquals("at $i", block.text[glyphs.offsets[i] - block.start], c)
        }
        // Blocks are separated by a break without a box; offsets never go back.
        assertTrue(glyphs.text.contains(PageGlyphs.BREAK))
        for (i in 1 until glyphs.length) assertTrue(glyphs.offsets[i] >= glyphs.offsets[i - 1])
        val breakAt = glyphs.text.indexOf(PageGlyphs.BREAK)
        assertTrue(glyphs.boxes[breakAt * PageGlyphs.FIELDS] < 0f)
    }

    @Test
    fun boxesLieInTheColumnAndReadInLines() {
        val book = book(paragraphs = 1)
        val paginator = book.paginate(SPEC, DENSITY)
        val glyphs = book.pageGlyphs(book.pages.first(), paginator)
        var lastTop = -1f
        for (i in 0 until glyphs.length) {
            val left = glyphs.boxes[i * PageGlyphs.FIELDS]
            if (left < 0f) continue
            val top = glyphs.boxes[i * PageGlyphs.FIELDS + 1]
            val right = glyphs.boxes[i * PageGlyphs.FIELDS + 2]
            val bottom = glyphs.boxes[i * PageGlyphs.FIELDS + 3]
            assertTrue("$i", left >= 0f && right <= SPEC.width + 1f && left <= right)
            assertTrue("$i", top >= 0f && bottom <= SPEC.height && top < bottom)
            assertTrue("$i", top >= lastTop)
            lastTop = top
        }
    }

    @Test
    fun aParagraphSplitAcrossPagesIsShownOncePerPartAndFoundByOffset() {
        val book = book(paragraphs = 80)
        val paginator = book.paginate(SPEC, DENSITY)
        assertTrue(book.pages.size > 1)
        val first = book.pageGlyphs(book.pages[0], paginator)
        val second = book.pageGlyphs(book.pages[1], paginator)
        // The second page starts where the first one ends.
        assertEquals(book.pages[1].start, second.offsets.first { it >= book.pages[1].start })
        assertTrue(first.offsets.last() < book.pages[1].start)
        // A range on the second page only is found there and not on the first.
        val at = book.pages[1].start + 2
        assertNull(first.indicesOf(at, at + 3))
        val found = second.indicesOf(at, at + 3)!!
        assertEquals(at, second.offsets[found.first])
        assertEquals(at + 3, second.offsets[found.last])
    }

    @Test
    fun imagesTakeOnePlaceWithoutABoxAndHaveTheirArea() {
        val book = book(paragraphs = 1, image = true)
        val paginator = book.paginate(SPEC, DENSITY)
        val page = book.pages.first()
        val glyphs = book.pageGlyphs(page, paginator)
        val image = book.chapters[0].blocks.filterIsInstance<Block.Image>().single()
        val i = glyphs.offsets.indexOfFirst { it == image.start }
        assertEquals(PageGlyphs.BREAK, glyphs.text[i])
        val area = book.imageAreas(page).single()
        assertEquals("OEBPS/pic.png", area.path)
        assertTrue(area.right > area.left && area.bottom > area.top)
    }

    private fun book(paragraphs: Int, image: Boolean = false): ReflowBook {
        val body = (1..paragraphs).joinToString("") { n ->
            "<p>Paragraph $n has a few sentences of plain text, long enough to wrap over several lines of the page " +
                "and, with enough of them, onto the next page as well. It goes on a little more to be sure.</p>"
        }
        val pic = if (image) "<p><img src=\"pic.png\" alt=\"A picture\"/></p>" else ""
        val c1 = """<html xmlns="http://www.w3.org/1999/xhtml"><body><h1>One</h1>$body$pic</body></html>"""
        val opf = """<package xmlns="http://www.idpf.org/2007/opf" version="3.0"><metadata
            xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>Glyphs</dc:title><dc:language>en</dc:language>
            </metadata><manifest><item id="c1" href="c1.xhtml" media-type="application/xhtml+xml"/>
            <item id="pic" href="pic.png" media-type="image/png"/>
            </manifest><spine><itemref idref="c1"/></spine></package>"""
        val container = """<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0">
            <rootfiles><rootfile full-path="OEBPS/book.opf" media-type="application/oebps-package+xml"/></rootfiles>
            </container>"""
        val file = File.createTempFile("glyphs", ".epub").also(made::add)
        ZipOutputStream(file.outputStream()).use { z ->
            mapOf(
                "mimetype" to "application/epub+zip".toByteArray(),
                "META-INF/container.xml" to container.toByteArray(),
                "OEBPS/book.opf" to opf.toByteArray(),
                "OEBPS/c1.xhtml" to c1.toByteArray(),
                "OEBPS/pic.png" to PNG
            ).forEach { (name, bytes) ->
                z.putNextEntry(ZipEntry(name))
                z.write(bytes)
            }
        }
        val archive = EpubArchive.open(file).also(archives::add)
        return ReflowBook(archive, EpubParser.parse(archive))
    }

    private companion object {
        val SPEC = PageSpec(600, 900, 30f, Locale.ENGLISH)
        const val DENSITY = 2f

        /** A 1 x 1 PNG. */
        val PNG: ByteArray = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8/5+hHgAHggJ/PchI7wAAAABJRU5ErkJggg=="
        )
    }
}
