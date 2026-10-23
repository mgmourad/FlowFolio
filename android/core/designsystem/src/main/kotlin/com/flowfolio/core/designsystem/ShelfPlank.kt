package com.flowfolio.core.designsystem

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.unit.dp

/**
 * The shelf the books stand on (`.plank`): a 15 dp board with the mock's face gradient and drop shadow. Dark mode
 * lights its top edge with a cyan line and glow; light mode gives it a warm top highlight and wood grain. A shelf with
 * its own colour ([accent], owner 2026-10-22) lights its edge in that colour instead, in both modes.
 */
@Composable
fun ShelfPlank(modifier: Modifier = Modifier, accent: Color? = null) {
    val p = FlowFolio.palette
    val lit = accent ?: p.item
    val shape = RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp, bottomStart = 6.dp, bottomEnd = 6.dp)
    Spacer(
        modifier
            .fillMaxWidth()
            .height(15.dp)
            .softShadow(shape, Color.Black.copy(alpha = 0.6f), blur = 24.dp, offsetY = 16.dp, spread = (-8).dp)
            .drawWithCache {
                val outline = shape.createOutline(size, layoutDirection, this)
                val face = Brush.verticalGradient(listOf(p.shelfTop, p.shelfBottom))
                val edge = 2.dp.toPx()
                onDrawBehind {
                    if (p.dark && p.itemGlow.color.alpha > 0f) {
                        // `0 -2px 18px rgba(0,240,255,.55)`: the glow rising off the edge.
                        blurredShape(
                            lit.copy(alpha = 0.55f),
                            18.dp.toPx(),
                            BlurShape.Rect,
                            Offset(0f, -edge),
                            androidx.compose.ui.geometry.Size(size.width, 2 * edge)
                        )
                    }
                    drawOutline(outline, face)
                    if (p.dark || accent != null) {
                        drawRect(lit, Offset(0f, -edge), androidx.compose.ui.geometry.Size(size.width, edge))
                    }
                    if (!p.dark) {
                        if (accent == null) {
                            drawRect(
                                WOOD_HIGHLIGHT,
                                Offset(0f, -1.dp.toPx()),
                                androidx.compose.ui.geometry.Size(size.width, 1.dp.toPx())
                            )
                        }
                        // `repeating-linear-gradient(92deg, ...)` at 35%: a darker grain line every 23 px.
                        var x = 9.dp.toPx()
                        val step = 23.dp.toPx()
                        while (x < size.width) {
                            drawRect(
                                WOOD_GRAIN,
                                Offset(x, 0f),
                                androidx.compose.ui.geometry.Size(1.dp.toPx(), size.height)
                            )
                            x += step
                        }
                    }
                }
            }
    )
}

private val WOOD_HIGHLIGHT = Color(0xB3FFE6BE)
private val WOOD_GRAIN = Color(0x2E50280A)
