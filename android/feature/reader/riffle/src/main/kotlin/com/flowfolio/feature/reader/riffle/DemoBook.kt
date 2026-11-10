@file:Suppress("MatchingDeclarationName") // the demo's book: its pages, and the landing it shows

package com.flowfolio.feature.reader.riffle

import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.flowfolio.core.designsystem.FlowFolio

/** A landing in progress: from the unit shown before to the one now shown. */
internal data class Landing(val from: Int, val to: Int)

/**
 * The demo's book (the reader supplies its own pages in R5): a spread, or one page, of skeleton pages for
 * [unit], with the [landing] turn drawn over it. Taps in the middle third call [onMiddle] (the chrome), the outer
 * thirds turn a page with [onTurn] (-1 or 1) without showing the chrome (spec 3.4).
 */
@Composable
internal fun DemoSpread(
    book: BookGeometry,
    unit: Int,
    spread: Boolean,
    landing: Landing?,
    onLanded: () -> Unit,
    onMiddle: () -> Unit,
    onTurn: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val reduced = reducedMotion()
    Box(
        modifier.pointerInput(Unit) {
            detectTapGestures { tap ->
                val third = size.width / THIRDS
                when {
                    tap.x < third -> onTurn(-1)
                    tap.x > 2 * third -> onTurn(1)
                    else -> onMiddle()
                }
            }
        }
    ) {
        if (spread) {
            val (left, right) = miniSpread(book, book.pagesOf(unit))
            Row(Modifier.fillMaxSize()) {
                DemoPage(left, Modifier.weight(1f).fillMaxHeight())
                DemoPage(right, Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            DemoPage(book.pagesOf(unit).first, Modifier.fillMaxSize())
        }
        landing?.let {
            LandingTurn(book, it.from, it.to, spread, reduced, page = { page, m ->
                DemoPage(page, m)
            }, onDone = onLanded)
        }
    }
}

/** One page: paper, a skeleton of text, and its printed number at the foot. */
@Composable
internal fun DemoPage(page: Int?, modifier: Modifier) {
    val palette = FlowFolio.palette
    if (page == null) {
        Box(modifier.background(palette.backgroundDeep))
        return
    }
    Box(modifier.background(palette.paper)) {
        Canvas(Modifier.fillMaxSize()) { drawSkeleton(page, palette.paperInk, palette.ink, isRecto = page % 2 == 0) }
        Text(
            (page + 1).toString(),
            Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp),
            color = palette.paperInk.copy(alpha = 0.55f)
        )
    }
}

/** The system's "remove animations" (animator duration scale 0): land with a crossfade, keep the loupe still. */
@Composable
fun reducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
}

private const val THIRDS = 3
