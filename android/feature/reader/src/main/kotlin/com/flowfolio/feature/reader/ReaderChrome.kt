@file:Suppress("TooManyFunctions")

package com.flowfolio.feature.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowfolio.core.designsystem.EdgeFade
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioGlyph
import com.flowfolio.core.designsystem.FlowFolioIconButton
import com.flowfolio.core.designsystem.FlowFolioMenu
import com.flowfolio.core.designsystem.FlowFolioMenuItem
import com.flowfolio.core.designsystem.FlowFolioTooltip
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.Glyph
import com.flowfolio.core.designsystem.Motion
import com.flowfolio.core.designsystem.ScreenEntrance
import com.flowfolio.core.designsystem.dragDownToDismiss
import com.flowfolio.core.designsystem.glass
import com.flowfolio.core.designsystem.reveal
import com.flowfolio.core.model.FlowStatus
import com.flowfolio.core.model.PageTurn
import com.flowfolio.feature.reader.layout.ReaderLayout
import com.flowfolio.feature.reader.layout.Spreads
import com.flowfolio.feature.reader.riffle.R as RiffleR
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The mock's reader chrome (`.r-top`, `.r-bottom`): glass bars over the full-bleed pages. A tap in the centre of the
 * screen slides them in until the chrome clock hides them: Back, the title, Contents, Go to page, Aa and the ribbon
 * bookmark on top; [bottomBar], the riffle scrubber (ADR-0022), at the bottom. While they are hidden, the page number
 * stays at the foot of the page in print style.
 */
