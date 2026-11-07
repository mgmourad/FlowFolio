package com.flowfolio.core.data.flow

import com.flowfolio.core.conversion.SidecarBuilder
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** A bundle is readable while it is being written: the complete EPUB when whole, the partial when not yet. */
class FlowFilesTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun nothingIsReadableWhenNeitherExists() {
        assertNull(FlowFiles(tmp.root).readable("book"))
    }

    @Test
    fun thePartialIsReadableBeforeCompletion() {
        val files = FlowFiles(tmp.root)
        files.dir("book").mkdirs()
        files.partial("book").writeText("growing")
        assertEquals(files.partial("book"), files.readable("book"))
    }

    @Test
    fun theCompleteEpubIsReadableOnceReady() {
        val files = FlowFiles(tmp.root)
        files.dir("book").mkdirs()
        files.epub("book").writeText("done")
        // A sidecar carrying the current engine version makes the bundle current, so ready() is true.
        files.sidecar("book").writeText(
            """{"recipe":{"engine":{"name":"flowfolio-converter","version":"${SidecarBuilder.ENGINE_VERSION}"}}}"""
        )
        assertEquals(files.epub("book"), files.readable("book"))
    }
}
