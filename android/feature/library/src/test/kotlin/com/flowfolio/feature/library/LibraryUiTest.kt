package com.flowfolio.feature.library

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flowfolio.core.designsystem.FlowFolioTheme
import com.flowfolio.core.model.Availability
import com.flowfolio.core.model.Book
import com.flowfolio.core.model.Shelf
import com.flowfolio.core.model.StorageMode
import com.flowfolio.core.model.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Compose UI tests on the JVM (Robolectric). */
@RunWith(AndroidJUnit4::class)
class LibraryUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val res = ApplicationProvider.getApplicationContext<android.content.Context>().resources

    private fun content(state: LibraryUiState, actions: LibraryActions = LibraryActions()) {
        compose.setContent {
            FlowFolioTheme { LibraryContent(state = state, snackbar = SnackbarHostState(), actions = actions) }
        }
    }

    private fun loaded(vararg items: LibraryItem, shelves: List<Shelf> = emptyList(), hero: LibraryItem? = null) =
        LibraryUiState(
            loading = false,
            items = items.toList(),
            totalCount = items.size,
            shelves = shelves,
            continueReading = hero
        )

    @Test
    fun emptyLibraryExplainsHowToImport() {
        content(LibraryUiState(loading = false))
        compose.onNodeWithTag(LibraryTestTags.EMPTY).assertIsDisplayed()
        compose.onNodeWithTag(LibraryTestTags.IMPORT).assertIsDisplayed()
    }

    @Test
    fun booksShowTitlesAndMissingBadge() {
        val items = listOf(
            LibraryItem(book("a", "University Physics")),
            LibraryItem(book("b", "Linked One", linked = true), Availability.MISSING)
        )
        content(loaded(*items.toTypedArray()))
        compose.onNodeWithContentDescription("University Physics").assertIsDisplayed()
        compose.onNodeWithText(res.getString(R.string.badge_missing)).assertIsDisplayed()
    }

    @Test
    fun tapOpensAndLongPressOffersDelete() {
        var opened: LibraryItem? = null
        var deleted: Book? = null
        val item = LibraryItem(book("a", "Calculus"))
        content(loaded(item), LibraryActions(onOpen = { tapped, _ -> opened = tapped }, onDelete = { deleted = it }))
        compose.onNodeWithContentDescription("Calculus").performClick()
        assertEquals(item, opened)
        compose.onNodeWithContentDescription("Calculus").performTouchInput { longClick() }
        compose.onNodeWithText(res.getString(R.string.action_delete)).performClick()
        assertEquals(item.book, deleted)
    }

    @Test
    fun shelvesAreRowsAndLooseBooksGetTheirOwnRow() {
        val a = LibraryItem(book("a", "Anatomy"))
        val b = LibraryItem(book("b", "Biology"))
        content(loaded(a, b, shelves = listOf(Shelf(1, "Fall Term", listOf("a")))))
        compose.onNodeWithText("Fall Term").assertIsDisplayed()
        // On a narrow screen the header takes more room: scroll to the second row.
        compose.onNodeWithTag(LibraryTestTags.GRID).performScrollToNode(hasText(res.getString(R.string.shelf_loose)))
        compose.onNodeWithText(res.getString(R.string.shelf_loose)).assertIsDisplayed()
    }

    @Test
    fun continueReadingResumes() {
        var resumed: LibraryItem? = null
        val item = LibraryItem(book("a", "Optics").copy(lastPage = 4, lastOpenedAtMillis = 5))
        content(loaded(item, hero = item), LibraryActions(onResume = { resumed = it }))
        compose.onNodeWithTag(LibraryTestTags.HERO).assertIsDisplayed()
        compose.onNodeWithTag(LibraryTestTags.RESUME).performClick()
        assertEquals(item, resumed)
    }

    @Test
    fun storageChoiceShowsBothApprovedDisclaimersAndSize() {
        var chosen: StorageMode? = null
        compose.setContent {
            FlowFolioTheme {
                StorageChoiceDialog(
                    title = "Choose",
                    initial = StorageMode.COPY,
                    sizeBytes = 2_500_000,
                    showChangeLater = true,
                    onConfirm = { chosen = it },
                    onDismiss = {}
                )
            }
        }
        compose.onNodeWithText(res.getString(R.string.storage_link_disclaimer)).assertExists()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val size = android.text.format.Formatter.formatShortFileSize(context, 2_500_000)
        compose.onNodeWithText("FlowFolio stores its own copy of this book ($size)", substring = true).assertExists()
        compose.onNodeWithText(res.getString(R.string.storage_shared_note)).assertExists()
        compose.onNodeWithText(res.getString(R.string.storage_link)).performClick()
        compose.onNodeWithText(res.getString(R.string.action_continue)).performClick()
        assertEquals(StorageMode.LINK, chosen)
    }

    @Test
    fun settingsShowStorageOptionsAndAskSwitch() {
        compose.setContent {
            FlowFolioTheme { SettingsContent(UserSettings(), SettingsActions()) }
        }
        compose.onNodeWithText(res.getString(R.string.storage_copy)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(res.getString(R.string.settings_ask_each)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(res.getString(R.string.theme_dark)).performScrollTo().assertIsDisplayed()
    }
}
