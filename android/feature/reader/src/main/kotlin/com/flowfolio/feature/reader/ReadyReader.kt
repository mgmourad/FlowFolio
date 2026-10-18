package com.flowfolio.feature.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flowfolio.core.designsystem.FlowFolioPalette
import com.flowfolio.core.designsystem.GlassStyle
import com.flowfolio.core.designsystem.LocalGlassState
import com.flowfolio.core.designsystem.LocalGlassStyle
import com.flowfolio.core.model.Annotation
import com.flowfolio.core.model.Bookmark
import com.flowfolio.core.model.PageTurn
import com.flowfolio.feature.reader.layout.ReaderLayout
import com.flowfolio.feature.reader.layout.Spreads
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay

/**
 * What floats over the book. A centre tap shows the controls for a while (each use of them restarts the clock);
 * another centre tap hides them sooner. They stay up while the contents list or Go to page is open.
 */
@Stable
private class Overlay {
    var shownAt by mutableStateOf<Long?>(null)
    var panel by mutableStateOf(Panel.NONE)
    var webLink by mutableStateOf<String?>(null)

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
        shownAt = if (shownAt == null) System.nanoTime() else null
    }

    fun touch() {
        shownAt = System.nanoTime()
    }

    /** Opens [which], or closes it when it is already open (a second tap on its button). */
    fun open(which: Panel) {
        panel = if (panel == which) Panel.NONE else which
    }

    /** Acts on a routed tap; true when it was not a page turn (so the page should not see it). */
    fun handle(target: PageFrames.TapTarget, jump: (Int) -> Unit): Boolean {
        when (target) {
            is PageFrames.TapTarget.Jump -> jump(target.page)
            is PageFrames.TapTarget.Web -> webLink = target.uri
            PageFrames.TapTarget.Centre -> toggle()
            PageFrames.TapTarget.Page -> Unit
        }
        return target != PageFrames.TapTarget.Page
    }

    fun closeAll() {
        panel = Panel.NONE
        shownAt = null
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
    onBack: () -> Unit
) {
    val links by viewModel.links.collectAsStateWithLifecycle()
    val rtl = LocalRightToLeft.current
    val marks by bookmarks.bookmarks.collectAsStateWithLifecycle()
    val bounce = remember { EdgeBounce() }
    val haze = rememberHazeState()
    val tiles by viewModel.tiles.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val overlay = remember { Overlay() }
    AutoHide(overlay)
    LaunchedEffect(s.unit, s.layout, s.coverAlone) { overlay.zoom = null } // a jump or fold/unfold leaves zoom
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
    val mark = chromeMark(s, marks, bookmarks, annotating.all)
    // A tap on a highlight opens its menu; otherwise links, the centre and page turns as before.
    val onTap = { tap: Offset, size: IntSize ->
        annotating.tap(tap, size) || overlay.handle(PageFrames.route(tap, size, framesFor(size), links), jump)
    }
    Box(
        Modifier
            .fillMaxSize()
            .hazeSource(haze)
            .annotate(annotating, enabled = overlay.zoom == null)
            .interceptTaps(onTap)
            .pinchGestures(overlay::pinch, overlay::endPinch)
            .edgeBounce(bounce, atStart = s.unit <= 0, atEnd = s.unit >= s.unitCount - 1, reversed = rtl)
    ) {
        // Recreate the page state when the layout or turn style changes (fold/unfold, Aa); the ViewModel keeps the
        // position.
        key(s.layout, s.coverAlone, s.tone, s.turn, rtl) {
            CompositionLocalProvider(
                LocalHighlights provides annotating.byPage(),
                LocalPageBounds provides pageBounds
            ) {
                MirroredBook(rtl && s.turn != PageTurn.SCROLL) {
                    BookPages(s, images, s.pageAspect, viewModel::onUnitShown)
                }
            }
        }
    }
    overlay.zoom?.let {
        ZoomView(it, framesFor, images, tiles, viewModel::showTiles, annotating, onExit = { overlay.zoom = null })
    }
    // Selection and highlight menus: over the pages, and over the zoomed view too; under the bars.
    CompositionLocalProvider(LocalGlassState provides haze) { AnnotatingLayer(annotating, s) }
    if (overlay.zoom == null) {
        CompositionLocalProvider(LocalGlassState provides haze) { Controls(s, navigation, overlay, mark, onBack, jump) }
    }
    overlay.webLink?.let { uri ->
        WebLinkDialog(uri, onOpen = { runCatching { uriHandler.openUri(uri) } }, onDismiss = { overlay.webLink = null })
    }
    EdgeGuards()
}

/** Hides the bars [CONTROLS_VISIBLE_MS] after they were last used, unless a panel is open. */
@Composable
private fun AutoHide(overlay: Overlay) {
    LaunchedEffect(overlay.shownAt, overlay.panel) {
        if (overlay.shownAt != null && overlay.panel == Panel.NONE) {
            delay(CONTROLS_VISIBLE_MS)
            overlay.shownAt = null
        }
    }
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

/**
 * The bars and their panels. They take the light or dark palette of the page colour, so they read well on it, and a
 * glass that shows on it (owner, 2026-10-18).
 */
@Composable
private fun BoxScope.Controls(
    s: ReaderState.Ready,
    navigation: BookNavigation,
    overlay: Overlay,
    mark: ChromeMark,
    onBack: () -> Unit,
    jump: (Int) -> Unit
) {
    FlowFolioPalette(dark = s.tone.dark) {
        CompositionLocalProvider(LocalGlassStyle provides glassOver(s.tone)) {
            PanelDismiss(overlay.panel) { overlay.panel = Panel.NONE }
            Chrome(s, navigation, overlay, mark, onBack, jump)
            ReaderPanels(overlay.panel, s, navigation, mark, jump, onDismiss = { overlay.panel = Panel.NONE })
        }
    }
}

@Composable
private fun BoxScope.Chrome(
    s: ReaderState.Ready,
    navigation: BookNavigation,
    overlay: Overlay,
    mark: ChromeMark,
    onBack: () -> Unit,
    jump: (Int) -> Unit
) {
    ReaderChrome(
        s,
        navigation,
        controlsVisible = overlay.shownAt != null,
        mark = mark,
        actions = ChromeActions(
            onBack = onBack,
            onPanel = overlay::open,
            onScrub = jump,
            onInteract = overlay::touch
        )
    )
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
    notes: List<Annotation>
): ChromeMark {
    val inView = pagesInView(s)
    return ChromeMark(
        color = marks.firstOrNull { it.page in inView }?.color,
        all = marks,
        notes = notes,
        onMark = { bookmarks.mark(inView.first(), it) },
        onUnmark = { inView.forEach(bookmarks::unmark) }
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
