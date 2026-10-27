package com.flowfolio.feature.reader

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.flowfolio.feature.reader.layout.ReaderLayout
import com.flowfolio.feature.reader.layout.Spreads

/** Where pages sit on screen, mirroring how the reader lays them out (images at their exact size, no scaling). */
internal object PageFrames {
    /** A page's image placed at ([x], [y]) with size [w] x [h] pixels. */
    data class Frame(val page: Int, val x: Int, val y: Int, val w: Int, val h: Int)

    /** [rightToLeft] mirrors the layout, as the reader shows right-to-left books (a spread starts on the right). */
    fun of(
        state: ReaderState.Ready,
        screen: IntSize,
        rightToLeft: Boolean = false,
        imageSize: (Int) -> IntSize?
    ): List<Frame> {
        val frames = mutableListOf<Frame>()
        fun place(page: Int, x: (IntSize) -> Int) {
            val size = imageSize(page) ?: return
            frames += Frame(page, x(size), (screen.height - size.height) / 2, size.width, size.height)
        }
        val half = screen.width / 2
        when (state.layout) {
            ReaderLayout.SINGLE -> place(state.unit) { (screen.width - it.width) / 2 }

            ReaderLayout.SPREAD -> {
                // Left page hugs the spine from the left, right page from the right (see SpreadReader).
                val left = Spreads.leftOf(state.unit, state.pageCount, state.coverAlone)
                val right = Spreads.rightOf(state.unit, state.pageCount, state.coverAlone)
                if (left >= 0) place(left) { half - it.width }
                if (right >= 0) place(right) { half }
            }
        }
        return if (rightToLeft) frames.map { it.copy(x = screen.width - it.x - it.w) } else frames
    }

    /** The link under [tap], if any; links are given per page as fractions of the page. */
    fun linkAt(tap: Offset, frames: List<Frame>, links: Map<Int, List<ReaderLink>>): ReaderLink? {
        for (f in frames) {
            val fx = (tap.x - f.x) / f.w
            val fy = (tap.y - f.y) / f.h
            if (fx !in 0f..1f || fy !in 0f..1f) continue
            // Small links (footnote markers, single digits) are hard to hit; allow a little slack around them.
            val slackX = TOUCH_SLACK_PX / f.w
            val slackY = TOUCH_SLACK_PX / f.h
            // Scroll's frames overlap where one page's text ends and the next one's begins: try every page there.
            val hit = links[f.page].orEmpty().firstOrNull { it.contains(fx, fy) }
                ?: links[f.page].orEmpty().firstOrNull {
                    fx in (it.left - slackX)..(it.right + slackX) && fy in (it.top - slackY)..(it.bottom + slackY)
                }
            if (hit != null) return hit
        }
        return null
    }

    /** What a tap on the open book should do. */
    sealed interface TapTarget {
        data class Jump(val page: Int) : TapTarget

        data class Web(val uri: String) : TapTarget

        /** An EPUB footnote reference, tapped at [at]: its note opens in a popover there. */
        data class Note(val href: String, val at: Offset) : TapTarget

        /** An EPUB equation or table shown smaller than its size: it opens zoomable. */
        data class Island(val id: Int) : TapTarget

        /** An EPUB picture: it opens zoomable (M4). */
        data class Picture(val path: String) : TapTarget

        data object Centre : TapTarget

        data object Page : TapTarget
    }

    /** Links win over everything; a tap in the centre toggles the controls; anything else turns pages. */
    fun route(tap: Offset, size: IntSize, frames: List<Frame>, links: Map<Int, List<ReaderLink>>): TapTarget =
        when (val target = linkAt(tap, frames, links)?.target) {
            is LinkTarget.Page -> TapTarget.Jump(target.page)
            is LinkTarget.Note -> TapTarget.Note(target.href, tap)
            is LinkTarget.Island -> TapTarget.Island(target.id)
            is LinkTarget.Picture -> TapTarget.Picture(target.path)
            is LinkTarget.Web if isOpenable(target.uri) -> TapTarget.Web(target.uri)
            else -> if (isCentre(tap, size)) TapTarget.Centre else TapTarget.Page
        }

    /** Web links FlowFolio will offer to open; anything else (javascript:, file:, intent:...) is ignored. */
    fun isOpenable(uri: String): Boolean = OPENABLE_SCHEMES.any { uri.startsWith(it, ignoreCase = true) }

    private const val TOUCH_SLACK_PX = 24f
    private val OPENABLE_SCHEMES = listOf("https://", "http://", "mailto:")
}
