package com.flowfolio.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BookDaoTest {
    private lateinit var db: FlowFolioDatabase
    private lateinit var dao: BookDao

    private fun book(id: String, title: String, added: Long, opened: Long? = null) =
        BookEntity(id, title, 10, "COPY", "/books/$id.pdf", 100, added, opened, null, false)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FlowFolioDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.bookDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun recentOrderUsesLastOpenedThenAdded() = runTest {
        dao.insert(book("a", "Alpha", added = 1))
        dao.insert(book("b", "beta", added = 2))
        dao.insert(book("c", "Gamma", added = 3))
        dao.markOpened("a", 10)
        dao.observeByRecent().test {
            assertEquals(listOf("a", "c", "b"), awaitItem().map { it.id })
        }
    }

    @Test
    fun titleOrderIgnoresCase() = runTest {
        dao.insert(book("1", "beta", added = 1))
        dao.insert(book("2", "Alpha", added = 2))
        dao.observeByTitle().test {
            assertEquals(listOf("Alpha", "beta"), awaitItem().map { it.title })
        }
    }

    @Test
    fun relinkAndDelete() = runTest {
        dao.insert(book("x", "X", added = 1))
        dao.updateLocation("x", "LINK", "content://doc/1")
        assertEquals("content://doc/1", dao.get("x")?.location)
        assertEquals("LINK", dao.get("x")?.storageMode)
        assertEquals(1, dao.delete("x"))
        assertNull(dao.get("x"))
    }

    @Test
    fun lastPageStartsEmptyAndIsSaved() = runTest {
        dao.insert(book("p", "P", added = 1))
        assertNull(dao.get("p")?.lastPage)
        dao.updateLastPage("p", 41)
        assertEquals(41, dao.get("p")?.lastPage)
    }

    @Test(expected = android.database.sqlite.SQLiteConstraintException::class)
    fun duplicateIdIsRejected() = runTest {
        dao.insert(book("dup", "One", added = 1))
        dao.insert(book("dup", "Two", added = 2))
    }
}
