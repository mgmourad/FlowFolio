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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flowfolio.core.designsystem.LocalGlassState
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
    val marks by bookmarks.bookmarks.collectAsStateWithLifecycle()
    val mark = chromeMark(s, marks, bookmarks)
    val bounce = remember { EdgeBounce() }
    val haze = rememberHazeState()
    val tiles by viewModel.tiles.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val overlay = remember { Overlay() }
    LaunchedEffect(overlay.shownAt, overlay.panel) {
        if (overlay.shownAt != null && overlay.panel == Panel.NONE) {
            delay(CONTROLS_VISIBLE_MS)
            overlay.shownAt = null
        }
    }
    LaunchedEffect(s.unit, s.layout, s.coverAlone) { overlay.zoom = null } // a jump or fold/unfold leaves zoom
    val jump = { page: Int ->
        viewModel.goToPage(page)
        overlay.closeAll()
    }
    val framesFor = { size: IntSize ->
        PageFrames.of(s, size) { page -> images[page]?.let { IntSize(it.width, it.height) } }
    }
    val onTap = { tap: Offset, size: IntSize ->
        val target = PageFrames.route(tap, size, framesFor(size), links)
        when (target) {
            is PageFrames.TapTarget.Jump -> jump(target.page)
            is PageFrames.TapTarget.Web -> overlay.webLink = target.uri
            PageFrames.TapTarget.Centre -> overlay.toggle()
            PageFrames.TapTarget.Page -> Unit
        }
        target != PageFrames.TapTarget.Page
    }
    Box(
        Modifier
            .fillMaxSize()
            .hazeSource(haze)
            .interceptTaps(onTap)
            .pinchGestures(overlay::pinch, overlay::endPinch)
            .edgeBounce(bounce, atStart = s.unit <= 0, atEnd = s.unit >= s.unitCount - 1)
    ) {
        // Recreate the page state when the layout or turn style changes (fold/unfold, Aa); the ViewModel keeps the
        // position.
        key(s.layout, s.coverAlone, s.tone, s.turn) {
            BookPages(s, images, s.pageAspect, viewModel::onUnitShown)
        }
    }
    overlay.zoom?.let { ZoomView(it, framesFor, images, tiles, viewModel::showTiles, onExit = { overlay.zoom = null }) }
    if (overlay.zoom == null) {
        CompositionLocalProvider(LocalGlassState provides haze) {
            Chrome(s, navigation, overlay, mark, onBack, jump)
        }
    }
    Panels(overlay.panel, s, navigation, jump, onDismiss = { overlay.panel = Panel.NONE })
    overlay.webLink?.let { uri ->
        WebLinkDialog(uri, onOpen = { runCatching { uriHandler.openUri(uri) } }, onDismiss = { overlay.webLink = null })
    }
    EdgeGuards()
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
            onContents = { overlay.panel = Panel.CONTENTS },
            onGoTo = { overlay.panel = Panel.GO_TO },
            onSettings = { overlay.panel = Panel.SETTINGS },
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
private fun chromeMark(s: ReaderState.Ready, marks: List<Bookmark>, bookmarks: ReaderBookmarksViewModel): ChromeMark {
    val inView = pagesInView(s)
    return ChromeMark(
        color = marks.firstOrNull { it.page in inView }?.color,
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

/** What is open on top of the reader. */
private enum class Panel { NONE, CONTENTS, GO_TO, SETTINGS }

@Composable
private fun Panels(
    panel: Panel,
    s: ReaderState.Ready,
    navigation: BookNavigation,
    jump: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    when (panel) {
        Panel.NONE -> Unit
        Panel.CONTENTS -> ContentsSheet(navigation.outline, navigation.labels, s.anchorPage, jump, onDismiss)
        Panel.GO_TO -> GoToPageDialog(navigation.labels, s.pageCount, jump, onDismiss)
        Panel.SETTINGS -> ReaderSettingsSheet(onDismiss)
    }
}
