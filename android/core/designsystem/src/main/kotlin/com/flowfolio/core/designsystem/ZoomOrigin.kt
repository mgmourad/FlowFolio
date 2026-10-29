package com.flowfolio.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * Where the next page zooms from (owner, M3T1): the centre of what was tapped (a shelf's card), as a fraction of the
 * window. The navigation reads it to scale the page up from there and back into it on Back. Scaling moves pixels only,
 * so the page is not measured again on every frame.
 */
object ZoomOrigin {
    var pivot: TransformOrigin = TransformOrigin.Center
        private set

    internal fun set(bounds: Rect, rootWidth: Float, rootHeight: Float) {
        if (rootWidth <= 0f || rootHeight <= 0f) return
        pivot = TransformOrigin(bounds.center.x / rootWidth, bounds.center.y / rootHeight)
    }
}

/** An element a page zooms out of: [modifier] goes on it, and [mark] is called as it is tapped. */
class ZoomSource internal constructor() {
    private var bounds: Rect? = null
    private var root = 0f to 0f

    val modifier: Modifier = Modifier.onGloballyPositioned { coordinates ->
        bounds = coordinates.boundsInRoot()
        val size = coordinates.findRootCoordinates().size
        root = size.width.toFloat() to size.height.toFloat()
    }

    /** Makes this element the origin of the next zoom. */
    fun mark() {
        bounds?.let { ZoomOrigin.set(it, root.first, root.second) }
    }
}

@Composable
fun rememberZoomSource(): ZoomSource = remember { ZoomSource() }
