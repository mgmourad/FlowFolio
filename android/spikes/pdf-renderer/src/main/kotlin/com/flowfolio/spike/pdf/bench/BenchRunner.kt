package com.flowfolio.spike.pdf.bench

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import android.util.LruCache
import com.flowfolio.spike.pdf.engine.AndroidxPdfEngine
import com.flowfolio.spike.pdf.engine.FrameworkEngine
import com.flowfolio.spike.pdf.engine.PdfDoc
import com.flowfolio.spike.pdf.engine.PdfEngine
import com.flowfolio.spike.pdf.engine.PdfiumDoc
import com.flowfolio.spike.pdf.engine.PdfiumEngine
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random
import org.json.JSONArray
import org.json.JSONObject

const val TAG = "FFSpike"

/** Runs the measurement scenarios and writes one JSON file per run under files/results/. */
class BenchRunner(private val context: Context, private val log: (String) -> Unit) {
    private val resultsDir = File(context.getExternalFilesDir(null), "results").apply { mkdirs() }

    fun engine(name: String): PdfEngine = when (name) {
        "framework" -> FrameworkEngine()
        "androidx" -> AndroidxPdfEngine(context)
        "pdfium" -> PdfiumEngine()
        else -> error("unknown engine $name")
    }

    /** Render target: fit page width to [screenWidthPx], keeping the page's aspect ratio. */
    private suspend fun target(doc: PdfDoc, index: Int, screenWidthPx: Int): Pair<Int, Int> {
        val size = doc.pageSizePt(index)
        return screenWidthPx to (screenWidthPx * size.height / size.width).roundToInt()
    }

    suspend fun runEngine(engineName: String, book: File, screenWidthPx: Int): File {
        val engine = engine(engineName)
        val out = JSONObject()
            .put("engine", engineName)
            .put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
            .put("sdk", Build.VERSION.SDK_INT)
            .put("book", book.name)
            .put("bookBytes", book.length())
            .put("renderWidthPx", screenWidthPx)

        // S1: open time (cold open of a fresh document object, 5 repetitions)
        val openMs = ArrayList<Double>()
        repeat(5) {
            val t0 = SystemClock.elapsedRealtimeNanos()
            engine.open(book).use { }
            openMs += ms(t0)
        }
        out.put("openMs", stats(openMs))
        log("$engineName open p50=${stats(openMs).getDouble("p50")} ms")

        engine.open(book).use { doc ->
            out.put("pageCount", doc.pageCount)
            out.put("features", JSONArray(doc.features.map { it.name }))

            // S2: sequential render, pages 0..59
            out.put(
                "sequentialRenderMs",
                stats(renderTimes(doc, (0 until minOf(60, doc.pageCount)).toList(), screenWidthPx))
            )
            log("$engineName sequential done")

            // S3: random access, 60 pages, fixed seed
            val rnd = Random(42)
            val randomPages = List(60) { rnd.nextInt(doc.pageCount) }
            out.put("randomRenderMs", stats(renderTimes(doc, randomPages, screenWidthPx)))
            log("$engineName random done")

            // S4: memory over a 300-page flip with a 7-page LRU of bitmaps (current +-3)
            out.put("memory", memoryFlip(doc, screenWidthPx))
            log("$engineName memory done")

            // S5: features
            val outline = doc.outline()
            out.put(
                "outline",
                if (outline == null) {
                    JSONObject.NULL
                } else {
                    JSONObject()
                        .put("entries", outline.size)
                        .put("maxDepth", outline.maxOfOrNull { it.depth } ?: -1)
                        .put(
                            "first",
                            JSONArray(
                                outline.take(8).map {
                                    "${"  ".repeat(it.depth)}${it.title} -> p${it.pageIndex + 1}"
                                }
                            )
                        )
                }
            )
            val labels = JSONObject()
            for (p in listOf(0, 1, 10, 20, 30, 100, 500).filter { it < doc.pageCount }) {
                labels.put(
                    "p${p + 1}",
                    doc.pageLabel(p) ?: JSONObject.NULL
                )
            }
            out.put("pageLabels", labels)

            // S6: Stage B extraction probe (PDFium only), pages 100..149
            if (doc is PdfiumDoc) out.put("extraction", extractionProbe(doc))
        }

        val file = File(resultsDir, "$engineName.json")
        file.writeText(out.toString(2))
        return file
    }

    private suspend fun renderTimes(doc: PdfDoc, pages: List<Int>, width: Int): List<Double> = pages.map { p ->
        val (w, h) = target(doc, p, width)
        val t0 = SystemClock.elapsedRealtimeNanos()
        val bmp = doc.render(p, w, h)
        val elapsed = ms(t0)
        bmp.recycle()
        elapsed
    }

    private suspend fun memoryFlip(doc: PdfDoc, width: Int): JSONObject {
        val cache = object : LruCache<Int, Bitmap>(7) {
            override fun entryRemoved(evicted: Boolean, key: Int, oldValue: Bitmap, newValue: Bitmap?) =
                oldValue.recycle()
        }
        val start = minOf(100, doc.pageCount / 4)
        val end = minOf(start + 300, doc.pageCount)
        val samples = JSONArray()
        var peakPss = 0
        var peakNative = 0L
        val baseline = Debug.getPss()
        for (p in start until end) {
            val (w, h) = target(doc, p, width)
            cache.put(p, doc.render(p, w, h))
            if ((p - start) % 25 == 0) {
                val pss = Debug.getPss().toInt()
                val native = Debug.getNativeHeapAllocatedSize()
                peakPss = maxOf(peakPss, pss)
                peakNative = maxOf(peakNative, native)
                samples.put(JSONObject().put("page", p + 1).put("pssKb", pss).put("nativeHeapKb", native / 1024))
            }
        }
        cache.evictAll()
        return JSONObject()
            .put("pages", end - start)
            .put("baselinePssKb", baseline)
            .put("peakPssKb", peakPss)
            .put("peakNativeHeapKb", peakNative / 1024)
            .put("samples", samples)
    }

