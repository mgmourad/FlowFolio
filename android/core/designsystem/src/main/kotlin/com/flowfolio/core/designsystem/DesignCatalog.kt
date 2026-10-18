package com.flowfolio.core.designsystem

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Debug-only showcase of the foundation slice (milestone 5): every token and component on the mock's background, so
 * it can be checked against `docs/design/FlowFolio_Mock.html` on a device. Not reachable in release builds.
 */
@Composable
fun DesignCatalog(dark: Boolean, onDarkChange: (Boolean) -> Unit) {
    val p = FlowFolio.palette
    Column(
        Modifier
            .fillMaxSize()
            .appBackground(p)
            .verticalScroll(rememberScrollState())
            .safeDrawingPadding()
            .padding(bottom = 40.dp)
    ) {
        Header(dark, onDarkChange)
        Chips()
        HeroCard()
        Shelf()
        Controls()
        Settings()
        Health()
        Glyphs()
    }
}

@Composable
private fun Header(dark: Boolean, onDarkChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 10.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Text("Library", style = FlowFolioType.screenTitle.merge(FlowFolio.title))
            Text(
                "10 books · 8 in progress",
                style = FlowFolioType.sub.merge(FlowFolio.hint),
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        FlowFolioSegmented(listOf("Dark", "Light"), if (dark) 0 else 1, { onDarkChange(it == 0) })
        Spacer(Modifier.width(12.dp))
        FlowFolioIconButton(onClick = {}) { FlowFolioGlyph(Glyph.Search, "Search", size = 20.dp) }
    }
}

@Composable
private fun Chips() {
    var on by remember { mutableIntStateOf(0) }
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf("All", "Textbooks", "Converted", "Novels", "Recently added").forEachIndexed { i, label ->
            FlowFolioChip(label, i == on, { on = i })
        }
    }
}

@Composable
private fun HeroCard() {
    FlowFolioCard(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp), radius = 26.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Book3D(SAMPLES[0], 104.dp, 152.dp)
            Column(Modifier.weight(1f)) {
                Text("CONTINUE READING", style = FlowFolioType.eyebrow.merge(FlowFolio.subItem))
                Text(
                    "Cellular Biology",
                    style = FlowFolioType.heroTitle.merge(FlowFolio.item),
                    modifier = Modifier.padding(top = 6.dp)
                )
                Text("Systems & Signals · Dr. Amara Okafor", style = FlowFolioType.sub.merge(FlowFolio.hint))
                Spacer(Modifier.height(12.dp))
                FlowFolioProgressBar(0.25f)
                Text(
                    "p. 214 of 842 · 18 min left in chapter",
                    style = FlowFolioType.rowDetail.merge(FlowFolio.hint),
                    modifier = Modifier.padding(top = 6.dp)
                )
                Spacer(Modifier.height(12.dp))
                FlowFolioPrimaryButton("Resume", {}, icon = {
                    FlowFolioGlyph(Glyph.Play, null, color = FlowFolio.palette.buttonInk, size = 14.dp)
                })
            }
        }
    }
}

@Composable
private fun Shelf() {
    Column(Modifier.padding(top = 22.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp), verticalAlignment = Alignment.Bottom) {
            Text("Fall Term", style = FlowFolioType.sectionTitle.merge(FlowFolio.title), modifier = Modifier.weight(1f))
            Text("5 books", style = FlowFolioType.chip.merge(FlowFolio.subItem))
        }
        Row(
            Modifier.horizontalScroll(
                rememberScrollState()
            ).padding(start = 30.dp, end = 30.dp, top = 22.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            SAMPLES.forEach { b -> Book3D(b, 90.dp, (124 + minOf(18, b.pageCount / 70)).dp, onClick = {}) }
        }
        ShelfPlank(Modifier.padding(horizontal = 14.dp))
    }
}

