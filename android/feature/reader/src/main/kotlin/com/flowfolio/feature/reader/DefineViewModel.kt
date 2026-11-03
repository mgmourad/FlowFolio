package com.flowfolio.feature.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfolio.core.data.DictionaryRepository
import com.flowfolio.core.model.WordDefinition
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class DefineStatus { WORKING, DONE, NOT_FOUND }

data class DefineRequest(val word: String, val page: Int, val locator: String? = null)

data class DefineUiState(
    val word: String = "",
    val entry: WordDefinition? = null,
    val status: DefineStatus = DefineStatus.WORKING
)

/**
 * Looks up a selected single word in the offline English Wiktionary dictionary (owner, M3T3; ADR-0046) and saves the
 * definition in Room (`definitions`) so it appears on the Dictionary tab and in global search.
 */
@HiltViewModel
class DefineViewModel @Inject constructor(saved: SavedStateHandle, private val dictionary: DictionaryRepository) :
    ViewModel() {
    private val bookId: String? = saved[ReaderViewModel.ARG_BOOK_ID]
    private val _state = MutableStateFlow(DefineUiState())
    val state: StateFlow<DefineUiState> = _state.asStateFlow()

    private var job: Job? = null

    fun define(request: DefineRequest) {
        job?.cancel()
        job = viewModelScope.launch {
            _state.value = DefineUiState(word = request.word.trim(), status = DefineStatus.WORKING)
            val result = runCatching {
                dictionary.define(
                    word = request.word,
                    bookId = bookId,
                    page = request.page,
                    locator = request.locator
                )
            }.getOrNull()
            _state.value = if (result != null) {
                DefineUiState(word = result.word, entry = result, status = DefineStatus.DONE)
            } else {
                DefineUiState(word = request.word.trim(), entry = null, status = DefineStatus.NOT_FOUND)
            }
        }
    }
}

/**
 * What the reader asked to have translated, and where from, so the saved translation can take them back there
 * (owner, M4b). The twin of [DefineRequest].
 */
data class TranslateRequest(val text: String, val page: Int? = null, val locator: String? = null)
