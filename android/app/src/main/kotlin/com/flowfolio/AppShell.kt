package com.flowfolio

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioGlyph
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.Glyph
import com.flowfolio.core.designsystem.LocalChromePadding
import com.flowfolio.core.designsystem.LocalGlassState
import com.flowfolio.core.designsystem.Motion
import com.flowfolio.core.designsystem.appBackground
import com.flowfolio.core.designsystem.glass
import com.flowfolio.core.designsystem.glassSource
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.LocalHazePerformanceMode
import dev.chrisbanes.haze.rememberHazeState

/** The four top-level destinations the tab bar (folded) and the rail (unfolded) switch between. */
enum class TopLevel(val route: String, val glyph: Glyph, @StringRes val label: Int) {
    Library("library", Glyph.Library, R.string.tab_library),
    Shelves("shelves", Glyph.Shelves, R.string.tab_shelves),
    Bookmarks("bookmarks", Glyph.Bookmark, R.string.tab_bookmarks),
    Settings("settings", Glyph.Settings, R.string.tab_settings);

    companion object {
        fun of(route: String?): TopLevel? = entries.firstOrNull { it.route == route }
    }
}

object ShellTestTags {
    const val TAB_BAR = "shell.tabBar"
    const val RAIL = "shell.rail"
    fun tab(destination: TopLevel) = "shell.tab.${destination.route}"
}

/**
 * The app shell from the mock: screens on the app background, with the floating glass tab bar (`.tabbar`) over them
 * when folded, or a glass rail on the left when the window is at least [RAIL_MIN_WIDTH] wide (unfolded, tablets).
 * The chrome shows only on top-level screens ([current] is one of [TopLevel]) and slides away elsewhere (the reader).
 * Screens learn the space it takes from [LocalChromePadding]; the glass blurs them through [LocalGlassState].
 */
@Composable
fun AppShell(current: TopLevel?, onSelect: (TopLevel) -> Unit, content: @Composable () -> Unit) {
    val p = FlowFolio.palette
    // Exposes Compose test tags as resource ids so UiAutomator (on-device gesture tests, benchmarks) can find them.
    BoxWithConstraints(Modifier.fillMaxSize().appBackground(p).semantics { testTagsAsResourceId = true }) {
        val wide = maxWidth >= RAIL_MIN_WIDTH
        val shown = current != null
        val haze = rememberHazeState()
        val chrome = when {
            !shown -> PaddingValues()
            wide -> PaddingValues(start = RAIL_WIDTH + CHROME_INSET)
            else -> PaddingValues(bottom = BAR_HEIGHT + BAR_BOTTOM)
        }
        CompositionLocalProvider(
            LocalGlassState provides haze,
            LocalChromePadding provides chrome,
            // Blurs a down-scaled copy of what is behind the glass: indistinguishable at these radii and far
            // lighter on GPU memory on the large unfolded screen (ADR-0013).
            LocalHazePerformanceMode provides HazePerformanceMode.Performance
        ) {
            // Only top-level screens are recorded for the blur, so the reader pays nothing for it.
            Box(Modifier.fillMaxSize().then(if (shown) Modifier.glassSource() else Modifier)) { content() }
            val selected = current ?: TopLevel.Library
            if (wide) {
                AnimatedVisibility(
                    visible = shown,
                    enter = slideInHorizontally(tween(HIDE_MS, easing = Motion.EaseOut)) { -it * 3 / 2 },
                    exit = slideOutHorizontally(tween(HIDE_MS, easing = Motion.EaseOut)) { -it * 3 / 2 }
                ) { Rail(selected, onSelect) }
            } else {
                AnimatedVisibility(
                    visible = shown,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = slideInVertically(tween(HIDE_MS, easing = Motion.EaseOut)) { it * 3 / 2 },
                    exit = slideOutVertically(tween(HIDE_MS, easing = Motion.EaseOut)) { it * 3 / 2 }
                ) { TabBar(selected, onSelect) }
            }
        }
    }
}