@Composable
private fun Controls() {
    var tab by remember { mutableIntStateOf(0) }
    var turn by remember { mutableIntStateOf(0) }
    Column(Modifier.padding(horizontal = 22.dp, vertical = 26.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            FlowFolioPrimaryButton("Continue", {}, icon = {
                FlowFolioGlyph(Glyph.Play, null, color = FlowFolio.palette.buttonInk, size = 14.dp)
            })
            FlowFolioGhostButton("Original PDF", {})
            FlowFolioIconButton({
            }) { FlowFolioGlyph(Glyph.Bookmark, "Bookmark", color = FlowFolio.palette.subItem, size = 20.dp) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FlowFolioPill("3rd Edition", FlowFolio.subItem)
            FlowFolioPill("604 pages", FlowFolio.hint)
            FlowFolioPill("PDF · Original", FlowFolio.item)
        }
        FlowFolioTabs(listOf("Overview", "Details", "Author", "Contents"), tab, { tab = it })
        FlowFolioSegmented(listOf("Curl", "Slide", "Scroll"), turn, { turn = it })
    }
}

@Composable
private fun Settings() {
    Text(
        "APPEARANCE",
        style = FlowFolioType.eyebrow.merge(FlowFolio.subItem),
        modifier = Modifier.padding(start = 28.dp)
    )
    FlowFolioCard(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
        radius = 24.dp,
        padding = androidx.compose.foundation.layout.PaddingValues(0.dp)
    ) {
        Column {
            SettingRow("Neon glow", "Soft light bloom on text and icons", true)
            HorizontalDivider(color = FlowFolio.palette.edge)
            SettingRow("Publisher fonts", "Use fonts embedded in the PDF", false)
            HorizontalDivider(color = FlowFolio.palette.edge)
            SettingRow("Reading reminders", "A gentle nudge each evening", false, Glyph.Bell)
        }
    }
}

@Composable
private fun SettingRow(label: String, detail: String, initial: Boolean, glyph: Glyph? = null) {
    var on by remember { mutableStateOf(initial) }
    FlowFolioRow {
        if (glyph != null) {
            Box(
                Modifier.padding(end = 0.dp),
                contentAlignment = Alignment.Center
            ) { FlowFolioGlyph(glyph, null, size = 19.dp) }
        }
        Column(Modifier.weight(1f)) {
            Text(label, style = FlowFolioType.rowLabel.merge(FlowFolio.item))
            Text(detail, style = FlowFolioType.rowDetail.merge(FlowFolio.hint))
        }
        FlowFolioToggle(on, { on = it })
    }
}

@Composable
private fun Health() {
    FlowFolioCard(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
        radius = 20.dp,
        padding = androidx.compose.foundation.layout.PaddingValues(16.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            FlowFolioRing(0.992f) {
                Text(
                    "99%",
                    style = FlowFolioType.stat.copy(fontSize = FlowFolioType.heroTitle.fontSize).merge(FlowFolio.item)
                )
            }
            Column {
                Text("Conversion health", style = FlowFolioType.rowLabel.merge(FlowFolio.title))
                Text("99.2% text coverage verified", style = FlowFolioType.sub.merge(FlowFolio.hint))
                Text("3 pages flagged for review", style = FlowFolioType.sub.merge(FlowFolio.subItem))
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun Glyphs() {
    FlowRow(
        Modifier.padding(horizontal = 22.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Glyph.entries.forEach { FlowFolioGlyph(it, it.name, size = 26.dp) }
    }
}

private val SAMPLES = listOf(
    BookFace("Cellular Biology", 842, colors = BookColors(Color(0xFF14B8A6), Color(0xFF064E3B), Color(0xFFEAFFF7))),
    BookFace("Linear Algebra", 604, colors = BookColors(Color(0xFF2747B8), Color(0xFF0B1640), Color(0xFFFFD27A))),
    BookFace("Organic Chemistry", 1118, colors = BookColors(Color(0xFFC2410C), Color(0xFF3B0D02), Color(0xFFFFEADB))),
    BookFace("Human Anatomy", 1264, colors = BookColors(Color(0xFFBE123C), Color(0xFF4C0519), Color(0xFFFFE6EE))),
    BookFace("Macroeconomics", 736, colors = BookColors(Color(0xFF52525B), Color(0xFF18181B), Color(0xFFFDE047)))
)
