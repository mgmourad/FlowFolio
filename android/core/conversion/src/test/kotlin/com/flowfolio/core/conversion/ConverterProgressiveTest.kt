package com.flowfolio.core.conversion

import com.flowfolio.core.conversion.epub.Sidecar
import com.flowfolio.core.conversion.epub.SourceInfo
import com.flowfolio.core.conversion.pdf.Crop
import com.flowfolio.core.conversion.pdf.EncodedImage
import com.flowfolio.core.conversion.pdf.FontInfo
import com.flowfolio.core.conversion.pdf.OutlineItem
import com.flowfolio.core.conversion.pdf.PdfSource
import com.flowfolio.core.conversion.pdf.RawPage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Progressive delivery (spec 6): one bundle per chapter, the last identical to a complete conversion. */
class ConverterProgressiveTest {
    private val modified = "2026-11-07T00:00:00Z"

    @Test
    fun emitsOneBundlePerChapterInOrder() {
        val emits = ArrayList<Triple<ByteArray, Sidecar, Int>>()
        val total = convert { epub, sidecar, finished, totalChapters -> emits += Triple(epub, sidecar, finished) }
        assertEquals(2, total)
        assertEquals(listOf(1, 2), emits.map { it.third })
        assertEquals(2, emits.last().second.source.pageCount)
    }

    @Test
    fun partialIsAValidEpubOfFinishedChapters() {
        val emits = ArrayList<Pair<ByteArray, Sidecar>>()
        convert { epub, sidecar, _, _ -> emits += epub to sidecar }
        val (firstEpub, firstSidecar) = emits.first()
        assertFalse(firstSidecar.complete)
        assertEquals(listOf("ch001.xhtml"), firstSidecar.finishedSpineItems)
        val entries = zipEntries(firstEpub)
        assertTrue(entries.containsKey("OEBPS/ch001.xhtml"))
        assertFalse(entries.containsKey("OEBPS/ch002.xhtml"))
    }

    @Test
    fun finalEmitMatchesACompleteConversion() {
        var lastEpub: ByteArray? = null
        convert { epub, _, finished, total -> if (finished == total) lastEpub = epub }
        val complete = ByteArrayOutputStream().use { out ->
            Converter().convert(Source("book.pdf"), SourceInfo("book.pdf", "0".repeat(64), 2, "Book"), out, modified)
            out.toByteArray()
        }
        assertArrayEquals(complete, lastEpub)
    }

    private fun convert(emit: (epub: ByteArray, sidecar: Sidecar, finished: Int, total: Int) -> Unit): Int {
        var total = 0
        Converter().convertProgressively(
            Source("book.pdf"),
            SourceInfo("book.pdf", "0".repeat(64), 2, "Book"),
            modified,
            emit = { writeEpub, sidecar, finished, totalChapters ->
                total = totalChapters
                val epub = java.io.ByteArrayOutputStream().also(writeEpub).toByteArray()
                emit(epub, sidecar, finished, totalChapters)
            }
        )
        return total
    }

    private fun zipEntries(bytes: ByteArray): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                out[e.name] = zip.readBytes().toString(Charsets.UTF_8)
                zip.closeEntry()
                e = zip.nextEntry
            }
        }
        return out
    }

    /** A two-page, two-chapter PDF: one paragraph a page, cut into chapters by its outline. */
    private class Source(private val name: String) : PdfSource {
        override val pageCount = 2

        override fun metadata(key: String) = if (key == "Title") "Book" else null

        override fun outline() = listOf(
            OutlineItem(0, "Chapter One", 0),
            OutlineItem(0, "Chapter Two", 1)
        )

        override fun pageLabel(index: Int) = (index + 1).toString()

        override fun page(index: Int): RawPage {
            val text = "Hello world"
            val n = text.length
            val boxes = FloatArray(n * FIELDS)
            val sizes = FloatArray(n) { 10f }
            val weights = IntArray(n) { 400 }
            val ids = IntArray(n) { 0 }
            val colors = IntArray(n) { 0 }
            val generated = BooleanArray(n)
            var x = 72f
            for (i in 0 until n) {
                val w = if (text[i] == ' ') 4f else 6f
                boxes[i * FIELDS] = x
                boxes[i * FIELDS + 1] = 100f
                boxes[i * FIELDS + 2] = x + w
                boxes[i * FIELDS + 3] = 110f
                x += w + 1f
            }
            return RawPage(
                index,
                612f,
                792f,
                text,
                boxes,
                sizes,
                weights,
                ids,
                colors,
                generated,
                listOf(FontInfo("Serif", 0)),
                emptyList(),
                emptyList()
            )
        }

        override fun renderImages(index: Int, crops: List<Crop>, scale: Float): List<EncodedImage> = emptyList()

        override fun close() = Unit

        companion object {
            const val FIELDS = 4
        }
    }
}
