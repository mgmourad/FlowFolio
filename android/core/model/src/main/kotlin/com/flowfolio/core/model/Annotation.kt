package com.flowfolio.core.model

/**
 * A highlight on a page, with an optional [note]. [text] is the selected text; [rects] are where it lies on the page,
 * as fractions of the page (0..1 from the top left), one per line; [start] and [end] are its first and last character
 * on the page (PDFium's text order), which sort a page's highlights.
 */
data class Annotation(
    val id: Long,
    val bookId: String,
    val page: Int,
    /** The page's printed label when it was highlighted ("214"), or null when the PDF has none. */
    val label: String?,
    val start: Int,
    val end: Int,
    val text: String,
    val rects: List<PageRect>,
    val color: HighlightColor,
    val note: String?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)

/**
 * The passage a new highlight covers: on [page] (printed [label], if any), characters [range] of the page's text,
 * reading [text], lying in [rects] (fractions of the page).
 */
data class Passage(val page: Int, val label: String?, val range: IntRange, val text: String, val rects: List<PageRect>)

/** A rectangle on a page, as fractions of the page's width and height (0..1, top left origin). */
data class PageRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

/** The highlighter colours (owner, 2026-10-18: four or five choices); the app picks shades readable on every paper. */
enum class HighlightColor { YELLOW, GREEN, BLUE, PINK, PURPLE }

/**
 * Text to share a highlight (owner, 2026-10-18): the passage in quotation marks, then on its own line where it
 * comes from, for example "“Passive transport never requires ATP.”" then "— Cellular Biology, Amara Okafor, p. 216".
 */
fun shareQuote(text: String, title: String, author: String?, page: String): String =
    "“${text.trim()}”\n— " + listOfNotNull(title, author, "p. $page").joinToString(", ")
