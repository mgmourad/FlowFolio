package com.flowfolio.feature.reader

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import com.flowfolio.core.model.PageTurn
import com.flowfolio.feature.reader.layout.ReaderLayout
import com.flowfolio.feature.reader.riffle.LandingTurn
import com.flowfolio.feature.reader.riffle.reducedMotion

/** A landing under way (spec 5.4): the reader already shows [to]; the turn plays over it from [from]. */
internal data class ReaderLanding(val from: Int, val to: Int)

/** The scrubber's view of the book, the landing under way, and how the scrubber moves the reader ([land]). */
internal class Riffling(val book: ReaderBook, val landing: MutableState<ReaderLanding?>, val land: (unit: Int) -> Unit)

/** The page lands with the turn in Curl (owner decision 4); Slide and Scroll jump straight there. */
@Composable
internal fun rememberRiffling(s: ReaderState.Ready, navigation: BookNavigation, viewModel: ReaderViewModel): Riffling {
    val book = rememberReaderBook(s, navigation)
    val landing = remember { mutableStateOf<ReaderLanding?>(null) }
    return Riffling(book, landing) { unit ->
        if (s.turn == PageTurn.CURL && unit != s.unit) landing.value = ReaderLanding(s.unit, unit)
        viewModel.goToPage(book.anchorOf(unit))
    }
}

/**
 * The landing turn over the pages, in Curl (owner decision 4): the riffle's sheets drawn from the pages already
 * rendered when there are some, else from the loupe's thumbnails. The book below shows the target throughout, so the
 * last sheet settles onto it rather than into a pagecurl turn (a deviation from the mock, noted in the spec).
 */
@Composable
internal fun LandingLayer(
    s: ReaderState.Ready,
    book: ReaderBook,
    landing: ReaderLanding,
    images: Map<Int, ImageBitmap>,
    thumbnails: ReaderThumbnails?,
    onDone: () -> Unit
) {
    val rtl = LocalRightToLeft.current
    val spread = s.layout == ReaderLayout.SPREAD
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screen = IntSize(constraints.maxWidth, constraints.maxHeight)
        val area =
            pageArea(
                PageFrames.of(s, screen, rtl) { page ->
                    images[page]?.let { IntSize(it.width, it.height) }
                },
                screen,
                spread
            )
                ?: return@BoxWithConstraints
        val density = LocalDensity.current
        Box(
            Modifier
                .offset { IntOffset(area.left, area.top) }
                .size(with(density) { area.width.toDp() }, with(density) { area.height.toDp() })
        ) {
            LandingTurn(book, landing.from, landing.to, spread, reducedMotion(), page = { page, m ->
                LandingPage(page, m, images, thumbnails)
            }, onDone = onDone)
        }
    }
}

/** The pages' bounds; a spread's are made even about the middle, so the sheets turn on the spine with one page. */
private fun pageArea(frames: List<PageFrames.Frame>, screen: IntSize, spread: Boolean): IntRect? {
    if (frames.isEmpty()) return null
    var left = frames.minOf { it.x }
    var right = frames.maxOf { it.x + it.w }
    if (spread) {
        val half = maxOf(screen.width / 2 - left, right - screen.width / 2)
        left = screen.width / 2 - half
        right = screen.width / 2 + half
    }
    return IntRect(left, frames.minOf { it.y }, right, frames.maxOf { it.y + it.h })
}

/** One page of a sheet: the full page if it is rendered, else its thumbnail, on the book's paper. */
@Composable
private fun LandingPage(page: Int?, modifier: Modifier, images: Map<Int, ImageBitmap>, thumbnails: ReaderThumbnails?) {
    val image by produceState(page?.let { images[it] ?: thumbnails?.cached(it) }, page) {
        if (value == null && page != null) value = thumbnails?.load(page)
    }
    Box(modifier.background(PAPER)) {
        image?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
    }
}
