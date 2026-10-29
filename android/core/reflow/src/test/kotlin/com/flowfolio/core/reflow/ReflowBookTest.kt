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
    fun scrollShowsEachPageDownToItsTextWithAWiderGapBetweenChapters() {
        val book = book()
        book.paginate(SPEC, DENSITY)
        val chapterGap = (3 * SPEC.bodyPx).toInt()
        book.pages.indices.forEach { assertTrue(book.scrollExtent(it) in 1..SPEC.height + chapterGap) }
        // The last page of a chapter ends in at least the gap between chapters below its text.
        val last = book.pages.indexOfLast { it.chapter == 0 }
        val text = book.pages[last].slices.last()
        assertTrue(book.scrollExtent(last) >= text.y.toInt() + chapterGap)
        assertEquals(0, book.scrollExtent(book.pages.size))
    }

    @Test
    fun layingOutOneChapterAgainKeepsTheOthers() {
        val book = book()
        var islandSize: Pair<Int, Int>? = null
        val paginator = book.paginate(SPEC, DENSITY, islandSize = { islandSize })
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

    @Test
    fun theReadersChapterFirstThenTheRestKeepsEveryPageItHad() {
        val whole = book().also { it.paginate(SPEC, DENSITY) }.pages.map { it.chapter to it.start }
        val book = book()
        val paginator = book.paginate(SPEC, DENSITY, first = 1)
        assertTrue("chapter one waits", !book.complete)
        val shown = book.pages.filter { it.chapter == 1 }
        // The reader's chapter has its real pages at once; the chapter before it is estimated.
        assertEquals(whole.filter { it.first == 1 }, shown.map { it.chapter to it.start })
        assertTrue(book.pages.filter { it.chapter == 0 }.all { it.slices.isEmpty() })
        book.applyRest(book.layOutRest(paginator))
        assertTrue(book.complete)
        assertEquals(whole, book.pages.map { it.chapter to it.start })
        // Pages laid out first are the same objects afterwards: their images can move with them.
        shown.forEach { page -> assertTrue(book.pages.any { it === page }) }
    }

    @Test
    fun inASpreadEveryChapterAfterTheFirstStartsOnALeftPage() {
        for (shift in listOf(0, 1)) {
            val book = book()
            val paginator = book.paginate(SPEC, DENSITY, first = 1, spreadShift = shift)
            val startEstimated = book.pageOf(Locator(1, 0))
            assertEquals("shift $shift, estimated", 0, (startEstimated + shift) % 2)
            book.applyRest(book.layOutRest(paginator))
            val start = book.pageOf(Locator(1, 0))
            // Laid out exactly, chapter two still starts on a left page: the spread shown never re-pairs.
            assertEquals("shift $shift", 0, (start + shift) % 2)
            // A blank page comes before it when needed; it has no text and leads back to the chapter's start.
            if (start > 0 && book.pages[start - 1].chapter == 1) {
                val blank = book.pages[start - 1]
                assertTrue(blank.slices.isEmpty())
                assertEquals(Locator(1, 0), book.locatorOf(start - 1))
            }
        }
        // Single-page layout has no blanks.
        val single = book().also { it.paginate(SPEC, DENSITY) }
        assertTrue(single.pages.all { it.slices.isNotEmpty() })
    }

    @Test
    fun aConvertedBooksPdfPagesMapToPlacesAndBack() {
        val book = book(pdfPages = true)
        val section = Locator(0, book.chapters[0].anchors.getValue("page-2"))
        // 0-based PDF pages, whatever the printed labels say.
        assertEquals(listOf(0, 1, 2), book.sourcePages.map { it.second })
        assertEquals(section, book.locatorOfSourcePage(1))
        assertEquals(Locator(1, book.chapters[1].anchors.getValue("page-3")), book.locatorOfSourcePage(2))
        // A page without a marker of its own (a page of only a figure) goes to the last marked page before it.
        assertEquals(book.locatorOfSourcePage(2), book.locatorOfSourcePage(7))
        // A place is on the last page that starts at or before it.
        assertEquals(1, book.sourcePageOf(Locator(section.chapter, section.offset + 5)))
        assertEquals(0, book.sourcePageOf(Locator(0, 0)))
        assertEquals(2, book.sourcePageOf(Locator(1, 40)))
    }

    @Test
    fun aBookWithoutPdfMarkersHasNoSourcePages() {
        val book = book()
        assertTrue(book.sourcePages.isEmpty())
        assertNull(book.locatorOfSourcePage(0))
        assertNull(book.sourcePageOf(Locator(0, 0)))
    }

    /**
     * Two chapters: a heading with an anchor, a block and an inline equation, and plain text. With [pdfPages], the
     * converter's markers of the PDF pages: page 1 starts chapter one, page 2 its section, page 3 chapter two.
     */
    private fun book(pdfPages: Boolean = false): ReflowBook {
        val words = (1..120).joinToString(" ") { "word$it" }
        val math = """<math xmlns="http://www.w3.org/1998/Math/MathML"><mi>x</mi></math>"""
        fun mark(n: Int) = if (pdfPages) """<span id="page-$n"></span>""" else ""
        val c1 = """<html xmlns="http://www.w3.org/1999/xhtml"><body>${mark(1)}<h1>One</h1><p>$words</p>
            <math xmlns="http://www.w3.org/1998/Math/MathML" display="block"><mi>y</mi></math>
            ${mark(2)}<h2 id="s1">Section</h2><p>Inline $math in a sentence. $words</p></body></html>"""
        val c2 = """<html xmlns="http://www.w3.org/1999/xhtml"><body>${mark(3)}<h1>Two</h1><p>$words</p><p>$words</p>
            </body></html>"""
        val pageList = if (!pdfPages) {
            ""
        } else {
            """<nav epub:type="page-list"><ol>
            <li><a href="c1.xhtml#page-1">i</a></li><li><a href="c1.xhtml#page-2">ii</a></li>
            <li><a href="c2.xhtml#page-3">1</a></li></ol></nav>"""
        }
        val nav = """<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><body>
            <nav epub:type="toc"><ol><li><a href="c1.xhtml">One</a></li><li><a href="c2.xhtml">Two</a></li></ol></nav>
            $pageList</body></html>"""
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
