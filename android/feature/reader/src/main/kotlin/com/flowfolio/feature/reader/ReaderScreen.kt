package com.flowfolio.feature.reader

import android.content.ComponentCallbacks2
import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flowfolio.core.pagecurl.page.PageCurl
import com.flowfolio.core.pagecurl.page.PageCurlState
import com.flowfolio.core.pagecurl.page.rememberPageCurlState
import com.flowfolio.feature.reader.layout.ReaderLayout
import com.flowfolio.feature.reader.layout.Spreads
import kotlinx.coroutines.launch

/** Original-mode PDF reader: page curl, single page or two-page spread (Phase 1, milestone 3). */
@Composable
fun ReaderScreen(onBack: () -> Unit, viewModel: ReaderViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val images by viewModel.pageImages.collectAsStateWithLifecycle()
    TrimMemoryEffect(viewModel::onTrimMemory)

    BoxWithConstraints(Modifier.fillMaxSize().background(PAPER)) {
        val viewport = Viewport(constraints.maxWidth, constraints.maxHeight, maxWidth.value, maxHeight.value)
        LaunchedEffect(viewport) { viewModel.onViewport(viewport) }

        when (val s = state) {
            ReaderState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

            is ReaderState.NeedsPassword -> PasswordPrompt(s.wrongPassword, viewModel::submitPassword, onBack)

            is ReaderState.Failed -> Message(
                stringResource(if (s.missingFile) R.string.reader_missing else R.string.reader_damaged),
                onBack
            )

            is ReaderState.Ready -> {
                // Recreate the curl state when the layout flips (fold/unfold); the ViewModel keeps the position.
                key(s.layout) {
                    when (s.layout) {
                        ReaderLayout.SINGLE -> SinglePageReader(s, images, viewModel::onUnitShown)
                        ReaderLayout.SPREAD -> SpreadReader(s, images, viewModel::onUnitShown)
                    }
                }
                Chrome(s, onBack)
                // Android's back gesture owns the screen edges. Reserve the middle of each side for page turns
                // (200 dp is the system maximum per edge); back still works above and below.
                Spacer(
                    Modifier.align(
                        Alignment.CenterStart
                    ).width(EDGE_GUARD_WIDTH).height(EDGE_GUARD_HEIGHT).systemGestureExclusion()
                )
                Spacer(
                    Modifier.align(
                        Alignment.CenterEnd
                    ).width(EDGE_GUARD_WIDTH).height(EDGE_GUARD_HEIGHT).systemGestureExclusion()
                )
            }
        }
    }
}

@Composable
private fun SinglePageReader(state: ReaderState.Ready, images: Map<Int, ImageBitmap>, onUnitShown: (Int) -> Unit) {
    val curl = rememberPageCurlState(initialCurrent = state.unit)
    ReportUnit(curl, onUnitShown)
    PageCurl(
        count = state.unitCount,
        state = curl,
        gradientShadow = true,
        modifier = Modifier.fillMaxSize().testTag(ReaderTestTags.PAGE)
    ) { page ->
        PagePane(images[page], Alignment.Center)
    }
}

/**
 * Book spread: the left page is static, the right half is the curl. Each curl "page" is a spread; the back of the
 * turning sheet shows the next spread's left page (vendored pagecurl change, ADR-0011). Turning back starts on the
 * left page (tap or drag right).
 */
@Composable
private fun SpreadReader(state: ReaderState.Ready, images: Map<Int, ImageBitmap>, onUnitShown: (Int) -> Unit) {
    val curl = rememberPageCurlState(initialCurrent = state.unit)
    val scope = rememberCoroutineScope()
    val latestImages by rememberUpdatedState(images)
    ReportUnit(curl, onUnitShown)
    Row(Modifier.fillMaxSize().testTag(ReaderTestTags.PAGE)) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .pointerInput(curl) { detectTapGestures { scope.launch { curl.prev() } } }
                .pointerInput(curl) {
                    var dragged = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { dragged = 0f },
                        onHorizontalDrag = { _, delta -> dragged += delta },
                        onDragEnd = { if (dragged > BACK_DRAG_PX) scope.launch { curl.prev() } }
                    )
                }
        ) {
            PagePane(images[Spreads.leftOf(state.unit, state.pageCount)], Alignment.CenterEnd)
        }
        Box(Modifier.weight(1f).fillMaxHeight()) {
            PageCurl(
                count = state.unitCount,
                state = curl,
                gradientShadow = true,
                modifier = Modifier.fillMaxSize(),
                backPage = { unit -> latestImages[Spreads.leftOf(unit + 1, state.pageCount)] }
            ) { unit ->
                PagePane(images[Spreads.rightOf(unit, state.pageCount)], Alignment.CenterStart)
            }
        }
    }
}

@Composable
private fun ReportUnit(curl: PageCurlState, onUnitShown: (Int) -> Unit) {
    val callback by rememberUpdatedState(onUnitShown)
    LaunchedEffect(curl) { snapshotFlow { curl.current }.collect { callback(it) } }
}

/** One page image at its exact rendered size (no scaling), on paper. Missing images show blank paper. */
@Composable
private fun PagePane(image: ImageBitmap?, alignment: Alignment) {
    Box(Modifier.fillMaxSize().background(PAPER), contentAlignment = alignment) {
        if (image != null) Image(image, contentDescription = null, contentScale = ContentScale.None)
    }
}

@Composable
private fun Chrome(state: ReaderState.Ready, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
        TextButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart)) {
            Text(stringResource(R.string.reader_back))
        }
        val shown = if (state.layout == ReaderLayout.SPREAD) {
            Spreads.pagesOf(state.unit, state.pageCount).joinToString("–") { (it + 1).toString() }
        } else {
            (state.unit + 1).toString()
        }
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = INDICATOR_ALPHA),
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp).testTag(ReaderTestTags.INDICATOR)
        ) {
            Text(
                stringResource(R.string.reader_page_of, shown, state.pageCount),
                Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun Message(text: String, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        Text(text, color = Color.Black, style = MaterialTheme.typography.bodyLarge)
        TextButton(onClick = onBack) { Text(stringResource(R.string.reader_back)) }
    }
}

@Composable
private fun TrimMemoryEffect(onTrim: () -> Unit) {
    val context = LocalContext.current
    val latest by rememberUpdatedState(onTrim)
    DisposableEffect(context) {
        val callbacks = object : ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) {
                // Only the levels that mean "the system is short on memory while we are visible".
                if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW &&
                    level < ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN
                ) {
                    latest()
                }
            }

            override fun onConfigurationChanged(newConfig: Configuration) = Unit

            @Deprecated("Deprecated in Java")
            override fun onLowMemory() = latest()
        }
        context.registerComponentCallbacks(callbacks)
        onDispose { context.unregisterComponentCallbacks(callbacks) }
    }
}

private val PAPER = Color.White
private const val INDICATOR_ALPHA = 0.85f
private const val BACK_DRAG_PX = 48f
private val EDGE_GUARD_WIDTH = 32.dp
private val EDGE_GUARD_HEIGHT = 200.dp
