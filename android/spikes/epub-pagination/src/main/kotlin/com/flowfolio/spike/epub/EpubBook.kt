package com.flowfolio.spike.epub

import android.util.Xml
import java.io.File
import java.util.zip.ZipFile
import org.xmlpull.v1.XmlPullParser

/** Our own minimal EPUB reader (container, package document, nav), with the limits a hostile book needs. */
class EpubBook private constructor(
    private val zip: ZipFile,
    val title: String,
    val spine: List<String>,
    val toc: List<Pair<String, String>>,
    val pageList: List<Pair<String, String>>,
    val rightToLeft: Boolean,
    val fixedLayout: Boolean
) : AutoCloseable {

    /** The bytes of a book-internal path, or null when it is missing, outside the book or too large. */
    fun read(path: String): ByteArray? {
        val clean = normalize(path) ?: return null
        val entry = zip.getEntry(clean) ?: return null
        if (entry.size > MAX_ENTRY_BYTES) return null
        return zip.getInputStream(entry).use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(BUFFER)
            var total = 0L
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                total += n
                // Sizes in the zip directory can lie: count what actually inflates.
                if (total > MAX_ENTRY_BYTES) return null
                out.write(buf, 0, n)
            }
            out.toByteArray()
        }
    }

    override fun close() = zip.close()

    companion object {
        const val MAX_ENTRY_BYTES = 64L * 1024 * 1024
        private const val BUFFER = 64 * 1024

        /** Resolves `.` and `..`; anything that climbs out of the book is refused. */
        fun normalize(path: String): String? {
            val out = ArrayDeque<String>()
            for (part in path.replace('\\', '/').split('/')) {
                when (part) {
                    "", "." -> Unit
                    ".." -> if (out.isEmpty()) return null else out.removeLast()
                    else -> out.addLast(part)
                }
            }
            return out.joinToString("/").takeIf { it.isNotEmpty() }
        }

        fun resolve(base: String, href: String): String? {
            val noFragment = java.net.URLDecoder.decode(href.substringBefore('#'), "UTF-8")
            val dir = base.substringBeforeLast('/', "")
            return normalize(if (dir.isEmpty()) noFragment else "$dir/$noFragment")
        }

        /** Opens a book or throws [IllegalArgumentException] with the reason it was refused. */
        fun open(file: File): EpubBook {
            val zip = ZipFile(file)
            try {
                require(zip.getEntry("META-INF/encryption.xml") == null || !encrypted(zip)) { "DRM-protected" }
                val probe = EpubBook(zip, "", emptyList(), emptyList(), emptyList(), false, false)
                val container = probe.read("META-INF/container.xml") ?: throw IllegalArgumentException("no container")
                val opfPath = attr(container, "rootfile", "full-path") ?: throw IllegalArgumentException("no rootfile")
                val opf = probe.read(opfPath) ?: throw IllegalArgumentException("no package document")
                return parsePackage(zip, opfPath, opf)
            } catch (e: Exception) {
                zip.close()
                throw if (e is IllegalArgumentException) e else IllegalArgumentException(e.toString(), e)
            }
        }

        private fun encrypted(zip: ZipFile): Boolean {
            val xml = zip.getInputStream(zip.getEntry("META-INF/encryption.xml")).readBytes().decodeToString()
            // Font obfuscation (IDPF and Adobe) is allowed; anything else that encrypts content is DRM.
            return Regex("Algorithm=\"([^\"]+)\"").findAll(xml).map { it.groupValues[1] }.any {
                it != "http://www.idpf.org/2008/embedding" && it != "http://ns.adobe.com/pdf/enc#RC"
            }
        }

        private fun parser(bytes: ByteArray): XmlPullParser = Xml.newPullParser().apply {
            // No DOCTYPE processing: entity tricks (billion laughs, external entities) are not expanded.
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
            setInput(bytes.inputStream(), null)
        }

        private fun attr(xml: ByteArray, tag: String, name: String): String? {
            val p = parser(xml)
            while (p.next() != XmlPullParser.END_DOCUMENT) {
                if (p.eventType == XmlPullParser.START_TAG && p.name.substringAfter(':') == tag) {
                    return p.getAttributeValue(null, name)
                }
            }
            return null
        }

        private fun parsePackage(zip: ZipFile, opfPath: String, opf: ByteArray): EpubBook {
            val manifest = HashMap<String, Pair<String, String?>>()
            val spineIds = ArrayList<String>()
            var title = ""
            var rtl = false
            var fixed = false
            var navHref: String? = null
            var inTitle = false
            val p = parser(opf)
            while (p.next() != XmlPullParser.END_DOCUMENT) {
                when (p.eventType) {
                    XmlPullParser.START_TAG -> when (p.name.substringAfter(':')) {
                        "title" -> inTitle = title.isEmpty()
                        "item" -> {
                            val id = p.getAttributeValue(null, "id") ?: continue
                            val href = p.getAttributeValue(null, "href") ?: continue
                            val props = p.getAttributeValue(null, "properties")
                            manifest[id] = href to props
                            if (props?.split(' ')?.contains("nav") == true) navHref = href
                        }
                        "spine" -> rtl = p.getAttributeValue(null, "page-progression-direction") == "rtl"
                        "itemref" -> p.getAttributeValue(null, "idref")?.let(spineIds::add)
                        "meta" -> if (p.getAttributeValue(null, "property") == "rendition:layout") {
                            fixed = p.nextText().trim() == "pre-paginated"
                        }
                    }
                    XmlPullParser.TEXT -> if (inTitle) title += p.text
                    XmlPullParser.END_TAG -> if (p.name.substringAfter(':') == "title") inTitle = false
                }
            }
            val spine = spineIds.mapNotNull { id -> manifest[id]?.first?.let { resolve(opfPath, it) } }
            require(spine.isNotEmpty()) { "empty spine" }
            val book = EpubBook(zip, title.trim(), spine, emptyList(), emptyList(), rtl, fixed)
            val navPath = navHref?.let { resolve(opfPath, it) }
            val navBytes = navPath?.let(book::read)
            val (toc, pages) = if (navBytes != null) parseNav(navPath, navBytes) else emptyList<Pair<String, String>>() to emptyList()
            return EpubBook(zip, title.trim(), spine, toc, pages, rtl, fixed)
        }

        /** The `toc` and `page-list` navs: (label, resolved href with fragment). */
        private fun parseNav(navPath: String, bytes: ByteArray): Pair<List<Pair<String, String>>, List<Pair<String, String>>> {
            val toc = ArrayList<Pair<String, String>>()
            val pages = ArrayList<Pair<String, String>>()
            var current: MutableList<Pair<String, String>>? = null
            var depth = 0
            var href: String? = null
            val label = StringBuilder()
            val p = parser(bytes)
            while (p.next() != XmlPullParser.END_DOCUMENT) {
                when (p.eventType) {
                    XmlPullParser.START_TAG -> {
                        if (p.name == "nav") {
                            current = when (p.getAttributeValue(null, "epub:type")) {
                                "toc" -> toc
                                "page-list" -> pages
                                else -> null
                            }
                            depth = 0
                        }
                        if (p.name == "a") {
                            href = p.getAttributeValue(null, "href")
                            label.clear()
                        }
                        depth++
                    }
                    XmlPullParser.TEXT -> if (href != null) label.append(p.text)
                    XmlPullParser.END_TAG -> {
                        depth--
                        if (p.name == "a" && href != null) {
                            val target = resolve(navPath, href!!)
                            val fragment = href!!.substringAfter('#', "")
                            if (target != null) current?.add(label.toString().trim() to if (fragment.isEmpty()) target else "$target#$fragment")
                            href = null
                        }
                        if (p.name == "nav") current = null
                    }
                }
            }
            return toc to pages
        }
    }
}
