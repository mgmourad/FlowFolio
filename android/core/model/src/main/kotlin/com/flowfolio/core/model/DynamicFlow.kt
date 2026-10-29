package com.flowfolio.core.model

/** Where a book's Dynamic Flow stands (ADR-0045). */
sealed interface FlowStatus {
    /** Not converted, and not being converted. */
    data object None : FlowStatus

    /** Waiting for another book to finish. */
    data object Queued : FlowStatus

    /**
     * Being converted: [fraction] done, 0 to 1. [paused] says why it is waiting (the phone is hot, the battery is low)
     * or is null while it runs.
     */
    data class Converting(val fraction: Float, val paused: FlowPause? = null) : FlowStatus

    /** Converted: the book can be read in Dynamic Flow. */
    data object Ready : FlowStatus

    /** The PDF could not be converted ([reason]: a password, a damaged file, no pages, or anything else). */
    data class Failed(val reason: FlowFailure) : FlowStatus
}

enum class FlowPause { HOT, BATTERY }

enum class FlowFailure { PASSWORD, DAMAGED, NO_PAGES, MISSING, OTHER }

/** Something about Dynamic Flow to tell the reader in the app (the phone gets its own notification). */
sealed interface FlowNotice {
    val bookId: String
    val title: String

    data class Started(override val bookId: String, override val title: String) : FlowNotice

    data class Done(override val bookId: String, override val title: String) : FlowNotice

    data class Failed(override val bookId: String, override val title: String, val reason: FlowFailure) : FlowNotice

    /** Highlights, notes and bookmarks carried into the other view: [carried] of [total] found their place. */
    data class CarryStarted(override val bookId: String, override val title: String) : FlowNotice

    data class Carried(override val bookId: String, override val title: String, val carried: Int, val total: Int) :
        FlowNotice
}
