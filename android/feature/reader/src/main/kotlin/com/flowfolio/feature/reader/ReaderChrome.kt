package com.flowfolio.feature.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioGlyph
import com.flowfolio.core.designsystem.FlowFolioIconButton
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.Glyph
import com.flowfolio.core.designsystem.Motion
import com.flowfolio.core.designsystem.glass
import com.flowfolio.core.model.PageTurn
import com.flowfolio.feature.reader.layout.ReaderLayout
import com.flowfolio.feature.reader.layout.Spreads
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The mock's reader chrome (`.r-top`, `.r-bottom`): glass bars over the full-bleed pages. A tap in the centre of the
 * screen slides them in for [CONTROLS_VISIBLE_MS]: Back, the title, Contents, Go to page, Aa and the ribbon bookmark
 * on top; the page scrubber with the page and how far along at the bottom. While they are hidden, the page number
 * stays at the foot of the page in print style.
 */
@Composable
internal fun BoxScope.ReaderChrome(
    state: ReaderState.Ready,
    navigation: BookNavigation,
    controlsVisible: Boolean,
    mark: ChromeMark,
    actions: ChromeActions
) {
    val slide = tween<IntOffset>(Motion.SLOW_MS, easing = Motion.EaseOut)
    AnimatedVisibility(
        controlsVisible,
        Modifier.align(Alignment.TopCenter),
        enter = slideInVertically(slide) { -it * 2 },
        exit = slideOutVertically(slide) { -it * 2 }
    ) {
        TopBar(state.title, mark, actions)
    }
    AnimatedVisibility(
        controlsVisible,
        Modifier.align(Alignment.BottomCenter),
        enter = slideInVertically(slide) { it * 2 },
        exit = slideOutVertically(slide) { it * 2 }
    ) {
        BottomBar(state, navigation, actions)
    }
    // In Scroll the number would float over the text; the bars show the page there.
    if (!controlsVisible && state.turn != PageTurn.SCROLL) PageNumber(state, navigation.labels)
}

/** `.r-top`: Back, the title, and the reading tools, each on glass. */
@Composable
private fun TopBar(title: String, mark: ChromeMark, actions: ChromeActions) {
    // Unfolded, the title sits beside Back at the top left (owner, 2026-10-18); folded, it is centred.
    val wide = LocalWindowInfo.current.containerDpSize.width >= TITLE_BESIDE_BACK
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = BAR_INSET, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        FlowFolioIconButton(onClick = actions.onBack, glass = true) {
            FlowFolioGlyph(Glyph.Back, stringResource(R.string.reader_back), color = FlowFolio.palette.text)
        }
        Box(Modifier.weight(1f), contentAlignment = if (wide) Alignment.CenterStart else Alignment.Center) {
            Text(
                title,
                style = FlowFolioType.rowLabel.copy(fontSize = 13.sp).merge(FlowFolio.item),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .widthIn(max = TITLE_MAX_WIDTH)
                    .glass(RoundedCornerShape(50))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .testTag(ReaderTestTags.TITLE)
            )
        }
        val contents = Modifier.testTag(ReaderTestTags.CONTENTS)
        FlowFolioIconButton(onClick = { actions.onPanel(Panel.CONTENTS) }, glass = true, modifier = contents) {
            FlowFolioGlyph(Glyph.Contents, stringResource(R.string.reader_contents))
        }
        val goTo = Modifier.testTag(ReaderTestTags.GO_TO)
        FlowFolioIconButton(onClick = { actions.onPanel(Panel.GO_TO) }, glass = true, modifier = goTo) {
            FlowFolioGlyph(Glyph.GoTo, stringResource(R.string.reader_go_to))
        }
        val notes = Modifier.testTag(ReaderTestTags.ANNOTATIONS)
        FlowFolioIconButton(onClick = { actions.onPanel(Panel.ANNOTATIONS) }, glass = true, modifier = notes) {
            FlowFolioGlyph(Glyph.Highlight, stringResource(R.string.reader_annotations))
        }
        val settings = Modifier.testTag(ReaderTestTags.SETTINGS)
        FlowFolioIconButton(onClick = { actions.onPanel(Panel.SETTINGS) }, glass = true, modifier = settings) {
            Text(
                stringResource(R.string.reader_settings_button),
                style = FlowFolioType.button.copy(fontSize = 16.sp, fontFamily = FlowFolioType.heroTitle.fontFamily)
                    .merge(FlowFolio.title)
            )
        }
        BookmarkButton(mark, actions)
    }
}

