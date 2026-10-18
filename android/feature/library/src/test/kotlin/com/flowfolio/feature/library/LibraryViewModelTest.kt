package com.flowfolio.feature.library

import app.cash.turbine.test
import com.flowfolio.core.data.ImportResult
import com.flowfolio.core.model.Availability
import com.flowfolio.core.model.LibrarySort
import com.flowfolio.core.model.StorageMode
import com.flowfolio.core.model.UserSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {
    private val library = FakeLibrary()

    // One scheduler for the test body, viewModelScope (Main) and collectors, so state updates are observed eagerly.
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm(settings: FakeSettings) = LibraryViewModel(library, settings, FakeShelves())

    private fun seen(mode: StorageMode = StorageMode.COPY, ask: Boolean = false) =
        FakeSettings(UserSettings(storageMode = mode, askStorageEachImport = ask, storageNoticeSeen = true))

    @Test
    fun firstImportAsksOnceAndRemembersTheChoice() = runTest(dispatcher) {
        val settings = FakeSettings()
        val vm = vm(settings)
        backgroundScope.launch { vm.uiState.collect {} }
        vm.onFilePicked("content://docs/a")
        val dialog = vm.uiState.value.dialog as LibraryDialog.ChooseStorage
        assertTrue(dialog.firstTime)
        assertEquals(StorageMode.COPY, dialog.initial)
        assertEquals(2_500_000L, dialog.sizeBytes)

        vm.onStorageChosen(dialog, StorageMode.LINK)
        assertNull(vm.uiState.value.dialog)
        assertEquals(StorageMode.LINK, settings.state.value.storageMode)
        assertTrue(settings.state.value.storageNoticeSeen)
        assertEquals(Triple("content://docs/a", StorageMode.LINK, false), library.imports.single())

        vm.onFilePicked("content://docs/b")
        assertNull("no dialog once the notice was seen", vm.uiState.value.dialog)
        assertEquals(StorageMode.LINK, library.imports.last().second)
    }

    @Test
    fun askEveryTimeShowsTheChoiceWithoutChangingTheSetting() = runTest(dispatcher) {
        val settings = seen(ask = true)
        val vm = vm(settings)
        backgroundScope.launch { vm.uiState.collect {} }
        vm.onFilePicked("content://docs/a")
        val dialog = vm.uiState.value.dialog as LibraryDialog.ChooseStorage
        vm.onStorageChosen(dialog, StorageMode.LINK)
        assertEquals(StorageMode.COPY, settings.state.value.storageMode)
        assertEquals(StorageMode.LINK, library.imports.single().second)
    }

    @Test
    fun sharedFilesAreCopiedWithoutAsking() = runTest(dispatcher) {
        val vm = vm(FakeSettings(UserSettings(storageMode = StorageMode.LINK)))
        backgroundScope.launch { vm.uiState.collect {} }
        vm.onFileShared("content://other.app/file")
        assertEquals(Triple("content://other.app/file", StorageMode.COPY, true), library.imports.single())
        assertNull(vm.uiState.value.dialog)
    }

    @Test
    fun linkNotPossibleOffersCopyInstead() = runTest(dispatcher) {
        library.nextResult =
            { uri -> if (library.imports.size == 1) ImportResult.LinkNotPossible else ImportResult.Imported(book(uri)) }
        val vm = vm(seen(StorageMode.LINK))
        backgroundScope.launch { vm.uiState.collect {} }
        vm.onFilePicked("content://docs/a")
        val dialog = vm.uiState.value.dialog as LibraryDialog.LinkNotPossible
        vm.onCopyInstead(dialog.uri)
        assertEquals(StorageMode.COPY, library.imports.last().second)
        assertNull(vm.uiState.value.dialog)
    }

    @Test
    fun importResultsBecomeMessages() = runTest(dispatcher) {
        val vm = vm(seen())
        backgroundScope.launch { vm.uiState.collect {} }
        vm.messages.test {
            vm.onFilePicked("content://docs/physics")
            assertEquals(LibraryMessage.Imported("Book physics"), awaitItem())
            library.nextResult = { ImportResult.NotAPdf }
            vm.onFilePicked("content://docs/notes")
            assertEquals(LibraryMessage.NotAPdf, awaitItem())
            library.nextResult = { ImportResult.NotEnoughSpace(99) }
            vm.onFilePicked("content://docs/huge")
            assertEquals(LibraryMessage.NotEnoughSpace(99), awaitItem())
        }
    }

    @Test
    fun missingLinkedBooksAreFlaggedAndRelinkClearsIt() = runTest(dispatcher) {
        library.books.value = listOf(book("a", linked = true), book("b"))
        library.missing += "a"
        val vm = vm(seen())
        backgroundScope.launch { vm.uiState.collect {} }
        val items = vm.uiState.first {
            it.items.size == 2 &&
                it.items.any { i -> i.availability == Availability.MISSING }
        }.items
        assertEquals(Availability.MISSING, items.first { it.book.id == "a" }.availability)
        assertEquals(Availability.AVAILABLE, items.first { it.book.id == "b" }.availability)

        vm.onRelinkPicked("a", "content://new/a")
        assertEquals(Availability.AVAILABLE, vm.uiState.value.items.first { it.book.id == "a" }.availability)
    }

    @Test
    fun deleteGoesThroughConfirmation() = runTest(dispatcher) {
        val target = book("a")
        library.books.value = listOf(target)
        val vm = vm(seen())
        backgroundScope.launch { vm.uiState.collect {} }
        vm.onRequestDelete(target)
        assertEquals(LibraryDialog.ConfirmDelete(target), vm.uiState.value.dialog)
        assertTrue(library.deleted.isEmpty())
        vm.onConfirmDelete(target)
        assertEquals(listOf("a"), library.deleted)
        assertTrue(vm.uiState.value.items.isEmpty())
    }

    @Test
    fun sortIsPersisted() = runTest(dispatcher) {
        val settings = seen()
        library.books.value = listOf(book("1", "Zeta"), book("2", "Alpha"))
        val vm = vm(settings)
        backgroundScope.launch { vm.uiState.collect {} }
        vm.onSortChange(LibrarySort.TITLE)
        assertEquals(LibrarySort.TITLE, settings.state.value.librarySort)
        assertEquals(listOf("Alpha", "Zeta"), vm.uiState.value.items.map { it.book.title })
    }
}
