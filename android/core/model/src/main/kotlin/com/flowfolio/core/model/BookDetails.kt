package com.flowfolio.core.model

/**
 * What a book is about, as the reader enters it (or, from slice 7, as the online lookup suggests). Every field is
 * optional; blank means unknown. Released is free text ("August 2025", "2016"), since books give dates loosely.
 */
data class BookDetails(
    val subtitle: String? = null,
    val edition: String? = null,
    val publisher: String? = null,
    val isbn: String? = null,
    val description: String? = null,
    val language: String? = null,
    val released: String? = null,
    /** The author's role or field ("Professor of Molecular Biology"). */
    val authorRole: String? = null,
    val authorBio: String? = null,
    val type: BookType? = null,
    /** Subjects or categories, most telling first (from the online lookup). */
    val subjects: List<String> = emptyList(),
    /** Pages in the printed edition (the file's page count can differ). */
    val printPages: Int? = null,
    val series: String? = null,
    /** Average reader rating out of 5, and how many ratings it averages. */
    val rating: Float? = null,
    val ratingsCount: Int? = null
)

/** What kind of book it is (the mock's library chips: Textbooks, Novels). Null until the reader says. */
enum class BookType { TEXTBOOK, NOVEL, NONFICTION, OTHER }
