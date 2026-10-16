package com.flowfolio.ui.library

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flowfolio.ui.theme.FlowFolioTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Compose UI test that runs on the JVM via Robolectric (no emulator needed). */
@RunWith(AndroidJUnit4::class)
class LibraryScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun emptyState_showsEmptyMessage() {
        compose.setContent { FlowFolioTheme { LibraryScreen(LibraryUiState()) } }
        compose.onNodeWithTag(LibraryTestTags.EMPTY).assertIsDisplayed()
    }

    @Test
    fun withBooks_showsTitles() {
        val state = LibraryUiState(listOf(BookSummary("1", "University Physics")))
        compose.setContent { FlowFolioTheme { LibraryScreen(state) } }
        compose.onNodeWithText("University Physics").assertIsDisplayed()
    }
}
