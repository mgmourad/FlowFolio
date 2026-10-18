package com.flowfolio.feature.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioGlyph
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.Glyph
import com.flowfolio.core.designsystem.glass
import com.flowfolio.core.designsystem.marker
import com.flowfolio.core.model.Annotation
import com.flowfolio.core.model.HighlightColor
import kotlin.math.roundToInt

/**
 * The glass bar over a selection or a tapped highlight: the five highlighter colours (the current one ringed), then
 * Note, Share and Copy (or Delete). It sits above [around], or below it when there is no room above.
 */
@Composable
internal fun BoxScope.HighlightBar(around: Rect, actions: HighlightActions) {
    val gap = with(androidx.compose.ui.platform.LocalDensity.current) { BAR_GAP.toPx() }
    Layout(
        content = {
            Row(
                Modifier
                    .glass(RoundedCornerShape(26.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .testTag(ReaderTestTags.HIGHLIGHT_BAR),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HighlightColor.entries.forEach { c -> ColourDot(c, c == actions.current) { actions.onColour(c) } }
                BarAction(R.string.reader_note, actions.onNote)
                BarAction(R.string.reader_share, actions.onShare)
                actions.onCopy?.let { BarAction(R.string.reader_copy, it) }
                actions.onDelete?.let { BarAction(R.string.reader_delete, it, warn = true) }
            }
        },
        modifier = Modifier.matchParentSize()
    ) { measurables, constraints ->
        val bar = measurables.first().measure(Constraints(maxWidth = constraints.maxWidth))
        val x = (around.center.x - bar.width / 2f).roundToInt().coerceIn(
            0,
            (constraints.maxWidth - bar.width).coerceAtLeast(0)
        )
        val above = around.top - gap - bar.height
        val y = if (above > gap * TOP_ROOM) above else around.bottom + gap
        layout(constraints.maxWidth, constraints.maxHeight) {
            bar.place(IntOffset(x, y.roundToInt().coerceIn(0, (constraints.maxHeight - bar.height).coerceAtLeast(0))))
        }
    }
}

@Composable
private fun ColourDot(colour: HighlightColor, current: Boolean, onClick: () -> Unit) {
    val fill = colour.marker
    val ring = FlowFolio.palette.text
    val name = stringResource(colour.label)
    Box(
        Modifier
            .size(30.dp)
            .semantics {
                contentDescription = name
                selected = current
            }
            .clip(CircleShape)
            .drawBehind { drawCircle(fill) }
            .then(if (current) Modifier.border(2.5.dp, ring, CircleShape) else Modifier)
            .clickable(onClick = onClick)
    )
}

@Composable
private fun BarAction(label: Int, onClick: () -> Unit, warn: Boolean = false) {
    Text(
        stringResource(label),
        style = FlowFolioType.pill.copy(fontSize = 13.sp).merge(if (warn) FlowFolio.subItem else FlowFolio.title),
        modifier = Modifier.clip(
            RoundedCornerShape(50)
        ).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 8.dp)
    )
}

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

/** The open book's highlights (the reader's Annotations panel): colour, passage, note and page; a tap goes there. */
@Composable
internal fun AnnotationList(all: List<Annotation>, navigation: BookNavigation, onGo: (Int) -> Unit) {
    Text(
        stringResource(R.string.reader_annotations),
        style = MaterialTheme.typography.titleLarge.merge(FlowFolio.title),
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
    )
    if (all.isEmpty()) {
        Text(
            stringResource(R.string.reader_annotations_empty),
            style = MaterialTheme.typography.bodyMedium.merge(FlowFolio.hint),
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp)
        )
        return
    }
    LazyColumn(Modifier.testTag(ReaderTestTags.ANNOTATION_LIST)) {
        items(all, key = { it.id }) { a ->
            Row(
                Modifier.fillMaxWidth().clickable { onGo(a.page) }.padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                val bar = a.color.marker
                Canvas(Modifier.width(4.dp).size(4.dp, 44.dp)) { drawRect(bar) }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(
                        "“${a.text}”",
                        style = FlowFolioType.sub.copy(
                            fontFamily = FlowFolioType.heroTitle.fontFamily,
                            fontStyle = FontStyle.Italic
                        )
                            .merge(FlowFolio.item),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    a.note?.let {
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
                            a.label ?: PageLookup.labelOf(a.page, navigation.labels)
                        ),
                        style = FlowFolioType.rowDetail.merge(FlowFolio.hint),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            HorizontalDivider(color = FlowFolio.palette.edge)
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
    }

private val BAR_GAP = 14.dp
private const val TOP_ROOM = 6f
private val NOTE_WIDTH = 480.dp
