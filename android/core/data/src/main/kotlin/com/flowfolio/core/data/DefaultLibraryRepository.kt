package com.flowfolio.core.data

import android.os.ParcelFileDescriptor
import com.flowfolio.core.database.BookDao
import com.flowfolio.core.database.BookEntity
import com.flowfolio.core.model.Availability
import com.flowfolio.core.model.Book
import com.flowfolio.core.model.BookDetails
import com.flowfolio.core.model.BookStorage
import com.flowfolio.core.model.BookType
import com.flowfolio.core.model.LibrarySort
import com.flowfolio.core.model.StorageMode
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class DefaultLibraryRepository @Inject constructor(
    private val dao: BookDao,
    private val source: DocumentSource,
    private val inspector: PdfInspector,
    private val files: BookFiles,
    private val clock: Clock,
    @IoDispatcher private val io: CoroutineDispatcher
) : LibraryRepository {

    override fun observeBooks(sort: LibrarySort): Flow<List<Book>> {
        val source = if (sort == LibrarySort.TITLE) dao.observeByTitle() else dao.observeByRecent()
        return source.map { list -> list.map { it.toBook() } }
    }

    override suspend fun import(uri: String, mode: StorageMode, fromShare: Boolean): ImportResult = withContext(io) {
        val effectiveMode = if (fromShare) StorageMode.COPY else mode
        if (effectiveMode == StorageMode.LINK && !source.persistReadAccess(uri)) {
            return@withContext ImportResult.LinkNotPossible
        }
        val outcome = when (effectiveMode) {
            StorageMode.COPY -> importCopy(uri)
            StorageMode.LINK -> importLink(uri)
        }
        if (outcome !is ImportResult.Imported && effectiveMode == StorageMode.LINK) source.releaseReadAccess(uri)
        outcome
    }

    // Each early return is a distinct, user-visible import outcome; nesting them would hide that.
    @Suppress("ReturnCount")
    private suspend fun importCopy(uri: String): ImportResult {
        val fd = source.open(uri) ?: return ImportResult.Unreadable
        val size = fd.statSize
        if (size > 0 && files.usableBytes() < size + SPACE_RESERVE_BYTES) {
            fd.close()
            return ImportResult.NotEnoughSpace(size + SPACE_RESERVE_BYTES)
        }
        val temp = files.newTemp()
        val (id, bytes) = try {
            temp.outputStream().use { out -> hash(fd, out) }
        } catch (_: IOException) {
            temp.delete()
            val full = files.usableBytes() < size + SPACE_RESERVE_BYTES
            return if (full) ImportResult.NotEnoughSpace(size + SPACE_RESERVE_BYTES) else ImportResult.Unreadable
        }
        dao.get(id)?.let {
            temp.delete()
            return ImportResult.Duplicate(it.toBook())
        }
        val target = files.book(id)
        if (!temp.renameTo(target)) {
            temp.delete()
            return ImportResult.Unreadable
        }
        val info = inspectOrNull(ParcelFileDescriptor.open(target, ParcelFileDescriptor.MODE_READ_ONLY), id)
        if (info == null) {
            target.delete()
            return ImportResult.NotAPdf
        }
        return save(Stored(id, bytes, StorageMode.COPY, target.absolutePath), info, uri)
    }

    @Suppress("ReturnCount")
    private suspend fun importLink(uri: String): ImportResult {
        val (id, bytes) = hashOrNull(uri) ?: return ImportResult.Unreadable
        dao.get(id)?.let { return ImportResult.Duplicate(it.toBook()) }
        val fd = source.open(uri) ?: return ImportResult.Unreadable
        val info = inspectOrNull(fd, id) ?: return ImportResult.NotAPdf
        return save(Stored(id, bytes, StorageMode.LINK, uri), info, uri)
    }

    private fun hashOrNull(uri: String): Pair<String, Long>? =
        source.open(uri)?.let { fd -> runCatching { hash(fd, null) }.getOrNull() }

    private suspend fun inspectOrNull(fd: ParcelFileDescriptor, id: String): PdfInfo? = fd.use {
        try {
            inspector.inspect(it, files.cover(id), COVER_WIDTH_PX)
        } catch (_: NotAPdfException) {
            files.cover(id).delete()
            null
        }
    }

    /** Where an imported file ended up. */
    private data class Stored(val id: String, val bytes: Long, val mode: StorageMode, val location: String)

    private suspend fun save(stored: Stored, info: PdfInfo, uri: String): ImportResult {
        val cover = files.cover(stored.id).takeIf { it.exists() }?.absolutePath
        val title = info.title ?: source.displayName(uri)?.removeSuffix(".pdf")?.removeSuffix(".PDF") ?: DEFAULT_TITLE
        val entity = BookEntity(
            id = stored.id,
            title = title,
            pageCount = info.pageCount,
            storageMode = stored.mode.name,
            location = stored.location,
            sizeBytes = stored.bytes,
            addedAt = clock.now(),
            lastOpenedAt = null,
            coverPath = cover,
            passwordProtected = info.passwordProtected,
            author = info.author
        )
        dao.insert(entity)
        return ImportResult.Imported(entity.toBook())
    }

    override suspend fun availability(book: Book): Availability = withContext(io) {
        val ok = when (val s = book.storage) {
            is BookStorage.Copied -> File(s.path).canRead()
            is BookStorage.Linked -> source.open(s.uri)?.use { true } ?: false
        }
        if (ok) Availability.AVAILABLE else Availability.MISSING
    }

    override suspend fun sizeOf(uri: String): Long? = withContext(io) { source.size(uri) }

    override suspend fun relink(bookId: String, uri: String): RelinkResult = withContext(io) {
        val book = dao.get(bookId) ?: return@withContext RelinkResult.Unreadable
        val id = hashOrNull(uri)?.first ?: return@withContext RelinkResult.Unreadable
        if (id != bookId) return@withContext RelinkResult.DifferentFile
        if (!source.persistReadAccess(uri)) return@withContext RelinkResult.LinkNotPossible
        if (book.storageMode == StorageMode.LINK.name && book.location != uri) source.releaseReadAccess(book.location)
        dao.updateLocation(bookId, StorageMode.LINK.name, uri)
        RelinkResult.Relinked
    }

    override suspend fun delete(bookId: String) = withContext(io) {
        val book = dao.get(bookId) ?: return@withContext
        dao.delete(bookId)
        when (book.storageMode) {
            StorageMode.COPY.name -> File(book.location).delete()
            StorageMode.LINK.name -> source.releaseReadAccess(book.location)
        }
        files.cover(bookId).delete()
        Unit
    }

    override suspend fun rename(bookId: String, title: String) = withContext(io) {
        require(title.isNotBlank()) { "title is blank" }
        val book = dao.get(bookId)?.toBook() ?: return@withContext
        updateDetails(bookId, title, book.author, book.details)
    }

    override suspend fun updateDetails(bookId: String, title: String, author: String?, details: BookDetails) =
        withContext(io) {
            require(title.isNotBlank()) { "title is blank" }
            dao.updateDetails(
                id = bookId,
                title = title.trim(),
                author = author.clean(),
                subtitle = details.subtitle.clean(),
                edition = details.edition.clean(),
                publisher = details.publisher.clean(),
                isbn = details.isbn.clean(),
                description = details.description.clean(),
                language = details.language.clean(),
                released = details.released.clean(),
                authorRole = details.authorRole.clean(),
                authorBio = details.authorBio.clean(),
                bookType = details.type?.name
            )
        }

    override fun observeBook(bookId: String): Flow<Book?> = dao.observe(bookId).map { it?.toBook() }

    override suspend fun markOpened(bookId: String) = withContext(io) { dao.markOpened(bookId, clock.now()) }

    private companion object {
        const val COVER_WIDTH_PX = 360
        const val SPACE_RESERVE_BYTES = 50L * 1024 * 1024
        const val DEFAULT_TITLE = "Untitled"
    }
}

