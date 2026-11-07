package com.flowfolio.feature.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioChip
import com.flowfolio.core.designsystem.FlowFolioGhostButton
import com.flowfolio.core.designsystem.FlowFolioGlyph
import com.flowfolio.core.designsystem.FlowFolioMenu
import com.flowfolio.core.designsystem.FlowFolioPrimaryButton
import com.flowfolio.core.designsystem.FlowFolioSlider
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.Glyph
import com.flowfolio.core.designsystem.flowFolioRadioColors
import com.flowfolio.core.model.Ambient
import com.flowfolio.core.model.Atmosphere
import com.flowfolio.core.model.DEFAULT_AMBIENT_VOLUME
import com.flowfolio.core.model.PaperStyle
import com.flowfolio.core.model.UserSettings
import com.flowfolio.core.model.allAtmospheres
import com.flowfolio.core.model.atmosphereInForce

/** Where an atmosphere's sound comes from (owner, M5 round): a choice, so only one of the three is ever in force. */
private enum class SoundSource {
    AMBIENCE,
    FILE,
    STREAM
}

/** Which source [atmosphere] uses, read back from what it kept. */
private fun sourceOf(atmosphere: Atmosphere?): SoundSource {
    val sound = atmosphere?.soundUri
    return when {
        sound == null -> SoundSource.AMBIENCE

        sound.startsWith("http://", ignoreCase = true) || sound.startsWith("https://", ignoreCase = true) ->
            SoundSource.STREAM

        else -> SoundSource.FILE
    }
}

/**
 * Making an atmosphere of your own, or changing one (owner, M5): a name, the page it sets, and what plays behind it.
 *
 * A new one starts from the page settings in force, so whatever the reader has already arranged is what gets kept;
 * the paper is offered here as well, since it is the part of a mood that changes most. Editing fills everything in
 * from the atmosphere as saved.
 *
 * The three sources are a radio choice rather than three fields that fight each other (owner, M5 round): picking one
 * says plainly which is in force, and the other two keep what was typed in them in case the reader goes back.
 */
