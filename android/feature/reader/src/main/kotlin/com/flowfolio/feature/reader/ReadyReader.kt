package com.flowfolio.feature.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import com.flowfolio.feature.reader.layout.ReaderLayout
import kotlinx.coroutines.delay

/** The open book: page turns, the centre-tap controls and the panels they open. */
@Composable
internal fun BoxScope.ReadyReader(
    s: ReaderState.Ready,
    images: Map<Int, ImageBitmap>,
    navigation: BookNavigation,
    viewModel: ReaderViewModel,
    onBack: () -> Unit
) {
    // A centre tap shows the controls for a while (each use of them restarts the clock); another centre
    // tap hides them sooner. They stay up while the contents list or Go to page is open.
    var controlsShownAt by remember { mutableStateOf<Long?>(null) }
    var panel by remember { mutableStateOf(Panel.NONE) }
    LaunchedEffect(controlsShownAt, panel) {
        if (controlsShownAt != null && panel == Panel.NONE) {
            delay(CONTROLS_VISIBLE_MS)
            controlsShownAt = null
        }
    }
    val jump = { page: Int ->
        viewModel.goToPage(page)
        panel = Panel.NONE
        controlsShownAt = null
    }
    Box(
        Modifier.fillMaxSize().centreTap {
            controlsShownAt = if (controlsShownAt == null) System.nanoTime() else null
        }
    ) {
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
        controlsVisible = controlsShownAt != null,
        actions = ChromeActions(
            onBack = onBack,
            onContents = { panel = Panel.CONTENTS },
            onGoTo = { panel = Panel.GO_TO },
            onScrub = jump,
            onInteract = { controlsShownAt = System.nanoTime() }
        )
    )
    Panels(panel, s, navigation, jump, onDismiss = { panel = Panel.NONE })
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
