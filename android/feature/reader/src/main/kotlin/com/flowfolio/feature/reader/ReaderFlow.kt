package com.flowfolio.feature.reader

import com.flowfolio.core.data.ReaderRepository
import com.flowfolio.core.data.flow.DynamicFlow
import com.flowfolio.core.model.BookFormat
import com.flowfolio.core.model.FlowStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The open book's Dynamic Flow (ADR-0045): which view the reader wants, following its conversion, and switching between
 * the PDF and Dynamic Flow. The reader ([Host]) opens and closes the book; this decides when, and from which page.
 */
internal class ReaderFlow(
    private val bookId: String,
    private val scope: CoroutineScope,
    private val repository: ReaderRepository,
    private val dynamicFlow: DynamicFlow,
    private val host: Host
) {
    /** What the reader does for Dynamic Flow. */
    interface Host {
        /** The page the reader is on. */
        val anchorPage: Int

        /** A book is open and showing (not loading, failed or asking for a password). */
        val showing: Boolean

        /** Closes the book and opens it again in Dynamic Flow (true) or as the PDF. */
        fun reopen(view: Boolean)

        /** Whether PDFs open in Dynamic Flow when the book has no choice of its own (Settings). */
        suspend fun flowOnOpen(): Boolean

        /** Saves Dynamic Flow's latest place, and the PDF page it is in, before leaving it. */
        suspend fun savePlace()
    }

    private val _state = MutableStateFlow(FlowState())

    /** The book's Dynamic Flow: the "Aa" sheet's switch and its progress ring, the bar by the page number. */
    val state: StateFlow<FlowState> = _state.asStateFlow()

    /** Whether the reader shows the book's Dynamic Flow now. */
    val reading: Boolean get() = _state.value.reading

    /** A PDF page to open Dynamic Flow at (a switch from the PDF), used once by the next flow opening. */
    private var sourcePage: Int? = null
    private var watching: Job? = null

    /**
     * Whether to open the book's Dynamic Flow: the reader's choice [view] when switching, else the book's own, else the
     * "upon opening" setting (which then becomes the book's choice). True when it is wanted and converted; when it is
     * wanted but not converted yet, its conversion starts and the PDF opens meanwhile.
     */
    suspend fun opensFlow(view: Boolean?): Boolean {
        val book = repository.book(bookId)?.takeIf { it.format == BookFormat.PDF } ?: return false
        val wants = view ?: book.wantsFlow(host.flowOnOpen())
        if (wants && book.flowMode == null) dynamicFlow.setFlowMode(bookId, true)
        _state.update { it.copy(available = true, wanted = wants) }
        watch()
        val converted = wants && dynamicFlow.readableBundle(bookId) != null
        if (wants && !converted) dynamicFlow.convert(bookId)
        return converted
    }

    /** The reader now shows Dynamic Flow ([reading]) or not (the PDF, or nothing while switching). */
    fun shown(reading: Boolean) = _state.update { it.copy(reading = reading) }

    /** The PDF page to open Dynamic Flow at after a switch from the PDF, once. */
    fun takeSourcePage(): Int? = sourcePage.also { sourcePage = null }

    /** Dynamic Flow's place, and the PDF page it is in (the library's progress); null keeps the PDF page. */
    suspend fun savePlace(locator: String, pdfPage: Int?) = dynamicFlow.saveFlowPlace(bookId, locator, pdfPage)

    /**
     * Switches the book's view (the "Aa" sheet's switch, after its notice): to Dynamic Flow at the PDF's page, or back
     * to the PDF at the page Dynamic Flow is on. Turning it on before the book is converted starts the conversion and
     * stays on the PDF until it is done. With [carry], the highlights, notes and bookmarks made in the view being left
     * are brought into the other one in the background.
     */
    fun switch(on: Boolean, carry: Boolean) {
        scope.launch {
            val now = _state.value
            if (!now.available) return@launch
            // Into a Dynamic Flow that has nothing readable yet: it opens once the first chapter is.
            val later = on && !now.reading && dynamicFlow.readableBundle(bookId) == null
            dynamicFlow.setFlowMode(bookId, on, carry)
            _state.update { it.copy(wanted = on) }
            if (on == now.reading || later) return@launch
            if (on) sourcePage = host.anchorPage else host.savePlace()
            host.reopen(on)
        }
    }

    /** Follows the book's conversion: opens its first chapter as soon as it is readable, and the whole book when done. */
    private fun watch() {
        if (watching != null) return
        watching = scope.launch {
            dynamicFlow.status(bookId).collect { status ->
                val readingFlow = _state.value.reading
                val wanted = _state.value.wanted
                _state.update { it.copy(status = status) }
                if (!wanted || !host.showing) return@collect
                // Open the partial when the first chapter lands (still on the PDF), or reopen to the full book when
                // conversion finishes (from the PDF or from the partial).
                val shouldOpen = when (status) {
                    is FlowStatus.Converting -> status.readableChapters > 0 && !readingFlow
                    FlowStatus.Ready -> true
                    else -> false
                }
                if (shouldOpen) {
                    if (readingFlow) host.savePlace() else sourcePage = host.anchorPage
                    host.reopen(true)
                }
            }
        }
    }
}
