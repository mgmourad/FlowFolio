package com.flowfolio.feature.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flowfolio.core.designsystem.AtmosphereOverlay
import com.flowfolio.core.designsystem.FeatureSplash
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioGlyph
import com.flowfolio.core.designsystem.FlowFolioPalette
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.GlassStyle
import com.flowfolio.core.designsystem.Glyph
import com.flowfolio.core.designsystem.LocalGlassState
import com.flowfolio.core.designsystem.LocalGlassStyle
import com.flowfolio.core.designsystem.Motion
import com.flowfolio.core.designsystem.glass
import com.flowfolio.core.model.Annotation
import com.flowfolio.core.model.Bookmark
import com.flowfolio.core.model.PageTurn
import com.flowfolio.core.model.atmosphereInForce
import com.flowfolio.feature.reader.layout.ReaderLayout
import com.flowfolio.feature.reader.layout.Spreads
import com.flowfolio.feature.reader.riffle.ChromePause
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay

/**
 * What floats over the book. A centre tap shows the controls until the [chrome] clock hides them (each use of them
 * restarts it); another centre tap hides them sooner. They stay up while a panel is open.
 */
@Stable
internal class Overlay(val chrome: ChromeClock, val textPinch: TextPinch) {
    var panel by mutableStateOf(Panel.NONE)
    var riffleDismissed by mutableStateOf(false)
    var webLink by mutableStateOf<String?>(null)

    /** The panel whose button was last tapped, while its first-use guide may still be owed (owner, 2026-11-07). */
    var guide by mutableStateOf<Panel?>(null)

    /** The EPUB footnote shown in a popover, if any. */
    var note by mutableStateOf<ShownNote?>(null)

    /** The EPUB island (equation or table) or picture shown zoomed, if any. */
    var figure by mutableStateOf<Figure?>(null)

    /** Non-null while zoomed in; page turns and the chrome are paused meanwhile. */
    var zoom by mutableStateOf<ZoomState?>(null)

    /** A pinch on the book: zooming in starts zoom mode, zooming out at 1x does nothing. */
    fun pinch(centroid: Offset, pan: Offset, factor: Float, size: IntSize) {
        val current = zoom ?: if (factor > 1f) ZoomState().also { zoom = it } else return
        current.transform(centroid, pan, factor, size)
    }

    /** Fingers lifted: back at 1x means leaving zoom. */
    fun endPinch() {
        if (zoom?.isAtRest == true) zoom = null
    }

    fun toggle() {
        riffleDismissed = false
        chrome.toggle()
    }

    /** Opens [which], or closes it when it is already open (a second tap on its button); dismisses the riffle bar. */
    fun open(which: Panel) {
        chrome.interacted()
        riffleDismissed = true
        val next = if (panel == which) Panel.NONE else which
        panel = next
        guide = if (next == Panel.NONE) null else which
    }

    /** Closes the panel (a tap outside it, or its own close); a pending guide goes with it. */
    fun dismiss() {
        panel = Panel.NONE
        guide = null
    }

    /** Acts on a routed tap; true when it was not a page turn (so the page should not see it). */
    fun handle(target: PageFrames.TapTarget, jump: (Int) -> Unit): Boolean {
        when (target) {
            is PageFrames.TapTarget.Jump -> jump(target.page)
            is PageFrames.TapTarget.Web -> webLink = target.uri
            is PageFrames.TapTarget.Note -> note = ShownNote(target.href, target.at)
            is PageFrames.TapTarget.Island -> figure = Figure.Island(target.id)
            is PageFrames.TapTarget.Picture -> figure = Figure.Picture(target.path)
            PageFrames.TapTarget.Centre -> toggle()
            PageFrames.TapTarget.Page -> Unit
        }
        return target != PageFrames.TapTarget.Page
    }

    /** A turn, a jump or fold/unfold: zoom ends and an open footnote closes. */
    fun leavePage() {
        zoom = null
        note = null
        figure = null
    }

    fun closeAll() {
        panel = Panel.NONE
        guide = null
        riffleDismissed = false
        chrome.hide()
    }
}

