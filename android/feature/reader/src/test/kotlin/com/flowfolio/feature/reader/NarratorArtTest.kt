package com.flowfolio.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Test

/** The cover keeps its shape on the square album art: fitted and centred, never stretched (owner, 2026-11-10). */
class NarratorArtTest {
    @Test
    fun aPortraitCoverIsFittedByItsHeightAndCentred() {
        val r = ArtGeometry.fit(600, 900, 600f)
        assertEquals(400f, r.width, DELTA)
        assertEquals(600f, r.height, DELTA)
        assertEquals(100f, r.left, DELTA)
        assertEquals(0f, r.top, DELTA)
    }

    @Test
    fun aLandscapeCoverIsFittedByItsWidth() {
        val r = ArtGeometry.fit(1200, 600, 600f, left = 10f, top = 10f)
        assertEquals(600f, r.width, DELTA)
        assertEquals(300f, r.height, DELTA)
        assertEquals(160f, r.top, DELTA)
    }

    @Test
    fun theAspectIsAlwaysKept() {
        for ((w, h) in listOf(600 to 900, 1000 to 1000, 333 to 1000, 1600 to 900)) {
            val r = ArtGeometry.fit(w, h, 720f)
            assertEquals(w.toFloat() / h, r.width / r.height, 0.001f)
        }
    }

    @Test
    fun theBackdropCropsTheMiddleSquare() {
        val square = ArtGeometry.cropSquare(600, 900)
        assertEquals(600f, square.width, DELTA)
        assertEquals(150f, square.top, DELTA)
    }

    private companion object {
        const val DELTA = 0.01f
    }
}
