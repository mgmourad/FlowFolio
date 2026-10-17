package com.flowfolio.feature.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flowfolio.core.model.OutlineEntry

/** The book's table of contents; the current chapter is highlighted and scrolled into view. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ContentsSheet(
    outline: List<OutlineEntry>,
    labels: List<String?>,
    currentPage: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Text(
            stringResource(R.string.reader_contents),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )
        if (outline.isEmpty()) {
            Text(
                stringResource(R.string.reader_contents_empty),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(24.dp)
            )
            return@ModalBottomSheet
        }
        val current = PageLookup.chapterAt(outline, currentPage)
        val list = rememberLazyListState(initialFirstVisibleItemIndex = (current - CONTEXT_ROWS).coerceAtLeast(0))
        LazyColumn(state = list, modifier = Modifier.testTag(ReaderTestTags.CONTENTS_LIST)) {
            itemsIndexed(outline) { index, entry ->
                ContentsRow(entry, labels, index == current, onPick)
            }
        }
    }
}

@Composable
private fun ContentsRow(entry: OutlineEntry, labels: List<String?>, current: Boolean, onPick: (Int) -> Unit) {
    val target = entry.pageIndex
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = target >= 0) { onPick(target) }
            .padding(
                start = 24.dp + INDENT * entry.depth.coerceAtMost(MAX_INDENT_LEVELS),
                end = 24.dp,
                top = 12.dp,
                bottom = 12.dp
            )
    ) {
        Text(
            entry.title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (current || entry.depth == 0) FontWeight.SemiBold else FontWeight.Normal,
            color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (target >= 0) {
            Text(
                PageLookup.labelOf(target, labels),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp)
            )
        }
    }
}

/** Go to a page by its printed number (e.g. "214", "xiv") or its position in the file. */
@Composable
internal fun GoToPageDialog(labels: List<String?>, pageCount: Int, onGo: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var notFound by rememberSaveable { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val submit = {
        val page = PageLookup.resolve(text, labels, pageCount)
        if (page != null) onGo(page) else notFound = true
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reader_go_to_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        notFound = false
                    },
                    singleLine = true,
                    isError = notFound,
                    label = { Text(stringResource(R.string.reader_go_to_label)) },
                    supportingText = {
                        Text(
                            if (notFound) {
                                stringResource(R.string.reader_go_to_not_found, pageCount)
                            } else {
                                stringResource(R.string.reader_go_to_hint, pageCount)
                            }
                        )
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { submit() }),
                    modifier = Modifier.focusRequester(focus).testTag(ReaderTestTags.GO_TO_FIELD)
                )
            }
        },
        confirmButton = { TextButton(onClick = submit) { Text(stringResource(R.string.reader_go_to_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.reader_cancel)) } }
    )
}

private val INDENT = 20.dp
private const val MAX_INDENT_LEVELS = 4
private const val CONTEXT_ROWS = 3

/** PDFs can carry any address, so web links are confirmed before leaving the book. */
@Composable
internal fun WebLinkDialog(uri: String, onOpen: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reader_link_title)) },
        text = { Text(uri) },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onOpen() // a missing browser just means nothing opens
            }) { Text(stringResource(R.string.reader_link_open)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.reader_cancel)) } }
    )
}