/** The open book: page turns, link and centre taps, the controls and the panels they open. */
@Composable
internal fun BoxScope.ReadyReader(
    s: ReaderState.Ready,
    images: Map<Int, ImageBitmap>,
    navigation: BookNavigation,
    viewModel: ReaderViewModel,
    bookmarks: ReaderBookmarksViewModel,
    marks: List<Bookmark>,
    onBack: () -> Unit
) {
    val links by viewModel.links.collectAsStateWithLifecycle()
    val rtl = LocalRightToLeft.current
    val bounce = remember { EdgeBounce() }
    val haze = rememberHazeState()
    val tiles by viewModel.tiles.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val overlay = rememberOverlay(viewModel)
    val riffling = rememberRiffling(s, navigation, viewModel)
    val detour = remember { DetourState() }
    LaunchedEffect(s.unit, s.layout, s.coverAlone) { overlay.leavePage() }
    val jump = { page: Int ->
        viewModel.goToPage(page)
        overlay.closeAll()
    }
    val framesFor = { size: IntSize ->
        PageFrames.of(s, size, rtl) { page -> images[page]?.let { IntSize(it.width, it.height) } }
    }
    val pageBounds = remember { PageBounds() }
    // Where the pages are for selecting: as laid out (every turn style), or as the zoomed view shows them.
    val annotating = rememberAnnotating(s, viewModel, navigation) { size ->
        overlay.zoom?.let { framesFor(size).zoomed(it) } ?: pageBounds.frames(s, size)
    }
    val mark = chromeMark(s, marks, bookmarks, annotating.all, viewModel::locatorOfPage)
    // A tap on a highlight opens its menu; otherwise links (in Scroll, where the pages lie), the centre, page turns.
    val onTap = { tap: Offset, size: IntSize ->
        val frames = if (s.turn == PageTurn.SCROLL) pageBounds.frames(s, size) else framesFor(size)
        annotating.tap(tap, size) || overlay.handle(PageFrames.route(tap, size, frames, links), jump)
    }
    // Under the pages, which cover it: lays out and draws an EPUB's equations, SVG and tables.
    if (s.reflowable) IslandHost(viewModel)
    Box(
        Modifier
            .fillMaxSize()
            .toggleOnEnter(overlay::toggle)
            .hazeSource(haze)
            .annotate(annotating, enabled = overlay.zoom == null)
            .interceptTaps(onTap)
            .bookPinch(s, overlay, viewModel)
            .edgeBounce(bounce, atStart = s.unit <= 0, atEnd = s.unit >= s.unitCount - 1, reversed = rtl)
    ) {
        CompositionLocalProvider(LocalReaderSounds provides riffling.sounds) {
            Book(s, images, viewModel, annotating, pageBounds)
        }
    }
    if (overlay.zoom == null && overlay.figure == null) PictureHints(links, framesFor)
    RiffleLanding(s, riffling, images, viewModel.thumbnails)
    CompositionLocalProvider(LocalGlassState provides haze) { PageOverlays(s, overlay.textPinch, viewModel) }
    overlay.zoom?.let {
        ZoomView(it, framesFor, images, tiles, viewModel::showTiles, annotating, onExit = { overlay.zoom = null })
    }
    // Selection and highlight menus: over the pages, and over the zoomed view too; under the bars.
    CompositionLocalProvider(LocalGlassState provides haze) { AnnotatingLayer(annotating, s) }
    if (overlay.zoom == null) {
        ReaderControls(s, navigation, viewModel, riffling, overlay, mark, marks, haze, detour, onBack, jump)
    }
    // Outside the chrome: a mark made just before the bars hide is still worth announcing.
    DetourNotice(detour)
    overlay.webLink?.let { uri ->
        WebLinkDialog(uri, onOpen = { runCatching { uriHandler.openUri(uri) } }, onDismiss = { overlay.webLink = null })
    }
    EdgeGuards()
}

/**
 * The pages, recreated when the layout or turn style changes (fold/unfold, Aa); the ViewModel keeps the position.
 */
@Composable
private fun Book(
    s: ReaderState.Ready,
    images: Map<Int, ImageBitmap>,
    viewModel: ReaderViewModel,
    annotating: Annotating,
    pageBounds: PageBounds
) {
    val rtl = LocalRightToLeft.current
    val narrating by viewModel.narrating.collectAsStateWithLifecycle()
    key(s.layout, s.coverAlone, s.tone, s.turn, rtl) {
        CompositionLocalProvider(
            LocalHighlights provides annotating.byPage(),
            LocalPageBounds provides pageBounds
        ) {
            MirroredBook(rtl && s.turn != PageTurn.SCROLL) {
                BookPages(s, images, s.pageAspect, narrating, viewModel::onUnitShown)
            }
        }
    }
}

