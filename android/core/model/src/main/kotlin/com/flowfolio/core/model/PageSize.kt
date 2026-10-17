package com.flowfolio.core.model

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/** A page's size in PDF points (1/72 inch), after the page's own rotation is applied. */
data class PageSize(val widthPt: Float, val heightPt: Float) {
    init {
        require(widthPt > 0f && heightPt > 0f) { "page size must be positive: $widthPt x $heightPt" }
    }

    val aspect: Float get() = widthPt / heightPt

    /**
     * Largest pixel size with this page's aspect ratio that fits inside [maxWidthPx] x [maxHeightPx].
     * Rendering at exactly the displayed size is the first rule of the reader's memory budget.
     */
    fun fitInside(maxWidthPx: Int, maxHeightPx: Int): PixelSize {
        require(maxWidthPx > 0 && maxHeightPx > 0) { "viewport must be positive: $maxWidthPx x $maxHeightPx" }
        val scale = minOf(maxWidthPx / widthPt, maxHeightPx / heightPt)
        val w = max(1, minOf(maxWidthPx, floor(widthPt * scale + EPSILON).toInt()))
        val h = max(1, minOf(maxHeightPx, floor(heightPt * scale + EPSILON).toInt()))
        return PixelSize(w, h)
    }

    /** Pixel size at a given zoom, where 1.0 means [fitInside] the viewport. */
    fun atZoom(base: PixelSize, zoom: Float): PixelSize {
        require(zoom > 0f) { "zoom must be positive: $zoom" }
        return PixelSize(max(1, (base.width * zoom).roundToInt()), max(1, (base.height * zoom).roundToInt()))
    }

    private companion object {
        const val EPSILON = 1e-3f
    }
}

data class PixelSize(val width: Int, val height: Int) {
    /** Bytes for an ARGB_8888 image of this size. */
    val argbBytes: Long get() = width.toLong() * height * 4
}