@Suppress("LongMethod") // the bars, the dock and the riffle bar, each sliding in its own way
@Composable
internal fun BoxScope.ReaderChrome(
    state: ReaderState.Ready,
    navigation: BookNavigation,
    flow: FlowState,
    controlsVisible: Boolean,
    mark: ChromeMark,
    actions: ChromeActions,
    panel: Panel = Panel.NONE,
    bottomBarVisible: Boolean = controlsVisible,
    bottomBar: @Composable (footer: @Composable () -> Unit) -> Unit
) {
    val slide = tween<IntOffset>(Motion.SLOW_MS, easing = Motion.EaseOut)
    val density = LocalDensity.current
    var barHeight by remember { mutableStateOf(0.dp) }
    // A soft, blurred fade in the paper's colour behind the top bar while it shows, so its buttons never sit on the
    // book's text (owner, 2026-10-23). It fades rather than slides, and stays solid behind the buttons.
    AnimatedVisibility(
        controlsVisible,
        Modifier.align(Alignment.TopCenter),
        enter = fadeIn(tween(Motion.MEDIUM_MS, easing = Motion.EaseOut)),
        exit = fadeOut(tween(Motion.MEDIUM_MS, easing = Motion.EaseOut))
    ) {
        val wide = LocalWindowInfo.current.containerDpSize.width >= TITLE_BESIDE_BACK
        val status = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
        val solid = barHeight.takeIf { it > 0.dp }
            ?: (status + (if (wide) BIG_BUTTON else FOLDED_BUTTON) + BAR_VERTICAL_PADDING * 2)
        val total = solid + TOP_FADE_TAIL
        EdgeFade(Modifier, total, state.tone.paper, fromTop = true, hold = solid / total)
    }
    AnimatedVisibility(
        controlsVisible,
        Modifier.align(Alignment.TopCenter),
        enter = slideInVertically(slide) { -it * 2 } + fadeIn(tween(Motion.MEDIUM_MS, easing = Motion.EaseOut)),
        exit = slideOutVertically(slide) { -it * 2 } + fadeOut(tween(Motion.MEDIUM_MS, easing = Motion.EaseOut))
    ) {
        // Each time the bars show, their buttons fall into place one after another (ADR-0034).
        ScreenEntrance(holdForLandings = false) {
            val readMillis = LocalReadMillis.current
            TopBar(
                TitleLine(
                    state.title,
                    // An outline entry named after the book itself is not a chapter (OpenStax adds one).
                    PageLookup.chapterTitleAt(
                        navigation.outline.filter { it.title.trim() != state.title.trim() },
                        state.anchorPage
                    ),
                    ReadingEstimate.millisLeft(
                        (state.pageCount - state.anchorPage - 1).coerceAtLeast(0),
                        state.anchorPage + 1,
                        readMillis
                    ).takeIf { state.anchorPage < state.pageCount - 1 }
                ),
                mark,
                actions,
                panel,
                modifier = Modifier.onSizeChanged { barHeight = with(density) { it.height.toDp() } }
            )
        }
    }
    // ADR-0052: the reading tools sit in a dock above the riffle bar, so nothing covers the text (the unfolded
    // right-hand rail sat on the right page's line ends). The dock keeps clear of the riffle bar by its height.
    var riffleHeight by remember { mutableStateOf(0.dp) }
    var dockHeight by remember { mutableStateOf(0.dp) }
    // What the panels keep clear of: the riffle card (its own navigation-bar padding included), else the tools.
    val navBar = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    val space = LocalDockSpace.current
    space.height = dockClearance(bottomBarVisible, controlsVisible, riffleHeight, dockHeight, navBar)
    // The mock's `.dock` is one glass card: the riffle strip with the tools under it. The tools stand on their own
    // only while the riffle bar is away (a panel is open, or the bar was dismissed), so they stay in reach.
    AnimatedVisibility(
        controlsVisible && !bottomBarVisible,
        Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding(),
        enter = slideInVertically(slide) { it } + fadeIn(tween(Motion.MEDIUM_MS, easing = Motion.EaseOut)),
        exit = slideOutVertically(slide) { it } + fadeOut(tween(Motion.MEDIUM_MS, easing = Motion.EaseOut))
    ) {
        ScreenEntrance(holdForLandings = false) {
            Box(Modifier.onSizeChanged { dockHeight = with(density) { it.height.toDp() } }) { ToolDock(actions, panel) }
        }
    }
    AnimatedVisibility(
        bottomBarVisible,
        Modifier.align(Alignment.BottomCenter),
        enter = slideInVertically(slide) { it * 2 } + fadeIn(tween(Motion.MEDIUM_MS, easing = Motion.EaseOut)),
        exit = slideOutVertically(slide) { it * 2 } + fadeOut(tween(Motion.MEDIUM_MS, easing = Motion.EaseOut))
    ) {
        ScreenEntrance(holdForLandings = false) {
            Box(
                Modifier
                    .reveal(DOCK_ORDER)
                    .dragDownToDismiss(actions.onDismissRiffle)
                    .coachSpot(CoachSpot.STRIP)
                    .onSizeChanged { riffleHeight = with(density) { it.height.toDp() } }
            ) { bottomBar { ToolRow(actions, panel, Modifier.padding(horizontal = 18.dp).padding(bottom = 4.dp)) } }
        }
    }
    // In Scroll the number would float over the text; the bars show the page there. Whenever the riffle bar is not
    // showing -- hidden with the rest of the chrome, or dismissed on its own -- the page number stands in for it, and
    // carries the way back from a detour with it (owner, M5 round 3).
    // While a panel is open the number would sit under the dock, over the text; the panel has the reader's attention.
    val panelOpen = controlsVisible && panel != Panel.NONE
    if (!bottomBarVisible && state.turn != PageTurn.SCROLL && !panelOpen) {
        // With the riffle bar dismissed the dock still sits at the foot: the number rides above it.
        PageNumber(state, navigation, flow, actions, controlsVisible, lift = if (controlsVisible) dockHeight else 0.dp)
    }
}

/**
 * `.rtop` (ADR-0052): Back, the book's title chip, the ribbon button (tap to mark this page, long press for its colour)
 * and ⋯, folded and unfolded alike. The reading tools are in the dock at the bottom.
 */
