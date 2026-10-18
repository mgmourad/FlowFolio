package com.flowfolio.feature.library

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flowfolio.core.designsystem.FlowFolioTheme
import com.flowfolio.core.model.BookDetails
import com.flowfolio.core.model.BookType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Slice 6 on the JVM (Robolectric): the Author tab, time read and Edit details. */
@RunWith(AndroidJUnit4::class)
class BookDetailsUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val res = ApplicationProvider.getApplicationContext<android.content.Context>().resources

    private val physics = book("p", "University Physics").copy(
        author = "Dr. Samuel Ling",
        details = BookDetails(authorRole = "Professor of Physics", authorBio = "Taught physics for decades.")
    )

    @Test
    fun authorTabShowsInitialsRoleBioAndOtherBooks() {
        val other = book("q", "Volume 2").copy(author = "Dr. Samuel Ling")
        var opened: String? = null
        compose.setContent {
            FlowFolioTheme {
                AuthorPanel(DetailsUiState(loading = false, book = physics, sameAuthor = listOf(other)), physics) {
                    opened = it.id
                }
            }
        }
        compose.onNodeWithText("SL").assertIsDisplayed()
        compose.onNodeWithText("Professor of Physics").assertIsDisplayed()
        compose.onNodeWithText("Taught physics for decades.").assertIsDisplayed()
        compose.onNodeWithText(res.getString(R.string.author_more).uppercase()).assertIsDisplayed()
        assertEquals(null, opened)
    }

    @Test
    fun initialsSkipTitles() {
        assertEquals("AO", initials("Dr. Amara Okafor"))
        assertEquals("HV", initials("Henrik Vale"))
        assertEquals("P", initials("Plato"))
    }

    @Test
    fun overviewShowsTimeRead() {
        compose.setContent {
            FlowFolioTheme {
                OverviewPanel(
                    DetailsUiState(
                        loading = false,
                        book = physics,
                        readMillis =
                            2 * 3_600_000L + 300_000L
                    ),
                    physics
                )
            }
        }
        compose.onNodeWithText("2h 05m").assertIsDisplayed()
    }

    @Test
    fun editDetailsSavesWhatWasTyped() {
        var saved: Triple<String, String?, BookDetails>? = null
        compose.setContent {
            FlowFolioTheme { EditDetailsContent(physics, onCancel = {}) { t, a, d -> saved = Triple(t, a, d) } }
        }
        compose.onNodeWithText(res.getString(R.string.edit_subtitle)).performTextReplacement("Volume 1")
        compose.onNodeWithText(res.getString(R.string.type_textbook)).performScrollTo().performClick()
        compose.onNodeWithTag(LibraryTestTags.SAVE_DETAILS).performScrollTo().performClick()
        val (title, author, details) = saved!!
        assertEquals("University Physics", title)
        assertEquals("Dr. Samuel Ling", author)
        assertEquals("Volume 1", details.subtitle)
        assertEquals(BookType.TEXTBOOK, details.type)
        assertEquals("Taught physics for decades.", details.authorBio)
    }
}
