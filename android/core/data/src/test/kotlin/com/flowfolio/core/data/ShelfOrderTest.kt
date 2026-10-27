package com.flowfolio.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flowfolio.core.database.BookEntity
import com.flowfolio.core.database.FlowFolioDatabase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** A shelf's order (Room v13 `shelf_books.position`): books added stand first, and drag and drop sticks. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ShelfOrderTest {
    private lateinit var db: FlowFolioDatabase
    private lateinit var shelves: DefaultShelfRepository
    private var now = 1_000L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FlowFolioDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        shelves = DefaultShelfRepository(db.shelfDao(), { now++ }, UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() = db.close()

    private suspend fun books(vararg ids: String) = ids.forEach {
        db.bookDao().insert(BookEntity(it, it, 1, "COPY", "/$it", 1, 0, null, null, false))
    }

    private suspend fun order(shelfId: Long) = shelves.observeShelves().first().single { it.id == shelfId }.bookIds

    @Test
    fun booksAddedStandFirstAndAReorderSticks() = runTest {
        books("a", "b", "c", "d")
        val shelf = shelves.create("Fall")
        shelves.addBooks(shelf, listOf("a", "b"))
        shelves.addBooks(shelf, listOf("c", "a"))
        // The new one first, in the order picked; one already there keeps its place.
        assertEquals(listOf("c", "a", "b"), order(shelf))
        shelves.setOrder(shelf, listOf("b", "c", "a"))
        assertEquals(listOf("b", "c", "a"), order(shelf))
        // A book put on the shelf from its own menu stands first too.
        shelves.setShelves("d", setOf(shelf))
        assertEquals(listOf("d", "b", "c", "a"), order(shelf))
    }
}
