package com.flowfolio.spike.pdf.engine

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.ext.SdkExtensions
import android.util.SizeF
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext

/** android.graphics.pdf.PdfRenderer: built into Android, no dependency. One page open at a time. */
class FrameworkEngine : PdfEngine {
    override val name = "framework"

    @OptIn(ExperimentalCoroutinesApi::class)
    private val thread = Dispatchers.IO.limitedParallelism(1)

    override suspend fun open(file: File): PdfDoc = withContext(thread) {
        val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        FrameworkDoc(PdfRenderer(fd), fd, thread)
    }
}

private class FrameworkDoc(
    private val renderer: PdfRenderer,
    private val fd: ParcelFileDescriptor,
    private val thread: kotlinx.coroutines.CoroutineDispatcher
) : PdfDoc {
    override val pageCount: Int = renderer.pageCount

    // Text and link APIs arrived in API 35, and on API 31-34 with SDK extension level 13.
    override val features: Set<Feature> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM ||
            (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= 13
                )
        ) {
            setOf(Feature.TEXT, Feature.LINKS)
        } else {
            emptySet()
        }

    override suspend fun pageSizePt(index: Int): SizeF = withContext(thread) {
        renderer.openPage(index).use { SizeF(it.width.toFloat(), it.height.toFloat()) }
    }

    override suspend fun render(index: Int, widthPx: Int, heightPx: Int): Bitmap = withContext(thread) {
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE) // PdfRenderer draws on a transparent background
        renderer.openPage(index).use { it.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
        bitmap
    }

    override suspend fun outline(): List<OutlineEntry>? = null

    override suspend fun pageLabel(index: Int): String? = null

    override fun close() {
        renderer.close()
        fd.close()
    }
}
