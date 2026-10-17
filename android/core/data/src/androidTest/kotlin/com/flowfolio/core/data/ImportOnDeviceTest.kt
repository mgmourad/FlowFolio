package com.flowfolio.core.data

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.flowfolio.core.database.FlowFolioDatabase
import com.flowfolio.core.model.BookStorage
import com.flowfolio.core.model.StorageMode
import com.flowfolio.core.pdf.pdfium.PdfiumEngine
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Import through the real stack: ContentResolver, PDFium inspection and covers, Room. */
@RunWith(AndroidJUnit4::class)
class ImportOnDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: FlowFolioDatabase
    private lateinit var root: File
    private lateinit var repo: DefaultLibraryRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, FlowFolioDatabase::class.java).build()
        root = File(context.cacheDir, "import-test").apply {
            deleteRecursively()
            mkdirs()
        }
        repo = DefaultLibraryRepository(
            db.bookDao(),
            AndroidDocumentSource(context),
            PdfiumInspector(PdfiumEngine()),
            BookFiles(File(root, "app")),
            { System.currentTimeMillis() },
            Dispatchers.IO
        )
    }

    @After
    fun tearDown() {
        db.close()
        root.deleteRecursively()
    }

    private fun fixture(name: String): Uri {
        val file = File(root, name)
        InstrumentationRegistry.getInstrumentation().context.assets.open("pdfs/$name").use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        return Uri.fromFile(file)
    }

    @Test
    fun copyImportReadsTitlePageCountAndWritesCover() = runBlocking {
        val result = repo.import(fixture("labels-outline.pdf").toString(), StorageMode.COPY)
        val book = (result as ImportResult.Imported).book
        assertEquals("FlowFolio Test Book", book.title)
        assertEquals(5, book.pageCount)
        assertTrue(File((book.storage as BookStorage.Copied).path).exists())
        val cover = BitmapFactory.decodeFile(book.coverPath)
        assertNotNull("cover should decode", cover)
        assertEquals(360, cover.width)
    }

    @Test
    fun passwordProtectedImportsWithoutCover() = runBlocking {
        val book = (
            repo.import(
                fixture("encrypted-rc4.pdf").toString(),
                StorageMode.COPY
            ) as ImportResult.Imported
            ).book
        assertTrue(book.passwordProtected)
        assertEquals(null, book.coverPath)
        assertEquals("encrypted-rc4", book.title)
    }

    @Test
    fun nonPdfIsRejected() = runBlocking {
        assertEquals(ImportResult.NotAPdf, repo.import(fixture("not-a-pdf.pdf").toString(), StorageMode.COPY))
    }

    @Test
    fun plainFileUrisCannotBeLinked() = runBlocking {
        // Only Storage Access Framework URIs can keep access; this is what makes shared files copy-only.
        assertEquals(ImportResult.LinkNotPossible, repo.import(fixture("rotated.pdf").toString(), StorageMode.LINK))
    }

    @Test
    fun textbookCopyImportTiming() = runBlocking {
        val book = File("/data/local/tmp/flowfolio/university-physics-volume-1.pdf")
        assumeTrue("textbook not on device", book.canRead())
        val t0 = System.nanoTime()
        val imported = (repo.import(Uri.fromFile(book).toString(), StorageMode.COPY) as ImportResult.Imported).book
        val ms = (System.nanoTime() - t0) / 1_000_000
        android.util.Log.i(
            "FlowFolioDataTest",
            "FF_IMPORT textbook copy ms=$ms pages=${imported.pageCount} title=${imported.title}"
        )
        assertEquals(959, imported.pageCount)
        assertEquals("93652b69c43197d10dc83438b813b5f7515e61e7ea4336da8636ff902cb4aba8", imported.id)
    }
}
