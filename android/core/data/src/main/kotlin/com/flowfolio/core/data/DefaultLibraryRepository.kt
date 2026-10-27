package com.flowfolio.core.data

import android.os.ParcelFileDescriptor
import com.flowfolio.core.database.BookDao
import com.flowfolio.core.database.BookEntity
import com.flowfolio.core.model.Availability
import com.flowfolio.core.model.Book
import com.flowfolio.core.model.BookDetails
import com.flowfolio.core.model.BookFormat
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

@Suppress("TooManyFunctions") // the repository's calls and the import steps behind them
class DefaultLibraryRepository @Inject constructor(
    private val dao: BookDao,
    private val source: DocumentSource,
    private val inspectors: BookInspectors,
    private val files: BookFiles,
    private val clock: Clock,
    @IoDispatcher private val io: CoroutineDispatcher
) : LibraryRepository {

    override fun observeBooks(sort: LibrarySort): Flow<List<Book>> {
        val source = if (sort == LibrarySort.TITLE) dao.observeByTitle() else dao.observeByRecent()
        return source.map { list -> list.map { it.toBook() } }
    }

    override suspend fun import(
        uri: String,
        mode: StorageMode,
        fromShare: Boolean,
        folder: String?,
        accessHeld: Boolean
    ): ImportResult = withContext(io) {
        val effectiveMode = if (fromShare) StorageMode.COPY else mode
        val ownGrant = effectiveMode == StorageMode.LINK && !accessHeld
        if (ownGrant && !source.persistReadAccess(uri)) return@withContext ImportResult.LinkNotPossible
        val outcome = when (effectiveMode) {
            StorageMode.COPY -> importCopy(uri, folder)
            StorageMode.LINK -> importLink(uri, folder)
        }
        if (outcome !is ImportResult.Imported && ownGrant) source.releaseReadAccess(uri)
        outcome
    }

    override suspend fun listFolder(treeUri: String): List<FolderFile>? = withContext(io) { source.listFolder(treeUri) }

    override suspend fun persistFolderAccess(treeUri: String): Boolean =
        withContext(io) { source.persistFolderAccess(treeUri) }

    /** A book already in the library: it joins the imported folder when it was imported on its own before. */
    private suspend fun duplicate(existing: BookEntity, folder: String?): ImportResult {
        if (folder != null && existing.folder == null) dao.setFolder(existing.id, folder)
        return ImportResult.Duplicate(existing.toBook())
    }

    // Each early return is a distinct, user-visible import outcome; nesting them would hide that.
    @Suppress("ReturnCount")
    private suspend fun importCopy(uri: String, folder: String?): ImportResult {
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
            return duplicate(it, folder)
        }
        val format = formatOf(ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY))
        val target = files.book(id, format)
        if (!temp.renameTo(target)) {
            temp.delete()
            return ImportResult.Unreadable
        }
        val info = inspect(ParcelFileDescriptor.open(target, ParcelFileDescriptor.MODE_READ_ONLY), id, format)
        if (info !is Inspected.Ok) {
            target.delete()
            return info.failure()
        }
        return save(Stored(id, bytes, StorageMode.COPY, target.absolutePath, folder), info, uri)
    }

    @Suppress("ReturnCount")
    private suspend fun importLink(uri: String, folder: String?): ImportResult {
        val (id, bytes) = hashOrNull(uri) ?: return ImportResult.Unreadable
        dao.get(id)?.let { return duplicate(it, folder) }
        val format = source.open(uri)?.let(::formatOf) ?: return ImportResult.Unreadable
        val fd = source.open(uri) ?: return ImportResult.Unreadable
        val info = inspect(fd, id, format)
        if (info !is Inspected.Ok) return info.failure()
        return save(Stored(id, bytes, StorageMode.LINK, uri, folder), info, uri)
    }

    private fun hashOrNull(uri: String): Pair<String, Long>? =
        source.open(uri)?.let { fd -> runCatching { hash(fd, null) }.getOrNull() }

    /** The outcome of reading an imported file: what the library keeps, or why it was refused. */
    private sealed interface Inspected {
        class Ok(val format: BookFormat, val pdf: PdfInfo?, val epub: EpubInfo?) : Inspected

        data object NotABook : Inspected

        data object Drm : Inspected

        fun failure(): ImportResult = if (this == Drm) ImportResult.DrmProtected else ImportResult.NotABook
    }

    private suspend fun inspect(fd: ParcelFileDescriptor, id: String, format: BookFormat): Inspected = fd.use {
        try {
            when (format) {
                BookFormat.PDF -> Inspected.Ok(
                    format,
                    inspectors.pdf.inspect(it, files.cover(id), COVER_WIDTH_PX),
                    null
                )

                BookFormat.EPUB -> Inspected.Ok(
                    format,
                    null,
                    inspectors.epub.inspect(it, files.cover(id), COVER_WIDTH_PX)
                )
            }
        } catch (_: NotAPdfException) {
            files.cover(id).delete()
            Inspected.NotABook
        } catch (_: DrmProtectedException) {
            files.cover(id).delete()
            Inspected.Drm
        }
    }

    /** Where an imported file ended up. */
    private data class Stored(
        val id: String,
        val bytes: Long,
        val mode: StorageMode,
        val location: String,
        val folder: String? = null
    )

    private suspend fun save(stored: Stored, info: Inspected.Ok, uri: String): ImportResult {
        val cover = files.cover(stored.id).takeIf { it.exists() }?.absolutePath
        val epub = info.epub
        val fileName = source.displayName(uri)?.replace(EXTENSION, "")
        val title = (info.pdf?.title ?: epub?.title)?.takeIf { it.isNotBlank() } ?: fileName ?: DEFAULT_TITLE
        val entity = BookEntity(
            id = stored.id,
            title = title,
            // EPUB pages depend on the text size: until the reader paginates, the printed pages it lists, if any.
            pageCount = info.pdf?.pageCount ?: epub?.printedPages ?: 0,
            storageMode = stored.mode.name,
            location = stored.location,
            sizeBytes = stored.bytes,
            addedAt = clock.now(),
            lastOpenedAt = null,
            coverPath = cover,
            passwordProtected = info.pdf?.passwordProtected ?: false,
            author = info.pdf?.author ?: epub?.author,
            publisher = epub?.publisher,
            isbn = epub?.isbn,
            description = epub?.description,
            language = epub?.language,
            released = epub?.released,
            rightToLeft = epub?.rightToLeft ?: false,
            format = info.format.name,
            folder = stored.folder
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
                bookType = details.type?.name,
                subjects = details.subjects.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
                    .joinToString(SUBJECT_SEPARATOR).ifEmpty { null },
                printPages = details.printPages?.takeIf { it > 0 },
                series = details.series.clean(),
                rating = details.rating?.takeIf { it in 0f..MAX_RATING },
                ratingsCount = details.ratingsCount?.takeIf { it > 0 }
            )
        }

    override fun observeBook(bookId: String): Flow<Book?> = dao.observe(bookId).map { it?.toBook() }

    override suspend fun markOpened(bookId: String) = withContext(io) { dao.markOpened(bookId, clock.now()) }

    private companion object {
        val EXTENSION = Regex("\\.(pdf|epub)$", RegexOption.IGNORE_CASE)
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
        type = BookType.entries.firstOrNull { it.name == bookType },
        subjects = subjects?.split(SUBJECT_SEPARATOR)?.filter { it.isNotBlank() }.orEmpty(),
        printPages = printPages,
        series = series,
        rating = rating,
        ratingsCount = ratingsCount
    ),
    rightToLeft = rightToLeft,
    pageLabels = pageLabels,
    format = BookFormat.entries.firstOrNull { it.name == format } ?: BookFormat.PDF,
    lastLocator = lastLocator,
    folder = folder
)

private const val SUBJECT_SEPARATOR = "; "
private const val MAX_RATING = 5f

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

/** EPUBs are zip files ("PK" 3 4). Everything else goes to the PDF engine, which finds its own header. */
private fun formatOf(fd: ParcelFileDescriptor): BookFormat = fd.use {
    val head = ByteArray(ZIP_MAGIC.size)
    val n = runCatching {
        ParcelFileDescriptor.AutoCloseInputStream(it.dup()).use { s -> s.read(head) }
    }.getOrDefault(0)
    if (n == head.size && head.contentEquals(ZIP_MAGIC)) BookFormat.EPUB else BookFormat.PDF
}

private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
