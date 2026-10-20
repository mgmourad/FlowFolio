package com.flowfolio.feature.reader

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The island disk cache's sizes and trimming (images need a device: Bitmap). */
class IslandStoreTest {
    private val root: File = Files.createTempDirectory("islands").toFile()

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun sizesSurviveAReopenPerLayout() {
        val store = IslandStore(root, "book", 54f, 900)
        store.saveSizes(3, listOf(IslandBox(7, 120, 60, 44, 10, 20), IslandBox(8, 30, 40, 31, 0, 0)))
        store.saveSizes(4, listOf(IslandBox(9, 1, 2, 1, 0, 0)))

        val again = IslandStore(root, "book", 54f, 900).sizes()
        // Positions on the WebView's page are not kept: only sizes and baselines matter later.
        assertEquals(IslandBox(7, 120, 60, 44, 0, 0), again[7])
        assertEquals(setOf(7, 8, 9), again.keys)
        // Another text size or column width is another layout.
        assertTrue(IslandStore(root, "book", 60f, 900).sizes().isEmpty())
        assertFalse(again.isEmpty())
        assertFalse(store.has(7, null))
        assertTrue(File(store.dir, "sizes-3.txt").exists())
        assertTrue(store.dir.listFiles().orEmpty().none { it.name.endsWith(".tmp") })
    }

    @Test
    fun trimDeletesTheOldestLayoutsButNotTheOneInUse() {
        val old = IslandStore(root, "a", 54f, 900).also { fill(it, 600) }
        val newer = IslandStore(root, "b", 54f, 900).also { fill(it, 600) }
        val current = IslandStore(root, "c", 54f, 900).also { fill(it, 600) }
        old.dir.setLastModified(1_000)
        newer.dir.setLastModified(2_000)
        current.dir.setLastModified(500) // in use, though oldest

        IslandStore.trim(root, keep = current.dir, capBytes = 1_300)

        assertFalse(old.dir.exists())
        assertTrue(newer.dir.exists())
        assertTrue(current.dir.exists())
    }

    private fun fill(store: IslandStore, bytes: Int) = File(store.dir, "1.png").writeBytes(ByteArray(bytes))
}
