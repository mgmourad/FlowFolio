package com.flowfolio.core.epub

/** What a book declares about itself: its package document and navigation, with every path resolved in the book. */
data class EpubPackage(
    /** Book-internal path of the package document (`OEBPS/content.opf`). */
    val packagePath: String,
    val metadata: EpubMetadata,
    val manifest: Map<String, ManifestItem>,
    /** Reading order. */
    val spine: List<SpineItem>,
    val toc: List<NavPoint>,
    /** Printed page numbers (`page-list`), in reading order. */
    val pageList: List<NavPoint>,
    val landmarks: List<NavPoint>,
    /** `page-progression-direction="rtl"`: pages turn right to left. */
    val rightToLeft: Boolean,
    /** `rendition:layout` is `pre-paginated`: every spine item is one fixed page. */
    val fixedLayout: Boolean,
    /** The cover image's path, when the book names one (or its first page shows one). */
    val coverPath: String?
)

data class EpubMetadata(
    val title: String?,
    val creators: List<String>,
    val language: String?,
    val identifiers: List<String>,
    val publisher: String?,
    /** Plain text: markup in the description is removed. */
    val description: String?,
    val date: String?,
    /** The identifier the package names as its `unique-identifier` (the font obfuscation key's source). */
    val uniqueIdentifier: String? = null
) {
    /** The first identifier that is an ISBN: 13 digits starting 978 or 979, or 10 characters labelled as an ISBN. */
    val isbn: String?
        get() = identifiers.firstNotNullOfOrNull { id ->
            val chars = id.uppercase().filter { it.isDigit() || it == 'X' }
            when {
                chars.length == ISBN13 && chars.all(Char::isDigit) &&
                    (chars.startsWith("978") || chars.startsWith("979")) -> chars

                chars.length == ISBN10 && chars.dropLast(
                    1
                ).all(Char::isDigit) && id.contains("isbn", ignoreCase = true) -> chars

                else -> null
            }
        }

    private companion object {
        const val ISBN10 = 10
        const val ISBN13 = 13
    }
}

data class ManifestItem(val id: String, val path: String, val mediaType: String, val properties: Set<String>)

data class SpineItem(val path: String, val mediaType: String, val linear: Boolean)

/** An entry of the contents, page list or landmarks: [href] is a book-internal path, maybe with a `#fragment`. */
data class NavPoint(val label: String, val href: String, val children: List<NavPoint> = emptyList())
