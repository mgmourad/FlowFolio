package com.flowfolio.core.model

/** A passage translated in the reader: kept as history (newest first) so earlier translations can be read again. */
data class Translation(
    val id: Long,
    val source: String,
    val translated: String,
    /** BCP-47 language codes ("es", "en"). */
    val from: String,
    val to: String,
    val bookId: String?,
    val createdAtMillis: Long
)
