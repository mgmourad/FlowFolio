package com.flowfolio.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.flowfolio.core.data.UpdateState
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioGhostButton
import com.flowfolio.core.designsystem.FlowFolioPrimaryButton
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.model.UserSettings

/**
 * Keeping FlowFolio current (owner, M5 round 4; ADR-0048).
 *
 * The app is not on a store, so nothing else would ever tell a reader a new build exists. The switch makes the
 * checking and the fetching happen on their own -- but never the installing, which is Android's own dialog and
 * always the reader's decision. The wording says so rather than implying a silent update.
 */
@Composable
internal fun UpdatesGroup(settings: UserSettings, version: String, state: UpdateState, actions: SettingsActions) {
    Group(R.string.settings_updates, 5) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(stringResource(R.string.settings_version), style = FlowFolioType.rowLabel.merge(FlowFolio.item))
            Text(version, style = FlowFolioType.rowDetail.merge(FlowFolio.hint))
        }
        Divider()
        UpdateRow(state, actions)
        Divider()
        SwitchRow(
            R.string.settings_auto_update,
            R.string.settings_auto_update_detail,
            settings.autoUpdate,
            actions.onAutoUpdate
        )
    }
}

/** Where the last check got to, in a line, and the buttons that follow from it. */
@Composable
private fun UpdateRow(state: UpdateState, actions: SettingsActions) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).testTag(LibraryTestTags.UPDATE_ROW),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        UpdateSays(state)
        if (state is UpdateState.Downloading) {
            LinearProgressIndicator(
                progress = { state.fraction },
                color = FlowFolio.palette.title,
                trackColor = FlowFolio.palette.edge,
                modifier = Modifier.fillMaxWidth()
            )
        }
        UpdateButtons(state, actions)
    }
}

/** The one line under the heading; nothing at all before the first check. */
@Composable
private fun UpdateSays(state: UpdateState) {
    val said = when (state) {
        UpdateState.Idle -> null

        UpdateState.Checking -> stringResource(R.string.settings_update_checking)

        UpdateState.UpToDate -> stringResource(R.string.settings_update_current)

        is UpdateState.Available -> stringResource(R.string.settings_update_found, state.release.versionName)

        is UpdateState.Downloading -> stringResource(R.string.settings_update_downloading)

        is UpdateState.Ready -> stringResource(R.string.settings_update_ready, state.release.versionName)

        is UpdateState.Failed -> stringResource(
            if (state.offline) R.string.settings_update_offline else R.string.settings_update_broken
        )
    } ?: return
    Text(
        said,
        style = FlowFolioType.rowDetail.merge(if (state is UpdateState.Failed) FlowFolio.subItem else FlowFolio.hint),
        modifier = Modifier.testTag(LibraryTestTags.UPDATE_SAID)
    )
}

/** Check, then download, then install: one button at a time, and a way out of each. */
@Composable
private fun UpdateButtons(state: UpdateState, actions: SettingsActions) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        val tag = Modifier.testTag(LibraryTestTags.UPDATE_ACTION)
        when (state) {
            is UpdateState.Available ->
                FlowFolioPrimaryButton(stringResource(R.string.settings_update_download), actions.onDownloadUpdate, tag)

            is UpdateState.Ready ->
                FlowFolioPrimaryButton(stringResource(R.string.settings_update_install), actions.onInstallUpdate, tag)

            else ->
                FlowFolioGhostButton(stringResource(R.string.settings_update_check), actions.onCheckUpdates, tag)
        }
        val busy = state is UpdateState.Available || state is UpdateState.Ready || state is UpdateState.Downloading
        if (busy) {
            FlowFolioGhostButton(text = stringResource(R.string.action_cancel), onClick = actions.onDismissUpdate)
        }
    }
}

/**
 * Emptying the cache, and starting over (owner, M5 round 4).
 *
 * Both say plainly what they do before they do it, because one is harmless and the other is not, and nothing about
 * two buttons sitting together tells them apart.
 */
@Composable
internal fun HousekeepingGroup(cacheBytes: Long, actions: SettingsActions) {
    var resetting by rememberSaveable { mutableStateOf(false) }
    Group(R.string.settings_housekeeping, 5) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(stringResource(R.string.settings_cache), style = FlowFolioType.rowLabel.merge(FlowFolio.item))
            Text(
                stringResource(R.string.settings_cache_detail, readableBytes(cacheBytes)),
                style = FlowFolioType.rowDetail.merge(FlowFolio.hint),
                modifier = Modifier.testTag(LibraryTestTags.CACHE_SIZE)
            )
            FlowFolioGhostButton(
                text = stringResource(R.string.settings_cache_clear),
                onClick = actions.onClearCache,
                modifier = Modifier.testTag(LibraryTestTags.CACHE_CLEAR)
            )
        }
        Divider()
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(stringResource(R.string.settings_reset), style = FlowFolioType.rowLabel.merge(FlowFolio.subItem))
            Text(
                stringResource(R.string.settings_reset_detail),
                style = FlowFolioType.rowDetail.merge(FlowFolio.hint)
            )
            FlowFolioGhostButton(
                text = stringResource(R.string.settings_reset),
                onClick = { resetting = true },
                modifier = Modifier.testTag(LibraryTestTags.RESET)
            )
        }
    }
    if (resetting) {
        ConfirmReset(onDismiss = { resetting = false }, onConfirm = actions.onResetApp)
    }
}

/** The question before a reset, which spells out both what goes and what is never touched. */
@Composable
private fun ConfirmReset(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val p = FlowFolio.palette
    val shape = RoundedCornerShape(24.dp)
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(p.background, shape)
                .border(1.dp, p.edge, shape)
                .padding(20.dp)
                .testTag(LibraryTestTags.RESET_CONFIRM),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(R.string.settings_reset_title),
                style = FlowFolioType.sectionTitle.merge(FlowFolio.title)
            )
            Text(
                stringResource(R.string.settings_reset_goes),
                style = FlowFolioType.rowDetail.merge(FlowFolio.item)
            )
            Text(
                stringResource(R.string.settings_reset_safe),
                style = FlowFolioType.rowDetail.merge(FlowFolio.hint)
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
                FlowFolioGhostButton(text = stringResource(R.string.action_cancel), onClick = onDismiss)
                FlowFolioGhostButton(
                    text = stringResource(R.string.settings_reset_yes),
                    onClick = {
                        onDismiss()
                        onConfirm()
                    },
                    modifier = Modifier.testTag(LibraryTestTags.RESET_YES)
                )
            }
        }
    }
}

/** Bytes as a reader would say them: "48 MB", not "50331648". */
internal fun readableBytes(bytes: Long): String = when {
    bytes >= GB -> "%.1f GB".format(bytes / GB.toFloat())
    bytes >= MB -> "%d MB".format(bytes / MB)
    bytes >= KB -> "%d kB".format(bytes / KB)
    else -> "$bytes bytes"
}

private const val KB = 1024L
private const val MB = KB * 1024
private const val GB = MB * 1024
