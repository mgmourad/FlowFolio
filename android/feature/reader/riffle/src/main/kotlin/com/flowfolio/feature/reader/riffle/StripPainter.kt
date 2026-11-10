@file:Suppress("MagicNumber") // the mock's drawing: sizes in dp, colours and shading stops, carried over as designed

package com.flowfolio.feature.reader.riffle

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The book's cover colours for the boards: [main] and a [dark] shade (spec 4.1), plus optional [pageColor] for the
 * riffled leaves and open book pages when matched to the reader's paper tone.
 *
 * With [ink] the fore edge is drawn monochrome (ADR-0052, the mock's `.edge`; owner, 2026-11-10): the leaves in
 * shades of the theme's ink, darker on the part already read, the chapter bands in [accent]. The leaves still bow
 * and riffle as before; only their colours change.
 */
data class BoardColors(
    val main: Color,
    val dark: Color,
    val pageColor: Color? = null,
    val ink: Color? = null,
    val accent: Color? = null
)

/**
 * Draws the fore edge (spec 4.1, the mock's draw order without its floor shadow, owner 2026-10-21): the book's
 * dark interior, the leaves in 18 shades, the chapter bleed bands, the block's shading. Made once per size, theme and
 * book; a frame only rewinds and refills its paths, so drawing allocates nothing.
 */
internal class StripPainter(
    private val state: RiffleScrubberState,
    private val g: StripGeometry,
    private val dark: Boolean,
    boards: BoardColors? = null
) {
    private val d = g.density
    private val blockHeight = g.bottom - g.top
    private val mid = (g.top + g.bottom) / 2

    private val mono = boards?.ink
    private val shades = Array(SHADES) { Path() }
    private val readShades = Array(SHADES) { Path() }
    private val readColors = Array(SHADES) { i ->
        val k = i / (SHADES - 1f)
        mono?.copy(alpha = MONO_READ_ALPHA + k * MONO_RANGE) ?: Color.Transparent
    }
    private val shadeColors = Array(SHADES) { i ->
        val m = MIN_SHADE + i / (SHADES - 1f) * SHADE_RANGE
        val page = boards?.pageColor
        if (mono != null) {
            mono.copy(alpha = MONO_ALPHA + i / (SHADES - 1f) * MONO_RANGE)
        } else if (page != null) {
            Color(
                (page.red * m).coerceIn(0f, 1f),
                (page.green * m).coerceIn(0f, 1f),
                (page.blue * m).coerceIn(0f, 1f),
                1f
            )
        } else {
            val base = if (dark) PAPER_DARK else PAPER_LIGHT
            Color(base[0] * m / 255f, base[1] * m / 255f, base[2] * m / 255f)
        }
    }

    // Leaves narrower than their spacing, so the dark between them shows and each page reads on its own.
    private val leafWidth = max(1f, (g.x1 - g.x0) / max(1, state.leaves.count) * 0.7f)
    private val leafStroke = Stroke(width = leafWidth, cap = StrokeCap.Butt)

    private val bands = BleedBands.of(state.book.sections, state.mapping)
    private val bandPaths = Array(BAND_COLORS) { Path() }
    private val bandColors = boards?.accent?.let { a -> List(BAND_COLORS) { a } }
        ?: ((if (dark) TABS_DARK else TABS_LIGHT) + (if (dark) GREY_DARK else GREY_LIGHT))
    private val bandHeight = (blockHeight - 6 * d) / BleedBands.BANDS

    private val interior = if (mono != null) {
        Brush.verticalGradient(listOf(mono.copy(alpha = MONO_FLOOR), mono.copy(alpha = MONO_FLOOR)))
    } else {
        Brush.verticalGradient(
            0f to if (dark) Color(0xFF120B26) else Color(0xFF2A1E14),
            0.5f to if (dark) Color(0xFF05020C) else Color(0xFF0D0805),
            1f to if (dark) Color(0xFF120B26) else Color(0xFF2A1E14),
            startY = g.top,
            endY = g.bottom
        )
    }
    private val shadow = if (mono != null) 0.35f else 1f
    private val shadeTopBottom = Brush.verticalGradient(
        0f to Color.Black.copy(alpha = 0.28f * shadow),
        0.18f to Color.Transparent,
        0.82f to Color.Transparent,
        1f to Color.Black.copy(alpha = 0.32f * shadow),
        startY = g.top,
        endY = g.bottom
    )
    private val shadeEnds = Brush.horizontalGradient(
        0f to Color.Black.copy(alpha = 0.3f * shadow),
        0.05f to Color.Transparent,
        0.95f to Color.Transparent,
        1f to Color.Black.copy(alpha = 0.3f * shadow),
        startX = g.x0,
        endX = g.x1
    )

    fun DrawScope.paintBlock() {
        drawRect(interior, Offset(g.x0, g.top), Size(g.x1 - g.x0, blockHeight))
        paintLeaves()
        paintBands()
        drawRect(shadeTopBottom, Offset(g.x0, g.top), Size(g.x1 - g.x0, blockHeight))
        drawRect(shadeEnds, Offset(g.x0, g.top), Size(g.x1 - g.x0, blockHeight))
    }

    /** Each leaf a curve whose middle leads in its direction of travel, brighter near the gap and when moving fast. */
    private fun DrawScope.paintLeaves() {
        val leaves = state.leaves
        shades.forEach { it.rewind() }
        readShades.forEach { it.rewind() }
        val e = state.engagement
        for (j in 0 until leaves.count) {
            val x = leaves.x[j]
            val speedDp = abs(leaves.velocity[j]) / d
            val near = abs(state.restX(j) - state.pinX) / d
            var m = 0.9f - (j * 7 % 5) * 0.014f + min(0.12f, speedDp * 0.0007f) + 0.14f * e * exp(-near / 9f)
            if (j * 13 % 3 == 0) m -= 0.05f
            m = m.coerceIn(MIN_SHADE, MIN_SHADE + SHADE_RANGE)
            val shade = ((m - MIN_SHADE) / SHADE_RANGE * (SHADES - 1)).roundToInt()
            // Monochrome: the leaves already read (before the pin, or after it right to left) are darker.
            val read =
                mono != null &&
                    (if (state.book.rightToLeft) state.restX(j) > state.pinX else state.restX(j) < state.pinX)
            val path = if (read) readShades[shade] else shades[shade]
            path.moveTo(x, g.top)
            path.quadraticTo(x + leaves.bow[j] * 2, mid, x, g.bottom)
        }
        for (i in 0 until SHADES) drawPath(shades[i], shadeColors[i], style = leafStroke)
        if (mono != null) for (i in 0 until SHADES) drawPath(readShades[i], readColors[i], style = leafStroke)
    }

    /** Chapter bleed tabs printed on the page edges: a short segment on each leaf, following its bow. */
    private fun DrawScope.paintBands() {
        if (bands.isEmpty()) return
        bandPaths.forEach { it.rewind() }
        val leaves = state.leaves
        val per = state.sheetsPerLeaf
        for (band in bands) {
            val y0 = g.top + 3 * d + band.band * bandHeight
            val y1 = y0 + bandHeight * 0.8f
            val u0 = (y0 - g.top) / blockHeight
            val u1 = (y1 - g.top) / blockHeight
            val path = bandPaths[colorOf(band.section)]
            val last = min(leaves.count, (band.endSheet + per - 1) / per)
            for (j in band.firstSheet / per until last) {
                path.moveTo(leaves.x[j] + 4 * u0 * (1 - u0) * leaves.bow[j], y0)
                path.lineTo(leaves.x[j] + 4 * u1 * (1 - u1) * leaves.bow[j], y1)
            }
        }
        val alpha = if (mono != null) {
            MONO_BAND_ALPHA
        } else if (dark) {
            0.38f
        } else {
            0.32f
        }
        for (i in 0 until BAND_COLORS) drawPath(bandPaths[i], bandColors[i], alpha = alpha, style = leafStroke)
    }

    private fun colorOf(section: Section): Int =
        if (section.kind == SectionKind.CHAPTER) (section.number ?: 0) % TAB_COLORS else TAB_COLORS

    private companion object {
        const val SHADES = 18
        const val MONO_ALPHA = 0.16f
        const val MONO_READ_ALPHA = 0.34f
        const val MONO_RANGE = 0.22f
        const val MONO_FLOOR = 0.06f
        const val MONO_BAND_ALPHA = 0.7f
        const val MIN_SHADE = 0.55f
        const val SHADE_RANGE = 0.49f
        const val TAB_COLORS = 4
        const val BAND_COLORS = TAB_COLORS + 1
        val PAPER_DARK = intArrayOf(214, 203, 184)
        val PAPER_LIGHT = intArrayOf(243, 235, 219)
        val TABS_DARK = listOf(Color(0xFF14B8A6), Color(0xFFFF8A00), Color(0xFFC02F9A), Color(0xFF00B8D9))
        val TABS_LIGHT = listOf(Color(0xFF0F766E), Color(0xFFB45309), Color(0xFF7E22CE), Color(0xFF0E7490))
        val GREY_DARK = Color(0xFF9B9AA6)
        val GREY_LIGHT = Color(0xFF6B625A)
    }
}
