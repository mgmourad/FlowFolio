package com.flowfolio.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flowfolio.core.database.BookEntity
import com.flowfolio.core.database.FlowFolioDatabase
import com.flowfolio.core.model.BookDetails
import com.flowfolio.core.model.BookType
import com.flowfolio.core.model.HighlightColor
import com.flowfolio.core.model.PageRect
import com.flowfolio.core.model.Passage
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Schema v5: editable details and reading sessions, on an in-memory database. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class BookDetailsTest {
    private lateinit var db: FlowFolioDatabase
    private lateinit var library: DefaultLibraryRepository
    private lateinit var reading: DefaultReadingRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FlowFolioDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val io = UnconfinedTestDispatcher()
        library =
            DefaultLibraryRepository(
                db.bookDao(),
                FakeDocumentSource(),
                BookInspectors(
                    FakeInspector(),
                    FakeEpubInspector()
                ),
                BookFiles(File("x")),
                {
                    1L
                },
                io
            )
        reading = DefaultReadingRepository(db.readingSessionDao(), io)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun insert(id: String = "b") = db.bookDao().insert(
        BookEntity(id, "Physics", 10, "COPY", "/p.pdf", 1, 0, null, null, false, author = "From PDF")
    )

    @Test
    fun detailsRoundTripAndBlanksBecomeUnknown() = runTest {
        insert()
        val details = BookDetails(
            subtitle = " Volume 1 ",
            edition = "2nd",
            publisher = "OpenStax",
            isbn = "",
            description = "Mechanics.",
            language = "English",
            released = "2016",
            authorRole = "  ",
            authorBio = "Many authors.",
            type = BookType.TEXTBOOK
        )
        library.updateDetails("b", " University Physics ", "Ling, Sanny, Moebs", details)
        val book = library.observeBook("b").first()!!
        assertEquals("University Physics", book.title)
        assertEquals("Ling, Sanny, Moebs", book.author)
        assertEquals("Volume 1", book.details.subtitle)
        assertNull(book.details.isbn)
        assertNull(book.details.authorRole)
        assertEquals(BookType.TEXTBOOK, book.details.type)
        assertEquals("Mechanics.", book.details.description)
    }

    @Test
    fun readingTimeAddsUpAndGoesWithTheBook() = runTest {
        insert()
        reading.record("b", 0, 60_000)
        reading.record("b", 100_000, 160_000)
        reading.record("b", 5, 5) // empty: ignored
        assertEquals(120_000L, reading.observeReadMillis("b").first())
        db.bookDao().delete("b")
        assertEquals(0L, reading.observeReadMillis("b").first())
    }

    @Test
    fun annotationsKeepTheirPlaceColourAndNote() = runTest {
        insert()
        val repo =
            DefaultAnnotationRepository(db.annotationDao(), db.bookReadingDao(), { 5L }, UnconfinedTestDispatcher())
        val rects = listOf(PageRect(0.1f, 0.2f, 0.5f, 0.23f), PageRect(0.1f, 0.24f, 0.3f, 0.27f))
        val id = repo.add("b", Passage(4, "285", 10..42, "Drag force is", rects), HighlightColor.GREEN, note = "  ")
        repo.setNote(id, "Exam!")
        val a = repo.observe("b").first().single()
        assertEquals(4, a.page)
        assertEquals("285", a.label)
        assertEquals(10, a.start)
        assertEquals(rects, a.rects)
        assertEquals(HighlightColor.GREEN, a.color)
        assertEquals("Exam!", a.note)
        repo.setRightToLeft("b", true)
        assertEquals(true, library.observeBook("b").first()!!.rightToLeft)
        db.bookDao().delete("b")
        assertEquals(0, repo.observeAll().first().size)
    }
}
