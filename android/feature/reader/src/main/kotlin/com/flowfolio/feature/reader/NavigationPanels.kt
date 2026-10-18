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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioAlertDialog
import com.flowfolio.core.model.OutlineEntry

/** The book's table of contents; the current chapter is highlighted and scrolled into view. */
@Composable
internal fun ContentsList(outline: List<OutlineEntry>, labels: List<String?>, currentPage: Int, onPick: (Int) -> Unit) {
    Text(
        stringResource(R.string.reader_contents),
        style = MaterialTheme.typography.titleLarge.merge(FlowFolio.title),
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
    )
    if (outline.isEmpty()) {
        Text(
            stringResource(R.string.reader_contents_empty),
            style = MaterialTheme.typography.bodyMedium.merge(FlowFolio.hint),
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp)
        )
        return
    }
    val current = PageLookup.chapterAt(outline, currentPage)
    val list = rememberLazyListState(initialFirstVisibleItemIndex = (current - CONTEXT_ROWS).coerceAtLeast(0))
    LazyColumn(state = list, modifier = Modifier.testTag(ReaderTestTags.CONTENTS_LIST)) {
        itemsIndexed(outline) { index, entry ->
            ContentsRow(entry, labels, index == current, onPick)
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
                start = 20.dp + INDENT * entry.depth.coerceAtMost(MAX_INDENT_LEVELS),
                end = 20.dp,
                top = 12.dp,
                bottom = 12.dp
            )
    ) {
        // Chapters are items, nested sections sub-items; the chapter you are in is lit in the title colour.
        val role = when {
            current -> FlowFolio.title
            entry.depth == 0 -> FlowFolio.item
            else -> FlowFolio.subItem
        }
        Text(
            entry.title,
            style = MaterialTheme.typography.bodyLarge.merge(role),
            fontWeight = if (current || entry.depth == 0) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (target >= 0) {
            Text(
                PageLookup.labelOf(target, labels),
                style = MaterialTheme.typography.bodyMedium.merge(FlowFolio.hint),
                modifier = Modifier.padding(start = 16.dp)
            )
        }
    }
}

/** Go to a page by its printed number (e.g. "214", "xiv") or its position in the file. */
@Composable
internal fun GoToForm(labels: List<String?>, pageCount: Int, onGo: (Int) -> Unit, onCancel: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var notFound by rememberSaveable { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val submit = {
        val page = PageLookup.resolve(text, labels, pageCount)
        if (page != null) onGo(page) else notFound = true
    }
    Column(Modifier.padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 8.dp)) {
        Text(
            stringResource(R.string.reader_go_to_title),
            style = MaterialTheme.typography.titleLarge.merge(FlowFolio.title)
        )
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
            modifier = Modifier
                .padding(top = 12.dp, end = 8.dp)
                .fillMaxWidth()
                .focusRequester(focus)
                .testTag(ReaderTestTags.GO_TO_FIELD)
        )
        Row(Modifier.align(Alignment.End)) {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.reader_cancel)) }
            TextButton(onClick = submit) { Text(stringResource(R.string.reader_go_to_confirm)) }
        }
    }
}

private val INDENT = 20.dp
private const val MAX_INDENT_LEVELS = 4
private const val CONTEXT_ROWS = 3

/** PDFs can carry any address, so web links are confirmed before leaving the book. */
@Composable
internal fun WebLinkDialog(uri: String, onOpen: () -> Unit, onDismiss: () -> Unit) {
    FlowFolioAlertDialog(
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
