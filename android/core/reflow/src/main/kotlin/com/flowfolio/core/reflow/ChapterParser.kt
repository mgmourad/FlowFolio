package com.flowfolio.core.reflow

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.SubscriptSpan
import android.text.style.SuperscriptSpan
import android.text.style.TypefaceSpan
import android.util.Xml
import com.flowfolio.core.epub.EpubArchive
import java.util.Locale
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

/**
 * XHTML to blocks for one chapter at [path]. Tolerant: broken markup ends the chapter where it breaks instead of
 * failing the book. Islands get ids from [nextIslandId], unique across the book.
 */
// One handler per kind of element (blocks, indents, footnotes, islands, images, spans).
@Suppress("TooManyFunctions")
class ChapterParser(private val path: String, private val nextIslandId: () -> Int) {
    private val blocks = ArrayList<Block>()
    private val anchors = HashMap<String, Int>()
    private val footnotes = HashMap<String, CharSequence>()
    private var text = SpannableStringBuilder()
    private var style = TextStyle()
    private var flushed = 0

    /** Spans still open: the tag that opened each, the span, and where it starts in [text]. */
    private val open = ArrayList<OpenSpan>()
    private var indent = 0f
    private var skipDepth = 0
    private var depth = 0
    private var pre = 0

    /** An open note: its id, the element depth it started at, its first block, and whether it stays in the flow. */
    private var footnote: Footnote? = null

    private class OpenSpan(val tag: String, val span: Any, var at: Int)

    private class Footnote(val id: String?, val depth: Int, val firstBlock: Int, val keep: Boolean)

    /** The offset the next character will have. */
    private val here get() = flushed + text.length

