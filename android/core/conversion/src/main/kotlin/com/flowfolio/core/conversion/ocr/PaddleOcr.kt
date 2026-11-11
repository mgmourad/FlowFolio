package com.flowfolio.core.conversion.ocr

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * PaddleOCR's PP-OCR detection and recognition (ADR-0054), done the way RapidOCR 3.10 does them, so the spike's
 * numbers hold (docs/spikes/2026-11-ocr):
 * - **detection** (DB): the page reduced to at most 2000 px, then sized in multiples of 32 (short side at least 736),
 *   BGR scaled to -1..1; the probability map cut at 0.3 and dilated; each region fitted with its minimum-area
 *   **rotated** rectangle, kept when the mean probability inside reaches 0.5, then grown by 1.6 x area / perimeter;
 * - **recognition** (CTC): each line cut out along its rectangle (so a tilted scan line comes out straight), scaled
 *   to 48 px high (at least 320 wide, zero padded), greedily decoded; a line's confidence is its kept characters'
 *   mean probability.
 *
 * Lines come back top to bottom, left to right. Arabic models read in visual order: [rightToLeft] turns them back.
 */
class PaddleOcr(
    private val detector: OnnxModel,
    private val recognizer: OnnxModel,
    /**
     * The recognizer's symbols, one per line, as in its `character` metadata. Passed in, not read from the model:
     * ONNX Runtime's Java API hands metadata over as JNI "modified UTF-8", which mangles 4-byte characters and
     * swallows line breaks around them (PP-OCRv6's 18,708 symbols arrive as 18,166, and the space falls off the end).
     */
    symbols: String,
    private val engine: String,
    private val rightToLeft: Boolean = false
) : OcrReader {
    private val alphabet: List<String> = buildList {
        add("") // CTC blank
        // As Python's splitlines(): a trailing newline makes no empty symbol (it would shift the space below).
        addAll(symbols.lines().dropLastWhile { it.isEmpty() })
        add(" ")
    }

    override fun read(image: OcrImage): OcrPage = read(image, detect(image))

    /** Reads the lines found at [rects] (from [detect], so a caller that already detected them need not again). */
    internal fun read(image: OcrImage, rects: List<RotatedRect>): OcrPage {
        val lines = rects.mapNotNull { rect ->
            val (text, confidence) = recognise(image, rect)
            val read = if (rightToLeft) RtlText.visualToLogical(text) else text
            read.trim().takeIf { it.isNotEmpty() }
                ?.let { OcrLine(it, rect.bounds(image.width, image.height), confidence) }
        }
        return OcrPage(readingOrder(lines), engine)
    }

    internal fun detect(image: OcrImage): List<RotatedRect> {
        // As RapidOCR: the page reduced so its long side is at most 2000 px, then the detector's own sizing.
        val reduce = min(1f, MAX_SIDE.toFloat() / max(image.width, image.height))
        val rw = (image.width * reduce).toInt()
        val rh = (image.height * reduce).toInt()
        val grow = if (min(rw, rh) < LIMIT_SIDE) LIMIT_SIDE.toFloat() / min(rw, rh) else 1f
        val w = max(STRIDE, roundHalfEven(rw * grow / STRIDE) * STRIDE)
        val h = max(STRIDE, roundHalfEven(rh * grow / STRIDE) * STRIDE)
        val whole = RotatedRect.upright(0f, 0f, image.width.toFloat(), image.height.toFloat())
        val input = Pixels.chw(image, whole, w, h, w)
        val prob = detector.run(input, longArrayOf(1, CHANNELS.toLong(), h.toLong(), w.toLong())).data
        val sx = image.width.toFloat() / w
        val sy = image.height.toFloat() / h
        return Regions.rects(prob, w, h).map { it.scaled(sx, sy) }
    }

    internal fun recognise(image: OcrImage, rect: RotatedRect): Pair<String, Float> {
        val resizedW = ceil(REC_HEIGHT * rect.width / rect.height).toInt().coerceAtLeast(1)
        val width = max(REC_MIN_WIDTH, resizedW)
        val input = Pixels.chw(image, rect, resizedW, REC_HEIGHT, width)
        val out = recognizer.run(input, longArrayOf(1, CHANNELS.toLong(), REC_HEIGHT.toLong(), width.toLong()))
        return Ctc.decode(out.data, out.shape[1].toInt(), out.shape[2].toInt(), alphabet)
    }

    /** Lines top to bottom; lines whose middles share a row go left to right (right to left for Arabic). */
    private fun readingOrder(lines: List<OcrLine>): List<OcrLine> {
        val rows = ArrayList<MutableList<OcrLine>>()
        for (line in lines.sortedBy { it.box.top }) {
            val mid = (line.box.top + line.box.bottom) / 2
            val row = rows.lastOrNull()?.takeIf { r -> r.any { mid in it.box.top..it.box.bottom } }
            if (row != null) row += line else rows += mutableListOf(line)
        }
        return rows.flatMap { row ->
            if (rightToLeft) row.sortedByDescending { it.box.left } else row.sortedBy { it.box.left }
        }
    }

    override fun toString() = engine

    private companion object {
        const val LIMIT_SIDE = 736
        const val MAX_SIDE = 2000
        const val STRIDE = 32
        const val CHANNELS = 3
        const val REC_HEIGHT = 48
        const val REC_MIN_WIDTH = 320

        /** Python's round(): halves go to the even neighbour, so map sizes match RapidOCR's. */
        fun roundHalfEven(v: Float): Int = Math.rint(v.toDouble()).toInt()
    }
}

