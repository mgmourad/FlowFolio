package com.flowfolio.spike.pdf.engine

import android.graphics.Bitmap
import android.util.SizeF
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext

/** JNI entry points in pdfium_bridge.cpp. */
object PdfiumNative {
    init {
        System.loadLibrary("pdfium")
        System.loadLibrary("flowfolio_pdfium")
    }

    external fun nativeOpen(path: String): Long

    external fun nativeClose(doc: Long)

    external fun nativePageCount(doc: Long): Int

    external fun nativePageSize(doc: Long, index: Int): FloatArray

    external fun nativeRender(doc: Long, index: Int, bitmap: Bitmap): Boolean

    external fun nativeOutline(doc: Long): Array<String>

    external fun nativePageLabel(doc: Long, index: Int): String?

    external fun nativeExtract(doc: Long, index: Int): Array<Any>?
}

/** PDFium (Chrome's PDF engine) through our own JNI bridge, built from pdfium-binaries. */
class PdfiumEngine : PdfEngine {
    override val name = "pdfium"

    @OptIn(ExperimentalCoroutinesApi::class)
    private val thread = Dispatchers.IO.limitedParallelism(1)

    override suspend fun open(file: File): PdfDoc = withContext(thread) {
        val handle = PdfiumNative.nativeOpen(file.absolutePath)
        if (handle == 0L) throw IOException("PDFium could not open ${file.name}")
        PdfiumDoc(handle, thread)
    }
}

class PdfiumDoc internal constructor(private val handle: Long, private val thread: CoroutineDispatcher) : PdfDoc {
    override val pageCount: Int = PdfiumNative.nativePageCount(handle)
    override val features = setOf(
        Feature.OUTLINE,
        Feature.PAGE_LABELS,
        Feature.TEXT,
        Feature.LINKS,
        Feature.GLYPH_GEOMETRY,
        Feature.FONT_INFO,
        Feature.VECTOR_PATHS
    )

    override suspend fun pageSizePt(index: Int): SizeF = withContext(thread) {
        val size = PdfiumNative.nativePageSize(handle, index)
        SizeF(size[0], size[1])
    }

    override suspend fun render(index: Int, widthPx: Int, heightPx: Int): Bitmap = withContext(thread) {
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        check(PdfiumNative.nativeRender(handle, index, bitmap)) { "render failed for page $index" }
        bitmap
    }

    override suspend fun outline(): List<OutlineEntry> = withContext(thread) {
        PdfiumNative.nativeOutline(handle).map { line ->
            val (depth, page, title) = line.split('|', limit = 3)
            OutlineEntry(depth.toInt(), page.toInt(), title)
        }
    }

    override suspend fun pageLabel(index: Int): String? = withContext(thread) {
        PdfiumNative.nativePageLabel(handle, index)
    }

    /** Stage B probe: raw per-glyph data for one page. */
    suspend fun extract(index: Int): RawPage? = withContext(thread) {
        val parts = PdfiumNative.nativeExtract(handle, index) ?: return@withContext null
        @Suppress("UNCHECKED_CAST")
        RawPage(
            geometry = parts[0] as FloatArray,
            ints = parts[1] as IntArray,
            fontNames = (parts[2] as Array<String>).toList(),
            objectStats = parts[3] as IntArray
        )
    }

    override fun close() = PdfiumNative.nativeClose(handle)
}

/** Per glyph: geometry = [left, top, right, bottom, size, weight], ints = [codepoint, fontId, argb]. */
class RawPage(val geometry: FloatArray, val ints: IntArray, val fontNames: List<String>, val objectStats: IntArray) {
    val charCount: Int get() = ints.size / 3
}