    fun parse(bytes: ByteArray): Chapter {
        val p = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
            runCatching { setFeature("http://xmlpull.org/v1/doc/features.html#relaxed", true) }
            setInput(bytes.inputStream(), null)
        }
        try {
            while (p.next() != XmlPullParser.END_DOCUMENT) event(p)
        } catch (_: XmlPullParserException) {
            // Broken markup: keep what was read so far.
        }
        flush()
        return Chapter(path, blocks, anchors, footnotes, here)
    }

    private fun event(p: XmlPullParser) {
        when (p.eventType) {
            XmlPullParser.START_TAG -> start(p)
            XmlPullParser.END_TAG -> end(p.name.local())
            XmlPullParser.TEXT, XmlPullParser.ENTITY_REF -> if (skipDepth == 0) p.text?.let(::append)
        }
    }

    private fun start(p: XmlPullParser) {
        val name = p.name.local()
        if (skipDepth > 0 || name in SKIP || p.getAttributeValue(null, "hidden") != null) {
            skipDepth++
            return
        }
        depth++
        p.getAttributeValue(null, "id")?.let { anchors.putIfAbsent(it, here) }
        val type = p.getAttributeValue(null, "epub:type").orEmpty() + " " + p.getAttributeValue(null, "role").orEmpty()
        val note = noteKind(type)
        when {
            footnote == null && note == NoteKind.ASIDE -> startFootnote(p, keep = false)

            name in ISLANDS -> island(p, name)

            name == "img" || name == "image" -> image(p)

            else -> {
                if (footnote == null && note == NoteKind.KEPT) startFootnote(p, keep = true)
                startInline(p, name)
            }
        }
    }

    /** Line breaks, blocks, indents, links and inline styles. */
    private fun startInline(p: XmlPullParser, name: String) {
        when (name) {
            "br" -> text.append('\n')

            in BLOCKS -> startBlock(name)

            in INDENTED -> startIndented()

            "a" -> p.getAttributeValue(null, "href")?.let {
                open += OpenSpan(name, LinkSpan(resolved(it)), text.length)
            }

            else -> spanFor(name)?.let { open += OpenSpan(name, it, text.length) }
        }
    }

    private fun end(name: String) {
        if (skipDepth > 0) {
            skipDepth--
            return
        }
        when (name) {
            in BLOCKS -> {
                flush()
                if (name == "pre") pre--
                style = TextStyle(indentEm = indent)
            }

            in INDENTED -> {
                flush()
                indent = (indent - INDENT_EM).coerceAtLeast(0f)
                style = TextStyle(indentEm = indent)
            }

            else -> closeSpan(name)
        }
        if (footnote?.depth == depth) endFootnote()
        depth--
    }

    private fun append(raw: String) {
        if (pre > 0) {
            text.append(raw)
            return
        }
        var s = raw.replace(WHITESPACE, " ")
        if (text.isEmpty() || text[text.length - 1] == ' ' || text[text.length - 1] == '\n') s = s.trimStart()
        text.append(s)
    }

    private fun startBlock(name: String) {
        flush()
        if (name == "pre") pre++
        val heading = HEADING_SCALE[name]
        style = TextStyle(
            scale = heading ?: if (name == "figcaption" || name == "caption") CAPTION_SCALE else 1f,
            bold = heading != null,
            heading = heading != null,
            indentEm = indent,
            spaceBeforeEm = if (heading != null) HEADING_SPACE_BEFORE_EM else 0f,
            spaceAfterEm = if (heading != null) HEADING_SPACE_AFTER_EM else TextStyle.BODY_SPACE_AFTER_EM,
            justify = name == "p" || name == "dd",
            centered = name == "figcaption" || name == "caption",
            preformatted = name == "pre"
        )
    }

    private fun startIndented() {
        flush()
        indent += INDENT_EM
        style = TextStyle(indentEm = indent)
    }

    /**
     * A note starts. A footnote's body goes aside, for the popover, and the flow continues after it; an endnote or rear
     * note ([keep]) stays where the book puts it (its notes section) and is copied for the popover too.
     */
    private fun startFootnote(p: XmlPullParser, keep: Boolean) {
        flush()
        footnote = Footnote(p.getAttributeValue(null, "id"), depth, blocks.size, keep)
    }

    /** The note's element closed: its text becomes its popover's, and a footnote's blocks leave the flow. */
    private fun endFootnote() {
        flush()
        val note = footnote ?: return
        footnote = null
        val body = SpannableStringBuilder()
        val noteBlocks = blocks.subList(note.firstBlock, blocks.size)
        for (b in noteBlocks.filterIsInstance<Block.Text>()) {
            if (body.isNotEmpty()) body.append('\n')
            body.append(b.text)
        }
        if (!note.keep) noteBlocks.clear()
        note.id?.let { footnotes[it] = body }
    }

    /**
     * What a note's `epub:type` or `role` makes it ([type] holds both): an [NoteKind.ASIDE] footnote, or a
     * [NoteKind.KEPT] endnote, rear note or note; null for anything else, including the sections that hold notes.
     */
    private fun noteKind(type: String): NoteKind? {
        val tokens = type.lowercase().split(' ').map { it.removePrefix("doc-") }
        return when {
            "footnote" in tokens -> NoteKind.ASIDE
            tokens.any { it in KEPT_NOTES } -> NoteKind.KEPT
            else -> null
        }
    }

    private enum class NoteKind { ASIDE, KEPT }

    private fun island(p: XmlPullParser, name: String) {
        val xml = outerXml(p)
        val id = nextIslandId()
        val inline = name == "math" && !xml.contains("display=\"block\"") && text.isNotEmpty()
        if (inline) {
            val at = text.length
            text.append(OBJECT)
            text.setSpan(IslandSpan(id, name, xml), at, at + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        } else {
            flush()
            val block = Block.Island(here, id, name, xml)
            blocks += block
            flushed += block.length
        }
        // outerXml consumed the element's end tag, so end() never sees it.
        depth--
    }

    private fun image(p: XmlPullParser) {
        val src = p.getAttributeValue(null, "src") ?: p.getAttributeValue(null, "xlink:href") ?: return
        val target = EpubArchive.resolve(path, src)?.substringBefore('#') ?: return
        flush()
        val block = Block.Image(here, target, p.getAttributeValue(null, "alt"))
        blocks += block
        flushed += block.length
    }

    /** Ends the text collected so far as a block (trailing spaces trimmed). Spans still open continue in the next. */
    private fun flush() {
        while (text.isNotEmpty() && text[text.length - 1] == ' ') text.delete(text.length - 1, text.length)
        for (o in open) {
            if (o.at < text.length) text.setSpan(o.span, o.at, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        open.forEach { it.at = 0 }
        if (text.isNotEmpty()) {
            val block = Block.Text(flushed, text, style)
            blocks += block
            flushed += block.length
        }
        text = SpannableStringBuilder()
    }

    private fun closeSpan(tag: String) {
        val i = open.indexOfLast { it.tag == tag }
        if (i < 0) return
        val o = open.removeAt(i)
        if (o.at < text.length) text.setSpan(o.span, o.at, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun resolved(href: String): String = EpubArchive.resolve(path, href) ?: href

    /** Serialises the element at the parser's position (and its children) back to XML, consuming it. */
    private fun outerXml(p: XmlPullParser): String {
        val sb = StringBuilder()
        var level = 0
        do {
            when (p.eventType) {
                XmlPullParser.START_TAG -> {
                    level++
                    sb.append('<').append(p.name)
                    for (i in 0 until p.attributeCount) {
                        sb.append(' ').append(p.getAttributeName(i)).append("=\"")
                            .append(p.getAttributeValue(i).escaped()).append('"')
                    }
                    sb.append('>')
                }

                XmlPullParser.END_TAG -> {
                    level--
                    sb.append("</").append(p.name).append('>')
                }

                XmlPullParser.TEXT -> sb.append(p.text.escaped())
            }
            if (level > 0) p.next()
        } while (level > 0 && p.eventType != XmlPullParser.END_DOCUMENT)
        return sb.toString()
    }

    private companion object {
        const val OBJECT = '￼'
        const val INDENT_EM = 1.2f
        const val CAPTION_SCALE = 0.85f
        const val SMALL_SCALE = 0.85f
        const val HEADING_SPACE_BEFORE_EM = 1f
        const val HEADING_SPACE_AFTER_EM = 0.4f
        val WHITESPACE = Regex("\\s+")
        val BLOCKS = setOf(
            "p", "div", "li", "blockquote", "figcaption", "caption", "h1", "h2", "h3", "h4", "h5", "h6", "dt", "dd",
            "pre"
        )
        val INDENTED = setOf("aside", "ul", "ol", "dl")

        /** Notes that stay in the flow: endnotes and rear notes in a notes section, and plain notes. */
        val KEPT_NOTES = setOf("endnote", "rearnote", "note")
        val SKIP = setOf("head", "script", "style", "iframe", "object", "video", "audio", "nav", "noscript", "template")
        val ISLANDS = setOf("math", "svg", "table")
        val HEADING_SCALE = mapOf("h1" to 1.6f, "h2" to 1.3f, "h3" to 1.12f, "h4" to 1.05f, "h5" to 1f, "h6" to 1f)

        fun String.local() = substringAfter(':').lowercase(Locale.ROOT)

        fun String.escaped() = replace("&", "&amp;").replace("<", "&lt;").replace("\"", "&quot;")

        fun spanFor(name: String): Any? = when (name) {
            "b", "strong", "th" -> StyleSpan(Typeface.BOLD)
            "i", "em", "cite", "var", "dfn" -> StyleSpan(Typeface.ITALIC)
            "sup" -> SuperscriptSpan()
            "sub" -> SubscriptSpan()
            "code", "kbd", "samp", "tt" -> TypefaceSpan("monospace")
            "small" -> RelativeSizeSpan(SMALL_SCALE)
            else -> null
        }
    }
}
