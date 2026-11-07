package com.flowfolio.feature.library

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flowfolio.core.designsystem.FlowFolioTheme
import com.flowfolio.core.model.Bookmark
import com.flowfolio.core.model.BookmarkColor
import com.flowfolio.core.model.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Bookmarks tab and Settings on the JVM (Robolectric). */
@RunWith(AndroidJUnit4::class)
class BookmarksUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val res = ApplicationProvider.getApplicationContext<android.content.Context>().resources

    private val item = BookmarkItem(
        Bookmark(1, "a", 4, BookmarkColor.GREEN, 0),
        book("a", "University Physics"),
        chapter = "6.4 Drag Force and Terminal Speed",
        label = "285"
    )

    @Test
    fun noRibbonsExplainsHowToAddOne() {
        compose.setContent {
            FlowFolioTheme { BookmarksContent(BookmarksUiState(loading = false), BookmarkActions()) }
        }
        compose.onNodeWithText(res.getString(R.string.bookmarks_empty_title)).assertIsDisplayed()
    }

    @Test
    fun cardShowsPageTitleAndChapterAndOpens() {
        var opened: BookmarkItem? = null
        val state = BookmarksUiState(loading = false, items = listOf(item), total = 1, bookCount = 1)
        compose.setContent {
            FlowFolioTheme { BookmarksContent(state, BookmarkActions(onOpen = { i, _ -> opened = i })) }
        }
        compose.onNodeWithText(res.getString(R.string.bookmarks_kind, "285")).assertIsDisplayed()
        compose.onNodeWithText("6.4 Drag Force and Terminal Speed").assertIsDisplayed()
        compose.onNodeWithText("University Physics").performClick()
        assertEquals(item, opened)
    }

    @Test
    fun colourChipsFilter() {
        var chosen: BookmarkColor? = BookmarkColor.TITLE
        val state = BookmarksUiState(
            loading = false,
            items = listOf(item),
            total = 2,
            bookCount = 1,
            colours = listOf(BookmarkColor.SUB_ITEM, BookmarkColor.GREEN)
        )
        compose.setContent {
            FlowFolioTheme { BookmarksContent(state, BookmarkActions(onFilter = { chosen = it })) }
        }
        compose.onNodeWithContentDescription(res.getString(R.string.bookmark_colour_green)).performClick()
        assertEquals(BookmarkColor.GREEN, chosen)
        compose.onNodeWithText(res.getString(R.string.bookmarks_filter_all)).performClick()
        assertEquals(null, chosen)
    }

    @Test
    fun neonGlowSwitchReportsTheChange() {
        var glow: Boolean? = null
        compose.setContent {
            FlowFolioTheme { SettingsContent(UserSettings(), SettingsActions(onGlow = { glow = it })) }
        }
        compose.onNodeWithText(res.getString(R.string.settings_appearance)).performClick()
        compose.onNodeWithText(res.getString(R.string.settings_glow)).performScrollTo().performClick()
        assertEquals(false, glow)
    }
}
