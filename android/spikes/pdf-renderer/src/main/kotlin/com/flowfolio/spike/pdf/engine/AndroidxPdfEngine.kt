package com.flowfolio.spike.pdf.engine

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Size
import android.util.SizeF
import androidx.pdf.PdfDocument
import androidx.pdf.SandboxedPdfLoader
import java.io.File
import kotlinx.coroutines.Dispatchers

/**
 * Jetpack androidx.pdf: wraps the framework renderer and runs it in a separate sandboxed process.
 * Memory used for rendering is therefore NOT in the app process's PSS.
 */
class AndroidxPdfEngine(private val context: Context) : PdfEngine {
    override val name = "androidx"

    override suspend fun open(file: File): PdfDoc {
        val document = SandboxedPdfLoader(context, Dispatchers.IO).openDocument(Uri.fromFile(file), null)
        return AndroidxDoc(document)
    }
}

private class AndroidxDoc(private val document: PdfDocument) : PdfDoc {
    override val pageCount: Int = document.pageCount
    override val features = setOf(Feature.TEXT, Feature.LINKS, Feature.SANDBOXED)

    override suspend fun pageSizePt(index: Int): SizeF {
        val info = document.getPageInfo(index)
        return SizeF(info.width.toFloat(), info.height.toFloat())
    }

    override suspend fun render(index: Int, widthPx: Int, heightPx: Int): Bitmap {
        val source = document.getPageBitmapSource(index)
        return try {
            source.getBitmap(Size(widthPx, heightPx), null)
        } finally {
            source.close()
        }
    }

    override suspend fun outline(): List<OutlineEntry>? = null

    override suspend fun pageLabel(index: Int): String? = null

    override fun close() = document.close()
}
