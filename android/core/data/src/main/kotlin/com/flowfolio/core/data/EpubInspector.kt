package com.flowfolio.core.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.ParcelFileDescriptor
import com.flowfolio.core.epub.EpubArchive
import com.flowfolio.core.epub.EpubException
import com.flowfolio.core.epub.EpubPackage
import com.flowfolio.core.epub.EpubParser
import java.io.File
import javax.inject.Inject

/** What the library keeps from an EPUB's own metadata at import. */
data class EpubInfo(
    val title: String?,
    val author: String?,
    val language: String?,
    val publisher: String?,
    val isbn: String?,
    val description: String?,
    val released: String?,
    /** Printed pages listed in the book's page list (0 when it has none). */
    val printedPages: Int,
    val rightToLeft: Boolean
)

/** The readers of each book format, injected together. */
class BookInspectors @Inject constructor(val pdf: PdfInspector, val epub: EpubInspector)

/** The file is an EPUB whose content is encrypted (DRM). */
class DrmProtectedException : Exception("DRM-protected")

/** Reads what the library needs from an EPUB at import time. Abstracted so import logic is testable on the JVM. */
interface EpubInspector {
    /**
     * Reads the book's metadata and writes a cover thumbnail ([coverWidthPx] wide, JPEG) to [coverOut] when the
     * book has a cover image.
     *
     * @throws NotAPdfException when the file is not a readable EPUB; [DrmProtectedException] when it is encrypted.
     */
    suspend fun inspect(fd: ParcelFileDescriptor, coverOut: File, coverWidthPx: Int): EpubInfo
}

class CoreEpubInspector @Inject constructor() : EpubInspector {
    override suspend fun inspect(fd: ParcelFileDescriptor, coverOut: File, coverWidthPx: Int): EpubInfo {
        val archive = try {
            EpubArchive.open(fd)
        } catch (e: EpubException) {
            throw NotAPdfException(e)
        }
        return archive.use {
            val book = try {
                EpubParser.parse(it)
            } catch (e: EpubException) {
                if (e.reason == EpubException.Reason.DRM) throw DrmProtectedException()
                throw NotAPdfException(e)
            }
            book.coverPath?.let { path -> it.read(path) }?.let { bytes -> writeCover(bytes, coverOut, coverWidthPx) }
            info(book)
        }
    }

    private fun info(book: EpubPackage) = with(book.metadata) {
        EpubInfo(
            title = title,
            author = creators.takeIf { it.isNotEmpty() }?.joinToString(", "),
            language = language,
            publisher = publisher,
            isbn = isbn,
            description = description,
            released = date,
            printedPages = book.pageList.size,
            rightToLeft = book.rightToLeft
        )
    }

    private fun writeCover(bytes: ByteArray, out: File, width: Int) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return // not a decodable image: the library draws its own cover
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= width) sample *= 2
        val decoded =
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: return
        val scaled = if (decoded.width > width) {
            Bitmap.createScaledBitmap(decoded, width, decoded.height * width / decoded.width, true).also {
                decoded.recycle()
            }
        } else {
            decoded
        }
        try {
            out.parentFile?.mkdirs()
            out.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, COVER_QUALITY, it) }
        } finally {
            scaled.recycle()
        }
    }

    private companion object {
        const val COVER_QUALITY = 85
    }
}
