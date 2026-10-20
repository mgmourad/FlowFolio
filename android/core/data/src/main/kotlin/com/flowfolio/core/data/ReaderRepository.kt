package com.flowfolio.core.data

import android.os.ParcelFileDescriptor
import com.flowfolio.core.database.BookDao
import com.flowfolio.core.database.BookReadingDao
import com.flowfolio.core.epub.EpubArchive
import com.flowfolio.core.epub.EpubException
import com.flowfolio.core.epub.EpubPackage
import com.flowfolio.core.epub.EpubParser
import com.flowfolio.core.model.Book
import com.flowfolio.core.model.BookFormat
import com.flowfolio.core.model.BookStorage
import com.flowfolio.core.pdf.PdfDocument
import com.flowfolio.core.pdf.PdfEngine
import com.flowfolio.core.pdf.PdfException
import java.io.File
import java.io.FileNotFoundException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Opens books for reading. */
interface ReaderRepository {
    /**
     * Opens the book's PDF. The caller owns the returned document and must close it.
     *
     * @throws PdfException.PasswordRequired when a password is needed or [password] is wrong.
     * @throws PdfException for damaged files; [FileNotFoundException] when the book or its file is gone.
     */
    suspend fun open(bookId: String, password: String? = null): OpenedBook

    /** Remembers [page] (0-based) as where the reader left off in [bookId]. */
    suspend fun saveProgress(bookId: String, page: Int)

    /** Remembers the book's printed page numbers ([com.flowfolio.core.model.PageLabels] runs). */
    suspend fun savePageLabels(bookId: String, labels: String)

    /**
     * Opens an EPUB for reading. The caller owns the archive and must close it.
     *
     * @throws FileNotFoundException when the book or its file is gone; [EpubException] when it cannot be read.
     */
    suspend fun openEpub(bookId: String): OpenedEpub = throw FileNotFoundException("no EPUB support")

    /** Remembers where an EPUB was left: its [locator], and the [page] and [pageCount] at the size it was read. */
    suspend fun saveEpubProgress(bookId: String, locator: String, page: Int, pageCount: Int) = Unit

    /** An EPUB's top-level contents (chapter titles in reading order); empty for PDFs or when it cannot be read. */
    suspend fun epubContents(bookId: String): List<String> = emptyList()
}

class OpenedBook(val book: Book, val document: PdfDocument)

/** An open EPUB: its container (owned by the caller) and package. */
class OpenedEpub(val book: Book, val archive: EpubArchive, val pkg: EpubPackage)

/** The book is in a format the reader cannot show yet (EPUB until Phase 2 milestone 2). */
class NotYetReadableException(val format: BookFormat) : Exception("cannot read $format yet")

class DefaultReaderRepository @Inject constructor(
    private val dao: BookDao,
    private val reading: BookReadingDao,
    private val source: DocumentSource,
    private val engine: PdfEngine,
    @IoDispatcher private val io: CoroutineDispatcher
) : ReaderRepository {
    override suspend fun open(bookId: String, password: String?): OpenedBook {
        val book = withContext(io) { dao.get(bookId)?.toBook() } ?: throw FileNotFoundException("no book $bookId")
        if (book.format != BookFormat.PDF) throw NotYetReadableException(book.format)
        val fd = withContext(io) {
            when (val s = book.storage) {
                is BookStorage.Copied -> File(s.path).takeIf {
                    it.canRead()
                }?.let { ParcelFileDescriptor.open(it, ParcelFileDescriptor.MODE_READ_ONLY) }

                is BookStorage.Linked -> source.open(s.uri)
            }
        } ?: throw FileNotFoundException("file for $bookId is missing")
        val document = fd.use { engine.open(it, password) } // the engine keeps its own duplicate of the descriptor
        return OpenedBook(book, document)
    }

    override suspend fun saveProgress(bookId: String, page: Int) = withContext(io) { dao.updateLastPage(bookId, page) }

    override suspend fun openEpub(bookId: String): OpenedEpub = withContext(io) {
        val book = dao.get(bookId)?.toBook() ?: throw FileNotFoundException("no book $bookId")
        val fd = descriptorOf(book) ?: throw FileNotFoundException("file for $bookId is missing")
        val archive = fd.use { EpubArchive.open(it) }
        try {
            OpenedEpub(book, archive, EpubParser.parse(archive))
        } catch (e: EpubException) {
            archive.close()
            throw e
        }
    }

    override suspend fun saveEpubProgress(bookId: String, locator: String, page: Int, pageCount: Int) =
        withContext(io) { reading.updateEpubProgress(bookId, locator, page, pageCount) }

    private fun descriptorOf(book: Book): ParcelFileDescriptor? = when (val s = book.storage) {
        is BookStorage.Copied -> File(s.path).takeIf { it.canRead() }
            ?.let { ParcelFileDescriptor.open(it, ParcelFileDescriptor.MODE_READ_ONLY) }

        is BookStorage.Linked -> source.open(s.uri)
    }

    override suspend fun epubContents(bookId: String): List<String> = withContext(io) {
        val book = dao.get(bookId)?.toBook()?.takeIf { it.format == BookFormat.EPUB } ?: return@withContext emptyList()
        val fd = descriptorOf(book) ?: return@withContext emptyList()
        try {
            fd.use { EpubArchive.open(it).use { archive -> EpubParser.parse(archive).toc.map { p -> p.label } } }
        } catch (_: EpubException) {
            emptyList()
        }
    }

    override suspend fun savePageLabels(bookId: String, labels: String) =
        withContext(io) { reading.setPageLabels(bookId, labels) }
}
