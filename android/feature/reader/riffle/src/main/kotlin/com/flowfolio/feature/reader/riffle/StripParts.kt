@file:Suppress("MagicNumber") // the mock's drawing: sizes in dp, colours and gradient stops, carried over as designed

package com.flowfolio.feature.reader.riffle

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.flowfolio.core.designsystem.UiFamily
import kotlin.math.sin
import kotlin.random.Random

/** What the strip's parts are drawn with: the book's board colours, its labels' text, and the cloth texture. */
internal class StripAssets(
    val boards: BoardColors,
    val measurer: TextMeasurer,
    val labels: Pair<String, String>,
    val cloth: ImageBitmap,
    /** The theme's colour, for the glass marker's glow. */
    val glow: Color,
    /** The riffle book's outline: the theme's darkest colour, or black on a light theme. */
    val ink: Color
)

/**
 * The parts around the page block (the mock's draw order): the cover boards seen edge-on with their labels, the
 * bookmark flags on top, and the handle: a glass marker on the block and the riffle book under it (owner, M3T1).
 * Made once per size, theme, book and set of flags.
 */
internal class StripParts(
    private val state: RiffleScrubberState,
    private val g: StripGeometry,
    private val dark: Boolean,
    assets: StripAssets
) {
    private val boards = assets.boards
    private val measurer = assets.measurer
    private val labels = assets.labels
    private val cloth = assets.cloth
    private val d = g.density
    private val blockHeight = g.bottom - g.top

    // Front board on the left, back on the right; mirrored for right-to-left books.
    private val left = board(g.x0 - 15 * d, leftEdge = true)
    private val right = board(g.x1 + 3 * d, leftEdge = false)
    private val leftBrush = boardBrush(g.x0 - 15 * d, boards, 0.55f)
    private val rightBrush = boardBrush(g.x1 + 3 * d, boards, 0.45f)
    private val clothBrush = ShaderBrush(ImageShader(cloth, TileMode.Repeated, TileMode.Repeated))
    private val labelStyle = TextStyle(
        fontFamily = UiFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 8.sp,
        color = if (dark) Color(0x80ECE8FF) else Color(0x8C2E2219)
    )
    private val frontLabel = measurer.measure(if (state.book.rightToLeft) labels.second else labels.first, labelStyle)
    private val backLabel = measurer.measure(if (state.book.rightToLeft) labels.first else labels.second, labelStyle)

    private val stacks = state.stacks
    private val badgeStyle = TextStyle(fontFamily = UiFamily, fontWeight = FontWeight.Bold, fontSize = 7.sp)
    private val badges = stacks.map {
        if (it.stack.count >
            1
        ) {
            measurer.measure("${it.stack.count}", badgeStyle)
        } else {
            null
        }
    }
    private val outline = Stroke(width = 1.2f * d)

    /** A flag at x = 0: a tab with rounded top corners, standing 18 dp above the block and 4 dp into it. */
    private val flag = Path().apply {
        addRoundRect(
            RoundRect(
                left = -d,
                top = g.top - 18 * d,
                right = 9 * d,
                bottom = g.top + 4 * d,
                topLeftCornerRadius = CornerRadius(3 * d),
                topRightCornerRadius = CornerRadius(3 * d)
            )
        )
    }
    private val glow = assets.glow
    private val ink = assets.ink
    private val ghostStroke = Stroke(width = 1.5f * d)

    // The riffle book (owner, M3T1): an open book under the strip at the reader's place, its pages flipping while it
    // moves. Its shapes are made once; a frame only rewinds the flipping pages.
    private val bookWidth = BOOK_WIDTH_DP * d
    private val bookHeight = BOOK_HEIGHT_DP * d
    private val bookLine =
        Stroke(width = 1.6f * d, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
    private val pageFill = if (dark) Color(0xFFD6CBB8) else Color(0xFFF3EBDB)
    private val book = openBook()
    private val flipping = Array(FLIP_PAGES) { Path() }
    private var flipPhase = 0f
    private var lastFrame = Float.NaN

    // The glass marker (owner, M3T1): a vertical bar of glass on the page block at the reader's place, glowing in the
    // theme's colour; never wider than the book.
    private val markerWidth = minOf(MARKER_WIDTH_DP * d, bookWidth)
    private val markerTop = g.top - 4 * d
    private val markerBottom = g.bottom + 4 * d
    private val glass = Brush.horizontalGradient(
        0f to glow.copy(alpha = 0.55f),
        0.3f to Color.White.copy(alpha = 0.85f),
        0.45f to glow.copy(alpha = 0.7f),
        1f to glow.copy(alpha = 0.95f),
        startX = -markerWidth / 2,
        endX = markerWidth / 2
    )

    fun DrawScope.paintBoards() {
        drawPath(left, leftBrush)
        drawPath(left, clothBrush)
        drawPath(right, rightBrush)
        drawPath(right, clothBrush)
        val lx = g.x0 - 15 * d
        val rx = g.x1 + 3 * d
        val highlight = Color.White.copy(alpha = 0.2f)
        drawRect(highlight, Offset(lx + 2 * d, g.top - 3 * d), Size(1.2f * d, blockHeight + 6 * d))
        drawRect(highlight, Offset(rx + 12 * d - 3.2f * d, g.top - 3 * d), Size(1.2f * d, blockHeight + 6 * d))
        val gilt = Color(0x8CFFD78C)
        drawRect(gilt, Offset(lx + 5.5f * d, g.top + 4 * d), Size(d, blockHeight - 8 * d))
        drawRect(gilt, Offset(rx + 5.5f * d, g.top + 4 * d), Size(d, blockHeight - 8 * d))
        label(frontLabel, g.x0 - 9 * d)
        label(backLabel, g.x1 + 9 * d)
    }

    private fun DrawScope.label(text: TextLayoutResult, centerX: Float) =
        drawText(text, topLeft = Offset(centerX - text.size.width / 2f, g.bottom + 16 * d - text.firstBaseline))

    /** Flags (or stacks of them) standing out of the top of the block, at their leaf's current place. */
    fun DrawScope.paintFlags() {
        val shadow = Color.Black.copy(alpha = 0.25f)
        for (i in stacks.indices) {
            val stack = stacks[i].stack
            val x = state.leaves.x[stacks[i].leaf]
            // A hovered flag lifts 4 dp and gets an outline (spec 4.3).
            val lift = if (state.hovered == i) 4 * d else 0f
            val top = g.top - 18 * d - lift
            translate(x, d - lift) { drawPath(flag, shadow) }
            translate(x, -lift) { drawPath(flag, Color(stack.color), alpha = 0.92f) }
            if (lift > 0f) {
                translate(x, -lift) { drawPath(flag, if (dark) Color.White else Color(0xFF2E2219), style = outline) }
            }
            drawRect(Color.White.copy(alpha = 0.4f), Offset(x - d, top + 1.5f * d), Size(10 * d, 1.4f * d))
            badges[i]?.let { badge ->
                val center = Offset(x + 4 * d, top - 5 * d)
                drawCircle(if (dark) Color.White else Color(0xFF2E2219), 5.5f * d, center)
                drawText(
                    badge,
                    color = if (dark) Color(0xFF1D0A47) else Color.White,
                    topLeft = Offset(center.x - badge.size.width / 2f, center.y - badge.size.height / 2f)
                )
            }
        }
    }

    /**
     * The handle (owner, M3T1): the glass marker on the block at the reader's place and, under the strip, the riffle
     * book that riffles. They move together; the book sways a little at rest and wiggles when the block is tapped.
     */
    fun DrawScope.paintRibbon() {
        val grab = state.grab
        val sway =
            (sin(state.timeSeconds / 0.7f) * 1.4f * d * (1 - grab) - state.pinVelocity * 0.004f) * state.swayAmount
        // A tap on the block wiggles the book for 0.6 s (spec 5.3).
        val sinceWiggle = state.timeSeconds - state.wiggleAt
        val wiggle = if (sinceWiggle in 0f..0.6f) (1 - sinceWiggle / 0.6f) * sin(sinceWiggle / 0.04f) * 3 * d else 0f
        val x = state.pinX + wiggle
        paintMarker(state.pinX, grab)
        val cy = (g.bottom + g.ribbonBottom) / 2 + 3 * d
        translate(x + sway, cy) { paintBook(grab) }
        paintGhost(x, cy)
    }

    /** The glass bar: a glow of widening strokes in the theme's colour (no blur), the glass, and its bright edge. */
    private fun DrawScope.paintMarker(x: Float, grab: Float) {
        val half = markerWidth / 2
        val radius = CornerRadius(half)
        translate(x) {
            for (k in GLOW_RINGS downTo 1) {
                val spread = k * 2.2f * d
                drawRoundRect(
                    glow,
                    Offset(-half - spread, markerTop - spread),
                    Size(markerWidth + 2 * spread, markerBottom - markerTop + 2 * spread),
                    CornerRadius(half + spread),
                    alpha = (if (dark) 0.10f else 0.07f) * (1 + grab)
                )
            }
            drawRoundRect(
                Color.Black.copy(alpha = 0.35f),
                Offset(-half + d, markerTop + d),
                Size(
                    markerWidth,
                    markerBottom - markerTop
                ),
                radius
            )
            drawRoundRect(glass, Offset(-half, markerTop), Size(markerWidth, markerBottom - markerTop), radius)
            drawRoundRect(
                Color.White.copy(alpha = 0.7f),
                Offset(-half, markerTop),
                Size(markerWidth, markerBottom - markerTop),
                radius,
                style = Stroke(width = 0.8f * d)
            )
            drawRect(
                Color.White.copy(alpha = 0.8f),
                Offset(-half * 0.45f, markerTop + 3 * d),
                Size(
                    0.9f * d,
                    (markerBottom - markerTop) * 0.4f
                )
            )
        }
    }

    /**
     * The open book at (0, 0): its pages, and while it moves pages flipping over the spine in the direction of travel,
     * faster the faster it goes (forward turns them right to left, as reading does).
     */
    private fun DrawScope.paintBook(grab: Float) {
        drawPath(book.pages, pageFill)
        drawPath(book.cover, ink, style = bookLine)
        drawPath(book.pages, ink, style = bookLine)
        val now = state.timeSeconds
        val speed = (kotlin.math.abs(state.pinVelocity) / (FLIP_PX_PER_TURN_DP * d)).coerceAtMost(MAX_FLIPS)
        if (!lastFrame.isNaN()) flipPhase += (now - lastFrame).coerceIn(0f, 0.1f) * speed
        lastFrame = now
        if (speed < MIN_FLIPS && grab < 0.05f) return
        val forward = (state.pinVelocity >= 0f) != state.book.rightToLeft
        val top = -bookHeight / 2 + 3 * d
        val bottom = bookHeight / 2 - 1 * d
        for (k in 0 until FLIP_PAGES) {
            val raw = (flipPhase + k.toFloat() / FLIP_PAGES) % 1f
            val p = if (forward) raw else 1f - raw
            val angle = Math.PI.toFloat() * p
            val outer = kotlin.math.cos(angle) * bookWidth / 2
            val lift = sin(angle) * 6 * d
            val page = flipping[k]
            page.rewind()
            page.moveTo(0f, top)
            page.quadraticTo(outer * 0.5f, top - lift - 2 * d, outer, top - lift)
            page.lineTo(outer, bottom - lift)
            page.quadraticTo(outer * 0.5f, bottom - lift * 0.6f, 0f, bottom)
            page.close()
            drawPath(page, pageFill)
            drawPath(page, ink, style = bookLine, alpha = 0.85f)
        }
    }

    /** The book's still parts: its two pages bowing up from the spine, and the cover's edge under them. */
    private fun openBook(): BookShape {
        val w = bookWidth / 2
        val h = bookHeight / 2
        val pages = Path().apply {
            moveTo(0f, -h + 3 * d)
            quadraticTo(-w * 0.5f, -h - 1 * d, -w, -h + 1.5f * d)
            lineTo(-w, h - 2 * d)
            quadraticTo(-w * 0.5f, h - 5 * d, 0f, h - 1 * d)
            quadraticTo(w * 0.5f, h - 5 * d, w, h - 2 * d)
            lineTo(w, -h + 1.5f * d)
            quadraticTo(w * 0.5f, -h - 1 * d, 0f, -h + 3 * d)
            close()
            moveTo(0f, -h + 3 * d)
            lineTo(0f, h - 1 * d)
        }
        val cover = Path().apply {
            moveTo(-w - 2.5f * d, -h + 4 * d)
            lineTo(-w - 2.5f * d, h + 1 * d)
            quadraticTo(-w * 0.5f, h - 2 * d, 0f, h + 2 * d)
            quadraticTo(w * 0.5f, h - 2 * d, w + 2.5f * d, h + 1 * d)
            lineTo(w + 2.5f * d, -h + 4 * d)
        }
        return BookShape(pages, cover)
    }

    private class BookShape(val pages: Path, val cover: Path)

    /** The demo's fingertip on the ribbon. */
    private fun DrawScope.paintGhost(x: Float, y: Float) {
        val a = state.ghostAlpha
        if (a <= 0.02f) return
        val color = if (dark) Color.White else Color(0xFF2E2219)
        drawCircle(color, 15 * d, Offset(x, y), alpha = a * 0.55f)
        drawCircle(color, 15 * d, Offset(x, y), alpha = a * 0.9f, style = ghostStroke)
    }

    private fun board(x: Float, leftEdge: Boolean): Path {
        val outer = CornerRadius(5 * d)
        val inner = CornerRadius(2 * d)
        val rect = RoundRect(
            left = x,
            top = g.top - 5 * d,
            right = x + 12 * d,
            bottom = g.bottom + 5 * d,
            topLeftCornerRadius = if (leftEdge) outer else inner,
            topRightCornerRadius = if (leftEdge) inner else outer,
            bottomRightCornerRadius = if (leftEdge) inner else outer,
            bottomLeftCornerRadius = if (leftEdge) outer else inner
        )
        return Path().apply { addRoundRect(rect) }
    }

    private fun boardBrush(x: Float, colors: BoardColors, peak: Float) = Brush.horizontalGradient(
        0f to colors.dark,
        peak to colors.main,
        1f to colors.dark,
        startX = x,
        endX = x + 12 * d
    )

    companion object {
        private const val CLOTH_PX = 24
        private const val BOOK_WIDTH_DP = 38f
        private const val BOOK_HEIGHT_DP = 26f
        private const val MARKER_WIDTH_DP = 8f
        private const val GLOW_RINGS = 4
        private const val FLIP_PAGES = 3

        /** One page turns over for each this many dp the book travels per second; at most [MAX_FLIPS] a second. */
        private const val FLIP_PX_PER_TURN_DP = 80f
        private const val MAX_FLIPS = 10f
        private const val MIN_FLIPS = 0.15f
        private const val THREAD = (13 shl 24) or 0xFFFFFF

        /** The boards' cloth: a few light and dark specks and faint threads (the mock's pattern), drawn once. */
        fun cloth(): ImageBitmap {
            val bmp = android.graphics.Bitmap.createBitmap(CLOTH_PX, CLOTH_PX, android.graphics.Bitmap.Config.ARGB_8888)
            val random = Random(9)
            repeat(160) {
                val light = random.nextFloat() < 0.5f
                val alpha = ((0.04f + random.nextFloat() * 0.07f) * 255).toInt()
                val rgb = if (light) 0xFFFFFF else 0x000000
                bmp.setPixel(random.nextInt(CLOTH_PX), random.nextInt(CLOTH_PX), (alpha shl 24) or rgb)
            }
            for (y in 0 until CLOTH_PX step 3) {
                for (xx in 0 until CLOTH_PX) if (bmp.getPixel(xx, y) == 0) bmp.setPixel(xx, y, THREAD)
            }
            return bmp.asImageBitmap()
        }
    }
}
