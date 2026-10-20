package com.flowfolio.core.epub

import org.xmlpull.v1.XmlPullParser

/** Reads the package document (`content.opf`) at [path]: metadata, manifest, spine and rendition properties. */
internal class PackageReader(val path: String) {
    val manifest = LinkedHashMap<String, ManifestItem>()
    var ncxId: String? = null
        private set
    var coverId: String? = null
        private set
    var rightToLeft = false
        private set
    var fixedLayout = false
        private set
    private val spineIds = ArrayList<Pair<String, Boolean>>()
    private val creators = ArrayList<String>()
    private val identifiers = ArrayList<String>()
    private val first = HashMap<String, String>()
    private var inMetadata = false

    fun read(opf: ByteArray) {
        val p = xmlParser(opf)
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> start(p)
                XmlPullParser.END_TAG -> if (p.name == "metadata") inMetadata = false
            }
        }
    }

    fun spine(): List<SpineItem> =
        spineIds.mapNotNull { (id, linear) -> manifest[id]?.let { SpineItem(it.path, it.mediaType, linear) } }

    fun metadata() = EpubMetadata(
        title = first["title"],
        creators = creators,
        language = first["language"],
        identifiers = identifiers,
        publisher = first["publisher"],
        description = first["description"]?.let(::plain),
        date = first["date"]
    )

    private fun start(p: XmlPullParser) {
        when (p.name) {
            "metadata" -> inMetadata = true

            "item" -> item(p)

            "spine" -> {
                ncxId = p.getAttributeValue(null, "toc")
                rightToLeft = p.getAttributeValue(null, "page-progression-direction") == "rtl"
            }

            "itemref" -> p.getAttributeValue(null, "idref")?.let {
                spineIds += it to (p.getAttributeValue(null, "linear") != "no")
            }

            "meta" -> meta(p)

            in DC_FIELDS -> if (inMetadata) dublinCore(p)
        }
    }

    private fun item(p: XmlPullParser) {
        val id = p.getAttributeValue(null, "id")
        val target = p.getAttributeValue(null, "href")?.let { EpubArchive.resolve(path, it)?.substringBefore('#') }
        if (id != null && target != null) {
            val props = p.getAttributeValue(null, "properties").orEmpty().split(' ').filter { it.isNotEmpty() }.toSet()
            manifest[id] = ManifestItem(id, target, p.getAttributeValue(null, "media-type").orEmpty(), props)
        }
    }

    private fun meta(p: XmlPullParser) {
        when {
            p.getAttributeValue(null, "name") == "cover" -> coverId = p.getAttributeValue(null, "content")

            p.getAttributeValue(null, "property") == "rendition:layout" ->
                fixedLayout = p.nextText().trim() == "pre-paginated"
        }
    }

    private fun dublinCore(p: XmlPullParser) {
        val key = p.name
        val text = p.nextText().collapsed()
        when {
            text.isEmpty() -> Unit
            key == "creator" -> creators += text
            key == "identifier" -> identifiers += text
            else -> first.putIfAbsent(key, text)
        }
    }

    private companion object {
        val DC_FIELDS = setOf("title", "creator", "language", "identifier", "publisher", "description", "date")
        val TAGS = Regex("<[^>]*>")
        val ENTITIES = mapOf(
            "&amp;" to "&",
            "&lt;" to "<",
            "&gt;" to ">",
            "&quot;" to "\"",
            "&#39;" to "'",
            "&nbsp;" to " "
        )

        /** Descriptions are often HTML: keep the words only. */
        fun plain(html: String): String? {
            var s = html.replace(TAGS, " ")
            ENTITIES.forEach { (k, v) -> s = s.replace(k, v) }
            return s.collapsed().takeIf { it.isNotEmpty() }
        }
    }
}
