package com.flowfolio.core.model

/**
 * One entry in a document's outline (table of contents), in document order.
 * [pageIndex] is 0-based, or -1 when the entry has no resolvable destination.
 */
data class OutlineEntry(val depth: Int, val pageIndex: Int, val title: String)
