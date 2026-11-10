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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioTabs
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.Motion
import com.flowfolio.core.designsystem.glass
import com.flowfolio.core.model.Bookmark
import com.flowfolio.core.voice.Voice
import com.flowfolio.core.voice.VoicePackState

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
    searchQuery: String = "",
    onSearchQuery: (String) -> Unit = {},
    searchMatchCase: Boolean = false,
    onSearchMatchCase: (Boolean) -> Unit = {},
    onSearchJump: (SearchHit) -> Unit = {},
    narrator: NarratorUi = NarratorUi(),
    onNarratorToggle: () -> Unit = {},
    onNarratorRate: (Float) -> Unit = {},
    onNarratorContinuous: (Boolean) -> Unit = {},
    onNarratorVoice: (String) -> Unit = {}
) {
    DockPanel(panel == Panel.CONTENTS, inner = LIST_PADDING) {
        ContentsPanel(navigation, s.anchorPage, mark.all, jump) { onPanel(Panel.GO_TO) }
    }
    DockPanel(panel == Panel.DICTIONARY) {
        // ADR-0052: Words, on History from the dock (a selection opens it on Define or Translate).
        WordsPanel(null, WordsTab.HISTORY, navigation, jump, onClose = null)
    }
    DockPanel(panel == Panel.ANNOTATIONS, inner = LIST_PADDING) {
        AnnotationList(mark.notes, navigation, jump)
    }
    DockPanel(panel == Panel.GO_TO, inner = LIST_PADDING) {
        GoToForm(navigation.labels, s.pageCount, jump, onDismiss)
    }
    DockPanel(panel == Panel.MARK, inner = LIST_PADDING) {
        ColourStrip(mark) { chosen ->
            if (chosen == null) mark.onUnmark() else mark.onMark(chosen)
            onDismiss()
        }
        RibbonList(mark.all, navigation, jump)
    }
    DockPanel(panel == Panel.SETTINGS) { ReaderSettingsContent(s.reflowable, s.bookFont, flow, onFlow) }
    DockPanel(panel == Panel.SEARCH) {
        ReaderSearchPanel(
            searchQuery,
            hits,
            onSearchQuery,
            onSearchJump,
            navigation,
            s.anchorPage,
            searchMatchCase,
            onSearchMatchCase,
            onDismiss
        )
    }
    DockPanel(panel == Panel.NARRATOR) {
        ReaderNarratorPanel(
            narrator.narrating,
            narrator.rate,
            narrator.continuous,
            narrator.voices,
            narrator.voice,
            narrator.packState,
            narrator.error,
            onNarratorToggle,
            onNarratorRate,
            onNarratorContinuous,
            onNarratorVoice
        )
    }
}

/** Narrator Mode's state, collected in [ReadyReader] and passed to [ReaderNarratorPanel] (Phase 6 M3). */
internal data class NarratorUi(
    val narrating: Boolean = false,
    val rate: Float = 1f,
    val continuous: Boolean = true,
    val voices: List<Voice> = emptyList(),
    val voice: String? = null,
    val packState: VoicePackState = VoicePackState.Idle,
    val error: String? = null
)

/** `.rpanel` Contents (ADR-0052): the book's contents, and its ribbons under a second tab. */
@Composable
private fun ContentsPanel(
    navigation: BookNavigation,
    page: Int,
    ribbonMarks: List<Bookmark>,
    jump: (Int) -> Unit,
    onGoTo: () -> Unit
) {
    // `.rpanel` Contents (ADR-0052): Contents and Ribbons as tabs, the ribbons with their count.
    var ribbons by rememberSaveable { mutableStateOf(false) }
    val tabs: @Composable () -> Unit = {
        FlowFolioTabs(
            listOf(stringResource(R.string.reader_contents), stringResource(R.string.reader_ribbons_tab)),
            if (ribbons) 1 else 0,
            { ribbons = it == 1 },
            Modifier.padding(horizontal = 16.dp).padding(bottom = 4.dp),
            counts = listOf(null, ribbonMarks.size)
        )
    }
    if (ribbons) {
        ContentsHeaderless(tabs) { RibbonList(ribbonMarks, navigation, jump, heading = false) }
    } else {
        ContentsList(navigation.outline, navigation.labels, page, jump, onGoTo, tabs)
    }
}

/** The Ribbons tab under the Contents title, with the tabs above it. */
@Composable
private fun ContentsHeaderless(tabs: @Composable () -> Unit, content: @Composable () -> Unit) {
    Text(
        stringResource(R.string.reader_contents),
        style = FlowFolioType.dialogTitle.copy(fontSize = 20.sp, color = FlowFolio.palette.ink),
        modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 10.dp)
    )
    tabs()
    content()
}

/**
 * Every reader panel (`.rpanel`, ADR-0052): a glass card as wide as the dock (560 dp unfolded, the width less a margin
 * folded), [PANEL_GAP] above it, or above the keyboard while one is up, rising into place. [LocalDockSpace] says how
 * tall the dock is, so the gap is the same for every panel, folded and unfolded (owner, 2026-11-10).
 */
@Composable
private fun BoxScope.DockPanel(
    visible: Boolean,
    inner: Dp = PANEL_PADDING,
    content: @Composable ColumnScope.() -> Unit
) {
    val density = LocalDensity.current
    val dock = LocalDockSpace.current.height
    val ime = with(density) { WindowInsets.ime.getBottom(this).toDp() }
    val status = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    val bottom = if (ime > 0.dp) ime + PANEL_GAP else dock + PANEL_GAP
    val room = LocalWindowInfo.current.containerDpSize.height - bottom - status - TOP_BAR_CLEARANCE
    AnimatedVisibility(
        visible,
        Modifier
            .align(Alignment.BottomCenter)
            .padding(start = PANEL_INSET, end = PANEL_INSET, bottom = bottom),
        enter = fadeIn(tween(Motion.FAST_MS)) +
            slideInVertically(tween(Motion.RISE_MS, easing = Motion.Rise)) { it / DROP_FRACTION },
        exit = fadeOut(tween(Motion.FAST_MS)) +
            slideOutVertically(tween(Motion.FAST_MS, easing = Motion.EmphasizedExit)) { it / DROP_FRACTION }
    ) {
        Column(
            Modifier
                .widthIn(max = READER_DOCK_WIDTH)
                .fillMaxWidth()
                .heightIn(max = room.coerceAtLeast(MIN_PANEL))
                .glass(PanelShape, tint = panelTint())
                .padding(inner),
            content = content
        )
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
private val PanelShape = RoundedCornerShape(24.dp)
private val PANEL_GAP = 10.dp
private val PANEL_PADDING = 18.dp
private val LIST_PADDING = 4.dp
private val MIN_PANEL = 160.dp
private val PANEL_INSET = 10.dp

/** The top bar is 8 + 56 + 8 dp unfolded, and 2 rows folded; panels start clearly below it. */
private val TOP_BAR_CLEARANCE = 78.dp

private const val DROP_FRACTION = 8
