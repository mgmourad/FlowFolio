package com.flowfolio.core.epub

import org.xmlpull.v1.XmlPullParser

/**
 * EPUB 3 navigation: `<nav epub:type="toc|page-list|landmarks">` with nested `<ol><li><a>` lists. A `<li>` may have a
 * `<span>` heading instead of a link; it then points at its first child. Hrefs are resolved against [path].
 */
internal class NavReader(private val path: String) {
    private val lists = HashMap<String, MutableList<NavPoint>>()
    private var kind: String? = null

    /** One list per open `<ol>`. */
    private val levels = ArrayDeque<MutableList<NavPoint>>()

    /** Label and href of each open `<li>`. */
    private val items = ArrayDeque<Array<String?>>()

    /** A nested `<ol>` closes before its `<li>`: its points wait here for that `<li>`. */
    private val pendingChildren = ArrayDeque<List<NavPoint>>()
    private val text = StringBuilder()
    private var inLabel = false

    fun read(bytes: ByteArray): Map<String, List<NavPoint>> {
        val p = xmlParser(bytes, relaxed = true)
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> start(p)
                XmlPullParser.TEXT, XmlPullParser.ENTITY_REF -> if (inLabel) text.append(p.text)
                XmlPullParser.END_TAG -> end(p.name)
            }
        }
        return lists
    }

    private fun start(p: XmlPullParser) {
        when {
            p.name == "nav" -> kind = p.getAttributeValue(OPS_NS, "type")?.split(' ')?.firstOrNull { it in KINDS }
            kind == null -> Unit
            p.name == "ol" -> levels.addLast(ArrayList())
            p.name == "li" -> items.addLast(arrayOfNulls(2))
            (p.name == "a" || p.name == "span") && items.isNotEmpty() -> startLabel(p)
        }
    }

    /** A link or heading inside the open `<li>`: its text is the label, a link's href the target. */
    private fun startLabel(p: XmlPullParser) {
        if (p.name == "a") items.last()[1] = p.getAttributeValue(null, "href")?.let { EpubArchive.resolve(path, it) }
        text.clear()
        inLabel = true
    }

    private fun end(name: String) {
        when (name) {
            "nav" -> kind = null

            "a", "span" -> if (inLabel) {
                items.lastOrNull()?.set(0, text.toString().collapsed())
                inLabel = false
            }

            "li" -> if (kind != null && items.isNotEmpty()) closeItem()

            "ol" -> if (kind != null && levels.isNotEmpty()) closeList()
        }
    }

    private fun closeItem() {
        val (label, href) = items.removeLast()
        val children = pendingChildren.removeLastOrNull().orEmpty()
        val target = href ?: children.firstOrNull()?.href
        if (label != null && target != null) levels.lastOrNull()?.add(NavPoint(label, target, children))
    }

    private fun closeList() {
        val done = levels.removeLast()
        if (levels.isEmpty()) lists.getOrPut(kind!!) { ArrayList() } += done else pendingChildren.addLast(done)
    }

    private companion object {
        const val OPS_NS = "http://www.idpf.org/2007/ops"
        val KINDS = setOf("toc", "page-list", "landmarks")
    }
}

/**
 * EPUB 2 navigation (NCX): `navMap/navPoint` (nested) and `pageList/pageTarget`, each with `navLabel/text` and
 * `content@src`. Returns the contents and the page list.
 */
internal class NcxReader(private val path: String) {
    private class Open(
        val label: StringBuilder = StringBuilder(),
        var href: String? = null,
        val children: MutableList<NavPoint> = ArrayList()
    )

    private val stack = ArrayDeque<Open>()
    private val toc = ArrayList<NavPoint>()
    private val pages = ArrayList<NavPoint>()
    private var inText = false

    fun read(bytes: ByteArray): Pair<List<NavPoint>, List<NavPoint>> {
        val p = xmlParser(bytes, relaxed = true)
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> start(p)
                XmlPullParser.TEXT, XmlPullParser.ENTITY_REF -> if (inText) stack.last().label.append(p.text)
                XmlPullParser.END_TAG -> end(p.name)
            }
        }
        return toc to pages
    }

    private fun start(p: XmlPullParser) {
        when (p.name) {
            "navPoint", "pageTarget" -> stack.addLast(Open())

            "text" -> inText = stack.isNotEmpty()

            "content" -> stack.lastOrNull()?.href =
                p.getAttributeValue(null, "src")?.let { EpubArchive.resolve(path, it) }
        }
    }

    private fun end(name: String) {
        if (name == "text") inText = false
        if (name != "navPoint" && name != "pageTarget") return
        val open = stack.removeLast()
        val point = open.href?.let { NavPoint(open.label.toString().collapsed(), it, open.children) } ?: return
        when {
            name == "pageTarget" -> pages += point
            stack.isEmpty() -> toc += point
            else -> stack.last().children += point
        }
    }
}
