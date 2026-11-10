package com.flowfolio.feature.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioChip
import com.flowfolio.core.designsystem.FlowFolioGlyph
import com.flowfolio.core.designsystem.FlowFolioSheet
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.Glyph

/**
 * The target-language chips (ADR-0052): the reader's own languages, the current one filled, then (+), which opens a
 * sheet of every language ML Kit offers. A language's model downloads the first time it is used (the panel shows
 * that as it happens); the chips are kept in settings.
 */
@Composable
internal fun TargetChips(state: TranslateUiState, viewModel: TranslateViewModel) {
    val chips by viewModel.chips.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    val shown = if (state.to in chips) chips else chips + state.to
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 12.dp)
            .testTag(ReaderTestTags.TRANSLATE_CHIPS),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        shown.forEach { code ->
            FlowFolioChip(languageName(code), selected = code == state.to, onClick = { viewModel.setTo(code) })
        }
        FlowFolioChip(
            stringResource(R.string.reader_translate_add),
            selected = false,
            onClick = { adding = true },
            glyph = Glyph.Add
        )
    }
    if (adding) {
        LanguageSheet(
            languages = viewModel.languages,
            chosen = shown,
            onAdd = {
                viewModel.addChip(it)
                adding = false
            },
            onRemove = viewModel::removeChip,
            onDismiss = { adding = false }
        )
    }
}

@Composable
private fun LanguageSheet(
    languages: List<String>,
    chosen: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val p = FlowFolio.palette
    FlowFolioSheet(onDismissRequest = onDismiss, title = stringResource(R.string.reader_translate_languages)) {
        Text(
            stringResource(R.string.reader_translate_languages_note),
            style = FlowFolioType.rowDetail.copy(color = p.ink2),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        LazyColumn(Modifier.heightIn(max = SHEET_LIST_HEIGHT)) {
            items(languages.sortedBy { languageName(it) }, key = { it }) { code ->
                val on = code in chosen
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { if (on) onRemove(code) else onAdd(code) }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        languageName(code),
                        style = FlowFolioType.rowLabel.copy(color = p.ink),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (on) FlowFolioGlyph(Glyph.Check, null, color = p.accent, size = 18.dp, glow = false)
                }
            }
        }
    }
}

private val SHEET_LIST_HEIGHT = 460.dp
