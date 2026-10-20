package com.flowfolio

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flowfolio.core.designsystem.BookOpeningHost
import com.flowfolio.core.designsystem.CoverFlightHost
import com.flowfolio.core.designsystem.DesignCatalog
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioSplash
import com.flowfolio.core.designsystem.FlowFolioTheme
import com.flowfolio.core.model.PageTurn
import com.flowfolio.core.model.ThemePreference
import com.flowfolio.core.model.UserSettings
import com.flowfolio.feature.reader.layout.ReaderLayout
import com.flowfolio.feature.reader.layout.chooseLayout
import com.flowfolio.feature.reader.tone
import dagger.hilt.android.AndroidEntryPoint

private const val EXTRA_OPEN_BOOK = "openBookId"
private const val EXTRA_LAYOUT = "layout"
private const val EXTRA_START_PAGE = "startPage"
private const val EXTRA_IMPORT_PATH = "importPath"
private const val EXTRA_GC_NOW = "gcNow"
private const val EXTRA_CATALOG = "catalog"
private const val EXTRA_THEME = "theme"
private val DEBUG_EXTRAS = listOf(EXTRA_OPEN_BOOK, EXTRA_IMPORT_PATH, EXTRA_GC_NOW, EXTRA_CATALOG)

/** The app's only Activity. Screens are Compose destinations (see [FlowFolioApp]). */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val appViewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIncoming(intent)
        // Debug builds: `--ez catalog true` shows the design catalog instead of the app (milestone 5 checks).
        val catalog = isDebuggable() && intent.getBooleanExtra(EXTRA_CATALOG, false)
        // The animated splash plays on a cold start only; adb-driven debug launches go straight to work.
        val splashOnStart = savedInstanceState == null && DEBUG_EXTRAS.none(intent::hasExtra)
        setContent {
            val theme by appViewModel.theme.collectAsStateWithLifecycle()
            if (catalog) {
                var dark by rememberSaveable { mutableStateOf(intent.getStringExtra(EXTRA_THEME) != "light") }
                FlowFolioTheme(if (dark) ThemePreference.DARK else ThemePreference.LIGHT) {
                    DesignCatalog(dark) { dark = it }
                }
            } else {
                var splash by rememberSaveable { mutableStateOf(splashOnStart) }
                ThemeRevealHost(theme) { shown ->
                    val look by appViewModel.readerSettings.collectAsStateWithLifecycle()
                    FlowFolioTheme(shown, glow = look.neonGlow) {
                        SystemBarsFollowTheme(FlowFolio.palette.dark)
                        val reading by appViewModel.readerSettings.collectAsStateWithLifecycle()
                        BookOpeningHost(
                            paper = reading.paperStyle.tone(FlowFolio.palette.dark).paper,
                            spread = { w, h -> reading.opensAsSpread(w, h) }
                        ) { CoverFlightHost { FlowFolioApp(appViewModel) } }
                        if (splash) FlowFolioSplash(stringResource(R.string.splash_tagline)) { splash = false }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncoming(intent)
    }

    /** PDFs handed over by other apps ("Open with FlowFolio", share sheet) are imported as copies. */
    private fun handleIncoming(intent: Intent) {
        val uri: Uri? = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            else -> null
        }
        uri?.let { appViewModel.onSharedFile(it.toString()) }
        // Debug builds: `am start ... --es openBookId <sha256> [--es layout single|spread]` (measurement scripts).
        val debuggable = isDebuggable()
        val bookId = intent.getStringExtra(EXTRA_OPEN_BOOK)
        // Optional `--ei startPage N` (0-based) sets the saved position first.
        if (debuggable && bookId != null) {
            val start = intent.getIntExtra(EXTRA_START_PAGE, -1).takeIf { it >= 0 }
            appViewModel.onDirectOpen(bookId, intent.getStringExtra(EXTRA_LAYOUT), start)
        }
        // Debug builds: `--ez gcNow true` collects garbage so tools/measure-reader.ps1 samples retained memory only.
        if (debuggable && intent.getBooleanExtra(EXTRA_GC_NOW, false)) collectGarbageForMeasurement()
        // Debug builds: `--es importPath /data/local/tmp/...` imports a test book as a copy (measurement scripts).
        intent.getStringExtra(EXTRA_IMPORT_PATH)?.takeIf {
            debuggable
        }?.let { appViewModel.onSharedFile(Uri.fromFile(java.io.File(it)).toString()) }
    }

    /** Status and navigation bar icons follow the app's theme (not the system's), over fully transparent bars. */
    @Composable
    private fun SystemBarsFollowTheme(dark: Boolean) {
        LaunchedEffect(dark) {
            val style = if (dark) {
                SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            } else {
                SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
            }
            enableEdgeToEdge(style, style)
            window.isNavigationBarContrastEnforced = false
        }
    }

    /** Debug builds, and the benchmark builds :benchmark drives (`BuildConfig.MEASUREMENT_HOOKS`), accept adb hooks. */
    private fun isDebuggable() = BuildConfig.MEASUREMENT_HOOKS ||
        applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0

    /** Debug builds only: lets the memory script sample retained memory (ADR-0013). Never called in release use. */
    @Suppress("ExplicitGarbageCollectionCall")
    private fun collectGarbageForMeasurement() {
        Runtime.getRuntime().gc()
        Runtime.getRuntime().runFinalization()
        Runtime.getRuntime().gc()
    }
}

/** Whether the reader will show two pages side by side in a window of [widthDp] x [heightDp] (its own rule). */
private fun UserSettings.opensAsSpread(widthDp: Float, heightDp: Float): Boolean =
    readerSpreads && pageTurn != PageTurn.SCROLL && chooseLayout(widthDp, heightDp) == ReaderLayout.SPREAD
