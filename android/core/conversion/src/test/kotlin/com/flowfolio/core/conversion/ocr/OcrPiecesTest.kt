package com.flowfolio.core.conversion.ocr

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The parts of OCR that need no model: decoding, Arabic order, the gate, rectangles, metadata, page building. */
class OcrPiecesTest {
    @Test
    fun ctcDropsBlanksAndRepeats() {
        val alphabet = listOf("", "a", "b", " ")
        // Steps: a a blank a b b -> "aab"
        val steps = listOf(1, 1, 0, 1, 2, 2)
        val probs = FloatArray(steps.size * 4) { 0.01f }
        steps.forEachIndexed { t, c -> probs[t * 4 + c] = 0.9f }
        val (text, confidence) = Ctc.decode(probs, steps.size, 4, alphabet)
        assertEquals("aab", text)
        assertEquals(0.9f, confidence, 1e-4f)
    }

    @Test
    fun arabicVisualOrderBecomesLogical() {
        val logical = "مرحبا بالعالم"
        assertEquals(logical, RtlText.visualToLogical(logical.reversed()))
        // Numbers inside keep their own order.
        assertEquals("سنة 1956 م", RtlText.visualToLogical("م 1956 ةنس"))
        assertEquals("plain text", RtlText.visualToLogical("plain text"))
    }

    @Test
    fun agreementCountsSharedWordsOverTheLongerReading() {
        val a = page("the quick brown fox", "jumps over")
        val b = page("the quick brown cat", "jumps")
        assertEquals(4f / 6f, OcrGate.agree(a, b), 1e-4f)
        // Arabic: vowel marks and letter forms do not count as disagreement.
        assertEquals(1f, OcrGate.agree(page("أَحْمَد في المدرسة"), page("احمد فى المدرسه")), 1e-4f)
    }

    @Test
    fun theGateKeepsOnlyTrustedPages() {
        val sure = OcrPage(listOf(OcrLine("text", PixelBox(0, 0, 10, 10), 0.9f)), "t")
        val unsure = OcrPage(listOf(OcrLine("text", PixelBox(0, 0, 10, 10), 0.5f)), "t")
        val image = OcrImage(1, 1, IntArray(1), 1f)
        assertEquals(sure, OcrGate({ sure }).read(image))
        assertNull(OcrGate({ unsure }).read(image))
        // Two readers: agreement decides, whatever either is sure of; the first reader's text is kept.
        val other = OcrPage(listOf(OcrLine("text", PixelBox(0, 0, 10, 10), 0.1f)), "u")
        assertEquals(unsure, OcrGate({ unsure }, { other }).read(image))
        val different = OcrPage(listOf(OcrLine("nothing alike", PixelBox(0, 0, 10, 10), 0.99f)), "u")
        assertNull(OcrGate({ sure }, { different }).read(image))
    }

    @Test
    fun aTiltedLineGetsATightRotatedRectangle() {
        // A thin bar 100 long tilted by 0.05 rad: an upright box would be about 6 high, the rotated one 1.
        val points = (0..100).flatMap { i ->
            val x = i.toFloat()
            val y = 0.05f * i
            listOf(x to y, x to y + 1f)
        }
        val rect = MinAreaRect.of(points)
        assertTrue("width ${rect.width}", rect.width > 99f)
        assertTrue("height ${rect.height}", rect.height < 1.2f)
        assertTrue("points right", rect.ux > 0.99f)
        assertTrue("points down", rect.vy > 0.99f)
    }

    @Test
    fun metadataIsReadAsUtf8FromTheModelBytes() {
        // ModelProto with ir_version (1, varint), a skipped graph (7), and metadata_props (14) holding a 4-byte char.
        val entry = bytes(1, "character".toByteArray()) + bytes(2, "a\n𠀀\nb".toByteArray())
        val model = byteArrayOf(0x08, 0x0A) + bytes(7, ByteArray(300) { 1 }) + bytes(14, entry)
        val meta = OnnxMetadata.read(model.inputStream())
        assertEquals(listOf("a", "𠀀", "b"), meta["character"]!!.lines())
    }

    @Test
    fun ocrLinesBecomeCharactersOfAPage() {
        val read = OcrPage(
            listOf(
                OcrLine("Hello", PixelBox(0, 0, 100, 20), 0.9f),
                OcrLine("world", PixelBox(0, 30, 100, 50), 0.9f)
            ),
            "paddle"
        )
        val raw = OcrPages.raw(3, 50f, 50f, read, scale = 2f)
        assertEquals("Hello\nworld", raw.text)
        assertTrue(raw.generated[5])
        // The last of five letters ends at the line's right edge: 100 px at 2 px per point is 50 pt.
        assertEquals(50f, raw.box(4).right, 1e-4f)
        assertEquals(10f, raw.box(0).right, 1e-4f)
        assertTrue(raw.images.isEmpty())
    }

    private fun page(vararg lines: String) = OcrPage(lines.map { OcrLine(it, PixelBox(0, 0, 1, 1), 1f) }, "t")

    private fun bytes(field: Int, value: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write((field shl 3) or 2)
        var n = value.size
        while (n >= 0x80) {
            out.write((n and 0x7F) or 0x80)
            n = n ushr 7
        }
        out.write(n)
        out.write(value)
        return out.toByteArray()
    }
}
