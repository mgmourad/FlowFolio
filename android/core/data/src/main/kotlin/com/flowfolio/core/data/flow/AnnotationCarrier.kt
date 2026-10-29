package com.flowfolio.core.data.flow

/**
 * "Convert the changes" (ADR-0045): brings a PDF book's highlights, notes and bookmarks from the view being left into
 * the other one. The reader implements it (it lays out both views); [DynamicFlow] calls it when a book's view is
 * switched, from the reader or the library.
 */
interface AnnotationCarrier {
    /** Carries [bookId]'s items out of Dynamic Flow ([fromFlow]) or out of the PDF, in the background. */
    fun carry(bookId: String, fromFlow: Boolean)

    /**
     * [bookId]'s Dynamic Flow was converted again (a newer converter): its highlights are found again by their words
     * near where they were, since the new text's offsets differ.
     */
    fun reanchor(bookId: String)

    /** Carries nothing: for tests. */
    object None : AnnotationCarrier {
        override fun carry(bookId: String, fromFlow: Boolean) = Unit

        override fun reanchor(bookId: String) = Unit
    }
}
