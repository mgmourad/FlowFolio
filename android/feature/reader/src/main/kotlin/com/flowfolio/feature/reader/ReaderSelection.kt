package com.flowfolio.feature.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.flowfolio.core.designsystem.marker
import com.flowfolio.core.model.Annotation
import com.flowfolio.core.model.HighlightColor
import com.flowfolio.core.model.PageRect
import com.flowfolio.core.pdf.PageText
import kotlin.math.roundToInt

/**
 * Text being selected on the open book (owner, 2026-10-18: highlights and notes). A long press picks the word under
 * the finger; dragging before lifting extends the selection, and the handles adjust it afterwards. [editing] is an
 * existing highlight that was tapped.
 */
@Stable
internal class TextPick {
    var page by mutableStateOf(-1)
        private set
    var text by mutableStateOf<PageText?>(null)
        private set
    var range by mutableStateOf<IntRange?>(null)
    var editing by mutableStateOf<Annotation?>(null)

    /** Where the selection was started, so dragging back over it keeps the first word. */
    private var anchor: IntRange = IntRange.EMPTY

    /** Something is selected or tapped; changes only when that starts or ends, not as the selection grows. */
    val active: Boolean by derivedStateOf { range != null || editing != null }

    /** A selection is being made (as opposed to a tapped highlight). */
    val selecting: Boolean by derivedStateOf { range != null }

    fun start(page: Int, text: PageText, at: Int) {
        this.page = page
        this.text = text
        anchor = TextSelection.wordAt(text, at)
        range = anchor
        editing = null
    }

    /** Extends from the first word to the character [at] (dragging). */
    fun extendTo(at: Int) {
        if (anchor.isEmpty()) return
        range = if (at < anchor.first) at..anchor.last else anchor.first..maxOf(at, anchor.last)
    }

    /** Moves one end (a handle) to [at]; the ends swap if dragged past each other. */
    fun moveEnd(start: Boolean, at: Int) {
        val r = range ?: return
        range = if (start) TextSelection.ordered(at, r.last) else TextSelection.ordered(r.first, at)
        anchor = range ?: anchor
    }

    fun clear() {
        range = null
        editing = null
        page = -1
        text = null
        anchor = IntRange.EMPTY
    }
}

/** A page's text, loaded once and kept for the few pages around the reader. */
internal class PageTexts(private val load: suspend (Int) -> PageText?) {
    private val cache = object : LinkedHashMap<Int, PageText>(CACHED, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, PageText>?) = size > CACHED
    }

    suspend fun get(page: Int): PageText? = cache[page] ?: load(page)?.also { cache[page] = it }

    private companion object {
        const val CACHED = 6
        const val LOAD_FACTOR = 0.75f
    }
}

/** The page under [point] and where on it, as fractions of the page. */
internal fun frameAt(point: Offset, frames: List<PageFrames.Frame>): Pair<PageFrames.Frame, Offset>? {
    val f = frames.firstOrNull { point.x >= it.x && point.x < it.x + it.w && point.y >= it.y && point.y < it.y + it.h }
        ?: return null
    return f to Offset((point.x - f.x) / f.w, (point.y - f.y) / f.h)
}

/** A page rectangle placed on screen in [frame]. */
internal fun PageRect.on(frame: PageFrames.Frame): Rect =
    Rect(frame.x + left * frame.w, frame.y + top * frame.h, frame.x + right * frame.w, frame.y + bottom * frame.h)

/**
 * Press and hold on the book to select (without lifting, dragging extends the selection). Until the press has been
 * held, nothing is consumed, so swipes and taps still turn pages; once it has, the gesture belongs to the selection.
 */
internal fun Modifier.selectText(
    enabled: Boolean,
    onStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onEnd: () -> Unit
): Modifier = composed {
    val start by rememberUpdatedState(onStart)
    val drag by rememberUpdatedState(onDrag)
    val end by rememberUpdatedState(onEnd)
    val on by rememberUpdatedState(enabled)
    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (!on) return@awaitEachGesture
            // Null when the press outlasted the long-press time without moving or lifting: a selection.
            val gaveUp = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                var waiting = true
                while (waiting) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id }
                    val moved = change == null ||
                        (change.position - down.position).getDistance() > viewConfiguration.touchSlop
                    waiting = !moved && change.pressed && event.changes.size == 1
                }
                true
            }
            val held = gaveUp == null
            if (!held) return@awaitEachGesture
            start(down.position)
            var pressed = true
            while (pressed) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id }
                event.changes.forEach { it.consume() }
                pressed = change?.pressed == true
                if (change != null && pressed) drag(change.position)
            }
            end()
        }
    }
}

/**
 * The selection on screen: the selected lines tinted, a handle at each end, and a catcher behind them so a tap
 * elsewhere ends the selection instead of turning the page.
 */
