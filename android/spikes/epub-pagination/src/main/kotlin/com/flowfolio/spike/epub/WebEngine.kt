package com.flowfolio.spike.epub

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream
import kotlin.coroutines.resume
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

/**
 * Candidate A (WebView + CSS columns) and C's island renderer. Everything is served from the book through
 * `https://book.local/`; any other request gets an empty 403, so a book can never reach the network. Book scripts are
 * stripped before the page loads; only our own measuring script runs.
 */
@SuppressLint("SetJavaScriptEnabled")
class WebEngine(private val web: WebView, private val book: EpubBook) {
    private var loaded: CompletableDeferred<Unit>? = null
    var blockedRequests = 0
        private set

    init {
        web.settings.javaScriptEnabled = true
        web.settings.allowFileAccess = false
        web.settings.allowContentAccess = false
        web.settings.blockNetworkLoads = true
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
                val url = request.url
                if (url.host != HOST) {
                    blockedRequests++
                    return WebResourceResponse("text/plain", "utf-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                }
                val path = url.path.orEmpty().removePrefix("/")
                val bytes = (if (path == ISLANDS_PAGE) islandsHtml?.toByteArray() else book.read(path))
                    ?: return WebResourceResponse("text/plain", "utf-8", 404, "Missing", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                val mime = mimeOf(path)
                val body = if (mime == "application/xhtml+xml" || mime == "text/html") stripScripts(bytes) else bytes
                return WebResourceResponse(mime, "utf-8", ByteArrayInputStream(body))
            }

            override fun onPageFinished(view: WebView, url: String) {
                loaded?.complete(Unit)
            }
        }
    }

    private var islandsHtml: String? = null

    /** Screen pixels per CSS pixel (3 on the Fold): the WebView lays out in CSS pixels, everything else in screen pixels. */
    private val dpr: Float get() = web.resources.displayMetrics.density

    private suspend fun load(path: String) {
        val done = CompletableDeferred<Unit>()
        loaded = done
        web.loadUrl("https://$HOST/$path")
        withTimeout(TIMEOUT_MS) { done.await() }
    }

    suspend fun js(script: String): String = withTimeout(TIMEOUT_MS) {
        suspendCancellableCoroutine { cont -> web.evaluateJavascript(script) { cont.resume(it ?: "null") } }
    }

    /** Waits for fonts and two frames, so measurements see the final layout. */
    private suspend fun settle() {
        js("(async()=>{await document.fonts.ready;})()")
        repeat(2) { withTimeout(TIMEOUT_MS) { suspendCancellableCoroutine { c -> web.postVisualStateCallback(0, object : WebView.VisualStateCallback() {
            override fun onComplete(requestId: Long) = c.resume(Unit)
        }) } } }
    }

    data class WebChapter(val pages: Int, val loadMs: Long, val paginateMs: Long, val lineRects: Int, val badRects: Int)

    /** Loads [path] paginated into columns of the WebView's size with [margin] px margins at [fontPercent]. */
    suspend fun paginate(path: String, fontPercent: Int, margin: Int): WebChapter {
        // In CSS pixels: the WebView's own size, margins and the body text size (18 CSS px at 100%, like 18sp natively).
        val w = Math.round(web.width / dpr)
        val h = Math.round(web.height / dpr)
        val margin = Math.round(margin / dpr)
        val t0 = SystemClock.elapsedRealtime()
        load(path)
        val t1 = SystemClock.elapsedRealtime()
        js(
            """(function(){var s=document.createElement('style');s.textContent=
            'html{height:${h}px !important;width:${w}px;margin:0 !important;padding:${margin}px !important;box-sizing:border-box;'+
            'column-width:${w - 2 * margin}px;column-gap:${2 * margin}px;column-fill:auto;overflow:hidden;font-size:${BODY_CSS_PX * fontPercent / 100f}px;}'+
            'body{margin:0 !important;padding:0 !important;}img,svg,table,math,figure{max-width:100%;break-inside:avoid;}'+
            'h1,h2,h3{break-after:avoid;}p{orphans:2;widows:2;}';
            document.head.appendChild(s);})()"""
        )
        settle()
        val result = JSONObject(
            js(
                """(function(){var W=$w,H=$h,M=$margin,n=Math.round(document.documentElement.scrollWidth/W);
                var bad=0,total=0,walker=document.createTreeWalker(document.body,NodeFilter.SHOW_TEXT);
                var r=document.createRange();while(walker.nextNode()){var t=walker.currentNode;if(!t.data.trim())continue;
                r.selectNodeContents(t);var rects=r.getClientRects();for(var i=0;i<rects.length;i++){var q=rects[i];
                if(q.width<1)continue;total++;var x=q.left+window.scrollX,col=Math.floor(x/W);
                if(q.right+window.scrollX>(col+1)*W-M+1||q.bottom>H-M+1||q.top<M-1)bad++;}}
                var sw=document.documentElement.scrollWidth;if(!isFinite(n))n=-1;return JSON.stringify({pages:n,total:total,bad:bad,sw:sw,W:W,ct:document.contentType});})()"""
            ).let { JSONObject("{\"v\":$it}").getString("v") }
        )
        val t2 = SystemClock.elapsedRealtime()
        if (result.getInt("pages") < 0) android.util.Log.w("EpubSpike", "paginate $path: $result")
        return WebChapter(result.getInt("pages"), t1 - t0, t2 - t1, result.getInt("total"), result.getInt("bad"))
    }

    /** Draws page [index] of the loaded chapter into [into] (same size as the WebView); returns the time it took. */
    suspend fun snapshot(index: Int, into: Bitmap): Long {
        val t0 = SystemClock.elapsedRealtime()
        web.scrollTo(index * web.width, 0)
        settle()
        val c = Canvas(into)
        c.translate(-web.scrollX.toFloat(), -web.scrollY.toFloat())
        web.draw(c)
        return SystemClock.elapsedRealtime() - t0
    }

    data class IslandBox(val id: Int, val w: Int, val h: Int, val baseline: Int)

    /** C: lays out all [islands] of a chapter at [bodyPx] in one page and returns their sizes. */
    suspend fun measureIslands(islands: List<Block.Island>, bodyPx: Float, maxWidth: Int, css: String?): List<IslandBox> {
        if (islands.isEmpty()) return emptyList()
        val cssBody = bodyPx / dpr
        val cssMax = (maxWidth / dpr).toInt()
        val html = StringBuilder(
            "<!DOCTYPE html><html><head><meta charset='utf-8'/>" + (css?.let { "<link rel='stylesheet' href='$it'/>" } ?: "") +
                "<style>html,body{margin:0;padding:0;background:#fff}body{font-family:serif;font-size:${cssBody}px;width:${cssMax}px}" +
                ".i{display:inline-block;max-width:${cssMax}px}.b{display:block;margin:0}</style></head><body>"
        )
        islands.forEach { html.append("<div class='b'><span class='i' id='i${it.id}'>").append(it.xml).append("</span></div>") }
        html.append("</body></html>")
        islandsHtml = html.toString()
        load(ISLANDS_PAGE)
        settle()
        val json = js(
            """(function(){var out=[];document.querySelectorAll('.i').forEach(function(e){var r=e.getBoundingClientRect();
            var probe=document.createElement('span');probe.style.cssText='display:inline-block;width:0;height:0;vertical-align:baseline';
            e.appendChild(probe);var b=probe.getBoundingClientRect().top-r.top;e.removeChild(probe);
            out.push([parseInt(e.id.substring(1)),Math.ceil(r.width),Math.ceil(r.height),Math.round(b),Math.round(r.top+window.scrollY)]);});
            return JSON.stringify(out);})()"""
        )
        val arr = JSONArray(JSONObject("{\"v\":$json}").getString("v"))
        islandTops.clear()
        return (0 until arr.length()).map {
            val a = arr.getJSONArray(it)
            // Back to screen pixels.
            islandTops[a.getInt(0)] = (a.getInt(4) * dpr).toInt()
            IslandBox(a.getInt(0), (a.getInt(1) * dpr).toInt() + 1, (a.getInt(2) * dpr).toInt() + 1, (a.getInt(3) * dpr).toInt())
        }
    }

    private val islandTops = HashMap<Int, Int>()

    /** C: snapshots one measured island from the islands page into a bitmap of its size. */
    suspend fun renderIsland(box: IslandBox): Bitmap {
        val top = islandTops[box.id] ?: 0
        web.scrollTo(0, top)
        settle()
        val bmp = Bitmap.createBitmap(box.w.coerceAtLeast(1), box.h.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.translate(0f, -(top - web.scrollY).toFloat() - web.scrollY.toFloat())
        web.draw(c)
        return bmp
    }

    companion object {
        const val HOST = "book.local"
        const val BODY_CSS_PX = 18f
        /** A stalled WebView (screen off, broken page) fails the measurement instead of hanging the run. */
        const val TIMEOUT_MS = 20_000L
        private const val ISLANDS_PAGE = "__islands.html"
        private val SCRIPT = Regex("<script[\\s\\S]*?</script>|<script[^>]*/>", RegexOption.IGNORE_CASE)
        private val HANDLERS = Regex("\\son[a-z]+\\s*=\\s*\"[^\"]*\"", RegexOption.IGNORE_CASE)

        fun stripScripts(bytes: ByteArray): ByteArray =
            bytes.decodeToString().replace(SCRIPT, "").replace(HANDLERS, "").toByteArray()

        fun mimeOf(path: String): String = when (path.substringAfterLast('.').lowercase()) {
            "xhtml", "xht" -> "application/xhtml+xml"
            "html", "htm" -> "text/html"
            "css" -> "text/css"
            "svg" -> "image/svg+xml"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "otf" -> "font/otf"
            "ttf" -> "font/ttf"
            "woff" -> "font/woff"
            "woff2" -> "font/woff2"
            else -> "application/octet-stream"
        }
    }
}
