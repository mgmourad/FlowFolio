package com.flowfolio.feature.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.flowfolio.core.designsystem.R as DesignR
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * Narrator Mode's album art (owner, 2026-11-10): the book's cover on a square, with the app icon on its bottom right.
 *
 * The cover is **fitted**, never stretched: scaled by its longer side to fit inside the square with a margin, while the
 * room left over (a portrait cover leaves its sides) is filled with the same cover enlarged and softened. A book
 * without a cover gets its title on paper.
 */
internal object NarratorArt {
    const val SIZE = 720

    fun compose(context: Context, coverPath: String?, title: String): Bitmap {
        val out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val cover = coverPath?.let { decode(it) }
        val margin = SIZE * MARGIN
        val coverRect = if (cover != null) {
            drawBackdrop(canvas, cover)
            ArtGeometry.fit(cover.width, cover.height, SIZE - 2 * margin, margin, margin).toRectF().also {
                drawCover(canvas, cover, it)
            }
        } else {
            drawPaper(canvas, title, margin)
        }
        drawIcon(context, canvas, coverRect)
        cover?.recycle()
        return out
    }

    /** The cover filling the square, blurred by drawing a tiny copy large, and dimmed so the cover stands on it. */
    private fun drawBackdrop(canvas: Canvas, cover: Bitmap) {
        val square = ArtGeometry.cropSquare(cover.width, cover.height).toRect()
        val tiny = Bitmap.createBitmap(BLUR_SIDE, BLUR_SIDE, Bitmap.Config.ARGB_8888)
        Canvas(tiny).drawBitmap(cover, square, RectF(0f, 0f, BLUR_SIDE.toFloat(), BLUR_SIDE.toFloat()), FILTERED)
        canvas.drawBitmap(tiny, null, RectF(0f, 0f, SIZE.toFloat(), SIZE.toFloat()), FILTERED)
        tiny.recycle()
        canvas.drawColor(Color.argb(BACKDROP_DIM, 0, 0, 0))
    }

    private fun drawCover(canvas: Canvas, cover: Bitmap, rect: RectF) {
        val radius = SIZE * CORNER
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(SHADOW_ALPHA, 0, 0, 0)
            setShadowLayer(SIZE * SHADOW_BLUR, 0f, SIZE * SHADOW_DROP, Color.argb(SHADOW_ALPHA, 0, 0, 0))
        }
        canvas.drawRoundRect(rect, radius, radius, shadow)
        canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) })
        canvas.drawBitmap(cover, null, rect, FILTERED)
        canvas.restore()
    }

    /** No cover: warm paper with the title set in the middle, a 2:3 page like a cover would be. */
    private fun drawPaper(canvas: Canvas, title: String, margin: Float): RectF {
        canvas.drawColor(PAPER_EDGE)
        val rect = ArtGeometry.fit(PAGE_WIDTH, PAGE_HEIGHT, SIZE - 2 * margin, margin, margin).toRectF()
        canvas.drawRoundRect(rect, SIZE * CORNER, SIZE * CORNER, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PAPER })
        val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK
            textSize = SIZE * TITLE_SIZE
            isFakeBoldText = true
        }
        val width = (rect.width() * TITLE_WIDTH).toInt()
        val layout = StaticLayout.Builder.obtain(title, 0, title.length, text, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setMaxLines(TITLE_LINES)
            .setEllipsize(android.text.TextUtils.TruncateAt.END)
            .build()
        canvas.save()
        canvas.translate(rect.centerX() - width / 2f, rect.centerY() - layout.height / 2f)
        layout.draw(canvas)
        canvas.restore()
        return rect
    }

    /** The app icon as a badge on the cover's bottom right corner, rounded like a launcher icon. */
    private fun drawIcon(context: Context, canvas: Canvas, cover: RectF) {
        val icon = BitmapFactory.decodeResource(context.resources, DesignR.drawable.flowfolio_icon) ?: return
        val side = SIZE * ICON
        val inset = SIZE * ICON_INSET
        val rect = RectF(
            cover.right - inset - side,
            cover.bottom - inset - side,
            cover.right - inset,
            cover.bottom - inset
        )
        val radius = side * ICON_CORNER
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            setShadowLayer(SIZE * SHADOW_BLUR / 2, 0f, SIZE * SHADOW_DROP / 2, Color.argb(SHADOW_ALPHA, 0, 0, 0))
        }
        val ringWidth = SIZE * ICON_RING
        canvas.drawRoundRect(
            RectF(rect.left - ringWidth, rect.top - ringWidth, rect.right + ringWidth, rect.bottom + ringWidth),
            radius + ringWidth,
            radius + ringWidth,
            ring
        )
        canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) })
        canvas.drawBitmap(icon, null, rect, FILTERED)
        canvas.restore()
        icon.recycle()
    }

    /** The cover file, read at about the art's size (a cover can be several megapixels). */
    private fun decode(path: String): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        if (File(path).isFile) BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= SIZE) sample *= 2
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private val FILTERED = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private const val MARGIN = 0.08f
    private const val PAGE_WIDTH = 2
    private const val PAGE_HEIGHT = 3
    private const val CORNER = 0.018f
    private const val BLUR_SIDE = 12
    private const val BACKDROP_DIM = 110
    private const val SHADOW_ALPHA = 120
    private const val SHADOW_BLUR = 0.03f
    private const val SHADOW_DROP = 0.012f
    private const val ICON = 0.17f
    private const val ICON_INSET = 0.03f
    private const val ICON_CORNER = 0.24f
    private const val ICON_RING = 0.006f
    private const val TITLE_SIZE = 0.06f
    private const val TITLE_WIDTH = 0.8f
    private const val TITLE_LINES = 5
    private val PAPER = Color.rgb(0xF6, 0xEF, 0xE3)
    private val PAPER_EDGE = Color.rgb(0xE2, 0x60, 0x2A)
    private val INK = Color.rgb(0x2A, 0x21, 0x18)
}

/**
 * The album art's geometry, apart from [NarratorArt] (whose paints are Android objects) so it is tested on the JVM:
 * the cover is fitted, never stretched (owner, 2026-11-10).
 */
internal object ArtGeometry {
    /** A rectangle, kept free of Android classes so its geometry is tested on the JVM. */
    data class Box(val left: Float, val top: Float, val width: Float, val height: Float) {
        val right: Float get() = left + width
        val bottom: Float get() = top + height
    }

    /** [width] x [height] scaled to fit [box] x [box] at [left], [top] keeping its aspect, centred. */
    fun fit(width: Int, height: Int, box: Float, left: Float = 0f, top: Float = 0f): Box {
        val scale = min(box / width, box / height)
        val w = width * scale
        val h = height * scale
        return Box(left + (box - w) / 2, top + (box - h) / 2, w, h)
    }

    /** The square [width] x [height] cuts from the middle of itself (the backdrop fills, so it crops). */
    fun cropSquare(width: Int, height: Int): Box {
        val side = min(width, height).toFloat()
        return Box((width - side) / 2, (height - side) / 2, side, side)
    }
}

private fun ArtGeometry.Box.toRectF() = RectF(left, top, right, bottom)

private fun ArtGeometry.Box.toRect() = android.graphics.Rect(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
