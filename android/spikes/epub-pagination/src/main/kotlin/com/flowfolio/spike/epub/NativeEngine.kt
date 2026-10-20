package com.flowfolio.spike.epub

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.text.LineBreaker
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ReplacementSpan
import android.text.style.StyleSpan
import android.text.style.SubscriptSpan
import android.text.style.SuperscriptSpan
import android.text.style.TypefaceSpan
import android.text.style.RelativeSizeSpan
import android.util.Xml
import java.util.Locale
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

/** One chapter as blocks: what the native engine lays out. */
sealed interface Block {
    /** Text with spans; [scale] is relative to the body size. */
    data class Text(
        val text: SpannableStringBuilder,
        val scale: Float,
        val heading: Boolean,
        val indentEm: Float,
        val spaceBeforeEm: Float,
        val spaceAfterEm: Float,
        val justify: Boolean
    ) : Block

    data class Image(val path: String) : Block

    /** Content the native engine does not lay out itself (MathML, SVG, tables): rendered by a WebView in C. */
    data class Island(val id: Int, val kind: String, val xml: String) : Block
}

/** A page break marker from the book (`epub:type="pagebreak"`): before [block], at [offset] in its text. */
data class PageMarker(val label: String, val block: Int, val offset: Int)

data class Chapter(val blocks: List<Block>, val markers: List<PageMarker>, val inlineIslands: List<Block.Island>)

/** XHTML to blocks. Tolerant: broken markup ends the chapter where it breaks instead of failing the book. */
object XhtmlBlocks {
    private val BLOCK_TAGS = setOf(
        "p", "div", "li", "blockquote", "figcaption", "h1", "h2", "h3", "h4", "h5", "h6", "dt", "dd", "pre", "caption"
    )
    private val SKIP = setOf("head", "script", "style", "iframe", "object", "video", "audio", "nav")
    private val ISLANDS = setOf("math", "svg", "table")
    private val HEADING_SCALE = mapOf("h1" to 1.6f, "h2" to 1.3f, "h3" to 1.12f, "h4" to 1.05f, "h5" to 1f, "h6" to 1f)
    private val WS = Regex("\\s+")

