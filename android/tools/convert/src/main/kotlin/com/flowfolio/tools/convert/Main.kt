package com.flowfolio.tools.convert

import com.flowfolio.core.conversion.Converter
import com.flowfolio.core.conversion.epub.SourceInfo
import com.flowfolio.core.conversion.ir.BlockType
import com.flowfolio.core.conversion.pdf.Box
import com.flowfolio.core.conversion.pdf.Crop
import com.flowfolio.core.conversion.pdf.RawPage
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.serialization.json.Json

/**
 * The converter on the desktop JVM (development only; the product converts on the phone):
 * - `probe <file.pdf> [page]` prints what PDFium reads from a page;
 * - `convert <file.pdf> <outDir>` writes `book.epub` and `flowfolio.json` to [outDir], as the app will;
 * - `page <file.pdf> <index>` prints a page's lines and blocks after layout;
 * - `chars <file.pdf> <index> [count]` prints PDFium's characters in order, with their boxes (right-to-left text);
 * - `render <file.pdf> <page,page,...> <outDir>` draws whole pages (1-based) as PNGs, for checking hand labels.
 */
fun main(args: Array<String>) {
    when (args.firstOrNull()) {
        "probe" -> probe(File(args[1]), args.getOrNull(2)?.toInt() ?: 0)

        "convert" -> convert(File(args[1]), File(args[2]))

        "page" -> page(File(args[1]), args[2].toInt())

        "chars" -> chars(File(args[1]), args[2].toInt(), args.getOrNull(OUT_ARG)?.toInt() ?: PROBE_CHARS)

        "unmatched" -> unmatched(File(args[1]))

        "render" -> render(File(args[1]), args[2].split(",").map { it.toInt() - 1 }, File(args[OUT_ARG]))

        else -> println(
            "usage: probe <file.pdf> [page] | convert <file.pdf> <outDir> | page <file.pdf> <index> | " +
                "unmatched <file.pdf> | render <file.pdf> <page,page> <outDir>"
        )
    }
}

/** A finished conversion: the converter's [result], the sidecar's [json], the [epub] written and how long it took. */
class Converted(val result: Converter.Result, val json: String, val epub: File, val seconds: Float)

/** Converts [pdf] into [outDir]/book.epub and flowfolio.json; returns the sidecar's JSON. */
fun convert(pdf: File, outDir: File): String = convertBook(pdf, outDir).json

fun convertBook(pdf: File, outDir: File): Converted {
    outDir.mkdirs()
    val source = SourceInfo(pdf.name, sha256(pdf), 0, null)
    val started = System.nanoTime()
    val epub = File(outDir, "book.epub")
    val result = JnaPdfSource.open(pdf).use { src ->
        epub.outputStream().buffered().use { out ->
            Converter().convert(
                src,
                source.copy(pageCount = src.pageCount, title = src.metadata("Title")),
                out,
                Instant.now().truncatedTo(ChronoUnit.SECONDS).toString()
            )
        }
    }
    val seconds = (System.nanoTime() - started) / NANOS_PER_SECOND
    val json = JSON.encodeToString(result.sidecar)
    File(outDir, "flowfolio.json").writeText(json)
    val s = result.sidecar.health.summary
    println(
        "${pdf.name}: ${s.pages} pages, ${s.chapters} chapters (${s.chaptersFrom}), " +
            "${s.fallbackPages} fallback pages, " +
            "${s.fallbackBlocks} image blocks, %.1f s".format(seconds)
    )
    return Converted(result, json, epub, seconds)
}

fun sha256(file: File): String {
    val md = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buf = ByteArray(READ_BUFFER)
        var n = input.read(buf)
        while (n >= 0) {
            md.update(buf, 0, n)
            n = input.read(buf)
        }
    }
    return md.digest().joinToString("") { "%02x".format(it) }
}

private val JSON = Json { prettyPrint = true }

