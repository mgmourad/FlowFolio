package com.flowfolio.core.pdf.pdfium

import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.os.Build
import android.os.ParcelFileDescriptor
import com.flowfolio.core.conversion.pdf.Box
import com.flowfolio.core.conversion.pdf.Crop
import com.flowfolio.core.conversion.pdf.EncodedImage
import com.flowfolio.core.conversion.pdf.FontInfo
import com.flowfolio.core.conversion.pdf.Ink
import com.flowfolio.core.conversion.pdf.OutlineItem
import com.flowfolio.core.conversion.pdf.PathInfo
import com.flowfolio.core.conversion.pdf.PdfSource
import com.flowfolio.core.conversion.pdf.RawPage
import com.flowfolio.core.pdf.PdfException
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking

/**
 * The converter's [PdfSource] on the phone (Phase 3): PDFium through our JNI bridge, reading what `JnaPdfSource` reads
 * on the desktop JVM, field for field. Synchronous, since the converter runs in a background job; each call hops to
 * the PDFium thread, as every PDFium call in the app does.
 */
class PdfiumConversionSource private constructor(private val handle: Long, override val pageCount: Int) : PdfSource {
    private val closed = AtomicBoolean(false)

    override fun metadata(key: String): String? =
        call { PdfiumNative.nativeMetaText(handle, key) }?.trim()?.takeIf { it.isNotEmpty() }

    override fun outline(): List<OutlineItem> = call {
        OutlineCodec.decode(PdfiumNative.nativeOutline(handle)).map { OutlineItem(it.depth, it.title, it.pageIndex) }
    }

    override fun pageLabel(index: Int): String? = call { PdfiumNative.nativePageLabel(handle, index) }

    override fun page(index: Int): RawPage {
        val parts = call { PdfiumNative.nativeConversionPage(handle, index) } ?: throw PdfException.PageError(index)
        val size = parts[0] as FloatArray
        val names = parts[NAMES] as Array<*>
        val flags = parts[FLAGS] as IntArray
        val images = parts[IMAGES] as FloatArray
        val paths = parts[PATHS] as FloatArray
        val filled = parts[FILLED] as BooleanArray
        return RawPage(
            index = index,
            width = size[0],
            height = size[1],
            text = parts[1] as String,
            boxes = parts[2] as FloatArray,
            fontSizes = parts[3] as FloatArray,
            fontWeights = parts[4] as IntArray,
            fontIds = parts[5] as IntArray,
            colors = parts[6] as IntArray,
            generated = parts[7] as BooleanArray,
            fonts = names.indices.map { FontInfo(names[it] as String, flags[it]) },
            images = boxes(images),
            paths = boxes(paths).mapIndexed { i, b -> PathInfo(b, filled[i]) }
        )
    }

    override fun renderImages(index: Int, crops: List<Crop>, scale: Float): List<EncodedImage> {
        if (crops.isEmpty()) return emptyList()
        val size = call { PdfiumNative.nativePageSize(handle, index) } ?: throw PdfException.PageError(index)
        val pageW = (size[0] * scale).toInt().coerceIn(1, MAX_RENDER_PX)
        val pageH = (size[1] * scale).toInt().coerceIn(1, MAX_RENDER_PX)
        val page = Bitmap.createBitmap(pageW, pageH, Bitmap.Config.ARGB_8888)
        try {
            val ok = call { PdfiumNative.nativeRender(handle, index, page, pageW, pageH, 0, 0, 0, 0, 0) }
            if (!ok) throw PdfException.PageError(index)
            return crops.map { crop ->
                val r = crop.box
                val x0 = (r.left * scale).toInt().coerceIn(0, pageW - 1)
                val y0 = (r.top * scale).toInt().coerceIn(0, pageH - 1)
                val x1 = (r.right * scale).toInt().coerceIn(x0 + 1, pageW)
                val y1 = (r.bottom * scale).toInt().coerceIn(y0 + 1, pageH)
                val cut = Bitmap.createBitmap(page, x0, y0, x1 - x0, y1 - y0)
                try {
                    val (format, quality) = webp(crop.photo)
                    val bytes = ByteArrayOutputStream().also { cut.compress(format, quality, it) }.toByteArray()
                    EncodedImage(bytes, "webp", coloured = !crop.photo && coloured(cut))
                } finally {
                    if (cut !== page) cut.recycle()
                }
            }
        } finally {
            page.recycle()
        }
    }

