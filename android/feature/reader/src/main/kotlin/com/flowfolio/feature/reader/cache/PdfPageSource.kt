package com.flowfolio.feature.reader.cache

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.flowfolio.core.pdf.PdfDocument
import com.flowfolio.core.pdf.PdfException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Renders into one reusable CPU bitmap, then keeps only a GPU-backed (HARDWARE) copy: measured on the reference Fold,
 * six inner-screen pages cost +84 MB this way versus +127 MB as plain bitmaps, before any GPU upload (milestone 1).
 */
class PdfPageSource(private val document: PdfDocument) : PageSource {
    private val lock = Mutex()
    private var scratch: Bitmap? = null

    override suspend fun render(key: PageKey): ImageBitmap? = lock.withLock {
        val (w, h) = key.size
        val bitmap = scratchFor(w, h)
        try {
            document.render(key.page, bitmap)
        } catch (_: PdfException) {
            return@withLock null // damaged page: the reader shows a blank page instead of crashing
        }
        (bitmap.copy(Bitmap.Config.HARDWARE, false) ?: bitmap.copy(Bitmap.Config.ARGB_8888, false))?.asImageBitmap()
    }

    /** Releases the scratch bitmap; the document is owned and closed by the caller. */
    fun release() {
        scratch?.recycle()
        scratch = null
    }

    private fun scratchFor(w: Int, h: Int): Bitmap {
        val current = scratch
        val needed = w.toLong() * h * BYTES_PER_PIXEL
        if (current != null && current.allocationByteCount >= needed) {
            current.reconfigure(w, h, Bitmap.Config.ARGB_8888)
            return current
        }
        current?.recycle()
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { scratch = it }
    }

    private companion object {
        const val BYTES_PER_PIXEL = 4
    }
}