/** Page [index]'s lines and blocks after layout, for debugging the stages. */
private fun page(pdf: File, index: Int) {
    val doc = JnaPdfSource.open(pdf).use { Converter().analyse(it, SourceInfo(pdf.name, "0", it.pageCount, null)) }
    val page = doc.pages[index]
    println(
        "page ${index + 1} label=${page.label} kind=${page.kind} coverage=${page.coverage} drawings=${page.drawings}"
    )
    doc.styles.take(SHOWN_STYLES).forEach { println("  style ${it.role} L${it.level} ${it.chars} ${it.key}") }
    doc.outline.filter { it.page in index - 1..index + 1 }.forEach {
        println("  outline d${it.depth} p${it.page + 1}: ${it.title}")
    }
    page.lines.forEach { l ->
        val b = l.box
        println(
            "  line [%.0f,%.0f-%.0f,%.0f] %s%s %s: %s".format(
                b.left,
                b.top,
                b.right,
                b.bottom,
                l.style,
                if (l.artifact) " ARTIFACT" else "",
                l.inlines.size,
                l.text
            )
        )
    }
    page.blocks.forEach {
        val b = it.box
        val box = "[%.0f,%.0f-%.0f,%.0f]".format(b.left, b.top, b.right, b.bottom)
        println("  ${it.id} ${it.type} L${it.level} inl=${it.inlines.size} $box: ${it.text.take(SHOWN_TEXT)}")
    }
}

/** Outline entries with no heading of their title on their page (Metrics.outlineMatched), and the page's headings. */
private fun unmatched(pdf: File) {
    val doc = JnaPdfSource.open(pdf).use { Converter().analyse(it, SourceInfo(pdf.name, "0", it.pageCount, null)) }
    doc.outline.filter { it.page >= 0 && it.title.isNotBlank() }.forEach { e ->
        val headings = doc.pages.getOrNull(e.page)?.blocks.orEmpty().filter { it.type == BlockType.HEADING }
        if (headings.none { com.flowfolio.core.conversion.stage.Linking.matches(it.text, e.title) }) {
            println("p${e.page + 1} d${e.depth} '${e.title}' headings: ${headings.map { it.text.take(SHOWN_HEADING) }}")
        }
    }
}

/** Pages [indices] (0-based) drawn whole as PNGs into [outDir], named by their 1-based page. */
private fun render(pdf: File, indices: List<Int>, outDir: File) {
    outDir.mkdirs()
    JnaPdfSource.open(pdf).use { src ->
        for (index in indices) {
            val page = src.page(index)
            val crop = Crop(Box(0f, 0f, page.width, page.height), photo = true)
            val png = src.renderImages(index, listOf(crop), RENDER_SCALE).single().bytes
            File(outDir, "%04d.png".format(index + 1)).writeBytes(png)
        }
    }
}

private fun probe(file: File, index: Int) {
    JnaPdfSource.open(file).use { pdf ->
        val started = System.nanoTime()
        val page = pdf.page(index)
        val ms = (System.nanoTime() - started) / NANOS_PER_MILLI
        println("${file.name}: ${pdf.pageCount} pages; title=${pdf.metadata("Title")}; label=${pdf.pageLabel(index)}")
        println(
            "page $index: ${page.width} x ${page.height} pt, ${page.length} chars, ${page.fonts.size} fonts, " +
                "${page.images.size} images, ${page.paths.size} paths, read in $ms ms"
        )
        page.fonts.forEach { println("  font ${it.name} flags=${it.flags}") }
        page.paths.forEach { println("  path ${it.bounds} filled=${it.filled}") }
        println(page.text.take(PROBE_TEXT))
        println("outline: " + pdf.outline().take(PROBE_OUTLINE).joinToString { "${it.depth}:${it.title}@${it.page}" })
    }
}

/** Each character PDFium reads, in its order: code point, box, and whether PDFium made it up (a space, a break). */
private fun chars(file: File, index: Int, count: Int) {
    JnaPdfSource.open(file).use { pdf ->
        val page = pdf.page(index)
        for (i in 0 until minOf(count, page.length)) println(charLine(page, i))
    }
}

private fun charLine(page: RawPage, i: Int): String {
    val c = page.text[i]
    val where = if (page.hasBox(i)) {
        page.box(i).let { "%.1f,%.1f-%.1f,%.1f".format(it.left, it.top, it.right, it.bottom) }
    } else {
        "-"
    }
    val shown = if (c.isWhitespace()) "\\s" else c.toString()
    return "%4d U+%04X %s %s%s".format(i, c.code, shown, where, if (page.generated[i]) " gen" else "")
}

private const val PROBE_TEXT = 400
private const val PROBE_CHARS = 80
private const val RENDER_SCALE = 1.1f
private const val OUT_ARG = 3
private const val NANOS_PER_SECOND = 1e9f
private const val NANOS_PER_MILLI = 1_000_000
private const val READ_BUFFER = 1 shl 16
private const val SHOWN_TEXT = 120
private const val SHOWN_HEADING = 40
private const val PROBE_OUTLINE = 8
private const val SHOWN_STYLES = 12
