package com.flowfolio.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfolio.core.data.ImportResult
import com.flowfolio.core.data.LibraryRepository
import com.flowfolio.core.data.RelinkResult
import com.flowfolio.core.data.SettingsRepository
import com.flowfolio.core.model.Availability
import com.flowfolio.core.model.Book
import com.flowfolio.core.model.BookStorage
import com.flowfolio.core.model.LibrarySort
import com.flowfolio.core.model.StorageMode
import com.flowfolio.core.model.UserSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LibraryItem(val book: Book, val availability: Availability = Availability.AVAILABLE)

data class LibraryUiState(
    val items: List<LibraryItem> = emptyList(),
    val loading: Boolean = true,
    val sort: LibrarySort = LibrarySort.RECENT,
    val importing: Boolean = false,
    val dialog: LibraryDialog? = null
)

sealed interface LibraryDialog {
    /** First import ever, or "ask every time": choose copy or read-in-place, with both disclaimers. */
    data class ChooseStorage(
        val uri: String,
        val initial: StorageMode,
        val firstTime: Boolean,
        val sizeBytes: Long? = null
    ) : LibraryDialog

    data class LinkNotPossible(val uri: String) : LibraryDialog

    data class ConfirmDelete(val book: Book) : LibraryDialog
}

sealed interface LibraryMessage {
    data class Imported(val title: String) : LibraryMessage

    data class Duplicate(val title: String) : LibraryMessage

    data object NotAPdf : LibraryMessage

    data object Unreadable : LibraryMessage

    data class NotEnoughSpace(val bytes: Long) : LibraryMessage

    data object Relinked : LibraryMessage

    data object RelinkDifferentFile : LibraryMessage

    data object RelinkFailed : LibraryMessage

    data class Deleted(val title: String) : LibraryMessage
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val library: LibraryRepository,
    private val settingsRepo: SettingsRepository
) : ViewModel() {
    private val transient = MutableStateFlow(Transient())
    private val availability = MutableStateFlow<Map<String, Availability>>(emptyMap())
    private val messageChannel = Channel<LibraryMessage>(Channel.BUFFERED)
    val messages: Flow<LibraryMessage> = messageChannel.receiveAsFlow()

    private val settings: StateFlow<UserSettings?> = settingsRepo.settings.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        null
    )

    private val books: Flow<List<Book>> = settingsRepo.settings
        .map { it.librarySort }
        .distinctUntilChanged()
        .flatMapLatest { library.observeBooks(it) }
        .onEach { list -> checkAvailability(list) }

    val uiState: StateFlow<LibraryUiState> = combine(books, settingsRepo.settings, availability, transient) {
            list,
            s,
            avail,
            t
        ->
        LibraryUiState(
            items = list.map { LibraryItem(it, avail[it.id] ?: Availability.AVAILABLE) },
            loading = false,
            sort = s.librarySort,
            importing = t.importing,
            dialog = t.dialog
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LibraryUiState())

    /** A file chosen with the in-app picker. */
    fun onFilePicked(uri: String) = viewModelScope.launch {
        val s = settings.value ?: settingsRepo.settings.first()
        when {
            !s.storageNoticeSeen -> showDialog(
                LibraryDialog.ChooseStorage(uri, s.storageMode, true, library.sizeOf(uri))
            )

            s.askStorageEachImport -> showDialog(
                LibraryDialog.ChooseStorage(uri, s.storageMode, false, library.sizeOf(uri))
            )

            else -> import(uri, s.storageMode)
        }
    }

    /** A file handed over by another app ("Open with", share sheet): always copied. */
    fun onFileShared(uri: String) {
        viewModelScope.launch { import(uri, StorageMode.COPY, fromShare = true) }
    }

    fun onStorageChosen(dialog: LibraryDialog.ChooseStorage, mode: StorageMode) = viewModelScope.launch {
        dismissDialog()
        if (dialog.firstTime) {
            settingsRepo.setStorageMode(mode)
            settingsRepo.setStorageNoticeSeen()
        }
        import(dialog.uri, mode)
    }

    fun onCopyInstead(uri: String) = viewModelScope.launch {
        dismissDialog()
        import(uri, StorageMode.COPY)
    }

    fun onRequestDelete(book: Book) = showDialog(LibraryDialog.ConfirmDelete(book))

    fun onConfirmDelete(book: Book) = viewModelScope.launch {
        dismissDialog()
        library.delete(book.id)
        messageChannel.send(LibraryMessage.Deleted(book.title))
    }

    fun onRelinkPicked(bookId: String, uri: String) = viewModelScope.launch {
        val message = when (library.relink(bookId, uri)) {
            RelinkResult.Relinked -> LibraryMessage.Relinked
            RelinkResult.DifferentFile -> LibraryMessage.RelinkDifferentFile
            RelinkResult.Unreadable, RelinkResult.LinkNotPossible -> LibraryMessage.RelinkFailed
        }
        if (message == LibraryMessage.Relinked) availability.update { it + (bookId to Availability.AVAILABLE) }
        messageChannel.send(message)
    }

    fun onSortChange(sort: LibrarySort) = viewModelScope.launch { settingsRepo.setLibrarySort(sort) }

    fun onBookOpened(bookId: String) = viewModelScope.launch { library.markOpened(bookId) }

    fun dismissDialog() = transient.update { it.copy(dialog = null) }

    private fun showDialog(dialog: LibraryDialog) = transient.update { it.copy(dialog = dialog) }

    private suspend fun import(uri: String, mode: StorageMode, fromShare: Boolean = false) {
        transient.update { it.copy(importing = true) }
        try {
            when (val r = library.import(uri, mode, fromShare)) {
                is ImportResult.Imported -> messageChannel.send(LibraryMessage.Imported(r.book.title))
                is ImportResult.Duplicate -> messageChannel.send(LibraryMessage.Duplicate(r.existing.title))
                ImportResult.NotAPdf -> messageChannel.send(LibraryMessage.NotAPdf)
                ImportResult.Unreadable -> messageChannel.send(LibraryMessage.Unreadable)
                is ImportResult.NotEnoughSpace -> messageChannel.send(LibraryMessage.NotEnoughSpace(r.neededBytes))
                ImportResult.LinkNotPossible -> showDialog(LibraryDialog.LinkNotPossible(uri))
            }
        } finally {
            transient.update { it.copy(importing = false) }
        }
    }

    /** Linked books can go missing outside the app; check each one whenever the list changes. */
    private fun checkAvailability(list: List<Book>) {
        viewModelScope.launch {
            val linked = list.filter { it.storage is BookStorage.Linked }
            val results = linked.associate { it.id to library.availability(it) }
            availability.update { results }
        }
    }

    private data class Transient(val importing: Boolean = false, val dialog: LibraryDialog? = null)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
