package com.flowfolio.feature.reader.riffle

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioTheme
import com.flowfolio.core.model.ThemePreference
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshots of the riffle dock (ADR-0024): at rest, mid-riffle, with stacked flags, and in single pages, in dark and
 * light. The frame clock is driven by hand, so every image is the same on every run. Reference images are recorded and
 * verified on the development machine (`recordRoborazziDebug`, `verifyRoborazziDebug`); see ADR-0024 for CI.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w840dp-h500dp-420dpi")
class RiffleScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    /** [tall]: room above the dock for the loupe, as in the reader where the dock sits at the bottom. */
    private fun show(dark: Boolean, state: RiffleScrubberState, tall: Boolean = false) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            FlowFolioTheme(if (dark) ThemePreference.DARK else ThemePreference.LIGHT) {
                val space = if (tall) Modifier.height(LOUPE_ROOM.dp) else Modifier
                Box(space.background(FlowFolio.palette.background), contentAlignment = Alignment.BottomCenter) {
                    RiffleDock(state, BOARDS, Modifier.fillMaxWidth())
                }
            }
        }
        compose.mainClock.advanceTimeBy(FRAMES_MS)
    }

    private fun sample(single: Boolean = false, flags: List<RiffleFlag>? = null): RiffleScrubberState {
        val book = SampleBook(singlePages = single)
        return RiffleScrubberState(book, book.unitOf(PAGE_216)).also { state ->
            state.flags = flags ?: FLAGS.mapIndexed { i, page -> RiffleFlag(i.toLong(), page, FLAG_COLORS[i]) }
        }
    }

    private fun capture(name: String) = compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")

    private fun riffleTo(state: RiffleScrubberState, fraction: Float) {
        val g = requireNotNull(state.geometry)
        state.riffleX = g.x0 + fraction * (g.x1 - g.x0)
        compose.mainClock.advanceTimeBy(RIFFLE_MS)
    }

    @Test
    fun atRestDark() {
        show(dark = true, sample())
        capture("riffle_rest_dark")
    }

    @Test
    fun atRestLight() {
        show(dark = false, sample())
        capture("riffle_rest_light")
    }

    @Test
    fun midRiffleDark() {
        val state = sample()
        show(dark = true, state, tall = true)
        riffleTo(state, 0.7f)
        capture("riffle_mid_dark")
    }

    @Test
    fun midRiffleLight() {
        val state = sample()
        show(dark = false, state, tall = true)
        riffleTo(state, 0.7f)
        capture("riffle_mid_light")
    }

    @Test
    fun stackedFlagsDark() {
        // Six bookmarks within a few pages of each other stack into two flags with counts; one stands alone.
        val close = listOf(300, 302, 304, 306, 340, 346, 600)
        show(dark = true, sample(flags = close.mapIndexed { i, p -> RiffleFlag(i.toLong(), p, FLAG_COLORS[i % 5]) }))
        capture("riffle_stacks_dark")
    }

    @Test
    fun singlePagesLight() {
        show(dark = false, sample(single = true))
        capture("riffle_single_light")
    }

    private companion object {
        const val PAGE_216 = 215
        const val FRAMES_MS = 500L
        const val RIFFLE_MS = 600L
        const val LOUPE_ROOM = 440
        val FLAGS = listOf(87, 232, 432, 589, 701)
        val FLAG_COLORS = listOf(0xFF00F0FF, 0xFFFF8A00, 0xFFC02F9A, 0xFF2ECC8A, 0xFFFFD166).map { it.toInt() }
        val BOARDS = BoardColors(main = Color(0xFF0F766E), dark = Color(0xFF064E3B))
    }
}
