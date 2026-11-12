package com.flowfolio

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.flowfolio.core.designsystem.ButtonStyle
import com.flowfolio.core.designsystem.DialogAction
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioDialog
import com.flowfolio.core.designsystem.FlowFolioToggle
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.Glyph

/**
 * Before a scanned book is read by OCR (TM1, owner 2026-11-12): the text may not be exact, it takes about [minutes],
 * the reader can go on reading the original or another book meanwhile, and a notification says when it is done.
 * "Not now" keeps the book on its PDF; the switch stops the notice for every book.
 */
@Composable
internal fun ScanNotice(minutes: Int, onAnswer: (convert: Boolean, dontAsk: Boolean) -> Unit) {
    var dontAsk by rememberSaveable { mutableStateOf(false) }
    val time = if (minutes < MINUTES_PER_HOUR) {
        pluralStringResource(R.plurals.scan_notice_minutes, minutes, minutes)
    } else {
        val hours = (minutes + MINUTES_PER_HOUR / 2) / MINUTES_PER_HOUR
        pluralStringResource(R.plurals.scan_notice_hours, hours, hours)
    }
    FlowFolioDialog(
        onDismissRequest = { onAnswer(false, dontAsk) },
        title = stringResource(R.string.scan_notice_title),
        glyph = Glyph.Page,
        body = stringResource(R.string.scan_notice_body, time),
        actions = listOf(
            DialogAction(stringResource(R.string.scan_notice_not_now)) { onAnswer(false, dontAsk) },
            DialogAction(stringResource(R.string.scan_notice_convert), ButtonStyle.PRIMARY) { onAnswer(true, dontAsk) }
        ),
        modifier = Modifier.testTag(SCAN_NOTICE_TAG),
        extra = {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.scan_notice_dont_ask),
                    style = FlowFolioType.rowDetail.copy(color = FlowFolio.palette.ink2),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(12.dp))
                FlowFolioToggle(dontAsk, { dontAsk = it })
            }
        }
    )
}

private const val MINUTES_PER_HOUR = 60

internal const val SCAN_NOTICE_TAG = "scan_notice"
