package com.flowfolio.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioType

/**
 * The head of a top-level page: its name on the same line as its buttons, the line under it saying what the page is
 * for (owner, M4b, moved up 2026-11-07). The Shelves page had this shape already; every other page now wears it, so
 * the pages read alike and a page's select mode has somewhere to open under.
 */
@Composable
internal fun ScreenHeader(
    title: String,
    slogan: String,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null
) {
    Column(modifier.fillMaxWidth().padding(start = 4.dp, top = 10.dp, bottom = 6.dp)) {
        val narrow = LocalWindowInfo.current.containerDpSize.width < HEADER_WIDE
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            leading?.invoke()
            Text(
                title,
                // Folded, the title shares the row with Back and the page's buttons, so it steps down to fit
                // ("Bookmarks" with a Back and two buttons used to truncate to "Book..."; owner, 2026-11-07).
                style = FlowFolioType.screenTitle.merge(FlowFolio.title).let { base ->
                    if (narrow) {
                        base.copy(
                            fontSize = HEADER_NARROW_TITLE_SP.sp,
                            lineHeight = HEADER_NARROW_TITLE_SP.sp
                        )
                    } else {
                        base
                    }
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 10.dp)
            )
            if (actions != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    actions()
                }
            }
        }
        Text(
            slogan,
            style = FlowFolioType.sub.merge(FlowFolio.hint),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

/** Wide enough for the full-size title beside the page's buttons (the app's two-pane breakpoint). */
private val HEADER_WIDE = 600.dp

/** The title's size on a folded cover screen, small enough for "Bookmarks" beside a Back and two buttons. */
private const val HEADER_NARROW_TITLE_SP = 30f
