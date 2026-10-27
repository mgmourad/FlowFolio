package com.flowfolio.core.data

import android.os.ParcelFileDescriptor
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.flowfolio.core.database.FlowFolioDatabase
import com.flowfolio.core.model.Availability
import com.flowfolio.core.model.BookFormat
import com.flowfolio.core.model.BookStorage
import com.flowfolio.core.model.LibrarySort
import com.flowfolio.core.model.StorageMode
import java.io.File
import java.io.FileInputStream
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DefaultLibraryRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var db: FlowFolioDatabase
    private lateinit var appFiles: File
    private lateinit var source: FakeDocumentSource
    private lateinit var files: BookFiles
    private lateinit var repo: DefaultLibraryRepository
    private var now = 1_000L

    private val inspectors = BookInspectors(FakeInspector(), FakeEpubInspector())

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FlowFolioDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        appFiles = tmp.newFolder("app")
        files = BookFiles(appFiles)
        source = FakeDocumentSource()
        repo =
            DefaultLibraryRepository(db.bookDao(), source, inspectors, files, {
                now++
            }, UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() = db.close()

    private fun pdf(name: String, body: String = name) = source.add(
        "content://docs/$name",
        tmp.newFile(name).apply {
            writeText("%PDF-1.7 $body")
        }
    )

    @Test
    fun copyImportStoresOwnCopyNamedBySha256() = runTest {
        val uri = pdf("physics.pdf")
        val result = repo.import(uri, StorageMode.COPY) as ImportResult.Imported
        val storage = result.book.storage as BookStorage.Copied
        assertEquals(64, result.book.id.length)
        assertTrue(File(storage.path).exists())
        assertEquals("${result.book.id}.pdf", File(storage.path).name)
        assertEquals("Title of physics.pdf", result.book.title)
        assertTrue(File(result.book.coverPath!!).exists())
        assertTrue("no temp files left", File(appFiles, "import-tmp").listFiles().isNullOrEmpty())
    }

    @Test
    fun sameContentIsADuplicateEvenUnderAnotherName() = runTest {
        val first = repo.import(pdf("a.pdf", "same"), StorageMode.COPY) as ImportResult.Imported
        val second = repo.import(pdf("b.pdf", "same"), StorageMode.COPY)
        assertEquals(first.book.id, (second as ImportResult.Duplicate).existing.id)
        assertEquals(1, File(appFiles, "books").listFiles()!!.size)
    }

    @Test
    fun linkImportKeepsUriAndPersistsAccess() = runTest {
        val uri = pdf("linked.pdf")
        val book = (repo.import(uri, StorageMode.LINK) as ImportResult.Imported).book
        assertEquals(BookStorage.Linked(uri), book.storage)
        assertTrue(uri in source.persisted)
        assertFalse(File(appFiles, "books/${book.id}.pdf").exists())
    }

    @Test
    fun linkNotPossibleWhenProviderRefusesPersistentAccess() = runTest {
        val uri = pdf("shared.pdf")
        source.refusePersist += uri
        assertEquals(ImportResult.LinkNotPossible, repo.import(uri, StorageMode.LINK))
    }

    @Test
    fun sharedFilesAreAlwaysCopied() = runTest {
        val uri = pdf("shared.pdf")
        source.refusePersist += uri
        val book = (repo.import(uri, StorageMode.LINK, fromShare = true) as ImportResult.Imported).book
        assertTrue(book.storage is BookStorage.Copied)
    }

    @Test
    fun notAPdfLeavesNothingBehindAndReleasesAccess() = runTest {
        val uri = source.add("content://docs/notes.pdf", tmp.newFile("notes.pdf").apply { writeText("just text") })
        assertEquals(ImportResult.NotABook, repo.import(uri, StorageMode.COPY))
        assertTrue(File(appFiles, "books").listFiles().isNullOrEmpty())
        assertEquals(ImportResult.NotABook, repo.import(uri, StorageMode.LINK))
        assertFalse(uri in source.persisted)
        repo.observeBooks(LibrarySort.RECENT).test { assertEquals(0, awaitItem().size) }
    }

    private fun epub(name: String, body: String) = source.add(
        "content://docs/$name",
        tmp.newFile(name).apply { writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04) + body.toByteArray()) }
    )

    @Test
    fun epubsAreRecognisedByContentAndKeepTheirMetadata() = runTest {
        val book = (repo.import(epub("novel.epub", "good"), StorageMode.COPY) as ImportResult.Imported).book
        assertEquals(BookFormat.EPUB, book.format)
        assertEquals("${book.id}.epub", File((book.storage as BookStorage.Copied).path).name)
        assertEquals("An EPUB", book.title)
        assertEquals("Ann Author", book.author)
        assertEquals("9780306406157", book.details.isbn)
        assertEquals("ar", book.details.language)
        assertTrue(book.rightToLeft)
        assertEquals(12, book.pageCount)
        // A PDF named .epub is still a PDF: the content decides.
        val odd = repo.import(pdf("odd.epub"), StorageMode.COPY) as ImportResult.Imported
        assertEquals(BookFormat.PDF, odd.book.format)
    }

    @Test
    fun drmAndDamagedEpubsAreRefusedAndLeaveNothing() = runTest {
        assertEquals(ImportResult.DrmProtected, repo.import(epub("locked.epub", "drm"), StorageMode.COPY))
        assertEquals(ImportResult.NotABook, repo.import(epub("broken.epub", "broken"), StorageMode.COPY))
        assertTrue(File(appFiles, "books").listFiles().isNullOrEmpty())
        assertTrue(File(appFiles, "covers").listFiles().isNullOrEmpty())
    }

    @Test
    fun freeSpaceIsMeasuredEvenBeforeTheBooksFolderExists() {
        val fresh = BookFiles(File(tmp.root, "not-created-yet"))
        assertTrue(fresh.usableBytes() > 0)
    }

    @Test
    fun unreadableUri() = runTest {
        assertEquals(ImportResult.Unreadable, repo.import("content://docs/missing.pdf", StorageMode.COPY))
    }

    @Test
    fun linkedBookGoesMissingAndRelinksOnlyToTheSameContent() = runTest {
        val uri = pdf("moved.pdf", "the book")
        val book = (repo.import(uri, StorageMode.LINK) as ImportResult.Imported).book
        source.remove(uri)
        assertEquals(Availability.MISSING, repo.availability(book))

        val other = pdf("other.pdf", "a different book")
        assertEquals(RelinkResult.DifferentFile, repo.relink(book.id, other))

        val sameElsewhere = source.add(
            "content://docs/new-place.pdf",
            tmp.newFile("new-place.pdf").apply {
                writeText("%PDF-1.7 the book")
            }
        )
        assertEquals(RelinkResult.Relinked, repo.relink(book.id, sameElsewhere))
        repo.observeBooks(LibrarySort.RECENT).test {
            val relinked = awaitItem().single()
            assertEquals(BookStorage.Linked(sameElsewhere), relinked.storage)
            assertEquals(Availability.AVAILABLE, repo.availability(relinked))
        }
    }

    @Test
    fun deleteRemovesCopyCoverAndReleasesLinks() = runTest {
        val copied = (repo.import(pdf("c.pdf"), StorageMode.COPY) as ImportResult.Imported).book
        val linkedUri = pdf("l.pdf")
        val linked = (repo.import(linkedUri, StorageMode.LINK) as ImportResult.Imported).book
        repo.delete(copied.id)
        repo.delete(linked.id)
        assertFalse(File((copied.storage as BookStorage.Copied).path).exists())
        assertFalse(File(copied.coverPath!!).exists())
        assertFalse(linkedUri in source.persisted)
        repo.observeBooks(LibrarySort.TITLE).test { assertEquals(0, awaitItem().size) }
    }

    @Test
    fun recentSortFollowsOpening() = runTest {
        val a = (repo.import(pdf("a.pdf"), StorageMode.COPY) as ImportResult.Imported).book
        val b = (repo.import(pdf("b.pdf"), StorageMode.COPY) as ImportResult.Imported).book
        repo.observeBooks(LibrarySort.RECENT).test {
            assertEquals(listOf(b.id, a.id), awaitItem().map { it.id })
            repo.markOpened(a.id)
            assertEquals(listOf(a.id, b.id), awaitItem().map { it.id })
        }
    }
}

