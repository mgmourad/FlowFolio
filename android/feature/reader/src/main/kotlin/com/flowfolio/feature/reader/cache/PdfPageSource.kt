package com.flowfolio.feature.reader.cache

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.flowfolio.core.pdf.PageTint
import com.flowfolio.core.pdf.PdfDocument
import com.flowfolio.core.pdf.PdfException
import com.flowfolio.core.pdf.RenderRegion
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Renders into one reusable CPU bitmap, then keeps only a 16-bit GPU-backed (HARDWARE) copy. Measured on the reference
 * Fold: HARDWARE copies cost +84 MB vs +127 MB as plain bitmaps for six inner-screen pages (milestone 1), and 16-bit
 * pages cut the reader's total by a further 37-70 MB (ADR-0013). Zoom tiles go through the same path.
 */
class PdfPageSource(private val document: PdfDocument) :
    PageSource,
    TileSource {
    private val lock = Mutex()
    private var scratch: Bitmap? = null

    override suspend fun render(key: PageKey): ImageBitmap? {
        val (w, h) = key.size
        return renderRegion(key.page, w, h, RenderRegion.fullPage(w, h), key.tone.tint)
    }

    override suspend fun render(key: TileKey): ImageBitmap? = renderRegion(
        key.page,
        key.widthPx,
        key.heightPx,
        RenderRegion(key.pageWidthPx, key.pageHeightPx, key.tx * TileKey.TILE_PX, key.ty * TileKey.TILE_PX),
        key.tone.tint
    )

    private suspend fun renderRegion(page: Int, w: Int, h: Int, region: RenderRegion, tint: PageTint?): ImageBitmap? =
        if (w <= 0 || h <= 0) null else lock.withLock { renderLocked(page, w, h, region, tint) }

    @Suppress("LongParameterList") // one page render: what, how big, which part, which colours
    private suspend fun renderLocked(page: Int, w: Int, h: Int, region: RenderRegion, tint: PageTint?): ImageBitmap? {
        val bitmap = scratchFor(w, h)
        try {
            document.render(page, bitmap, region, tint)
        } catch (_: PdfException) {
            return null // damaged page: the reader shows a blank page instead of crashing
        }
        // 16-bit pages (ADR-0013): half the memory; text and line art are visually identical to 32-bit.
        val source = bitmap.copy(Bitmap.Config.RGB_565, false) ?: bitmap
        val image =
            source.copy(Bitmap.Config.HARDWARE, false) ?: source.copy(source.config ?: Bitmap.Config.ARGB_8888, false)
        if (source !== bitmap) source.recycle()
        return image?.asImageBitmap()
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
