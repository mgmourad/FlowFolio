package com.flowfolio.core.designsystem

import androidx.compose.foundation.border
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButtonColors
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** An alert dialog in the design language: background colour, rounded, thin edge (light mode), lit title. */
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
    val shape = MaterialTheme.shapes.extraLarge
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier.border(EDGE_WIDTH, p.edge, shape),
        dismissButton = dismissButton,
        title = title,
        text = text,
        shape = shape,
        containerColor = p.background,
        titleContentColor = p.title,
        textContentColor = p.item
    )
}

/** Radio buttons in the design language: the title colour when chosen, the hint colour otherwise. */
@Composable
fun flowFolioRadioColors(): RadioButtonColors {
    val p = FlowFolio.palette
    return RadioButtonDefaults.colors(selectedColor = p.title, unselectedColor = p.hint)
}

/** Width of the thin outline around rounded surfaces (the design's light-mode edge). */
val EDGE_WIDTH = 1.dp