/** Pinching zooms a PDF page and changes an EPUB's text size. */
private fun Modifier.bookPinch(s: ReaderState.Ready, overlay: Overlay, viewModel: ReaderViewModel) = if (s.reflowable) {
    val text = overlay.textPinch
    pinchGestures(
        onTransform = { _, _, zoom, _ ->
            text.begin(viewModel.userSettings.value.text.sizeSp)
            text.pinch(zoom)
        },
        onEnd = text::end
    )
} else {
    pinchGestures(overlay::pinch, overlay::endPinch)
}

/** The overlay, with the chrome clock following the auto-hide setting; an open panel holds the countdown. */
@Composable
private fun rememberOverlay(viewModel: ReaderViewModel): Overlay {
    val settings by viewModel.userSettings.collectAsStateWithLifecycle()
    val chrome = rememberChromeClock(settings.controlsAutoHide)
    val textPinch = rememberTextPinch(viewModel)
    val overlay = remember(chrome, textPinch) { Overlay(chrome, textPinch) }
    // Closing the panel starts the countdown again in full.
    LaunchedEffect(overlay.panel) { chrome.pause(ChromePause.PANEL, overlay.panel != Panel.NONE) }
    LaunchedEffect(chrome.shown) {
        if (!chrome.shown) overlay.riffleDismissed = false
    }
    return overlay
}

/**
 * Right to left: the whole book is mirrored (the curl lifts from the left, a spread starts on the right) and each page
 * mirrors itself back, so pages read normally. Scroll runs top to bottom either way and is never mirrored.
 */
@Composable
private fun MirroredBook(mirrored: Boolean, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().graphicsLayer { scaleX = if (mirrored) -1f else 1f }) {
        CompositionLocalProvider(LocalMirroredPages provides mirrored, content = content)
    }
}

/** Back, a jump from a panel, the bottom bar (the riffle scrubber), and the book, for footnotes and islands. */
internal class ChromeParts(
    val onBack: () -> Unit,
    val jump: (Int) -> Unit,
    val bar: @Composable () -> Unit,
    val book: ReaderViewModel
)

/**
 * The bars and their panels. They take the light or dark palette of the page colour, so they read well on it, and a
 * glass that shows on it (owner, 2026-10-18).
 */
@Suppress("LongMethod")
@Composable
internal fun BoxScope.Controls(
    s: ReaderState.Ready,
    navigation: BookNavigation,
    overlay: Overlay,
    mark: ChromeMark,
    parts: ChromeParts
) {
    FlowFolioPalette(dark = s.tone.dark) {
        CompositionLocalProvider(LocalGlassStyle provides glassOver(s.tone)) {
            PanelDismiss(overlay.panel, overlay::dismiss)
            val flowNow by parts.book.flow.collectAsStateWithLifecycle()
            val settings by parts.book.userSettings.collectAsStateWithLifecycle()
            val hits by parts.book.searchResults.collectAsStateWithLifecycle()
            val searchQuery by parts.book.searchQuery.collectAsStateWithLifecycle()
            val narrating by parts.book.narrating.collectAsStateWithLifecycle()
            val narratorRate by parts.book.narratorRate.collectAsStateWithLifecycle()
            val narratorContinuous by parts.book.narratorContinuous.collectAsStateWithLifecycle()
            val narratorVoice by parts.book.narratorVoice.collectAsStateWithLifecycle()
            val voicePackState by parts.book.voicePackState.collectAsStateWithLifecycle()
            val narratorError by parts.book.narratorError.collectAsStateWithLifecycle()
            val narrator = NarratorUi(
                narrating = narrating,
                rate = narratorRate,
                continuous = narratorContinuous,
                voices = parts.book.narratorVoices,
                voice = narratorVoice,
                packState = voicePackState,
                error = narratorError
            )
            val coachTargets = remember { CoachTargets() }
            CompositionLocalProvider(LocalCoachTargets provides coachTargets) {
                ReaderChrome(
                    s,
                    navigation,
                    flowNow,
                    controlsVisible = overlay.chrome.shown,
                    mark = mark,
                    actions = ChromeActions(parts.onBack, onPanel = overlay::open),
                    panel = overlay.panel,
                    bottomBarVisible = overlay.chrome.shown && overlay.panel == Panel.NONE && !overlay.riffleDismissed,
                    bottomBar = parts.bar
                )
            }
            ReaderPanels(
                overlay.panel,
                s,
                navigation,
                mark,
                parts.jump,
                onDismiss = overlay::dismiss,
                flow = flowNow,
                onFlow = parts.book::setFlow,
                onPanel = overlay::open,
                hits = hits,
                searchQuery = searchQuery,
                onSearchQuery = parts.book::search,
                onSearchJump = { hit ->
                    parts.book.jumpTo(hit)
                    overlay.dismiss()
                },
                narrator = narrator,
                onNarratorToggle = parts.book::toggleNarration,
                onNarratorRate = parts.book::setNarratorRate,
                onNarratorContinuous = parts.book::setNarratorContinuous,
                onNarratorVoice = parts.book::setNarratorVoice
            )
            AtmosphereGlimpse(parts.book)
            VoiceDownloadOverlay(voicePackState)
            FootnotePopover(overlay.note, parts.book::footnote, onDismiss = { overlay.note = null })
            overlay.figure?.let { FigureZoom(it, parts.book, onDismiss = { overlay.figure = null }) }
            // The coach marks (ADR-0052): once, after the first-book demo, with the chrome held up to point at.
            val coaching = settings.tipsEnabled && READER_DEMO in settings.seenGuides &&
                READER_COACH !in settings.seenGuides && overlay.panel == Panel.NONE
            LaunchedEffect(coaching) {
                if (coaching && !overlay.chrome.shown) overlay.chrome.toggle()
                overlay.chrome.pause(ChromePause.POPOVER, coaching)
            }
            if (coaching) ReaderCoach(coachTargets) { parts.book.markGuideSeen(READER_COACH) }
            // A first-use guide on the button just tapped: a centred window, not a spotlight, so it can't be clipped
            // or misaligned on any screen (owner, 2026-11-07).
            overlay.guide?.let { panel ->
                ReaderGuide.of(panel)?.takeIf { it.unseen(settings) }?.let { guide ->
                    FeatureSplash(
                        glyph = guide.glyph,
                        title = stringResource(guide.title),
                        body = stringResource(guide.body),
                        dismiss = stringResource(R.string.reader_demo_got_it),
                        onDismiss = {
                            parts.book.markGuideSeen(guide.id)
                            overlay.guide = null
                        },
                        neverShow = stringResource(R.string.reader_demo_never),
                        onNeverShow = {
                            parts.book.disableTips(guide.id)
                            overlay.guide = null
                        }
                    )
                }
            }
        }
    }
}

