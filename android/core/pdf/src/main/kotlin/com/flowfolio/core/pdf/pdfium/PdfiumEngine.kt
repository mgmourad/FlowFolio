package com.flowfolio.core.pdf.pdfium

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import com.flowfolio.core.model.OutlineEntry
import com.flowfolio.core.model.PageSize
import com.flowfolio.core.pdf.PageTint
import com.flowfolio.core.pdf.PdfDocument
import com.flowfolio.core.pdf.PdfEngine
import com.flowfolio.core.pdf.PdfException
import com.flowfolio.core.pdf.PdfLink
import com.flowfolio.core.pdf.RenderRegion
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/** [PdfEngine] backed by PDFium through our JNI bridge (ADR-0010). */
class PdfiumEngine : PdfEngine {
    override suspend fun open(fd: ParcelFileDescriptor, password: String?): PdfDocument =
        withContext(PdfiumThread.dispatcher) {
            val err = IntArray(1)
            val handle = PdfiumNative.nativeOpen(fd.fd, password, err)
            if (handle == 0L) throw PdfException.fromCode(err[0])
            PdfiumDocument(handle, PdfiumNative.nativePageCount(handle))
        }
}

internal class PdfiumDocument(private val handle: Long, override val pageCount: Int) : PdfDocument {
    private val closed = AtomicBoolean(false)

    /** Renders since the last reload; only touched on the PDFium thread. */
    private var rendersSinceReload = 0

    override suspend fun pageSize(index: Int): PageSize = call {
        checkIndex(index)
        val size = PdfiumNative.nativePageSize(handle, index) ?: throw PdfException.PageError(index)
        PageSize(size[0], size[1])
    }

    override suspend fun render(index: Int, target: Bitmap, region: RenderRegion, tint: PageTint?) {
        require(target.config == Bitmap.Config.ARGB_8888 && target.isMutable && !target.isRecycled) {
            "target must be a mutable, live ARGB_8888 bitmap"
        }
        call {
            checkIndex(index)
            val ok = with(region) {
                val mode = when {
                    tint == null -> 0
                    tint.invert -> 2
                    else -> 1
                }
                PdfiumNative.nativeRender(
                    handle,
                    index,
                    target,
                    pageWidthPx,
                    pageHeightPx,
                    offsetX,
                    offsetY,
                    mode,
                    tint?.paper ?: 0,
                    tint?.ink ?: 0
                )
            }
            if (!ok) throw PdfException.PageError(index)
            // Bound PDFium's per-document caches (+12 MB per 100 pages otherwise; see nativeReload). If a reload
            // fails, keep going on the current document and try again later.
            if (++rendersSinceReload >= RELOAD_EVERY_RENDERS &&
                PdfiumNative.nativeReload(handle)
            ) {
                rendersSinceReload = 0
            }
        }
    }

    override suspend fun outline(): List<OutlineEntry> = call {
        OutlineCodec.decode(PdfiumNative.nativeOutline(handle))
    }

    override suspend fun pageLabel(index: Int): String? = call {
        checkIndex(index)
        PdfiumNative.nativePageLabel(handle, index)
    }

    override suspend fun author(): String? = call {
        PdfiumNative.nativeMetaText(handle, "Author")?.trim()?.takeIf { it.isNotEmpty() }
    }

    override suspend fun title(): String? = call {
        PdfiumNative.nativeMetaText(handle, "Title")?.trim()?.takeIf { it.isNotEmpty() }
    }

    override suspend fun links(index: Int): List<PdfLink> = call {
        checkIndex(index)
        LinkCodec.decode(PdfiumNative.nativeLinks(handle, index))
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        // Must run on the PDFium thread; close() may be called from anywhere, including that thread.
        if (PdfiumThread.isCurrent()) {
            PdfiumNative.nativeClose(handle)
        } else {
            runBlocking(PdfiumThread.dispatcher) { PdfiumNative.nativeClose(handle) }
        }
    }

    private fun checkIndex(index: Int) {
        if (index !in 0 until pageCount) throw IndexOutOfBoundsException("page $index of $pageCount")
    }

    private suspend fun <T> call(block: () -> T): T = withContext(PdfiumThread.dispatcher) {
        if (closed.get()) throw PdfException.Closed()
        block()
    }

    private companion object {
        const val RELOAD_EVERY_RENDERS = 100
    }
}
