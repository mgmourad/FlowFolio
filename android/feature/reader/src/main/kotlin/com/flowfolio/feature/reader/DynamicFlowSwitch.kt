package com.flowfolio.feature.reader

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
    DynamicFlowPrompt(switching, carry, actions.onSwitch, actions.onRemember) { switching = null }
}

/** The card itself: the glyph, the title and what the book's Dynamic Flow is doing, and the ringed switch. */
@Composable
private fun FlowCard(flow: FlowState, onSwitch: (Boolean) -> Unit) {
    val p = FlowFolio.palette
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .clip(RoundedCornerShape(CARD_RADIUS))
            .background(p.surface2.copy(alpha = CARD_ALPHA))
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

/** What the switch does: [onSwitch] to Dynamic Flow (true) or the PDF, bringing the changes or not; [onRemember]. */
internal class FlowActions(val onSwitch: (Boolean, Boolean) -> Unit, val onRemember: (FlowCarry) -> Unit)

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
private val GLYPH_TILE = 42.dp
private const val CARD_ALPHA = 0.55f
private const val TILE_ALPHA = 0.14f
private const val PERCENT = 100
