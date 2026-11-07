package com.flowfolio.feature.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioCard
import com.flowfolio.core.designsystem.FlowFolioGlyph
import com.flowfolio.core.designsystem.FlowFolioIconButton
import com.flowfolio.core.designsystem.FlowFolioMenu
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.Glyph
import com.flowfolio.core.designsystem.glass
import com.flowfolio.core.model.Translation
import java.util.Locale

/**
 * FlowFolio's Translate panel (owner, 2026-10-18; ADR-0018): glass, in the reader's palette. The source language
 * (detected, or chosen) and the target language as chips with a swap between them, the passage, its translation, Copy
 * and Share, and the recent translations below. Translation happens on the phone (Google ML Kit).
 */
@Composable
internal fun BoxScope.TranslateOverlay(
    request: TranslateRequest,
    onClose: () -> Unit,
    viewModel: TranslateViewModel = hiltViewModel()
) {
    val text = request.text
    LaunchedEffect(request) { viewModel.start(text, request.page, request.locator) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    Column(
        Modifier
            .align(Alignment.Center)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(18.dp)
            .widthIn(max = PANEL_WIDTH)
            .fillMaxWidth()
            .fillMaxHeight(HEIGHT_SHARE)
            .glass(RoundedCornerShape(28.dp))
            .padding(18.dp)
            .testTag(ReaderTestTags.TRANSLATE)
    ) {
        Header(onClose)
        LanguageRow(state, viewModel)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Passage(state)
            Result(state)
        }
        History(history, viewModel::show, viewModel::clearHistory)
    }
}

@Composable
private fun Header(onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.reader_translate),
                style = FlowFolioType.screenTitle.copy(fontSize = 26.sp).merge(FlowFolio.title)
            )
            Text(stringResource(R.string.reader_translate_note), style = FlowFolioType.rowDetail.merge(FlowFolio.hint))
        }
        FlowFolioIconButton(onClick = onClose, glass = true) {
            FlowFolioGlyph(Glyph.Back, stringResource(R.string.reader_close), color = FlowFolio.palette.text)
        }
    }
}

/** Source chip, swap, target chip. */
@Composable
private fun LanguageRow(state: TranslateUiState, viewModel: TranslateViewModel) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val from = state.from
        LanguageChip(
            label = from?.let { languageName(it) } ?: stringResource(R.string.reader_translate_detecting),
            badge = if (state.detected && from != null) stringResource(R.string.reader_translate_detected) else null,
            languages = viewModel.languages,
            onPick = viewModel::setFrom,
            modifier = Modifier.weight(1f)
        )
        Text(
            "⇄",
            style = FlowFolioType.button.copy(fontSize = 22.sp).merge(FlowFolio.title),
            modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = viewModel::swap).padding(8.dp)
        )
        LanguageChip(languageName(state.to), null, viewModel.languages, viewModel::setTo, Modifier.weight(1f))
    }
}

