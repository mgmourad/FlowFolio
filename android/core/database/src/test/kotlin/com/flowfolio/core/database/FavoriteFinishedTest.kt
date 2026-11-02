package com.flowfolio.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Favourites and finished books (schema v16, M3T2), and a view's notes counted for Dynamic Flow's question (M3T1). */
@RunWith(AndroidJUnit4::class)
class FavoriteFinishedTest {
    private lateinit var db: FlowFolioDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FlowFolioDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        kotlinx.coroutines.runBlocking {
            db.bookDao().insert(BookEntity("b", "Book", 10, "COPY", "/books/b.pdf", 100, 1, null, null, false))
        }
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun aBookStartsNeitherFavouriteNorFinished() = runTest {
        val book = db.bookDao().observe("b").first()!!
        assertFalse(book.favorite)
        assertNull(book.finishedAt)
    }

    @Test
    fun favouriteAndFinishedAreSetAndCleared() = runTest {
        val dao = db.bookDao()
        dao.setFavorite("b", true)
        dao.setFinishedAt("b", 1_234L)
        dao.observe("b").first()!!.let {
            assertTrue(it.favorite)
            assertEquals(1_234L, it.finishedAt)
        }
        dao.setFavorite("b", false)
        dao.setFinishedAt("b", null)
        dao.observe("b").first()!!.let {
            assertFalse(it.favorite)
            assertNull(it.finishedAt)
        }
    }

    @Test
    fun notesAreCountedPerView() = runTest {
        val notes = db.annotationDao()
        fun note(locator: String?) = AnnotationEntity(
            bookId = "b",
            page = 1,
            pageLabel = null,
            charStart = 0,
            charEnd = 4,
            text = "Text",
            rects = "",
            color = "YELLOW",
            note = null,
            createdAt = 1,
            updatedAt = 1,
            locator = locator
        )
        notes.insert(note("2:10"))
        notes.insert(note(null))
        notes.insert(note(null))
        assertEquals(1, notes.countInView("b", flow = true))
        assertEquals(2, notes.countInView("b", flow = false))
        assertEquals(0, db.bookmarkDao().countInView("b", flow = true))
    }
}