@Composable
private fun TopBar(
    title: TitleLine,
    mark: ChromeMark,
    actions: ChromeActions,
    panel: Panel,
    modifier: Modifier = Modifier
) {
    val wide = LocalWindowInfo.current.containerDpSize.width >= TITLE_BESIDE_BACK
    val button = if (wide) BIG_BUTTON else FOLDED_BUTTON
    Row(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = BAR_INSET, vertical = BAR_VERTICAL_PADDING),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TOOL_GAP)
    ) {
        FlowFolioIconButton(
            onClick = actions.onBack,
            Modifier.reveal(0),
            glass = true,
            size = button,
            tooltip = stringResource(R.string.reader_back)
        ) {
            FlowFolioGlyph(Glyph.Back, null, color = FlowFolio.palette.text, size = button * GLYPH_SHARE)
        }
        TitlePill(title, wide, Modifier.weight(1f).reveal(1))
        Box(Modifier.reveal(2).coachSpot(CoachSpot.RIBBON)) {
            BookmarkButton(
                mark,
                actions,
                button,
                glowing =
                    panel == Panel.MARK
            )
        }
        Box(Modifier.reveal(3)) { MoreMenu(actions, button) }
    }
}

/** The top bar's ⋯ (ADR-0052): Go to page and the ribbon's colour. */
@Composable
private fun MoreMenu(actions: ChromeActions, button: Dp) {
    var open by remember { mutableStateOf(false) }
    Box {
        FlowFolioIconButton(
            onClick = { open = true },
            glass = true,
            size = button,
            tooltip = stringResource(R.string.reader_more),
            modifier = Modifier.testTag(ReaderTestTags.MORE)
        ) {
            FlowFolioGlyph(Glyph.More, null, color = FlowFolio.palette.text, size = button * GLYPH_SHARE)
        }
        FlowFolioMenu(expanded = open, onDismissRequest = { open = false }) {
            FlowFolioMenuItem(stringResource(R.string.reader_go_to), {
                open = false
                actions.onPanel(Panel.GO_TO)
            }, glyph = Glyph.GoTo)
            FlowFolioMenuItem(stringResource(R.string.reader_ribbon_colour), {
                open = false
                actions.onPanel(Panel.MARK)
            }, glyph = Glyph.Bookmark)
        }
    }
}

/**
 * The tools on their own glass (`.dock` without the riffle): shown while the riffle bar is away, so the tools stay in
 * reach with a panel open.
 */
@Composable
private fun ToolDock(actions: ChromeActions, panel: Panel) {
    val wide = LocalWindowInfo.current.containerDpSize.width >= TITLE_BESIDE_BACK
    ToolRow(
        actions,
        panel,
        Modifier
            .padding(horizontal = BAR_INSET, vertical = 6.dp)
            .then(if (wide) Modifier.width(READER_DOCK_WIDTH) else Modifier.fillMaxWidth())
            .glass(RoundedCornerShape(DOCK_RADIUS))
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .testTag(ReaderTestTags.DOCK)
    )
}

/**
 * The six reading tools (`.tools`, ADR-0052): Contents, Search, Aa, Listen, Notebook and Words, as icons with their
 * names in tooltips, spread across the dock. The tool whose panel is open sits on the soft accent.
 */
@Composable
private fun ToolRow(actions: ChromeActions, panel: Panel, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        DockTool(Panel.CONTENTS, Glyph.Contents, R.string.reader_contents, ReaderTestTags.CONTENTS, panel, actions, 4)
        DockTool(Panel.SEARCH, Glyph.Search, R.string.reader_dock_search, ReaderTestTags.SEARCH, panel, actions, 5)
        DockTool(
            Panel.SETTINGS,
            Glyph.TextSize,
            R.string.reader_settings_button,
            ReaderTestTags.SETTINGS,
            panel,
            actions,
            6
        )
        DockTool(Panel.NARRATOR, Glyph.Sound, R.string.reader_listen, ReaderTestTags.NARRATOR, panel, actions, 7)
        DockTool(
            Panel.ANNOTATIONS,
            Glyph.Notebook,
            R.string.reader_notebook,
            ReaderTestTags.ANNOTATIONS,
            panel,
            actions,
            8
        )
        DockTool(
            Panel.DICTIONARY,
            Glyph.Translate,
            R.string.reader_words,
            ReaderTestTags.DICTIONARY,
            panel,
            actions,
            9
        )
    }
}

