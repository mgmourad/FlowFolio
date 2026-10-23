package com.flowfolio.feature.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowfolio.core.designsystem.EdgeFade
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioGlyph
import com.flowfolio.core.designsystem.FlowFolioIconButton
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.Glyph
import com.flowfolio.core.designsystem.Motion
import com.flowfolio.core.designsystem.ScreenEntrance
import com.flowfolio.core.designsystem.glass
import com.flowfolio.core.designsystem.reveal
import com.flowfolio.core.model.PageTurn
import com.flowfolio.feature.reader.layout.ReaderLayout
import com.flowfolio.feature.reader.layout.Spreads
import kotlin.math.abs

/**
 * The mock's reader chrome (`.r-top`, `.r-bottom`): glass bars over the full-bleed pages. A tap in the centre of the
 * screen slides them in until the chrome clock hides them: Back, the title, Contents, Go to page, Aa and the ribbon
 * bookmark on top; [bottomBar], the riffle scrubber (ADR-0022), at the bottom. While they are hidden, the page number
 * stays at the foot of the page in print style.
 */
@Composable
internal fun BoxScope.ReaderChrome(
    state: ReaderState.Ready,
    navigation: BookNavigation,
    controlsVisible: Boolean,
    mark: ChromeMark,
    actions: ChromeActions,
    bottomBar: @Composable () -> Unit
) {
    val slide = tween<IntOffset>(Motion.SLOW_MS, easing = Motion.EaseOut)
    // A soft, blurred fade in the paper's colour behind the top bar while it shows, so its buttons never sit on the
    // book's text (owner, 2026-10-23). It fades rather than slides, and stays solid behind the buttons.
    AnimatedVisibility(
        controlsVisible,
        Modifier.align(Alignment.TopCenter),
        enter = fadeIn(tween(Motion.MEDIUM_MS, easing = Motion.EaseOut)),
        exit = fadeOut(tween(Motion.MEDIUM_MS, easing = Motion.EaseOut))
    ) {
        val wide = LocalWindowInfo.current.containerDpSize.width >= TITLE_BESIDE_BACK
        val status = with(LocalDensity.current) { WindowInsets.statusBars.getTop(this).toDp() }
        val solid = status + (if (wide) BIG_BUTTON else FOLDED_BUTTON) + BAR_VERTICAL_PADDING * 2
        val total = solid + TOP_FADE_TAIL
        EdgeFade(Modifier, total, state.tone.paper, fromTop = true, hold = solid / total)
    }
    AnimatedVisibility(
        controlsVisible,
        Modifier.align(Alignment.TopCenter),
        enter = slideInVertically(slide) { -it * 2 } + fadeIn(tween(Motion.MEDIUM_MS, easing = Motion.EaseOut)),
        exit = slideOutVertically(slide) { -it * 2 } + fadeOut(tween(Motion.MEDIUM_MS, easing = Motion.EaseOut))
    ) {
        // Each time the bars show, their buttons fall into place one after another (ADR-0034).
        ScreenEntrance(holdForLandings = false) { TopBar(state.title, mark, actions, reflowable = state.reflowable) }
    }
    AnimatedVisibility(
        controlsVisible,
        Modifier.align(Alignment.BottomCenter),
        enter = slideInVertically(slide) { it * 2 } + fadeIn(tween(Motion.MEDIUM_MS, easing = Motion.EaseOut)),
        exit = slideOutVertically(slide) { it * 2 } + fadeOut(tween(Motion.MEDIUM_MS, easing = Motion.EaseOut))
    ) {
        ScreenEntrance(holdForLandings = false) { Box(Modifier.reveal(DOCK_ORDER)) { bottomBar() } }
    }
    // In Scroll the number would float over the text; the bars show the page there.
    if (!controlsVisible && state.turn != PageTurn.SCROLL) PageNumber(state, navigation.labels)
}

