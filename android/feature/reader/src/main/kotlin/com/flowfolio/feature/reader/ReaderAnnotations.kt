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
import com.flowfolio.core.designsystem.softShadow
import com.flowfolio.core.model.Annotation
import com.flowfolio.core.model.HighlightColor
import kotlin.math.roundToInt

/**
 * The selection menu (`.selmenu`, ADR-0052) over a selection or a tapped highlight: the nine highlighter colours in
 * one row, a hairline, then the actions with their names under them (Note, VoiceMark, Define, Translate, Copy or
 * Delete, Share). It sits above [around], or below it when there is no room above.
 */
@Composable
internal fun BoxScope.HighlightBar(around: Rect, actions: HighlightActions) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val gap = with(density) { BAR_GAP.toPx() }
    val margin = with(density) { BAR_EDGE_MARGIN.roundToPx() }
    val p = FlowFolio.palette
    Layout(
        content = {
            Column(
                Modifier
                    .width(MENU_WIDTH)
                    .softShadow(RoundedCornerShape(18.dp), MENU_SHADOW, blur = 40.dp, offsetY = 14.dp)
                    .background(p.surface, RoundedCornerShape(18.dp))
                    .border(1.dp, p.line, RoundedCornerShape(18.dp))
                    .padding(8.dp)
                    .testTag(ReaderTestTags.HIGHLIGHT_BAR)
            ) {
                HighlightColourRow(actions.current, actions.onColour)
                Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
                HighlightActionRow(actions)
            }
        },
        modifier = Modifier.matchParentSize()
    ) { measurables, constraints ->
        val maxW = (constraints.maxWidth - margin * 2).coerceAtLeast(0)
        val bar = measurables.first().measure(Constraints(maxWidth = maxW))
        val x = (around.center.x - bar.width / 2f).roundToInt().coerceIn(
            margin,
            (constraints.maxWidth - margin - bar.width).coerceAtLeast(margin)
        )
        val above = around.top - gap - bar.height
        val y = if (above > gap * TOP_ROOM) above else around.bottom + gap
        layout(constraints.maxWidth, constraints.maxHeight) {
            bar.place(
                IntOffset(
                    x,
                    y.roundToInt().coerceIn(margin, (constraints.maxHeight - margin - bar.height).coerceAtLeast(margin))
                )
            )
        }
    }
}

@Composable
private fun HighlightColourRow(current: HighlightColor?, onColour: (HighlightColor) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        HighlightColor.entries.forEach { c -> ColourDot(c, c == current) { onColour(c) } }
    }
}

@Composable
private fun HighlightActionRow(actions: HighlightActions) {
    val items = buildList {
        add(BarExtra(R.string.reader_note, Glyph.Edit, warn = false, onClick = actions.onNote))
        add(BarExtra(R.string.reader_voicemark, Glyph.Mic, warn = false, onClick = actions.onVoiceMark))
        actions.onDefine?.let { add(BarExtra(R.string.reader_define, Glyph.Book, warn = false, onClick = it)) }
        add(BarExtra(R.string.reader_translate, Glyph.Translate, warn = false, onClick = actions.onTranslate))
        // Copy before Share, as the mock has it; Delete (on a tapped highlight) goes last, away from the rest.
        if (!actions.extra.warn) add(actions.extra)
        add(BarExtra(R.string.reader_share, Glyph.Share, warn = false, onClick = actions.onShare))
        if (actions.extra.warn) add(actions.extra)
    }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        items.forEach { item -> BarAction(item.label, item.glyph, item.onClick, item.warn, Modifier.weight(1f)) }
    }
}

@Composable
private fun ColourDot(colour: HighlightColor, current: Boolean, onClick: () -> Unit) {
    val fill = colour.marker
    val ring = FlowFolio.palette.ink
    val name = stringResource(colour.label)
    // A 30 dp target around the 22 dp dot: nine of them fit the 300 dp menu.
    Box(
        Modifier
            .size(DOT_TARGET)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = name
                selected = current
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(DOT)
                .clip(CircleShape)
                .drawBehind { drawCircle(fill) }
                .border(if (current) 2.5.dp else 2.dp, if (current) ring else DOT_EDGE, CircleShape)
        )
    }
}

@Composable
private fun BarAction(label: Int, glyph: Glyph, onClick: () -> Unit, warn: Boolean, modifier: Modifier) {
    val name = stringResource(label)
    val p = FlowFolio.palette
    val ink = if (warn) p.danger else p.ink2
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = name }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        FlowFolioGlyph(glyph, contentDescription = null, color = ink, size = BAR_GLYPH, glow = false)
        Text(
            name,
            style = FlowFolioType.tab.copy(color = ink, fontSize = 10.5.sp, letterSpacing = 0.sp),
            maxLines = 1,
            softWrap = false
        )
    }
}

private val MENU_WIDTH = 320.dp
private val DOT = 22.dp
private val DOT_TARGET = 30.dp
private val MENU_SHADOW = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.25f)
private val DOT_EDGE = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.08f)

/** Writing a note for a highlight: a glass card with a text field, Save and Cancel. */
@Composable
internal fun BoxScope.NoteEditor(quote: String, initial: String?, onSave: (String) -> Unit, onCancel: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial.orEmpty()) }
    Column(
        Modifier
            .align(Alignment.Center)
            .imePadding()
            .padding(18.dp)
            .widthIn(max = NOTE_WIDTH)
            .fillMaxWidth()
            .glass(RoundedCornerShape(26.dp))
            .padding(18.dp)
            .testTag(ReaderTestTags.NOTE_EDITOR)
    ) {
        Text(
            stringResource(R.string.reader_note_title),
            style = MaterialTheme.typography.titleLarge.merge(FlowFolio.title)
        )
        Text(
            "“$quote”",
            style = FlowFolioType.sub.copy(fontStyle = FontStyle.Italic).merge(FlowFolio.hint),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp)
        )
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            minLines = 3,
            label = { Text(stringResource(R.string.reader_note)) },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        )
        Row(Modifier.align(Alignment.End).padding(top = 8.dp)) {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.reader_cancel)) }
            TextButton(onClick = { onSave(text) }) { Text(stringResource(R.string.reader_save)) }
        }
    }
}

/** Opens the share sheet with [text]. */
internal fun share(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Puts [text] on the clipboard. */
internal fun copy(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(null, text))
}

internal val HighlightColor.label: Int
    get() = when (this) {
        HighlightColor.YELLOW -> R.string.highlight_yellow
        HighlightColor.GREEN -> R.string.highlight_green
        HighlightColor.BLUE -> R.string.highlight_blue
        HighlightColor.PINK -> R.string.highlight_pink
        HighlightColor.PURPLE -> R.string.highlight_purple
        HighlightColor.ORANGE -> R.string.highlight_orange
        HighlightColor.TEAL -> R.string.highlight_teal
        HighlightColor.MINT -> R.string.highlight_mint
        HighlightColor.CORAL -> R.string.highlight_coral
    }

private val BAR_GLYPH = 20.dp
private val BAR_GAP = 14.dp
private val BAR_EDGE_MARGIN = 8.dp

/** The top row of the highlighter grid; the rest go under it (five and four since M4b). */
private const val TOP_ROOM = 6f
private val NOTE_WIDTH = 480.dp
