package com.flowfolio.feature.reader

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.flowfolio.feature.reader.cache.TileKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoomTest {
    private val screen = IntSize(2000, 1000)

    @Test
    fun pinchZoomsAroundTheFingersAndStopsAtTheEdges() {
        val z = ZoomState()
        z.transform(centroid = Offset(1000f, 500f), pan = Offset.Zero, zoom = 2f, screen = screen)
        assertEquals(2f, z.scale)
        assertEquals(Offset(-1000f, -500f), z.offset) // the point under the fingers stays put
        z.transform(Offset(1000f, 500f), pan = Offset(5000f, 0f), zoom = 1f, screen = screen)
        assertEquals(0f, z.offset.x) // cannot pan past the left edge
        z.transform(Offset(0f, 0f), Offset.Zero, zoom = 100f, screen = screen)
        assertEquals(ZoomState.MAX_SCALE, z.scale)
        assertFalse(z.isAtRest)
        z.transform(Offset(0f, 0f), Offset.Zero, zoom = 0.01f, screen = screen)
        assertEquals(ZoomState.MIN_SCALE, z.scale)
        assertEquals(Offset.Zero, z.offset)
        assertTrue(z.isAtRest)
    }

    @Test
    fun liftEventsWithoutACentroidAreIgnored() {
        val z = ZoomState()
        z.transform(Offset(1000f, 500f), Offset.Zero, 2f, screen)
        z.transform(Offset.Unspecified, Offset.Zero, 1f, screen)
        z.transform(Offset(Float.NaN, 1f), Offset.Zero, 1f, screen)
        assertEquals(Offset(-1000f, -500f), z.offset)
        assertEquals(2f, z.scale)
    }

    @Test
    fun levelsSnapToAFewSteps() {
        assertNull(TilePlan.levelFor(1.1f))
        assertEquals(1.5f, TilePlan.levelFor(1.3f))
        assertEquals(2f, TilePlan.levelFor(1.97f))
        assertEquals(3f, TilePlan.levelFor(2.4f))
        assertEquals(6f, TilePlan.levelFor(9f))
    }

    @Test
    fun onlyTilesOnScreenAreRequestedCentreFirst() {
        // One 1000 x 1000 page at the left half; zoomed 2x on its centre: content (250..750, 250..500) is visible.
        val frames = listOf(PageFrames.Frame(page = 4, x = 0, y = 0, w = 1000, h = 1000))
        val tiles = TilePlan.visible(frames, scale = 2f, offset = Offset(-500f, -500f), screen = screen)
        // Level 2: the page is 2000 px, tiles are 512 px = 256 content px. Visible content x 250..1250 -> clipped
        // to the page 250..1000 -> tiles 0..3; y 250..750 -> tiles 0..2.
        assertEquals(4 * 3, tiles.size)
        assertTrue(tiles.all { it.page == 4 && it.level == 2f && it.pageWidthPx == 2000 })
        assertEquals(TileKey(4, 2f, 2000, 2000, 2, 1), tiles.first())
        // The last column is cut to the page: 2000 - 3 * 512 = 464 px wide.
        assertEquals(464, tiles.first { it.tx == 3 }.widthPx)
    }

    @Test
    fun noTilesNearOneX() {
        val frames = listOf(PageFrames.Frame(0, 0, 0, 1000, 1000))
        assertTrue(TilePlan.visible(frames, 1.05f, Offset.Zero, screen).isEmpty())
    }
}
