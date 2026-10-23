package com.flowfolio.core.designsystem

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Select mode for a list or grid (owner, 2026-10-22): which items are chosen, by key. While [active], items jiggle
 * and a tap chooses them instead of opening them; the screen's header shows a [SelectionBar].
 */
@Stable
class Selection(active: Boolean = false, chosen: Set<String> = emptySet()) {
    var active by mutableStateOf(active)
        private set
    var chosen by mutableStateOf(chosen)
        private set

    fun start() {
        active = true
    }

    fun done() {
        active = false
        chosen = emptySet()
    }

    fun toggle(key: String) {
        chosen = if (key in chosen) chosen - key else chosen + key
    }

    fun setAll(keys: Collection<String>) {
        chosen = keys.toSet()
    }

    fun isChosen(key: String) = key in chosen

    /** Drops chosen keys that are gone (deleted elsewhere), so the count stays true. */
    fun keepOnly(keys: Collection<String>) {
        if (chosen.any { it !in keys }) chosen = chosen.intersect(keys.toSet())
    }

    companion object {
        val Saver: Saver<Selection, Any> = listSaver(
            save = { listOf(it.active.toString()) + it.chosen },
            restore = { Selection(it.first().toBoolean(), it.drop(1).toSet()) }
        )
    }
}

@Composable
fun rememberSelection(): Selection = rememberSaveable(saver = Selection.Saver) { Selection() }

/**
 * iOS's jiggle: while [active], the item rocks a little either way, each item ([key]) out of step with its neighbours.
 * Turned as a layer drawn once, so blurred shadows inside are not drawn again every frame (CLAUDE.md, GPU memory).
 */
fun Modifier.jiggle(active: Boolean, key: Any): Modifier = if (!active) {
    this
} else {
    composed {
        val phase = abs(key.hashCode()) % JIGGLE_MS
        val sway = rememberInfiniteTransition(label = "jiggle")
        val t by sway.animateFloat(
            -1f,
            1f,
            infiniteRepeatable(
                tween(JIGGLE_MS, easing = FastOutSlowInEasing),
                RepeatMode.Reverse,
                StartOffset(phase)
            ),
            label = "jiggleT"
        )
        graphicsLayer {
            rotationZ = t * JIGGLE_DEGREES
            translationY = abs(t) * JIGGLE_LIFT.toPx()
            compositingStrategy = CompositingStrategy.Offscreen
        }
    }
}

/** A chosen item's tick, or an empty ring while it is not chosen: shown at an item's corner in select mode. */
@Composable
fun SelectionCheck(chosen: Boolean, modifier: Modifier = Modifier) {
    val p = FlowFolio.palette
    Box(
        modifier
            .size(CHECK)
            .background(if (chosen) p.title else Color.Black.copy(alpha = RING_FILL), CircleShape)
            .border(2.dp, if (chosen) p.title else Color.White, CircleShape)
            .semantics { selected = chosen },
        contentAlignment = Alignment.Center
    ) {
        if (chosen) FlowFolioGlyph(Glyph.Check, null, color = p.buttonInk, size = 16.dp, glow = false)
    }
}

/**
 * Select mode's bar, a full-width row under the header's title (owner, 2026-10-23: sharing the title's row squeezed
 * the title on the cover screen): how many are chosen, Select all (Unselect all once all are chosen), delete them
 * (asks first, the caller's dialog), and Done.
 */
@Composable
fun SelectionBar(
    selection: Selection,
    labels: SelectionLabels,
    allChosen: Boolean,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = FlowFolio.palette
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            labels.count,
            style = FlowFolioType.rowLabel.merge(FlowFolio.item),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            if (allChosen) labels.unselectAll else labels.selectAll,
            style = FlowFolioType.rowLabel.merge(FlowFolio.subItem),
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .clickable(role = Role.Button) { if (allChosen) selection.setAll(emptyList()) else onSelectAll() }
                .padding(horizontal = 8.dp, vertical = 12.dp)
        )
        FlowFolioIconButton(onClick = { if (selection.chosen.isNotEmpty()) onDelete() }) {
            FlowFolioGlyph(
                Glyph.Trash,
                labels.delete,
                color = if (selection.chosen.isEmpty()) p.hint else p.subItem
            )
        }
        Text(
            labels.done,
            style = FlowFolioType.button.merge(FlowFolio.title),
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .clickable(role = Role.Button, onClick = selection::done)
                .padding(start = 8.dp, top = 12.dp, bottom = 12.dp)
        )
    }
}

/** The words on [SelectionBar], from the caller's strings. */
@Immutable
data class SelectionLabels(
    val count: String,
    val selectAll: String,
    val unselectAll: String,
    val delete: String,
    val done: String
)

/** The header button that starts select mode. */
@Composable
fun SelectButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FlowFolioIconButton(onClick = onClick, modifier = modifier) {
        FlowFolioGlyph(Glyph.Select, label)
    }
}

private const val JIGGLE_MS = 150
private const val JIGGLE_DEGREES = 1.6f
private val JIGGLE_LIFT = 0.6.dp
private val CHECK = 26.dp
private const val RING_FILL = 0.25f