/**
 * A rectangle at any angle: corner ([x], [y]) and two edges, ([ux], [uy]) along the text ([width] long) and
 * ([vx], [vy]) down it ([height] long), as unit vectors.
 */
@Suppress("LongParameterList")
internal class RotatedRect(
    val x: Float,
    val y: Float,
    val ux: Float,
    val uy: Float,
    val vx: Float,
    val vy: Float,
    val width: Float,
    val height: Float
) {
    fun corners(): List<Pair<Float, Float>> = listOf(
        x to y,
        x + ux * width to y + uy * width,
        x + ux * width + vx * height to y + uy * width + vy * height,
        x + vx * height to y + vy * height
    )

    fun scaled(sx: Float, sy: Float): RotatedRect {
        val wx = ux * width * sx
        val wy = uy * width * sy
        val hx = vx * height * sx
        val hy = vy * height * sy
        val wl = hypot(wx, wy)
        val hl = hypot(hx, hy)
        return RotatedRect(x * sx, y * sy, wx / wl, wy / wl, hx / hl, hy / hl, wl, hl)
    }

    /** The upright box around it, clipped to the image, for laying the line out on the page. */
    fun bounds(imageW: Int, imageH: Int): PixelBox {
        val c = corners()
        return PixelBox(
            c.minOf { it.first }.toInt().coerceIn(0, imageW - 1),
            c.minOf { it.second }.toInt().coerceIn(0, imageH - 1),
            ceil(c.maxOf { it.first }).toInt().coerceIn(1, imageW),
            ceil(c.maxOf { it.second }).toInt().coerceIn(1, imageH)
        )
    }

    companion object {
        fun upright(left: Float, top: Float, right: Float, bottom: Float) =
            RotatedRect(left, top, 1f, 0f, 0f, 1f, right - left, bottom - top)
    }
}

