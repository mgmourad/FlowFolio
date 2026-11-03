package com.flowfolio.feature.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.flowfolio.core.data.DictionaryRepository
import com.flowfolio.core.data.TranslationRepository
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioGlyph
import com.flowfolio.core.designsystem.FlowFolioIconButton
import com.flowfolio.core.designsystem.FlowFolioPill
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.Glyph
import com.flowfolio.core.model.Translation
import com.flowfolio.core.model.WordDefinition
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One definition: the word, its part of speech, the page it was looked up on, and the text. */
@Composable
internal fun DefinitionRow(d: WordDefinition, navigation: BookNavigation, onGo: (Int) -> Unit, onForget: () -> Unit) {
    val page = d.page
    DictionaryRow(
        onGo = if (page != null && page >= 0) ({ onGo(page) }) else null,
        onForget = onForget,
        forgetLabel = R.string.reader_definition_forget
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                d.word,
                style = FlowFolioType.rowLabel.copy(
                    fontFamily = FlowFolioType.heroTitle.fontFamily,
                    fontStyle = FontStyle.Italic,
                    fontSize = 16.sp
                ).merge(FlowFolio.title),
                modifier = Modifier.weight(1f, fill = false)
            )
            d.partOfSpeech?.takeIf { it.isNotBlank() }?.let { pos ->
                FlowFolioPill(pos, FlowFolio.subItem)
            }
            if (page != null && page >= 0) {
                Text(
                    stringResource(R.string.reader_page_short, PageLookup.labelOf(page, navigation.labels)),
                    style = FlowFolioType.rowDetail.merge(FlowFolio.hint)
                )
            }
        }
        Text(
            d.definition,
            style = FlowFolioType.sub.merge(FlowFolio.item),
            maxLines = 4,
            overflow = TextOverflow.Ellipsis
        )
    }
    HorizontalDivider(color = FlowFolio.palette.edge)
}

@Composable
internal fun TranslationsTabList(
    items: List<Translation>,
    hasQuery: Boolean,
    navigation: BookNavigation,
    onGo: (Int) -> Unit,
    onForget: (Long) -> Unit
) {
    if (items.isEmpty()) {
        Text(
            stringResource(if (hasQuery) R.string.reader_dictionary_no_match else R.string.reader_translations_empty),
            style = MaterialTheme.typography.bodyMedium.merge(FlowFolio.hint),
            modifier = Modifier.padding(vertical = 20.dp, horizontal = 4.dp)
        )
        return
    }
    LazyColumn(Modifier.padding(top = 8.dp).heightIn(max = LIST_MAX_HEIGHT)) {
        items(items, key = { it.id }) { t -> TranslationRow(t, navigation, onGo) { onForget(t.id) } }
    }
}

/** One translation: the passage, the languages, the page it was asked on, and the result. */
@Composable
internal fun TranslationRow(t: Translation, navigation: BookNavigation, onGo: (Int) -> Unit, onForget: () -> Unit) {
    val page = t.page
    DictionaryRow(
        onGo = if (page != null && page >= 0) ({ onGo(page) }) else null,
        onForget = onForget,
        forgetLabel = R.string.reader_translation_forget
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                t.source,
                style = FlowFolioType.rowLabel.copy(
                    fontFamily = FlowFolioType.heroTitle.fontFamily,
                    fontStyle = FontStyle.Italic,
                    fontSize = 15.sp
                ).merge(FlowFolio.title),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            FlowFolioPill("${t.from} → ${t.to}", FlowFolio.subItem)
            if (page != null && page >= 0) {
                Text(
                    stringResource(R.string.reader_page_short, PageLookup.labelOf(page, navigation.labels)),
                    style = FlowFolioType.rowDetail.merge(FlowFolio.hint)
                )
            }
        }
        Text(
            t.translated,
            style = FlowFolioType.sub.merge(FlowFolio.item),
            maxLines = 4,
            overflow = TextOverflow.Ellipsis
        )
    }
    HorizontalDivider(color = FlowFolio.palette.edge)
}

/**
 * The frame both dictionary rows share (owner, M5 round): what the row says, and a bin to forget it by.
 *
 * The bin is a child of the row, so a tap on it never also goes to the page; a row whose page is unknown simply
 * does not navigate.
 */
@Composable
private fun DictionaryRow(
    onGo: (() -> Unit)?,
    onForget: () -> Unit,
    forgetLabel: Int,
    content: @Composable ColumnScope.() -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onGo != null) Modifier.clickable(onClick = onGo) else Modifier)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
        val says = stringResource(forgetLabel)
        Box(
            Modifier
                .size(FORGET)
                .clip(CircleShape)
                .clickable(onClick = onForget)
                .semantics { contentDescription = says }
                .testTag(ReaderTestTags.DICTIONARY_FORGET),
            contentAlignment = Alignment.Center
        ) {
            FlowFolioGlyph(
                Glyph.Trash,
                contentDescription = null,
                color = FlowFolio.palette.subItem,
                size = 18.dp,
                glow = false
            )
        }
    }
}

private val FORGET = 38.dp

@Composable
internal fun DefinitionsTabList(
    items: List<WordDefinition>,
    hasQuery: Boolean,
    navigation: BookNavigation,
    onGo: (Int) -> Unit,
    onForget: (Long) -> Unit
) {
    if (items.isEmpty()) {
        Text(
            stringResource(if (hasQuery) R.string.reader_dictionary_no_match else R.string.reader_definitions_empty),
            style = MaterialTheme.typography.bodyMedium.merge(FlowFolio.hint),
            modifier = Modifier.padding(vertical = 20.dp, horizontal = 4.dp)
        )
        return
    }
    LazyColumn(Modifier.padding(top = 8.dp).heightIn(max = LIST_MAX_HEIGHT)) {
        items(items, key = { it.id }) { d -> DefinitionRow(d, navigation, onGo) { onForget(d.id) } }
    }
}

/** Everything a search found, definitions first, with no regard for the tab (owner, M4b). */
@Composable
internal fun LookupResults(
    definitions: List<WordDefinition>,
    translations: List<Translation>,
    navigation: BookNavigation,
    onGo: (Int) -> Unit,
    onForgetDefinition: (Long) -> Unit,
    onForgetTranslation: (Long) -> Unit
) {
    if (definitions.isEmpty() && translations.isEmpty()) {
        Text(
            stringResource(R.string.reader_dictionary_no_match),
            style = MaterialTheme.typography.bodyMedium.merge(FlowFolio.hint),
            modifier = Modifier.padding(vertical = 20.dp, horizontal = 4.dp)
        )
        return
    }
    LazyColumn(Modifier.padding(top = 8.dp).heightIn(max = LIST_MAX_HEIGHT)) {
        items(definitions, key = { "d" + it.id }) { d ->
            DefinitionRow(d, navigation, onGo) { onForgetDefinition(d.id) }
        }
        items(translations, key = { "t" + it.id }) { t ->
            TranslationRow(t, navigation, onGo) { onForgetTranslation(t.id) }
        }
    }
}
