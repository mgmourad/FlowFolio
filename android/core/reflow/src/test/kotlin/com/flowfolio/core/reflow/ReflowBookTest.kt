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
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Locators, anchors and islands, and laying out one chapter again, on the JVM. Exact pagination on real text layout
 * is checked on the device (`PaginationOnDeviceTest`); here nothing depends on where lines break.
 */
@RunWith(AndroidJUnit4::class)
class ReflowBookTest {
    private val made = ArrayList<File>()
    private val archives = ArrayList<EpubArchive>()

    @After
    fun tearDown() {
        archives.forEach { it.close() }
        made.forEach { it.delete() }
    }

    @Test
    fun locatorsRoundTripAndOrder() {
        val at = Locator(3, 1250)
        assertEquals("3:1250", at.encode())
        assertEquals(at, Locator.decode(at.encode()))
        assertEquals(Locator(0, 0), Locator.decode("-2:-5"))
        listOf(null, "", "3", "a:b", "1:2:3").forEach { assertNull(it, Locator.decode(it)) }
        assertTrue(Locator(1, 900) < Locator(2, 0))
        assertTrue(Locator(2, 10) < Locator(2, 11))
    }

    @Test
    fun anchorsAndIslandsAreFoundInTheText() {
        val book = book()
        val c1 = book.chapters[0]
        // The section anchor sits where its heading starts.
        val heading = c1.blocks.filterIsInstance<Block.Text>().first { it.text.toString() == "Section" }
        assertEquals(heading.start, c1.anchors["s1"])
        // One block equation, one inline in running text.
        assertEquals(1, c1.blocks.count { it is Block.Island })
        assertEquals(2, book.islands(0).size)
        assertTrue(book.islands(0).all { (_, xml) -> xml.startsWith("<math") })
        assertTrue(book.islands(1).isEmpty())
    }

    @Test
    fun hrefsMapToLocators() {
        val book = book()
        assertEquals(Locator(1, 0), book.locatorOf("OEBPS/c2.xhtml"))
        assertEquals(Locator(0, book.chapters[0].anchors.getValue("s1")), book.locatorOf("OEBPS/c1.xhtml#s1"))
        // An unknown anchor lands at the chapter's start; a file outside the reading order has no place.
        assertEquals(Locator(1, 0), book.locatorOf("OEBPS/c2.xhtml#nowhere"))
        assertNull(book.locatorOf("OEBPS/nav.xhtml"))
    }

    @Test
    fun everyPageLocatorLeadsBackToItsPage() {
        val book = book()
        book.paginate(SPEC, DENSITY)
        assertTrue(book.pages.isNotEmpty())
        book.pages.indices.forEach { assertEquals(it, book.pageOf(book.locatorOf(it))) }
        assertEquals(listOf("One", "Two"), book.contents().map { it.second })
    }

    @Test
    fun layingOutOneChapterAgainKeepsTheOthers() {
        val book = book()
        var islandSize: Pair<Int, Int>? = null
        val paginator = book.paginate(SPEC, DENSITY) { islandSize }
        val before = book.pages.toList()
        val firstOfTwo = book.pageOf(Locator(1, 0))
        val inTwo = book.locatorOf(before.lastIndex)

        // The block equation is measured: nearly a page tall, so chapter one needs more pages.
        islandSize = SPEC.width to SPEC.height * 9 / 10
        book.repaginate(0, paginator)
        val grown = book.pageOf(Locator(1, 0)) - firstOfTwo
        assertTrue("chapter one grew", grown > 0)
        assertEquals(before.size + grown, book.pages.size)
        // Chapter two's pages are the same objects, moved along; its locators follow them.
        before.subList(firstOfTwo, before.size).forEachIndexed { i, page ->
            assertSame(page, book.pages[firstOfTwo + grown + i])
        }
        assertEquals(before.lastIndex + grown, book.pageOf(inTwo))
        assertEquals(firstOfTwo + grown, book.contents().last().third)
    }

    /** Two chapters: a heading with an anchor, a block and an inline equation, and plain text. */
    private fun book(): ReflowBook {
        val words = (1..120).joinToString(" ") { "word$it" }
        val math = """<math xmlns="http://www.w3.org/1998/Math/MathML"><mi>x</mi></math>"""
        val c1 = """<html xmlns="http://www.w3.org/1999/xhtml"><body><h1>One</h1><p>$words</p>
            <math xmlns="http://www.w3.org/1998/Math/MathML" display="block"><mi>y</mi></math>
            <h2 id="s1">Section</h2><p>Inline $math in a sentence. $words</p></body></html>"""
        val c2 = """<html xmlns="http://www.w3.org/1999/xhtml"><body><h1>Two</h1><p>$words</p><p>$words</p>
            </body></html>"""
        val nav = """<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><body>
            <nav epub:type="toc"><ol><li><a href="c1.xhtml">One</a></li><li><a href="c2.xhtml">Two</a></li></ol></nav>
            </body></html>"""
        val opf = """<package xmlns="http://www.idpf.org/2007/opf" version="3.0"><metadata
            xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>Test</dc:title><dc:language>en</dc:language>
            </metadata><manifest><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
            <item id="c1" href="c1.xhtml" media-type="application/xhtml+xml"/>
            <item id="c2" href="c2.xhtml" media-type="application/xhtml+xml"/></manifest>
            <spine><itemref idref="c1"/><itemref idref="c2"/></spine></package>"""
        val container = """<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0">
            <rootfiles><rootfile full-path="OEBPS/book.opf" media-type="application/oebps-package+xml"/></rootfiles>
            </container>"""
        val file = File.createTempFile("reflow", ".epub").also(made::add)
        ZipOutputStream(file.outputStream()).use { z ->
            mapOf(
                "mimetype" to "application/epub+zip",
                "META-INF/container.xml" to container,
                "OEBPS/book.opf" to opf,
                "OEBPS/nav.xhtml" to nav,
                "OEBPS/c1.xhtml" to c1,
                "OEBPS/c2.xhtml" to c2
            ).forEach { (name, text) ->
                z.putNextEntry(ZipEntry(name))
                z.write(text.toByteArray())
            }
        }
        val archive = EpubArchive.open(file).also(archives::add)
        return ReflowBook(archive, EpubParser.parse(archive))
    }

    private companion object {
        val SPEC = PageSpec(600, 900, 30f, Locale.ENGLISH)
        const val DENSITY = 2f
    }
}