@Suppress("LongMethod", "CyclomaticComplexMethod") // one dialog: a name, a paper, three sources and a volume
@Composable
internal fun AtmosphereEditor(
    base: UserSettings,
    editing: Atmosphere?,
    actions: SettingsActions,
    audition: Audition,
    onDismiss: () -> Unit,
    onSave: (Atmosphere) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val p = FlowFolio.palette
    val key = editing?.name
    var name by rememberSaveable(key) { mutableStateOf(editing?.name.orEmpty()) }
    var paper by rememberSaveable(key) { mutableStateOf(editing?.paper ?: base.paperStyle) }
    var ambient by rememberSaveable(key) { mutableStateOf(editing?.ambient ?: Ambient.SILENCE) }
    var source by rememberSaveable(key) { mutableStateOf(sourceOf(editing)) }
    var file by rememberSaveable(key) {
        mutableStateOf(editing?.soundUri.takeIf { sourceOf(editing) == SoundSource.FILE })
    }
    var url by rememberSaveable(key) {
        mutableStateOf(editing?.soundUri.orEmpty().takeIf { sourceOf(editing) == SoundSource.STREAM }.orEmpty())
    }
    var volume by rememberSaveable(key) { mutableFloatStateOf(editing?.volume ?: DEFAULT_AMBIENT_VOLUME) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            file = uri.toString()
            source = SoundSource.FILE
        }
    }
    val shape = RoundedCornerShape(28.dp)
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .widthIn(max = EDITOR_WIDTH)
                .fillMaxWidth()
                .clip(shape)
                .background(p.background, shape)
                .border(1.dp, p.edge, shape)
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
                .testTag(LibraryTestTags.ATMOSPHERE_EDITOR),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                stringResource(if (editing == null) R.string.atmosphere_create else R.string.atmosphere_edit_title),
                style = FlowFolioType.sectionTitle.merge(FlowFolio.title)
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(stringResource(R.string.atmosphere_name)) },
                modifier = Modifier.fillMaxWidth().testTag(LibraryTestTags.ATMOSPHERE_NAME)
            )
            Text(stringResource(R.string.atmosphere_paper), style = FlowFolioType.rowLabel.merge(FlowFolio.item))
            PaperChips(paper) { paper = it }
            Text(stringResource(R.string.atmosphere_sound), style = FlowFolioType.rowLabel.merge(FlowFolio.item))
            SourceChoice(source) {
                actions.onAuditionStop()
                source = it
            }
            when (source) {
                SoundSource.AMBIENCE -> AmbientChips(ambient, overridden = false) { ambient = it }

                SoundSource.FILE -> FileSource(file) { pick.launch(AUDIO_TYPES) }

                SoundSource.STREAM -> StreamSource(
                    url = url,
                    audition = audition,
                    onUrl = { url = it },
                    onListen = { actions.onAudition(url) },
                    onStop = actions.onAuditionStop
                )
            }
            Text(
                stringResource(R.string.settings_atmosphere_volume),
                style = FlowFolioType.rowLabel.merge(FlowFolio.item)
            )
            FlowFolioSlider(
                value = volume,
                onValueChangeFinished = { volume = it },
                label = stringResource(R.string.settings_atmosphere_volume),
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Throwing one away belongs with editing it, away from Save so neither is hit by accident
                // (owner, M5 round 3). It asks first, since an atmosphere takes a while to put together.
                if (onDelete != null) {
                    var asking by rememberSaveable { mutableStateOf(false) }
                    DeleteMark { asking = true }
                    if (asking) {
                        ConfirmDelete(name, onDismiss = { asking = false }, onConfirm = onDelete)
                    }
                }
                Spacer(Modifier.weight(1f))
                FlowFolioGhostButton(text = stringResource(R.string.action_cancel), onClick = onDismiss)
                FlowFolioPrimaryButton(
                    text = stringResource(R.string.action_save),
                    onClick = {
                        val clean = name.trim()
                        if (clean.isNotEmpty()) {
                            onSave(
                                Atmosphere(
                                    name = clean,
                                    text = editing?.text ?: base.text,
                                    paper = paper,
                                    ambient = if (source == SoundSource.AMBIENCE) ambient else Ambient.SILENCE,
                                    volume = volume,
                                    soundUri = when (source) {
                                        SoundSource.AMBIENCE -> null
                                        SoundSource.FILE -> file
                                        SoundSource.STREAM -> url.trim().takeIf { it.isNotEmpty() }
                                    }
                                )
                            )
                        }
                    },
                    modifier = Modifier.testTag(LibraryTestTags.ATMOSPHERE_SAVE)
                )
            }
        }
    }
}

