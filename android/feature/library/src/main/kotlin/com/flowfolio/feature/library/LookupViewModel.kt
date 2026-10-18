package com.flowfolio.feature.library

import androidx.lifecycle.ViewModel
import com.flowfolio.core.data.BookLookup
import com.flowfolio.core.data.Found
import com.flowfolio.core.model.Book
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Edit details' "Look up online" (ADR-0017): only when the reader taps it. */
@HiltViewModel
class LookupViewModel @Inject constructor(private val lookup: BookLookup) : ViewModel() {
    /** What Open Library has for [book] as typed so far, or null. */
    suspend fun find(book: Book): Found? = lookup.find(book)
}
