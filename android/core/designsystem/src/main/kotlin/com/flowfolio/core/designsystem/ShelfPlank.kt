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
    val shape = RoundedCornerShape(3.dp)
    // `.plank` (ADR-0052): 10 dp of surface 3 shading to the line colour, a short soft shadow under it; a shelf with a
    // colour of its own shows it as a 2 dp edge along the top.
    Spacer(
        modifier
            .fillMaxWidth()
            .height(10.dp)
            .softShadow(shape, Color.Black.copy(alpha = 0.35f), blur = 10.dp, offsetY = 6.dp, spread = (-6).dp)
            .drawWithCache {
                val outline = shape.createOutline(size, layoutDirection, this)
                val face = Brush.verticalGradient(listOf(p.shelfTop, p.shelfBottom))
                val edge = 2.dp.toPx()
                onDrawBehind {
                    drawOutline(outline, face)
                    if (accent != null) {
                        drawRect(accent, Offset.Zero, androidx.compose.ui.geometry.Size(size.width, edge))
                    }
                }
            }
    )
}
