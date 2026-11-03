package com.flowfolio.feature.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfolio.core.data.AnnotationRepository
import com.flowfolio.core.data.LibraryRepository
import com.flowfolio.core.data.VoicePlayer
import com.flowfolio.core.data.VoiceRecorder
import com.flowfolio.core.data.flow.ReadingViews
import com.flowfolio.core.model.Annotation
import com.flowfolio.core.model.Book
import com.flowfolio.core.model.HighlightColor
import com.flowfolio.core.model.Passage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The open book's highlights and notes, and the book itself (for sharing a quote). */
@HiltViewModel
class ReaderAnnotationsViewModel @Inject constructor(
    saved: SavedStateHandle,
    private val annotations: AnnotationRepository,
    private val recorder: VoiceRecorder,
    private val voicePlayer: VoicePlayer,
    library: LibraryRepository,
    views: ReadingViews
) : ViewModel() {
    private val bookId: String = checkNotNull(saved[ReaderViewModel.ARG_BOOK_ID]) { "reader needs a bookId" }

    /** The highlights of the view the book is read in (ADR-0045: the PDF's and its Dynamic Flow's are kept apart). */
    val all: StateFlow<List<Annotation>> = views.annotations(
        annotations.observe(bookId)
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), emptyList())

    val book: StateFlow<Book?> =
        library.observeBook(bookId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), null)

    fun highlight(passage: Passage, color: HighlightColor, note: String? = null) = viewModelScope.launch {
        annotations.add(bookId, passage, color, note)
    }

    /**
     * Saves [passage] as a highlight and hands back its id (owner, M5): a VoiceMark needs a highlight to hang on,
     * and a selection has none until it is kept.
     */
    fun highlightThen(passage: Passage, color: HighlightColor, onSaved: (Long) -> Unit) = viewModelScope.launch {
        onSaved(annotations.add(bookId, passage, color))
    }

    fun recolour(id: Long, color: HighlightColor) = viewModelScope.launch { annotations.setColor(id, color) }

    fun setNote(id: Long, note: String?) = viewModelScope.launch { annotations.setNote(id, note) }

    // ---- Voice notes (owner, M5) ----

    /** The loudnesses heard so far while recording, for the live waveform. */
    val voiceLevels: StateFlow<List<Float>> = recorder.levels

    /** The path of the note playing now, or null. */
    val voicePlaying: StateFlow<String?> = voicePlayer.playing

    private val _recording = MutableStateFlow(false)

    /**
     * Whether a recording is running, as state the pill can follow. The recorder's own flag is a plain property,
     * so reading it told the UI nothing and left the clock frozen at zero (owner, M5 round).
     */
    val recording: StateFlow<Boolean> = _recording.asStateFlow()

    private val _recordedMillis = MutableStateFlow(0L)

    /** How long the recording has been running, ticked with the waveform. */
    val recordedMillis: StateFlow<Long> = _recordedMillis.asStateFlow()

    /** How far playback has got, in milliseconds. */
    val playbackMillis: Int get() = voicePlayer.positionMillis

    /**
     * Starts recording a note for [page] of this book, ticking the waveform while it runs. Returns false when the
     * recorder would not start, which is what a refused microphone looks like from here.
     */
    fun startVoice(bookTitle: String, page: Int): Boolean {
        voicePlayer.stop()
        if (!recorder.start(bookTitle, page)) return false
        _recording.value = true
        _recordedMillis.value = 0L
        val startedAt = System.currentTimeMillis()
        viewModelScope.launch {
            while (recorder.recording) {
                recorder.sample()
                _recordedMillis.value = System.currentTimeMillis() - startedAt
                delay(SAMPLE_MS)
            }
        }
        return true
    }

    /** Keeps the recording against [annotationId]; a recording too short to be meant is thrown away instead. */
    fun stopVoice(annotationId: Long) = viewModelScope.launch {
        val note = recorder.stop()
        _recording.value = false
        _recordedMillis.value = 0L
        if (note != null) annotations.setVoice(annotationId, note)
    }

    /** Throws away whatever is being recorded. */
    fun cancelVoice() {
        recorder.cancel()
        _recording.value = false
        _recordedMillis.value = 0L
    }

    /** Takes the voice note off a highlight and deletes its file. */
    fun removeVoice(annotationId: Long) = viewModelScope.launch {
        voicePlayer.stop()
        annotations.setVoice(annotationId, null)
    }

    /** Plays a note, or pauses the one already playing. */
    fun playVoice(path: String) {
        if (voicePlayer.playing.value == path) voicePlayer.pause() else voicePlayer.play(path)
    }

    fun stopVoicePlayback() = voicePlayer.stop()

    override fun onCleared() {
        recorder.cancel()
        voicePlayer.stop()
    }

    fun remove(id: Long) = viewModelScope.launch { annotations.remove(id) }

    /** EPUB highlights whose first page moved in a new layout (ADR-0040). */
    internal fun syncPages(pages: Map<Long, Int>) = viewModelScope.launch { annotations.setPages(pages) }

    private companion object {
        /** How often the waveform takes a reading while recording. */
        const val SAMPLE_MS = 90L

        const val STOP_MS = 5_000L
    }
}
