package com.flowfolio.core.reflow

import android.graphics.Typeface
import android.text.Spanned
import android.text.style.StyleSpan
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

/**
 * Where links sit on a page, and footnotes kept for their popover (the M2 links slice). Line breaks differ on the JVM,
 * so a link wrapping over two lines is checked on the Fold.
 */
@RunWith(AndroidJUnit4::class)
class PageLinksTest {
    private val made = ArrayList<File>()
    private val archives = ArrayList<EpubArchive>()

    @After
    fun tearDown() {
        archives.forEach { it.close() }
        made.forEach { it.delete() }
    }

    @Test
    fun everyLinkOnThePageHasAnAreaInsideTheColumn() {
        val book = book()
        val paginator = book.paginate(SPEC, DENSITY)
        val areas = book.linkAreas(book.pages[book.pageOf(Locator(1, 0))], paginator)
        assertEquals(
            setOf("OEBPS/c2.xhtml#s2", "https://example.org/", "OEBPS/c1.xhtml#fn1"),
            areas.map { it.href }.toSet()
        )
        areas.forEach {
            assertTrue("$it", it.left >= 0f && it.right <= SPEC.width && it.left < it.right)
            assertTrue("$it", it.top >= 0f && it.bottom <= SPEC.height && it.top < it.bottom)
        }
    }

    @Test
    fun footnotesLeaveTheFlowAndKeepTheirStyle() {
        val book = book()
        val note = book.footnote("OEBPS/c1.xhtml#fn1")
        assertEquals("The note text.", note.toString().trim())
        val italic = (note as Spanned).getSpans(0, note.length, StyleSpan::class.java).single()
        assertEquals(Typeface.ITALIC, italic.style)
        assertEquals("note", note.subSequence(note.getSpanStart(italic), note.getSpanEnd(italic)).toString())
        assertNull(book.footnote("OEBPS/c1.xhtml#nowhere"))
        // The note's text is not on the page.
        assertTrue(book.chapters[1].blocks.none { it is Block.Text && "The note" in it.text })
    }

    @Test
    fun aContentsPageInTheReadingOrderShowsItsLinksButPageListsStayHidden() {
        val book = book()
        val paginator = book.paginate(SPEC, DENSITY)
        val toc = book.pageOf(Locator(0, 0))
        assertEquals(
            listOf("OEBPS/c1.xhtml", "OEBPS/c2.xhtml"),
            book.linkAreas(book.pages[toc], paginator).map {
                it.href
            }
        )
        // The page list's "99" is not printed in chapter one.
        assertTrue(book.chapters[1].blocks.none { it is Block.Text && "99" in it.text })
    }

    @Test
    fun rearNotesStayInTheirSectionAndOpenInAPopoverToo() {
        val book = book()
        assertEquals("Rear note.", book.footnote("OEBPS/c2.xhtml#rn1").toString().trim())
        val two = book.chapters[2].blocks.filterIsInstance<Block.Text>().map { it.text.toString().trim() }
        assertTrue("$two", "Notes" in two && "Rear note." in two)
    }

    /** One chapter with a long internal link, a web link and a footnote reference; a second with the link's target. */
    private fun book(): ReflowBook {
        val c1 = """<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><body>
            <nav epub:type="page-list"><ol><li><a href="c2.xhtml#s2">99</a></li></ol></nav>
            <h1>One</h1><p>See <a href="c2.xhtml#s2">the second chapter of this little book, which goes on</a>, the
            <a href="https://example.org/">web</a>, and a note<a epub:type="noteref" href="#fn1">1</a>.</p>
            <aside epub:type="footnote" id="fn1"><p>The <i>note</i> text.</p></aside><p>After the note.</p>
            </body></html>"""
        val c2 = """<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><body>
            <h1>Two</h1><p id="s2">There.</p><section epub:type="rearnotes"><h2>Notes</h2><ol>
            <li id="rn1" epub:type="rearnote"><p>Rear note.</p></li></ol></section></body></html>"""
        val opf = """<package xmlns="http://www.idpf.org/2007/opf" version="3.0"><metadata
            xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>Links</dc:title><dc:language>en</dc:language>
            </metadata><manifest><item id="c1" href="c1.xhtml" media-type="application/xhtml+xml"/>
            <item id="c2" href="c2.xhtml" media-type="application/xhtml+xml"/>
            <item id="toc" href="toc.xhtml" media-type="application/xhtml+xml"/>
            </manifest><spine><itemref idref="toc"/><itemref idref="c1"/><itemref idref="c2"/></spine></package>"""
        val container = """<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0">
            <rootfiles><rootfile full-path="OEBPS/book.opf" media-type="application/oebps-package+xml"/></rootfiles>
            </container>"""
        val file = File.createTempFile("links", ".epub").also(made::add)
        ZipOutputStream(file.outputStream()).use { z ->
            mapOf(
                "mimetype" to "application/epub+zip",
                "META-INF/container.xml" to container,
                "OEBPS/book.opf" to opf,
                "OEBPS/c1.xhtml" to c1,
                "OEBPS/c2.xhtml" to c2,
                "OEBPS/toc.xhtml" to TOC
            ).forEach { (name, text) ->
                z.putNextEntry(ZipEntry(name))
                z.write(text.toByteArray())
            }
        }
        val archive = EpubArchive.open(file).also(archives::add)
        return ReflowBook(archive, EpubParser.parse(archive))
    }

    private companion object {
        /** A printed contents page in the reading order. */
        const val TOC = """<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><body>
            <h1>Contents</h1><nav epub:type="toc"><ol><li><a href="c1.xhtml">One</a></li>
            <li><a href="c2.xhtml">Two</a></li></ol></nav></body></html>"""

        val SPEC = PageSpec(600, 900, 30f, Locale.ENGLISH)
        const val DENSITY = 2f
    }
}
