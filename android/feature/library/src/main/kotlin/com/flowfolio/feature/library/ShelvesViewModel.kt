package com.flowfolio.feature.library

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfolio.core.data.AnnotationRepository
import com.flowfolio.core.data.BookmarkRepository
import com.flowfolio.core.data.DictionaryRepository
import com.flowfolio.core.data.LibraryRepository
import com.flowfolio.core.data.ReadingRepository
import com.flowfolio.core.data.ShelfRepository
import com.flowfolio.core.data.flow.DynamicFlow
import com.flowfolio.core.model.Annotation
import com.flowfolio.core.model.Book
import com.flowfolio.core.model.BookFormat
import com.flowfolio.core.model.Bookmark
import com.flowfolio.core.model.BookmarkColor
import com.flowfolio.core.model.FlowNotice
import com.flowfolio.core.model.FlowStatus
import com.flowfolio.core.model.HighlightColor
import com.flowfolio.core.model.LibrarySort
import com.flowfolio.core.model.Shelf
import com.flowfolio.core.model.ShelfColor
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A shelf with its books resolved (most recently shelved first) and minimalist stats, for the Shelves screen. */
data class ShelfCard(
    val shelf: Shelf,
    val books: List<Book>,
    val annotationsCount: Int = 0,
    val bookmarksCount: Int = 0,
    val dictionaryCount: Int = 0,
    val readTimeMillis: Long = 0L,
    val flowActive: Boolean = false,
    val flowConverting: Boolean = false
) {
    val hasPdf: Boolean get() = books.any { it.format == BookFormat.PDF }
}

data class ShelvesUiState(
    val loading: Boolean = true,
    val cards: List<ShelfCard> = emptyList(),
    /** Every book in the library, for a shelf's "Add books". */
    val books: List<Book> = emptyList()
)

private object EmptyAnnotationRepository : AnnotationRepository {
    override fun observe(bookId: String): Flow<List<Annotation>> = flowOf(emptyList())
    override fun observeAll(): Flow<List<Annotation>> = flowOf(emptyList())
    override suspend fun add(
        bookId: String,
        passage: com.flowfolio.core.model.Passage,
        color: HighlightColor,
        note: String?
    ): Long = 0L
    override suspend fun setColor(id: Long, color: HighlightColor) = Unit
    override suspend fun setNote(id: Long, note: String?) = Unit
    override suspend fun remove(id: Long) = Unit
    override suspend fun setRightToLeft(bookId: String, on: Boolean) = Unit
}

private object EmptyBookmarkRepository : BookmarkRepository {
    override fun observe(bookId: String): Flow<List<Bookmark>> = flowOf(emptyList())
    override fun observeAll(): Flow<List<Bookmark>> = flowOf(emptyList())
    override suspend fun add(bookId: String, page: Int, color: BookmarkColor, locator: String?): Long = 0L
    override suspend fun setColor(bookmarkId: Long, color: BookmarkColor) = Unit
    override suspend fun remove(bookmarkId: Long) = Unit
}

private object EmptyShelfReadingRepository : ReadingRepository {
    override suspend fun record(bookId: String, startedAt: Long, endedAt: Long) = Unit
    override fun observeReadMillis(bookId: String): Flow<Long> = flowOf(0L)
    override fun observeLibraryReadMillis(): Flow<Long> = flowOf(0L)
}

