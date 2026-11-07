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

/**
 * Reading Atmosphere in Settings (owner, M5): the mood in force, how loud its ambience plays, and a way to make one
 * of your own.
 *
 * The choice is a labelled menu rather than an icon, so it reads as something to open and says what is on without
 * being opened.
 */
@Composable
internal fun AtmosphereGroup(settings: UserSettings, actions: SettingsActions, audition: Audition = Audition.IDLE) {
    var creating by rememberSaveable { mutableStateOf(false) }
    // The one being edited is kept by name rather than by value, so it survives the screen being rebuilt and always
    // shows what is actually saved.
    var editingName by rememberSaveable { mutableStateOf<String?>(null) }
    Group(R.string.settings_atmosphere, 1) {
        AtmosphereMenuRow(
            settings = settings,
            onPick = actions.onAtmosphere,
            onCreate = { creating = true },
            onEdit = { editingName = it.name }
        )
        val chosen = settings.atmosphereInForce()
        if (chosen != null && !chosen.silent) {
            Divider()
            AmbientVolumeRow(chosen.volume, actions.onAtmosphereVolume)
        }
    }
    val editing = editingName?.let { name -> settings.allAtmospheres().firstOrNull { it.name == name } }
    if (creating || editing != null) {
        val close = {
            actions.onAuditionStop()
            creating = false
            editingName = null
        }
        AtmosphereEditor(
            base = settings,
            editing = editing,
            actions = actions,
            audition = audition,
            onDismiss = close,
            onSave = {
                actions.onAtmosphereSave(it, editing?.name)
                close()
            },
            onDelete = editing?.takeIf { !it.builtIn }?.let { doomed ->
                {
                    actions.onAtmosphereDelete(doomed.name)
                    close()
                }
            }
        )
    }
}

