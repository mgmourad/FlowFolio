package com.flowfolio.feature.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flowfolio.core.designsystem.AtmosphereOverlay
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioGlyph
import com.flowfolio.core.designsystem.FlowFolioPalette
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.GlassStyle
import com.flowfolio.core.designsystem.Glyph
import com.flowfolio.core.designsystem.LocalGlassState
import com.flowfolio.core.designsystem.LocalGlassStyle
import com.flowfolio.core.designsystem.Motion
import com.flowfolio.core.designsystem.glass
import com.flowfolio.core.model.Annotation
import com.flowfolio.core.model.Bookmark
import com.flowfolio.core.model.PageTurn
import com.flowfolio.core.model.atmosphereInForce
import com.flowfolio.feature.reader.layout.ReaderLayout
import com.flowfolio.feature.reader.layout.Spreads
import com.flowfolio.feature.reader.riffle.ChromePause
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay

/**
 * The page left behind on a detour (owner, M5), held at the reader's level rather than inside the riffle scrubber.
 *
 * The scrubber is where a detour is marked, but the mark has to outlive it: once the bar is dismissed the way back
 * shows beside the page number instead, and both do the same thing.
 *
 * It is deliberately not saved. A detour is something you are in the middle of; one left over from last week would
 * be a puzzle rather than a help.
 */
@Stable
class DetourState {
    /** The unit marked, or null when nothing is. */
    var unit: Int? by mutableStateOf(null)
        private set

    /** Bumped each time a detour is marked, so the notice shows again for a second mark. */
    var announced by mutableIntStateOf(0)
        private set

    /** Marks [at] and asks for the notice to be shown. */
    fun mark(at: Int) {
        unit = at
        announced++
    }

    /** Forgets the mark, once the reader has taken it or cleared it. */
    fun clear() {
        unit = null
    }
}

val LocalDetour = staticCompositionLocalOf { DetourState() }

/**
 * What the reader says when a detour is marked (owner, M5 round): a pill at the top for a few seconds, because the
 * gesture that marks a detour -- holding the book on the riffle bar -- gives no other sign that it worked.
 *
 * It is driven by [DetourState.announced] rather than by the mark itself, so marking a second detour over the first
 * says so again.
 */
@Composable
internal fun BoxScope.DetourNotice(detour: DetourState) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(detour.announced) {
        if (detour.announced == 0) return@LaunchedEffect
        shown = true
        delay(DETOUR_NOTICE_MS)
        shown = false
    }
    AnimatedVisibility(
        visible = shown,
        enter = fadeIn(tween(Motion.MEDIUM_MS, easing = Motion.EaseOut)) +
            slideInVertically(tween(Motion.MEDIUM_MS, easing = Motion.EaseOut)) { -it / 2 },
        exit = fadeOut(tween(Motion.FAST_MS, easing = Motion.EaseOut)),
        modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = NOTICE_TOP)
    ) {
        Row(
            Modifier
                .glass(RoundedCornerShape(50))
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .testTag(ReaderTestTags.DETOUR_NOTICE),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FlowFolioGlyph(
                Glyph.Detour,
                contentDescription = null,
                color = FlowFolio.palette.ink,
                size = 16.dp,
                glow = false
            )
            Text(stringResource(R.string.reader_detour_marked), style = FlowFolioType.rowDetail.merge(FlowFolio.title))
        }
    }
}

/** Clear of the reader's top bar, which sits under the status bar when the controls are showing. */
private val NOTICE_TOP = 78.dp

private const val DETOUR_NOTICE_MS = 2600L
