package com.flowfolio.feature.reader

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.flowfolio.core.epub.EpubArchive
import com.flowfolio.core.epub.EpubPackage
import com.flowfolio.feature.reader.cache.PageKey
import com.flowfolio.feature.reader.cache.PageSource
import java.io.ByteArrayInputStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A fixed-layout (`rendition:layout: pre-paginated`) EPUB's pages (ADR-0019): one WebView page per linear spine item,
 * drawn at the page's size. It serves only the book's own files (any other request gets an empty 403, and there are no
 * book scripts), so a comics or children's book shows exactly as it was laid out instead of being reflowed.
 */
internal class FixedEpubPageSource(context: Context, private val archive: EpubArchive, pkg: EpubPackage) : PageSource {
    val pageCount: Int

    private val spine: List<String>
    private val renderer = FixedPageRenderer(context, archive)

    init {
        spine = pkg.spine.filter { it.linear && it.mediaType.contains("html") }.map { it.path }.ifEmpty {
            pkg.spine.map { it.path }
        }
        pageCount = spine.size
    }

    override suspend fun render(key: PageKey): ImageBitmap? {
        val path = spine.getOrNull(key.page) ?: return null
        return renderer.renderPage(path, key.size.width, key.size.height)?.asImageBitmap()
    }

    fun close() {
        renderer.close()
        archive.close()
    }
}

/** The WebView behind [FixedEpubPageSource]; one page loads and draws at a time, on the main thread. */
@SuppressLint("SetJavaScriptEnabled")
private class FixedPageRenderer(private val context: Context, private val archive: EpubArchive) {
    private var web: WebView? = null
    private var loaded: CompletableDeferred<Unit>? = null

    private fun ensureWeb(): WebView {
        web?.let { return it }
        IslandRenderer.enableWholeDocumentDraw()
        val w = WebView(context)
        w.settings.javaScriptEnabled = true // the book's own page; scripts are not served
        w.settings.allowFileAccess = false
        w.settings.allowContentAccess = false
        w.settings.blockNetworkLoads = true
        w.settings.useWideViewPort = true
        w.settings.loadWithOverviewMode = true
        w.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        w.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse =
                serve(request)

            override fun onPageFinished(view: WebView, url: String) {
                loaded?.complete(Unit)
            }
        }
        web = w
        return w
    }

    private fun serve(request: WebResourceRequest): WebResourceResponse {
        val url = request.url
        val path = url.path.orEmpty().removePrefix("/")
        val bytes = if (url.host == IslandRenderer.HOST) {
            archive.read(path)
        } else {
            null
                ?: return WebResourceResponse(
                    "text/plain",
                    "utf-8",
                    FORBIDDEN,
                    "Blocked",
                    emptyMap(),
                    ByteArrayInputStream(ByteArray(0))
                )
        }
        return WebResourceResponse(IslandRenderer.mimeOf(path), "utf-8", ByteArrayInputStream(bytes))
    }

    suspend fun renderPage(path: String, width: Int, height: Int): Bitmap? = withContext(Dispatchers.Main) {
        val w = ensureWeb()
        val done = CompletableDeferred<Unit>()
        loaded = done
        w.loadUrl("https://${IslandRenderer.HOST}/$path")
        withTimeoutOrNull(TIMEOUT_MS) { done.await() } ?: return@withContext null
        w.layout(0, 0, width, height)
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        w.draw(canvas)
        bmp
    }

    fun close() {
        web?.destroy()
        web = null
    }

    private companion object {
        const val FORBIDDEN = 403
        const val TIMEOUT_MS = 15_000L
    }
}
