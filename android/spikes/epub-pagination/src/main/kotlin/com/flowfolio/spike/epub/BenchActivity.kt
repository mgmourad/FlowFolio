package com.flowfolio.spike.epub

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.util.TypedValue
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.ImageView
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Runs one spike bench (`--es engine native|hybrid|web|robust`) over every book in files/books and writes
 * files/results/<engine>.json. `run-spike.ps1` drives it and waits for "SPIKE_DONE" in logcat.
 */
class BenchActivity : Activity() {
    private val scope = MainScope()
    private lateinit var web: WebView
    private lateinit var preview: ImageView
    private var pageW = 0
    private var pageH = 0
    private var margin = 0
    private var bodyPx = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Runs take minutes: a WebView draws nothing while the screen is off, so keep it on while this is in front.
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val dm = resources.displayMetrics
        // One page of a spread on a wide screen, the whole screen otherwise.
        pageW = if (dm.widthPixels / dm.density >= WIDE_DP) dm.widthPixels / 2 else dm.widthPixels
        pageH = dm.heightPixels
        margin = (MARGIN_DP * dm.density).toInt()
        bodyPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, BODY_SP, dm)
        web = WebView(this)
        preview = ImageView(this)
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(Color.WHITE)
            addView(web, FrameLayout.LayoutParams(pageW, pageH))
            addView(preview, FrameLayout.LayoutParams(pageW, pageH).apply { leftMargin = pageW })
        })
        val engine = intent.getStringExtra("engine") ?: "native"
        scope.launch {
            val result = runCatching { run(engine) }.getOrElse { JSONObject().put("crash", it.stackTraceToString()) }
            File(getExternalFilesDir("results"), "$engine.json").writeText(result.toString(2))
            Log.i(TAG, "SPIKE_DONE $engine")
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private val books: List<File>
        get() = getExternalFilesDir("books")!!.listFiles { f -> f.name.endsWith(".epub") }!!.sortedBy { it.name }

    private suspend fun run(engine: String): JSONObject = JSONObject()
        .put("engine", engine)
        .put("page", "${pageW}x$pageH")
        .put("bodyPx", bodyPx)
        .put("results", JSONArray().apply {
            when (engine) {
                "init" -> Unit
                "robust" -> books.forEach { put(robust(it)) }
                "native" -> readable().forEach { put(native(it, hybrid = false)) }
                "hybrid" -> readable().forEach { put(native(it, hybrid = true)) }
                "web" -> readable().forEach { put(web(it)) }
            }
        })

    private fun readable() = books.filterNot { it.name.startsWith("hostile") }

    // ---- Robustness: our parser on every book, hostile ones included -------------------------------------------

    private suspend fun robust(file: File): JSONObject {
        val out = JSONObject().put("book", file.name)
        val t0 = SystemClock.elapsedRealtime()
        val book = try {
            withContext(Dispatchers.IO) { EpubBook.open(file) }
        } catch (e: IllegalArgumentException) {
            return out.put("refused", e.message).put("ms", SystemClock.elapsedRealtime() - t0)
        } catch (t: Throwable) {
            return out.put("crash", t.toString())
        }
        book.use {
            var missing = 0
            var blocks = 0
            withContext(Dispatchers.Default) {
                it.spine.forEach { path ->
                    val bytes = it.read(path)
                    if (bytes == null) missing++ else blocks += XhtmlBlocks.parse(bytes, path) { 0 }.blocks.size
                }
            }
            out.put("title", it.title).put("spine", it.spine.size).put("toc", it.toc.size).put("pageList", it.pageList.size)
                .put("missing", missing).put("blocks", blocks).put("rtl", it.rightToLeft).put("fixedLayout", it.fixedLayout)
            if (file.name.contains("script")) {
                val w = WebEngine(web, it)
                w.paginate(it.spine.first(), FONT_SIZES[1], margin)
                out.put("webBlockedRequests", w.blockedRequests)
            }
        }
        return out.put("ms", SystemClock.elapsedRealtime() - t0)
    }

    // ---- B (native) and C (hybrid) ------------------------------------------------------------------------------

    private suspend fun native(file: File, hybrid: Boolean): JSONObject {
        val out = JSONObject().put("book", file.name)
        val book = withContext(Dispatchers.IO) { EpubBook.open(file) }
        if (book.fixedLayout) return out.put("skipped", "fixed layout").also { book.close() }
        book.use { b ->
            var islandId = 0
            val t0 = SystemClock.elapsedRealtime()
            val chapters = withContext(Dispatchers.Default) {
                b.spine.mapNotNull { path -> b.read(path)?.let { path to XhtmlBlocks.parse(it, path) { islandId++ } } }
            }
            out.put("parseMs", SystemClock.elapsedRealtime() - t0).put("chapters", chapters.size)
                .put("islands", chapters.sumOf { (_, c) -> c.blocks.count { it is Block.Island } + c.inlineIslands.size })
            val sizes = if (hybrid) listOf(FONT_SIZES[1], FONT_SIZES[3]) else FONT_SIZES
            val runs = JSONArray()
            for (pct in sizes) {
                val px = bodyPx * pct / 100f
                val islandBoxes = HashMap<Int, WebEngine.IslandBox>()
                var islandMs = 0L
                if (hybrid) {
                    val w = WebEngine(web, b)
                    val css = b.spine.firstOrNull()?.let { "https://${WebEngine.HOST}/${it.substringBeforeLast('/')}/book.css" }
                    val ti = SystemClock.elapsedRealtime()
                    chapters.forEach { (_, c) ->
                        val islands = c.blocks.filterIsInstance<Block.Island>() + c.inlineIslands
                        w.measureIslands(islands, px, pageW - 2 * margin, css).forEach { islandBoxes[it.id] = it }
                    }
                    islandMs = SystemClock.elapsedRealtime() - ti
                    chapters.forEach { (_, c) -> c.inlineIslands.forEach { isl ->
                        islandBoxes[isl.id]?.let { box -> spanOf(c, isl)?.apply { width = box.w; ascent = box.baseline; descent = box.h - box.baseline } }
                    } }
                }
                val paginator = NativePaginator(pageW - 2 * margin, pageH - 2 * margin, px, Locale.ENGLISH) { block ->
                    when (block) {
                        is Block.Island -> islandBoxes[block.id]?.let { it.w to it.h }
                        is Block.Image -> b.read(block.path)?.let(NativePaginator::imageSize)
                        else -> null
                    }
                }
                val tp = SystemClock.elapsedRealtime()
                var firstChapterMs = 0L
                var pages = 0
                val errors = JSONArray()
                val paged = withContext(Dispatchers.Default) {
                    chapters.mapIndexed { i, (path, c) ->
                        val tc = SystemClock.elapsedRealtime()
                        val p = paginator.paginate(c)
                        if (i == 0) firstChapterMs = SystemClock.elapsedRealtime() - tc
                        pages += p.size
                        paginator.check(c, p)?.let { errors.put("$path: $it") }
                        Triple(path, c, p)
                    }
                }
                val paginateMs = SystemClock.elapsedRealtime() - tp
                // Draw a few pages, as the reader would for its page cache.
                val bmp = Bitmap.createBitmap(pageW, pageH, Bitmap.Config.ARGB_8888)
                val drawTimes = ArrayList<Long>()
                val sample = paged.flatMap { (_, c, p) -> p.map { c to it } }.let { all -> all.drop(all.size / 3).take(DRAW_SAMPLES) }
                var islandRenderMs = 0L
                for ((c, page) in sample) {
                    if (hybrid) islandRenderMs += renderIslandsFor(b, c, page, islandBoxes, px)
                    val td = SystemClock.elapsedRealtime()
                    bmp.eraseColor(Color.WHITE)
                    paginator.draw(Canvas(bmp), page, c, margin.toFloat(), margin.toFloat()) { bl -> rendered[(bl as? Block.Island)?.id] ?: imageOf(b, bl) }
                    drawTimes += SystemClock.elapsedRealtime() - td
                }
                if (pct == FONT_SIZES[1]) savePreview(bmp, "${file.nameWithoutExtension}-${if (hybrid) "hybrid" else "native"}")
                runs.put(JSONObject().put("fontPercent", pct).put("pages", pages).put("paginateMs", paginateMs)
                    .put("firstChapterMs", firstChapterMs).put("islandMeasureMs", islandMs).put("islandRenderMs", islandRenderMs)
                    .put("drawMsP50", drawTimes.sorted().getOrNull(drawTimes.size / 2)).put("drawMsMax", drawTimes.maxOrNull())
                    .put("errors", errors))
                rendered.clear()
            }
            out.put("runs", runs)
        }
        return out
    }

    private val rendered = HashMap<Int?, Bitmap>()

    private fun spanOf(c: Chapter, island: Block.Island): IslandSpan? = c.blocks.asSequence().filterIsInstance<Block.Text>()
        .flatMap { it.text.getSpans(0, it.text.length, IslandSpan::class.java).asSequence() }.firstOrNull { it.island.id == island.id }

    /** C: renders the islands a page shows (block and inline) through the WebView; returns the time taken. */
    private suspend fun renderIslandsFor(b: EpubBook, c: Chapter, page: Page, boxes: Map<Int, WebEngine.IslandBox>, px: Float): Long {
        val needed = page.slices.flatMap { s ->
            when (val block = c.blocks[s.block]) {
                is Block.Island -> listOf(block)
                is Block.Text -> block.text.getSpans(0, block.text.length, IslandSpan::class.java).map { it.island }
                else -> emptyList()
            }
        }.filter { it.id !in rendered }
        if (needed.isEmpty()) return 0
        val t0 = SystemClock.elapsedRealtime()
        val w = WebEngine(web, b)
        w.measureIslands(needed, px, pageW - 2 * margin, null)
        needed.forEach { isl ->
            boxes[isl.id]?.let { box ->
                val bmp = w.renderIsland(box)
                rendered[isl.id] = bmp
                spanOf(c, isl)?.bitmap = bmp
            }
        }
        return SystemClock.elapsedRealtime() - t0
    }

    private val images = HashMap<String, Bitmap?>()

    private fun imageOf(b: EpubBook, block: Block): Bitmap? = (block as? Block.Image)?.let { img ->
        images.getOrPut(img.path) { b.read(img.path)?.let { BitmapFactory.decodeByteArray(it, 0, it.size) } }
    }

    // ---- A (web) --------------------------------------------------------------------------------------------------

    private suspend fun web(file: File): JSONObject {
        val out = JSONObject().put("book", file.name)
        val book = withContext(Dispatchers.IO) { EpubBook.open(file) }
        book.use { b ->
            val w = WebEngine(web, b)
            val runs = JSONArray()
            for (pct in listOf(FONT_SIZES[1], FONT_SIZES[3])) {
                var pages = 0
                var loadMs = 0L
                var paginateMs = 0L
                var rects = 0
                var bad = 0
                var firstChapterMs = 0L
                val snapTimes = ArrayList<Long>()
                val bmp = Bitmap.createBitmap(pageW, pageH, Bitmap.Config.ARGB_8888)
                b.spine.forEachIndexed { i, path ->
                    val c = w.paginate(path, pct, margin)
                    if (i == 0) firstChapterMs = c.loadMs + c.paginateMs
                    pages += c.pages
                    loadMs += c.loadMs
                    paginateMs += c.paginateMs
                    rects += c.lineRects
                    bad += c.badRects
                    if (i in SNAP_CHAPTERS && c.pages > 1) {
                        for (p in 0 until minOf(c.pages, SNAPS_PER_CHAPTER)) snapTimes += w.snapshot(p, bmp)
                        if (pct == FONT_SIZES[1] && i == SNAP_CHAPTERS.first()) savePreview(bmp, "${file.nameWithoutExtension}-web")
                    }
                }
                runs.put(JSONObject().put("fontPercent", pct).put("pages", pages).put("loadMs", loadMs).put("paginateMs", paginateMs)
                    .put("firstChapterMs", firstChapterMs).put("lineRects", rects).put("badRects", bad)
                    .put("snapMsP50", snapTimes.sorted().getOrNull(snapTimes.size / 2)).put("snapMsMax", snapTimes.maxOrNull()))
            }
            out.put("runs", runs).put("blockedRequests", w.blockedRequests)
        }
        return out
    }

    private fun savePreview(bmp: Bitmap, name: String) {
        preview.setImageBitmap(bmp.copy(Bitmap.Config.ARGB_8888, false))
        File(getExternalFilesDir("results"), "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
    }

    companion object {
        const val TAG = "EpubSpike"
        val FONT_SIZES = listOf(80, 100, 130, 170, 220)
        const val BODY_SP = 18f
        const val MARGIN_DP = 24f
        const val WIDE_DP = 600
        const val DRAW_SAMPLES = 30
        val SNAP_CHAPTERS = setOf(2, 3)
        const val SNAPS_PER_CHAPTER = 5
        const val PNG_QUALITY = 100
    }
}
