package com.flowfolio.feature.reader.riffle

import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.UiFamily
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive

/**
 * The riffle scrubber's dock (spec 4, the mock's layout): a header with the section and the pages shown, the 2 dp
 * auto-hide countdown line ([remaining] from 1 to 0), and the fore-edge strip. [boards] are the book's cover colours.
 */
@Composable
fun RiffleDock(
    state: RiffleScrubberState,
    boards: BoardColors,
    modifier: Modifier = Modifier,
    events: RiffleEvents? = null,
    thumbnails: PageThumbnailProvider? = null,
    remaining: () -> Float = { 1f }
) {
    val palette = FlowFolio.palette
    Column(modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 10.dp)) {
        Box {
            RiffleHeader(state, events)
            events?.let { GoToPageField(state, it) }
        }
        // The countdown is read while drawing, so a ticking clock redraws this line and nothing else.
        val left = palette.item.copy(alpha = 0.45f)
        Box(
            Modifier
                .padding(horizontal = 6.dp)
                .padding(bottom = 4.dp)
                .fillMaxWidth()
                .height(2.dp)
                .background(palette.hint.copy(alpha = 0.18f), RoundedCornerShape(1.dp))
                .drawBehind {
                    val w = size.width * remaining().coerceIn(0f, 1f)
                    drawRoundRect(left, size = Size(w, size.height), cornerRadius = CornerRadius(size.height / 2))
                }
        )
        Box {
            val touch = events?.let { Modifier.riffleGestures(state, it).riffleAccess(state, it) } ?: Modifier
            RiffleStrip(state, boards, Modifier.fillMaxWidth().height(STRIP_HEIGHT.dp).then(touch))
            events?.let {
                FlagStackPopover(state, it)
                NudgeArrows(state, it)
            }
            // Above the strip, taking no room in the layout; it fades in only while riffling or hovering a flag.
            Loupe(
                state,
                thumbnails,
                stripLeft = 0f,
                modifier = Modifier.layout { measurable, _ ->
                    val placeable = measurable.measure(Constraints())
                    layout(0, 0) { placeable.place(0, 0) }
                }
            )
        }
    }
}

@Composable
private fun RiffleHeader(state: RiffleScrubberState, events: RiffleEvents?) {
    val palette = FlowFolio.palette
    val unit = state.previewUnit
    val book = state.book
    Row(
        Modifier.fillMaxWidth().height(26.dp).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val style = TextStyle(fontFamily = UiFamily, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
        Text(
            RiffleLabels.section(book, unit),
            Modifier.weight(1f),
            color = palette.subItem,
            style = style,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        // The page numbers open the go-to-page field (spec 3.2); the touch area is at least 48 dp tall.
        val goTo = stringResource(R.string.riffle_open_go_to)
        val open = if (events == null) {
            Modifier
        } else {
            Modifier
                .clickable {
                    events.onInteraction()
                    state.goToOpen = true
                }
                .semantics { contentDescription = goTo }
                .defaultMinSize(minHeight = 48.dp)
        }
        Box(open, contentAlignment = Alignment.Center) {
            Text(RiffleLabels.pages(book, unit), color = if (state.live) palette.title else palette.hint, style = style)
        }
        Text(
            " " + stringResource(
                R.string.riffle_of,
                book.pageCount,
                (state.mapping.fractionOf(unit).let { if (book.rightToLeft) 1 - it else it } * PERCENT).roundToInt()
            ),
            color = palette.hint,
            style = style.copy(fontWeight = FontWeight.SemiBold)
        )
    }
}

/**
 * The fore-edge strip itself. Draws in one canvas; its frame loop runs only while something moves (ADR-0022), and a
 * frame invalidates only the drawing.
 */
@Composable
fun RiffleStrip(state: RiffleScrubberState, boards: BoardColors, modifier: Modifier = Modifier) {
    val dark = FlowFolio.palette.dark
    val density = LocalDensity.current.density
    val measurer = rememberTextMeasurer()
    val labels = stringResource(R.string.riffle_front) to stringResource(R.string.riffle_back)
    val cloth = remember { StripParts.cloth() }
    val timing = remember { DrawTiming() }
    LaunchedEffect(state) {
        while (isActive) {
            val seen = state.wakeups
            var running = true
            while (running) running = withFrameNanos(state::advance)
            snapshotFlow { state.wakeups }.first { it != seen }
        }
    }
    Spacer(
        modifier
            .onSizeChanged { state.layout(it.width.toFloat(), it.height.toFloat(), density) }
            .drawWithCache {
                val g = state.geometry
                state.flags // new flags make new stacks: build the parts again
                val painter = g?.let { StripPainter(state, it, dark) }
                val parts = g?.let { StripParts(state, it, dark, StripAssets(boards, measurer, labels, cloth)) }
                onDrawBehind {
                    state.frame // redraw every frame the loop advances
                    if (painter != null && parts != null) {
                        val start = SystemClock.elapsedRealtimeNanos()
                        with(painter) { paintBlock() }
                        with(parts) {
                            paintBoards()
                            paintFlags()
                            paintRibbon()
                        }
                        if (state.measureDraw) timing.add(SystemClock.elapsedRealtimeNanos() - start)
                    }
                }
            }
    )
}

/** Average and worst strip draw times, logged every [REPORT_FRAMES] frames (checks the 4 ms target, spec 7). */
private class DrawTiming {
    private var frames = 0
    private var total = 0L
    private var worst = 0L

    fun add(nanos: Long) {
        frames++
        total += nanos
        worst = maxOf(worst, nanos)
        if (frames == REPORT_FRAMES) {
            Log.d(
                TAG,
                "strip draw: mean %.2f ms, worst %.2f ms over %d frames".format(
                    total / frames / NANOS_PER_MS,
                    worst / NANOS_PER_MS,
                    frames
                )
            )
            frames = 0
            total = 0
            worst = 0
        }
    }

    private companion object {
        const val REPORT_FRAMES = 120
        const val NANOS_PER_MS = 1e6
        const val TAG = "FlowFolioRiffle"
    }
}

private const val STRIP_HEIGHT = 124
private const val PERCENT = 100
