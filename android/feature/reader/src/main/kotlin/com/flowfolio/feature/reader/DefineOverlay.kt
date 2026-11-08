package com.flowfolio.feature.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioGlyph
import com.flowfolio.core.designsystem.FlowFolioIconButton
import com.flowfolio.core.designsystem.FlowFolioPill
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.Glyph
import com.flowfolio.core.designsystem.glass
import com.flowfolio.core.model.DefineEntry

/**
 * FlowFolio's Define panel (owner, M3T3; ADR-0046): glass, in the reader's palette. Shows the selected word, its part
 * of speech, its English Wiktionary definition, Copy and Share, and the CC BY-SA attribution note.
 */
@Composable
internal fun BoxScope.DefineOverlay(
    request: DefineRequest,
    onClose: () -> Unit,
    viewModel: DefineViewModel = hiltViewModel()
) {
    LaunchedEffect(request) { viewModel.define(request) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(
        Modifier
            .align(Alignment.Center)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(18.dp)
            .widthIn(max = DEFINE_PANEL_WIDTH)
            .fillMaxWidth()
            .glass(RoundedCornerShape(28.dp))
            .padding(18.dp)
            .testTag(ReaderTestTags.DEFINE)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.reader_define),
                    style = FlowFolioType.screenTitle.copy(fontSize = 26.sp).merge(FlowFolio.title)
                )
                Text(
                    stringResource(R.string.reader_define_note),
                    style = FlowFolioType.rowDetail.merge(FlowFolio.hint)
                )
            }
            FlowFolioIconButton(onClick = onClose, glass = true) {
                FlowFolioGlyph(Glyph.Back, stringResource(R.string.reader_close), color = FlowFolio.palette.text)
            }
        }
        DefineBody(state)
    }
}

@Composable
private fun DefineBody(state: DefineUiState) {
    val context = LocalContext.current
    Column(Modifier.padding(top = 14.dp).verticalScroll(rememberScrollState())) {
        Text(
            "“${state.word}”",
            style = FlowFolioType.heroTitle.copy(fontStyle = FontStyle.Italic, fontSize = 22.sp)
                .merge(FlowFolio.item)
        )
        when (state.status) {
            DefineStatus.DONE -> {
                state.entries.forEach { entry -> DefineSourceRow(entry) }
                val combined = state.entries.joinToString("\n\n") { "${it.source}: ${it.definition}" }
                Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    DefineAction(R.string.reader_copy) { copy(context, "${state.word}: $combined") }
                    DefineAction(R.string.reader_share) { share(context, "${state.word} — $combined") }
                }
            }

            DefineStatus.NOT_FOUND -> Text(
                stringResource(R.string.reader_define_not_found),
                style = FlowFolioType.sub.merge(FlowFolio.subItem),
                modifier = Modifier.padding(top = 12.dp)
            )

            DefineStatus.WORKING -> Row(
                Modifier.padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = FlowFolio.palette.title)
                Text(
                    stringResource(R.string.reader_define_working),
                    style = FlowFolioType.sub.merge(FlowFolio.hint),
                    modifier = Modifier.padding(start = 10.dp)
                )
            }
        }
    }
}

/** One definition from one source: its name, part of speech and gloss. */
@Composable
private fun DefineSourceRow(entry: DefineEntry) {
    Column(Modifier.padding(top = 14.dp)) {
        Text(
            entry.source,
            style = FlowFolioType.eyebrow.merge(FlowFolio.hint)
        )
        entry.partOfSpeech?.takeIf { it.isNotBlank() }?.let { pos ->
            FlowFolioPill(pos, FlowFolio.subItem, Modifier.padding(top = 4.dp))
        }
        Text(
            entry.definition,
            style = FlowFolioType.rowLabel.copy(fontSize = 18.sp, lineHeight = 26.sp)
                .merge(FlowFolio.text),
            modifier = Modifier.padding(top = 8.dp).testTag(ReaderTestTags.DEFINITION)
        )
    }
}

@Composable
private fun DefineAction(label: Int, onClick: () -> Unit) {
    Text(
        stringResource(label),
        style = FlowFolioType.pill.copy(fontSize = 14.sp).merge(FlowFolio.title),
        modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClick).padding(vertical = 6.dp)
    )
}

private val DEFINE_PANEL_WIDTH = 560.dp
