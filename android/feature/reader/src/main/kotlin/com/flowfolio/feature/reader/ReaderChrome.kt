package com.flowfolio.feature.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowfolio.feature.reader.layout.ReaderLayout
import com.flowfolio.feature.reader.layout.Spreads
import kotlin.math.abs

/**
 * Reader chrome styled like print on the page (owner request, 2026-10-17): no background, grey type with a soft
 * shadow. The page number is always there: bottom left of the left page in a spread, bottom centre on a single page.
 * The book title (and Back) appear for [TITLE_VISIBLE_MS] after a tap in the centre of the screen: top right of the
 * right page in a spread, top centre on a single page.
 */
@Composable
internal fun BoxScope.ReaderChrome(state: ReaderState.Ready, titleVisible: Boolean, onBack: () -> Unit) {
    val spread = state.layout == ReaderLayout.SPREAD
    val shown = if (spread) {
        Spreads.pagesOf(state.unit, state.pageCount).joinToString("–") { (it + 1).toString() }
    } else {
        (state.unit + 1).toString()
    }
    Text(
        stringResource(R.string.reader_page_of, shown, state.pageCount),
        style = PrintStyle,
        modifier = Modifier
            .align(if (spread) Alignment.BottomStart else Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(horizontal = PAGE_MARGIN, vertical = FOOTER_MARGIN)
            .testTag(ReaderTestTags.INDICATOR)
    )
    AnimatedVisibility(titleVisible, Modifier.matchParentSize(), enter = fadeIn(), exit = fadeOut()) {
        Box(Modifier.fillMaxSize().statusBarsPadding()) {
            Text(
                state.title,
                style = PrintStyle.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(if (spread) Alignment.TopEnd else Alignment.TopCenter)
                    .padding(horizontal = PAGE_MARGIN, vertical = HEADER_MARGIN)
                    .widthIn(max = TITLE_MAX_WIDTH)
                    .testTag(ReaderTestTags.TITLE)
            )
            TextButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(top = 4.dp)) {
                Text(stringResource(R.string.reader_back), style = PrintStyle)
            }
        }
    }
}

/**
 * Calls [onTap] for a tap in the centre of the screen (the middle [CENTRE_ZONE] of the width and height) and keeps that
 * tap from turning a page: the release is consumed in the initial pass, before the page handlers see it. Drags that
 * start in the centre still turn pages.
 */
internal fun Modifier.centreTap(onTap: () -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val inCentre = abs(down.position.x - size.width / 2f) < size.width * CENTRE_ZONE / 2 &&
            abs(down.position.y - size.height / 2f) < size.height * CENTRE_ZONE / 2
        if (!inCentre) return@awaitEachGesture
        var tracking = true
        while (tracking) {
            val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
            val moved = change == null || (change.position - down.position).getDistance() > viewConfiguration.touchSlop
            if (!moved && !change.pressed &&
                change.uptimeMillis - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis
            ) {
                change.consume()
                onTap()
            }
            tracking = !moved && change.pressed
        }
    }
}

private val PrintStyle = TextStyle(
    color = Color(0xFF8E8E93),
    fontSize = 13.sp,
    letterSpacing = 0.3.sp,
    shadow = Shadow(color = Color.Black.copy(alpha = 0.22f), offset = Offset(0f, 1.5f), blurRadius = 6f)
)

internal const val TITLE_VISIBLE_MS = 10_000L
private const val CENTRE_ZONE = 0.3f
private val PAGE_MARGIN = 32.dp
private val FOOTER_MARGIN = 14.dp
private val HEADER_MARGIN = 14.dp
private val TITLE_MAX_WIDTH = 420.dp
