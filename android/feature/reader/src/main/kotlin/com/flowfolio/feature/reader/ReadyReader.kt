package com.flowfolio.feature.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.runtime.Composable
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
import com.flowfolio.feature.reader.layout.ReaderLayout
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
    onBack: () -> Unit
) {
    val links by viewModel.links.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val overlay = remember { Overlay() }
    LaunchedEffect(overlay.shownAt, overlay.panel) {
        if (overlay.shownAt != null && overlay.panel == Panel.NONE) {
            delay(CONTROLS_VISIBLE_MS)
            overlay.shownAt = null
        }
    }
    val jump = { page: Int ->
        viewModel.goToPage(page)
        overlay.closeAll()
    }
    val onTap = { tap: Offset, size: IntSize ->
        val frames = PageFrames.of(s, size) { page -> images[page]?.let { IntSize(it.width, it.height) } }
        val target = PageFrames.route(tap, size, frames, links)
        when (target) {
            is PageFrames.TapTarget.Jump -> jump(target.page)
            is PageFrames.TapTarget.Web -> overlay.webLink = target.uri
            PageFrames.TapTarget.Centre -> overlay.toggle()
            PageFrames.TapTarget.Page -> Unit
        }
        target != PageFrames.TapTarget.Page
    }
    Box(Modifier.fillMaxSize().interceptTaps(onTap)) {
        // Recreate the curl state when the layout flips (fold/unfold); the ViewModel keeps the position.
        key(s.layout) {
            when (s.layout) {
                ReaderLayout.SINGLE -> SinglePageReader(s, images, viewModel::onUnitShown)
                ReaderLayout.SPREAD -> SpreadReader(s, images, viewModel::onUnitShown)
            }
        }
    }
    ReaderChrome(
        s,
        navigation,
        controlsVisible = overlay.shownAt != null,
        actions = ChromeActions(
            onBack = onBack,
            onContents = { overlay.panel = Panel.CONTENTS },
            onGoTo = { overlay.panel = Panel.GO_TO },
            onScrub = jump,
            onInteract = overlay::touch
        )
    )
    Panels(overlay.panel, s, navigation, jump, onDismiss = { overlay.panel = Panel.NONE })
    overlay.webLink?.let { uri ->
        WebLinkDialog(uri, onOpen = { runCatching { uriHandler.openUri(uri) } }, onDismiss = { overlay.webLink = null })
    }
    EdgeGuards()
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
private enum class Panel { NONE, CONTENTS, GO_TO }

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
    }
}
