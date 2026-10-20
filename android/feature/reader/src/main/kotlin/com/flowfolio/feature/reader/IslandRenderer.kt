package com.flowfolio.feature.reader

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.flowfolio.core.epub.EpubArchive
import java.io.ByteArrayInputStream
import kotlin.coroutines.resume
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

/** An island's size in screen pixels, its baseline from the top, and where it sits on the islands page. */
internal data class IslandBox(val id: Int, val w: Int, val h: Int, val baseline: Int, val top: Int, val left: Int)

/** One island to lay out: its id, markup, and whether it sits in running text. */
internal data class IslandItem(val id: Int, val xml: String, val inline: Boolean)

/**
 * Lays out and draws islands (MathML, SVG, complex tables) with the system WebView (ADR-0019). The WebView is in the
 * reader's window under the pages, serves only the book's own files (any other request gets an empty 403, and there
 * are no book scripts), and draws whole documents in software so each island is captured at its exact rectangle.
 * Main thread only.
 */
@SuppressLint("SetJavaScriptEnabled")
internal class IslandRenderer(private val web: WebView, private val archive: EpubArchive) {
    private var loaded: CompletableDeferred<Unit>? = null
    private var page: String = ""
    private var pageBase: String = ""

    init {
        web.settings.javaScriptEnabled = true // our measuring script only; book markup never carries scripts here
        web.settings.allowFileAccess = false
        web.settings.allowContentAccess = false
        web.settings.blockNetworkLoads = true
        web.isVerticalScrollBarEnabled = false
        web.isHorizontalScrollBarEnabled = false
        web.setBackgroundColor(android.graphics.Color.TRANSPARENT) // the page's paper shows through
        web.settings.useWideViewPort = false
        web.settings.loadWithOverviewMode = false
        web.settings.textZoom = TEXT_ZOOM
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse =
                serve(request)

            override fun onPageFinished(view: WebView, url: String) {
                loaded?.complete(Unit)
            }
        }
    }

    private fun serve(request: WebResourceRequest): WebResourceResponse {
        val url = request.url
        val path = url.path.orEmpty().removePrefix("/")
        val bytes = when {
            url.host != HOST -> null
            path.endsWith(ISLANDS) -> page.toByteArray()
            else -> archive.read(path)
        }
            ?: return WebResourceResponse(
                "text/plain",
                "utf-8",
                FORBIDDEN,
                "Blocked",
                emptyMap(),
                ByteArrayInputStream(ByteArray(0))
            )
        val mime = if (path.endsWith(ISLANDS)) "text/html" else mimeOf(path)
        return WebResourceResponse(mime, "utf-8", ByteArrayInputStream(bytes))
    }

    /**
     * Lays [work]'s islands out at its text size in a column of its width, in the page of its chapter (the base for
     * relative links, such as images inside SVG or tables). Returns their boxes in screen pixels.
     */
    suspend fun measure(work: IslandWork): Map<Int, IslandBox> {
        val items = work.items
        if (items.isEmpty()) return emptyMap()
        val dpr = web.resources.displayMetrics.density
        val chapterPath = work.chapterPath
        page = islandsPage(items, work.bodyPx / dpr, (work.widthPx / dpr).toInt(), work.ink)
        pageBase = chapterPath.substringBeforeLast('/', "")
        val done = CompletableDeferred<Unit>()
        loaded = done
        web.loadUrl("https://$HOST/${if (pageBase.isEmpty()) "" else "$pageBase/"}$ISLANDS")
        withTimeout(TIMEOUT_MS) { done.await() }
        val json = js(MEASURE)
        val rows = JSONArray(JSONObject("{\"v\":$json}").getString("v"))
        return (0 until rows.length()).associate { i ->
            val a = rows.getJSONObject(i)
            val id = a.getInt("id")
            id to IslandBox(
                id = id,
                w = (a.getDouble("w") * dpr).toInt() + 1,
                h = (a.getDouble("h") * dpr).toInt() + 1,
                baseline = (a.getDouble("b") * dpr).toInt(),
                top = (a.getDouble("t") * dpr).toInt(),
                left = (a.getDouble("l") * dpr).toInt()
            )
        }
    }

    /** Draws one measured island (from the page [measure] loaded last) into a bitmap of its size. */
    fun render(box: IslandBox): Bitmap {
        val bmp = Bitmap.createBitmap(box.w.coerceAtLeast(1), box.h.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.translate(-box.left.toFloat(), -box.top.toFloat())
        web.draw(c)
        return bmp
    }

    private suspend fun js(script: String): String = withTimeout(TIMEOUT_MS) {
        suspendCancellableCoroutine { cont -> web.evaluateJavascript(script) { cont.resume(it ?: "null") } }
    }

    private fun islandsPage(items: List<IslandItem>, bodyCss: Float, widthCss: Int, ink: Int): String = buildString {
        val color = "#%06X".format(ink and RGB)
        // Scale 1: one CSS pixel per dp, whatever the view's size (no shrinking a wide page to fit).
        append("<!DOCTYPE html><html><head><meta charset='utf-8'/>")
        append("<meta name='viewport' content='width=device-width,initial-scale=1,minimum-scale=1,maximum-scale=1'/>")
        append("<style>")
        append("html,body{margin:0;padding:0;background:transparent;}")
        append("body{font-family:serif;font-size:${bodyCss}px;width:${widthCss}px;color:$color;}")
        // Each island alone on its own band, far from the next, so no capture can reach a neighbour.
        append(".b{display:block;margin:0 0 ${GAP_CSS}px 0;}.i{display:inline-block;max-width:${widthCss}px;}")
        append("table{border-collapse:collapse;}th,td{border:1px solid $color;padding:0.2em 0.5em;}")
        append("svg,img{max-width:100%;height:auto;}math[display=block]{display:block;}")
        append(FENCES)
        append("</style></head><body>")
        items.forEach {
            append("<div class='b'><span class='i' id='i${it.id}'>").append(it.xml).append("</span></div>")
        }
        append("<script>").append(MFENCED).append("</script></body></html>")
    }

    companion object {
        const val HOST = "book.local"
        private const val ISLANDS = "__flowfolio_islands.html"
        private const val FORBIDDEN = 403
        private const val TIMEOUT_MS = 20_000L
        private const val GAP_CSS = 200
        private const val RGB = 0xFFFFFF

        /** The reader sets text sizes itself; the system's font scale must not change them again. */
        private const val TEXT_ZOOM = 100

        /**
         * MathML's `mfenced` (brackets, parentheses and separators around its children) is gone from MathML Core, so
         * Chrome draws matrices without their brackets. It becomes the `mrow` of fences and separators it stands for;
         * around a table the fences are drawn with [FENCES] instead, as they could not stretch.
         */
        private const val MFENCED = """(function(){var ns='http://www.w3.org/1998/Math/MathML';
            var K={'[':'sq',']':'sq','(':'rd',')':'rd','{':'rd','}':'rd','|':'bar','\u2016':'dbl'};
            function mo(t,a){var m=document.createElementNS(ns,'mo');m.textContent=t;m.setAttribute(a,'true');return m;}
            function at(f,n,d){var v=f.getAttribute(n);return v===null?d:v.trim();}
            function wrap(k,c){var r=document.createElementNS(ns,'mrow');r.setAttribute('class',c);r.appendChild(k);
            return r;}
            document.querySelectorAll('mfenced').forEach(function(f){var row=document.createElementNS(ns,'mrow');
            var o=at(f,'open','('),c=at(f,'close',')'),s=at(f,'separators',',').replace(/\s/g,'');
            var tall=f.querySelector('mtable')!==null&&(!o||K[o])&&(!c||K[c]);
            if(o&&!tall)row.appendChild(mo(o,'fence'));Array.from(f.children).forEach(function(k,i){
            if(i>0&&s)row.appendChild(mo(s[Math.min(i-1,s.length-1)],'separator'));row.appendChild(k);});
            if(c&&!tall)row.appendChild(mo(c,'fence'));var out=row;
            if(tall){if(c)out=wrap(out,'ffr-'+K[c]);if(o)out=wrap(out,'ffl-'+K[o]);}
            f.replaceWith(out);});})();"""

        /** Brackets drawn as borders around a matrix: phones' fonts can't stretch brackets (no OpenType MATH table). */
        private const val FENCES = """
            .ffl-sq,.ffl-rd,.ffl-bar,.ffl-dbl{border-left:.07em solid;padding-left:.25em;margin-left:.1em}
            .ffr-sq,.ffr-rd,.ffr-bar,.ffr-dbl{border-right:.07em solid;padding-right:.25em;margin-right:.1em}
            .ffl-sq{background:linear-gradient(currentColor,currentColor) left top/.3em .07em no-repeat,
                linear-gradient(currentColor,currentColor) left bottom/.3em .07em no-repeat}
            .ffr-sq{background:linear-gradient(currentColor,currentColor) right top/.3em .07em no-repeat,
                linear-gradient(currentColor,currentColor) right bottom/.3em .07em no-repeat}
            .ffl-rd{border-radius:.7em 0 0 .7em/50% 0 0 50%}.ffr-rd{border-radius:0 .7em .7em 0/0 50% 50% 0}
            .ffl-dbl{border-left-style:double;border-left-width:.25em}
            .ffr-dbl{border-right-style:double;border-right-width:.25em}"""

        /** Size, baseline, and position on the page (CSS pixels) of every island, measured with a zero-size probe. */
        private const val MEASURE = """(function(){var out=[];document.querySelectorAll('.i').forEach(function(e){
            var r=e.getBoundingClientRect();var p=document.createElement('span');
            p.style.cssText='display:inline-block;width:0;height:0;vertical-align:baseline';e.appendChild(p);
            var b=p.getBoundingClientRect().top-r.top;e.removeChild(p);
            out.push({id:parseInt(e.id.substring(1)),w:r.width,h:r.height,b:b,t:r.top+window.scrollY,
            l:r.left+window.scrollX});});
            return JSON.stringify(out);})()"""

        /** True when [bmp] is fully transparent: the WebView had nothing drawn yet (or the island is empty). */
        fun isBlank(bmp: Bitmap): Boolean {
            val row = IntArray(bmp.width)
            return (0 until bmp.height).none { y ->
                bmp.getPixels(row, 0, bmp.width, 0, y, bmp.width, 1)
                row.any { it != 0 }
            }
        }

        /** Lets [render] draw parts of the page outside the view. Must run before any WebView is created. */
        fun enableWholeDocumentDraw() = WebView.enableSlowWholeDocumentDraw()

        fun mimeOf(path: String): String = when (path.substringAfterLast('.').lowercase()) {
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
