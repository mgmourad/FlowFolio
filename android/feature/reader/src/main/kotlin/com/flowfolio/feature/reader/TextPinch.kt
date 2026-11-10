package com.flowfolio.feature.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.glass
import com.flowfolio.core.model.ReaderText
import kotlin.math.roundToInt

/**
 * Pinching an EPUB page changes the text size (owner decision, 2026-10-16): while the fingers move, the size the
 * pinch would set shows on the page; letting go sets it, and the book is laid out again at the reader's place.
 * Pinching on a PDF zooms instead.
 */
@Stable
internal class TextPinch(private val apply: (Int) -> Unit) {
    private var start = ReaderText.DEFAULT_SIZE_SP
    private var scale = 1f

    /** The size the pinch would set, while pinching; null otherwise. */
    var preview by mutableStateOf<Int?>(null)
        private set

    fun begin(sizeSp: Int) {
        if (preview != null) return
        start = sizeSp
        scale = 1f
        preview = sizeSp
    }

    fun pinch(zoom: Float) {
        scale *= zoom
        preview = sizeFor(start, scale)
    }

    fun end() {
        val size = preview ?: return
        preview = null
        if (size != start) apply(size)
    }

    companion object {
        /** The text size a pinch of [scale] makes of [startSp], kept within the allowed sizes. */
        fun sizeFor(startSp: Int, scale: Float): Int =
            (startSp * scale).roundToInt().coerceIn(ReaderText.MIN_SIZE_SP, ReaderText.MAX_SIZE_SP)
    }
}

@Composable
internal fun rememberTextPinch(viewModel: ReaderViewModel): TextPinch =
    remember(viewModel) { TextPinch(viewModel::setTextSize) }

/** The pinch's size, large on glass in the middle of the page, while pinching. */
@Composable
internal fun BoxScope.TextPinchBadge(pinch: TextPinch) {
    val size = pinch.preview ?: return
    Box(
        Modifier
            .align(Alignment.Center)
            .glass(RoundedCornerShape(20.dp))
            .padding(horizontal = 28.dp, vertical = 16.dp)
            .testTag(ReaderTestTags.TEXT_PINCH)
    ) {
        Text("Aa  $size", style = TextStyle(fontSize = 34.sp), color = FlowFolio.palette.ink)
    }
}
