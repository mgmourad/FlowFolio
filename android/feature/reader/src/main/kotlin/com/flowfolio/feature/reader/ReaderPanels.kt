package com.flowfolio.feature.reader

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.Motion
import com.flowfolio.core.designsystem.glass

/**
 * Catches taps outside an open panel and closes it (and Back closes it), so a tap meant to dismiss never turns a
 * page. Sits under the bars, so they stay usable.
 */
@Composable
internal fun BoxScope.PanelDismiss(panel: Panel, onDismiss: () -> Unit) {
    BackHandler(enabled = panel != Panel.NONE, onBack = onDismiss)
    if (panel != Panel.NONE) {
        Box(Modifier.matchParentSize().pointerInput(Unit) { detectTapGestures { onDismiss() } })
    }
}

/**
 * The reader's panels, on glass that blurs the page behind (they are drawn in the reader's own window: a sheet or a
 * dialog is a separate window, which glass cannot see through). Contents, Go to page and the ribbon colours drop from
 * under the top bar; the reading sheet ("Aa") rises from the bottom like the mock's `.sheet`.
 */
@Composable
internal fun BoxScope.ReaderPanels(
    panel: Panel,
    s: ReaderState.Ready,
    navigation: BookNavigation,
    mark: ChromeMark,
    jump: (Int) -> Unit,
    onDismiss: () -> Unit,
    flow: FlowState? = null,
    onFlow: ((Boolean, Boolean) -> Unit)? = null,
    onPanel: (Panel) -> Unit = {},
    hits: List<SearchHit> = emptyList(),
    onSearchQuery: (String) -> Unit = {},
    onSearchJump: (SearchHit) -> Unit = {},
    onSearchClear: () -> Unit = {}
) {
    val wide = LocalWindowInfo.current.containerDpSize.width >= TITLE_BESIDE_BACK
    if (wide) {
        CenteredPanel(panel == Panel.CONTENTS, CONTENTS_WIDTH) {
            ContentsList(navigation.outline, navigation.labels, s.anchorPage, jump) { onPanel(Panel.GO_TO) }
        }
    } else {
        DropPanel(panel == Panel.CONTENTS, CONTENTS_WIDTH, fillDown = true) {
            ContentsList(navigation.outline, navigation.labels, s.anchorPage, jump) { onPanel(Panel.GO_TO) }
        }
    }
    CenteredPanel(panel == Panel.DICTIONARY, CONTENTS_WIDTH) {
        ReaderDictionaryPanel(navigation, jump)
    }
    DropPanel(panel == Panel.ANNOTATIONS, CONTENTS_WIDTH, fillDown = true) {
        AnnotationList(mark.notes, navigation, jump)
    }
    DropPanel(panel == Panel.GO_TO, GO_TO_WIDTH) {
        GoToForm(navigation.labels, s.pageCount, jump, onDismiss)
    }
    DropPanel(panel == Panel.MARK, RIBBONS_WIDTH) {
        ColourStrip(mark) { chosen ->
            if (chosen == null) mark.onUnmark() else mark.onMark(chosen)
            onDismiss()
        }
        RibbonList(mark.all, navigation, jump)
    }
    SheetPanel(panel == Panel.SETTINGS) { ReaderSettingsContent(s.reflowable, s.bookFont, flow, onFlow) }
    DropPanel(panel == Panel.SEARCH, SEARCH_WIDTH, fillDown = true) {
        ReaderSearchPanel(hits, onSearchQuery, onSearchJump, onSearchClear)
    }
}

/**
 * A glass panel centered in the middle of the screen (for unfolded Contents and the Dictionary popup), keeping clear
 * of the top control buttons and bottom bar.
 */
@Composable
private fun BoxScope.CenteredPanel(visible: Boolean, width: Dp, content: @Composable ColumnScope.() -> Unit) {
    val maxHeight = LocalWindowInfo.current.containerDpSize.height * CENTERED_MAX_HEIGHT
    AnimatedVisibility(
        visible,
        Modifier
            .align(Alignment.Center)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = PANEL_INSET, vertical = CENTERED_VERTICAL_CLEARANCE)
            .imePadding(),
        enter = fadeIn(tween(Motion.FAST_MS)) +
            slideInVertically(tween(Motion.MEDIUM_MS, easing = Motion.EaseOut)) { -it / DROP_FRACTION },
        exit = fadeOut(tween(Motion.FAST_MS)) +
            slideOutVertically(tween(Motion.FAST_MS, easing = Motion.EaseOut)) { -it / DROP_FRACTION }
    ) {
        Column(
            Modifier
                .widthIn(max = width)
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .glass(PanelShape, tint = panelTint())
                .padding(vertical = 4.dp),
            content = content
        )
    }
}

/**
 * A glass panel under the top bar, at the right (under the buttons that open it). [fillDown] stretches it to just above
 * the bottom bar; [width] caps it on wide screens (null: as wide as its content).
 */
