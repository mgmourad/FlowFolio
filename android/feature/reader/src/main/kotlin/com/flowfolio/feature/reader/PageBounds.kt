package com.flowfolio.feature.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import com.flowfolio.core.model.PageTurn
import kotlin.math.roundToInt

/**
 * Where each page image actually is on screen, as laid out: reported by the page panes themselves, so selecting text
 * works the same in every turn style (curl, slide, scroll), mid-slide, and right to left, without re-deriving the
 * layout. Plain map, not observable: it is read when a finger needs it.
 */
internal class PageBounds {
    private val rects = HashMap<Int, Rect>()

    fun put(page: Int, rect: Rect) {
        rects[page] = rect
    }

    fun remove(page: Int) {
        rects.remove(page)
    }

    /**
     * The pages showing on a [screen] of this size: in Scroll every page on screen; otherwise only the pages in view
     * ([s]), since the curl keeps the next page laid out under the current one.
     */
    fun frames(s: ReaderState.Ready, screen: IntSize): List<PageFrames.Frame> {
        val inView = pagesInView(s)
        val bounds = Rect(0f, 0f, screen.width.toFloat(), screen.height.toFloat())
        return rects.filter { (page, rect) ->
            if (s.turn == PageTurn.SCROLL) rect.overlaps(bounds) else page in inView
        }.map { (page, r) ->
            PageFrames.Frame(page, r.left.roundToInt(), r.top.roundToInt(), r.width.roundToInt(), r.height.roundToInt())
        }
    }
}

internal val LocalPageBounds = staticCompositionLocalOf<PageBounds?> { null }

/** Reports this page image's place on screen to [LocalPageBounds] while it is shown. */
@Composable
internal fun Modifier.reportBounds(page: Int): Modifier {
    val bounds = LocalPageBounds.current ?: return this
    DisposableEffect(bounds, page) { onDispose { bounds.remove(page) } }
    return onGloballyPositioned { bounds.put(page, it.boundsInRoot()) }
}

/** [frames] as the zoomed view shows them: scaled by [scale] about the top left, then moved by [offset]. */
internal fun List<PageFrames.Frame>.zoomed(zoom: ZoomState): List<PageFrames.Frame> = map {
    PageFrames.Frame(
        it.page,
        (it.x * zoom.scale + zoom.offset.x).roundToInt(),
        (it.y * zoom.scale + zoom.offset.y).roundToInt(),
        (it.w * zoom.scale).roundToInt(),
        (it.h * zoom.scale).roundToInt()
    )
}
