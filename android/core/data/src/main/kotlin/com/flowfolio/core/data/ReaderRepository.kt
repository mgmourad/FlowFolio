package com.flowfolio.core.data

import android.os.ParcelFileDescriptor
import com.flowfolio.core.database.BookDao
import com.flowfolio.core.model.Book
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
}

class OpenedBook(val book: Book, val document: PdfDocument)

class DefaultReaderRepository @Inject constructor(
    private val dao: BookDao,
    private val source: DocumentSource,
    private val engine: PdfEngine,
    @IoDispatcher private val io: CoroutineDispatcher
) : ReaderRepository {
    override suspend fun open(bookId: String, password: String?): OpenedBook {
        val book = withContext(io) { dao.get(bookId)?.toBook() } ?: throw FileNotFoundException("no book $bookId")
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
}
