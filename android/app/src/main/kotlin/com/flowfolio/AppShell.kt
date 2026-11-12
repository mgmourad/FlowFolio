package com.flowfolio

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.sp
import com.flowfolio.core.designsystem.DisplayFamily
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
import com.flowfolio.core.designsystem.softShadow
import com.flowfolio.feature.library.OnboardingHost
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.LocalHazePerformanceMode
import dev.chrisbanes.haze.rememberHazeState

/** The four top-level destinations the tab bar (folded) and the rail (unfolded) switch between (ADR-0052). */
enum class TopLevel(val route: String, val glyph: Glyph, @StringRes val label: Int) {
    Library("library", Glyph.Library, R.string.tab_library),
    Shelves("shelves", Glyph.Shelves, R.string.tab_shelves),
    Notebook("notebook", Glyph.Notebook, R.string.tab_notebook),
    Settings("settings", Glyph.Settings, R.string.tab_settings);

    companion object {
        /** The tab [route] belongs to, arguments and retired routes included ([Destinations.tabOf]). */
        fun of(route: String?): TopLevel? = Destinations.tabOf(route)
    }
}

object ShellTestTags {
    const val TAB_BAR = "shell.tabBar"
    const val RAIL = "shell.rail"
    fun tab(destination: TopLevel) = "shell.tab.${destination.route}"
}

/**
 * The app shell (ADR-0052): screens on the app background, with a floating glass tab bar over them when folded, or a
 * rail down the left edge when the window is at least [RAIL_MIN_WIDTH] wide (unfolded, tablets): the app's mark at
 * its top, the tabs, and Settings on its own at the bottom. The selected tab sits on a soft accent pill. The chrome
 * stays on every screen and slides away only for the reader. On a page under a tab (a book's details, a shelf) that
 * tab stays lit. Screens learn the space it takes from [LocalChromePadding]; the glass blurs them through
 * [LocalGlassState].
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
            PaddingValues(start = RAIL_WIDTH)
        } else {
            PaddingValues(bottom = BAR_HEIGHT + BAR_BOTTOM)
        }
        CompositionLocalProvider(
            LocalGlassState provides haze,
            LocalChromePadding provides chrome,
            LocalScrollToTop provides scrollSignals,
            // Blurs a down-scaled copy of what is behind the glass: indistinguishable at these radii and far
            // lighter on GPU memory on the large unfolded screen (ADR-0013).
            LocalHazePerformanceMode provides HazePerformanceMode.Performance
        ) {
            // Every screen but the reader is recorded for the blur (the glass bar and the edge fades), so the reader
            // pays nothing for it. The background is drawn inside the recording too, so the fades blur it along with
            // the content instead of filling a flat band where the screen has nothing of its own.
            Box(
                Modifier.fillMaxSize().then(if (reading) Modifier else Modifier.glassSource().appBackground(p))
            ) { content() }
            // The status-bar fade (ADR-0052): content scrolling up never shows through under the clock. Folded, the
            // bottom fades behind the tab bar too; unfolded the rail leaves the bottom edge clear.
            val fades by animateFloatAsState(if (reading) 0f else 1f, tween(Motion.FAST_MS), label = "edgeFades")
            if (fades > 0f) EdgeFades(fades, Routes.topFadeOf(route), bottom = !wide)
            val selected = current ?: lastTab
            if (wide) {
                AnimatedVisibility(
                    visible = shown,
                    enter = slideInHorizontally(tween(HIDE_MS, easing = Motion.Emphasized)) { -it } +
                        fadeIn(tween(HIDE_MS / 2)),
                    exit = slideOutHorizontally(tween(HIDE_MS * 2 / 3, easing = Motion.EmphasizedExit)) { -it } +
                        fadeOut(tween(HIDE_MS / 2))
                ) { Rail(selected, select) }
            } else {
                AnimatedVisibility(
                    visible = shown,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = slideInVertically(tween(HIDE_MS, easing = Motion.Emphasized)) { it * 3 / 2 },
                    exit = slideOutVertically(tween(HIDE_MS * 2 / 3, easing = Motion.EmphasizedExit)) { it * 3 / 2 }
                ) { TabBar(selected, select) }
            }
        }
        // Over the bars and everything else: the app's own tour, the first time it opens (owner, M5 round 4).
        OnboardingHost()
        // After the tour, once: the optional downloads (TM2), its own window since most people skip the tour.
        com.flowfolio.feature.library.ResourcesOfferHost()
    }
}

/** `.tabbar`: four tabs on glass, 64 dp tall and fully round, floating above the bottom edge. */
@Composable
private fun TabBar(selected: TopLevel, onSelect: (TopLevel) -> Unit) {
    Row(
        Modifier
            .navigationBarsPadding()
            .padding(start = BAR_SIDE, end = BAR_SIDE, bottom = BAR_BOTTOM)
            .fillMaxWidth()
            .height(BAR_HEIGHT)
            .glass(RoundedCornerShape(BAR_HEIGHT / 2))
            .testTag(ShellTestTags.TAB_BAR),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TopLevel.entries.forEach {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                TabItem(it, it == selected, onSelect, Modifier.fillMaxWidth())
            }
        }
    }
}