    private suspend fun extractionProbe(doc: PdfiumDoc): JSONObject {
        val start = minOf(100, doc.pageCount - 1)
        val end = minOf(start + 50, doc.pageCount)
        val perPage = ArrayList<Double>()
        var chars = 0L
        var runs = 0L
        var lines = 0L
        val fonts = HashSet<String>()
        val objects = IntArray(6)
        var sample: List<GlyphRun> = emptyList()
        for (p in start until end) {
            val t0 = SystemClock.elapsedRealtimeNanos()
            val raw = doc.extract(p) ?: continue
            val grouped = groupRuns(raw)
            perPage += ms(t0)
            chars += raw.charCount
            runs += grouped.size
            lines += countLines(grouped)
            fonts += raw.fontNames
            for (i in 0 until 6) objects[i] += raw.objectStats[i]
            if (p == start + 10) sample = grouped.take(15)
        }
        return JSONObject()
            .put("pages", "${start + 1}..$end")
            .put("msPerPage", stats(perPage))
            .put("chars", chars)
            .put("glyphRuns", runs)
            .put("lines", lines)
            .put("distinctFonts", JSONArray(fonts.sorted()))
            .put(
                "objects",
                JSONObject().put("text", objects[0]).put("path", objects[1]).put("image", objects[2])
                    .put("shading", objects[3]).put("form", objects[4]).put("pathSegments", objects[5])
            )
            .put(
                "sampleRuns",
                JSONArray(
                    sample.map {
                        "${it.fontId}@${"%.1f".format(
                            it.size
                        )} [${it.left.roundToInt()},${it.bottom.roundToInt()}] ${it.text.take(60)}"
                    }
                )
            )
    }

    /** Renders the same pages with every engine, saves PNGs, and compares them pixel by pixel. */
    suspend fun runQuality(book: File, screenWidthPx: Int): File {
        val names = listOf("pdfium", "framework", "androidx")
        val docs = names.associateWith { engine(it).open(book) }
        val pngDir = File(resultsDir, "quality").apply { mkdirs() }
        val out = JSONObject()
        try {
            val count = docs.getValue("pdfium").pageCount
            for (p in listOf(0, 29, 110, 119, 399, 799).filter { it < count }) {
                val (w, h) = target(docs.getValue("pdfium"), p, screenWidthPx)
                val renders = names.associateWith { docs.getValue(it).render(p, w, h) }
                renders.forEach { (n, bmp) ->
                    File(pngDir, "p${p + 1}-$n.png").outputStream().use {
                        bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                }
                val ref = renders.getValue("pdfium")
                out.put(
                    "p${p + 1}",
                    JSONObject()
                        .put("framework_vs_pdfium", diff(ref, renders.getValue("framework")))
                        .put("androidx_vs_pdfium", diff(ref, renders.getValue("androidx")))
                )
                // 3x render, centre crop: shows glyph sharpness when the reader zooms in
                names.forEach { n ->
                    val big = docs.getValue(n).render(p, w * 3, h * 3)
                    val crop = Bitmap.createBitmap(big, w, h, w, h / 2)
                    File(pngDir, "p${p + 1}-$n-zoom3x.png").outputStream().use {
                        crop.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    crop.recycle()
                    big.recycle()
                }
                renders.values.forEach { it.recycle() }
                log("quality p${p + 1} done")
            }
        } finally {
            docs.values.forEach { it.close() }
        }
        val file = File(resultsDir, "quality.json")
        file.writeText(out.toString(2))
        return file
    }

    /** Mean absolute channel difference (0-255) and share of pixels differing by more than 32 in any channel. */
    private fun diff(a: Bitmap, b: Bitmap): JSONObject {
        val w = minOf(a.width, b.width)
        val h = minOf(a.height, b.height)
        val pa = IntArray(w * h).also { a.getPixels(it, 0, w, 0, 0, w, h) }
        val pb = IntArray(w * h).also { b.getPixels(it, 0, w, 0, 0, w, h) }
        var sum = 0L
        var strong = 0
        for (i in pa.indices) {
            val dr = abs(Color.red(pa[i]) - Color.red(pb[i]))
            val dg = abs(Color.green(pa[i]) - Color.green(pb[i]))
            val db = abs(Color.blue(pa[i]) - Color.blue(pb[i]))
            sum += dr + dg + db
            if (maxOf(dr, dg, db) > 32) strong++
        }
        return JSONObject()
            .put("meanAbsDiff", "%.3f".format(sum.toDouble() / (pa.size * 3)))
            .put("pixelsOver32Pct", "%.3f".format(strong * 100.0 / pa.size))
    }

    private fun ms(startNanos: Long) = (SystemClock.elapsedRealtimeNanos() - startNanos) / 1_000_000.0

    private fun stats(values: List<Double>): JSONObject {
        if (values.isEmpty()) return JSONObject().put("n", 0)
        val sorted = values.sorted()
        fun pct(q: Double) = sorted[((sorted.size - 1) * q).roundToInt()]
        return JSONObject()
            .put("n", sorted.size)
            .put("p50", "%.2f".format(pct(0.5)).toDouble())
            .put("p95", "%.2f".format(pct(0.95)).toDouble())
            .put("max", "%.2f".format(sorted.last()).toDouble())
            .put("mean", "%.2f".format(sorted.average()).toDouble())
    }

    companion object {
        fun done(file: File) = Log.i(TAG, "SPIKE_DONE ${file.absolutePath}")
    }
}
