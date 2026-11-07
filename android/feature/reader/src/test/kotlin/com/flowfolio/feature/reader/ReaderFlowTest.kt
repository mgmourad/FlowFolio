package com.flowfolio.feature.reader

import com.flowfolio.core.data.OpenedBook
import com.flowfolio.core.data.ReaderRepository
import com.flowfolio.core.data.flow.DynamicFlow
import com.flowfolio.core.model.Book
import com.flowfolio.core.model.BookStorage
import com.flowfolio.core.model.FlowNotice
import com.flowfolio.core.model.FlowStatus
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The reader follows a converting book: it opens the partial at the first chapter and the full book when done. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ReaderFlowTest {
    @Test
    fun opensFlowReturnsTrueOnceAPartialIsReadable() = runTest(UnconfinedTestDispatcher()) {
        val flow = FakeDynamicFlow(readable = File("partial.epub"))
        val readerFlow = ReaderFlow("book", backgroundScope, FakeRepository(), flow, FakeHost())
        assertTrue(readerFlow.opensFlow(true))
    }

    @Test
    fun watchOpensThePartialWhenTheFirstChapterIsReadable() = runTest(UnconfinedTestDispatcher()) {
        val statuses = MutableStateFlow<FlowStatus>(FlowStatus.None)
        val host = FakeHost()
        val readerFlow =
            ReaderFlow("book", backgroundScope, FakeRepository(), FakeDynamicFlow(statusFlow = statuses), host)
        assertFalse("nothing readable yet", readerFlow.opensFlow(true))
        host.isShowing = true
        statuses.value = FlowStatus.Converting(0.5f, null, readableChapters = 1, totalChapters = 3)
        assertTrue("reopens to the partial", host.reopenedToFlow)
    }

    @Test
    fun watchReopensToTheFullBookWhenConversionFinishes() = runTest(UnconfinedTestDispatcher()) {
        val statuses = MutableStateFlow<FlowStatus>(FlowStatus.None)
        val host = FakeHost()
        val readerFlow =
            ReaderFlow("book", backgroundScope, FakeRepository(), FakeDynamicFlow(statusFlow = statuses), host)
        assertFalse(readerFlow.opensFlow(true))
        host.isShowing = true
        statuses.value = FlowStatus.Converting(0.5f, null, readableChapters = 1, totalChapters = 3)
        readerFlow.shown(true)
        host.reopenedToFlow = false
        statuses.value = FlowStatus.Ready
        assertTrue("reopens to the full book", host.reopenedToFlow)
        assertTrue("flushes the reading place first", host.savedPlace)
    }

    @Test
    fun aSingleChapterBookOpensOnceOnReady() = runTest(UnconfinedTestDispatcher()) {
        val statuses = MutableStateFlow<FlowStatus>(FlowStatus.None)
        val host = FakeHost()
        val readerFlow =
            ReaderFlow("book", backgroundScope, FakeRepository(), FakeDynamicFlow(statusFlow = statuses), host)
        assertFalse(readerFlow.opensFlow(true))
        host.isShowing = true
        // Its only chapter is already the whole book, so no partial open.
        statuses.value = FlowStatus.Converting(0.9f, null, readableChapters = 1, totalChapters = 1)
        assertFalse("no partial open for a single-chapter book", host.reopenedToFlow)
        statuses.value = FlowStatus.Ready
        assertTrue(host.reopenedToFlow)
    }

    @Test
    fun opensFlowRestartsAStalePartial() = runTest(UnconfinedTestDispatcher()) {
        val flow = FakeDynamicFlow(readable = File("partial.epub"))
        val readerFlow = ReaderFlow("book", backgroundScope, FakeRepository(), flow, FakeHost())
        assertTrue(readerFlow.opensFlow(true))
        assertTrue("a stale partial is cleaned up by a fresh conversion", flow.convertCalled)
    }

    @Test
    fun watchPicksUpChaptersAsTheyLandWhileReading() = runTest(UnconfinedTestDispatcher()) {
        val statuses = MutableStateFlow<FlowStatus>(FlowStatus.None)
        val host = FakeHost()
        var clock = 1_000L
        val readerFlow = ReaderFlow(
            "book",
            backgroundScope,
            FakeRepository(),
            FakeDynamicFlow(statusFlow = statuses),
            host,
            now = { clock }
        )
        assertFalse(readerFlow.opensFlow(true))
        host.isShowing = true
        statuses.value = FlowStatus.Converting(0.5f, null, readableChapters = 1, totalChapters = 5)
        readerFlow.shown(true)
        host.reopenedToFlow = false
        host.savedPlace = false
        clock += 20_000L
        statuses.value = FlowStatus.Converting(0.6f, null, readableChapters = 2, totalChapters = 5)
        assertTrue("picks up the new chapter", host.reopenedToFlow)
        assertTrue("flushes the place first", host.savedPlace)
    }

    @Test
    fun watchThrottlesRapidReopens() = runTest(UnconfinedTestDispatcher()) {
        val statuses = MutableStateFlow<FlowStatus>(FlowStatus.None)
        val host = FakeHost()
        var clock = 1_000L
        val readerFlow = ReaderFlow(
            "book",
            backgroundScope,
            FakeRepository(),
            FakeDynamicFlow(statusFlow = statuses),
            host,
            now = { clock }
        )
        assertFalse(readerFlow.opensFlow(true))
        host.isShowing = true
        statuses.value = FlowStatus.Converting(0.5f, null, readableChapters = 1, totalChapters = 5)
        readerFlow.shown(true)
        host.reopenedToFlow = false
        // A chapter lands soon after the first: throttled, so no reopen yet.
        clock += 1_000L
        statuses.value = FlowStatus.Converting(0.6f, null, readableChapters = 2, totalChapters = 5)
        assertFalse("throttled", host.reopenedToFlow)
        // Enough time passes: the next chapter is picked up.
        clock += 20_000L
        statuses.value = FlowStatus.Converting(0.7f, null, readableChapters = 3, totalChapters = 5)
        assertTrue("reopens after the throttle", host.reopenedToFlow)
    }

    private class FakeHost : ReaderFlow.Host {
        var isShowing = false
        var reopenedToFlow = false
        var savedPlace = false
        override val anchorPage = 3
        override val showing get() = isShowing

        override fun reopen(view: Boolean) {
            if (view) reopenedToFlow = true
        }

        override suspend fun flowOnOpen() = false

        override suspend fun savePlace() {
            savedPlace = true
        }
    }

    private class FakeRepository : ReaderRepository {
        override suspend fun open(bookId: String, password: String?): OpenedBook = throw UnsupportedOperationException()

        override suspend fun saveProgress(bookId: String, page: Int) = Unit

        override suspend fun savePageLabels(bookId: String, labels: String) = Unit

        override suspend fun book(bookId: String): Book = Book(
            id = bookId,
            title = "Book",
            pageCount = 10,
            storage = BookStorage.Copied("book.pdf"),
            sizeBytes = 0,
            addedAtMillis = 0,
            lastOpenedAtMillis = null,
            coverPath = null,
            passwordProtected = false
        )
    }

    private class FakeDynamicFlow(
        private val statusFlow: MutableStateFlow<FlowStatus> = MutableStateFlow(FlowStatus.None),
        private val readable: File? = null
    ) : DynamicFlow {
        var convertCalled = false

        override val statuses: StateFlow<Map<String, FlowStatus>> = MutableStateFlow(emptyMap())
        override val notices: SharedFlow<FlowNotice> = MutableSharedFlow()
        override val wantsNotificationPermission: StateFlow<Boolean> = MutableStateFlow(false)

        override fun status(bookId: String): Flow<FlowStatus> = statusFlow

        override fun bundle(bookId: String): File? = null

        override fun readableBundle(bookId: String): File? = readable

        override suspend fun convert(bookId: String) {
            convertCalled = true
        }

        override suspend fun setFlowMode(bookId: String, on: Boolean, carry: Boolean) = Unit

        override suspend fun saveFlowPlace(bookId: String, locator: String, pdfPage: Int?) = Unit

        override suspend fun hasNotes(bookId: String, inFlow: Boolean) = false

        override fun notificationPermissionAsked() = Unit

        override fun notify(notice: FlowNotice) = Unit

        override fun delete(bookId: String) = Unit
    }
}
