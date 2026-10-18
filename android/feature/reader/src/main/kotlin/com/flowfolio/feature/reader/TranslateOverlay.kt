package com.flowfolio.feature.reader

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.glass
import java.util.Locale

/**
 * Translate a passage without leaving the book (owner, 2026-10-18): Google Translate's own page in a glass panel over
 * the reader, from the detected language into the phone's. The view never navigates away from Google's sites, so
 * links and sign-in prompts stay inside it or go nowhere. The passage is sent to Google (ADR-0017).
 */
@Composable
internal fun BoxScope.TranslateOverlay(text: String, onClose: () -> Unit) {
    Column(
        Modifier
            .align(Alignment.Center)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(18.dp)
            .widthIn(max = PANEL_WIDTH)
            .fillMaxWidth()
            .fillMaxHeight(HEIGHT_SHARE)
            .glass(RoundedCornerShape(26.dp))
            .padding(14.dp)
            .testTag(ReaderTestTags.TRANSLATE)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.reader_translate),
                    style = MaterialTheme.typography.titleLarge.merge(FlowFolio.title)
                )
                Text(
                    stringResource(R.string.reader_translate_note),
                    style = FlowFolioType.rowDetail.merge(FlowFolio.hint)
                )
            }
            TextButton(onClick = onClose) { Text(stringResource(R.string.reader_close)) }
        }
        Box(Modifier.padding(top = 10.dp).fillMaxWidth().weight(1f).clip(RoundedCornerShape(18.dp))) {
            TranslatePage(text)
        }
    }
}

/** Google Translate's page for [text], kept on Google's own sites. */
@SuppressLint("SetJavaScriptEnabled") // Google Translate's page needs scripts; it only ever shows Google's pages.
@Composable
private fun TranslatePage(text: String) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                        !isGoogle(request.url)
                }
                loadUrl(translateUrl(text))
            }
        },
        onRelease = { it.destroy() },
        modifier = Modifier.fillMaxWidth().fillMaxHeight()
    )
}

/** Google Translate for [text], from any language into the phone's language. */
internal fun translateUrl(text: String, target: String = Locale.getDefault().language): String =
    Uri.Builder().scheme("https").authority("translate.google.com").path("/")
        .appendQueryParameter("sl", "auto")
        .appendQueryParameter("tl", target.ifBlank { "en" })
        .appendQueryParameter("text", text.take(MAX_CHARS))
        .appendQueryParameter("op", "translate")
        .build().toString()

/** Pages the view may show: Google's (Translate, and its consent and account pages), over HTTPS only. */
internal fun isGoogle(url: Uri): Boolean {
    val host = url.host.orEmpty()
    return url.scheme == "https" && (host == "google.com" || host.endsWith(".google.com"))
}

private val PANEL_WIDTH = 640.dp
private const val HEIGHT_SHARE = 0.72f

/** Google Translate's page takes up to 5,000 characters. */
private const val MAX_CHARS = 5000
