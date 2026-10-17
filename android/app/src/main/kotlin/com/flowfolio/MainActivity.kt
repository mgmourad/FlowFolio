package com.flowfolio

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flowfolio.core.designsystem.FlowFolioTheme
import dagger.hilt.android.AndroidEntryPoint

private const val EXTRA_OPEN_BOOK = "openBookId"
private const val EXTRA_LAYOUT = "layout"
private const val EXTRA_IMPORT_PATH = "importPath"

/** The app's only Activity. Screens are Compose destinations (see [FlowFolioApp]). */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val appViewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIncoming(intent)
        setContent {
            val theme by appViewModel.theme.collectAsStateWithLifecycle()
            FlowFolioTheme(theme) { FlowFolioApp(appViewModel) }
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
        val debuggable = applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
        val bookId = intent.getStringExtra(EXTRA_OPEN_BOOK)
        if (debuggable && bookId != null) appViewModel.onDirectOpen(bookId, intent.getStringExtra(EXTRA_LAYOUT))
        // Debug builds: `--es importPath /data/local/tmp/...` imports a test book as a copy (measurement scripts).
        intent.getStringExtra(EXTRA_IMPORT_PATH)?.takeIf {
            debuggable
        }?.let { appViewModel.onSharedFile(Uri.fromFile(java.io.File(it)).toString()) }
    }
}
