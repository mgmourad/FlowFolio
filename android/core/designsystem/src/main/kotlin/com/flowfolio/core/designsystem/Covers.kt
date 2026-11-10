// The spine and sheen are the mock's CSS gradient stops, read as a table; the file holds the cover and its badges.
@file:Suppress("MagicNumber", "MatchingDeclarationName")

package com.flowfolio.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The small marks a cover carries at its bottom right (`.cov .bdg`, ADR-0052). */
enum class CoverBadge(val glyph: Glyph) {
    DYNAMIC_FLOW(Glyph.DynamicFlow),
    BOOKMARKED(Glyph.Bookmark),
    FAVORITE(Glyph.StarFilled)
}

/**
 * A book's cover, the same everywhere (`.cov`, ADR-0052): 2:3, with the spine's lit edge and shade on the left, a
 * faint sheen across, a soft drop shadow, and [badges] at the bottom right. The face is the book's own cover image or
 * the generated one ([FlatCover]). [Book3D] stays for the transitions that fly a book.
 *
 * Give it a width; the height follows the 2:3 aspect. [badgeSize] scales the badges for very small covers.
 */
@Composable
fun BookCover(
    face: BookFace,
    modifier: Modifier = Modifier,
    badges: Set<CoverBadge> = emptySet(),
    badgeSize: Dp = 20.dp
) {
    val shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 8.dp, bottomEnd = 8.dp)
    Box(
        modifier
            .aspectRatio(COVER_ASPECT)
            .softShadow(shape, Color.Black.copy(alpha = 0.45f), blur = 22.dp, offsetY = 14.dp, spread = (-12).dp)
            .softShadow(shape, Color.Black.copy(alpha = 0.18f), blur = 5.dp, offsetY = 2.dp)
            .clip(shape)
    ) {
        FlatCover(face, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().spineAndSheen())
        if (badges.isNotEmpty()) {
            Row(
                Modifier.align(Alignment.BottomEnd).padding(end = badgeSize * 0.3f, bottom = badgeSize * 0.25f),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                CoverBadge.entries.filter { it in badges }.forEach { badge ->
                    Box(
                        Modifier
                            .size(badgeSize)
                            .background(Color.Black.copy(alpha = BADGE_ALPHA), RoundedCornerShape(badgeSize * 0.35f)),
                        contentAlignment = Alignment.Center
                    ) {
                        FlowFolioGlyph(badge.glyph, null, color = Color.White, size = badgeSize * 0.6f, glow = false)
                    }
                }
            }
        }
    }
}

/**
 * The spine (`inset 3px 0 0 rgba(255,255,255,.16), inset 7px 0 9px -2px rgba(0,0,0,.28)`) and the sheen
 * (`linear-gradient(115deg, white .14, transparent 38%, transparent 70%, black .12)`), drawn over the face.
 */
private fun Modifier.spineAndSheen(): Modifier = drawWithCache {
    val lit = 3.dp.toPx()
    val shade = 9.dp.toPx()
    val spineShade = Brush.horizontalGradient(
        0f to Color.Black.copy(alpha = 0.28f),
        1f to Color.Transparent,
        startX = lit,
        endX = lit + shade
    )
    val sheen = Brush.linearGradient(
        0f to Color.White.copy(alpha = 0.14f),
        0.38f to Color.Transparent,
        0.7f to Color.Transparent,
        1f to Color.Black.copy(alpha = 0.12f),
        start = Offset(0f, size.height * SHEEN_TILT),
        end = Offset(size.width, size.height * (1f - SHEEN_TILT))
    )
    onDrawBehind {
        drawRect(sheen)
        drawRect(spineShade, Offset(lit, 0f), Size(shade, size.height))
        drawRect(Color.White.copy(alpha = 0.16f), Offset.Zero, Size(lit, size.height))
    }
}

/** Width over height of every cover (2:3). */
const val COVER_ASPECT = 2f / 3f

private const val BADGE_ALPHA = 0.42f
private const val SHEEN_TILT = 0.2f