@Composable
internal fun BoxScope.SelectionMarks(
    pick: TextPick,
    frame: () -> PageFrames.Frame?,
    tint: Color,
    onLens: (Offset) -> Unit = {},
    onDismiss: () -> Unit
) {
    Box(Modifier.matchParentSize().pointerInput(Unit) { detectTapGestures { onDismiss() } })
    // The selection is read only while drawing and placing (owner, 2026-10-18: smooth): a moving finger redraws the
    // marks and moves the handles, without recomposing anything.
    val rects = remember(pick) {
        derivedStateOf {
            val text = pick.text
            val range = pick.range
            val f = frame()
            if (text == null || range == null ||
                f == null
            ) {
                emptyList()
            } else {
                TextSelection.rects(text, range).map { it.on(f) }
            }
        }
    }
    Canvas(Modifier.matchParentSize()) {
        rects.value.forEach { drawRect(tint.copy(alpha = SELECTION_ALPHA), it.topLeft, it.size) }
    }
    val end = { start: Boolean ->
        { p: Offset ->
            onLens(p)
            val f = frame()
            val text = pick.text
            if (f != null && text != null) charNear(p, f, text)?.let { pick.moveEnd(start, it) }
        }
    }
    Handle({
        rects.value.firstOrNull()?.let { Offset(it.left, it.bottom) }
    }, tint, { onLens(Offset.Unspecified) }, end(true))
    Handle({
        rects.value.lastOrNull()?.let { Offset(it.right, it.bottom) }
    }, tint, { onLens(Offset.Unspecified) }, end(false))
}

/**
 * A slim handle hanging under the selection's end (a stem and a knob); dragging it moves that end. [at] is read while
 * placing, so the handle follows the selection without recomposing.
 */
@Composable
private fun Handle(at: () -> Offset?, tint: Color, onEnd: () -> Unit, onMove: (Offset) -> Unit) {
    val half = with(LocalDensity.current) { HANDLE_WIDTH.toPx() } / 2f
    val reach = with(LocalDensity.current) { HANDLE_REACH.toPx() }
    val move by rememberUpdatedState(onMove)
    val done by rememberUpdatedState(onEnd)
    val place by rememberUpdatedState(at)
    Canvas(
        Modifier
            .offset {
                val p = place() ?: Offset(OFF_SCREEN, OFF_SCREEN)
                IntOffset((p.x - half).roundToInt(), p.y.roundToInt())
            }
            .size(HANDLE_WIDTH, HANDLE_HEIGHT)
            .pointerInput(Unit) {
                detectDragGestures(onDragEnd = { done() }, onDragCancel = { done() }) { change, _ ->
                    change.consume()
                    val p = place() ?: return@detectDragGestures
                    // Aim a little above the finger, at the line the handle hangs from.
                    move(Offset(p.x - half, p.y) + change.position - Offset(0f, reach))
                }
            }
    ) {
        val r = size.width / 2f
        drawLine(tint, Offset(r, 0f), Offset(r, size.height - r), strokeWidth = STEM.toPx())
        drawCircle(tint, radius = r, center = Offset(r, size.height - r))
    }
}

private fun charNear(point: Offset, frame: PageFrames.Frame, text: PageText): Int? =
    TextSelection.charAt(text, (point.x - frame.x) / frame.w, (point.y - frame.y) / frame.h)

/**
 * Draws [highlights] over a page image of this size: marker colours multiplied into light paper (the ink stays
 * crisp), a translucent wash on dark paper; a small dot at the end of a highlight that has a note.
 */
internal fun DrawScope.drawHighlights(highlights: List<Annotation>, darkPaper: Boolean, page: Size = size) {
    val alpha = highlightAlpha(darkPaper)
    val blend = if (darkPaper) BlendMode.SrcOver else BlendMode.Multiply
    highlights.forEach { a ->
        val colour = a.color.marker.copy(alpha = alpha)
        a.rects.forEach { r ->
            drawRect(
                colour,
                topLeft = Offset(r.left * page.width, r.top * page.height),
                size = Size((r.right - r.left) * page.width, (r.bottom - r.top) * page.height),
                blendMode = blend
            )
        }
        val last = a.rects.lastOrNull()
        if (a.note != null && last != null) {
            val radius = (last.bottom - last.top) * page.height * NOTE_DOT
            drawCircle(
                a.color.marker,
                radius,
                Offset(
                    last.right * page.width + radius * 2,
                    last.top * page.height + radius
                )
            )
        }
    }
}

/** How strongly highlights tint the page: multiplied on light paper, a translucent wash on dark paper. */
internal fun highlightAlpha(darkPaper: Boolean) = if (darkPaper) DARK_ALPHA else LIGHT_ALPHA

private const val SELECTION_ALPHA = 0.32f
private const val OFF_SCREEN = -10_000f
private const val NOTE_DOT = 0.22f
private const val DARK_ALPHA = 0.38f
private const val LIGHT_ALPHA = 0.6f
private val HANDLE_WIDTH = 14.dp
private val HANDLE_HEIGHT = 24.dp
private val STEM = 2.dp
private val HANDLE_REACH = 16.dp
