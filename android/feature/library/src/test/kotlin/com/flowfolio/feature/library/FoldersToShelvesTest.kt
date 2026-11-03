package com.flowfolio.feature.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun inFolder(id: String, folder: String?) = book(id).copy(folder = folder)

/** Which shelves a folder import would make (owner, M5 round 4). */
class FoldersToShelvesTest {
    @Test
    fun `each folder becomes a shelf of its own name`() {
        val plan = FoldersToShelves.plan(
            listOf(inFolder("a", "Books/Physics"), inFolder("b", "Books/Physics"), inFolder("c", "Books/Poetry"))
        )
        assertEquals(setOf("Physics", "Poetry"), plan.keys)
        assertEquals(listOf("a", "b"), plan.getValue("Physics").map { it.id })
    }

    /** "recursively too": a folder inside a folder is a folder, and gets its own shelf. */
    @Test
    fun `folders inside folders get their own shelves`() {
        val plan = FoldersToShelves.plan(
            listOf(inFolder("a", "Books"), inFolder("b", "Books/Physics"), inFolder("c", "Books/Physics/Optics"))
        )
        assertEquals(setOf("Books", "Physics", "Optics"), plan.keys)
    }

    /** A book that came in loose has no folder to name a shelf after. */
    @Test
    fun `books with no folder make no shelf`() {
        val plan = FoldersToShelves.plan(listOf(inFolder("a", null), inFolder("b", ""), inFolder("c", "   ")))
        assertTrue(plan.isEmpty())
    }

    /** Two folders of the same name in different places were almost certainly meant as one subject. */
    @Test
    fun `the same folder name anywhere shares one shelf`() {
        val plan = FoldersToShelves.plan(listOf(inFolder("a", "2024/Physics"), inFolder("b", "2025/Physics")))
        assertEquals(setOf("Physics"), plan.keys)
        assertEquals(listOf("a", "b"), plan.getValue("Physics").map { it.id })
    }

    /** The order books were imported in is the order they stand on the shelf. */
    @Test
    fun `shelves keep the import order`() {
        val plan = FoldersToShelves.plan(listOf(inFolder("c", "X"), inFolder("a", "X"), inFolder("b", "X")))
        assertEquals(listOf("c", "a", "b"), plan.getValue("X").map { it.id })
    }
}