    /** The whole page as ARGB pixels at [scale], for OCR of a scanned page (ADR-0054). */
    override fun renderPixels(index: Int, scale: Float): com.flowfolio.core.conversion.ocr.OcrImage? {
        val size = call { PdfiumNative.nativePageSize(handle, index) } ?: return null
        val w = (size[0] * scale).toInt().coerceIn(1, MAX_RENDER_PX)
        val h = (size[1] * scale).toInt().coerceIn(1, MAX_RENDER_PX)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        try {
            val drawn = call { PdfiumNative.nativeRender(handle, index, bitmap, w, h, 0, 0, 0, 0, 0) } == true
            return if (drawn) {
                val pixels = IntArray(w * h)
                bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
                com.flowfolio.core.conversion.ocr.OcrImage(w, h, pixels, w / size[0])
            } else {
                null
            }
        } finally {
            bitmap.recycle()
        }
    }

    /** [bitmap] has colour of its own ([Ink.coloured]). */
    private fun coloured(bitmap: Bitmap): Boolean {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return Ink.coloured(pixels, bitmap.width, bitmap.height)
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        if (PdfiumThread.isCurrent()) {
            PdfiumNative.nativeClose(handle)
        } else {
            runBlocking(PdfiumThread.dispatcher) { PdfiumNative.nativeClose(handle) }
        }
    }

    private fun <T> call(block: () -> T): T {
        if (closed.get()) throw PdfException.Closed()
        return if (PdfiumThread.isCurrent()) block() else runBlocking(PdfiumThread.dispatcher) { block() }
    }

    companion object {
        /** Opens the PDF behind [fd] for conversion. The caller may close its own [fd] afterwards. */
        fun open(fd: ParcelFileDescriptor, password: String? = null): PdfiumConversionSource =
            runBlocking(PdfiumThread.dispatcher) {
                val err = IntArray(1)
                val handle = PdfiumNative.nativeOpen(fd.fd, password, err)
                if (handle == 0L) throw PdfException.fromCode(err[0])
                PdfiumConversionSource(handle, PdfiumNative.nativePageCount(handle))
            }

        /**
         * Lossless WebP for line art (equations, tables), so thin strokes stay crisp; lossy for photos. Android 10 has
         * only the one WebP format, which is lossless at quality 100.
         */
        private fun webp(photo: Boolean): Pair<CompressFormat, Int> = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                if (photo) {
                    CompressFormat.WEBP_LOSSY to PHOTO_QUALITY
                } else {
                    CompressFormat.WEBP_LOSSLESS to
                        LOSSLESS_EFFORT
                }

            else ->
                @Suppress("DEPRECATION")
                CompressFormat.WEBP to if (photo) PHOTO_QUALITY else LOSSLESS_QUALITY
        }

        private fun boxes(values: FloatArray): List<Box> = (0 until values.size / Box.FIELDS).map {
            Box.of(
                values,
                it * Box.FIELDS
            )
        }

        private const val MAX_RENDER_PX = 8192

        // nativeConversionPage's result (pdfium_jni.cpp): 0-7 the page size, text, boxes, font sizes, weights,
        // font ids, colours and generated flags; then these.
        private const val NAMES = 8
        private const val FLAGS = 9
        private const val IMAGES = 10
        private const val PATHS = 11
        private const val FILLED = 12
        private const val PHOTO_QUALITY = 85
        private const val LOSSLESS_EFFORT = 75
        private const val LOSSLESS_QUALITY = 100
    }
}
