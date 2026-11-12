package com.flowfolio

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import com.flowfolio.core.data.flow.FlowNotifications
import com.flowfolio.core.designsystem.BookOpeningHost
import com.flowfolio.core.designsystem.CoverFlightHost
import com.flowfolio.core.designsystem.DesignCatalog
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioSplash
import com.flowfolio.core.designsystem.FlowFolioTheme
import com.flowfolio.core.model.AppStyle
import com.flowfolio.core.model.PageTurn
import com.flowfolio.core.model.ThemePreference
import com.flowfolio.core.model.UserSettings
import com.flowfolio.feature.library.ResourcesOfferHost
import com.flowfolio.feature.reader.layout.ReaderLayout
import com.flowfolio.feature.reader.layout.chooseLayout
import com.flowfolio.feature.reader.riffle.RiffleDemo
import com.flowfolio.feature.reader.tone
import dagger.hilt.android.AndroidEntryPoint

private const val EXTRA_OPEN_BOOK = "openBookId"
private const val EXTRA_LAYOUT = "layout"
private const val EXTRA_START_PAGE = "startPage"
private const val EXTRA_IMPORT_PATH = "importPath"
private const val EXTRA_GC_NOW = "gcNow"
private const val EXTRA_CATALOG = "catalog"
private const val EXTRA_RIFFLE_DEMO = "riffleDemo"
private const val EXTRA_THEME = "theme"
private val DEBUG_EXTRAS = listOf(EXTRA_OPEN_BOOK, EXTRA_IMPORT_PATH, EXTRA_GC_NOW, EXTRA_CATALOG, EXTRA_RIFFLE_DEMO)

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
        // `--ez riffleDemo true`: the riffle scrubber on the mock's sample book (ADR-0022), before it joins the reader.
        val riffleDemo = isDebuggable() && intent.getBooleanExtra(EXTRA_RIFFLE_DEMO, false)
        // The animated splash plays on a cold start only; adb-driven debug launches go straight to work.
        val splashOnStart = savedInstanceState == null && DEBUG_EXTRAS.none(intent::hasExtra)
        setContent {
            val theme by appViewModel.theme.collectAsStateWithLifecycle()
            if (catalog || riffleDemo) {
                var dark by rememberSaveable { mutableStateOf(intent.getStringExtra(EXTRA_THEME) != "light") }
                FlowFolioTheme(if (dark) ThemePreference.DARK else ThemePreference.LIGHT) {
                    if (riffleDemo) RiffleDemo(dark) { dark = it } else DesignCatalog(dark) { dark = it }
                }
            } else {
                var splash by rememberSaveable { mutableStateOf(splashOnStart) }
                ThemeRevealHost(theme) { shown ->
                    FlowFolioTheme(shown.mode, style = shown.style, seed = shown.seed) {
                        SystemBarsFollowTheme(FlowFolio.palette.dark)
                        val reading by appViewModel.readerSettings.collectAsStateWithLifecycle()
                        BookOpeningHost(
                            paper = reading.paperStyle.tone(
                                FlowFolio.palette.dark,
                                rustic = shown.style == AppStyle.RUSTIC
                            ).paper,
                            spread = { w, h -> reading.opensAsSpread(w, h) }
                        ) { ConversionProgressHost(appViewModel) { CoverFlightHost { FlowFolioApp(appViewModel) } } }
                        Overlays(splash) { splash = false }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        appViewModel.checkForUpdates()
        appViewModel.checkWhatsNew()
    }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { appViewModel.notificationsAsked() }

    /** Android 13+: asks to post Dynamic Flow's notifications when the first book starts converting (ADR-0045). */
    @androidx.compose.runtime.Composable
    private fun AskNotificationsOnce() {
        val ask by appViewModel.askNotifications.collectAsStateWithLifecycle()
        androidx.compose.runtime.LaunchedEffect(ask) {
            if (!ask || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@LaunchedEffect
            val granted =
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                appViewModel.notificationsAsked()
            } else {
                notificationPermission.launch(
                    Manifest.permission.POST_NOTIFICATIONS
                )
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
        // A Dynamic Flow notification (ours, so not only in debug builds) opens its book.
        intent.getStringExtra(FlowNotifications.EXTRA_OPEN_BOOK)?.let(appViewModel::onNotificationOpen)
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
    // Everything drawn over the app: notices, banners, the first-run windows and the splash on top.
    @Composable
    private fun Overlays(splash: Boolean, onSplashDone: () -> Unit) {
        FlowNoticeHost(appViewModel.flowNotices, appViewModel::onNotificationOpen)
        ScanAskHost(appViewModel)
        val update by appViewModel.updateAvailable.collectAsStateWithLifecycle()
        update?.let { release ->
            UpdateBanner(
                release,
                onOpen = appViewModel::onUpdateOpen,
                onDismiss = appViewModel::dismissUpdate
            )
        }
        val updatedTo by appViewModel.updatedTo.collectAsStateWithLifecycle()
        updatedTo?.let { UpdatedBanner(it, onDismiss = appViewModel::dismissUpdated) }
        val whatsNew by appViewModel.whatsNew.collectAsStateWithLifecycle()
        val tutorialSeen by appViewModel.tutorialSeen.collectAsStateWithLifecycle()
        // The tour and the downloads offer come first (a first install, or the first update with the offer).
        if (tutorialSeen) {
            whatsNew?.let { release -> WhatsNewDialog(release, onOk = appViewModel::dismissWhatsNew) }
        }
        AskNotificationsOnce()
        // The offer of optional downloads is its own window, so it waits for the splash to finish (TM2).
        if (splash) FlowFolioSplash(stringResource(R.string.splash_tagline), onSplashDone) else ResourcesOfferHost()
    }

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
