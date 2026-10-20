package com.flowfolio.core.epub

import android.util.Xml
import java.io.IOException
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

/**
 * Reads an EPUB 2 or 3 book's package document and navigation (ADR-0020). XML is parsed without DOCTYPE processing, so
 * entities are never expanded and nothing outside the book is loaded.
 */
object EpubParser {
    /** @throws EpubException when the book is not an EPUB, is DRM-protected or its package cannot be read. */
    fun parse(archive: EpubArchive): EpubPackage {
        if (!archive.contains(CONTAINER)) refuse(EpubException.Reason.NOT_EPUB)
        archive.read(ENCRYPTION)?.let { if (encryptsContent(it)) refuse(EpubException.Reason.DRM) }
        val pkg = try {
            val packagePath = archive.read(CONTAINER)?.let(::rootfile) ?: refuse(EpubException.Reason.DAMAGED)
            val opf = archive.read(packagePath) ?: refuse(EpubException.Reason.DAMAGED)
            PackageReader(packagePath).apply { read(opf) }
        } catch (e: XmlPullParserException) {
            refuse(EpubException.Reason.DAMAGED, e)
        } catch (e: IOException) {
            refuse(EpubException.Reason.DAMAGED, e)
        }
        val spine = pkg.spine()
        if (spine.isEmpty()) refuse(EpubException.Reason.DAMAGED)
        val nav = navigation(archive, pkg)
        return EpubPackage(
            packagePath = pkg.path,
            metadata = pkg.metadata(),
            manifest = pkg.manifest,
            spine = spine,
            toc = nav.toc,
            pageList = nav.pageList,
            landmarks = nav.landmarks,
            rightToLeft = pkg.rightToLeft,
            fixedLayout = pkg.fixedLayout,
            coverPath = cover(archive, pkg, spine)
        )
    }

    private const val CONTAINER = "META-INF/container.xml"
    private const val ENCRYPTION = "META-INF/encryption.xml"
    private const val PACKAGE_TYPE = "application/oebps-package+xml"
    private const val NCX_TYPE = "application/x-dtbncx+xml"
    private const val IDPF_FONT = "http://www.idpf.org/2008/embedding"
    private const val ADOBE_FONT = "http://ns.adobe.com/pdf/enc#RC"
    private val IMAGE_TYPES = setOf("image/jpeg", "image/png", "image/gif", "image/webp")
    private val IMG_SRC =
        Regex("<(?:img|image)\\b[^>]*?(?:src|xlink:href)\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)

    private fun refuse(reason: EpubException.Reason, cause: Throwable? = null): Nothing =
        throw EpubException(reason, cause)

    private fun encryptsContent(xml: ByteArray): Boolean = try {
        val p = xmlParser(xml)
        generateSequence { if (p.next() == XmlPullParser.END_DOCUMENT) null else p }
            .filter { it.eventType == XmlPullParser.START_TAG && it.name == "EncryptionMethod" }
            .any { it.getAttributeValue(null, "Algorithm").let { a -> a != IDPF_FONT && a != ADOBE_FONT } }
    } catch (_: XmlPullParserException) {
        true // an encryption file we cannot read is treated as DRM rather than risk showing ciphertext
    }

    /** The first `rootfile` that is a package document (or does not say what it is). */
    private fun rootfile(container: ByteArray): String? {
        val p = xmlParser(container)
        var found: String? = null
        while (found == null && p.next() != XmlPullParser.END_DOCUMENT) {
            if (p.eventType == XmlPullParser.START_TAG && p.name == "rootfile") {
                val type = p.getAttributeValue(null, "media-type")
                if (type == null || type == PACKAGE_TYPE) found = p.getAttributeValue(null, "full-path").orEmpty()
            }
        }
        return found?.let(EpubArchive::normalize)
    }

    private class Navigation(val toc: List<NavPoint>, val pageList: List<NavPoint>, val landmarks: List<NavPoint>)

    private val noNavigation = Navigation(emptyList(), emptyList(), emptyList())

    /** The EPUB 3 nav document when there is one, else the EPUB 2 NCX. Broken navigation is left empty. */
    private fun navigation(archive: EpubArchive, pkg: PackageReader): Navigation {
        val nav = pkg.manifest.values.firstOrNull { "nav" in it.properties }?.let { item ->
            val bytes = archive.read(item.path)
            bytes?.let { runCatching { NavReader(item.path).read(it) }.getOrNull() }
        }?.let { Navigation(it["toc"].orEmpty(), it["page-list"].orEmpty(), it["landmarks"].orEmpty()) }
        val ncx = pkg.ncxId?.let(pkg.manifest::get) ?: pkg.manifest.values.firstOrNull { it.mediaType == NCX_TYPE }
        return nav ?: ncx?.let { item ->
            val bytes = archive.read(item.path)
            bytes?.let { runCatching { NcxReader(item.path).read(it) }.getOrNull() }
        }?.let { (toc, pages) -> Navigation(toc, pages, emptyList()) } ?: noNavigation
    }

    /** EPUB 3 `cover-image`, then EPUB 2 `<meta name="cover">`, then the first image on the first page. */
    private fun cover(archive: EpubArchive, pkg: PackageReader, spine: List<SpineItem>): String? {
        val items = pkg.manifest.values
        val named = items.firstOrNull { "cover-image" in it.properties } ?: pkg.coverId?.let(pkg.manifest::get)
        return named?.takeIf { it.mediaType in IMAGE_TYPES }?.path ?: spine.firstOrNull()?.let { first ->
            archive.read(first.path)?.decodeToString()
                ?.let { IMG_SRC.find(it)?.groupValues?.get(1) }
                ?.let { EpubArchive.resolve(first.path, it)?.substringBefore('#') }
                ?.takeIf { path -> items.any { it.path == path && it.mediaType in IMAGE_TYPES } }
        }
    }
}

/** A pull parser with namespaces, without DOCTYPE processing; [relaxed] tolerates HTML entities and sloppy markup. */
internal fun xmlParser(bytes: ByteArray, relaxed: Boolean = false): XmlPullParser = Xml.newPullParser().apply {
    setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
    setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
    if (relaxed) runCatching { setFeature("http://xmlpull.org/v1/doc/features.html#relaxed", true) }
    setInput(bytes.inputStream(), null)
}

private val SPACES = Regex("\\s+")

internal fun String.collapsed() = replace(SPACES, " ").trim()
