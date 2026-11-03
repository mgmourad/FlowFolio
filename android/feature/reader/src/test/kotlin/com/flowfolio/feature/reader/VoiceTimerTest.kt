package com.flowfolio.feature.reader

import androidx.lifecycle.SavedStateHandle
import com.flowfolio.core.data.AnnotationRepository
import com.flowfolio.core.data.FolderFile
import com.flowfolio.core.data.ImportResult
import com.flowfolio.core.data.LibraryRepository
import com.flowfolio.core.data.RelinkResult
import com.flowfolio.core.data.VoicePlayer
import com.flowfolio.core.data.VoiceRecorder
import com.flowfolio.core.data.flow.ReadingViews
import com.flowfolio.core.model.Annotation
import com.flowfolio.core.model.Availability
import com.flowfolio.core.model.Book
import com.flowfolio.core.model.BookDetails
import com.flowfolio.core.model.HighlightColor
import com.flowfolio.core.model.LibrarySort
import com.flowfolio.core.model.Passage
import com.flowfolio.core.model.StorageMode
import com.flowfolio.core.model.VoiceNote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** A recorder with no microphone behind it: it says yes, and counts the readings asked of it. */
private open class FakeRecorder : VoiceRecorder {
    private val _levels = MutableStateFlow(emptyList<Float>())
    override val levels: StateFlow<List<Float>> = _levels.asStateFlow()
    final override var recording = false
        private set

    var samples = 0
        private set

    override fun start(bookTitle: String, page: Int): Boolean {
        recording = true
        return true
    }

    override fun sample() {
        samples++
        _levels.value = _levels.value + HALF
    }

    override fun stop(): VoiceNote? {
        recording = false
        return null
    }

    override fun cancel() {
        recording = false
    }

    private companion object {
        const val HALF = 0.5f
    }
}

/** Only what this ViewModel reads; anything else would be a surprise, so it says so. */
private class NoAnnotations : AnnotationRepository {
    override fun observe(bookId: String): Flow<List<Annotation>> = flowOf(emptyList())

    override fun observeAll(): Flow<List<Annotation>> = flowOf(emptyList())

    override suspend fun add(bookId: String, passage: Passage, color: HighlightColor, note: String?): Long = 1L

    override suspend fun setColor(id: Long, color: HighlightColor) = Unit

    override suspend fun setNote(id: Long, note: String?) = Unit

    override suspend fun remove(id: Long) = Unit

    override suspend fun setRightToLeft(bookId: String, on: Boolean) = Unit
}

@Suppress("TooManyFunctions") // the shape of the interface, not of this fake
private class NoLibrary : LibraryRepository {
    override fun observeBooks(sort: LibrarySort): Flow<List<Book>> = flowOf(emptyList())

    override fun observeBook(bookId: String): Flow<Book?> = flowOf(null)

    override suspend fun import(
        uri: String,
        mode: StorageMode,
        fromShare: Boolean,
        folder: String?,
        accessHeld: Boolean
    ): ImportResult = error("the clock never imports a book")

    override suspend fun listFolder(treeUri: String): List<FolderFile>? = null

    override suspend fun persistFolderAccess(treeUri: String) = false

    override suspend fun availability(book: Book) = Availability.AVAILABLE

    override suspend fun sizeOf(uri: String): Long? = null

    override suspend fun relink(bookId: String, uri: String): RelinkResult = RelinkResult.Unreadable

    override suspend fun delete(bookId: String) = Unit

    override suspend fun markOpened(bookId: String) = Unit

    override suspend fun rename(bookId: String, title: String) = Unit

    override suspend fun updateDetails(bookId: String, title: String, author: String?, details: BookDetails) = Unit
}

/**
 * The VoiceMark pill's clock (owner, M5 round 3).
 *
 * It sat at 00:00 because the recorder's own flags are plain properties: reading them told Compose nothing, so
 * nothing ever redrew. These hold the ViewModel to publishing both as state that moves, which is the part no
 * emulator can check -- recording on one takes its audio stack down with it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VoiceTimerTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(recorder: VoiceRecorder) = ReaderAnnotationsViewModel(
        SavedStateHandle(mapOf(ReaderViewModel.ARG_BOOK_ID to "book")),
        NoAnnotations(),
        recorder,
        VoicePlayer(),
        NoLibrary(),
        ReadingViews.ShowAll
    )

    @Test
    fun `the clock runs while a recording does`() = runTest(dispatcher) {
        val recorder = FakeRecorder()
        val vm = viewModel(recorder)

        assertFalse("nothing is recording yet", vm.recording.value)
        assertEquals(0L, vm.recordedMillis.value)

        assertTrue(vm.startVoice("Book", page = 1))
        assertTrue("the pill has to see that it started", vm.recording.value)

        advanceTimeBy(RUN_MS)
        assertTrue("the clock must move, not sit at zero", vm.recordedMillis.value > 0L)
        assertTrue("the waveform is sampled as it goes", recorder.samples > 1)

        // The ViewModel's loop runs until the recorder stops, so every test has to put it down again.
        vm.cancelVoice()
        advanceTimeBy(SETTLE_MS)
    }

    @Test
    fun `stopping puts the clock back`() = runTest(dispatcher) {
        val recorder = FakeRecorder()
        val vm = viewModel(recorder)
        vm.startVoice("Book", page = 1)
        advanceTimeBy(RUN_MS)

        vm.stopVoice(annotationId = 1L)
        advanceTimeBy(SETTLE_MS)
        assertFalse(vm.recording.value)
        assertEquals("a stopped recorder shows no time", 0L, vm.recordedMillis.value)
    }

    @Test
    fun `throwing one away puts the clock back too`() = runTest(dispatcher) {
        val vm = viewModel(FakeRecorder())
        vm.startVoice("Book", page = 1)
        advanceTimeBy(RUN_MS)

        vm.cancelVoice()
        advanceTimeBy(SETTLE_MS)
        assertFalse(vm.recording.value)
        assertEquals(0L, vm.recordedMillis.value)
    }

    /** A refused microphone looks like this from here, and must not leave the pill thinking it is recording. */
    @Test
    fun `a recorder that will not start leaves nothing running`() = runTest(dispatcher) {
        val refusing = object : FakeRecorder() {
            override fun start(bookTitle: String, page: Int) = false
        }
        val vm = viewModel(refusing)
        assertFalse(vm.startVoice("Book", page = 1))
        assertFalse(vm.recording.value)
        assertEquals(0L, vm.recordedMillis.value)
    }

    private companion object {
        const val RUN_MS = 1_000L
        const val SETTLE_MS = 100L
    }
}