/** The pages in view: both pages of a spread (curl or slide), otherwise the page being read. */
internal fun pagesInView(s: ReaderState.Ready): List<Int> =
    if (s.layout == ReaderLayout.SPREAD && s.turn != PageTurn.SCROLL) {
        Spreads.pagesOf(s.unit, s.pageCount, s.coverAlone).ifEmpty { listOf(s.anchorPage) }
    } else {
        listOf(s.anchorPage)
    }

/**
 * The bookmark button's view of the pages in view: the ribbon on any of them, and marking puts a ribbon on the first
 * (the left page of a spread); removing takes ribbons off all of them.
 */
private fun chromeMark(
    s: ReaderState.Ready,
    marks: List<Bookmark>,
    bookmarks: ReaderBookmarksViewModel,
    notes: List<Annotation>,
    locatorOf: (Int) -> String?
): ChromeMark {
    val inView = pagesInView(s)
    val shown = marks.filter { it.page in inView }
    return ChromeMark(
        color = shown.firstOrNull()?.color,
        all = marks,
        notes = notes,
        onMark = { bookmarks.mark(inView.first(), it, locatorOf(inView.first()), shown.firstOrNull()) },
        onUnmark = { bookmarks.remove(shown.map { it.id }) }
    )
}

/**
 * Android's back gesture owns the screen edges. Reserve the middle of each side for page turns (200 dp is the system
 * maximum per edge); back still works above and below.
 */
@Composable
private fun BoxScope.EdgeGuards() {
    Spacer(Modifier.align(Alignment.CenterStart).size(EDGE_GUARD_WIDTH, EDGE_GUARD_HEIGHT).systemGestureExclusion())
    Spacer(Modifier.align(Alignment.CenterEnd).size(EDGE_GUARD_WIDTH, EDGE_GUARD_HEIGHT).systemGestureExclusion())
}

/**
 * Glass over the page: on dark papers a light frosted tint and a pale edge, since the app's dark tint over a dark page
 * reads as a hole rather than glass; on light papers the light palette's own glass.
 */
private fun glassOver(tone: PageTone): GlassStyle = if (tone.dark) {
    GlassStyle(
        Color.White.copy(alpha = DARK_PAPER_TINT),
        Color.White.copy(alpha = DARK_PAPER_EDGE)
    )
} else {
    GlassStyle()
}

private const val DARK_PAPER_TINT = 0.12f
private const val DARK_PAPER_EDGE = 0.22f
