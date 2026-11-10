package com.flowfolio.feature.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioGlyph
import com.flowfolio.core.designsystem.FlowFolioIconButton
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.Glyph
import com.flowfolio.core.designsystem.VoiceBar
import com.flowfolio.core.designsystem.Waveform
import com.flowfolio.core.designsystem.glass
import com.flowfolio.core.designsystem.marker
import com.flowfolio.core.model.Annotation
import com.flowfolio.core.model.HighlightColor
import kotlin.math.roundToInt

/**
 * The open book's highlights (the reader's Annotations panel): colour, passage, note and page; a tap goes there.
 *
 * Each row also carries its own buttons (owner, M5 round) -- a bin, and on a VoiceMark a play and stop as well --
 * so a recording can be heard and a mark thrown away without first going to the page it is on. The row itself still
 * navigates: the buttons are children, so a tap on one never reaches the row underneath.
 */
@Suppress("LongMethod")
@Composable
internal fun AnnotationList(
    all: List<Annotation>,
    navigation: BookNavigation,
    onGo: (Int) -> Unit,
    vm: ReaderAnnotationsViewModel = hiltViewModel()
) {
    // Two tabs, as the Dictionary panel has (owner, M5): what was written, and what was spoken.
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf(
        SheetTab(stringResource(R.string.reader_tab_notes), Glyph.Edit, ReaderTestTags.TAB_NOTES),
        SheetTab(stringResource(R.string.reader_voicemark), Glyph.Mic, ReaderTestTags.TAB_VOICEMARKS)
    )
    Text(
        stringResource(R.string.reader_annotations),
        style = MaterialTheme.typography.titleLarge.merge(FlowFolio.title),
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
    )
    Box(Modifier.padding(horizontal = 20.dp)) { SheetTabs(tabs, tab) { tab = it } }
    val shown = if (tab == 0) all.filter { it.voice == null } else all.filter { it.voice != null }
    if (shown.isEmpty()) {
        Text(
            stringResource(
                if (tab == 0) R.string.reader_annotations_empty else R.string.reader_voicemarks_empty
            ),
            style = MaterialTheme.typography.bodyMedium.merge(FlowFolio.hint),
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 20.dp)
        )
        return
    }
    val playingPath by vm.voicePlaying.collectAsStateWithLifecycle()
    LazyColumn(Modifier.testTag(ReaderTestTags.ANNOTATION_LIST)) {
        items(shown, key = { it.id }) { a ->
            val voice = a.voice
            AnnotationRow(
                annotation = a,
                navigation = navigation,
                playing = voice != null && playingPath == voice.path,
                onGo = { onGo(a.page) },
                onPlay = voice?.let {
                    { if (playingPath == it.path) vm.stopVoicePlayback() else vm.playVoice(it.path) }
                },
                onDelete = { if (voice != null) vm.removeVoice(a.id) else vm.remove(a.id) }
            )
            HorizontalDivider(color = FlowFolio.palette.edge)
        }
    }
}

/** One row of the list: the colour, the passage, its note and page, then its buttons. */
@Composable
private fun AnnotationRow(
    annotation: Annotation,
    navigation: BookNavigation,
    playing: Boolean,
    onGo: () -> Unit,
    onPlay: (() -> Unit)?,
    onDelete: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onGo).padding(
            start = 20.dp,
            end = 8.dp,
            top = 12.dp,
            bottom = 12.dp
        ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val bar = annotation.color.marker
        Canvas(Modifier.width(4.dp).size(4.dp, 44.dp)) { drawRect(bar) }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                "“${annotation.text}”",
                style = FlowFolioType.sub.copy(
                    fontFamily = FlowFolioType.heroTitle.fontFamily,
                    fontStyle = FontStyle.Italic
                )
                    .merge(FlowFolio.item),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            annotation.note?.let {
                Text(
                    it,
                    style = FlowFolioType.rowDetail.merge(FlowFolio.subItem),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Text(
                stringResource(
                    R.string.reader_page_short,
                    annotation.label ?: PageLookup.labelOf(annotation.page, navigation.labels)
                ),
                style = FlowFolioType.rowDetail.merge(FlowFolio.hint),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        if (onPlay != null) {
            RowAction(
                glyph = if (playing) Glyph.Stop else Glyph.Play,
                label = if (playing) R.string.reader_voicemark_stop_playing else R.string.reader_voicemark_play,
                tag = ReaderTestTags.ANNOTATION_PLAY,
                onClick = onPlay
            )
        }
        RowAction(
            glyph = Glyph.Trash,
            label = if (onPlay != null) R.string.reader_voicemark_discard else R.string.reader_annotation_delete,
            tag = ReaderTestTags.ANNOTATION_DELETE,
            onClick = onDelete,
            warn = true
        )
    }
}

/** A round button on a list row, drawn as a glyph like the selection bar's (owner, M5 round). */
@Composable
private fun RowAction(glyph: Glyph, label: Int, tag: String, onClick: () -> Unit, warn: Boolean = false) {
    val name = stringResource(label)
    Box(
        Modifier
            .size(ROW_ACTION)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = name }
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        FlowFolioGlyph(
            glyph,
            contentDescription = null,
            color = if (warn) FlowFolio.palette.danger else FlowFolio.palette.ink,
            size = ROW_GLYPH,
            glow = false
        )
    }
}

private val ROW_ACTION = 40.dp
private val ROW_GLYPH = 19.dp
