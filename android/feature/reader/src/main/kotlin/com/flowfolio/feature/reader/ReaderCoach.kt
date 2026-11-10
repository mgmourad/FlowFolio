package com.flowfolio.feature.reader

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateRectAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.flowfolio.core.designsystem.ButtonStyle
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioButton
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.Motion

/** The guide id the coach marks are remembered under; Settings → Help → reset shows them again. */
internal const val READER_COACH = "reader_coach"

/** The controls the coach marks point at (ADR-0052, the mock's `coachSteps`). */
internal enum class CoachSpot(@StringRes val title: Int, @StringRes val body: Int) {
    RIBBON(R.string.reader_coach_ribbon_title, R.string.reader_coach_ribbon_body),
    STRIP(R.string.reader_coach_strip_title, R.string.reader_coach_strip_body),
    AA(R.string.reader_coach_aa_title, R.string.reader_coach_aa_body),
    LISTEN(R.string.reader_coach_listen_title, R.string.reader_coach_listen_body),
    PAGE(R.string.reader_coach_page_title, R.string.reader_coach_page_body)
}

/** Where each [CoachSpot] is on screen, in root coordinates, as the chrome lays it out. */
@Stable
internal class CoachTargets {
    val bounds = mutableStateMapOf<CoachSpot, Rect>()
}

internal val LocalCoachTargets = staticCompositionLocalOf<CoachTargets?> { null }

/** Marks this element as the coach mark's [spot]; does nothing outside a coach. */
@Composable
internal fun Modifier.coachSpot(spot: CoachSpot): Modifier {
    val targets = LocalCoachTargets.current ?: return this
    return onGloballyPositioned { targets.bounds[spot] = it.boundsInRoot() }
}

/**
 * The coach marks (ADR-0052): five steps, shown once after the first-book demo. The page dims except for a lit,
 * outlined cut-out round the control being explained; the card itself stays centred, so it can never be clipped
 * or land off a control on any screen (the owner's rule for first-use guides, 2026-11-07). Skip ends it; so does
 * Done on the last step.
 */
@Composable
internal fun BoxScope.ReaderCoach(targets: CoachTargets, onDone: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    val spot = CoachSpot.entries[step]
    val p = FlowFolio.palette
    val pad = with(androidx.compose.ui.platform.LocalDensity.current) { SPOT_PAD.toPx() }
    val target = targets.bounds[spot]?.inflate(pad) ?: Rect.Zero
    val hole by animateRectAsState(target, Motion.springSpec(), label = "coachHole")
    val accent = p.accent
    Canvas(
        Modifier
            .matchParentSize()
            .testTag(ReaderTestTags.COACH)
            // Swallows taps on the page underneath while the coach is up.
            .clickable(remember { MutableInteractionSource() }, indication = null) { }
    ) {
        val cut = Path().apply {
            if (hole != Rect.Zero) addRoundRect(androidx.compose.ui.geometry.RoundRect(hole, CornerRadius(HOLE_R)))
        }
        clipPath(cut, ClipOp.Difference) { drawRect(Color.Black.copy(alpha = SCRIM)) }
        if (hole != Rect.Zero) {
            drawRoundRect(
                accent,
                topLeft = Offset(hole.left, hole.top),
                size = hole.size,
                cornerRadius = CornerRadius(HOLE_R),
                style = Stroke(2.dp.toPx())
            )
        }
    }
    Column(
        Modifier
            .align(Alignment.Center)
            .padding(24.dp)
            .widthIn(max = CARD_WIDTH)
            .background(p.surface, RoundedCornerShape(22.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            stringResource(R.string.reader_coach_step, step + 1, CoachSpot.entries.size),
            style = FlowFolioType.eyebrow.copy(color = p.accent)
        )
        Text(stringResource(spot.title), style = FlowFolioType.dialogTitle.copy(color = p.ink))
        Text(stringResource(spot.body), style = FlowFolioType.rowDetail.copy(color = p.ink2))
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Dots(step)
            Spacer(Modifier.weight(1f))
            val last = step == CoachSpot.entries.lastIndex
            if (!last) FlowFolioButton(stringResource(R.string.reader_coach_skip), onDone, style = ButtonStyle.GHOST)
            Spacer(Modifier.size(8.dp))
            FlowFolioButton(
                stringResource(if (last) R.string.reader_coach_done else R.string.reader_coach_next),
                { if (last) onDone() else step++ }
            )
        }
    }
}

@Composable
private fun Dots(step: Int) {
    val p = FlowFolio.palette
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        CoachSpot.entries.indices.forEach { i ->
            Box(
                Modifier.size(
                    if (i ==
                        step
                    ) {
                        8.dp
                    } else {
                        6.dp
                    }
                ).background(if (i == step) p.accent else p.line, CircleShape)
            )
        }
    }
}

private val SPOT_PAD = 6.dp
private const val HOLE_R = 40f
private const val SCRIM = 0.62f
private val CARD_WIDTH = 420.dp