/** The atmosphere in force, as a menu that says what it is: its name, what it plays, and a chevron. */
@Composable
private fun AtmosphereMenuRow(
    settings: UserSettings,
    onPick: (String?) -> Unit,
    onCreate: () -> Unit,
    onEdit: (Atmosphere) -> Unit
) {
    val p = FlowFolio.palette
    var open by remember { mutableStateOf(false) }
    val chosen = settings.atmosphereInForce()
    val shape = RoundedCornerShape(14.dp)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(stringResource(R.string.settings_atmosphere_pick), style = FlowFolioType.rowLabel.merge(FlowFolio.item))
        Text(
            stringResource(R.string.settings_atmosphere_detail),
            style = FlowFolioType.rowDetail.merge(FlowFolio.hint),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Box {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(p.surface, shape)
                    .border(1.dp, p.title.copy(alpha = MENU_EDGE), shape)
                    .clickable { open = true }
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .testTag(LibraryTestTags.ATMOSPHERE_ROW),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FlowFolioGlyph(Glyph.Sound, contentDescription = null, color = p.glyph, size = 18.dp, glow = false)
                Column(Modifier.weight(1f)) {
                    Text(
                        chosen?.name ?: stringResource(R.string.atmosphere_none),
                        style = FlowFolioType.rowLabel.merge(if (chosen != null) FlowFolio.title else FlowFolio.hint),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        soundLabel(chosen),
                        style = FlowFolioType.rowDetail.merge(FlowFolio.hint),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                FlowFolioGlyph(Glyph.Sort, contentDescription = null, color = p.hint, size = 16.dp, glow = false)
            }
            AtmosphereMenu(settings, open, chosen?.name, { open = false }, onPick, onCreate, onEdit)
        }
    }
}

/** The menu itself: silence, then every atmosphere on offer, then a way to make one. */
@Composable
private fun AtmosphereMenu(
    settings: UserSettings,
    open: Boolean,
    chosenName: String?,
    onClose: () -> Unit,
    onPick: (String?) -> Unit,
    onCreate: () -> Unit,
    onEdit: (Atmosphere) -> Unit
) {
    FlowFolioMenu(expanded = open, onDismissRequest = onClose) {
        // Silence first, so turning it all off is never a hunt (owner, M5).
        DropdownMenuItem(
            text = { Text(stringResource(R.string.atmosphere_none), style = FlowFolio.item) },
            onClick = {
                onClose()
                onPick(null)
            }
        )
        settings.allAtmospheres().forEach { atmosphere ->
            DropdownMenuItem(
                text = { AtmosphereLine(atmosphere, atmosphere.name == chosenName) },
                // The five that ship with the app can be applied and copied but not edited (owner, M5 round), so
                // only the reader's own carry a pencil.
                trailingIcon = if (atmosphere.builtIn) {
                    null
                } else {
                    {
                        EditMark {
                            onClose()
                            onEdit(atmosphere)
                        }
                    }
                },
                onClick = {
                    onClose()
                    onPick(atmosphere.name)
                }
            )
        }
        DropdownMenuItem(
            text = { Text(stringResource(R.string.atmosphere_create), style = FlowFolio.title) },
            onClick = {
                onClose()
                onCreate()
            }
        )
    }
}

/** The pencil beside an atmosphere of the reader's own, which opens it for editing. */
@Composable
private fun EditMark(onClick: () -> Unit) {
    Box(
        Modifier.size(EDIT_MARK).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        FlowFolioGlyph(
            Glyph.EditBox,
            stringResource(R.string.atmosphere_edit),
            color = FlowFolio.palette.glyph,
            size = 18.dp,
            glow = false
        )
    }
}

/** One line of the menu: the atmosphere's name, and under it what it plays. */
@Composable
private fun AtmosphereLine(atmosphere: Atmosphere, selected: Boolean) {
    Column {
        Text(
            atmosphere.name,
            style = FlowFolioType.rowLabel.merge(if (selected) FlowFolio.title else FlowFolio.item)
        )
        Text(soundLabel(atmosphere), style = FlowFolioType.rowDetail.merge(FlowFolio.hint))
    }
}

/** What an atmosphere plays, in words: a sound of the reader's own, a bundled ambience, or nothing. */
@Composable
private fun soundLabel(atmosphere: Atmosphere?): String = when {
    atmosphere == null -> stringResource(R.string.settings_atmosphere_none_detail)
    atmosphere.soundUri != null -> stringResource(R.string.atmosphere_own_sound)
    else -> stringResource(atmosphere.ambient.label)
}

/** How loud the ambience plays under the words. */
@Composable
private fun AmbientVolumeRow(volume: Float, onVolume: (Float) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(stringResource(R.string.settings_atmosphere_volume), style = FlowFolioType.rowLabel.merge(FlowFolio.item))
        FlowFolioSlider(
            value = volume,
            onValueChangeFinished = onVolume,
            label = stringResource(R.string.settings_atmosphere_volume),
            modifier = Modifier.fillMaxWidth().testTag(LibraryTestTags.ATMOSPHERE_VOLUME)
        )
    }
}

/** The papers an atmosphere can set. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PaperChips(selected: PaperStyle, onPick: (PaperStyle) -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PaperStyle.entries.forEach { style ->
            FlowFolioChip(
                text = style.name.lowercase().replaceFirstChar { it.uppercase() },
                selected = style == selected,
                onClick = { onPick(style) }
            )
        }
    }
}

/** The bundled ambiences, including Silence. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AmbientChips(selected: Ambient, overridden: Boolean, onPick: (Ambient) -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Ambient.entries.forEach { ambient ->
            FlowFolioChip(
                text = stringResource(ambient.label),
                selected = !overridden && ambient == selected,
                onClick = { onPick(ambient) }
            )
        }
    }
}

/** What each ambience is called in the app. */
internal val Ambient.label: Int
    get() = when (this) {
        Ambient.SILENCE -> R.string.ambient_silence
        Ambient.RAIN -> R.string.ambient_rain
        Ambient.FIREPLACE -> R.string.ambient_fireplace
        Ambient.OCEAN -> R.string.ambient_ocean
        Ambient.FOREST -> R.string.ambient_forest
        Ambient.STREAM -> R.string.ambient_stream
        Ambient.NIGHT_GARDEN -> R.string.ambient_night_garden
        Ambient.CAFE -> R.string.ambient_cafe
        Ambient.BROWN_NOISE -> R.string.ambient_brown_noise
    }

internal val AUDIO_TYPES = arrayOf("audio/*")
internal val EDITOR_WIDTH = 440.dp
private const val MENU_EDGE = 0.45f

private val EDIT_MARK = 32.dp
