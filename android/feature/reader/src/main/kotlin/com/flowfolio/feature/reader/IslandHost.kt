package com.flowfolio.feature.reader

import android.graphics.Bitmap
import android.util.Log
import android.webkit.WebView
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * The island WebView (ADR-0019), kept in the reader's window under the pages (which cover it), so the system treats it
 * as shown. It takes the reader's island work one piece at a time: lay out, then draw, one island per frame so page
 * turns keep their frames.
 */
@Composable
internal fun IslandHost(viewModel: ReaderViewModel) {
    val work by viewModel.islandWork.collectAsStateWithLifecycle()
    val archive = viewModel.epubArchive ?: return
    var renderer by remember { mutableStateOf<IslandRenderer?>(null) }
    // As wide as the text column, so tables and equations lay out as they will be shown.
    var columnPx by remember { mutableIntStateOf(0) }
    work?.let { columnPx = it.widthPx }
    val width = with(LocalDensity.current) { columnPx.coerceAtLeast(1).toDp() }
    AndroidView(
        factory = { context ->
            IslandRenderer.enableWholeDocumentDraw()
            WebView(context).also { renderer = IslandRenderer(it, archive) }
        },
        modifier = Modifier.size(width, HOST_HEIGHT),
        onRelease = { it.destroy() }
    )
    LaunchedEffect(work, renderer) {
        val r = renderer ?: return@LaunchedEffect
        val w = work ?: return@LaunchedEffect r.idle()
        // Let the WebView take the column's width first (it is 1 px wide until the first work arrives); loading into
        // it before that sometimes hung the first measure after a cold start.
        withFrameNanos { }
        // Drawing loads only the islands it draws, not the whole chapter (hundreds of equations): far less for the
        // WebView to lay out and hold. Their sizes match the chapter's, with the same width and styles.
        val page = if (w.render.isEmpty()) w else w.copy(items = w.items.filter { it.id in w.render })
        val boxes = measureWithRetry(r, page)
        val images = HashMap<Int, Bitmap>()
        for (id in w.render) {
            val box = boxes[id] ?: continue
            var bmp = r.render(box)
            // Now and then the WebView has nothing drawn for a frame; an island that stays blank is kept as it is.
            repeat(BLANK_RETRIES) {
                if (IslandRenderer.isBlank(bmp)) {
                    withFrameNanos { }
                    bmp = r.render(box)
                }
            }
            images[id] = bmp
            withFrameNanos { } // one island per frame
        }
        viewModel.onIslandsDone(w, boxes, images)
    }
}

/**
 * Lays [work] out, trying again once when the WebView hangs (seen now and then on the first page after a cold start);
 * after that the chapter's islands keep placeholder sizes.
 */
private suspend fun measureWithRetry(r: IslandRenderer, work: IslandWork): Map<Int, IslandBox> {
    repeat(MEASURE_ATTEMPTS) { attempt ->
        try {
            return r.measure(work)
        } catch (e: IslandTimeoutException) {
            Log.w(TAG, "islands in ${work.chapterPath}: ${e.message} (attempt ${attempt + 1})")
            r.stop()
        }
    }
    return emptyMap()
}

private val HOST_HEIGHT = 400.dp
private const val MEASURE_ATTEMPTS = 2
private const val BLANK_RETRIES = 3
private const val TAG = "FlowFolioIslands"
