package com.flowfolio.core.data.flow

import com.flowfolio.core.conversion.ocr.OcrLine
import com.flowfolio.core.conversion.ocr.OcrPage
import com.flowfolio.core.conversion.ocr.OcrResult
import com.flowfolio.core.conversion.ocr.PixelBox
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A scanned page's OCR survives a stopped conversion, and only for the engine that read it (TM1). */
class FileOcrCacheTest {
    private val dir = Files.createTempDirectory("ocr-cache").toFile()

    @Test
    fun aReadPageComesBackAsItWasKept() {
        val cache = FileOcrCache(dir, "1.5.3")
        val page = OcrPage(
            listOf(
                OcrLine("وسافرت إلى الكويت", PixelBox(10, 20, 300, 48), 0.81f),
                OcrLine("a line\twith a tab", PixelBox(10, 60, 280, 88), 0.9f)
            ),
            "tesseract-ara"
        )
        cache.put(7, OcrResult(page, 3f, rendered = true))
        val back = cache.get(7)!!
        assertEquals("tesseract-ara", back.page!!.engine)
        assertEquals(listOf("وسافرت إلى الكويت", "a line with a tab"), back.page!!.lines.map { it.text })
        assertEquals(PixelBox(10, 20, 300, 48), back.page!!.lines[0].box)
        assertEquals(3f, back.scale, 0f)
        assertTrue(back.rendered)
    }

    @Test
    fun aRejectedPageIsRememberedAsRejected() {
        val cache = FileOcrCache(dir, "1.5.3")
        cache.put(2, OcrResult(null, 3f, rendered = true))
        val back = cache.get(2)!!
        assertNull(back.page)
        assertTrue(back.rendered)
        assertNull(cache.get(3))
    }

    @Test
    fun anotherEnginesReadingIsIgnored() {
        FileOcrCache(dir, "1.5.2").put(1, OcrResult(null, 3f, rendered = false))
        assertNull(FileOcrCache(dir, "1.5.3").get(1))
        assertFalse(FileOcrCache(dir, "1.5.2").get(1)!!.rendered)
    }
}
