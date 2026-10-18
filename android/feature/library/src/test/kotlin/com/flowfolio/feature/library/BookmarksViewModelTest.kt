package com.flowfolio.feature.library

import app.cash.turbine.test
import com.flowfolio.core.data.BookmarkRepository
import com.flowfolio.core.data.OpenedBook
import com.flowfolio.core.data.ReaderRepository
import com.flowfolio.core.model.Bookmark
import com.flowfolio.core.model.BookmarkColor
import java.io.FileNotFoundException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeBookmarks(initial: List<Bookmark> = emptyList()) : BookmarkRepository {
    val marks = MutableStateFlow(initial)

    override fun observe(bookId: String): Flow<List<Bookmark>> = marks.map { all -> all.filter { it.bookId == bookId } }

    override fun observeAll(): Flow<List<Bookmark>> = marks.map { all -> all.sortedByDescending { it.createdAtMillis } }

    override suspend fun add(bookId: String, page: Int, color: BookmarkColor): Long {
        val id = (marks.value.maxOfOrNull { it.id } ?: 0) + 1
        marks.update { it + Bookmark(id, bookId, page, color, id) }
        return id
    }

    override suspend fun setColor(bookmarkId: Long, color: BookmarkColor) =
        marks.update { all -> all.map { if (it.id == bookmarkId) it.copy(color = color) else it } }

    override suspend fun remove(bookmarkId: Long) = marks.update { all -> all.filterNot { it.id == bookmarkId } }
}

/** A reader whose books cannot be opened (no PDFs in unit tests), so cards fall back to page numbers. */
class FakeReader : ReaderRepository {
    val saved = mutableListOf<Pair<String, Int>>()

    override suspend fun open(bookId: String, password: String?): OpenedBook = throw FileNotFoundException(bookId)

    override suspend fun saveProgress(bookId: String, page: Int) {
        saved += bookId to page
    }

    override suspend fun savePageLabels(bookId: String, labels: String) = Unit
}

@OptIn(ExperimentalCoroutinesApi::class)
class BookmarksViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val library = FakeLibrary()
    private val reader = FakeReader()
    private val bookmarks = FakeBookmarks(
        listOf(
            Bookmark(1, "a", 4, BookmarkColor.SUB_ITEM, createdAtMillis = 10),
            Bookmark(2, "b", 0, BookmarkColor.GREEN, createdAtMillis = 30),
            Bookmark(3, "a", 7, BookmarkColor.GREEN, createdAtMillis = 20),
            Bookmark(4, "gone", 1, BookmarkColor.ITEM, createdAtMillis = 40)
        )
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        library.books.value = listOf(book("a", "Physics"), book("b", "Chemistry"))
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm(book: String? = null) =
        BookmarksViewModel(androidx.lifecycle.SavedStateHandle(mapOf("bookId" to book)), bookmarks, library, reader)

    @Test
    fun listsRibbonsNewestFirstAndSkipsRemovedBooks() = runTest(dispatcher) {
        vm().uiState.test {
            val state = expectMostRecentItem()
            assertEquals(listOf(2L, 3L, 1L), state.items.map { it.bookmark.id })
            assertEquals(3, state.total)
            assertEquals(2, state.bookCount)
            assertEquals(listOf(BookmarkColor.SUB_ITEM, BookmarkColor.GREEN), state.colours)
            // Without a readable PDF there is no chapter, and the page shows its 1-based number.
            assertNull(state.items.first().chapter)
            assertEquals("8", state.items[1].label)
        }
    }

    @Test
    fun filterShowsOneColourUntilCleared() = runTest(dispatcher) {
        val vm = vm()
        vm.uiState.test {
            vm.setFilter(BookmarkColor.GREEN)
            assertEquals(listOf(2L, 3L), expectMostRecentItem().items.map { it.bookmark.id })
            vm.setFilter(null)
            assertEquals(3, expectMostRecentItem().items.size)
        }
    }

    @Test
    fun recolourAndRemoveChangeTheRibbon() = runTest(dispatcher) {
        val vm = vm()
        vm.uiState.test {
            val first = expectMostRecentItem().items.first()
            vm.recolour(first, BookmarkColor.TITLE)
            assertEquals(BookmarkColor.TITLE, expectMostRecentItem().items.first().bookmark.color)
            vm.remove(first)
            assertTrue(expectMostRecentItem().items.none { it.bookmark.id == first.bookmark.id })
        }
    }

    @Test
    fun openingSavesThePageBeforeShowingTheReader() = runTest(dispatcher) {
        val vm = vm()
        vm.uiState.test {
            val item = expectMostRecentItem().items.last()
            var shownAfter: List<Pair<String, Int>>? = null
            vm.open(item) { shownAfter = reader.saved.toList() }
            assertEquals(listOf("a" to 4), shownAfter)
        }
    }
}
