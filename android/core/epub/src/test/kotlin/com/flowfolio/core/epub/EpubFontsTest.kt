package com.flowfolio.core.epub

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The book's own fonts (ADR-0030): CSS, obfuscation, WOFF 1 and the font check. */
@RunWith(AndroidJUnit4::class)
class EpubFontsTest {
    private val made = ArrayList<File>()

    @After
    fun tearDown() = made.forEach { it.delete() }

    /** A minimal font: an sfnt header and two tables, enough for the checks (not for drawing). */
    private val font: ByteArray = run {
        val head = ByteArray(54) { it.toByte() }
        val name = "FlowFolio test font".toByteArray()
        val out = ByteBuffer.allocate(12 + 2 * 16 + 56 + 20)
        out.putInt(0x00010000).putShort(2).putShort(32).putShort(1).putShort(0)
        out.putInt(0x68656164).putInt(0).putInt(44).putInt(head.size) // head
        out.putInt(0x6E616D65).putInt(0).putInt(100).putInt(name.size) // name
        out.put(head).put(ByteArray(2)).put(name).put(ByteArray(1))
        out.array()
    }

    @Test
    fun readsFontFacesAndResolvesTheirFiles() {
        val css = """
            /* @font-face { font-family: Ghost; src: url(ghost.otf); } */
            @font-face { font-family: 'Stix'; font-weight: normal; font-style: normal; src:url(../fonts/S.otf); }
            @font-face { font-family:"Stix"; font-weight: 700; font-style: italic; src: url("../fonts/SBI.woff2")
              format("woff2"), url('../fonts/SBI.woff') format("woff"); }
        """
        val faces = EpubFonts.faces("OPS/css/style.css", css.replace(Regex("/\\*.*?\\*/"), ""))
        assertEquals(
            listOf(
                FontFace("Stix", bold = false, italic = false, path = "OPS/fonts/S.otf"),
                FontFace("Stix", bold = true, italic = true, path = "OPS/fonts/SBI.woff")
            ),
            faces
        )
    }

    @Test
    fun theBodyFamilyComesFromTheBodyRule() {
        val css = "h1 { font-family: 'Display'; } body { font-size: 1em; font-family: 'Stix', serif; }"
        assertEquals("stix", EpubFonts.declaredFamily(css, setOf("body"), setOf("stix", "display")))
        assertNull(EpubFonts.declaredFamily(css, setOf("p"), setOf("stix")))
        // A family the book has no faces for is passed over.
        assertNull(EpubFonts.declaredFamily("body { font-family: Georgia, serif }", setOf("body"), setOf("stix")))
    }

    @Test
    fun idpfObfuscationRoundTrips() {
        val id = " urn:uuid:12345678-1234-1234-1234-123456789abc "
        val hidden = FontFiles.deobfuscateIdpf(font + ByteArray(2000), id)
        assertFalse(hidden.copyOf(font.size).contentEquals(font))
        assertArrayEquals(font + ByteArray(2000), FontFiles.deobfuscateIdpf(hidden, id.trim()))
    }

    @Test
    fun woffBecomesThePlainFontItWraps() {
        val sfnt = font
        val woff = woff(sfnt)
        val back = Woff.toSfnt(woff)!!
        assertTrue(FontFiles.isSfnt(back))
        // The tables' bytes survive (the directory is rewritten).
        assertTrue(back.toList().windowed(19).any { String(it.toByteArray()) == "FlowFolio test font" })
    }

    @Test
    fun onlyRealFontsPass() {
        assertTrue(FontFiles.isSfnt(font))
        assertFalse(FontFiles.isSfnt("<html>not a font</html>".toByteArray()))
        assertFalse(FontFiles.isSfnt(ByteArray(8)))
        assertNull(Woff.toSfnt("wOFF".toByteArray() + ByteArray(40)))
    }

