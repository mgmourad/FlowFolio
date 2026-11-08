package com.flowfolio.core.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Scales the element while pressed, on the mock's spring (`.icon-btn:active`, `.btn:active`). */
@Composable
private fun Modifier.pressScale(source: MutableInteractionSource, pressed: Float): Modifier {
    val isPressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (isPressed) pressed else 1f,
        tween(PRESS_MS, easing = Motion.Spring),
        label = "press"
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** A card (`.hero-card`, `.set-group`, `.stat`): the mock's gradient fill, soft shadow and top highlight. */
@Composable
fun FlowFolioCard(
    modifier: Modifier = Modifier,
    radius: Dp = 22.dp,
    padding: PaddingValues = PaddingValues(18.dp),
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(radius)
    Box(modifier.card(shape).clip(shape).padding(padding), content = content)
}

/** A filter chip (`.chip`): hint text on a card; selected is lit in the title colour with a thin ring. */
@Composable
fun FlowFolioChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glyph: Glyph? = null,
    glow: Boolean = false
) {
    val p = FlowFolio.palette
    val shape = RoundedCornerShape(50)
    // Unselected chips keep a faint ring in the accent, so they stand out from the page (owner, 2026-10-22).
    val ring = Modifier.ringOutline(shape, p.title.copy(alpha = if (selected) 0.6f else RING_ALPHA))
    // The chosen one can be lit as well as ringed (owner, M5 round 3), for chips that have to read as set at a
    // glance. A cached soft shadow, never a GPU blur: these sit in scrolling rows (known issue 21).
    val lit = if (selected && glow) {
        Modifier.softShadow(shape, p.title.copy(alpha = CHIP_GLOW), blur = CHIP_GLOW_BLUR, spread = CHIP_GLOW_SPREAD)
    } else {
        Modifier
    }
    Box(
        modifier
            .then(lit)
            .card(shape)
            .then(ring)
            .clip(shape)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        // A glyph before the name, in the name's colour (owner, M3T2).
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (glyph != null) {
                FlowFolioGlyph(
                    glyph,
                    contentDescription = null,
                    color = if (selected) p.title else p.hint,
                    size = CHIP_GLYPH,
                    glow = false,
                    modifier = Modifier.padding(end = 6.dp)
                )
            }
            Text(text, style = FlowFolioType.chip.merge(if (selected) FlowFolio.title else FlowFolio.hint))
        }
    }
}

private val CHIP_GLYPH = 18.dp
private val CHIP_GLOW_BLUR = 16.dp
private val CHIP_GLOW_SPREAD = 1.dp
private const val CHIP_GLOW = 0.55f

/** A 1.5 dp inner ring (`inset 0 0 0 1.5px`). */
private fun Modifier.ringOutline(shape: androidx.compose.ui.graphics.Shape, color: Color): Modifier = drawBehind {
    val outline = shape.createOutline(size, layoutDirection, this)
    val inset = 0.75.dp.toPx()
    when (outline) {
        is androidx.compose.ui.graphics.Outline.Rounded -> drawRoundRect(
            color,
            topLeft = Offset(inset, inset),
            size = Size(size.width - 2 * inset, size.height - 2 * inset),
            cornerRadius = CornerRadius(outline.roundRect.topLeftCornerRadius.x - inset),
            style = Stroke(1.5.dp.toPx())
        )

        else -> drawRect(color, style = Stroke(1.5.dp.toPx()))
    }
}

/** A small label (`.pill`): edition, page count, format. */
@Composable
fun FlowFolioPill(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Box(modifier.card(shape).clip(shape).padding(horizontal = 11.dp, vertical = 6.dp)) {
        Text(text, style = FlowFolioType.pill.merge(style))
    }
}

/** A 42 dp round button (`.icon-btn`) holding a glyph; shrinks to 0.9 on the spring when pressed. */
@Composable
fun FlowFolioIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glass: Boolean = false,
    size: Dp = 42.dp,
    glowing: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val p = FlowFolio.palette
    // Off glass, a faint ring in the accent sets the button off from the page (owner, 2026-10-22).
    val surface = if (glass) {
        Modifier.glass(CircleShape)
    } else {
        Modifier.card(CircleShape).ringOutline(CircleShape, p.title.copy(alpha = RING_ALPHA))
    }
    // The tool whose panel is open glows, so the two are clearly tied (owner, 2026-11-08).
    val glow = if (glowing) {
        Modifier
            .softShadow(CircleShape, p.title.copy(alpha = GLOW_ALPHA), blur = GLOW_BLUR, spread = GLOW_SPREAD)
            .ringOutline(CircleShape, p.title)
    } else {
        Modifier
    }
    Box(
        modifier
            .pressScale(source, ICON_PRESSED)
            .size(size)
            .then(glow)
            .then(surface)
            .clip(CircleShape)
            .clickable(source, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content
    )
}

/** The orange pill button (`.btn.primary`) with its own glow; [icon] sits before the label. */
@Composable
fun FlowFolioPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Narrows the side padding, for rows of buttons on a folded screen (owner, M4). */
    compact: Boolean = false,
    icon: (@Composable () -> Unit)? = null
) {
    val p = FlowFolio.palette
    val shape = RoundedCornerShape(50)
    val source = remember { MutableInteractionSource() }
    Row(
        modifier
            .pressScale(source, BUTTON_PRESSED)
            .softShadow(shape, p.title.copy(alpha = 0.8f), blur = 24.dp, offsetY = 10.dp, spread = (-8).dp)
            .clip(shape)
            .drawBehind {
                drawRect(Brush.verticalGradient(listOf(p.buttonTop, p.buttonBottom)))
                drawLine(Color.White.copy(alpha = 0.45f), Offset(0f, 0.5f), Offset(size.width, 0.5f), 1.dp.toPx())
            }
            .clickable(source, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (compact) 8.dp else 18.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(
            if (compact) 5.dp else 8.dp,
            Alignment.CenterHorizontally
        )
    ) {
        icon?.invoke()
        Text(
            text,
            style = FlowFolioType.button.copy(color = p.buttonInk),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** The quiet pill button (`.btn.ghost`): item-coloured text on a card. */
@Composable
fun FlowFolioGhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    val source = remember { MutableInteractionSource() }
    Box(
        modifier
            .pressScale(source, BUTTON_PRESSED)
            .card(shape)
            .clip(shape)
            .clickable(source, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 11.dp)
    ) {
        Text(text, style = FlowFolioType.button.merge(FlowFolio.item))
    }
}

/** A row of [content] separated like the mock's grouped settings rows. */
@Composable
fun FlowFolioRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        content = content
    )
}

private const val PRESS_MS = 250
private const val ICON_PRESSED = 0.9f
private const val BUTTON_PRESSED = 0.94f

/** The faint accent ring around buttons and chips off glass. */
private const val RING_ALPHA = 0.28f
private const val GLOW_ALPHA = 0.7f
private val GLOW_BLUR = 24.dp
private val GLOW_SPREAD = 2.dp