/** `.r-top`: Back, the title, and the reading tools, each on glass. */
@Composable
private fun TopBar(title: String, mark: ChromeMark, actions: ChromeActions, reflowable: Boolean) {
    // Unfolded, the title sits beside Back at the top left (owner, 2026-10-18); folded, it is centred.
    val wide = LocalWindowInfo.current.containerDpSize.width >= TITLE_BESIDE_BACK
    // A third larger than the app's usual 42 dp for easy reach (owner, 2026-10-22); folded, a little less.
    val button = if (wide) BIG_BUTTON else FOLDED_BUTTON
    val glyph = button * GLYPH_SHARE
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = BAR_INSET, vertical = BAR_VERTICAL_PADDING),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        FlowFolioIconButton(onClick = actions.onBack, Modifier.reveal(0), glass = true, size = button) {
            FlowFolioGlyph(
                Glyph.Back,
                stringResource(R.string.reader_back),
                color = FlowFolio.palette.text,
                size = glyph
            )
        }
        TitlePill(title, wide, Modifier.weight(1f).reveal(1))
        val contents = Modifier.testTag(ReaderTestTags.CONTENTS).reveal(2)
        FlowFolioIconButton({ actions.onPanel(Panel.CONTENTS) }, contents, glass = true, size = button) {
            FlowFolioGlyph(Glyph.Contents, stringResource(R.string.reader_contents), size = glyph)
        }
        val goTo = Modifier.testTag(ReaderTestTags.GO_TO).reveal(3)
        FlowFolioIconButton({ actions.onPanel(Panel.GO_TO) }, goTo, glass = true, size = button) {
            FlowFolioGlyph(Glyph.GoTo, stringResource(R.string.reader_go_to), size = glyph)
        }
        // Highlights and ribbons are PDF-only until EPUB annotations (Phase 2 milestone 4).
        if (!reflowable) {
            val notes = Modifier.testTag(ReaderTestTags.ANNOTATIONS).reveal(4)
            FlowFolioIconButton({ actions.onPanel(Panel.ANNOTATIONS) }, notes, glass = true, size = button) {
                FlowFolioGlyph(Glyph.Highlight, stringResource(R.string.reader_annotations), size = glyph)
            }
        }
        val settings = Modifier.testTag(ReaderTestTags.SETTINGS).reveal(5)
        FlowFolioIconButton({ actions.onPanel(Panel.SETTINGS) }, settings, glass = true, size = button) {
            Text(
                stringResource(R.string.reader_settings_button),
                style = FlowFolioType.button.copy(
                    fontSize = (SETTINGS_TEXT_SP * button.value / BASE_BUTTON.value).sp,
                    fontFamily = FlowFolioType.heroTitle.fontFamily
                )
                    .merge(FlowFolio.title)
            )
        }
        if (!reflowable) Box(Modifier.reveal(6)) { BookmarkButton(mark, actions, button) }
    }
}

/** The book's title on glass, beside Back (unfolded) or centred (folded). */
@Composable
private fun TitlePill(title: String, wide: Boolean, modifier: Modifier) {
    Box(modifier, contentAlignment = if (wide) Alignment.CenterStart else Alignment.Center) {
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
}

/** The print-style page number at the foot of the page while the bars are hidden. */
@Composable
private fun BoxScope.PageNumber(state: ReaderState.Ready, labels: List<String?>) {
    val spread = state.layout == ReaderLayout.SPREAD
    val pages = if (spread) Spreads.pagesOf(state.unit, state.pageCount, state.coverAlone) else listOf(state.anchorPage)
    // Two screen pages of one printed page (a reflowed book's page list) show its number once.
    val shown = pages.map { PageLookup.labelOf(it, labels) }.distinct().joinToString("–")
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

private const val CENTRE_ZONE = 0.4f
private val BAR_INSET = 14.dp
private val BAR_VERTICAL_PADDING = 8.dp
private val TOP_FADE_TAIL = 36.dp
private val PAGE_MARGIN = 32.dp
private val FOOTER_MARGIN = 14.dp
private val TITLE_MAX_WIDTH = 520.dp
private val TITLE_BESIDE_BACK = 600.dp

/** The reader's round buttons: 56 dp unfolded, 50 dp folded (the app's usual is 42 dp; owner, 2026-10-22). */
private val BASE_BUTTON = 42.dp
private val BIG_BUTTON = 56.dp
private val FOLDED_BUTTON = 50.dp
private const val SETTINGS_TEXT_SP = 16f

/** A glyph's share of its round button (24 dp in 42 dp). */
internal const val GLYPH_SHARE = 24f / 42f

/** The riffle dock falls in just after the top bar's first buttons. */
private const val DOCK_ORDER = 2
