package com.flowfolio.feature.reader

import androidx.compose.ui.graphics.ImageBitmap
import com.flowfolio.core.model.PixelSize
import com.flowfolio.feature.reader.cache.PageCache
import com.flowfolio.feature.reader.cache.PageKey
import com.flowfolio.feature.reader.cache.PageSource
import java.lang.reflect.Proxy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PageCacheTest {
    /** Stand-in images: the cache never inspects them, only keys and byte sizes matter. */
    private fun fakeImage(): ImageBitmap =
        Proxy.newProxyInstance(javaClass.classLoader, arrayOf(ImageBitmap::class.java)) { _, _, _ ->
            null
        } as ImageBitmap

    private class Recorder(
        private val gate: Map<Int, CompletableDeferred<Unit>> = emptyMap(),
        private val image: () -> ImageBitmap
    ) : PageSource {
        val rendered = mutableListOf<Int>()

        override suspend fun render(key: PageKey): ImageBitmap {
            gate[key.page]?.await()
            rendered += key.page
            return image()
        }
    }

    private val page = PixelSize(100, 100) // 40,000 bytes
    private fun keys(vararg pages: Int) = pages.map { PageKey(it, page) }

    @Test
    fun rendersWantedPagesInPriorityOrder() = runTest(UnconfinedTestDispatcher()) {
        val source = Recorder { fakeImage() }
        val cache = PageCache(source, backgroundScope, budgetBytes = 10 * page.argbBytes)
        cache.request(keys(5, 6, 4, 7))
        assertEquals(listOf(5, 6, 4, 7), source.rendered)
        assertEquals(keys(5, 6, 4, 7).toSet(), cache.images.value.keys)
    }

    @Test
    fun neverHoldsMoreThanTheBudget() = runTest(UnconfinedTestDispatcher()) {
        val source = Recorder { fakeImage() }
        val cache = PageCache(source, backgroundScope, budgetBytes = 3 * page.argbBytes)
        for (start in 0 until 50) {
            cache.request(keys(start, start + 1, start - 1, start + 2).filter { it.page >= 0 })
            assertTrue("held ${cache.heldBytes} > budget", cache.heldBytes <= cache.budgetBytes)
        }
        // Only the prefix that fits is rendered: 3 of the 4 wanted pages each time.
        assertEquals(keys(49, 50, 48).toSet(), cache.images.value.keys)
    }

    @Test
    fun alreadyHeldPagesAreNotRenderedAgain() = runTest(UnconfinedTestDispatcher()) {
        val source = Recorder { fakeImage() }
        val cache = PageCache(source, backgroundScope, budgetBytes = 10 * page.argbBytes)
        cache.request(keys(1, 2, 0))
        cache.request(keys(2, 3, 1))
        assertEquals(listOf(1, 2, 0, 3), source.rendered)
    }

    @Test
    fun aNewRequestAbandonsTheOldOne() = runTest(UnconfinedTestDispatcher()) {
        val slow = CompletableDeferred<Unit>()
        val source = Recorder(gate = mapOf(1 to slow)) { fakeImage() }
        val cache = PageCache(source, backgroundScope, budgetBytes = 10 * page.argbBytes)
        cache.request(keys(1, 2, 3)) // stuck on page 1
        cache.request(keys(40, 41))
        slow.complete(Unit)
        assertEquals(listOf(40, 41), source.rendered)
    }

    @Test
    fun sizeIsPartOfTheKey() = runTest(UnconfinedTestDispatcher()) {
        val source = Recorder { fakeImage() }
        val cache = PageCache(source, backgroundScope, budgetBytes = 10 * page.argbBytes)
        cache.request(listOf(PageKey(1, page)))
        cache.request(listOf(PageKey(1, PixelSize(50, 50)))) // layout changed (fold/unfold)
        assertEquals(listOf(1, 1), source.rendered)
    }

    @Test
    fun memoryPressureKeepsOnlyVisiblePages() = runTest(UnconfinedTestDispatcher()) {
        val cache = PageCache(Recorder { fakeImage() }, backgroundScope, budgetBytes = 10 * page.argbBytes)
        val wanted = keys(10, 11, 9, 12)
        cache.request(wanted)
        cache.trimToVisible(wanted, keep = 1)
        assertEquals(keys(10).toSet(), cache.images.value.keys)
    }

    @Test
    fun aSinglePageLargerThanTheBudgetIsStillShown() = runTest(UnconfinedTestDispatcher()) {
        val cache = PageCache(Recorder { fakeImage() }, backgroundScope, budgetBytes = 1)
        cache.request(keys(7, 8))
        assertEquals(keys(7).toSet(), cache.images.value.keys)
    }
}
