package com.flowfolio.core.epub

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EpubParserTest {
    private val made = ArrayList<File>()

    @After
    fun tearDown() = made.forEach { it.delete() }

    private fun epub(files: Map<String, Any>, mimetype: Boolean = true): File {
        val f = File.createTempFile("book", ".epub").also(made::add)
        ZipOutputStream(f.outputStream()).use { z ->
            if (mimetype) {
                val bytes = "application/epub+zip".toByteArray()
                z.putNextEntry(
                    ZipEntry("mimetype").apply {
                        method = ZipEntry.STORED
                        size = bytes.size.toLong()
                        crc = CRC32().apply { update(bytes) }.value
                    }
                )
                z.write(bytes)
            }
            files.forEach { (name, data) ->
                z.putNextEntry(ZipEntry(name))
                z.write(if (data is ByteArray) data else data.toString().toByteArray())
            }
        }
        return f
    }

    private fun parse(f: File) = EpubArchive.open(f).use { EpubParser.parse(it) }

    private fun refused(f: File): EpubException.Reason = try {
        parse(f)
        fail("expected the book to be refused")
        error("unreachable")
    } catch (e: EpubException) {
        e.reason
    }

    private val container = """<?xml version="1.0"?><container version="1.0"
        xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles>
        <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>"""

    private fun opf(
        metadata: String,
        manifest: String,
        spine: String = "<itemref idref=\"c1\"/><itemref idref=\"c2\"/>",
        spineAttrs: String = ""
    ) = """<?xml version="1.0"?><package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id">
        <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">$metadata</metadata>
        <manifest>$manifest</manifest><spine $spineAttrs>$spine</spine></package>"""

    private val chapters = """<item id="c1" href="text/ch%201.xhtml" media-type="application/xhtml+xml"/>
        <item id="c2" href="text/ch2.xhtml" media-type="application/xhtml+xml"/>"""

    private val page = "<html xmlns=\"http://www.w3.org/1999/xhtml\"><body><p>Hi</p></body></html>"

    private val nav = """<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><body>
        <nav epub:type="toc"><ol>
          <li><a href="text/ch%201.xhtml">Part One&#160;</a><ol>
            <li><a href="text/ch%201.xhtml#s1">Section 1.1</a></li>
            <li><a href="text/ch2.xhtml">Section 1.2</a></li></ol></li>
          <li><span>Appendices</span><ol><li><a href="text/ch2.xhtml#a">A</a></li></ol></li>
        </ol></nav>
        <nav epub:type="page-list" hidden=""><ol><li><a href="text/ch%201.xhtml#p1">i</a></li>
          <li><a href="text/ch2.xhtml#p2">2</a></li></ol></nav>
        <nav epub:type="landmarks"><ol><li><a epub:type="bodymatter" href="text/ch2.xhtml">Start</a></li></ol></nav>
        </body></html>"""

    @Test
    fun readsAnEpub3PackageWithNestedNavigation() {
        val book = parse(
            epub(
                mapOf(
                    "META-INF/container.xml" to container,
                    "OEBPS/content.opf" to opf(
                        """<dc:title>  A   Title </dc:title><dc:creator>Ann Author</dc:creator><dc:creator>Bo Second</dc:creator>
                <dc:language>en</dc:language><dc:identifier id="id">urn:isbn:978-0-306-40615-7</dc:identifier>
                <dc:publisher>Pub</dc:publisher><dc:description>&lt;p&gt;Long &amp;amp; &lt;b&gt;bold&lt;/b&gt;&lt;/p&gt;</dc:description>
                <dc:date>2026-01-02</dc:date><meta property="rendition:layout">reflowable</meta>""",
                        """$chapters<item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                <item id="img" href="images/cover.jpg" media-type="image/jpeg" properties="cover-image"/>""",
                        spineAttrs = "page-progression-direction=\"rtl\""
                    ),
                    "OEBPS/nav.xhtml" to nav,
                    "OEBPS/text/ch 1.xhtml" to page,
                    "OEBPS/text/ch2.xhtml" to page
                )
            )
        )
        assertEquals("A Title", book.metadata.title)
        assertEquals(listOf("Ann Author", "Bo Second"), book.metadata.creators)
        assertEquals("9780306406157", book.metadata.isbn)
        assertEquals("Long & bold", book.metadata.description)
        assertEquals(listOf("OEBPS/text/ch 1.xhtml", "OEBPS/text/ch2.xhtml"), book.spine.map { it.path })
        assertTrue(book.rightToLeft)
        assertFalse(book.fixedLayout)
        assertEquals("OEBPS/images/cover.jpg", book.coverPath)
        assertEquals(listOf("Part One", "Appendices"), book.toc.map { it.label })
        assertEquals(listOf("OEBPS/text/ch 1.xhtml#s1", "OEBPS/text/ch2.xhtml"), book.toc[0].children.map { it.href })
        // A heading without a link points at its first child.
        assertEquals("OEBPS/text/ch2.xhtml#a", book.toc[1].href)
        assertEquals(
            listOf("i" to "OEBPS/text/ch 1.xhtml#p1", "2" to "OEBPS/text/ch2.xhtml#p2"),
            book.pageList.map {
                it.label to
                    it.href
            }
        )
        assertEquals("Start", book.landmarks.single().label)
    }

    @Test
    fun readsAnEpub2NcxAndMetaCover() {
        val ncx = """<?xml version="1.0"?><ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1"><navMap>
            <navPoint id="n1"><navLabel><text>One</text></navLabel><content src="text/ch%201.xhtml"/>
              <navPoint id="n2"><navLabel><text>One.A</text></navLabel><content src="text/ch2.xhtml#x"/></navPoint></navPoint>
            </navMap><pageList><pageTarget type="normal" value="7"><navLabel><text>7</text></navLabel>
            <content src="text/ch2.xhtml#p7"/></pageTarget></pageList></ncx>"""
        val book = parse(
            epub(
                mapOf(
                    "META-INF/container.xml" to container,
                    "OEBPS/content.opf" to opf(
                        "<dc:title>Two</dc:title><meta name=\"cover\" content=\"pic\"/>" +
                            "<dc:identifier>0306406152</dc:identifier>",
                        "$chapters<item id=\"ncx\" href=\"toc.ncx\" media-type=\"application/x-dtbncx+xml\"/>" +
                            "<item id=\"pic\" href=\"c.png\" media-type=\"image/png\"/>",
                        spineAttrs = "toc=\"ncx\""
                    ),
                    "OEBPS/toc.ncx" to ncx
                )
            )
        )
        assertEquals("One", book.toc.single().label)
        assertEquals("OEBPS/text/ch2.xhtml#x", book.toc.single().children.single().href)
        assertEquals("7", book.pageList.single().label)
        assertEquals("OEBPS/c.png", book.coverPath)
        // Ten digits without "isbn" in the identifier could be anything: not taken as an ISBN.
        assertNull(book.metadata.isbn)
    }

    @Test
    fun coverFallsBackToTheFirstImageOfTheFirstPage() {
        val book = parse(
            epub(
                mapOf(
                    "META-INF/container.xml" to container,
                    "OEBPS/content.opf" to
                        opf(
                            "<dc:title>T</dc:title>",
                            "$chapters<item id=\"i\" href=\"img/front.jpg\" media-type=\"image/jpeg\"/>"
                        ),
                    "OEBPS/text/ch 1.xhtml" to "<html><body><img src=\"../img/front.jpg\" alt=\"\"/></body></html>"
                )
            )
        )
        assertEquals("OEBPS/img/front.jpg", book.coverPath)
    }

    @Test
    fun fixedLayoutIsReported() {
        val book = parse(
            epub(
                mapOf(
                    "META-INF/container.xml" to container,
                    "OEBPS/content.opf" to opf("<meta property=\"rendition:layout\">pre-paginated</meta>", chapters)
                )
            )
        )
        assertTrue(book.fixedLayout)
    }

    @Test
    fun drmIsRefusedButFontObfuscationIsNot() {
        val base = mapOf(
            "META-INF/container.xml" to container,
            "OEBPS/content.opf" to opf("<dc:title>T</dc:title>", chapters)
        )
        fun encryption(algorithm: String) = """<encryption xmlns="urn:oasis:names:tc:opendocument:xmlns:container"
            xmlns:enc="http://www.w3.org/2001/04/xmlenc#"><enc:EncryptedData><enc:EncryptionMethod Algorithm="$algorithm"/>
            </enc:EncryptedData></encryption>"""
        assertEquals(
            EpubException.Reason.DRM,
            refused(
                epub(base + ("META-INF/encryption.xml" to encryption("http://www.w3.org/2001/04/xmlenc#aes128-cbc")))
            )
        )
        parse(epub(base + ("META-INF/encryption.xml" to encryption("http://www.idpf.org/2008/embedding"))))
    }

    @Test
    fun notAnEpubAndDamagedPackagesAreRefused() {
        val pdf = File.createTempFile("notepub", ".pdf").also(made::add).apply { writeText("%PDF-1.7 not a zip") }
        assertEquals(EpubException.Reason.NOT_EPUB, refused(pdf))
        assertEquals(EpubException.Reason.NOT_EPUB, refused(epub(mapOf("OEBPS/content.opf" to "x"))))
        assertEquals(EpubException.Reason.DAMAGED, refused(epub(mapOf("META-INF/container.xml" to container))))
        // Empty spine.
        assertEquals(
            EpubException.Reason.DAMAGED,
            refused(
                epub(
                    mapOf(
                        "META-INF/container.xml" to container,
                        "OEBPS/content.opf" to opf("<dc:title>T</dc:title>", chapters, spine = "")
                    )
                )
            )
        )
    }

    @Test
    fun entityExpansionIsNeverExpanded() {
        val lol = "<!DOCTYPE package [<!ENTITY a \"aaaaaaaaaa\"><!ENTITY b \"&a;&a;&a;&a;&a;&a;&a;&a;&a;&a;\">]>"
        val opf = opf("<dc:title>&b;</dc:title>", chapters).replaceFirst("?>", "?>$lol")
        assertEquals(
            EpubException.Reason.DAMAGED,
            refused(
                epub(
                    mapOf(
                        "META-INF/container.xml" to container,
                        "OEBPS/content.opf" to opf
                    )
                )
            )
        )
    }

    @Test
    fun pathsNeverLeaveTheBook() {
        val f = epub(
            mapOf(
                "META-INF/container.xml" to container,
                "OEBPS/content.opf" to
                    opf(
                        "<dc:title>T</dc:title>",
                        "<item id=\"c1\" href=\"../../../etc/hosts\" media-type=\"application/xhtml+xml\"/>" +
                            "<item id=\"c2\" href=\"ok.xhtml\" media-type=\"application/xhtml+xml\"/>"
                    )
            )
        )
        val book = parse(f)
        // The escaping item is dropped from the manifest, so the spine keeps only the chapter inside the book.
        assertEquals(listOf("OEBPS/ok.xhtml"), book.spine.map { it.path })
        assertNull(EpubArchive.normalize("../secret"))
        assertNull(EpubArchive.resolve("OEBPS/a.xhtml", "https://example.com/x.png"))
        assertEquals("OEBPS/b c.xhtml#f", EpubArchive.resolve("OEBPS/a.xhtml", "b%20c.xhtml#f"))
    }

    @Test
    fun oversizedEntriesAreNotRead() {
        val big = ByteArray((EpubArchive.MAX_ENTRY_BYTES + 1).toInt())
        val f = epub(mapOf("META-INF/container.xml" to container, "OEBPS/huge.bin" to big))
        EpubArchive.open(f).use { a ->
            assertNull(a.read("OEBPS/huge.bin"))
            assertTrue(a.read("META-INF/container.xml")!!.isNotEmpty())
        }
    }
}