internal fun BookEntity.toBook() = Book(
    id = id,
    title = title,
    pageCount = pageCount,
    storage = if (storageMode == StorageMode.LINK.name) BookStorage.Linked(location) else BookStorage.Copied(location),
    sizeBytes = sizeBytes,
    addedAtMillis = addedAt,
    lastOpenedAtMillis = lastOpenedAt,
    coverPath = coverPath,
    passwordProtected = passwordProtected,
    lastPage = lastPage,
    author = author,
    details = BookDetails(
        subtitle = subtitle,
        edition = edition,
        publisher = publisher,
        isbn = isbn,
        description = description,
        language = language,
        released = released,
        authorRole = authorRole,
        authorBio = authorBio,
        type = BookType.entries.firstOrNull { it.name == bookType }
    ),
    rightToLeft = rightToLeft
)

/** Trimmed, and null when blank, so an emptied field reads as unknown. */
private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

/** Streams the file once: SHA-256 for the book id, optionally copying to [sink]. Closes [fd]. */
private fun hash(fd: ParcelFileDescriptor, sink: OutputStream?): Pair<String, Long> {
    val digest = MessageDigest.getInstance("SHA-256")
    var total = 0L
    DigestInputStream(ParcelFileDescriptor.AutoCloseInputStream(fd), digest).use { input ->
        val buf = ByteArray(HASH_BUFFER_BYTES)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            sink?.write(buf, 0, n)
            total += n
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) } to total
}

private const val HASH_BUFFER_BYTES = 1 shl 16
