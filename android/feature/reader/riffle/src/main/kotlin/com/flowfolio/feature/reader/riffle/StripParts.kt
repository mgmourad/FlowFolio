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
    val cloth: ImageBitmap
)

/**
 * The parts around the page block (the mock's draw order): the cover boards seen edge-on with their labels, the
 * bookmark flags on top, and the satin ribbon with its red pin rising from it into the
 * pages. Made once per size, theme, book and set of flags.
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
    private val ribbon = Path()
    private val chevrons = Path()
    private val ribbonBrush = Brush.horizontalGradient(
        0f to Color(0xFF6E0820),
        0.38f to if (dark) Color(0xFFFF2D5A) else Color(0xFFD4163B),
        0.5f to if (dark) Color(0xFFFF6D8A) else Color(0xFFEE4D6C),
        0.62f to if (dark) Color(0xFFE81F4C) else Color(0xFFC8102E),
        1f to Color(0xFF7A0A24),
        startX = -7 * d,
        endX = 7 * d
    )

    /** The pin at x = 0: a red shaft from inside the ribbon's top, through the edge into the pages, to a point. */
    private val shaft = Path().apply {
        val half = 1.3f * d
        val tip = g.top + blockHeight * 0.2f
        moveTo(-half, g.bottom + 8 * d)
        lineTo(-half, tip + 5 * d)
        lineTo(0f, tip)
        lineTo(half, tip + 5 * d)
        lineTo(half, g.bottom + 8 * d)
        close()
    }

    // The ribbon's satin reds, so the pin reads as part of it (owner, 2026-10-21).
    private val pinBrush = Brush.horizontalGradient(
        0f to Color(0xFF6E0820),
        0.35f to if (dark) Color(0xFFFF6D8A) else Color(0xFFEE4D6C),
        0.6f to if (dark) Color(0xFFE81F4C) else Color(0xFFC8102E),
        1f to Color(0xFF7A0A24),
        startX = -1.3f * d,
        endX = 1.3f * d
    )
    private val chevronStroke = Stroke(width = 1.3f * d, cap = StrokeCap.Round)
    private val ghostStroke = Stroke(width = 1.5f * d)
    private val glowStroke = Stroke(width = 6 * d)

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

    /** The pin at the reader's place and the satin ribbon hanging from it: the only riffle handle. */
    fun DrawScope.paintRibbon() {
        val grab = state.grab
        val sway =
            (sin(state.timeSeconds / 0.7f) * 1.4f * d * (1 - grab) - state.pinVelocity * 0.004f) * state.swayAmount
        val width = (12 + grab * 2) * d
        val top = g.bottom + 3 * d
        val bottom = g.ribbonBottom
        // A tap on the block wiggles the ribbon for 0.6 s (spec 5.3).
        val sinceWiggle = state.timeSeconds - state.wiggleAt
        val wiggle = if (sinceWiggle in 0f..0.6f) (1 - sinceWiggle / 0.6f) * sin(sinceWiggle / 0.04f) * 3 * d else 0f
        val x = state.pinX + wiggle
        val half = width / 2
        ribbon.rewind()
        ribbon.moveTo(-half + d, top)
        ribbon.lineTo(half - d, top)
        ribbon.quadraticTo(half + sway * 0.5f, (top + bottom) / 2, half + sway, bottom)
        ribbon.lineTo(sway, bottom - 7 * d)
        ribbon.lineTo(-half + sway, bottom)
        ribbon.quadraticTo(-half + sway * 0.5f, (top + bottom) / 2, -half + d, top)
        ribbon.close()
        paintPin(x)
        translate(left = x) {
            if (dark) drawPath(ribbon, Color(0xFFFF2D5A), alpha = 0.18f + 0.12f * grab, style = glowStroke)
            drawPath(ribbon, ribbonBrush)
            chevrons.rewind()
            val gy = (top + bottom) / 2 + 2 * d
            val gx = sway * 0.5f
            chevrons.moveTo(gx - 2 * d, gy - 3.5f * d)
            chevrons.lineTo(gx - 4.5f * d, gy)
            chevrons.lineTo(gx - 2 * d, gy + 3.5f * d)
            chevrons.moveTo(gx + 2 * d, gy - 3.5f * d)
            chevrons.lineTo(gx + 4.5f * d, gy)
            chevrons.lineTo(gx + 2 * d, gy + 3.5f * d)
            drawPath(chevrons, Color.White.copy(alpha = 0.75f), style = chevronStroke)
        }
        paintGhost(x, (top + bottom) / 2 + 2 * d)
    }

    /** The pin's shaft with a soft shadow beside it; the ribbon, drawn next, covers its foot. */
    private fun DrawScope.paintPin(x: Float) {
        translate(x + d, d) { drawPath(shaft, Color.Black, alpha = 0.3f) }
        translate(x) { drawPath(shaft, pinBrush) }
    }

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
