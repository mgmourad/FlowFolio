package com.flowfolio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.flowfolio.core.data.Release
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioPrimaryButton
import com.flowfolio.core.designsystem.FlowFolioType

/**
 * The "what's new" window shown once after an update (owner, 2026-11-07). It is dismissed only by "Ok" -- there is
 * no outside-tap or back way out, so the notes cannot be swept away unread.
 */
@Composable
internal fun WhatsNewDialog(release: Release, onOk: () -> Unit) {
    val p = FlowFolio.palette
    val shape = RoundedCornerShape(24.dp)
    Dialog(onDismissRequest = { /* only "Ok" dismisses */ }) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(p.background, shape)
                .border(1.dp, p.edge, shape)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(R.string.whats_new_title),
                style = FlowFolioType.sectionTitle.merge(FlowFolio.title)
            )
            Text(
                stringResource(R.string.whats_new_body, release.versionName),
                style = FlowFolioType.rowDetail.merge(FlowFolio.hint)
            )
            Text(release.notes, style = FlowFolioType.rowDetail.merge(FlowFolio.item))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                FlowFolioPrimaryButton(text = stringResource(R.string.whats_new_ok), onClick = onOk)
            }
        }
    }
}
