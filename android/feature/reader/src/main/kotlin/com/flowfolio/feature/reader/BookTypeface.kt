package com.flowfolio.feature.reader

import android.graphics.Typeface
import android.graphics.fonts.Font
import android.graphics.fonts.FontFamily
import android.graphics.fonts.FontStyle
import android.util.Log
import com.flowfolio.core.data.OpenedEpub
import com.flowfolio.core.epub.EpubFonts
import java.io.IOException
import java.nio.ByteBuffer

/**
 * The book's own body font (M3, ADR-0030) as one typeface: each face the book ships for its body family (regular,
 * bold, italic, bold italic) where it can be read, with the system serif behind it for characters it lacks. Null when
 * the book ships no usable font for its body text; the reader then sets it in Literata.
 */
internal object BookTypeface {
    fun load(opened: OpenedEpub): Typeface? {
        val family = EpubFonts.bodyFamily(opened.pkg, opened.archive)
        var builder: FontFamily.Builder? = null
        for (face in family?.faces.orEmpty()) {
            val font = font(opened, face.path, face.bold, face.italic) ?: continue
            builder = try {
                builder?.apply { addFont(font) } ?: FontFamily.Builder(font)
            } catch (_: IllegalArgumentException) {
                builder // two faces of one style: the first stays
            }
        }
        return builder?.let {
            Log.i(TAG, "book font ${family?.family}: ${family?.faces?.size} faces")
            Typeface.CustomFallbackBuilder(it.build()).setSystemFallback(FALLBACK).build()
        }
    }

    private fun font(opened: OpenedEpub, path: String, bold: Boolean, italic: Boolean): Font? {
        val bytes = EpubFonts.fontBytes(opened.pkg, opened.archive, path) ?: return null
        val buffer = ByteBuffer.allocateDirect(bytes.size).put(bytes).apply { flip() }
        return try {
            Font.Builder(buffer)
                .setWeight(if (bold) FontStyle.FONT_WEIGHT_BOLD else FontStyle.FONT_WEIGHT_NORMAL)
                .setSlant(if (italic) FontStyle.FONT_SLANT_ITALIC else FontStyle.FONT_SLANT_UPRIGHT)
                .build()
        } catch (_: IOException) {
            null // not a font the system can read: the book is set in Literata instead
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private const val FALLBACK = "serif"
    private const val TAG = "FlowFolioFonts"
}
