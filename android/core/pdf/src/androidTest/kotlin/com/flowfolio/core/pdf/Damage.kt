package com.flowfolio.core.pdf

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import java.io.File
import kotlin.random.Random

/** Builds damaged copies of PDFs and drives the engine through them. A native crash fails the whole test run. */
object Damage {
    fun truncations(bytes: ByteArray): List<ByteArray> =
        listOf(0.1, 0.25, 0.5, 0.75, 0.9, 0.99).map { bytes.copyOf((bytes.size * it).toInt().coerceAtLeast(1)) }

    fun bitFlips(bytes: ByteArray, variants: Int, seed: Int, flipsPerVariant: Int = 8): List<ByteArray> {
        val rnd = Random(seed)
        return List(variants) {
            bytes.copyOf().also { copy ->
                repeat(flipsPerVariant) {
                    val i = rnd.nextInt(copy.size)
                    copy[i] = (copy[i].toInt() xor (1 shl rnd.nextInt(8))).toByte()
                }
            }
        }
    }

    /**
     * Opens [file] and touches every page (size, label, small render). Returns true if it opened, false if the
     * engine rejected it with a [PdfException]. Any other exception propagates and fails the test.
     */
    suspend fun exercise(engine: PdfEngine, file: File, password: String? = null, maxPages: Int = 8): Boolean {
        val doc = try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { engine.open(it, password) }
        } catch (_: PdfException) {
            return false
        }
        doc.use {
            it.outline()
            val target = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
            for (page in 0 until minOf(it.pageCount, maxPages)) {
                try {
                    it.pageLabel(page)
                    val size = it.pageSize(page).fitInside(64, 64)
                    it.render(page, target, RenderRegion.fullPage(size.width, size.height))
                } catch (_: PdfException) {
                    // A damaged page is an acceptable outcome; crashing is not.
                }
            }
            target.recycle()
        }
        return true
    }
}
