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
    val type: BookType? = null
)

/** What kind of book it is (the mock's library chips: Textbooks, Novels). Null until the reader says. */
enum class BookType { TEXTBOOK, NOVEL, NONFICTION, OTHER }
