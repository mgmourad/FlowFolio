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
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.sp
import com.flowfolio.core.designsystem.EdgeFades
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioGlyph
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.Glyph
import com.flowfolio.core.designsystem.LocalChromePadding
import com.flowfolio.core.designsystem.LocalGlassState
import com.flowfolio.core.designsystem.LocalScrollToTop
import com.flowfolio.core.designsystem.Motion
import com.flowfolio.core.designsystem.ScrollToTopSignals
import com.flowfolio.core.designsystem.appBackground
import com.flowfolio.core.designsystem.glass
import com.flowfolio.core.designsystem.glassSource
import com.flowfolio.feature.library.OnboardingHost
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.LocalHazePerformanceMode
import dev.chrisbanes.haze.rememberHazeState

/** The four top-level destinations the tab bar (folded) and the rail (unfolded) switch between. */
enum class TopLevel(val route: String, val glyph: Glyph, @StringRes val label: Int) {
    Library("library", Glyph.Library, R.string.tab_library),
    Shelves("shelves", Glyph.Shelves, R.string.tab_shelves),
    Favorites("favorites", Glyph.Star, R.string.tab_favorites),
    Bookmarks("bookmarks", Glyph.Bookmark, R.string.tab_bookmarks),
    Annotations("annotations", Glyph.Highlight, R.string.tab_annotations),
    Dictionary("dictionary", Glyph.Translate, R.string.tab_dictionary),
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
 * The chrome stays on every screen, so page titles always sit in the same place (owner, M3T1), and slides away only
 * for the reader. On a page under a tab (a book's details, a shelf) that tab stays lit. Screens learn the space it
 * takes from [LocalChromePadding]; the glass blurs them through [LocalGlassState].
 */
@Composable
fun AppShell(route: String?, onSelect: (TopLevel) -> Unit, content: @Composable () -> Unit) {
    val current = TopLevel.of(route)
    // A tab tap shows its page from the top, even when that page is the one already showing (owner, M4c).
    val scrollSignals = remember { ScrollToTopSignals() }
    val select: (TopLevel) -> Unit = { destination ->
        scrollSignals.tapped(destination.route)
        onSelect(destination)
    }
    val reading = Routes.isReader(route)
    val p = FlowFolio.palette
    var lastTab by rememberSaveable { mutableStateOf(TopLevel.Library) }
    if (current != null) lastTab = current
    // Exposes Compose test tags as resource ids so UiAutomator (on-device gesture tests, benchmarks) can find them.
    BoxWithConstraints(Modifier.fillMaxSize().appBackground(p).semantics { testTagsAsResourceId = true }) {
        val wide = maxWidth >= RAIL_MIN_WIDTH
        val shown = !reading
        val haze = rememberHazeState()
        // The space the bar or rail takes, whether or not it is showing: a top-level screen keeps its layout while
        // the rail slides away as a book opens, instead of shifting under it (owner, 2026-10-18: no "kick").
        val chrome = if (wide) {
            PaddingValues(start = RAIL_WIDTH + CHROME_INSET)
        } else {
            PaddingValues(
                bottom =
                    BAR_HEIGHT + BAR_BOTTOM
            )
        }
        CompositionLocalProvider(
            LocalGlassState provides haze,
            LocalChromePadding provides chrome,
            LocalScrollToTop provides scrollSignals,
            // Blurs a down-scaled copy of what is behind the glass: indistinguishable at these radii and far
            // lighter on GPU memory on the large unfolded screen (ADR-0013).
            LocalHazePerformanceMode provides HazePerformanceMode.Performance
        ) {
            // Every screen but the reader is recorded for the blur (the glass bars and the edge fades), so the reader
            // pays nothing for it.
            // The background is drawn inside the recording too, so the fades blur it along with the content instead
            // of filling a flat band where the screen has nothing of its own.
            Box(
                Modifier.fillMaxSize().then(if (reading) Modifier else Modifier.glassSource().appBackground(p))
            ) { content() }
            // The themed, blurred fades under the status bar and over the navigation bar (owner, 2026-10-23); the
            // reader's pages fill the screen instead.
            val fades by animateFloatAsState(if (reading) 0f else 1f, tween(Motion.FAST_MS), label = "edgeFades")
            if (fades > 0f) EdgeFades(fades, Routes.topFadeOf(route))
            val selected = current ?: lastTab
            if (wide) {
                AnimatedVisibility(
                    visible = shown,
                    enter = slideInHorizontally(tween(HIDE_MS, easing = Motion.EaseOut)) { -it * 3 / 2 },
                    exit = slideOutHorizontally(tween(HIDE_MS, easing = Motion.EaseOut)) { -it * 3 / 2 }
                ) { Rail(selected, select) }
            } else {
                AnimatedVisibility(
                    visible = shown,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = slideInVertically(tween(HIDE_MS, easing = Motion.EaseOut)) { it * 3 / 2 },
                    exit = slideOutVertically(tween(HIDE_MS, easing = Motion.EaseOut)) { it * 3 / 2 }
                ) { TabBar(selected, select) }
            }
        }
        // Over the bars and everything else: the app's own tour, the first time it opens (owner, M5 round 4).
        OnboardingHost()
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
        // Equal shares of the bar, so the last tab never runs off its edge (owner, 2026-10-22).
        TopLevel.entries.forEach {
            // Each tab its share of the bar, the tab filling it (centred, whatever its own content's width).
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                TabItem(it, it == selected, onSelect, Modifier.fillMaxWidth())
            }
        }
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
        // Grouped in the middle with a generous gap (owner, 2026-10-18: spread out, then a little closer).
        verticalArrangement = Arrangement.spacedBy(RAIL_GAP, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TopLevel.entries.forEach { TabItem(it, it == selected, onSelect, showName = true) }
    }
}

/**
 * `.tab`: a glyph in the hint colour; the selected tab turns title-coloured, glows and lifts (spring). Glyphs only
 * (owner, M3T1): the tab's name is what TalkBack reads, and shows as a tooltip on a long press.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabItem(
    destination: TopLevel,
    selected: Boolean,
    onSelect: (TopLevel) -> Unit,
    modifier: Modifier = Modifier.width(TAB_WIDTH),
    showName: Boolean = false
) {
    val lift by animateFloatAsState(
        if (selected) 1f else 0f,
        tween(Motion.SPRING_MS, easing = Motion.Spring),
        label = "tab"
    )
    val color = if (selected) FlowFolio.palette.title else FlowFolio.palette.hint
    val name = stringResource(destination.label)
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(name, style = FlowFolioType.tab) } },
        state = rememberTooltipState(),
        modifier = modifier
    ) {
        // Centred in the tab's width; on the rail (unfolded), where there is room, its name under it (owner, M3T1).
        Column(
            Modifier
                .fillMaxWidth()
                .selectable(
                    selected = selected,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Tab
                ) { onSelect(destination) }
                .semantics(mergeDescendants = true) { contentDescription = name }
                .padding(vertical = TAB_PAD)
                .testTag(ShellTestTags.tab(destination)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(TAB_NAME_GAP)
        ) {
            TabGlyph(destination, color, selected, lift)
            if (showName) {
                Text(
                    name,
                    style = FlowFolioType.tab.merge(if (selected) FlowFolio.title else FlowFolio.hint),
                    maxLines = 1,
                    softWrap = false,
                    autoSize = TextAutoSize.StepBased(
                        minFontSize = TAB_MIN_SP,
                        maxFontSize = FlowFolioType.tab.fontSize
                    ),
                    modifier = Modifier.clearAndSetSemantics {}
                )
            }
        }
    }
}

@Composable
private fun TabGlyph(destination: TopLevel, color: androidx.compose.ui.graphics.Color, selected: Boolean, lift: Float) {
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
private val RAIL_GAP = 32.dp
private val TAB_WIDTH = 70.dp
private val TAB_PAD = 10.dp
private val TAB_NAME_GAP = 4.dp
private val TAB_MIN_SP = 8.sp
private val TAB_GLYPH = 26.dp
private val TAB_LIFT = 2.dp
private val TAB_GLOW_ROOM = 10.dp
private const val TAB_SCALE = 1.08f
private const val HIDE_MS = 600