/** Shelves: listing them, and making, renaming and removing them, and choosing a book's shelves. */
@Suppress("LongParameterList")
@HiltViewModel
class ShelvesViewModel @Inject constructor(
    private val shelves: ShelfRepository,
    library: LibraryRepository,
    annotationsRepo: AnnotationRepository = EmptyAnnotationRepository,
    bookmarksRepo: BookmarkRepository = EmptyBookmarkRepository,
    dictionaryRepo: DictionaryRepository = object : DictionaryRepository {},
    readingRepo: ReadingRepository = EmptyShelfReadingRepository,
    private val flow: DynamicFlow = DynamicFlow.None,
    @ApplicationContext private val appContext: Context? = null
) : ViewModel() {
    private data class StatsBundle(
        val annotationsByBook: Map<String, Int>,
        val bookmarksByBook: Map<String, Int>,
        val dictionaryByBook: Map<String, Int>,
        val readMillisByBook: Map<String, Long>,
        val flowStatuses: Map<String, FlowStatus>
    )

    private val pendingBatchShelves = LinkedHashSet<Long>()

    private val statsBundle = combine(
        annotationsRepo.observeAll(),
        bookmarksRepo.observeAll(),
        dictionaryRepo.observeCountsByBook(),
        readingRepo.observeReadMillisByBook(),
        flow.statuses
    ) { annotations, bookmarks, dictCounts, readMillis, statuses ->
        StatsBundle(
            annotationsByBook = annotations.groupingBy { it.bookId }.eachCount(),
            bookmarksByBook = bookmarks.groupingBy { it.bookId }.eachCount(),
            dictionaryByBook = dictCounts,
            readMillisByBook = readMillis,
            flowStatuses = statuses
        )
    }

    val uiState: StateFlow<ShelvesUiState> =
        combine(
            shelves.observeShelves(),
            library.observeBooks(LibrarySort.RECENT),
            statsBundle
        ) { list, books, stats ->
            val byId = books.associateBy { it.id }
            val cards = list.map { s ->
                val shelfBooks = s.bookIds.mapNotNull(byId::get)
                val pdfs = shelfBooks.filter { it.format == BookFormat.PDF }
                val converting = pdfs.any { b ->
                    val st = stats.flowStatuses[b.id]
                    st is FlowStatus.Converting || st == FlowStatus.Queued
                }
                val active = pdfs.isNotEmpty() && pdfs.all { b ->
                    b.flowMode == true && (stats.flowStatuses[b.id] == FlowStatus.Ready || flow.bundle(b.id) != null)
                }
                if (s.id in pendingBatchShelves && active && !converting) {
                    pendingBatchShelves.remove(s.id)
                    notifyBatchFlowDone(s, pdfs.firstOrNull()?.id.orEmpty())
                }
                ShelfCard(
                    shelf = s,
                    books = shelfBooks,
                    annotationsCount = shelfBooks.sumOf { stats.annotationsByBook[it.id] ?: 0 },
                    bookmarksCount = shelfBooks.sumOf { stats.bookmarksByBook[it.id] ?: 0 },
                    dictionaryCount = shelfBooks.sumOf { stats.dictionaryByBook[it.id] ?: 0 },
                    readTimeMillis = shelfBooks.sumOf { stats.readMillisByBook[it.id] ?: 0L },
                    flowActive = active,
                    flowConverting = converting
                )
            }
            ShelvesUiState(false, cards, books)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ShelvesUiState())

    /** Starts batch Dynamic Flow conversion on all PDF books in [shelfId]. */
    fun startBatchFlow(shelfId: Long) = viewModelScope.launch {
        val card = uiState.value.cards.firstOrNull { it.shelf.id == shelfId } ?: return@launch
        val pdfs = card.books.filter { it.format == BookFormat.PDF }
        if (pdfs.isEmpty()) return@launch
        pendingBatchShelves.add(shelfId)
        for (book in pdfs) {
            flow.setFlowMode(book.id, on = true, carry = true)
        }
        val allReady = pdfs.all { flow.bundle(it.id) != null }
        if (allReady && pendingBatchShelves.remove(shelfId)) {
            notifyBatchFlowDone(card.shelf, pdfs.first().id)
        }
    }

    /** Toggles Dynamic Flow off on all books in [shelfId], or turns it on if already converted. */
    fun setShelfFlowOff(shelfId: Long) = viewModelScope.launch {
        val card = uiState.value.cards.firstOrNull { it.shelf.id == shelfId } ?: return@launch
        pendingBatchShelves.remove(shelfId)
        for (book in card.books.filter { it.format == BookFormat.PDF }) {
            flow.setFlowMode(book.id, on = false, carry = true)
        }
    }

    /** True if every PDF book in [shelfId] already has a converted Dynamic Flow bundle. */
    fun isShelfFlowReady(shelfId: Long): Boolean {
        val card = uiState.value.cards.firstOrNull { it.shelf.id == shelfId } ?: return false
        val pdfs = card.books.filter { it.format == BookFormat.PDF }
        return pdfs.isNotEmpty() && pdfs.all { flow.bundle(it.id) != null }
    }

    private fun notifyBatchFlowDone(shelf: Shelf, bookId: String) {
        flow.notify(FlowNotice.Done(bookId, shelf.name))
        val ctx = appContext ?: return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val channel = NotificationChannel(
            BATCH_FLOW_CHANNEL_ID,
            "Batch Dynamic Flow",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        nm.createNotificationChannel(channel)
        val notification = NotificationCompat.Builder(ctx, BATCH_FLOW_CHANNEL_ID)
            .setSmallIcon(com.flowfolio.core.data.R.drawable.ic_stat_flowfolio)
            .setContentTitle("Dynamic Flow ready: ${shelf.name}")
            .setContentText("All books in “${shelf.name}” have been converted to Dynamic Flow.")
            .setAutoCancel(true)
            .build()
        nm.notify(BATCH_FLOW_NOTIFICATION_BASE + (shelf.id.toInt() and NOTIFICATION_ID_MASK), notification)
    }

    /** Makes a shelf; when [bookId] is given the book goes on it straight away (the "New shelf" in the picker). */
    fun create(name: String, bookId: String? = null, color: ShelfColor? = null) = viewModelScope.launch {
        if (name.isBlank()) return@launch
        val id = shelves.create(name, color)
        if (bookId != null) {
            val current = uiState.value.cards.filter { bookId in it.shelf.bookIds }.map { it.shelf.id }.toSet()
            shelves.setShelves(bookId, current + id)
        }
    }

    fun rename(shelfId: Long, name: String) = viewModelScope.launch {
        if (name.isNotBlank()) shelves.rename(shelfId, name)
    }

    fun setColor(shelfId: Long, color: ShelfColor?) = viewModelScope.launch { shelves.setColor(shelfId, color) }

    fun delete(shelfId: Long) = viewModelScope.launch { shelves.delete(shelfId) }

    fun setShelves(bookId: String, shelfIds: Set<Long>) = viewModelScope.launch { shelves.setShelves(bookId, shelfIds) }

    /** Puts [bookIds] on [shelfId] ("Add books"), in front of what is there. */
    fun addBooks(shelfId: Long, bookIds: List<String>) = viewModelScope.launch {
        if (bookIds.isNotEmpty()) shelves.addBooks(shelfId, bookIds)
    }

    /** Puts every one of [bookIds] on each of [shelfIds] (select mode's "Add to shelf"); takes none off a shelf. */
    fun addToShelves(bookIds: List<String>, shelfIds: Set<Long>) = viewModelScope.launch {
        shelfIds.forEach { shelves.addBooks(it, bookIds) }
    }

    /** Makes a shelf and puts [bookIds] on it. */
    fun createWith(name: String, color: ShelfColor?, bookIds: List<String>) = viewModelScope.launch {
        if (name.isBlank()) return@launch
        shelves.addBooks(shelves.create(name, color), bookIds)
    }

    /** Stands [bookIds] on [shelfId] in this order (drag and drop). */
    fun setOrder(shelfId: Long, bookIds: List<String>) = viewModelScope.launch { shelves.setOrder(shelfId, bookIds) }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val BATCH_FLOW_CHANNEL_ID = "flowfolio_batch_flow"
        const val BATCH_FLOW_NOTIFICATION_BASE = 5100
        const val NOTIFICATION_ID_MASK = 0xFFF
    }
}