@Suppress("LongParameterList")
@Composable
private fun DockTool(
    open: Panel,
    glyph: Glyph,
    label: Int,
    tag: String,
    active: Panel,
    actions: ChromeActions,
    order: Int
) {
    val p = FlowFolio.palette
    val on = active == open
    val name = stringResource(label)
    val fill by animateColorAsState(if (on) p.accentSoft else Color.Transparent, Motion.colorSpec(), label = "toolFill")
    FlowFolioTooltip(name) {
        Box(
            Modifier
                .then(coachSpotOf(open)?.let { Modifier.coachSpot(it) } ?: Modifier)
                .reveal(order)
                .size(TOOL_WIDTH, TOOL_HEIGHT)
                .clip(RoundedCornerShape(14.dp))
                .background(fill)
                .clickable(role = androidx.compose.ui.semantics.Role.Button) { actions.onPanel(open) }
                .semantics { contentDescription = name }
                .testTag(tag),
            contentAlignment = Alignment.Center
        ) {
            FlowFolioGlyph(glyph, null, color = if (on) p.accent else p.ink2, size = 21.dp, glow = false)
        }
    }
}

/** The book's title on glass, beside Back (unfolded) or centred (folded). */
@Composable
private fun TitlePill(title: TitleLine, wide: Boolean, modifier: Modifier) {
    val p = FlowFolio.palette
    Row(
        modifier
            .height(if (wide) BIG_BUTTON else FOLDED_BUTTON)
            .glass(RoundedCornerShape(50))
            .padding(horizontal = 16.dp)
            .testTag(ReaderTestTags.TITLE),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // As the mock's `.tt`: unfolded, the title gives way (ellipsis) so the chapter after it stays readable.
        // Folded there is room for one of them: the title comes first and the chapter takes what is left.
        Text(
            title.title,
            style = FlowFolioType.body.copy(fontSize = 13.sp, color = p.ink, fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = if (wide) Modifier.weight(1f, fill = false) else Modifier
        )
        title.chapter?.takeIf { it.isNotBlank() && it != title.title }?.let {
            Text(
                "  ·  $it",
                style = FlowFolioType.body.copy(fontSize = 13.sp, color = p.ink2),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // Folded, measured after the title, so it can no longer squeeze the title out (2026-11-12).
                modifier = if (wide) Modifier.widthIn(max = CHAPTER_MAX) else Modifier.weight(1f, fill = false)
            )
        }
        val left = title.millisLeft
        if (wide && left != null) {
            val (amount, hours) = ReadingEstimate.parts(left)
            Spacer(Modifier.weight(1f).widthIn(min = 12.dp))
            Text(
                stringResource(if (hours) R.string.reader_hours_left else R.string.reader_minutes_left, amount),
                style = FlowFolioType.body.copy(fontSize = 12.sp, color = p.ink3),
                maxLines = 1
            )
        }
    }
}

/** The title pill's words (`.rtop .tt`): the book, the chapter it is at, and how many pages are left. */
internal data class TitleLine(val title: String, val chapter: String?, val millisLeft: Long?)

/** The print-style page number at the foot of the page while the bars are hidden. */
@Suppress("LongMethod")
@Composable
private fun BoxScope.PageNumber(
    state: ReaderState.Ready,
    navigation: BookNavigation,
    flow: FlowState,
    actions: ChromeActions,
    controlsVisible: Boolean,
    lift: Dp
) {
    val spread = state.layout == ReaderLayout.SPREAD
    val pages = if (spread) Spreads.pagesOf(state.unit, state.pageCount, state.coverAlone) else listOf(state.anchorPage)
    // Two screen pages of one printed page (a reflowed book's page list) show its number once.
    val shown = pages.map { PageLookup.labelOf(it, navigation.labels) }.distinct().joinToString("–")
    // The book's own numbers stand alone ("48–49"); counting from the cover, "62–63 of 959".
    val printed = pages.any { navigation.labels.getOrNull(it) != null }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val half = maxWidth / 2
        Row(
            Modifier
                .align(if (spread) Alignment.BottomStart else Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = lift)
                .padding(horizontal = PAGE_MARGIN, vertical = FOOTER_MARGIN)
                .clickable { actions.onPanel(Panel.GO_TO) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (printed) shown else stringResource(R.string.reader_page_of, shown, state.pageCount),
                style = PrintStyle.copy(color = LocalTone.current.folioInk),
                modifier = Modifier.testTag(ReaderTestTags.INDICATOR)
            )
            DetourReturn()
            FlowProgressBar(flow)
        }
        // The chapter and its section, reflowed books only (a PDF already carries its own running heads). In a spread
        // the chapter heads the left page and the section the right page's foot, each kept to its own page's column
        // (the title ends at the left page's inner margin, the section ends at the right page's outer margin). Folded,
        // the section (else the chapter) sits at the top, shifted left of the text border and cut at the front camera
        // so the punch-hole never clips it (owner, 2026-11-07). Hidden while the chrome shows, so a running head never
        // sits under the top bar's buttons (owner, 2026-11-07). No status- or navigation-bar padding: those bars show
        // and hide with the chrome, so padding by them moved the heads for a moment each time (owner, 2026-11-12); they
        // sit where they rest while reading, with the bars away.
        if (state.reflowable && !controlsVisible) {
            val leftPage = pages.firstOrNull() ?: state.anchorPage
            val rightPage = pages.lastOrNull() ?: state.anchorPage
            val chapter = PageLookup.chapterTitleAt(navigation.outline, leftPage)
            val section = PageLookup.subtitleAt(navigation.outline, rightPage)
            if (spread) {
                chapter?.let {
                    ChapterLabel(
                        it,
                        Modifier.align(Alignment.TopStart)
                            .padding(horizontal = PAGE_MARGIN, vertical = FOOTER_MARGIN)
                            .widthIn(max = half - PAGE_MARGIN * 2),
                        actions
                    )
                }
                section?.let {
                    ChapterLabel(
                        it,
                        // Right-aligned to the right page's outer margin: it can grow left only to that page's inner
                        // margin, then truncates at the end of the line on the right (owner, 2026-11-07).
                        Modifier.align(Alignment.BottomEnd)
                            .padding(end = PAGE_MARGIN)
                            .padding(vertical = FOOTER_MARGIN)
                            .widthIn(max = half - PAGE_MARGIN * 2),
                        actions
                    )
                }
            } else {
                (section ?: chapter)?.let {
                    val camera = cameraEdgeDp(half)
                    ChapterLabel(
                        it,
                        Modifier.align(Alignment.TopStart)
                            .padding(start = FOLDED_HEAD_INSET, top = FOOTER_MARGIN)
                            .widthIn(max = (camera - FOLDED_HEAD_INSET - HEAD_GAP).coerceAtLeast(0.dp)),
                        actions
                    )
                }
            }
        }
    }
}

