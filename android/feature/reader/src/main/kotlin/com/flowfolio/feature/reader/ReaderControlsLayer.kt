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
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay

/**
 * The reader's bars and the panels they open.
 *
 * The riffle bar and the page number are both composed in here, so one place hands them the detour: the mark can be
 * made on the bar and cleared from beside the page number, and neither has to know about the other.
 */
@Suppress("LongParameterList") // the bars need most of what the reader has
@Composable
internal fun BoxScope.ReaderControls(
    s: ReaderState.Ready,
    navigation: BookNavigation,
    viewModel: ReaderViewModel,
    riffling: Riffling,
    overlay: Overlay,
    mark: ChromeMark,
    marks: List<Bookmark>,
    haze: HazeState,
    detour: DetourState,
    onBack: () -> Unit,
    jump: (Int) -> Unit
) {
    val bar: @Composable (@Composable () -> Unit) -> Unit = { footer ->
        RiffleBar(s, riffling, marks, viewModel, overlay.chrome, footer)
    }
    CompositionLocalProvider(
        LocalGlassState provides haze,
        LocalReaderSounds provides riffling.sounds,
        LocalDetour provides detour,
        LocalDetourJump provides riffling.land
    ) {
        Controls(s, navigation, overlay, mark, ChromeParts(onBack, jump, bar, viewModel))
    }
}
