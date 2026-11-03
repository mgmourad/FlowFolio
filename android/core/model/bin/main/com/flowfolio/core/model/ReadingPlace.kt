package com.flowfolio.core.model

/**
 * Where in a book something happened: the PDF [page], and [locator] (`chapter:offset`) for the reflowed view, as
 * annotations store it (ADR-0040). Carried together so a lookup, a note or a recording can take the reader back
 * to the place it came from (owner, M4b).
 */
data class ReadingPlace(val page: Int? = null, val locator: String? = null) {
    val known: Boolean get() = page != null && page >= 0

    companion object {
        val Unknown = ReadingPlace()
    }
}