    fun parse(bytes: ByteArray, chapterPath: String, nextIslandId: () -> Int): Chapter {
        val blocks = ArrayList<Block>()
        val markers = ArrayList<PageMarker>()
        val inline = ArrayList<Block.Island>()
        var text = SpannableStringBuilder()
        var tag = "p"
        var indent = 0f
        val styles = ArrayList<Pair<Any, Int>>() // span, start
        var skipDepth = 0

        fun flush() {
            // Trim the collected text; spans past the end are clamped by SpannableStringBuilder.
            while (text.isNotEmpty() && text[text.length - 1] == ' ') text.delete(text.length - 1, text.length)
            if (text.isNotEmpty()) {
                val h = HEADING_SCALE[tag]
                blocks += Block.Text(
                    text = text,
                    scale = h ?: if (tag == "figcaption" || tag == "caption") 0.85f else 1f,
                    heading = h != null,
                    indentEm = indent,
                    spaceBeforeEm = if (h != null) 1.0f else 0f,
                    spaceAfterEm = if (h != null) 0.4f else 0.6f,
                    justify = tag == "p"
                )
            }
            text = SpannableStringBuilder()
        }

        fun append(raw: String) {
            var s = raw.replace(WS, " ")
            if (text.isEmpty() || text[text.length - 1] == ' ') s = s.trimStart()
            text.append(s)
        }

        val p = Xml.newPullParser()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        p.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
        runCatching { p.setFeature("http://xmlpull.org/v1/doc/features.html#relaxed", true) }
        p.setInput(bytes.inputStream(), null)
        try {
            while (p.next() != XmlPullParser.END_DOCUMENT) {
                when (p.eventType) {
                    XmlPullParser.START_TAG -> {
                        val name = p.name.substringAfter(':').lowercase(Locale.ROOT)
                        if (skipDepth > 0 || name in SKIP) {
                            skipDepth++
                            continue
                        }
                        val type = p.getAttributeValue(null, "epub:type")
                        when {
                            type == "footnote" || p.getAttributeValue(null, "hidden") != null -> skipDepth++
                            type == "pagebreak" -> {
                                val label = p.getAttributeValue(null, "title") ?: p.getAttributeValue(null, "id") ?: ""
                                markers += PageMarker(label, blocks.size, text.length)
                            }
                            name in ISLANDS -> {
                                val xml = outerXml(p)
                                val island = Block.Island(nextIslandId(), name, xml)
                                val inlineMath = name == "math" && xml.contains("display=\"block\"").not() && text.isNotEmpty()
                                if (inlineMath) {
                                    val start = text.length
                                    text.append('￼')
                                    text.setSpan(IslandSpan(island), start, start + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                                    inline += island
                                } else {
                                    flush()
                                    blocks += island
                                }
                            }
                            name == "img" || name == "image" -> {
                                val src = p.getAttributeValue(null, "src") ?: p.getAttributeValue(null, "xlink:href")
                                src?.let { EpubBook.resolve(chapterPath, it) }?.let {
                                    flush()
                                    blocks += Block.Image(it)
                                }
                            }
                            name == "br" -> text.append('\n')
                            name in BLOCK_TAGS -> {
                                flush()
                                tag = name
                            }
                            name == "aside" || name == "ul" || name == "ol" -> {
                                flush()
                                indent += 1.2f
                            }
                            else -> spanFor(name)?.let { styles += it to text.length }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        val name = p.name.substringAfter(':').lowercase(Locale.ROOT)
                        if (skipDepth > 0) {
                            skipDepth--
                            continue
                        }
                        when {
                            name in BLOCK_TAGS -> {
                                flush()
                                tag = "p"
                            }
                            name == "aside" || name == "ul" || name == "ol" -> {
                                flush()
                                indent = (indent - 1.2f).coerceAtLeast(0f)
                            }
                            else -> spanFor(name)?.let {
                                val open = styles.indexOfLast { (span, _) -> span.javaClass == it.javaClass }
                                if (open >= 0) {
                                    val (span, start) = styles.removeAt(open)
                                    if (start < text.length) text.setSpan(span, start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                                }
                            }
                        }
                    }
                    XmlPullParser.TEXT, XmlPullParser.ENTITY_REF -> if (skipDepth == 0) p.text?.let(::append)
                }
            }
        } catch (_: XmlPullParserException) {
            // Broken markup: keep what was read so far.
        }
        flush()
        return Chapter(blocks, markers, inline)
    }

    private fun spanFor(name: String): Any? = when (name) {
        "b", "strong", "th" -> StyleSpan(Typeface.BOLD)
        "i", "em", "cite", "var", "dfn" -> StyleSpan(Typeface.ITALIC)
        "sup" -> SuperscriptSpan()
        "sub" -> SubscriptSpan()
        "code", "kbd", "samp" -> TypefaceSpan("monospace")
        "small" -> RelativeSizeSpan(0.85f)
        else -> null
    }

    /** Serialises the element at the parser's position (and its children) back to XML. */
    private fun outerXml(p: XmlPullParser): String {
        val sb = StringBuilder()
        var depth = 0
        do {
            when (p.eventType) {
                XmlPullParser.START_TAG -> {
                    depth++
                    sb.append('<').append(p.name)
                    for (i in 0 until p.attributeCount) {
                        sb.append(' ').append(p.getAttributeName(i)).append("=\"")
                            .append(escape(p.getAttributeValue(i))).append('"')
                    }
                    sb.append('>')
                }
                XmlPullParser.END_TAG -> {
                    depth--
                    sb.append("</").append(p.name).append('>')
                }
                XmlPullParser.TEXT -> sb.append(escape(p.text))
            }
            if (depth == 0) break
            p.next()
        } while (p.eventType != XmlPullParser.END_DOCUMENT)
        return sb.toString()
    }

    private fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace("\"", "&quot;")
}

/** An inline island in running text: drawn from its rendered bitmap in C, as a gap of the right size in B. */
class IslandSpan(val island: Block.Island) : ReplacementSpan() {
    var width = 0
    var ascent = 0
    var descent = 0
    var bitmap: Bitmap? = null

    override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        val w = if (width > 0) width else (paint.textSize * 3).toInt()
        if (fm != null && ascent > 0) {
            fm.ascent = minOf(fm.ascent, -ascent)
            fm.top = fm.ascent
            fm.descent = maxOf(fm.descent, descent)
            fm.bottom = fm.descent
        }
        return w
    }

    override fun draw(canvas: Canvas, text: CharSequence?, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
        bitmap?.let { canvas.drawBitmap(it, x, (y - ascent).toFloat(), null) }
    }
}

/** A laid-out piece of one block on one page. */
sealed interface Slice {
    val block: Int
    val y: Float

    data class Lines(override val block: Int, override val y: Float, val layout: StaticLayout, val first: Int, val last: Int) : Slice

    data class Box(override val block: Int, override val y: Float, val w: Int, val h: Int) : Slice
}

class Page(val slices: List<Slice>, val labels: List<String>)

/**
 * Paginates blocks into pages of [width] x [height] content pixels at [bodyPx] text size. Rules: whole lines only, at
 * least two lines of a paragraph at the bottom and top of a page (orphans and widows), and a heading never ends a page.
 */
class NativePaginator(
    private val width: Int,
    private val height: Int,
    private val bodyPx: Float,
    private val locale: Locale,
    /** Size of an island or image block, or null to use a placeholder size. */
    private val boxSize: (Block) -> Pair<Int, Int>?
) {
    private val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.SERIF
        color = Color.BLACK
        textLocale = locale
    }

    fun layout(block: Block.Text): StaticLayout {
        val p = TextPaint(paint).apply {
            textSize = bodyPx * block.scale
            if (block.heading) typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        val indent = (bodyPx * block.indentEm).toInt()
        return StaticLayout.Builder.obtain(block.text, 0, block.text.length, p, (width - indent).coerceAtLeast(1))
            .setBreakStrategy(LineBreaker.BREAK_STRATEGY_HIGH_QUALITY)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NORMAL)
            .setJustificationMode(if (block.justify) LineBreaker.JUSTIFICATION_MODE_INTER_WORD else LineBreaker.JUSTIFICATION_MODE_NONE)
            .setLineSpacing(0f, LINE_SPACING)
            .setIncludePad(false)
            .build()
    }

    fun paginate(chapter: Chapter): List<Page> {
        val pages = ArrayList<Page>()
        var slices = ArrayList<Slice>()
        var labels = ArrayList<String>()
        var y = 0f
        val markersByBlock = chapter.markers.groupBy { it.block }

        fun newPage() {
            pages += Page(slices, labels)
            slices = ArrayList()
            labels = ArrayList()
            y = 0f
        }

        chapter.blocks.forEachIndexed { index, block ->
            markersByBlock[index]?.forEach { labels += it.label }
            when (block) {
                is Block.Text -> {
                    val layout = layout(block)
                    if (y > 0f) y += bodyPx * block.spaceBeforeEm
                    var line = 0
                    val count = layout.lineCount
                    if (block.heading) {
                        // Keep a heading with at least two lines of what follows.
                        val need = layout.height + bodyPx * block.spaceAfterEm + 2 * bodyPx * LINE_SPACING
                        if (y > 0f && y + need > height) newPage()
                    }
                    while (line < count) {
                        val top = layout.getLineTop(line)
                        var fit = line
                        while (fit < count && y + (layout.getLineBottom(fit) - top) <= height) fit++
                        var take = fit - line
                        val remaining = count - line
                        if (take < remaining) {
                            if (take < MIN_LINES && y > 0f) take = 0 // orphan: move the paragraph's start over
                            else if (remaining - take < MIN_LINES && take > MIN_LINES) take -= MIN_LINES - (remaining - take) // widow
                        }
                        if (take == 0) {
                            if (y == 0f) take = 1 else {
                                newPage()
                                continue
                            }
                        }
                        slices += Slice.Lines(index, y, layout, line, line + take)
                        y += layout.getLineBottom(line + take - 1) - top
                        line += take
                        if (line < count) newPage()
                    }
                    y += bodyPx * block.spaceAfterEm
                }
                else -> {
                    val (w0, h0) = boxSize(block) ?: (width to (bodyPx * 4).toInt())
                    val scale = minOf(1f, width.toFloat() / w0, height.toFloat() / h0)
                    val w = (w0 * scale).toInt()
                    val h = (h0 * scale).toInt()
                    if (y > 0f && y + h > height) newPage()
                    slices += Slice.Box(index, y, w, h)
                    y += h + bodyPx * BOX_SPACE_EM
                }
            }
        }
        if (slices.isNotEmpty()) newPage()
        return pages
    }

    /** The text each page shows, joined: must equal the chapter's text exactly (nothing lost or repeated). */
    fun check(chapter: Chapter, pages: List<Page>): String? {
        val expected = chapter.blocks.mapIndexedNotNull { i, b -> (b as? Block.Text)?.let { i to it.text.toString() } }.toMap()
        val got = HashMap<Int, StringBuilder>()
        var lastBlock = -1
        for ((pi, page) in pages.withIndex()) {
            for (s in page.slices) {
                if (s.block < lastBlock) return "page $pi goes back to block ${s.block}"
                lastBlock = s.block
                if (s is Slice.Lines) {
                    val l = s.layout
                    val bottom = s.y + l.getLineBottom(s.last - 1) - l.getLineTop(s.first)
                    if (bottom > height + 0.5f) return "page $pi clips block ${s.block}"
                    got.getOrPut(s.block) { StringBuilder() }
                        .append(l.text.subSequence(l.getLineStart(s.first), l.getLineEnd(s.last - 1)))
                }
            }
        }
        for ((i, text) in expected) if (got[i]?.toString() != text) return "block $i text differs"
        return null
    }

    fun draw(canvas: Canvas, page: Page, chapter: Chapter, left: Float, top: Float, images: (Block) -> Bitmap?) {
        val placeholder = Paint().apply { color = PLACEHOLDER }
        for (s in page.slices) {
            when (s) {
                is Slice.Lines -> {
                    val block = chapter.blocks[s.block] as Block.Text
                    val l = s.layout
                    val lineTop = l.getLineTop(s.first).toFloat()
                    canvas.save()
                    canvas.translate(left + bodyPx * block.indentEm, top + s.y - lineTop)
                    canvas.clipRect(0f, lineTop, width.toFloat(), l.getLineBottom(s.last - 1).toFloat())
                    l.draw(canvas)
                    canvas.restore()
                }
                is Slice.Box -> {
                    val bmp = images(chapter.blocks[s.block])
                    val x = left + (width - s.w) / 2f
                    if (bmp != null) {
                        canvas.drawBitmap(bmp, null, android.graphics.RectF(x, top + s.y, x + s.w, top + s.y + s.h), null)
                    } else {
                        canvas.drawRect(x, top + s.y, x + s.w, top + s.y + s.h, placeholder)
                    }
                }
            }
        }
    }

    companion object {
        const val LINE_SPACING = 1.4f
        const val MIN_LINES = 2
        const val BOX_SPACE_EM = 0.8f
        private const val PLACEHOLDER = 0x22000000

        /** Reads an image's size without decoding it. */
        fun imageSize(bytes: ByteArray): Pair<Int, Int>? {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
            return if (o.outWidth > 0) o.outWidth to o.outHeight else null
        }
    }
}
