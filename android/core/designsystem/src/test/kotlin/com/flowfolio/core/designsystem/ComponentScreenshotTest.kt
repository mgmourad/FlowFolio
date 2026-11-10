package com.flowfolio.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flowfolio.core.model.AppStyle
import com.flowfolio.core.model.ThemePreference
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshots of the Quiet Library components (ADR-0052) in light and dark, and of the four themes side by side.
 * Recorded and verified on the development machine (`recordRoborazziDebug`, `verifyRoborazziDebug`; ADR-0024).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w420dp-h1400dp-420dpi")
class ComponentScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun componentsLight() = shoot("components-light", ThemePreference.LIGHT, AppStyle.FLOWFOLIO)

    @Test
    fun componentsDark() = shoot("components-dark", ThemePreference.DARK, AppStyle.FLOWFOLIO)

    @Test
    fun rusticLight() = shoot("components-rustic-light", ThemePreference.LIGHT, AppStyle.RUSTIC)

    @Test
    fun rusticDark() = shoot("components-rustic-dark", ThemePreference.DARK, AppStyle.RUSTIC)

    @Test
    fun amoled() = shoot("components-amoled", ThemePreference.DARK, AppStyle.AMOLED)

    private fun shoot(name: String, mode: ThemePreference, style: AppStyle) {
        compose.mainClock.autoAdvance = false
        compose.setContent { FlowFolioTheme(mode, style = style) { Sheet() } }
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Composable
    private fun Sheet() {
        val p = FlowFolio.palette
        Column(
            Modifier.fillMaxWidth().background(p.bg).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Library", style = FlowFolioType.screenTitle.copy(color = p.ink))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowFolioChip("All", true, {}, count = 17)
                FlowFolioChip("In progress", false, {})
                FlowFolioChip("Novels", false, {}, glyph = Glyph.Book)
            }
            FlowFolioCard(Modifier.fillMaxWidth()) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    BookCover(
                        SAMPLE,
                        Modifier.width(80.dp),
                        badges = setOf(CoverBadge.DYNAMIC_FLOW, CoverBadge.FAVORITE)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("CONTINUE READING", style = FlowFolioType.eyebrow.copy(color = p.accent))
                        Text("Linear Algebra", style = FlowFolioType.heroTitle.copy(color = p.ink))
                        FlowFolioProgressBar(0.4f)
                        FlowFolioButton("Resume", {}, icon = Glyph.Play)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowFolioButton("Details", {}, style = ButtonStyle.GHOST, small = true)
                FlowFolioButton("Add books", {}, style = ButtonStyle.SOFT, small = true)
                FlowFolioButton("Delete", {}, style = ButtonStyle.DANGER, small = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FlowFolioToggle(true, {})
                FlowFolioToggle(false, {})
                FlowFolioIconButton({}) { FlowFolioGlyph(Glyph.Search, null, size = 20.dp) }
                FlowFolioIconButton({}, glowing = true) { FlowFolioGlyph(Glyph.TextSize, null, size = 20.dp) }
                SettingsTile(Glyph.Brightness)
            }
            FlowFolioSlider(0.6f, {}, "Volume")
            FlowFolioSegmented(listOf("Light", "Dark", "System"), 1, {})
            FlowFolioTabs(listOf("All", "Highlights", "Notes"), 1, {}, counts = listOf(12, 7, 5))
            FlowFolioStepper("18", {}, {}, "Text size")
            Column(Modifier.width(240.dp).background(p.surface).padding(6.dp)) {
                FlowFolioMenuHeader("Sort by")
                FlowFolioMenuItem("Recently opened", {}, checked = true)
                FlowFolioMenuDivider()
                FlowFolioMenuItem("Share file", {}, glyph = Glyph.Share, trailing = "PDF")
                FlowFolioMenuItem("Remove from library", {}, glyph = Glyph.Trash, destructive = true)
            }
            FlowFolioSnackbar(Fake("Highlight removed", "Undo"))
        }
    }

    private class Fake(message: String, action: String) : SnackbarData {
        override val visuals: SnackbarVisuals = FlowFolioSnackbarVisuals(message, Glyph.Trash, action)
        override fun dismiss() = Unit
        override fun performAction() = Unit
    }

    private companion object {
        const val SETTLE_MS = 2_000L
        val SAMPLE = BookFace(
            "Linear Algebra",
            604,
            colors = BookColors(Color(0xFF2747B8), Color(0xFF0B1640), Color(0xFFFFD27A))
        )
    }
}