/**
 * The left edge of the front-camera cutout in dp, so the folded running head can end just before it; [fallback] when
 * the window reports no cutout. The cutout is read from the window rather than hardcoded, so it tracks the real camera
 * on any phone (owner, 2026-11-07).
 */
@Composable
private fun cameraEdgeDp(fallback: Dp): Dp {
    val view = LocalView.current
    val density = LocalDensity.current
    val cutout = view.rootWindowInsets?.displayCutout
    val band = with(density) { CUTOUT_BAND.toPx() }
    val left = cutout?.boundingRects?.filter { it.top < band && it.width() > 0 }?.minOfOrNull { it.left }
    return left?.let { with(density) { it.toDp() } } ?: fallback
}

/** A chapter or section heading in folio ink; tapping it opens the contents, which jumps to the current entry. */
@Composable
private fun BoxScope.ChapterLabel(text: String, modifier: Modifier, actions: ChromeActions) {
    Text(
        text,
        style = PrintStyle.copy(color = LocalTone.current.folioInk),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clickable { actions.onPanel(Panel.CONTENTS) }
            .testTag(ReaderTestTags.CHAPTER_TITLE)
    )
}

/**
 * Beside the page number while the book's Dynamic Flow is being prepared (ADR-0045): a small glyph and a short bar
 * filling as the conversion goes, a moving glint while it waits. Folio-ink coloured, so it sits quietly on any paper.
 */