    @Test
    fun aBookWithObfuscatedFontsGivesItsBodyFamily() {
        val id = "urn:uuid:0f9a0a4e-4f3a-4b9f-9a8e-1b2c3d4e5f60"
        val book = epub(
            mapOf(
                "META-INF/container.xml" to CONTAINER,
                "META-INF/encryption.xml" to """<encryption xmlns="urn:oasis:names:tc:opendocument:xmlns:container"
                    xmlns:enc="http://www.w3.org/2001/04/xmlenc#"><enc:EncryptedData>
                    <enc:EncryptionMethod Algorithm="http://www.idpf.org/2008/embedding"/>
                    <enc:CipherData><enc:CipherReference URI="OPS/fonts/body.ttf"/></enc:CipherData>
                    </enc:EncryptedData></encryption>""",
                "OPS/content.opf" to """<?xml version="1.0"?><package xmlns="http://www.idpf.org/2007/opf"
                    version="3.0" unique-identifier="uid"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:identifier id="isbn">9780000000002</dc:identifier><dc:identifier id="uid">$id</dc:identifier>
                    <dc:title>T</dc:title></metadata><manifest>
                    <item id="c" href="c.xhtml" media-type="application/xhtml+xml"/>
                    <item id="s" href="style.css" media-type="text/css"/>
                    <item id="f" href="fonts/body.ttf" media-type="font/ttf"/></manifest>
                    <spine><itemref idref="c"/></spine></package>""",
                "OPS/c.xhtml" to "<html xmlns=\"http://www.w3.org/1999/xhtml\"><body><p>Hi</p></body></html>",
                "OPS/style.css" to
                    "@font-face { font-family: Body; src: url(fonts/body.ttf); } p { font-family: Body; }",
                "OPS/fonts/body.ttf" to FontFiles.deobfuscateIdpf(font, id)
            )
        )
        EpubArchive.open(book).use { archive ->
            val pkg = EpubParser.parse(archive)
            assertEquals(id, pkg.metadata.uniqueIdentifier)
            val family = EpubFonts.bodyFamily(pkg, archive)!!
            assertEquals("Body", family.family)
            assertArrayEquals(font, EpubFonts.fontBytes(pkg, archive, family.faces.single().path))
        }
    }

    /** [sfnt] wrapped as WOFF 1, every table compressed. */
    private fun woff(sfnt: ByteArray): ByteArray {
        val src = ByteBuffer.wrap(sfnt)
        val count = src.getShort(4).toInt()
        val tables = (0 until count).map { i ->
            val at = 12 + i * 16
            val tag = src.getInt(at)
            val checksum = src.getInt(at + 4)
            val data = sfnt.copyOfRange(src.getInt(at + 8), src.getInt(at + 8) + src.getInt(at + 12))
            Triple(tag, checksum, data)
        }
        val packed = tables.map { (_, _, data) ->
            val d = Deflater().apply {
                setInput(data)
                finish()
            }
            val out = ByteArrayOutputStream()
            val buf = ByteArray(256)
            while (!d.finished()) out.write(buf, 0, d.deflate(buf))
            d.end()
            out.toByteArray().let { if (it.size < data.size) it else data }
        }
        val header = 44 + count * 20
        val out = ByteBuffer.allocate(header + packed.sumOf { (it.size + 3) and 3.inv() })
        out.put("wOFF".toByteArray()).putInt(0x00010000).putInt(out.capacity()).putShort(count.toShort())
            .putShort(0).putInt(sfnt.size).putShort(1).putShort(0).putInt(0).putInt(0).putInt(0).putInt(0).putInt(0)
        var offset = header
        tables.forEachIndexed { i, (tag, checksum, data) ->
            out.putInt(tag).putInt(offset).putInt(packed[i].size).putInt(data.size).putInt(checksum)
            offset += (packed[i].size + 3) and 3.inv()
        }
        packed.forEach { out.put(it).put(ByteArray(((it.size + 3) and 3.inv()) - it.size)) }
        return out.array()
    }

    private fun epub(files: Map<String, Any>): File {
        val f = File.createTempFile("fonts", ".epub").also(made::add)
        ZipOutputStream(f.outputStream()).use { z ->
            z.putNextEntry(ZipEntry("mimetype"))
            z.write("application/epub+zip".toByteArray())
            files.forEach { (name, data) ->
                z.putNextEntry(ZipEntry(name))
                z.write(if (data is ByteArray) data else data.toString().toByteArray())
            }
        }
        return f
    }

    private companion object {
        const val CONTAINER = """<?xml version="1.0"?><container version="1.0"
            xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles>
            <rootfile full-path="OPS/content.opf" media-type="application/oebps-package+xml"/>
            </rootfiles></container>"""
    }
}