/** Image to network input: [rect] sampled bilinearly into [w] x [h], BGR, scaled to -1..1, zero padded to [padW]. */
internal object Pixels {
    fun chw(image: OcrImage, rect: RotatedRect, w: Int, h: Int, padW: Int): FloatArray {
        val out = FloatArray(CHANNELS * h * padW)
        val plane = h * padW
        val stepW = rect.width / w
        val stepH = rect.height / h
        for (j in 0 until h) {
            val down = (j + HALF) * stepH - HALF
            for (i in 0 until w) {
                val across = (i + HALF) * stepW - HALF
                val px = rect.x + rect.ux * across + rect.vx * down
                val py = rect.y + rect.uy * across + rect.vy * down
                val at = j * padW + i
                // BGR, as OpenCV loads images and PaddleOCR was trained. No allocation here: it runs per pixel.
                out[at] = (sample(image, px, py, BLUE) / MAX - HALF) / HALF
                out[plane + at] = (sample(image, px, py, GREEN) / MAX - HALF) / HALF
                out[2 * plane + at] = (sample(image, px, py, RED) / MAX - HALF) / HALF
            }
        }
        return out
    }

    private fun sample(image: OcrImage, fx: Float, fy: Float, shift: Int): Float {
        val x = fx.coerceIn(0f, (image.width - 1).toFloat())
        val y = fy.coerceIn(0f, (image.height - 1).toFloat())
        val x0 = x.toInt()
        val y0 = y.toInt()
        val x1 = min(x0 + 1, image.width - 1)
        val y1 = min(y0 + 1, image.height - 1)
        val top = lerp(ch(image, x0, y0, shift), ch(image, x1, y0, shift), x - x0)
        val bottom = lerp(ch(image, x0, y1, shift), ch(image, x1, y1, shift), x - x0)
        return lerp(top, bottom, y - y0)
    }

    private fun ch(image: OcrImage, x: Int, y: Int, shift: Int) =
        ((image.pixels[y * image.width + x] shr shift) and MAX_BYTE).toFloat()

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    private const val CHANNELS = 3
    private const val HALF = 0.5f
    private const val MAX = 255f
    private const val MAX_BYTE = 0xFF
    private const val BLUE = 0
    private const val GREEN = 8
    private const val RED = 16
}

/** DB post-processing: regions of the probability map as rotated rectangles, in map pixels. */
internal object Regions {
    fun rects(prob: FloatArray, w: Int, h: Int): List<RotatedRect> {
        val mask = dilate(BooleanArray(w * h) { prob[it] > THRESH }, w, h)
        val seen = BooleanArray(w * h)
        val stack = IntArray(w * h)
        val out = ArrayList<RotatedRect>()
        for (start in 0 until w * h) {
            if (mask[start] && !seen[start] && out.size < MAX_CANDIDATES) {
                val (rowMin, rowMax) = flood(start, mask, seen, stack, w, h)
                region(prob, w, h, outline(rowMin, rowMax))?.let { out += it }
            }
        }
        return out
    }

    /**
     * The region of [mask] connected (8-way) to [start], marked in [seen]: each row's leftmost and rightmost pixel,
     * enough for its convex hull. No allocation per pixel: this runs for every text pixel of the page.
     */
    @Suppress("LongParameterList")
    private fun flood(
        start: Int,
        mask: BooleanArray,
        seen: BooleanArray,
        stack: IntArray,
        w: Int,
        h: Int
    ): Pair<Map<Int, Int>, Map<Int, Int>> {
        val rowMin = HashMap<Int, Int>()
        val rowMax = HashMap<Int, Int>()
        var size = 0
        stack[size++] = start
        seen[start] = true
        while (size > 0) {
            val i = stack[--size]
            val x = i % w
            val y = i / w
            rowMin[y] = min(rowMin[y] ?: x, x)
            rowMax[y] = max(rowMax[y] ?: x, x)
            for (k in 0 until NEIGHBOURS) {
                val nx = x + DX[k]
                val ny = y + DY[k]
                val n = ny * w + nx
                val onMap = nx in 0 until w && ny in 0 until h
                if (onMap && mask[n] && !seen[n]) {
                    seen[n] = true
                    stack[size++] = n
                }
            }
        }
        return rowMin to rowMax
    }

