package com.flowfolio.core.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * A stepper (`.stepper`): − and + round buttons around the value in Fraunces. Each button is disabled at its end of
 * the range; [label] names the setting for TalkBack.
 */
@Composable
fun FlowFolioStepper(
    value: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    canDecrease: Boolean = true,
    canIncrease: Boolean = true
) {
    val p = FlowFolio.palette
    Row(
        modifier.semantics(mergeDescendants = false) { contentDescription = "$label, $value" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StepButton("−", "$label −", canDecrease, onDecrease)
        Text(
            value,
            style = FlowFolioType.stat.copy(fontSize = 17.sp, color = p.ink),
            modifier = Modifier.widthIn(min = 24.dp)
        )
        StepButton("+", "$label +", canIncrease, onIncrease)
    }
}

@Composable
private fun StepButton(symbol: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    val p = FlowFolio.palette
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.size(34.dp).border(EDGE_WIDTH, p.line, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                symbol,
                style = FlowFolioType.button.copy(color = p.ink.copy(alpha = if (enabled) 1f else 0.35f))
            )
        }
    }
}

/** How large a [SettingsTile] is: a hub row's, the page header's, or the unfolded list's. */
enum class TileSize(val box: Dp, val radius: Dp, val glyph: Dp, val shadow: Boolean) {
    ROW(44.dp, 14.dp, 22.dp, true),
    BIG(56.dp, 18.dp, 28.dp, true),
    COMPACT(34.dp, 11.dp, 18.dp, false)
}

/**
 * The Settings hub's tile (`.tile`, ADR-0052): a rounded square in the accent, lighter at its top left, with a white
 * glyph and a soft accent shadow.
 */
@Composable
fun SettingsTile(glyph: Glyph, modifier: Modifier = Modifier, size: TileSize = TileSize.ROW) {
    val p = FlowFolio.palette
    val shape = RoundedCornerShape(size.radius)
    val lifted = if (size.shadow) {
        Modifier.softShadow(shape, p.accent.copy(alpha = 0.3f), blur = 14.dp, offsetY = 6.dp)
    } else {
        Modifier
    }
    Box(
        modifier
            .then(lifted)
            .size(size.box)
            .clip(shape)
            .background(Brush.linearGradient(listOf(lerp(p.accent, Color.White, TILE_LIGHT), p.accent))),
        contentAlignment = Alignment.Center
    ) {
        FlowFolioGlyph(glyph, null, color = Color.White, size = size.glyph, glow = false)
    }
}

private const val TILE_LIGHT = 0.2f
