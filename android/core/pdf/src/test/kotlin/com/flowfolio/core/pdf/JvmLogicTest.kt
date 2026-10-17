package com.flowfolio.core.pdf

import com.flowfolio.core.model.OutlineEntry
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
    fun `engine error codes map to typed exceptions`() {
        assertTrue(PdfException.fromCode(2) is PdfException.FileError)
        assertTrue(PdfException.fromCode(100) is PdfException.FileError)
        assertTrue(PdfException.fromCode(3) is PdfException.InvalidFormat)
        assertTrue(PdfException.fromCode(4) is PdfException.PasswordRequired)
        assertTrue(PdfException.fromCode(5) is PdfException.UnsupportedSecurity)
        assertTrue(PdfException.fromCode(1) is PdfException.Unknown)
    }
}
