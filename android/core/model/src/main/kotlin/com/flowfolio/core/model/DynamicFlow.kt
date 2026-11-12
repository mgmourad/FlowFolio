package com.flowfolio.core.model

/** Where a book's Dynamic Flow stands (ADR-0045). */
sealed interface FlowStatus {
    /** Not converted, and not being converted. */
    data object None : FlowStatus

    /** Waiting for another book to finish. */
    data object Queued : FlowStatus

    /**
     * Being converted: [fraction] done, 0 to 1. [paused] says why it is waiting (the phone is hot, the battery is low)
     * or is null while it runs. [readableChapters] of [totalChapters] are finished and readable (progressive delivery);
     * both are 0 before the first chapter lands.
     */
    data class Converting(
        val fraction: Float,
        val paused: FlowPause? = null,
        val readableChapters: Int = 0,
        val totalChapters: Int = 0
    ) : FlowStatus

    /** Converted: the book can be read in Dynamic Flow. */
    data object Ready : FlowStatus

    /** The PDF could not be converted ([reason]: a password, a damaged file, no pages, or anything else). */
    data class Failed(val reason: FlowFailure) : FlowStatus
}

/** Why a conversion is paused: the phone is [HOT], the [BATTERY] is low, or it is [WAITING] for a quicker book. */
enum class FlowPause { HOT, BATTERY, WAITING }

/**
 * Why a conversion ended without a book: [INTERRUPTED] means it was stopped part-way (Android ended the conversion
 * process or its time ran out), not that the PDF cannot be reflowed; opening the book again tries again.
 */
enum class FlowFailure { PASSWORD, DAMAGED, NO_PAGES, MISSING, INTERRUPTED, OTHER }

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
