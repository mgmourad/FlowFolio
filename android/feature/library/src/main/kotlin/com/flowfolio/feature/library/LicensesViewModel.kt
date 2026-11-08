package com.flowfolio.feature.library

import android.content.Context
import android.content.res.AssetManager
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One third-party component and the licence it ships under. [text] is the full licence, read from the bundled assets
 * where a copy is shipped; it is null for components whose licence is named but whose full text is not bundled.
 */
data class LicenseEntry(val name: String, val license: String, val note: String, val text: String? = null)

/** A headed group of [LicenseEntry]s. [heading] is a string resource shown as the group's eyebrow. */
data class LicenseSection(val heading: Int, val entries: List<LicenseEntry>)

/**
 * The third-party licences for what ships in the app, so the reader can see them without leaving it (known issue 8).
 * The full texts for the fonts and PDFium are read from `assets/third_party_licenses/`, exactly the copies the app
 * distributes; the remaining components are listed by name and licence from the licence matrix (`docs/licenses.md`).
 */
@HiltViewModel
class LicensesViewModel @Inject constructor(@ApplicationContext private val context: Context) : ViewModel() {

    private val _sections = MutableStateFlow(loadSections())
    val sections: StateFlow<List<LicenseSection>> = _sections.asStateFlow()

    private fun loadSections(): List<LicenseSection> {
        val assets = context.assets
        return listOf(
            LicenseSection(
                R.string.licenses_fonts,
                FONTS.map { font ->
                    LicenseEntry(
                        font.name,
                        font.license,
                        font.note,
                        readAsset(assets, "third_party_licenses/fonts/${font.asset}")
                    )
                }
            ),
            LicenseSection(R.string.licenses_pdfium, pdfiumEntries(assets)),
            LicenseSection(R.string.licenses_shipped, SHIPPED)
        )
    }

    private fun pdfiumEntries(assets: AssetManager): List<LicenseEntry> {
        val entries = mutableListOf(
            LicenseEntry(
                "PDFium",
                "BSD-3-Clause / Apache-2.0",
                "The PDF rendering engine",
                readAsset(assets, "third_party_licenses/pdfium/LICENSE")
            )
        )
        val dir = "third_party_licenses/pdfium/third_party"
        listAssets(assets, dir).forEach { file ->
            entries += LicenseEntry(
                PDFIUM_THIRD_PARTY[file] ?: prettyName(file),
                "Bundled with PDFium",
                "A component inside PDFium",
                readAsset(assets, "$dir/$file")
            )
        }
        return entries
    }

    private fun readAsset(assets: AssetManager, path: String): String? =
        runCatching { assets.open(path).bufferedReader().use { it.readText() } }.getOrNull()

    private fun listAssets(assets: AssetManager, dir: String): List<String> =
        runCatching { assets.list(dir) }.getOrNull()?.sorted().orEmpty()
}

/** A font's display name, its licence and the asset that holds the full text. */
private data class BundledFont(val name: String, val license: String, val note: String, val asset: String)

private val FONTS = listOf(
    BundledFont("Fraunces", "SIL Open Font License 1.1", "The display and reading typeface", "Fraunces-OFL.txt"),
    BundledFont("Instrument Sans", "SIL Open Font License 1.1", "The interface typeface", "InstrumentSans-OFL.txt"),
    BundledFont("Literata", "SIL Open Font License 1.1", "The default reading typeface", "Literata-OFL.txt"),
    BundledFont("Charter", "Bitstream Charter licence", "A reading typeface", "Charter-Bitstream-License.txt"),
    BundledFont(
        "Atkinson Hyperlegible Next",
        "SIL Open Font License 1.1",
        "A reading typeface",
        "AtkinsonHyperlegibleNext-OFL.txt"
    ),
    BundledFont("EB Garamond", "SIL Open Font License 1.1", "A reading typeface", "EBGaramond-OFL.txt"),
    BundledFont("Source Sans 3", "SIL Open Font License 1.1", "A reading typeface", "SourceSans3-OFL.txt"),
    BundledFont("Lexend", "SIL Open Font License 1.1", "A reading typeface", "Lexend-OFL.txt"),
    BundledFont("Andika", "SIL Open Font License 1.1", "A reading typeface", "Andika-OFL.txt"),
    BundledFont("JetBrains Mono", "SIL Open Font License 1.1", "The code typeface", "JetBrainsMono-OFL.txt")
)

