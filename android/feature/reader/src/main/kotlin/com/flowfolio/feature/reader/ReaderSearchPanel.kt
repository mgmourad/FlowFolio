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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioType

/** In-book search (Phase 6 M1): a field and the matches, each jumping to its page and highlighting it. */
@Composable
internal fun ReaderSearchPanel(hits: List<SearchHit>, onQuery: (String) -> Unit, onJump: (SearchHit) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(query) { onQuery(query) }
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
        when {
            query.isBlank() -> Unit

            hits.isEmpty() -> Text(
                stringResource(R.string.reader_search_empty),
                style = FlowFolioType.sub.merge(FlowFolio.hint),
                modifier = Modifier.padding(top = 16.dp, start = 4.dp)
            )

            else -> LazyColumn(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                itemsIndexed(hits, key = { _, hit -> hit.page to hit.start }) { _, hit ->
                    SearchResultRow(hit) { onJump(hit) }
                }
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
            hit.snippet.run { highlightMatch(this, hit.text) },
            style = MaterialTheme.typography.bodyMedium.merge(FlowFolio.item),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** The matched phrase in a snippet, bold with a soft marker behind it. */
private fun highlightMatch(snippet: String, match: String): AnnotatedString {
    val index = snippet.indexOf(match, ignoreCase = true)
    if (index < 0) return AnnotatedString(snippet)
    return buildAnnotatedString {
        append(snippet.substring(0, index))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold, background = MATCH_MARK)) {
            append(snippet.substring(index, index + match.length))
        }
        append(snippet.substring(index + match.length))
    }
}

private val MATCH_MARK = Color(0x4DFFD54F)
