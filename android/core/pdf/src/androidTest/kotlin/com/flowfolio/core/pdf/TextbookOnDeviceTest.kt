package com.flowfolio.core.pdf

import android.graphics.Bitmap
import android.os.Debug
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.flowfolio.core.model.PixelSize
import com.flowfolio.core.pdf.pdfium.PdfiumEngine
import java.io.File
import java.io.RandomAccessFile
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests that need the OpenStax test textbook (not committed). Push it first:
 *   adb push samples/corpus/cache/university-physics-volume-1.pdf /data/local/tmp/flowfolio/
 * Skipped when absent (for example in CI).
 */
@RunWith(AndroidJUnit4::class)
class TextbookOnDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().context
    private val book = File("/data/local/tmp/flowfolio/university-physics-volume-1.pdf")
    private val engine = PdfiumEngine()

    private suspend fun open(file: File) =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { engine.open(it) }

    @Test
    fun opensRendersAndReadsOutline() = runBlocking {
        assumeTrue("textbook not on device", book.canRead())
        val t0 = SystemClock.elapsedRealtime()
        open(book).use { doc ->
            val openMs = SystemClock.elapsedRealtime() - t0
            assertEquals(959, doc.pageCount)
            assertEquals(198, doc.outline().size)
            val px = doc.pageSize(110).fitInside(1080, 2520)
            val bmp = Bitmap.createBitmap(px.width, px.height, Bitmap.Config.ARGB_8888)
            doc.render(110, bmp)
            Log.i(TAG, "FF_TEXTBOOK openMs=$openMs page111=${px.width}x${px.height}")
            bmp.recycle()
        }
    }

    @Test
    fun damagedCopiesOfTheTextbookNeverCrash() = runBlocking {
        assumeTrue("textbook not on device", book.canRead())
        val copy = File(context.cacheDir, "damaged-textbook.pdf")
        var opened = 0
        var rejected = 0
        try {
            // Truncations: keep the first part of the file only.
            for (fraction in listOf(0.05, 0.5, 0.95)) {
                book.inputStream().use { input ->
                    copy.outputStream().use { out ->
                        copyPrefix(
                            input,
                            out,
                            (
                                book.length() *
                                    fraction
                                ).toLong()
                        )
                    }
                }
                if (Damage.exercise(engine, copy, maxPages = 5)) opened++ else rejected++
            }
            // Bit flips spread over the whole file, in place on a full copy.
            book.copyTo(copy, overwrite = true)
            val rnd = kotlin.random.Random(99)
            repeat(5) {
                RandomAccessFile(copy, "rw").use { raf ->
                    repeat(200) {
                        val pos = rnd.nextLong(raf.length())
                        raf.seek(pos)
                        val b = raf.read()
                        raf.seek(pos)
                        raf.write(b xor (1 shl rnd.nextInt(8)))
                    }
                }
                if (Damage.exercise(engine, copy, maxPages = 5)) opened++ else rejected++
            }
        } finally {
            copy.delete()
        }
        Log.i(TAG, "FF_ROBUSTNESS textbook opened=$opened rejected=$rejected")
        assertTrue(opened + rejected == 8)
    }

    /**
     * Milestone 1 measurement (Phase 1 plan, memory design point 2): keep pages as ordinary ARGB bitmaps (A), or
     * render into one reusable scratch bitmap and keep only GPU-backed HARDWARE copies (B). Logged, not asserted:
     * the numbers decide the cache design.
     */
    @Test
    fun hardwareBitmapMemoryComparison() = runBlocking {
        assumeTrue("textbook not on device", book.canRead())
        val args = InstrumentationRegistry.getArguments()
        val viewport = PixelSize(
            args.getString("viewportWidth")?.toInt() ?: 2256,
            args.getString("viewportHeight")?.toInt() ?: 2504
        )
        val pages = (100 until 106).toList()
        val result = JSONObject().put("viewport", "${viewport.width}x${viewport.height}").put("pages", pages.size)
        open(book).use { doc ->
            val sizes = pages.associateWith { doc.pageSize(it).fitInside(viewport.width, viewport.height) }

            settle()
            val baseA = memory()
            val keptA = pages.map { p ->
                val s = sizes.getValue(p)
                Bitmap.createBitmap(s.width, s.height, Bitmap.Config.ARGB_8888).also {
                    doc.render(p, it)
                }
            }
            val afterA = memory()
            result.put("A_argb", delta(baseA, afterA))
            keptA.forEach { it.recycle() }

            settle()
            val baseB = memory()
            val maxSize = sizes.values.maxBy { it.width.toLong() * it.height }
            val scratch = Bitmap.createBitmap(maxSize.width, maxSize.height, Bitmap.Config.ARGB_8888)
            val copyMs = ArrayList<Long>()
            val keptB = pages.map { p ->
                val s = sizes.getValue(p)
                scratch.reconfigure(s.width, s.height, Bitmap.Config.ARGB_8888)
                doc.render(p, scratch)
                val t0 = SystemClock.elapsedRealtimeNanos()
                val hw = scratch.copy(Bitmap.Config.HARDWARE, false)
                copyMs += (SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000
                assertNotNull("HARDWARE copy failed", hw)
                hw
            }
            val afterB = memory()
            result.put("B_hardware_plus_one_scratch", delta(baseB, afterB))
            result.put("B_copyMsMax", copyMs.max()).put("B_copyMsMedian", copyMs.sorted()[copyMs.size / 2])
            result.put("pageBytesEach", sizes.values.first().argbBytes)
            keptB.forEach { it.recycle() }
            scratch.recycle()
        }
        Log.i(TAG, "FF_MEMORY $result")
        File(context.getExternalFilesDir(null), "hardware-bitmap-memory.json").writeText(result.toString(2))
        assertTrue(result.has("A_argb") && result.has("B_hardware_plus_one_scratch"))
    }

    private fun settle() {
        repeat(3) {
            Runtime.getRuntime().gc()
            SystemClock.sleep(300)
        }
    }

    private fun memory(): Map<String, Long> {
        val info = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }
        return mapOf(
            "totalPssKb" to info.totalPss.toLong(),
            "nativeHeapKb" to (info.getMemoryStat("summary.native-heap")?.toLong() ?: -1),
            "graphicsKb" to (info.getMemoryStat("summary.graphics")?.toLong() ?: -1)
        )
    }

    private fun delta(before: Map<String, Long>, after: Map<String, Long>) =
        JSONObject().apply { before.keys.forEach { put("${it}Delta", after.getValue(it) - before.getValue(it)) } }

    private fun copyPrefix(input: java.io.InputStream, out: java.io.OutputStream, bytes: Long) {
        val buf = ByteArray(1 shl 16)
        var left = bytes
        while (left > 0) {
            val n = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
            if (n <= 0) break
            out.write(buf, 0, n)
            left -= n
        }
    }

    private companion object {
        const val TAG = "FlowFolioPdfTest"
    }
}