    /** The corners of each row's pixel run: OpenCV's contours trace pixel outlines, so the hull uses pixel corners. */
    private fun outline(rowMin: Map<Int, Int>, rowMax: Map<Int, Int>): List<Pair<Float, Float>> =
        rowMin.flatMap { (y, x0) ->
            val x1 = rowMax.getValue(y) + 1f
            listOf(x0.toFloat() to y.toFloat(), x1 to y.toFloat(), x0.toFloat() to y + 1f, x1 to y + 1f)
        }

    /** The region kept when it is big enough and sure enough, grown as PaddleOCR's unclip does; else null. */
    private fun region(prob: FloatArray, w: Int, h: Int, points: List<Pair<Float, Float>>): RotatedRect? {
        val rect = MinAreaRect.of(points)
        if (min(rect.width, rect.height) < MIN_SIZE || meanInside(prob, w, h, rect) < BOX_THRESH) return null
        val d = rect.width * rect.height * UNCLIP / (2f * (rect.width + rect.height))
        val grown = RotatedRect(
            rect.x - rect.ux * d - rect.vx * d,
            rect.y - rect.uy * d - rect.vy * d,
            rect.ux,
            rect.uy,
            rect.vx,
            rect.vy,
            rect.width + 2 * d,
            rect.height + 2 * d
        )
        return grown.takeIf { min(it.width, it.height) >= MIN_SIZE + 2 }
    }

    /** The mean probability over the map pixels whose centres fall inside [rect]. */
    private fun meanInside(prob: FloatArray, w: Int, h: Int, rect: RotatedRect): Double {
        val c = rect.corners()
        val x0 = c.minOf { it.first }.toInt().coerceIn(0, w - 1)
        val x1 = ceil(c.maxOf { it.first }).toInt().coerceIn(0, w - 1)
        val y0 = c.minOf { it.second }.toInt().coerceIn(0, h - 1)
        val y1 = ceil(c.maxOf { it.second }).toInt().coerceIn(0, h - 1)
        var sum = 0.0
        var n = 0
        for (y in y0..y1) {
            for (x in x0..x1) {
                val dx = x + HALF - rect.x
                val dy = y + HALF - rect.y
                val a = dx * rect.ux + dy * rect.uy
                val b = dx * rect.vx + dy * rect.vy
                if (a in -EDGE..rect.width + EDGE && b in -EDGE..rect.height + EDGE) {
                    sum += prob[y * w + x]
                    n++
                }
            }
        }
        return if (n == 0) 0.0 else sum / n
    }

    /** 2 x 2 dilation, as OpenCV's with a kernel of ones. */
    private fun dilate(on: BooleanArray, w: Int, h: Int) = BooleanArray(w * h) { i ->
        val x = i % w
        val y = i / w
        on[i] || (x > 0 && on[i - 1]) || (y > 0 && on[i - w]) || (x > 0 && y > 0 && on[i - w - 1])
    }

    private const val THRESH = 0.3f
    private const val BOX_THRESH = 0.5
    private const val UNCLIP = 1.6f
    private const val MIN_SIZE = 3f
    private const val MAX_CANDIDATES = 1000
    private const val HALF = 0.5f
    private const val EDGE = 0.5f
    private const val NEIGHBOURS = 8
    private val DX = intArrayOf(-1, 0, 1, -1, 1, -1, 0, 1)
    private val DY = intArrayOf(-1, -1, -1, 0, 0, 1, 1, 1)
}

/**
 * The smallest rectangle around a set of points at any angle (OpenCV's minAreaRect): over the convex hull, the best of
 * the rectangles with one side on a hull edge. Its "width" edge is the one nearer horizontal, pointing right, and
 * its "height" edge points down.
 */
