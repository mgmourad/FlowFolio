package com.flowfolio.feature.reader

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.flowfolio.core.designsystem.DynamicFlowPrompt
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioGlyph
import com.flowfolio.core.designsystem.FlowFolioToggle
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.Glyph
import com.flowfolio.core.designsystem.INDETERMINATE
import com.flowfolio.core.designsystem.Motion
import com.flowfolio.core.designsystem.softShadow
import com.flowfolio.core.model.FlowCarry
import com.flowfolio.core.model.FlowPause
import com.flowfolio.core.model.FlowStatus

/**
 * The "Aa" sheet's Dynamic Flow card (ADR-0045): the glyph, what the book's Dynamic Flow is doing, and the switch,
 * ringed with the conversion's progress while it runs. Switching asks first (unless the reader said not to) whether to
 * bring this view's highlights, notes and bookmarks into the other one; agreeing says that takes a while and runs in
 * the background.
 */
@Composable
internal fun DynamicFlowCard(flow: FlowState, carry: FlowCarry, actions: FlowActions) {
    var switching by remember { mutableStateOf<Boolean?>(null) }
    FlowCard(flow) { switching = it }
    DynamicFlowPrompt(switching, carry, actions.hasNotes, actions.onSwitch, actions.onRemember) { switching = null }
}

/**
 * The card itself: the glyph, the title and what the book's Dynamic Flow is doing, and the ringed switch. It stands
 * out from the sheet with a glowing outline in the theme's colours, which breathes while Dynamic Flow is on. The glow
 * is a cached soft shadow whose alpha alone changes (no blur is animated, the GPU-memory rule).
 */
@Composable
private fun FlowCard(flow: FlowState, onSwitch: (Boolean) -> Unit) {
    val p = FlowFolio.palette
    val shape = RoundedCornerShape(CARD_RADIUS)
    val breath = rememberInfiniteTransition(label = "flowGlow")
    val pulse by breath.animateFloat(
        GLOW_LOW,
        GLOW_HIGH,
        infiniteRepeatable(tween(GLOW_MS, easing = Motion.EaseInOut), RepeatMode.Reverse),
        label = "flowGlowAlpha"
    )
    val glow = if (flow.wanted) pulse else GLOW_OFF
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .softShadow(shape, p.title.copy(alpha = glow), GLOW_BLUR)
            .clip(shape)
            .background(p.surface2.copy(alpha = CARD_ALPHA))
            .border(OUTLINE, Brush.linearGradient(listOf(p.title, p.glyph, p.title)), shape)
            .toggleable(flow.wanted, role = Role.Switch, onValueChange = onSwitch)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag(ReaderTestTags.FLOW_SWITCH),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(GLYPH_TILE).clip(RoundedCornerShape(12.dp)).background(p.title.copy(alpha = TILE_ALPHA)),
            contentAlignment = Alignment.Center
        ) {
            FlowFolioGlyph(Glyph.DynamicFlow, contentDescription = null, size = 24.dp)
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(stringResource(R.string.flow_title), style = FlowFolioType.rowLabel.merge(FlowFolio.title))
            AnimatedContent(
                statusLine(flow),
                transitionSpec = { fadeIn(tween(Motion.MEDIUM_MS)) togetherWith fadeOut(tween(Motion.MEDIUM_MS)) },
                label = "flowStatus"
            ) { line ->
                Text(line, style = FlowFolioType.rowDetail.merge(FlowFolio.hint), maxLines = 2)
            }
        }
        FlowFolioToggle(flow.wanted, onCheckedChange = null, progress = ringOf(flow))
    }
}

/**
 * What the switch does: [onSwitch] to Dynamic Flow (true) or the PDF, bringing the changes or not; [onRemember]; and
 * [hasNotes], whether a view (true Dynamic Flow) has anything to bring.
 */
internal class FlowActions(
    val onSwitch: (Boolean, Boolean) -> Unit,
    val onRemember: (FlowCarry) -> Unit,
    val hasNotes: suspend (Boolean) -> Boolean
)

/** The card's second line. */
@Composable
private fun statusLine(flow: FlowState): String = when (val s = flow.status) {
    is FlowStatus.Converting -> when (s.paused) {
        FlowPause.HOT -> stringResource(R.string.flow_status_hot)
        FlowPause.BATTERY -> stringResource(R.string.flow_status_battery)
        null -> stringResource(R.string.flow_status_converting, (s.fraction * PERCENT).toInt())
    }

    FlowStatus.Queued -> stringResource(R.string.flow_status_queued)

    is FlowStatus.Failed -> stringResource(R.string.flow_status_failed)

    FlowStatus.Ready -> stringResource(if (flow.reading) R.string.flow_status_on else R.string.flow_status_ready)

    FlowStatus.None -> stringResource(R.string.flow_status_off)
}

/** The switch's ring: how far the conversion is, turning while it waits; none otherwise. */
private fun ringOf(flow: FlowState): Float? = when (val s = flow.status) {
    is FlowStatus.Converting -> if (s.fraction <= 0f) INDETERMINATE else s.fraction
    FlowStatus.Queued -> INDETERMINATE
    else -> null
}.takeIf { flow.wanted }

private val CARD_RADIUS = 20.dp
private val OUTLINE = 1.5.dp
private val GLOW_BLUR = 16.dp
private const val GLOW_LOW = 0.35f
private const val GLOW_HIGH = 0.75f
private const val GLOW_OFF = 0.25f
private const val GLOW_MS = 1800
private val GLYPH_TILE = 42.dp
private const val CARD_ALPHA = 0.55f
private const val TILE_ALPHA = 0.14f
private const val PERCENT = 100