@Composable
private fun LanguageChip(
    label: String,
    badge: String?,
    languages: List<String>,
    onPick: (String) -> Unit,
    modifier: Modifier
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        FlowFolioCard(
            Modifier.fillMaxWidth().clickable {
                open = true
            },
            radius = 18.dp,
            padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column {
                badge?.let { Text(it.uppercase(), style = FlowFolioType.eyebrow.merge(FlowFolio.subItem)) }
                Text(
                    label,
                    style = FlowFolioType.rowLabel.merge(FlowFolio.item),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        FlowFolioMenu(expanded = open, onDismissRequest = {
            open = false
        }, modifier = Modifier.heightIn(max = MENU_HEIGHT)) {
            languages.sortedBy { languageName(it) }.forEach { code ->
                DropdownMenuItem(
                    text = { Text(languageName(code), style = FlowFolio.item) },
                    onClick = {
                        open = false
                        onPick(code)
                    }
                )
            }
        }
    }
}

@Composable
private fun Passage(state: TranslateUiState) {
    Text(
        "“${state.source}”",
        style = FlowFolioType.sub.copy(
            fontFamily = FlowFolioType.heroTitle.fontFamily,
            fontStyle = FontStyle.Italic,
            fontSize = 15.sp
        )
            .merge(FlowFolio.hint),
        maxLines = PASSAGE_LINES,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun Result(state: TranslateUiState) {
    val context = LocalContext.current
    Column(Modifier.padding(top = 16.dp)) {
        when (state.status) {
            TranslateStatus.DONE -> {
                val result = state.result.orEmpty()
                Text(
                    result,
                    style = FlowFolioType.rowLabel.copy(fontSize = 19.sp, lineHeight = 27.sp).merge(FlowFolio.text),
                    modifier = Modifier.testTag(ReaderTestTags.TRANSLATION)
                )
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Action(R.string.reader_copy) { copy(context, result) }
                    Action(R.string.reader_share) { share(context, result) }
                }
            }

            TranslateStatus.FAILED -> Text(
                stringResource(R.string.reader_translate_failed),
                style = FlowFolioType.sub.merge(FlowFolio.subItem)
            )

            TranslateStatus.CHOOSE -> Text(
                stringResource(R.string.reader_translate_choose),
                style = FlowFolioType.sub.merge(FlowFolio.title)
            )

            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = FlowFolio.palette.title)
                val label = when (state.status) {
                    TranslateStatus.DOWNLOADING -> R.string.reader_translate_downloading
                    TranslateStatus.DETECTING -> R.string.reader_translate_detecting
                    else -> R.string.reader_translate_working
                }
                Text(
                    stringResource(label),
                    style = FlowFolioType.sub.merge(FlowFolio.hint),
                    modifier = Modifier.padding(start = 10.dp)
                )
            }
        }
    }
}

@Composable
private fun Action(label: Int, onClick: () -> Unit) {
    Text(
        stringResource(label),
        style = FlowFolioType.pill.copy(fontSize = 14.sp).merge(FlowFolio.title),
        modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClick).padding(vertical = 6.dp)
    )
}

/** Earlier translations, newest first; a tap shows one again. */
@Composable
private fun History(history: List<Translation>, onShow: (Translation) -> Unit, onClear: () -> Unit) {
    if (history.isEmpty()) return
    HorizontalDivider(color = FlowFolio.palette.edge, modifier = Modifier.padding(top = 12.dp))
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.reader_translate_history).uppercase(),
            style = FlowFolioType.eyebrow.merge(FlowFolio.subItem),
            modifier = Modifier.weight(1f)
        )
        Action(R.string.reader_translate_clear, onClear)
    }
    LazyColumn(Modifier.heightIn(max = HISTORY_HEIGHT).testTag(ReaderTestTags.TRANSLATE_HISTORY)) {
        items(history, key = { it.id }) { t ->
            Column(Modifier.fillMaxWidth().clickable { onShow(t) }.padding(vertical = 8.dp)) {
                Text(
                    "${languageName(t.from)} → ${languageName(t.to)}",
                    style = FlowFolioType.pill.merge(FlowFolio.hint)
                )
                Text(
                    t.source,
                    style = FlowFolioType.rowDetail.merge(FlowFolio.item),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    t.translated,
                    style = FlowFolioType.rowDetail.merge(FlowFolio.text),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** "es" -> "Spanish" (in the phone's language). */
internal fun languageName(code: String): String = Locale.forLanguageTag(code).let {
    it.getDisplayLanguage(Locale.getDefault()).replaceFirstChar { c -> c.titlecase(Locale.getDefault()) }
}
    .ifBlank { code }

private val PANEL_WIDTH = 640.dp
private const val HEIGHT_SHARE = 0.8f
private val MENU_HEIGHT = 420.dp
private val HISTORY_HEIGHT = 220.dp
private const val PASSAGE_LINES = 5