/** The PDFium third-party notices, keyed by their asset filename, so each reads by a real name. */
private val PDFIUM_THIRD_PARTY = mapOf(
    "abseil.txt" to "Abseil",
    "agg23.txt" to "Anti-Grain Geometry",
    "catapult.txt" to "Catapult",
    "cpu_features.txt" to "cpu_features",
    "dragonbox-Apache2-LLVM.txt" to "Dragonbox",
    "dragonbox-Boost.txt" to "Dragonbox (Boost)",
    "fast_float.txt" to "fast_float",
    "freetype.txt" to "FreeType",
    "harfbuzz.txt" to "HarfBuzz",
    "icu.txt" to "ICU",
    "lcms.txt" to "Little CMS",
    "libjpeg_turbo.ijg" to "libjpeg-turbo (IJG)",
    "libjpeg_turbo.md" to "libjpeg-turbo",
    "libopenjpeg.txt" to "OpenJPEG",
    "libpng.txt" to "libpng",
    "libunwind.txt" to "libunwind",
    "llvm-libc.txt" to "LLVM libc",
    "pdfium.txt" to "PDFium",
    "simdutf.txt" to "simdutf",
    "zlib.txt" to "zlib"
)

/** The components that ship without a bundled licence text, listed by name and licence from the licence matrix. */
private val SHIPPED = listOf(
    LicenseEntry("Kotlin", "Apache-2.0", "The language and standard library"),
    LicenseEntry("Jetpack Compose", "Apache-2.0", "The user interface toolkit"),
    LicenseEntry("Material 3", "Apache-2.0", "Design components"),
    LicenseEntry("Hilt and Dagger", "Apache-2.0", "Dependency injection"),
    LicenseEntry("Room", "Apache-2.0", "The library database"),
    LicenseEntry("DataStore Preferences", "Apache-2.0", "Stored settings"),
    LicenseEntry("Navigation Compose", "Apache-2.0", "Moving between screens"),
    LicenseEntry("kotlinx.coroutines", "Apache-2.0", "Concurrency"),
    LicenseEntry("kotlinx.serialization", "Apache-2.0", "The conversion sidecar's JSON"),
    LicenseEntry("pagecurl", "Apache-2.0", "The page-curl physics, a modified copy"),
    LicenseEntry("Haze", "Apache-2.0", "Glass blur behind the bars"),
    LicenseEntry("Chaquopy", "MIT", "Runs the embedded Python (CPython 3.12, PSF-2.0)"),
    LicenseEntry("yt-dlp", "Unlicense", "Finds the audio behind a video page"),
    LicenseEntry("ML Kit Translate", "Google ML Kit terms", "On-device translation"),
    LicenseEntry("ML Kit language-id", "Google ML Kit terms", "Detects a passage's language"),
    LicenseEntry("sherpa-onnx", "Apache-2.0", "Offline neural text-to-speech runtime (Kokoro and Piper)"),
    LicenseEntry("Apache Commons Compress", "Apache-2.0", "Unpacks downloaded voice-pack archives"),
    LicenseEntry("AndroidX ProfileInstaller", "Apache-2.0", "Installs the startup profile"),
    LicenseEntry("English Wiktionary", "CC BY-SA 4.0", "The offline dictionary's word data"),
    LicenseEntry("Princeton WordNet 3.1", "WordNet License", "Bundled lexical database of synonyms and hypernyms")
)

/** A filename's readable form, for any PDFium notice not in [PDFIUM_THIRD_PARTY]. */
private fun prettyName(file: String): String = file
    .substringBeforeLast('.')
    .replace('_', ' ')
    .replace('-', ' ')
    .split(' ')
    .filter { it.isNotBlank() }
    .joinToString(" ") { if (it == "libjpeg") "libjpeg-turbo" else it }
    .replaceFirstChar { it.titlecase() }