@Composable
private fun BoxScope.DropPanel(
    visible: Boolean,
    width: Dp?,
    fillDown: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val wide = LocalWindowInfo.current.containerDpSize.width >= TITLE_BESIDE_BACK
    val topClearance = if (wide) WIDE_TOP_BAR_CLEARANCE else FOLDED_TOP_BAR_CLEARANCE
    AnimatedVisibility(
        visible,
        Modifier
            .align(Alignment.TopEnd)
            .statusBarsPadding()
            .padding(start = PANEL_INSET, end = PANEL_INSET, top = topClearance)
            .then(if (fillDown) Modifier.navigationBarsPadding().padding(bottom = BOTTOM_BAR_CLEARANCE) else Modifier)
            .imePadding(),
        enter = fadeIn(tween(Motion.FAST_MS)) +
            slideInVertically(tween(Motion.MEDIUM_MS, easing = Motion.EaseOut)) { -it / DROP_FRACTION },
        exit = fadeOut(tween(Motion.FAST_MS)) +
            slideOutVertically(tween(Motion.FAST_MS, easing = Motion.EaseOut)) { -it / DROP_FRACTION }
    ) {
        val sized = if (width != null) Modifier.widthIn(max = width).fillMaxWidth() else Modifier
        Column(
            sized
                .then(if (fillDown) Modifier.fillMaxHeight() else Modifier)
                .glass(PanelShape, tint = panelTint())
                .padding(vertical = 4.dp),
            content = content
        )
    }
}

/** The mock's `.sheet`: glass rising from the bottom (600 ms, ease-out) with a grab handle. */
@Composable
private fun BoxScope.SheetPanel(visible: Boolean, content: @Composable ColumnScope.() -> Unit) {
    AnimatedVisibility(
        visible,
        Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(start = SHEET_INSET, end = SHEET_INSET, bottom = SHEET_INSET),
        enter = slideInVertically(tween(SHEET_MS, easing = Motion.EaseOut)) { it * 2 },
        exit = slideOutVertically(tween(Motion.MEDIUM_MS, easing = Motion.EaseOut)) { it * 2 }
    ) {
        // [SHEET_MAX_HEIGHT] of the window, so the page stays in view above it (owner, 2026-10-21); the content
        // scrolls inside if it must.
        val maxHeight = LocalWindowInfo.current.containerDpSize.height * SHEET_MAX_HEIGHT
        Column(
            Modifier
                .widthIn(max = SHEET_WIDTH)
                .fillMaxWidth()
                // One height for every tab, so switching tabs doesn't move the sheet under the finger.
                .height(maxHeight)
                .glass(RoundedCornerShape(28.dp), tint = panelTint())
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 16.dp)
        ) {
            // `.grab`
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 8.dp)
                    .size(40.dp, 5.dp)
                    .clip(RoundedCornerShape(50))
                    .background(FlowFolio.palette.hint.copy(alpha = GRAB_ALPHA))
            )
            content()
        }
    }
}

/**
 * The panels' glass. On dark papers it is nearly opaque (the app's surface at 88%): text over the light frosted glass
 * of the bars was hard to read where the page behind had a bright photo (owner, 2026-10-18). On light papers the
 * light palette's own glass reads well.
 */
@Composable
private fun panelTint(): Color {
    val p = FlowFolio.palette
    return if (p.dark) p.surface.copy(alpha = DARK_PANEL_ALPHA) else p.background.copy(alpha = LIGHT_PANEL_ALPHA)
}

private const val DARK_PANEL_ALPHA = 0.88f
private const val LIGHT_PANEL_ALPHA = 0.6f
private val PanelShape = RoundedCornerShape(26.dp)
private val PANEL_INSET = 14.dp

/** The top bar is 8 + 56 + 8 dp unfolded, and 2 rows folded; panels start clearly below it. */
private val WIDE_TOP_BAR_CLEARANCE = 78.dp
private val FOLDED_TOP_BAR_CLEARANCE = 116.dp
private val CENTERED_VERTICAL_CLEARANCE = 84.dp
private const val CENTERED_MAX_HEIGHT = 0.72f

/** The bottom bar is 56 dp tall, 26 dp above the navigation bar; the contents stop 10 dp above it. */
private val BOTTOM_BAR_CLEARANCE = 92.dp
private val CONTENTS_WIDTH = 520.dp
private val GO_TO_WIDTH = 420.dp
private val SEARCH_WIDTH = 420.dp
private val RIBBONS_WIDTH = 380.dp
private val SHEET_WIDTH = 600.dp
private const val SHEET_MAX_HEIGHT = 0.46f
private val SHEET_INSET = 10.dp
private const val SHEET_MS = 600
private const val DROP_FRACTION = 8
private const val GRAB_ALPHA = 0.4f
