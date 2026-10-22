package com.flowfolio.feature.library

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flowfolio.core.designsystem.FlowFolioTheme
import com.flowfolio.core.model.LineSpacing
import com.flowfolio.core.model.ReaderText
import com.flowfolio.core.model.ReadingFont
import com.flowfolio.core.model.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Settings > Text (M3) on the JVM (Robolectric): each control reports the whole new text setting. */
@RunWith(AndroidJUnit4::class)
class SettingsTextUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val res = ApplicationProvider.getApplicationContext<android.content.Context>().resources

    private fun show(text: ReaderText = ReaderText()): () -> ReaderText? {
        var changed: ReaderText? = null
        compose.setContent {
            FlowFolioTheme {
                SettingsContent(UserSettings(text = text), SettingsActions(onReaderText = { changed = it }))
            }
        }
        return { changed }
    }

    @Test
    fun largerTextStepsOneSize() {
        val changed = show()
        compose.onNodeWithContentDescription(res.getString(R.string.settings_text_larger)).performScrollTo()
            .performClick()
        assertEquals(ReaderText(sizeSp = ReaderText.DEFAULT_SIZE_SP + 1), changed())
    }

    @Test
    fun aFontCardChoosesThatFont() {
        val changed = show()
        compose.onNodeWithTag(LibraryTestTags.TEXT_FONT).performScrollTo()
        compose.onNodeWithText("Charter").performClick()
        assertEquals(ReadingFont.CHARTER, changed()?.font)
    }

    @Test
    fun lineSpacingAndJustifyKeepTheRest() {
        val changed = show(ReaderText(sizeSp = 20))
        compose.onNodeWithText(res.getString(R.string.spacing_loose)).performScrollTo().performClick()
        assertEquals(ReaderText(sizeSp = 20, lineSpacing = LineSpacing.LOOSE), changed())
        compose.onNodeWithText(res.getString(R.string.settings_text_justify)).performScrollTo().performClick()
        assertEquals(ReaderText(sizeSp = 20, justify = false), changed())
    }
}
