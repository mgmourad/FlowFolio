package com.flowfolio.core.epub

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

/** One face the book declares with `@font-face`: its family, weight and style, and the font file's path in the book. */
data class FontFace(val family: String, val bold: Boolean, val italic: Boolean, val path: String)

/** The body text's family, as the book's CSS names it, with the faces the book ships for it. */
data class BookFontFamily(val family: String, val faces: List<FontFace>)

/**
 * The book's own fonts (M3, ADR-0030): which `@font-face` family the book sets its body text in, and that family's
 * font files, read safely by [FontFiles]. WOFF 2 needs Brotli and is not read.
 */
object EpubFonts {
    /** The family the book sets its body text in, or null when it ships no fonts for it. */
    fun bodyFamily(pkg: EpubPackage, archive: EpubArchive): BookFontFamily? {
        val sheets = pkg.manifest.values
            .filter { it.mediaType.equals(CSS, ignoreCase = true) }
            .mapNotNull { item ->
                archive.read(item.path)?.takeIf {
                    it.size <= MAX_CSS_BYTES
                }?.let { item.path to uncommented(String(it)) }
            }
        val faces = sheets.flatMap { (path, css) -> faces(path, css) }.filter { archive.contains(it.path) }
        val families = faces.map { it.family.lowercase() }.toSet()
        // The body's family as the CSS names it; a book with one family uses it for the body too.
        val chosen = BODY_SELECTORS.firstNotNullOfOrNull { selectors ->
            sheets.firstNotNullOfOrNull { (_, css) -> declaredFamily(css, selectors, families) }
        } ?: families.singleOrNull()
        val own = faces.filter { it.family.lowercase() == chosen }.distinctBy { it.bold to it.italic }
        return own.firstOrNull()?.let { BookFontFamily(it.family, own) }
    }

    /** The font file at [path], ready to load, or null when it is missing, damaged, too large or not a font. */
    fun fontBytes(pkg: EpubPackage, archive: EpubArchive, path: String): ByteArray? {
        val raw = archive.read(path)?.takeIf { it.size <= FontFiles.MAX_FONT_BYTES }
        val clear = raw?.let {
            when (obfuscation(archive.read(ENCRYPTION), path)) {
                null -> it
                IDPF_FONT -> pkg.metadata.uniqueIdentifier?.let { id -> FontFiles.deobfuscateIdpf(it, id) }
                ADOBE_FONT -> FontFiles.adobeKey(pkg.metadata)?.let { key -> FontFiles.deobfuscateAdobe(it, key) }
                else -> null
            }
        }
        return clear?.let(FontFiles::plainFont)
    }

    /** The `@font-face` rules of a style sheet at [cssPath]; `src` paths are resolved in the book. */
    internal fun faces(cssPath: String, css: String): List<FontFace> = FONT_FACE.findAll(css).mapNotNull { rule ->
        val body = rule.groupValues[1]
        val family = declaration(body, "font-family")?.let(::unquoted)
        val src = declaration(body, "src")?.let { sources ->
            URL.findAll(sources)
                .map { unquoted(it.groupValues[1]) }
                .firstOrNull { !it.substringBefore('?').endsWith(".woff2", ignoreCase = true) }
        }
        val path = src?.let { EpubArchive.resolve(cssPath, it) }
        if (family == null || path == null) return@mapNotNull null
        val weight = declaration(body, "font-weight")?.trim()?.lowercase()
        val bold = weight == "bold" || weight == "bolder" || (weight?.toIntOrNull() ?: 0) >= BOLD_WEIGHT
        val italic = declaration(body, "font-style")?.trim()?.lowercase().let { it == "italic" || it == "oblique" }
        FontFace(family, bold, italic, path)
    }.toList()

    /** The first family in a `font-family` of a rule for one of [selectors] that the book has faces for. */
    internal fun declaredFamily(css: String, selectors: Set<String>, families: Set<String>): String? = RULE.findAll(css)
        .filter { rule -> rule.groupValues[1].split(',').any { it.trim().lowercase() in selectors } }
        .firstNotNullOfOrNull { rule ->
            declaration(rule.groupValues[2], "font-family")
                ?.split(',')
                ?.map { unquoted(it).lowercase() }
                ?.firstOrNull { it in families }
        }

    /** The obfuscation algorithm [encryption] (`encryption.xml`) names for [path], or null when it names none. */
    internal fun obfuscation(encryption: ByteArray?, path: String): String? = encryption?.let {
        try {
            cipherReferences(it)[EpubArchive.normalize(path)]
        } catch (_: XmlPullParserException) {
            UNKNOWN // unreadable: the font is not used rather than shown scrambled
        }
    }

    /** Every `CipherReference` in `encryption.xml`, with the algorithm of its `EncryptedData`. */
    private fun cipherReferences(xml: ByteArray): Map<String?, String> {
        val p = xmlParser(xml)
        val found = HashMap<String?, String>()
        var algorithm = UNKNOWN
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            if (p.eventType != XmlPullParser.START_TAG) continue
            when (p.name) {
                "EncryptedData" -> algorithm = UNKNOWN

                "EncryptionMethod" -> algorithm = p.getAttributeValue(null, "Algorithm") ?: UNKNOWN

                "CipherReference" -> found[EpubArchive.normalize(p.getAttributeValue(null, "URI").orEmpty())] =
                    algorithm
            }
        }
        return found
    }

    private fun declaration(block: String, name: String): String? =
        Regex("(?:^|[;{\\s])${Regex.escape(name)}\\s*:\\s*([^;]+)", RegexOption.IGNORE_CASE)
            .find(block)?.groupValues?.get(1)?.replace("!important", "")?.trim()

    private fun unquoted(s: String) = s.trim().trim('"', '\'').trim()

    private fun uncommented(css: String) = COMMENT.replace(css, " ")

    private const val CSS = "text/css"
    private const val ENCRYPTION = "META-INF/encryption.xml"
    internal const val IDPF_FONT = "http://www.idpf.org/2008/embedding"
    internal const val ADOBE_FONT = "http://ns.adobe.com/pdf/enc#RC"
    private const val UNKNOWN = "unknown"
    private const val BOLD_WEIGHT = 600
    private const val MAX_CSS_BYTES = 1 shl 20
    private val COMMENT = Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL)
    private val FONT_FACE = Regex("@font-face\\s*\\{([^}]*)\\}", RegexOption.IGNORE_CASE)
    private val RULE = Regex("([^{}@;]+)\\{([^{}]*)\\}")
    private val URL = Regex("url\\(\\s*([^)]+?)\\s*\\)", RegexOption.IGNORE_CASE)

    /** Where the body's family is looked for, in order: the page, then paragraphs. */
    private val BODY_SELECTORS = listOf(setOf("body", "html", ":root"), setOf("p"))
}
