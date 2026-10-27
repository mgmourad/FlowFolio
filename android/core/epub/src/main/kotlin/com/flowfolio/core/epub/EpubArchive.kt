package com.flowfolio.core.epub

import android.os.ParcelFileDescriptor
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.net.URLDecoder
import java.util.zip.ZipException
import java.util.zip.ZipFile

/** The zip itself: a file the app owns (`ZipFile`), or a descriptor for a book read in place ([ChannelZip]). */
private interface Container : Closeable {
    /** The entry's declared size, or null when there is no such entry. */
    fun sizeOf(name: String): Long?

    fun open(name: String): java.io.InputStream?
}

private class FileContainer(private val zip: ZipFile) : Container {
    override fun sizeOf(name: String) = zip.getEntry(name)?.size

    override fun open(name: String) = zip.getEntry(name)?.let(zip::getInputStream)

    override fun close() = zip.close()
}

private class ChannelContainer(private val zip: ChannelZip) : Container {
    override fun sizeOf(name: String) = zip.entry(name)?.size

    override fun open(name: String) = zip.entry(name)?.let(zip::open)

    override fun close() = zip.close()
}

/**
 * The zip container of an EPUB. Reads only paths inside the book, and never more than [MAX_ENTRY_BYTES] of any entry,
 * counted as it inflates (a zip can lie about sizes). Not thread-safe; one reader at a time.
 */
class EpubArchive private constructor(private val zip: Container) : Closeable {

    /** The bytes at a book-internal [path], or null when it is missing, outside the book or too large. */
    fun read(path: String): ByteArray? = normalize(path)
        ?.takeIf { (zip.sizeOf(it) ?: Long.MAX_VALUE) <= MAX_ENTRY_BYTES }
        ?.let { name ->
            try {
                zip.open(name)?.use(::readCapped)
            } catch (_: IOException) {
                null // a damaged entry costs that file only
            }
        }

    /** Reads at most [MAX_ENTRY_BYTES], counted as it inflates: a zip can lie about its sizes. */
    private fun readCapped(input: java.io.InputStream): ByteArray? {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(BUFFER_BYTES)
        var total = 0L
        var n = input.read(buf)
        while (n >= 0 && total <= MAX_ENTRY_BYTES) {
            total += n
            out.write(buf, 0, n)
            n = input.read(buf)
        }
        return out.takeIf { total <= MAX_ENTRY_BYTES }?.toByteArray()
    }

    fun contains(path: String): Boolean = normalize(path)?.let(zip::sizeOf) != null

    override fun close() = zip.close()

    companion object {
        /** Larger entries are refused: no chapter, image or font in a real book comes near this. */
        const val MAX_ENTRY_BYTES = 64L * 1024 * 1024
        private const val BUFFER_BYTES = 64 * 1024

        /** @throws EpubException ([EpubException.Reason.NOT_EPUB]) when [file] is not a zip. */
        fun open(file: File): EpubArchive = EpubArchive(FileContainer(zipOf(file)))

        /**
         * Opens a book from a descriptor (books read in place). Works on a duplicate, so the caller may close [fd]. It
         * is read through the descriptor itself: re-opening `/proc/self/fd/N` by path is refused for shared storage.
         */
        fun open(fd: ParcelFileDescriptor): EpubArchive {
            val stream = ParcelFileDescriptor.AutoCloseInputStream(fd.dup())
            return try {
                EpubArchive(ChannelContainer(ChannelZip(stream.channel, stream)))
            } catch (e: ZipException) {
                stream.close()
                // Not a zip (no directory), or a damaged or ZIP64 one.
                throw EpubException(EpubException.Reason.NOT_EPUB, e)
            } catch (e: IOException) {
                stream.close()
                throw EpubException(EpubException.Reason.DAMAGED, e)
            }
        }

        private fun zipOf(file: File): ZipFile = try {
            ZipFile(file)
        } catch (e: ZipException) {
            // Not a zip, or (on Android 14+) an entry path that climbs out of the archive.
            throw EpubException(EpubException.Reason.NOT_EPUB, e)
        } catch (e: IOException) {
            throw EpubException(EpubException.Reason.DAMAGED, e)
        }

        /** Resolves `.` and `..` in a book-internal path; anything that climbs out of the book is refused (null). */
        fun normalize(path: String): String? {
            val out = ArrayDeque<String>()
            for (part in path.replace('\\', '/').split('/')) {
                when (part) {
                    "", "." -> Unit
                    ".." -> if (out.isEmpty()) return null else out.removeLast()
                    else -> out.addLast(part)
                }
            }
            return out.joinToString("/").takeIf { it.isNotEmpty() }
        }

        /**
         * Resolves [href] (relative, percent-encoded, maybe with a fragment) against the document at [base]. Returns
         * the book-internal path with the fragment kept (`ch01.xhtml#page-3`), or null when it leaves the book.
         */
        fun resolve(base: String, href: String): String? {
            if (href.contains("://") || href.startsWith("data:")) return null
            val fragment = href.substringAfter('#', "")
            val decoded = try {
                URLDecoder.decode(href.substringBefore('#').replace("+", "%2B"), "UTF-8")
            } catch (_: IllegalArgumentException) {
                href.substringBefore('#')
            }
            val dir = base.substringBeforeLast('/', "")
            val target = when {
                decoded.isEmpty() -> base
                decoded.startsWith('/') || dir.isEmpty() -> normalize(decoded)
                else -> normalize("$dir/$decoded")
            }
            return target?.let { if (fragment.isEmpty()) it else "$it#$fragment" }
        }
    }
}

/** Why a file could not be opened as a book. */
class EpubException(val reason: Reason, cause: Throwable? = null) : Exception(reason.name, cause) {
    enum class Reason {
        /** Not a zip, or a zip without an EPUB package. */
        NOT_EPUB,

        /** Content is encrypted (DRM). Font obfuscation alone is fine. */
        DRM,

        /** An EPUB, but its package document cannot be read. */
        DAMAGED
    }
}