/**
 * `.rail`: a column down the left edge on a surface tint with a hairline at its right; the app's mark at the top,
 * the browsing tabs under it, and Settings at the bottom.
 */
@Composable
private fun Rail(selected: TopLevel, onSelect: (TopLevel) -> Unit) {
    val p = FlowFolio.palette
    Column(
        Modifier
            .width(RAIL_WIDTH)
            .fillMaxHeight()
            .drawBehind {
                drawRect(lerp(p.background, p.surface, RAIL_TINT))
                drawRect(p.edge, Offset(size.width - 1.dp.toPx(), 0f), size.copy(width = 1.dp.toPx()))
            }
            .safeDrawingPadding()
            .padding(top = 16.dp, bottom = 18.dp)
            .testTag(ShellTestTags.RAIL),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AppMark(Modifier.padding(bottom = 18.dp))
        TopLevel.entries.filter { it != TopLevel.Settings }.forEach {
            TabItem(it, it == selected, onSelect, Modifier.width(RAIL_ITEM))
        }
        Spacer(Modifier.weight(1f))
        TabItem(TopLevel.Settings, selected == TopLevel.Settings, onSelect, Modifier.width(RAIL_ITEM))
    }
}

/** The app's mark at the top of the rail (`.rail .logo`): the app icon itself, softly lifted (owner, 2026-11-10). */
@Composable
private fun AppMark(modifier: Modifier = Modifier) {
    val p = FlowFolio.palette
    val shape = RoundedCornerShape(13.dp)
    androidx.compose.foundation.Image(
        painter = androidx.compose.ui.res.painterResource(com.flowfolio.core.designsystem.R.drawable.flowfolio_icon),
        contentDescription = null,
        modifier = modifier
            .softShadow(shape, p.accent.copy(alpha = MARK_SHADOW), blur = 16.dp, offsetY = 6.dp)
            .size(MARK_SIZE)
            .clip(shape)
            .clearAndSetSemantics {}
    )
}

/**
 * `.nv`: the tab's glyph on a pill and its name under it, in the third ink. Selected, the pill fills with the soft
 * accent and grows out from its centre on a spring, the glyph turns accent and the name ink. TalkBack reads the name;
 * a long press shows it as a tooltip.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabItem(destination: TopLevel, selected: Boolean, onSelect: (TopLevel) -> Unit, modifier: Modifier) {
    val p = FlowFolio.palette
    val pill by animateFloatAsState(if (selected) 1f else 0f, Motion.bouncySpec(), label = "tabPill")
    val glyph by animateColorAsState(if (selected) p.accent else p.ink3, Motion.colorSpec(), label = "tabGlyph")
    val ink by animateColorAsState(if (selected) p.ink else p.ink3, Motion.colorSpec(), label = "tabInk")
    val name = stringResource(destination.label)
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = {
            PlainTooltip(containerColor = p.ink, contentColor = p.bg) { Text(name, style = FlowFolioType.tab) }
        },
        state = rememberTooltipState(),
        modifier = modifier
    ) {
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
                .padding(vertical = 7.dp)
                .testTag(ShellTestTags.tab(destination)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TabPill(destination.glyph, pill, glyph)
            Text(
                name,
                style = FlowFolioType.tab.copy(
                    color = ink,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                ),
                maxLines = 1,
                softWrap = false,
                autoSize = TextAutoSize.StepBased(minFontSize = TAB_MIN_SP, maxFontSize = FlowFolioType.tab.fontSize),
                modifier = Modifier.clearAndSetSemantics {}
            )
        }
    }
}

/** The tab's glyph on its pill; [pill] (0 to 1) is how far the soft accent has grown out from the centre. */
@Composable
private fun TabPill(glyph: Glyph, pill: Float, color: androidx.compose.ui.graphics.Color) {
    val p = FlowFolio.palette
    Box(
        Modifier
            .size(width = PILL_WIDTH, height = PILL_HEIGHT)
            .drawBehind {
                if (pill > 0f) {
                    val w = size.width * pill
                    drawRoundRect(
                        p.accentSoft.copy(alpha = pill.coerceIn(0f, 1f)),
                        topLeft = Offset((size.width - w) / 2f, 0f),
                        size = size.copy(width = w),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f)
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        FlowFolioGlyph(glyph, contentDescription = null, color = color, size = TAB_GLYPH, glow = false)
    }
}

private val RAIL_MIN_WIDTH = 600.dp
private val BAR_HEIGHT = 64.dp
private val BAR_BOTTOM = 14.dp
private val BAR_SIDE = 12.dp
private val RAIL_WIDTH = 84.dp
private val RAIL_ITEM = 72.dp
private val PILL_WIDTH = 54.dp
private val PILL_HEIGHT = 32.dp
private val TAB_MIN_SP = 9.sp
private val TAB_GLYPH = 22.dp
private const val RAIL_TINT = 0.6f
private val MARK_SIZE = 44.dp
private const val MARK_SHADOW = 0.35f
private const val HIDE_MS = 420
