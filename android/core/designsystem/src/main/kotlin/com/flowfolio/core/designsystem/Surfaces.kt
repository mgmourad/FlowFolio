package com.flowfolio.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButtonColors
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * An alert dialog in the design language (ADR-0052): the surface, 26 dp corners, ink title, body in the second ink.
 * New dialogs use [FlowFolioDialog], which adds the icon tile, fields and swatches.
 */
@Composable
fun FlowFolioAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null
) {
    val p = FlowFolio.palette
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier,
        dismissButton = dismissButton,
        title = title,
        text = text,
        shape = RoundedCornerShape(26.dp),
        containerColor = p.surface,
        tonalElevation = 0.dp,
        titleContentColor = p.ink,
        textContentColor = p.ink2
    )
}

/** Radio buttons in the design language: the accent when chosen, the third ink otherwise. */
@Composable
fun flowFolioRadioColors(): RadioButtonColors {
    val p = FlowFolio.palette
    return RadioButtonDefaults.colors(selectedColor = p.accent, unselectedColor = p.ink3)
}

/** Width of the thin outline around rounded surfaces (the design's light-mode edge). */
val EDGE_WIDTH = 1.dp
