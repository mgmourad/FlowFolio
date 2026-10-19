package com.flowfolio.feature.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfolio.core.data.SettingsRepository
import com.flowfolio.core.data.TranslationRepository
import com.flowfolio.core.model.Translation
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where a translation is. */
enum class TranslateStatus { DETECTING, CHOOSE, DOWNLOADING, TRANSLATING, DONE, FAILED }

data class TranslateUiState(
    val source: String = "",
    /** BCP-47 codes; [from] null while detecting. */
    val from: String? = null,
    val to: String = "en",
    /** Whether [from] was detected (shown as "Detected"). */
    val detected: Boolean = true,
    val result: String? = null,
    val status: TranslateStatus = TranslateStatus.DETECTING
)

/**
 * The reader's Translate panel (owner, 2026-10-18; ADR-0018): detects the passage's language, translates it on the
 * phone into the language last chosen (at first the phone's), and keeps a history.
 */
@HiltViewModel
class TranslateViewModel @Inject constructor(
    saved: SavedStateHandle,
    private val repository: TranslationRepository,
    private val settings: SettingsRepository
) : ViewModel() {
    private val bookId: String? = saved[ReaderViewModel.ARG_BOOK_ID]
    private val _state = MutableStateFlow(TranslateUiState())
    val state: StateFlow<TranslateUiState> = _state.asStateFlow()

    val history: StateFlow<List<Translation>> =
        repository.history().stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), emptyList())

    /** The languages to choose from, as codes. */
    val languages: List<String> get() = repository.translator.languages

    private var job: Job? = null

    /** Opens the panel on [text]: detect, then translate into the last language chosen. */
    fun start(text: String) {
        job?.cancel()
        job = viewModelScope.launch {
            _state.value = TranslateUiState(source = text, status = TranslateStatus.DETECTING)
            val detected = runCatching { repository.translator.detect(text) }.getOrNull()
            val phone = Locale.getDefault().language
            val preferred = settings.settings.first().translateTo ?: phone
            // Reading in the target language already: translate into English, or from English into the phone's.
            val to = when {
                detected != preferred -> preferred
                detected != ENGLISH -> ENGLISH
                else -> phone.takeIf { it != ENGLISH } ?: ENGLISH
            }
            _state.update { it.copy(from = detected ?: phone, to = to, detected = detected != null) }
            run()
        }
    }

    fun setFrom(language: String) {
        _state.update { it.copy(from = language, detected = false) }
        restart()
    }

    fun setTo(language: String) {
        _state.update { it.copy(to = language) }
        viewModelScope.launch { settings.setTranslateTo(language) }
        restart()
    }

    fun swap() {
        val s = _state.value
        val from = s.from ?: return
        _state.update { it.copy(from = s.to, to = from, detected = false, source = s.result ?: s.source) }
        restart()
    }

    /** Shows an earlier translation from the history. */
    fun show(t: Translation) {
        job?.cancel()
        _state.value =
            TranslateUiState(
                t.source,
                t.from,
                t.to,
                detected = false,
                result = t.translated,
                status = TranslateStatus.DONE
            )
    }

    /** Empties the translation history. */
    fun clearHistory() {
        viewModelScope.launch { repository.clearHistory() }
    }

    private fun restart() {
        job?.cancel()
        job = viewModelScope.launch { run() }
    }

    private suspend fun run() {
        val s = _state.value
        val from = s.from ?: return
        if (from == s.to) {
            // Already in that language: ask for the language to translate into.
            _state.update { it.copy(result = null, status = TranslateStatus.CHOOSE) }
            return
        }
        val ready = runCatching {
            repository.translator.isDownloaded(from) && repository.translator.isDownloaded(s.to)
        }.getOrDefault(false)
        _state.update {
            it.copy(result = null, status = if (ready) TranslateStatus.TRANSLATING else TranslateStatus.DOWNLOADING)
        }
        val done = runCatching { repository.translate(s.source, from, s.to, bookId) }.getOrNull()
        _state.update {
            if (done != null) {
                it.copy(result = done.translated, status = TranslateStatus.DONE)
            } else {
                it.copy(status = TranslateStatus.FAILED)
            }
        }
    }

    private companion object {
        const val ENGLISH = "en"
        const val STOP_MS = 5_000L
    }
}