/** `.r-bottom`: the scrubber with the page shown and how far through the book. */
@Composable
private fun BottomBar(state: ReaderState.Ready, navigation: BookNavigation, actions: ChromeActions) {
    val last = (state.pageCount - 1).coerceAtLeast(1)
    var dragging by remember { mutableStateOf<Float?>(null) }
    val page = (dragging ?: state.anchorPage.toFloat()).roundToInt().coerceIn(0, last)
    val p = FlowFolio.palette
    Row(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = BAR_INSET, end = BAR_INSET, bottom = BOTTOM_GAP)
            .glass(RoundedCornerShape(22.dp))
            .padding(horizontal = 16.dp)
            .height(BOTTOM_BAR_HEIGHT),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            stringResource(R.string.reader_page_short, PageLookup.labelOf(page, navigation.labels)),
            style = FlowFolioType.button.copy(fontSize = 12.sp).merge(FlowFolio.title)
        )
        // Right-to-left books scrub from right to left.
        val direction = if (LocalRightToLeft.current) LayoutDirection.Rtl else LayoutDirection.Ltr
        CompositionLocalProvider(LocalLayoutDirection provides direction) {
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
                    thumbColor = p.title,
                    activeTrackColor = p.title,
                    inactiveTrackColor = p.hint.copy(alpha = TRACK_ALPHA)
                ),
                modifier = Modifier.weight(1f).testTag(ReaderTestTags.SCRUBBER)
            )
        }
        Text(
            stringResource(R.string.reader_percent, (page * PERCENT) / last),
            style = FlowFolioType.button.copy(fontSize = 12.sp).merge(FlowFolio.hint)
        )
    }
}

/** The print-style page number at the foot of the page while the bars are hidden. */
@Composable
private fun BoxScope.PageNumber(state: ReaderState.Ready, labels: List<String?>) {
    val spread = state.layout == ReaderLayout.SPREAD
    val pages = if (spread) Spreads.pagesOf(state.unit, state.pageCount, state.coverAlone) else listOf(state.anchorPage)
    val shown = pages.joinToString("–") { PageLookup.labelOf(it, labels) }
    // The book's own numbers stand alone ("48–49"); counting from the cover, "62–63 of 959".
    val printed = pages.any { labels.getOrNull(it) != null }
    Text(
        if (printed) shown else stringResource(R.string.reader_page_of, shown, state.pageCount),
        style = PrintStyle.copy(color = LocalTone.current.folioInk),
        modifier = Modifier
            .align(if (spread) Alignment.BottomStart else Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(horizontal = PAGE_MARGIN, vertical = FOOTER_MARGIN)
            .testTag(ReaderTestTags.INDICATOR)
    )
}

/**
 * Offers each tap (a press released without moving, before long-press time) to [onTap] with its position and the
 * area's size. When [onTap] returns true the release is consumed in the initial pass, before the page handlers see it,
 * so the tap does not also turn a page. Drags are never intercepted.
 */
internal fun Modifier.interceptTaps(onTap: (Offset, IntSize) -> Boolean): Modifier = composed {
    val handler by rememberUpdatedState(onTap)
    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var tracking = true
            while (tracking) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                val moved =
                    change == null || (change.position - down.position).getDistance() > viewConfiguration.touchSlop
                val released = !moved && !change.pressed
                val quick =
                    change != null && change.uptimeMillis - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis
                if (released && quick && handler(change.position, size)) change.consume()
                tracking = !moved && change.pressed
            }
        }
    }
}

/**
 * Touch zones (owner, 2026-10-17): the outer 30% on each side turns pages (tap: left back, right forward; swipes
 * either way), the middle [CENTRE_ZONE] of the width, full height, is for taps that show or hide the controls.
 * Swipes that start in the middle still turn pages.
 */
internal fun isCentre(tap: Offset, size: IntSize): Boolean = abs(tap.x - size.width / 2f) < size.width * CENTRE_ZONE / 2

/** The page number in the page's own ink, softened like print, so it reads on every paper. */
private val PageTone.folioInk: Color get() = (ink ?: PRINT_INK).copy(alpha = FOLIO_ALPHA)

private val PRINT_INK = Color(0xFF3A3A3C)
private const val FOLIO_ALPHA = 0.72f

private val PrintStyle = TextStyle(
    fontSize = 13.sp,
    letterSpacing = 0.3.sp,
    shadow = Shadow(color = Color.Black.copy(alpha = 0.22f), offset = Offset(0f, 1.5f), blurRadius = 6f)
)

internal const val CONTROLS_VISIBLE_MS = 10_000L
private const val CENTRE_ZONE = 0.4f
private const val TRACK_ALPHA = 0.3f
private const val PERCENT = 100
private val BAR_INSET = 14.dp
private val BOTTOM_GAP = 26.dp
private val BOTTOM_BAR_HEIGHT = 56.dp
private val PAGE_MARGIN = 32.dp
private val FOOTER_MARGIN = 14.dp
private val TITLE_MAX_WIDTH = 520.dp
private val TITLE_BESIDE_BACK = 600.dp
