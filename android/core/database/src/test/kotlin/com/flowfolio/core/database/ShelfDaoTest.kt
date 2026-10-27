package com.flowfolio.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShelfDaoTest {
    private lateinit var db: FlowFolioDatabase
    private lateinit var books: BookDao
    private lateinit var dao: ShelfDao

    private fun book(id: String) = BookEntity(id, "Book $id", 10, "COPY", "/books/$id.pdf", 100, 1, null, null, false)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FlowFolioDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        books = db.bookDao()
        dao = db.shelfDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun setShelvesAddsAndRemovesAndKeepsDates() = runTest {
        books.insert(book("a"))
        val fall = dao.insert(ShelfEntity(name = "Fall Term", createdAt = 1))
        val fun2 = dao.insert(ShelfEntity(name = "Leisure", createdAt = 2))
        dao.setShelves("a", setOf(fall), now = 10)
        dao.setShelves("a", setOf(fall, fun2), now = 20)
        dao.observeMemberships().test {
            // Shelves, books and dates (positions are checked in ShelfOrderTest).
            assertEquals(
                listOf(Triple(fun2, "a", 20L), Triple(fall, "a", 10L)),
                awaitItem().map { Triple(it.shelfId, it.bookId, it.addedAt) }
            )
        }
        dao.setShelves("a", setOf(fun2), now = 30)
        assertEquals(listOf(fun2), dao.shelvesOf("a"))
    }

    @Test
    fun deletingABookOrShelfRemovesItsRows() = runTest {
        books.insert(book("a"))
        books.insert(book("b"))
        val shelf = dao.insert(ShelfEntity(name = "S", createdAt = 1))
        dao.setShelves("a", setOf(shelf), now = 1)
        dao.setShelves("b", setOf(shelf), now = 1)
        books.delete("a")
        assertEquals(emptyList<Long>(), dao.shelvesOf("a"))
        assertEquals(listOf(shelf), dao.shelvesOf("b"))
        dao.delete(shelf)
        assertEquals(emptyList<Long>(), dao.shelvesOf("b"))
        books.observeByRecent().test { assertEquals(listOf("b"), awaitItem().map { it.id }) }
    }
}