internal object MinAreaRect {
    fun of(points: List<Pair<Float, Float>>): RotatedRect {
        val hull = hull(points)
        if (hull.size < TRIANGLE) {
            return RotatedRect.upright(
                points.minOf { it.first },
                points.minOf { it.second },
                points.maxOf { it.first },
                points.maxOf { it.second }
            )
        }
        var best: RotatedRect? = null
        var bestArea = Float.MAX_VALUE
        for (i in hull.indices) {
            val (ax, ay) = hull[i]
            val (bx, by) = hull[(i + 1) % hull.size]
            val len = hypot(bx - ax, by - ay)
            if (len == 0f) continue
            val rect = around(hull, (bx - ax) / len, (by - ay) / len)
            val area = rect.width * rect.height
            if (area < bestArea) {
                bestArea = area
                best = rect
            }
        }
        return best!!
    }

    /** The rectangle around [hull] with one axis along (ex, ey), oriented as [of] promises. */
    private fun around(hull: List<Pair<Float, Float>>, ex: Float, ey: Float): RotatedRect {
        // Choose the axis nearer horizontal as "along", pointing right; the other as "down", pointing down.
        var ax = ex
        var ay = ey
        if (abs(ay) > abs(ax)) {
            ax = -ey
            ay = ex
        }
        if (ax < 0) {
            ax = -ax
            ay = -ay
        }
        var bx = -ay
        var by = ax
        if (by < 0) {
            bx = -bx
            by = -by
        }
        var minA = Float.MAX_VALUE
        var maxA = -Float.MAX_VALUE
        var minB = Float.MAX_VALUE
        var maxB = -Float.MAX_VALUE
        for ((px, py) in hull) {
            val a = px * ax + py * ay
            val b = px * bx + py * by
            minA = min(minA, a)
            maxA = max(maxA, a)
            minB = min(minB, b)
            maxB = max(maxB, b)
        }
        // The corner at (minA, minB), back in image coordinates.
        val x = minA * ax + minB * bx
        val y = minA * ay + minB * by
        return RotatedRect(x, y, ax, ay, bx, by, maxA - minA, maxB - minB)
    }

    /** Andrew's monotone chain. */
    private fun hull(points: List<Pair<Float, Float>>): List<Pair<Float, Float>> {
        val sorted = points.distinct().sortedWith(compareBy({ it.first }, { it.second }))
        if (sorted.size < TRIANGLE) return sorted
        val lower = chain(sorted)
        val upper = chain(sorted.asReversed())
        return lower.dropLast(1) + upper.dropLast(1)
    }

    private fun chain(points: List<Pair<Float, Float>>): List<Pair<Float, Float>> {
        val out = ArrayList<Pair<Float, Float>>()
        for (p in points) {
            while (out.size >= 2 && cross(out[out.size - 2], out.last(), p) <= 0) out.removeAt(out.size - 1)
            out += p
        }
        return out
    }

    private const val TRIANGLE = 3

    private fun cross(o: Pair<Float, Float>, a: Pair<Float, Float>, b: Pair<Float, Float>) =
        (a.first - o.first) * (b.second - o.second) - (a.second - o.second) * (b.first - o.first)
}

/** Greedy CTC decoding: the best symbol per step, repeats and blanks dropped; confidence is the kept ones' mean. */
internal object Ctc {
    fun decode(probs: FloatArray, steps: Int, classes: Int, alphabet: List<String>): Pair<String, Float> {
        val text = StringBuilder()
        var total = 0f
        var kept = 0
        var previous = -1
        for (t in 0 until steps) {
            var best = 0
            var bestP = probs[t * classes]
            for (c in 1 until classes) {
                val p = probs[t * classes + c]
                if (p > bestP) {
                    best = c
                    bestP = p
                }
            }
            if (best != 0 && best != previous && best < alphabet.size) {
                text.append(alphabet[best])
                total += bestP
                kept++
            }
            previous = best
        }
        return text.toString() to if (kept == 0) 0f else total / kept
    }
}