@Composable
private fun FlowProgressBar(flow: FlowState) {
    val status = flow.status
    val busy = flow.wanted && !flow.reading && (status is FlowStatus.Converting || status == FlowStatus.Queued)
    AnimatedVisibility(busy, enter = fadeIn(tween(Motion.MEDIUM_MS)), exit = fadeOut(tween(Motion.MEDIUM_MS))) {
        val ink = LocalTone.current.folioInk
        val accent = FlowFolio.palette.ink
        val fraction = (status as? FlowStatus.Converting)?.fraction ?: 0f
        val shown by animateFloatAsState(fraction, tween(Motion.MEDIUM_MS), label = "flowBar")
        val glint = rememberInfiniteTransition(label = "flowGlint")
        val sweep by glint.animateFloat(0f, 1f, infiniteRepeatable(tween(GLINT_MS)), label = "sweep")
        Row(
            Modifier.padding(start = 10.dp).testTag(ReaderTestTags.FLOW_PROGRESS),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FlowFolioGlyph(Glyph.DynamicFlow, contentDescription = null, color = ink, size = 13.dp)
            androidx.compose.foundation.Canvas(Modifier.padding(start = 6.dp).size(BAR_WIDTH, BAR_HEIGHT)) {
                val r = androidx.compose.ui.geometry.CornerRadius(size.height / 2f)
                drawRoundRect(ink.copy(alpha = BAR_TRACK_ALPHA), cornerRadius = r)
                if (shown > 0f) {
                    drawRoundRect(accent, size = size.copy(width = size.width * shown), cornerRadius = r)
                } else {
                    // Waiting its turn or just starting: a short glint travels along the track.
                    val w = size.width * GLINT_SHARE
                    val x = (size.width + w) * sweep - w
                    drawRoundRect(
                        accent.copy(alpha = GLINT_ALPHA),
                        topLeft = androidx.compose.ui.geometry.Offset(x.coerceAtLeast(0f), 0f),
                        size = size.copy(width = (w + minOf(0f, x)).coerceIn(0f, size.width - x.coerceAtLeast(0f))),
                        cornerRadius = r
                    )
                }
            }
            // ADR-0052: the bar says what it is, "Preparing · 42%", so it doesn't read as a stray line.
            Text(
                if (fraction > 0f) {
                    stringResource(R.string.reader_flow_preparing_percent, (fraction * PERCENT).roundToInt())
                } else {
                    stringResource(R.string.reader_flow_preparing)
                },
                style = FlowFolioType.tab.copy(color = ink, fontSize = 11.sp),
                modifier = Modifier.padding(start = 6.dp),
                maxLines = 1
            )
        }
    }
}

private const val PERCENT = 100
private val BAR_WIDTH = 34.dp
private val BAR_HEIGHT = 4.dp
private const val BAR_TRACK_ALPHA = 0.22f
private const val GLINT_MS = 1_400
private const val GLINT_SHARE = 0.35f
private const val GLINT_ALPHA = 0.85f

/**
 * Offers each tap (a press released without moving, before long-press time) to [onTap] with its position and the
 * area's size. When [onTap] returns true the release is consumed in the initial pass, before the page handlers see it,
 * so the tap does not also turn a page. Drags are never intercepted.
 */
internal fun Modifier.interceptTaps(onTap: (Offset, IntSize) -> Boolean): Modifier = composed {
    val handler by rememberUpdatedState(onTap)
    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var tracking = true
            while (tracking) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                val moved =
                    change == null || (change.position - down.position).getDistance() > viewConfiguration.touchSlop
                val released = !moved && !change.pressed
                val quick =
                    change != null && change.uptimeMillis - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis
                if (released && quick && handler(change.position, size)) change.consume()
                tracking = !moved && change.pressed
            }
        }
    }
}

/**
 * Touch zones (owner, 2026-10-17): the outer 30% on each side turns pages (tap: left back, right forward; swipes
 * either way), the middle [CENTRE_ZONE] of the width, full height, is for taps that show or hide the controls.
 * Swipes that start in the middle still turn pages.
 */
