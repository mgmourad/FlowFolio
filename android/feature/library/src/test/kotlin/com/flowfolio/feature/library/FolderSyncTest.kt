package com.flowfolio.feature.library

import com.flowfolio.core.data.DefaultFolderSync
import com.flowfolio.core.data.FolderFile
import com.flowfolio.core.model.StorageMode
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Books added to an imported folder on the phone come in on a refresh, and only those (TM2). */
class FolderSyncTest {
    private val library = FakeLibrary()
    private val sync = DefaultFolderSync(Files.createTempDirectory("folders").toFile(), library)

    @Test
    fun onlyNewFilesAreImported() = runTest {
        library.folders["tree:books"] = listOf(FolderFile("doc:a.pdf", "Books"), FolderFile("doc:b.pdf", "Books"))
        sync.remember("tree:books", StorageMode.COPY, listOf("doc:a.pdf", "doc:b.pdf"))
        // A third book is added to the folder on the phone.
        library.folders["tree:books"] =
            library.folders.getValue("tree:books") + FolderFile("doc:c.pdf", "Books/Physics")
        val report = sync.sync(inPlaceOnly = false)
        assertEquals(listOf("doc:c.pdf"), library.imports.map { it.first })
        assertEquals(StorageMode.COPY, library.imports.single().second)
        assertEquals("Books/Physics", library.importFolders["doc:c.pdf"])
        assertEquals(1, report.added.size)
        // Nothing new the second time.
        assertTrue(sync.sync(inPlaceOnly = false).added.isEmpty())
        assertEquals(1, library.imports.size)
    }

    @Test
    fun theQuietCheckLeavesCopiedFoldersToTheRefresh() = runTest {
        library.folders["tree:copied"] = listOf(FolderFile("doc:x.pdf", "Copied"))
        library.folders["tree:linked"] = listOf(FolderFile("doc:y.pdf", "Linked"))
        sync.remember("tree:copied", StorageMode.COPY, emptyList())
        sync.remember("tree:linked", StorageMode.LINK, emptyList())
        sync.sync(inPlaceOnly = true)
        assertEquals(listOf("doc:y.pdf" to StorageMode.LINK), library.imports.map { it.first to it.second })
    }

    @Test
    fun aFolderThatCannotBeReadIsLeftAsItIs() = runTest {
        sync.remember("tree:gone", StorageMode.LINK, listOf("doc:a.pdf"))
        assertTrue(sync.sync(inPlaceOnly = false).added.isEmpty())
        assertEquals(setOf("tree:gone"), sync.folders())
    }
}
