package com.flowfolio.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** EPUB highlights and ribbons keep their locator; a re-layout moves only their page (schema v14, ADR-0040). */
@RunWith(AndroidJUnit4::class)
class LocatorColumnsTest {
    private lateinit var db: FlowFolioDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FlowFolioDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        db.bookDao().insertBlocking()
    }

    @After
    fun tearDown() = db.close()

    private fun BookDao.insertBlocking() = kotlinx.coroutines.runBlocking {
        insert(BookEntity("b", "Book", 10, "COPY", "/books/b.epub", 100, 1, null, null, false))
    }

    @Test
    fun aHighlightKeepsItsLocatorAndDateWhenItsPageMoves() = runTest {
        val dao = db.annotationDao()
        val id = dao.insert(
            AnnotationEntity(
                bookId = "b",
                page = 12,
                pageLabel = null,
                charStart = 340,
                charEnd = 399,
                text = "Call me Ishmael.",
                rects = "",
                color = "YELLOW",
                note = null,
                createdAt = 1,
                updatedAt = 1,
                locator = "3:340"
            )
        )
        dao.setPage(id, 20)
        val a = dao.observe("b").first().single()
        assertEquals(20, a.page)
        assertEquals("3:340", a.locator)
        assertEquals(1L, a.updatedAt)
    }

    @Test
    fun aRibbonKeepsItsLocatorAndMovesPage() = runTest {
        val dao = db.bookmarkDao()
        val id = dao.insert(BookmarkEntity(bookId = "b", page = 4, color = "SUB_ITEM", createdAt = 1, locator = "2:0"))
        dao.setPage(id, 7)
        val m = dao.observe("b").first().single()
        assertEquals(7, m.page)
        assertEquals("2:0", m.locator)
    }
}
