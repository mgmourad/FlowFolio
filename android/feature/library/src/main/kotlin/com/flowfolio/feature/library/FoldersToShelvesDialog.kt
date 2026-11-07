package com.flowfolio.feature.library

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioAlertDialog
import com.flowfolio.core.designsystem.FlowFolioGlyph
import com.flowfolio.core.designsystem.FlowFolioIconButton
import com.flowfolio.core.designsystem.FlowFolioMenu
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.Glyph
import com.flowfolio.core.designsystem.flowFolioRadioColors
import com.flowfolio.core.model.BookStorage
import com.flowfolio.core.model.StorageMode

/**
 * "Make shelves out of these folders?" after a folder import (owner, M5 round 4).
 *
 * It says exactly what it will do, including the parts a reader would otherwise have to find out by doing it: the
 * folders inside folders are included, the colours are picked at random, and both the name and the colour can be
 * changed afterwards. The checkbox is the way to never be asked again.
 */
@Composable
internal fun FoldersToShelvesDialog(folders: Int, books: Int, onYes: (Boolean) -> Unit, onNo: (Boolean) -> Unit) {
    var never by rememberSaveable { mutableStateOf(false) }
    FlowFolioAlertDialog(
        onDismissRequest = { onNo(never) },
        title = { Text(stringResource(R.string.shelves_ask_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.shelves_ask_body, folders, books))
                Text(
                    stringResource(R.string.shelves_ask_detail),
                    style = MaterialTheme.typography.bodySmall.merge(FlowFolio.hint)
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { never = !never }
                        .padding(vertical = 4.dp)
                        .testTag(LibraryTestTags.SHELVES_NEVER),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = never, onCheckedChange = { never = it })
                    Text(
                        stringResource(R.string.shelves_ask_never),
                        style = MaterialTheme.typography.bodyMedium.merge(FlowFolio.item),
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onYes(never) }, modifier = Modifier.testTag(LibraryTestTags.SHELVES_YES)) {
                Text(stringResource(R.string.shelves_ask_yes))
            }
        },
        dismissButton = {
            TextButton(onClick = { onNo(never) }) { Text(stringResource(R.string.shelves_ask_no)) }
        }
    )
}
