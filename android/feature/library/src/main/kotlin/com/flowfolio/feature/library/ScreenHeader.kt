package com.flowfolio.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioType

/**
 * The head of a top-level page: its name, the line under it saying what the page is for, and its buttons on the right
 * (owner, M4b). The Shelves page had this shape already; every other page now wears it, so the pages read alike and a
 * page's select mode has somewhere to open under.
 *
 * The buttons sit on the slogan's line rather than above it: they are bottom-aligned with the whole heading and then
 * dropped by [HEADER_ACTIONS_DROP], which is what makes them line up with the words rather than float over them.
 */
@Composable
internal fun ScreenHeader(
    title: String,
    slogan: String,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null
) {
    Row(
        modifier.fillMaxWidth().padding(start = 4.dp, top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f).padding(end = 10.dp)) {
            Text(
                title,
                style = FlowFolioType.screenTitle.merge(FlowFolio.title),
                // One line, cut short: a long shelf name wrapped used to push the whole heading down.
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                slogan,
                style = FlowFolioType.sub.merge(FlowFolio.hint),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        if (actions != null) {
            Row(
                Modifier.offset(y = HEADER_ACTIONS_DROP),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                actions()
            }
        }
    }
}

/** How far the buttons drop to sit on the slogan's line instead of above it (owner, M4b). */
private val HEADER_ACTIONS_DROP = 8.dp
