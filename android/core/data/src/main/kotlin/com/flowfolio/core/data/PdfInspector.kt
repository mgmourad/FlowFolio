package com.flowfolio.core.data

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import com.flowfolio.core.pdf.PdfEngine
import com.flowfolio.core.pdf.PdfException
import java.io.File
import javax.inject.Inject

data class PdfInfo(val pageCount: Int, val title: String?, val passwordProtected: Boolean)

class NotAPdfException(cause: Throwable? = null) : Exception("not a readable PDF", cause)

/** Reads what the library needs from a PDF at import time. Abstracted so import logic is testable on the JVM. */
interface PdfInspector {
    /**
     * Reads page count and title, and writes a cover thumbnail (page 1, [coverWidthPx] wide) to [coverOut] when
     * possible. JPEG, because lossy WebP encoding needs API 30 and minSdk is 29. Password-protected files are
     * accepted without a cover; the password is asked when opening.
     *
     * @throws NotAPdfException when the file is not a PDF or is too damaged to open.
     */
    suspend fun inspect(fd: ParcelFileDescriptor, coverOut: File, coverWidthPx: Int): PdfInfo
}

class PdfiumInspector @Inject constructor(private val engine: PdfEngine) : PdfInspector {
    override suspend fun inspect(fd: ParcelFileDescriptor, coverOut: File, coverWidthPx: Int): PdfInfo {
        val doc = try {
            engine.open(fd)
        } catch (_: PdfException.PasswordRequired) {
            return PdfInfo(pageCount = 0, title = null, passwordProtected = true)
        } catch (e: PdfException) {
            throw NotAPdfException(e)
        }
        return doc.use {
            if (it.pageCount > 0) writeCover(it, coverOut, coverWidthPx)
            PdfInfo(it.pageCount, it.title(), passwordProtected = false)
        }
    }

    private suspend fun writeCover(doc: com.flowfolio.core.pdf.PdfDocument, out: File, width: Int) {
        try {
            val px = doc.pageSize(0).fitInside(width, width * MAX_COVER_ASPECT)
            val bitmap = Bitmap.createBitmap(px.width, px.height, Bitmap.Config.ARGB_8888)
            try {
                doc.render(0, bitmap)
                out.parentFile?.mkdirs()
                out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, COVER_QUALITY, it) }
            } finally {
                bitmap.recycle()
            }
        } catch (_: PdfException) {
            out.delete() // a damaged first page only costs the thumbnail
        }
    }

    private companion object {
        const val MAX_COVER_ASPECT = 2
        const val COVER_QUALITY = 85
    }
}
