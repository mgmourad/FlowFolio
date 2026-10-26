package com.flowfolio.feature.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioCard
import com.flowfolio.core.designsystem.FlowFolioGlyph
import com.flowfolio.core.designsystem.FlowFolioPrimaryButton
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.Glyph
import com.flowfolio.core.designsystem.appBackground

/**
 * A book that can't be shown (missing file, damaged PDF or EPUB): a card on the app's own background in the app's
 * theme, rather than a message on blank paper, since there is no page to read yet.
 */
@Composable
internal fun ReaderNotice(failed: ReaderState.Failed, onBack: () -> Unit) {
    val p = FlowFolio.palette
    StatusBarForApp(p.dark)
    val (title, body) = when {
        failed.missingFile -> R.string.reader_missing_title to R.string.reader_missing
        failed.epub -> R.string.reader_damaged_title to R.string.reader_damaged_epub
        else -> R.string.reader_damaged_title to R.string.reader_damaged
    }
    Box(
        Modifier.fillMaxSize().appBackground(p).systemBarsPadding().padding(24.dp).testTag(ReaderTestTags.NOTICE),
        contentAlignment = Alignment.Center
    ) {
        FlowFolioCard(Modifier.widthIn(max = NOTICE_WIDTH), radius = 24.dp, padding = PaddingValues(28.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                FlowFolioGlyph(Glyph.Book, contentDescription = null, color = p.subItem, size = 38.dp)
                Spacer(Modifier.size(14.dp))
                Text(
                    stringResource(title),
                    style = FlowFolioType.sectionTitle.merge(FlowFolio.title),
                    textAlign = TextAlign.Center
                )
                Text(
                    stringResource(body),
                    style = FlowFolioType.sub.merge(FlowFolio.hint),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Spacer(Modifier.size(22.dp))
                FlowFolioPrimaryButton(stringResource(R.string.reader_back), onClick = onBack)
            }
        }
    }
}

/** Status bar icons for the app's background, not the paper's (the reader sets them for the paper above this). */
@Composable
private fun StatusBarForApp(dark: Boolean) {
    val view = LocalView.current
    DisposableEffect(dark) {
        val window = (view.context as? android.app.Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val before = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = !dark
        onDispose { if (before != null) controller.isAppearanceLightStatusBars = before }
    }
}

private val NOTICE_WIDTH = 420.dp
