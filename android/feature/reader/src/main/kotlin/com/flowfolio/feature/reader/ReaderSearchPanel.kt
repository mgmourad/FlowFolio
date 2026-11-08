package com.flowfolio.feature.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioType

/** In-book search (Phase 6 M1): a field and the matches, each jumping to its page and highlighting it. */
@Composable
internal fun ReaderSearchPanel(
    hits: List<SearchHit>,
    onQuery: (String) -> Unit,
    onJump: (SearchHit) -> Unit,
    onClear: () -> Unit = {}
) {
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(query) { onQuery(query) }
    DisposableEffect(Unit) { onDispose(onClear) }
    Column(Modifier.padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            label = { Text(stringResource(R.string.reader_search_hint)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 8.dp)
                .focusRequester(focus)
                .testTag(ReaderTestTags.SEARCH_FIELD)
        )
        if (query.isBlank()) return
        if (hits.isEmpty()) {
            Text(
                stringResource(R.string.reader_search_empty),
                style = FlowFolioType.sub.merge(FlowFolio.hint),
                modifier = Modifier.padding(top = 16.dp, start = 4.dp)
            )
            return
        }
        LazyColumn(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            itemsIndexed(hits, key = { _, hit -> hit.page to hit.start }) { _, hit ->
                SearchResultRow(hit) { onJump(hit) }
            }
        }
    }
}

@Composable
private fun SearchResultRow(hit: SearchHit, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        hit.chapter?.let {
            Text(
                it,
                style = FlowFolioType.rowDetail.merge(FlowFolio.title),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            hit.snippet,
            style = MaterialTheme.typography.bodyMedium.merge(FlowFolio.item),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
