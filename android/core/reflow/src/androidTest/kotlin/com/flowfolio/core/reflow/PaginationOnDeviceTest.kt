package com.flowfolio.core.reflow

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.flowfolio.core.epub.EpubArchive
import com.flowfolio.core.epub.EpubParser
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Phase 2 exit criterion "correct pagination at multiple font sizes", on real Android text layout: the text of all
 * pages, joined, equals each chapter's text exactly at five sizes, and no line is clipped at a page's bottom.
 * Corpus books in /data/local/tmp/flowfolio are checked too when present (see CLAUDE.md for pushing them).
 */
@RunWith(AndroidJUnit4::class)
class PaginationOnDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun open(file: File): ReflowBook {
        val archive = EpubArchive.open(file)
        return ReflowBook(archive, EpubParser.parse(archive))
    }

    /** Joined page text per block must equal the block's text; returns the first problem, or null. */
    @Suppress("ReturnCount") // the first problem found is the one reported
    private fun check(book: ReflowBook): String? {
        val got = HashMap<Pair<Int, Int>, StringBuilder>()
        for ((n, page) in book.pages.withIndex()) {
            for (s in page.slices.filterIsInstance<Slice.Lines>()) {
                val l = s.layout
                val bottom = s.y + l.getLineBottom(s.last - 1) - l.getLineTop(s.first)
                if (bottom > book.spec.height + 0.5f) return "page $n clips chapter ${page.chapter} block ${s.block}"
                got.getOrPut(page.chapter to s.block) { StringBuilder() }
                    .append(l.text.subSequence(l.getLineStart(s.first), l.getLineEnd(s.last - 1)))
            }
        }
        book.chapters.forEachIndexed { c, chapter ->
            chapter.blocks.forEachIndexed { b, block ->
                if (block is Block.Text && got[c to b]?.toString() != block.text.toString()) {
                    return "chapter $c block $b text differs"
                }
            }
        }
        return null
    }

    private fun checkAtSizes(book: ReflowBook, name: String) {
        for (percent in listOf(80, 100, 130, 170, 220)) {
            val t0 = System.nanoTime()
            book.paginate(PageSpec(WIDTH, HEIGHT, BODY_PX * percent / 100f, book.locale), DENSITY)
            val ms = (System.nanoTime() - t0) / 1_000_000
            Log.i("FlowFolioReflowTest", "FF_PAGINATE $name $percent% pages=${book.pages.size} ms=$ms")
            assertNull("$name at $percent%", check(book))
            // Every page's locator leads back to that page.
            book.pages.indices.forEach { assertEquals("$name page $it", it, book.pageOf(book.locatorOf(it))) }
        }
    }

    @Test
    fun builtBookPaginatesExactlyAtFiveSizes() {
        val book = open(sample())
        checkAtSizes(book, "sample")
        // Footnotes leave the flow; anchors and the page list map to pages.
        assertEquals("Note text.", book.footnote("OEBPS/c1.xhtml#n1").toString())
        assertTrue(book.chapters[0].blocks.none { it is Block.Text && it.text.contains("Note text") })
        val printed = book.printedPages()
        assertEquals("1", printed.first())
        assertEquals(listOf("One", "Two"), book.contents().map { it.second })
    }

    @Test
    fun corpusBooksPaginateExactlyAtFiveSizes() {
        val dir = File("/data/local/tmp/flowfolio")
        val books = dir.listFiles { f -> f.name.endsWith(".epub") && !f.name.startsWith("hostile") }.orEmpty()
        books.forEach { checkAtSizes(open(it), it.name) }
    }

    /** Two chapters with headings, a long paragraph, a footnote, a page list and contents. */
    private fun sample(): File {
        val long = (1..400).joinToString(" ") { "word$it" }
        val chapter1 = """<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><body>
            <span epub:type="pagebreak" id="p1" title="1"/><h1>One</h1><p>$long<a href="#n1">1</a></p>
            <aside epub:type="footnote" id="n1"><p>Note text.</p></aside><p>${long.reversed()}</p></body></html>"""
        val chapter2 = """<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><body>
            <span epub:type="pagebreak" id="p2" title="2"/><h1>Two</h1><ul><li>A list item</li><li>Another</li></ul>
            <pre>  keep   these
  spaces</pre><p>$long</p></body></html>"""
        val nav = """<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><body>
            <nav epub:type="toc"><ol><li><a href="c1.xhtml">One</a></li><li><a href="c2.xhtml">Two</a></li></ol></nav>
            <nav epub:type="page-list"><ol><li><a href="c1.xhtml#p1">1</a></li><li><a href="c2.xhtml#p2">2</a></li></ol>
            </nav></body></html>"""
        val opf = """<package xmlns="http://www.idpf.org/2007/opf" version="3.0"><metadata
            xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>Sample</dc:title><dc:language>en</dc:language>
            </metadata><manifest><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
            <item id="c1" href="c1.xhtml" media-type="application/xhtml+xml"/>
            <item id="c2" href="c2.xhtml" media-type="application/xhtml+xml"/></manifest>
            <spine><itemref idref="c1"/><itemref idref="c2"/></spine></package>"""
        val container = """<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0">
            <rootfiles><rootfile full-path="OEBPS/book.opf" media-type="application/oebps-package+xml"/></rootfiles>
            </container>"""
        val file = File(context.cacheDir, "sample.epub")
        ZipOutputStream(file.outputStream()).use { z ->
            mapOf(
                "mimetype" to "application/epub+zip",
                "META-INF/container.xml" to container,
                "OEBPS/book.opf" to opf,
                "OEBPS/nav.xhtml" to nav,
                "OEBPS/c1.xhtml" to chapter1,
                "OEBPS/c2.xhtml" to chapter2
            ).forEach { (name, text) ->
                z.putNextEntry(ZipEntry(name))
                z.write(text.toByteArray())
            }
        }
        return file
    }

    private companion object {
        // One page of a spread on the unfolded Fold, minus margins.
        const val WIDTH = 1000
        const val HEIGHT = 2300
        const val BODY_PX = 54f
        const val DENSITY = 3f
    }
}