internal fun isCentre(tap: Offset, size: IntSize): Boolean = abs(tap.x - size.width / 2f) < size.width * CENTRE_ZONE / 2

/** The page number in the page's own ink, softened like print, so it reads on every paper. */
private val PageTone.folioInk: Color get() = (ink ?: PRINT_INK).copy(alpha = FOLIO_ALPHA)

private val PRINT_INK = Color(0xFF3A3A3C)
private const val FOLIO_ALPHA = 0.72f

private val PrintStyle = TextStyle(
    fontSize = 13.sp,
    letterSpacing = 0.3.sp,
    shadow = Shadow(color = Color.Black.copy(alpha = 0.22f), offset = Offset(0f, 1.5f), blurRadius = 6f)
)

private const val CENTRE_ZONE = 0.4f
private val BAR_INSET = 14.dp
private val BAR_VERTICAL_PADDING = 8.dp
private val TOP_FADE_TAIL = 36.dp
private val PAGE_MARGIN = 32.dp
private val FOOTER_MARGIN = 14.dp
internal val TITLE_BESIDE_BACK = 600.dp

/** The folded running head sits left of the page text border, ending just before the camera (owner, 2026-11-07). */
private val FOLDED_HEAD_INSET = 8.dp
private val HEAD_GAP = 8.dp
private val CUTOUT_BAND = 200.dp

private val BIG_BUTTON = 56.dp
private val FOLDED_BUTTON = 50.dp
private val TOOL_GAP = 8.dp

/** A glyph's share of its round button (24 dp in 42 dp). */
internal const val GLYPH_SHARE = 24f / 42f

/** The riffle dock falls in just after the top bar's first buttons. */
private const val DOCK_ORDER = 2

/**
 * The way back from a detour, beside the page number (owner, M5 round): it pulses until it is used, and does the
 * same as the notch on the riffle bar, so dismissing the bar does not take the way back with it.
 */
@Composable
private fun DetourReturn() {
    val detour = LocalDetour.current
    val marked = detour.unit ?: return
    val jump = LocalDetourJump.current
    val ink = LocalTone.current.folioInk
    val pulse = rememberInfiniteTransition(label = "detourFooter")
    val glow by pulse.animateFloat(
        initialValue = DETOUR_DIM,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(DETOUR_PULSE_MS, easing = Motion.EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "detourFooterPulse"
    )
    val back = stringResource(RiffleR.string.riffle_detour_back)
    Box(
        Modifier
            .padding(start = 8.dp)
            .clip(CircleShape)
            .clickable {
                detour.clear()
                jump(marked)
            }
            .semantics { contentDescription = back }
            .padding(3.dp)
            .testTag(ReaderTestTags.DETOUR_RETURN),
        contentAlignment = Alignment.Center
    ) {
        FlowFolioGlyph(
            Glyph.Detour,
            contentDescription = null,
            color = ink.copy(alpha = glow),
            size = DETOUR_GLYPH,
            glow = false
        )
    }
}

/** How the reader takes a detour back; set by the screen that knows how to land on a unit. */
val LocalDetourJump = staticCompositionLocalOf<(Int) -> Unit> { {} }

private val DETOUR_GLYPH = 15.dp
private const val DETOUR_DIM = 0.3f
private const val DETOUR_PULSE_MS = 900

private val TOOL_WIDTH = 46.dp
private val CHAPTER_MAX = 260.dp
private val TOOL_HEIGHT = 44.dp
private val DOCK_RADIUS = 26.dp

/** The dock tools the coach marks point at. */
private fun coachSpotOf(panel: Panel): CoachSpot? = when (panel) {
    Panel.SETTINGS -> CoachSpot.AA
    Panel.NARRATOR -> CoachSpot.LISTEN
    else -> null
}

/** What the panels keep clear of: the riffle card (navigation bar included), else the tools, else the bar. */
private fun dockClearance(riffle: Boolean, controls: Boolean, riffleHeight: Dp, toolsHeight: Dp, navBar: Dp): Dp =
    when {
        riffle -> riffleHeight
        controls -> toolsHeight + navBar
        else -> navBar
    }
