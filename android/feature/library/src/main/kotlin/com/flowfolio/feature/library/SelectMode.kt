package com.flowfolio.feature.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioAlertDialog
import com.flowfolio.core.designsystem.Motion
import com.flowfolio.core.designsystem.SelectButton
import com.flowfolio.core.designsystem.Selection
import com.flowfolio.core.designsystem.SelectionBar
import com.flowfolio.core.designsystem.SelectionCheck
import com.flowfolio.core.designsystem.SelectionLabels

/**
 * A header's buttons with select mode (owner, 2026-10-22): [normal] (the header's other buttons) and Select. While
 * selecting they step aside; the [SelectionRow] under the title takes over.
 */
@Composable
internal fun SelectControls(selection: Selection, allKeys: List<String>, normal: @Composable () -> Unit = {}) {
    AnimatedVisibility(
        !selection.active,
        enter = fadeIn(tween(Motion.FAST_MS, delayMillis = SWAP_DELAY_MS)),
        exit = fadeOut(tween(SWAP_DELAY_MS))
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(LocalHeaderGap.current),
            verticalAlignment = Alignment.CenterVertically
        ) {
            normal()
            if (allKeys.isNotEmpty()) {
                SelectButton(
                    stringResource(R.string.action_select),
                    selection::start,
                    Modifier.testTag(LibraryTestTags.SELECT)
                )
            }
        }
    }
}

/**
 * Select mode's bar on a row of its own under the header's title (owner, 2026-10-23: beside the title it squeezed
 * the title letter by letter on the cover screen). It opens below the title with a short rise (ADR-0033).
 */
@Composable
internal fun SelectionRow(
    selection: Selection,
    allKeys: List<String>,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    /** Select mode's "Add to shelf" for the chosen books; null where it does not apply. */
    onAddToShelf: (() -> Unit)? = null
) {
    // Items removed elsewhere leave the choice.
    LaunchedEffect(allKeys) { selection.keepOnly(allKeys) }
    AnimatedVisibility(
        selection.active,
        modifier,
        enter = expandVertically(tween(Motion.FAST_MS)) + fadeIn(tween(Motion.FAST_MS, delayMillis = SWAP_DELAY_MS)) +
            slideInVertically { it / SWAP_RISE },
        exit = shrinkVertically(tween(Motion.FAST_MS)) + fadeOut(tween(SWAP_DELAY_MS))
    ) {
        val count = selection.chosen.size
        SelectionBar(
            selection,
            SelectionLabels(
                count = pluralStringResource(R.plurals.selected_count, count, count),
                selectAll = stringResource(R.string.action_select_all),
                unselectAll = stringResource(R.string.action_unselect_all),
                delete = stringResource(R.string.action_delete),
                done = stringResource(R.string.action_done)
            ),
            allChosen = allKeys.isNotEmpty() && allKeys.all(selection::isChosen),
            onSelectAll = { selection.setAll(allKeys) },
            onDelete = onDelete,
            modifier = Modifier.padding(top = 8.dp).testTag(LibraryTestTags.SELECTION_BAR),
            actions = {
                onAddToShelf?.let { add ->
                    com.flowfolio.core.designsystem.FlowFolioIconButton(
                        onClick = { if (selection.chosen.isNotEmpty()) add() },
                        modifier = Modifier.testTag(LibraryTestTags.SELECTION_ADD_TO_SHELF),
                        tooltip = stringResource(R.string.action_add_to_shelf_short)
                    ) {
                        com.flowfolio.core.designsystem.FlowFolioGlyph(
                            com.flowfolio.core.designsystem.Glyph.Shelves,
                            stringResource(R.string.action_add_to_shelf_short),
                            color = if (selection.chosen.isEmpty()) FlowFolio.palette.ink2 else FlowFolio.palette.ink
                        )
                    }
                }
            }
        )
    }
}

/** An item's tick in select mode, at its top end. */
@Composable
internal fun BoxScope.SelectionMark(selection: Selection, key: String) {
    AnimatedVisibility(
        selection.active,
        Modifier.align(Alignment.TopEnd).padding(4.dp),
        enter = scaleIn(tween(Motion.FAST_MS, easing = Motion.Spring)) + fadeIn(tween(Motion.FAST_MS)),
        exit = scaleOut(tween(Motion.FAST_MS)) + fadeOut(tween(Motion.FAST_MS))
    ) { SelectionCheck(selection.isChosen(key)) }
}

private const val SWAP_DELAY_MS = 90
private const val SWAP_RISE = 3

/** "Remove N …?": asks before a bulk delete; [body] says what stays or goes. */
@Composable
internal fun BulkDeleteDialog(title: String, body: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    FlowFolioAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag(LibraryTestTags.CONFIRM_BULK_DELETE)) {
                Text(stringResource(R.string.action_remove), style = FlowFolio.subItem)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel), style = FlowFolio.hint) }
        }
    )
}
