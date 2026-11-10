package com.flowfolio.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioGlyph
import com.flowfolio.core.designsystem.FlowFolioIconButton
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.Glyph
import com.flowfolio.core.designsystem.LocalChromePadding
import com.flowfolio.core.designsystem.chromeStart
import com.flowfolio.core.designsystem.reveal

/**
 * The open-source licences for what ships in the app, reached from Settings. The fonts' and PDFium's full texts are
 * read from the very assets the app distributes; the rest are listed by name and licence. Tapping a row that has a
 * full text expands it (known issue 8, "needed before release").
 */
@Composable
fun LicensesScreen(onBack: () -> Unit, viewModel: LicensesViewModel = hiltViewModel()) {
    val sections by viewModel.sections.collectAsStateWithLifecycle()
    LicensesContent(sections, onBack)
}

@Composable
fun LicensesContent(sections: List<LicenseSection>, onBack: () -> Unit) {
    val chrome = LocalChromePadding.current
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    Box(Modifier.fillMaxSize().chromeStart().testTag(LibraryTestTags.LICENSES)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = insets.calculateTopPadding())
                .padding(horizontal = 18.dp)
        ) {
            ScreenHeader(
                title = stringResource(R.string.licenses_title),
                slogan = stringResource(R.string.licenses_sub),
                modifier = Modifier.reveal(0),
                leading = {
                    FlowFolioIconButton(
                        onClick = onBack,
                        glass = true,
                        modifier = Modifier.padding(end = 14.dp),
                        tooltip = stringResource(R.string.action_back)
                    ) {
                        FlowFolioGlyph(Glyph.Back, stringResource(R.string.action_back), color = FlowFolio.palette.text)
                    }
                }
            )
            Text(
                stringResource(R.string.licenses_mit),
                style = FlowFolioType.sub.merge(FlowFolio.hint),
                modifier = Modifier.reveal(1).padding(start = 4.dp, top = 4.dp, bottom = 4.dp)
            )
            sections.forEachIndexed { i, section ->
                Group(section.heading, 2 + i) {
                    section.entries.forEachIndexed { j, entry ->
                        if (j > 0) Divider()
                        LicenseEntryRow(entry)
                    }
                }
            }
            Spacer(Modifier.height(insets.calculateBottomPadding() + chrome.calculateBottomPadding() + 24.dp))
        }
    }
}

/** One component: its name, licence and a note, with the full text shown beneath once the row is tapped. */
@Composable
private fun LicenseEntryRow(entry: LicenseEntry) {
    var expanded by rememberSaveable(entry.name) { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (entry.text != null) Modifier.clickable { expanded = !expanded } else Modifier)
            .padding(horizontal = 16.dp, vertical = 13.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(entry.name, style = FlowFolioType.rowLabel.merge(FlowFolio.item))
                Text(entry.license, style = FlowFolioType.rowDetail.merge(FlowFolio.subItem))
                if (entry.note.isNotBlank()) {
                    Text(
                        entry.note,
                        style = FlowFolioType.rowDetail.merge(FlowFolio.hint),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            if (entry.text != null) {
                FlowFolioGlyph(
                    Glyph.GoTo,
                    stringResource(if (expanded) R.string.licenses_hide else R.string.licenses_show),
                    color = FlowFolio.palette.glyph,
                    size = 18.dp,
                    glow = false,
                    modifier = Modifier.padding(start = 10.dp).rotate(if (expanded) 90f else 0f)
                )
            }
        }
        if (expanded && entry.text != null) {
            Text(
                entry.text,
                style = FlowFolioType.sub.merge(FlowFolio.hint),
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}
