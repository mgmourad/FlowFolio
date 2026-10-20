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
            BookInspectors(PdfiumInspector(PdfiumEngine()), CoreEpubInspector()),
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

    /** A small EPUB 3 with a PNG cover, a page list and right-to-left pages, built on the device. */
    private fun epub(name: String, encrypted: Boolean = false): Uri {
        val cover = java.io.ByteArrayOutputStream().also { out ->
            android.graphics.Bitmap.createBitmap(600, 900, android.graphics.Bitmap.Config.ARGB_8888)
                .apply { eraseColor(android.graphics.Color.RED) }
                .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
        }.toByteArray()
        val file = File(root, name)
        java.util.zip.ZipOutputStream(file.outputStream()).use { z ->
            fun put(path: String, data: ByteArray) {
                z.putNextEntry(java.util.zip.ZipEntry(path))
                z.write(data)
            }
            put("mimetype", "application/epub+zip".toByteArray())
            put("META-INF/container.xml", CONTAINER.toByteArray())
            if (encrypted) put("META-INF/encryption.xml", ENCRYPTION.toByteArray())
            put("OPS/book.opf", PACKAGE.toByteArray())
            put("OPS/nav.xhtml", NAV.toByteArray())
            put("OPS/ch.xhtml", CHAPTER.toByteArray())
            put("OPS/c.png", cover)
        }
        return Uri.fromFile(file)
    }

    @Test
    fun epubImportReadsMetadataAndWritesCover() = runBlocking {
        val book = (repo.import(epub("device.epub").toString(), StorageMode.COPY) as ImportResult.Imported).book
        assertEquals(com.flowfolio.core.model.BookFormat.EPUB, book.format)
        assertEquals("On-device EPUB", book.title)
        assertEquals("Test Author", book.author)
        assertEquals(2, book.pageCount)
        assertTrue(book.rightToLeft)
        assertTrue((book.storage as BookStorage.Copied).path.endsWith(".epub"))
        val cover = BitmapFactory.decodeFile(book.coverPath)
        assertNotNull("cover should decode", cover)
        assertEquals(360, cover.width)
        assertEquals(540, cover.height)
    }

    @Test
    fun drmEpubIsRefused() = runBlocking {
        val result = repo.import(epub("locked.epub", encrypted = true).toString(), StorageMode.COPY)
        assertEquals(ImportResult.DrmProtected, result)
    }

    @Test
    fun nonPdfIsRejected() = runBlocking {
        assertEquals(ImportResult.NotABook, repo.import(fixture("not-a-pdf.pdf").toString(), StorageMode.COPY))
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

    private companion object {
        val CONTAINER = """
            <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0"><rootfiles>
            <rootfile full-path="OPS/book.opf" media-type="application/oebps-package+xml"/></rootfiles></container>
        """.trimIndent()
        val ENCRYPTION = """
            <encryption xmlns="urn:oasis:names:tc:opendocument:xmlns:container"
              xmlns:enc="http://www.w3.org/2001/04/xmlenc#"><enc:EncryptedData>
            <enc:EncryptionMethod Algorithm="http://www.w3.org/2001/04/xmlenc#aes128-cbc"/></enc:EncryptedData>
            </encryption>
        """.trimIndent()
        val PACKAGE = """
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
            <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>On-device EPUB</dc:title>
            <dc:creator>Test Author</dc:creator><dc:language>ar</dc:language></metadata><manifest>
            <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
            <item id="c" href="c.png" media-type="image/png" properties="cover-image"/>
            <item id="ch" href="ch.xhtml" media-type="application/xhtml+xml"/></manifest>
            <spine page-progression-direction="rtl"><itemref idref="ch"/></spine></package>
        """.trimIndent()
        val NAV = """
            <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><body>
            <nav epub:type="page-list"><ol><li><a href="ch.xhtml#p1">1</a></li>
            <li><a href="ch.xhtml#p2">2</a></li></ol></nav></body></html>
        """.trimIndent()
        const val CHAPTER = "<html xmlns=\"http://www.w3.org/1999/xhtml\"><body><p>Hi</p></body></html>"
    }
}