/** The three sources, as a radio choice. */
@Composable
private fun SourceChoice(selected: SoundSource, onPick: (SoundSource) -> Unit) {
    Column(Modifier.selectableGroup().testTag(LibraryTestTags.ATMOSPHERE_SOURCE)) {
        SoundSource.entries.forEach { source ->
            val on = source == selected
            Row(
                Modifier
                    .fillMaxWidth()
                    .selectable(selected = on, role = Role.RadioButton) { onPick(source) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = on, onClick = null, colors = flowFolioRadioColors())
                Text(
                    stringResource(source.label),
                    style = FlowFolioType.rowLabel.merge(if (on) FlowFolio.title else FlowFolio.item),
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }
    }
}

/** A sound from the phone: the button that opens the picker, and what was picked. */
@Composable
private fun FileSource(file: String?, onBrowse: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FlowFolioGhostButton(text = stringResource(R.string.atmosphere_browse), onClick = onBrowse)
        if (file != null) {
            Text(
                file.substringAfterLast('/').ifEmpty { stringResource(R.string.atmosphere_own_sound) },
                style = FlowFolioType.rowDetail.merge(FlowFolio.subItem),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * A sound from the web: the address, and a way to hear it before keeping it.
 *
 * Listening is the point of this (owner, M5 round): whether an address plays depends on the server at the other end,
 * so the only truthful answer comes from trying it.
 */
@Composable
private fun StreamSource(
    url: String,
    audition: Audition,
    onUrl: (String) -> Unit,
    onListen: () -> Unit,
    onStop: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = url,
            onValueChange = onUrl,
            singleLine = true,
            label = { Text(stringResource(R.string.atmosphere_url)) },
            supportingText = { Text(stringResource(R.string.atmosphere_url_detail)) },
            modifier = Modifier.fillMaxWidth().testTag(LibraryTestTags.ATMOSPHERE_URL)
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val playing = audition == Audition.PLAYING
            FlowFolioGhostButton(
                text = stringResource(if (playing) R.string.atmosphere_listen_stop else R.string.atmosphere_listen),
                onClick = if (playing) onStop else onListen,
                modifier = Modifier.testTag(LibraryTestTags.ATMOSPHERE_LISTEN)
            )
            val said = when (audition) {
                Audition.IDLE -> null
                Audition.TRYING -> R.string.atmosphere_listen_trying
                Audition.PLAYING -> R.string.atmosphere_listen_playing
                Audition.FAILED -> R.string.atmosphere_listen_failed
            }
            if (said != null) {
                Text(
                    stringResource(said),
                    style = FlowFolioType.rowDetail.merge(
                        if (audition == Audition.FAILED) FlowFolio.subItem else FlowFolio.hint
                    ),
                    modifier = Modifier.weight(1f).testTag(LibraryTestTags.ATMOSPHERE_LISTEN_SAID)
                )
            }
        }
    }
}

/** What each source is called. */
private val SoundSource.label: Int
    get() = when (this) {
        SoundSource.AMBIENCE -> R.string.atmosphere_source_ambience
        SoundSource.FILE -> R.string.atmosphere_source_file
        SoundSource.STREAM -> R.string.atmosphere_source_stream
    }

/** The bin in the editor, which opens the question rather than doing it. */
@Composable
private fun DeleteMark(onClick: () -> Unit) {
    val says = stringResource(R.string.atmosphere_delete)
    Box(
        Modifier
            .size(DELETE_MARK)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = says }
            .testTag(LibraryTestTags.ATMOSPHERE_DELETE),
        contentAlignment = Alignment.Center
    ) {
        FlowFolioGlyph(
            Glyph.Trash,
            contentDescription = null,
            color = FlowFolio.palette.subItem,
            size = 20.dp,
            glow = false
        )
    }
}

/** "Delete <name>?": the one question, with the answer that does it set apart by colour. */
@Composable
private fun ConfirmDelete(name: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val p = FlowFolio.palette
    val shape = RoundedCornerShape(24.dp)
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .widthIn(max = CONFIRM_WIDTH)
                .fillMaxWidth()
                .clip(shape)
                .background(p.background, shape)
                .border(1.dp, p.edge, shape)
                .padding(20.dp)
                .testTag(LibraryTestTags.ATMOSPHERE_DELETE_CONFIRM),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                stringResource(R.string.atmosphere_delete_title, name),
                style = FlowFolioType.sectionTitle.merge(FlowFolio.title)
            )
            Text(
                stringResource(R.string.atmosphere_delete_body),
                style = FlowFolioType.rowDetail.merge(FlowFolio.hint)
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
                FlowFolioGhostButton(text = stringResource(R.string.action_cancel), onClick = onDismiss)
                FlowFolioGhostButton(
                    text = stringResource(R.string.action_remove),
                    onClick = {
                        onDismiss()
                        onConfirm()
                    },
                    modifier = Modifier.testTag(LibraryTestTags.ATMOSPHERE_DELETE_YES)
                )
            }
        }
    }
}

private val DELETE_MARK = 40.dp
private val CONFIRM_WIDTH = 360.dp