/** Maps content URIs to temp files; tracks persisted grants. */
internal class FakeDocumentSource : DocumentSource {
    private val docs = mutableMapOf<String, File>()
    val persisted = mutableSetOf<String>()
    val refusePersist = mutableSetOf<String>()

    fun add(uri: String, file: File): String = uri.also { docs[it] = file }

    fun remove(uri: String) {
        docs.remove(uri)
    }

    override fun open(uri: String): ParcelFileDescriptor? =
        docs[uri]?.takeIf { it.exists() }?.let { ParcelFileDescriptor.open(it, ParcelFileDescriptor.MODE_READ_ONLY) }

    override fun displayName(uri: String) = uri.substringAfterLast('/')

    override fun size(uri: String): Long? = docs[uri]?.length()

    override fun persistReadAccess(uri: String): Boolean = (uri !in refusePersist).also { if (it) persisted += uri }

    override fun releaseReadAccess(uri: String) {
        persisted -= uri
    }

    /** Folder listings by tree URI. */
    val folders = mutableMapOf<String, List<FolderFile>>()

    override fun listFolder(treeUri: String): List<FolderFile>? = folders[treeUri]

    override fun persistFolderAccess(treeUri: String): Boolean = persistReadAccess(treeUri)
}

/** EPUB stand-in: "good" books carry metadata, "drm" ones are encrypted, anything else is damaged. */
internal class FakeEpubInspector : EpubInspector {
    override suspend fun inspect(fd: ParcelFileDescriptor, coverOut: File, coverWidthPx: Int): EpubInfo {
        val text = FileInputStream(fd.fileDescriptor).readBytes().drop(4).toByteArray().decodeToString()
        when (text) {
            "drm" -> throw DrmProtectedException()
            "good" -> Unit
            else -> throw NotAPdfException()
        }
        coverOut.parentFile?.mkdirs()
        coverOut.writeText("cover")
        return EpubInfo(
            title = "An EPUB", author = "Ann Author", language = "ar", publisher = "Pub", isbn = "9780306406157",
            description = "About it", released = "2026", printedPages = 12, rightToLeft = true
        )
    }
}

/** Accepts files starting with "%PDF"; title comes from the file's name on disk; writes a stub cover. */
internal class FakeInspector : PdfInspector {
    override suspend fun inspect(fd: ParcelFileDescriptor, coverOut: File, coverWidthPx: Int): PdfInfo {
        val text = FileInputStream(fd.fileDescriptor).bufferedReader().readText()
        if (!text.startsWith("%PDF")) throw NotAPdfException()
        coverOut.parentFile?.mkdirs()
        coverOut.writeText("cover")
        // Only the "physics" file has metadata; the others fall back to their display name.
        val title = "Title of ${text.removePrefix("%PDF-1.7 ")}".takeIf { "physics" in text }
        return PdfInfo(pageCount = 3, title = title, passwordProtected = false)
    }
}
