package com.flowfolio.feature.library

import com.flowfolio.core.data.ImportResult
import com.flowfolio.core.data.LibraryRepository
import com.flowfolio.core.data.RelinkResult
import com.flowfolio.core.data.SettingsRepository
import com.flowfolio.core.data.ShelfRepository
import com.flowfolio.core.model.Availability
import com.flowfolio.core.model.Book
import com.flowfolio.core.model.BookStorage
import com.flowfolio.core.model.LibrarySort
import com.flowfolio.core.model.Shelf
import com.flowfolio.core.model.StorageMode
import com.flowfolio.core.model.ThemePreference
import com.flowfolio.core.model.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

fun book(id: String, title: String = "Book $id", linked: Boolean = false) = Book(
    id = id,
    title = title,
    pageCount = 10,
    storage = if (linked) BookStorage.Linked("content://x/$id") else BookStorage.Copied("/files/$id.pdf"),
    sizeBytes = 1_000,
    addedAtMillis = 0,
    lastOpenedAtMillis = null,
    coverPath = null,
    passwordProtected = false
)

class FakeLibrary : LibraryRepository {
    val books = MutableStateFlow<List<Book>>(emptyList())
    val imports = mutableListOf<Triple<String, StorageMode, Boolean>>()
    var nextResult: (String) -> ImportResult = { uri -> ImportResult.Imported(book(uri.substringAfterLast('/'))) }
    val missing = mutableSetOf<String>()
    val deleted = mutableListOf<String>()
    val opened = mutableListOf<String>()

    override fun observeBooks(sort: LibrarySort): Flow<List<Book>> =
        books.map { list -> if (sort == LibrarySort.TITLE) list.sortedBy { it.title } else list }

    override fun observeBook(bookId: String): Flow<Book?> = books.map { list -> list.firstOrNull { it.id == bookId } }

    override suspend fun import(uri: String, mode: StorageMode, fromShare: Boolean): ImportResult {
        imports += Triple(uri, mode, fromShare)
        return nextResult(uri).also { r -> if (r is ImportResult.Imported) books.update { it + r.book } }
    }

    override suspend fun availability(book: Book) = if (book.id in
        missing
    ) {
        Availability.MISSING
    } else {
        Availability.AVAILABLE
    }

    override suspend fun sizeOf(uri: String): Long? = 2_500_000

    override suspend fun relink(bookId: String, uri: String): RelinkResult =
        if (uri.endsWith(bookId)) RelinkResult.Relinked.also { missing -= bookId } else RelinkResult.DifferentFile

    override suspend fun delete(bookId: String) {
        deleted += bookId
        books.update { list -> list.filterNot { it.id == bookId } }
    }

    override suspend fun markOpened(bookId: String) {
        opened += bookId
    }
}

class FakeSettings(initial: UserSettings = UserSettings()) : SettingsRepository {
    val state = MutableStateFlow(initial)
    override val settings: Flow<UserSettings> = state

    override suspend fun setStorageMode(mode: StorageMode) = state.update { it.copy(storageMode = mode) }

    override suspend fun setAskStorageEachImport(ask: Boolean) = state.update { it.copy(askStorageEachImport = ask) }

    override suspend fun setTheme(theme: ThemePreference) = state.update { it.copy(theme = theme) }

    override suspend fun setStorageNoticeSeen() = state.update { it.copy(storageNoticeSeen = true) }

    override suspend fun setLibrarySort(sort: LibrarySort) = state.update { it.copy(librarySort = sort) }

    override suspend fun setReaderSpreads(on: Boolean) = state.update { it.copy(readerSpreads = on) }

    override suspend fun setSpreadCoverAlone(on: Boolean) = state.update { it.copy(spreadCoverAlone = on) }

    override suspend fun setNightPages(on: Boolean) = state.update { it.copy(nightPages = on) }
}

class FakeShelves(initial: List<Shelf> = emptyList()) : ShelfRepository {
    val shelves = MutableStateFlow(initial)
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1

    override fun observeShelves(): Flow<List<Shelf>> = shelves

    override suspend fun create(name: String): Long =
        nextId++.also { id -> shelves.update { it + Shelf(id, name.trim(), emptyList()) } }

    override suspend fun rename(shelfId: Long, name: String) =
        shelves.update { list -> list.map { if (it.id == shelfId) it.copy(name = name) else it } }

    override suspend fun delete(shelfId: Long) = shelves.update { list -> list.filterNot { it.id == shelfId } }

    override suspend fun setShelves(bookId: String, shelfIds: Set<Long>) = shelves.update { list ->
        list.map { s ->
            val on = bookId in s.bookIds
            when {
                s.id in shelfIds && !on -> s.copy(bookIds = listOf(bookId) + s.bookIds)
                s.id !in shelfIds && on -> s.copy(bookIds = s.bookIds - bookId)
                else -> s
            }
        }
    }
}
