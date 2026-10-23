package com.flowfolio.feature.library

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.flowfolio.core.data.AnnotationRepository
import com.flowfolio.core.model.Annotation
import com.flowfolio.core.model.HighlightColor
import com.flowfolio.core.model.OutlineEntry
import com.flowfolio.core.model.Passage
import com.flowfolio.core.model.shareQuote
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
import org.junit.Before
import org.junit.Test

class FakeAnnotations(initial: List<Annotation>) : AnnotationRepository {
    val all = MutableStateFlow(initial)

    override fun observe(bookId: String): Flow<List<Annotation>> = all.map { l -> l.filter { it.bookId == bookId } }

    override fun observeAll(): Flow<List<Annotation>> = all

    override suspend fun add(bookId: String, passage: Passage, color: HighlightColor, note: String?): Long = 0

    override suspend fun setColor(id: Long, color: HighlightColor) = Unit

    override suspend fun setNote(id: Long, note: String?) =
        all.update { l -> l.map { if (it.id == id) it.copy(note = note) else it } }

    override suspend fun remove(id: Long) = all.update { l -> l.filterNot { it.id == id } }

    override suspend fun setRightToLeft(bookId: String, on: Boolean) = Unit
}

/** An annotation changed at time [at] (ordering), on [page] of [book]. */
fun annotation(id: Long, book: String, page: Int, color: HighlightColor, at: Long) =
    Annotation(id, book, page, null, id.toInt(), id.toInt() + 5, "text $id", emptyList(), color, null, at, at)

@OptIn(ExperimentalCoroutinesApi::class)
class AnnotationsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val library = FakeLibrary()
    private val repo = FakeAnnotations(
        listOf(
            annotation(1, "a", 9, HighlightColor.YELLOW, at = 10),
            annotation(2, "a", 3, HighlightColor.GREEN, at = 30).copy(note = "remember"),
            annotation(3, "b", 1, HighlightColor.PINK, at = 20),
            annotation(4, "gone", 1, HighlightColor.PINK, at = 50)
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
        AnnotationsViewModel(SavedStateHandle(mapOf("bookId" to book)), repo, library, FakeReader())

    @Test
    fun theTabListsBooksMostRecentlyAnnotatedFirst() = runTest(dispatcher) {
        vm().uiState.test {
            val state = expectMostRecentItem()
            assertEquals(listOf("a", "b"), state.books.map { it.book.id })
            val physics = state.books.first()
            assertEquals(2, physics.highlights)
            assertEquals(1, physics.notes)
            assertEquals(listOf(HighlightColor.YELLOW, HighlightColor.GREEN), physics.colours)
            assertEquals(3, state.total)
        }
    }

    @Test
    fun aBookPageListsItsAnnotationsInPageOrder() = runTest(dispatcher) {
        val vm = vm("a")
        vm.uiState.test {
            assertEquals(listOf(2L, 1L), expectMostRecentItem().annotations.map { it.id })
            vm.remove(repo.all.value.first())
            assertEquals(listOf(2L), expectMostRecentItem().annotations.map { it.id })
        }
    }

    @Test
    fun sharedQuotesNameTheBookAuthorAndPage() {
        assertEquals(
            "“Passive transport never requires ATP.”\n— Cellular Biology, Amara Okafor, p. 216",
            shareQuote(" Passive transport never requires ATP. ", "Cellular Biology", "Amara Okafor", "216")
        )
        assertEquals("“Q”\n— T, p. 3", shareQuote("Q", "T", null, "3"))
    }

    @Test
    fun chapterIsTheNearestEntryByPageNotTheLastListed() {
        // An outline flattened chapter by chapter, with a stray "Blank Page" listed last that points near the start.
        val toc = listOf(
            OutlineEntry(0, 10, "Chapter 1"),
            OutlineEntry(0, 40, "Chapter 2"),
            OutlineEntry(1, 12, "1.1 Units"),
            OutlineEntry(1, 42, "2.1 Vectors"),
            OutlineEntry(1, 42, "2.1.1 Scalars"),
            OutlineEntry(0, 2, "Blank Page")
        )
        assertEquals("1.1 Units", AnnotationsViewModel.chapterOf(toc, 20))
        assertEquals("2.1.1 Scalars", AnnotationsViewModel.chapterOf(toc, 45))
        assertEquals("Chapter 2", AnnotationsViewModel.chapterOf(toc, 40))
        assertEquals(null, AnnotationsViewModel.chapterOf(toc, 3))
        assertEquals(null, AnnotationsViewModel.chapterOf(null, 3))
    }
}
