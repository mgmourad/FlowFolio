package com.flowfolio.core.epub

import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** A book read in place goes through its descriptor ([ChannelZip]) and reads exactly as one opened from a file. */
@RunWith(AndroidJUnit4::class)
class ChannelZipTest {
    private val made = ArrayList<File>()

    @After
    fun tearDown() = made.forEach { it.delete() }

    private val chapter = ("<p>Call me Ishmael.</p>".repeat(2_000)).toByteArray()
    private val mimetype = "application/epub+zip".toByteArray()

    private fun zip(): File {
        val f = File.createTempFile("book", ".epub").also(made::add)
        ZipOutputStream(f.outputStream()).use { z ->
            // Stored, as EPUB's mimetype must be.
            z.putNextEntry(
                ZipEntry("mimetype").apply {
                    method = ZipEntry.STORED
                    size = mimetype.size.toLong()
                    crc = CRC32().apply { update(mimetype) }.value
                }
            )
            z.write(mimetype)
            z.putNextEntry(ZipEntry("OEBPS/ch1.xhtml"))
            z.write(chapter)
            z.putNextEntry(ZipEntry("OEBPS/empty.css"))
            z.setComment("a zip comment the reader must skip")
        }
        return f
    }

    private fun viaDescriptor(f: File) =
        ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY).use { EpubArchive.open(it) }

    @Test
    fun storedAndDeflatedEntriesReadTheSameAsFromTheFile() {
        val f = zip()
        viaDescriptor(f).use { fd ->
            EpubArchive.open(f).use { file ->
                assertArrayEquals(mimetype, fd.read("mimetype"))
                assertArrayEquals(chapter, fd.read("OEBPS/ch1.xhtml"))
                assertArrayEquals(file.read("OEBPS/ch1.xhtml"), fd.read("OEBPS/ch1.xhtml"))
                assertEquals(0, fd.read("OEBPS/empty.css")?.size)
                assertTrue(fd.contains("OEBPS/./ch1.xhtml"))
                assertFalse(fd.contains("OEBPS/missing.xhtml"))
                assertNull(fd.read("../outside"))
            }
        }
    }

    @Test
    fun aFileThatIsNotAZipIsNotABook() {
        val f = File.createTempFile("plain", ".epub").also(made::add).apply { writeText("not a zip at all") }
        val reason = try {
            viaDescriptor(f)
            null
        } catch (e: EpubException) {
            e.reason
        }
        assertEquals(EpubException.Reason.NOT_EPUB, reason)
    }
}
