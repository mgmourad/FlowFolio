package com.flowfolio.feature.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowfolio.feature.reader.layout.ReaderLayout
import com.flowfolio.feature.reader.layout.Spreads
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Reader chrome styled like print on the page (owner request, 2026-10-17): no background, grey type with a soft
 * shadow. The page number is always there: bottom left of the left page in a spread, bottom centre on a single page.
 * A tap in the centre of the screen shows the controls for [CONTROLS_VISIBLE_MS]: the book title (top right of the
 * right page, top centre on a single page), Back, Contents, Go to page, and a page scrubber.
 */
@Composable
internal fun BoxScope.ReaderChrome(
    state: ReaderState.Ready,
    navigation: BookNavigation,
    controlsVisible: Boolean,
    actions: ChromeActions
) {
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
    AnimatedVisibility(controlsVisible, Modifier.matchParentSize(), enter = fadeIn(), exit = fadeOut()) {
        Box(Modifier.fillMaxSize()) {
            TopControls(state.title, spread, actions)
            Scrubber(
                state,
                navigation,
                actions,
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = SCRUBBER_BOTTOM)
                    .fillMaxWidth(if (spread) SPREAD_SCRUBBER_WIDTH else SINGLE_SCRUBBER_WIDTH)
            )
        }
    }
}

@Composable
private fun BoxScope.TopControls(title: String, spread: Boolean, actions: ChromeActions) {
    val titleText = @Composable { modifier: Modifier ->
        Text(
            title,
            style = PrintStyle.copy(fontWeight = FontWeight.Medium),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = modifier.testTag(ReaderTestTags.TITLE)
        )
    }
    val buttons = @Composable {
        PrintButton(stringResource(R.string.reader_contents), actions.onContents, ReaderTestTags.CONTENTS)
        PrintButton(stringResource(R.string.reader_go_to), actions.onGoTo, ReaderTestTags.GO_TO)
    }
    if (spread) {
        Row(Modifier.align(Alignment.TopStart).statusBarsPadding().padding(top = 4.dp)) {
            PrintButton(stringResource(R.string.reader_back), actions.onBack)
            buttons()
        }
        titleText(
            Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(horizontal = PAGE_MARGIN, vertical = HEADER_MARGIN)
                .widthIn(max = TITLE_MAX_WIDTH)
        )
    } else {
        Row(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PrintButton(stringResource(R.string.reader_back), actions.onBack)
            titleText(Modifier.weight(1f).padding(horizontal = 8.dp))
            buttons()
        }
    }
}

@Composable
private fun PrintButton(text: String, onClick: () -> Unit, tag: String? = null) {
    TextButton(onClick = onClick, modifier = if (tag != null) Modifier.testTag(tag) else Modifier) {
        Text(text, style = PrintStyle)
    }
}

/** Drag to preview a page (its printed label shows above), release to go there. */
@Composable
private fun Scrubber(state: ReaderState.Ready, navigation: BookNavigation, actions: ChromeActions, modifier: Modifier) {
    val last = (state.pageCount - 1).coerceAtLeast(1)
    var dragging by remember { mutableStateOf<Float?>(null) }
    val page = (dragging ?: state.anchorPage.toFloat()).roundToInt().coerceIn(0, last)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            if (dragging != null) {
                stringResource(
                    R.string.reader_scrub_preview,
                    PageLookup.labelOf(page, navigation.labels),
                    page + 1,
                    state.pageCount
                )
            } else {
                ""
            },
            style = PrintStyle
        )
        Slider(
            value = page.toFloat(),
            onValueChange = {
                dragging = it
                actions.onInteract()
            },
            onValueChangeFinished = {
                dragging?.let { actions.onScrub(it.roundToInt()) }
                dragging = null
            },
            valueRange = 0f..last.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = PRINT_GREY,
                activeTrackColor = PRINT_GREY,
                inactiveTrackColor = PRINT_GREY.copy(alpha = TRACK_ALPHA)
            ),
            modifier = Modifier.testTag(ReaderTestTags.SCRUBBER)
        )
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

private val PRINT_GREY = Color(0xFF8E8E93)

private val PrintStyle = TextStyle(
    color = PRINT_GREY,
    fontSize = 13.sp,
    letterSpacing = 0.3.sp,
    shadow = Shadow(color = Color.Black.copy(alpha = 0.22f), offset = Offset(0f, 1.5f), blurRadius = 6f)
)

internal const val CONTROLS_VISIBLE_MS = 10_000L
private const val CENTRE_ZONE = 0.3f
private const val TRACK_ALPHA = 0.3f
private const val SPREAD_SCRUBBER_WIDTH = 0.5f
private const val SINGLE_SCRUBBER_WIDTH = 0.8f
private val PAGE_MARGIN = 32.dp
private val FOOTER_MARGIN = 14.dp
private val HEADER_MARGIN = 14.dp
private val SCRUBBER_BOTTOM = 40.dp
private val TITLE_MAX_WIDTH = 420.dp
