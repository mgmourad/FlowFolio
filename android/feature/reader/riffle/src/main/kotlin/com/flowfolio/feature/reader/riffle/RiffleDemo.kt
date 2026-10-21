package com.flowfolio.feature.reader.riffle

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.flowfolio.core.designsystem.FlowFolio
import kotlinx.coroutines.delay

/**
 * The riffle scrubber on the mock's sample book (spec 9), for checking it on a device before it joins the reader:
 * debug builds open it with `--ez riffleDemo true`. A fingertip replays the mock's riffle; gestures come in R3.
 */
@Composable
fun RiffleDemo(dark: Boolean, onDark: (Boolean) -> Unit) {
    var single by rememberSaveable { mutableStateOf(false) }
    var replay by remember { mutableIntStateOf(0) }
    val book = remember(single) { SampleBook(singlePages = single) }
    // Pages 216-217, where the mock opens.
    val state = remember(book) { RiffleScrubberState(book, book.unitOf(PAGE_216)).apply { measureDraw = true } }
    LaunchedEffect(book) { book.flags.flags().collect { state.flags = it } }
    LaunchedEffect(state, replay) {
        delay(DEMO_DELAY_MS)
        playDemo(state)
    }
    val palette = FlowFolio.palette
    Box(Modifier.fillMaxSize().background(palette.background)) {
        Column(
            Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DemoButton(stringResource(if (dark) R.string.riffle_demo_light else R.string.riffle_demo_dark)) {
                    onDark(!dark)
                }
                DemoButton(stringResource(if (single) R.string.riffle_demo_spreads else R.string.riffle_demo_single)) {
                    single = !single
                }
                DemoButton(stringResource(R.string.riffle_demo_replay)) { replay++ }
            }
        }
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(RiffleLabels.pages(book, state.currentUnit), style = FlowFolio.title)
            Text(RiffleLabels.section(book, state.currentUnit), color = palette.hint)
        }
        val view = LocalView.current
        val scope = rememberCoroutineScope()
        val events = remember(state) {
            object : RiffleEvents {
                override fun onRelease(unit: Int) {
                    state.currentUnit = unit
                }

                override fun onFlag(flag: RiffleFlag) = state.travelTo(book.unitOf(flag.page))

                override fun onFeedback(feedback: RiffleFeedback) {
                    feedback.haptic?.let { RiffleHaptics.play(view, it, scope) }
                }

                override fun onSpeed(speed: ScrubSpeed) = RiffleHaptics.play(view, RiffleHaptic.SPEED, scope)
            }
        }
        RiffleDock(state, SAMPLE_BOARDS, Modifier.align(Alignment.BottomCenter).navigationBarsPadding(), events)
    }
}

@Composable
private fun DemoButton(label: String, onClick: () -> Unit) {
    val palette = FlowFolio.palette
    Text(
        label,
        Modifier
            .background(palette.surface2, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        color = palette.item
    )
}

/** The mock's demo: a fingertip slides the ribbon across the book and back, easing between key points. */
private suspend fun playDemo(state: RiffleScrubberState) {
    val g = state.geometry ?: return
    val home = state.mapping.xOf(state.currentUnit, g.x0, g.x1)
    val far = g.x0 + FAR_FRACTION * (g.x1 - g.x0)
    val near = g.x0 + NEAR_FRACTION * (g.x1 - g.x0)
    val points = mapOf(HOME to home, FAR to far, NEAR to near)
    val keys = DEMO_KEYS.map { (ms, point) -> ms to points.getValue(point) }
    state.ghost = true
    val start = withFrameNanos { it }
    var done = false
    while (!done) {
        withFrameNanos { now ->
            val t = (now - start) / NANOS_PER_MS
            val i = keys.indexOfFirst { it.first >= t }
            if (i <= 0) {
                done = i < 0
                state.riffleX = if (done) null else home
            } else {
                val (t0, x0) = keys[i - 1]
                val (t1, x1) = keys[i]
                state.riffleX = x0 + (x1 - x0) * ease((t - t0).toFloat() / (t1 - t0))
            }
        }
    }
    state.ghost = false
}

/** The mock's cubic ease in and out. */
@Suppress("MagicNumber") // the curve's own coefficients
private fun ease(t: Float): Float = if (t < HALF) 4 * t * t * t else 1 - ((-2 * t + 2).let { it * it * it }) / 2

private const val PAGE_216 = 215
private const val DEMO_DELAY_MS = 1_200L
private const val FAR_FRACTION = 0.72f
private const val NEAR_FRACTION = 0.38f
private const val HOME = 0
private const val FAR = 1
private const val NEAR = 2

/** The mock's demo: when (ms) the fingertip reaches each point. */
private val DEMO_KEYS = listOf(
    0L to HOME,
    500L to HOME,
    2100L to FAR,
    2700L to FAR,
    4000L to NEAR,
    4600L to NEAR,
    5500L to HOME,
    6000L to HOME
)
private const val HALF = 0.5f
private const val NANOS_PER_MS = 1_000_000L

/** The sample book's cloth: the mock's teal boards. */
private val SAMPLE_BOARDS = BoardColors(main = Color(0xFF0F766E), dark = Color(0xFF064E3B))
