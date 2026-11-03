package com.flowfolio.feature.library

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flowfolio.core.designsystem.FlowFolioTheme
import com.flowfolio.core.model.Book
import com.flowfolio.core.model.Shelf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShelfPageUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val res = ApplicationProvider.getApplicationContext<android.content.Context>().resources

    @Test
    fun tappingAShelfCountOpensThatShelf() {
        var opened: Long? = null
        val a = LibraryItem(book("a", "Anatomy"))
        compose.setContent {
            FlowFolioTheme {
                LibraryContent(
                    state = LibraryUiState(
                        loading = false,
                        items = listOf(a),
                        totalCount = 1,
                        shelves = listOf(Shelf(7, "Fall Term", listOf("a")))
                    ),
                    snackbar = SnackbarHostState(),
                    actions = LibraryActions(onOpenShelf = { opened = it })
                )
            }
        }
        compose.onNodeWithTag(LibraryTestTags.shelfCount(7)).performClick()
        assertEquals(7L, opened)
    }

    @Test
    fun listRowsShowBooksAndSwipeLeftOffersDelete() {
        var deleted: Book? = null
        val item = LibraryItem(book("a", "Optics").copy(author = "Hecht"))
        compose.setContent {
            FlowFolioTheme {
                ShelfPageContent(
                    state = ShelfPageUiState(
                        loading = false,
                        shelf = Shelf(1, "Fall Term", listOf("a")),
                        items = listOf(item)
                    ),
                    onBack = {},
                    actions = BookRowActions({}, {}, { deleted = it }, {}, {})
                )
            }
        }
        compose.onNodeWithText(res.getString(R.string.view_list)).performClick()
        compose.onNodeWithText("Optics").assertIsDisplayed()
        compose.onNodeWithText("Hecht").assertIsDisplayed()
        compose.onNodeWithTag(LibraryTestTags.row("a")).performTouchInput { swipeLeft() }
        compose.onNodeWithText(res.getString(R.string.action_delete)).performClick()
        assertEquals(item.book, deleted)
    }

    @Test
    fun shelfPageSortMenuSelectsSortMode() {
        var selectedSort: ShelfSort? = null
        val item = LibraryItem(book("a", "Optics"))
        compose.setContent {
            FlowFolioTheme {
                ShelfPageContent(
                    state = ShelfPageUiState(
                        loading = false,
                        shelf = Shelf(1, "Fall Term", listOf("a")),
                        items = listOf(item),
                        sort = ShelfSort.CUSTOM
                    ),
                    onBack = {},
                    actions = BookRowActions({}, {}, {}, {}, {}, onSort = { selectedSort = it })
                )
            }
        }
        compose.onNodeWithTag(LibraryTestTags.SHELF_SORT).performClick()
        compose.onNodeWithText(res.getString(R.string.sort_read_time)).performClick()
        assertEquals(ShelfSort.READ_TIME, selectedSort)
    }

    @Test
    fun shelvesCardShowsStatsAndFlowToggleAndScopedSearch() {
        var toggledShelf: Long? = null
        val card = ShelfCard(
            shelf = Shelf(5, "Physics", listOf("a")),
            books = listOf(book("a", "Optics")),
            annotationsCount = 4,
            bookmarksCount = 2,
            dictionaryCount = 7,
            readTimeMillis = 90 * 60_000L,
            flowActive = true
        )
        compose.setContent {
            FlowFolioTheme {
                ShelvesContent(
                    state = ShelvesUiState(loading = false, cards = listOf(card)),
                    onOpen = {},
                    onNew = {},
                    onRename = {},
                    onDelete = {},
                    onToggleFlow = { toggledShelf = it.shelf.id }
                )
            }
        }
        compose.onNodeWithText("Physics").assertIsDisplayed()
        compose.onNodeWithText("1h 30m").assertIsDisplayed()
        compose.onNodeWithTag(LibraryTestTags.shelfFlow(5)).performClick()
        assertEquals(5L, toggledShelf)
        compose.onNodeWithTag(LibraryTestTags.SEARCH).performClick()
        compose.onNodeWithTag(LibraryTestTags.SEARCH_FIELD).assertIsDisplayed()
    }
}
