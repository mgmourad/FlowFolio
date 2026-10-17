package com.flowfolio.core.pdf

import com.flowfolio.core.model.OutlineEntry
import com.flowfolio.core.pdf.pdfium.LinkCodec
import com.flowfolio.core.pdf.pdfium.OutlineCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JvmLogicTest {
    @Test
    fun `outline lines decode, titles may contain the separator`() {
        val decoded = OutlineCodec.decode(arrayOf("0|6|Contents", "1|16|1.1 A|B ", "0|-1|No destination"))
        assertEquals(
            listOf(
                OutlineEntry(0, 6, "Contents"),
                OutlineEntry(1, 16, "1.1 A|B"),
                OutlineEntry(0, -1, "No destination")
            ),
            decoded
        )
    }

    @Test
    fun `malformed outline lines are skipped, not fatal`() {
        assertEquals(listOf(OutlineEntry(0, 1, "ok")), OutlineCodec.decode(arrayOf("garbage", "x|1|t", "0|1|ok")))
    }

    @Test
    fun `link lines decode into internal and web links, bad ones are skipped`() {
        val decoded = LinkCodec.decode(
            arrayOf(
                "0.100000|0.200000|0.300000|0.250000|41|",
                "0.5|0.5|0.9|0.6|-1|https://openstax.org/l/21scaleuniv",
                "0.1|0.1|0.2|0.2|-1|",
                "nonsense",
                "0.1|x|0.2|0.2|3|"
            )
        )
        assertEquals(
            listOf(
                PdfLink(0.1f, 0.2f, 0.3f, 0.25f, targetPage = 41, uri = null),
                PdfLink(0.5f, 0.5f, 0.9f, 0.6f, targetPage = null, uri = "https://openstax.org/l/21scaleuniv")
            ),
            decoded
        )
        assertTrue(decoded[0].contains(0.2f, 0.22f))
    }

    @Test
    fun `engine error codes map to typed exceptions`() {
        assertTrue(PdfException.fromCode(2) is PdfException.FileError)
        assertTrue(PdfException.fromCode(100) is PdfException.FileError)
        assertTrue(PdfException.fromCode(3) is PdfException.InvalidFormat)
        assertTrue(PdfException.fromCode(4) is PdfException.PasswordRequired)
        assertTrue(PdfException.fromCode(5) is PdfException.UnsupportedSecurity)
        assertTrue(PdfException.fromCode(1) is PdfException.Unknown)
    }
}