@Composable
private fun TabBar(selected: TopLevel, onSelect: (TopLevel) -> Unit) {
    Row(
        Modifier
            .navigationBarsPadding()
            .padding(start = CHROME_INSET, end = CHROME_INSET, bottom = BAR_BOTTOM)
            .fillMaxWidth()
            .height(BAR_HEIGHT)
            .glass(RoundedCornerShape(BAR_RADIUS), tint = FlowFolio.palette.tabBar, blur = BAR_BLUR)
            .testTag(ShellTestTags.TAB_BAR),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TopLevel.entries.forEach { TabItem(it, it == selected, onSelect) }
    }
}

@Composable
private fun Rail(selected: TopLevel, onSelect: (TopLevel) -> Unit) {
    Column(
        Modifier
            .safeDrawingPadding()
            .padding(start = CHROME_INSET, top = CHROME_INSET, bottom = CHROME_INSET)
            .width(RAIL_WIDTH)
            .fillMaxHeight()
            .glass(RoundedCornerShape(BAR_RADIUS), tint = FlowFolio.palette.tabBar, blur = BAR_BLUR)
            .testTag(ShellTestTags.RAIL),
        // The tabs share the rail's whole height, so it has no empty stretch at the top or bottom.
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TopLevel.entries.forEach { TabItem(it, it == selected, onSelect) }
    }
}

/** `.tab`: glyph over label in the hint colour; the selected tab turns title-coloured, glows and lifts (spring). */
@Composable
private fun TabItem(destination: TopLevel, selected: Boolean, onSelect: (TopLevel) -> Unit) {
    val lift by animateFloatAsState(
        if (selected) 1f else 0f,
        tween(Motion.SPRING_MS, easing = Motion.Spring),
        label = "tab"
    )
    val style = if (selected) FlowFolio.title else FlowFolio.hint
    val color = if (selected) FlowFolio.palette.title else FlowFolio.palette.hint
    Column(
        Modifier
            .width(TAB_WIDTH)
            .selectable(
                selected = selected,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab
            ) { onSelect(destination) }
            .testTag(ShellTestTags.tab(destination)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(TAB_GAP)
    ) {
        FlowFolioGlyph(
            destination.glyph,
            contentDescription = null,
            color = color,
            size = TAB_GLYPH,
            glow = selected,
            modifier = Modifier.glowLayer {
                translationY = -TAB_LIFT.toPx() * lift
                val s = 1f + (TAB_SCALE - 1f) * lift
                scaleX = s
                scaleY = s
                // The glow is rasterized once at rest size and the layer is scaled, so the lift leaves no trail of
                // glow textures in the GPU cache.
                compositingStrategy = CompositingStrategy.Offscreen
            }
        )
        Text(stringResource(destination.label), style = FlowFolioType.tab.merge(style))
    }
}

/**
 * A graphics layer that reaches [TAB_GLOW_ROOM] past the element on every side without changing the layout, so an
 * offscreen layer keeps the glow that spills outside the glyph.
 */
private fun Modifier.glowLayer(block: GraphicsLayerScope.() -> Unit): Modifier = layout { measurable, constraints ->
    val r = TAB_GLOW_ROOM.roundToPx()
    val placeable = measurable.measure(constraints.offset(2 * r, 2 * r))
    layout(placeable.width - 2 * r, placeable.height - 2 * r) { placeable.place(-r, -r) }
}.graphicsLayer(block).padding(TAB_GLOW_ROOM)

private val RAIL_MIN_WIDTH = 600.dp
private val BAR_HEIGHT = 68.dp
private val BAR_RADIUS = 28.dp
private val BAR_BOTTOM = 20.dp
private val BAR_BLUR = 22.dp
private val CHROME_INSET = 14.dp
private val RAIL_WIDTH = 84.dp
private val TAB_WIDTH = 70.dp
private val TAB_GAP = 4.dp
private val TAB_GLYPH = 23.dp
private val TAB_LIFT = 2.dp
private val TAB_GLOW_ROOM = 10.dp
private const val TAB_SCALE = 1.08f
private const val HIDE_MS = 600
