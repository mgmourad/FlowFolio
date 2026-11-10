package com.flowfolio.core.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Scales the element while pressed, on the mock's spring (`.icon-btn:active`, `.btn:active`). */
@Composable
private fun Modifier.pressScale(source: MutableInteractionSource, pressed: Float): Modifier {
    val isPressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) pressed else 1f, Motion.pressSpec(), label = "press")
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** A card (`.card`, ADR-0052): the surface with a hairline and the soft two-layer shadow, 22 dp round. */
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

/**
 * A filter chip (`.chip`, ADR-0052): 34 dp, a hairline and the second ink; selected, it fills with ink and its text
 * takes the background colour. [count] trails the name, dimmed. [glow] is kept for callers and no longer lights it.
 */
@Composable
fun FlowFolioChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glyph: Glyph? = null,
    @Suppress("UNUSED_PARAMETER") glow: Boolean = false,
    count: Int? = null
) {
    val p = FlowFolio.palette
    val shape = RoundedCornerShape(50)
    val fill by animateColorAsState(if (selected) p.ink else Color.Transparent, Motion.colorSpec(), label = "chipFill")
    val ink by animateColorAsState(if (selected) p.bg else p.ink2, Motion.colorSpec(), label = "chipInk")
    val edge by animateColorAsState(if (selected) p.ink else p.line, Motion.colorSpec(), label = "chipEdge")
    val source = remember { MutableInteractionSource() }
    Row(
        modifier
            .pressScale(source, CHIP_PRESSED)
            .heightIn(min = CHIP_HEIGHT)
            .clip(shape)
            .background(fill, shape)
            .border(EDGE_WIDTH, edge, shape)
            .selectable(selected, interactionSource = source, indication = null, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (glyph !=
            null
        ) {
            FlowFolioGlyph(glyph, contentDescription = null, color = ink, size = CHIP_GLYPH, glow = false)
        }
        Text(text, style = FlowFolioType.chip.copy(color = ink), maxLines = 1)
        if (count != null) {
            Text(
                count.toString(),
                style = FlowFolioType.chip.copy(color = ink.copy(alpha = COUNT_ALPHA), fontSize = COUNT_SIZE)
            )
        }
    }
}

private val CHIP_GLYPH = 14.dp
private val CHIP_HEIGHT = 34.dp
private val COUNT_SIZE = 11.5.sp
private const val COUNT_ALPHA = 0.7f
private const val CHIP_PRESSED = 0.96f

/** A small label (`.pill`): edition, page count, format. */
@Composable
fun FlowFolioPill(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    val p = FlowFolio.palette
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .clip(shape)
            .background(p.surface2, shape)
            .padding(horizontal = 11.dp, vertical = 5.dp)
    ) {
        Text(text, style = FlowFolioType.pill.merge(style))
    }
}

/**
 * A round icon button (`.ib`, ADR-0052): 42 dp (36 dp small) on the surface with a hairline, 48 dp of touch target
 * whatever its drawn size, shrinking a little when pressed. [tooltip] is shown on a long press (and read by TalkBack
 * as the button's name). [glowing] marks the tool whose panel is open: the soft accent fill with the accent ring.
 * [glass] puts it on the glass of the reader's bars.
 */
@Composable
fun FlowFolioIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glass: Boolean = false,
    size: Dp = 42.dp,
    glowing: Boolean = false,
    tooltip: String? = null,
    ghost: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val p = FlowFolio.palette
    val active by animateFloatAsState(if (glowing) 1f else 0f, Motion.springSpec(), label = "active")
    val surface = when {
        glass -> Modifier.glass(CircleShape)
        ghost -> Modifier
        else -> Modifier.background(p.surface, CircleShape).border(EDGE_WIDTH, p.line, CircleShape)
    }
    val button: @Composable () -> Unit = {
        Box(
            modifier
                .minimumInteractiveComponentSize()
                .pressScale(source, ICON_PRESSED)
                .size(size)
                .then(surface)
                .drawWithContent {
                    if (active > 0f) {
                        drawCircle(p.accentSoft.copy(alpha = active))
                        drawCircle(
                            p.accent.copy(alpha = active),
                            radius = this.size.minDimension / 2f - 0.75.dp.toPx(),
                            style = Stroke(1.5.dp.toPx())
                        )
                    }
                    drawContent()
                }
                .clip(CircleShape)
                .clickable(source, indication = null, role = Role.Button, onClick = onClick)
                .then(if (tooltip != null) Modifier.semantics { contentDescription = tooltip } else Modifier),
            contentAlignment = Alignment.Center,
            content = content
        )
    }
    if (tooltip == null) button() else FlowFolioTooltip(tooltip) { button() }
}

/** What a [FlowFolioButton] looks like (`.btn`, `.btn.ghost`, `.btn.soft`, `.btn.danger`). */
enum class ButtonStyle { PRIMARY, GHOST, SOFT, DANGER }

/**
 * A pill button (`.btn`, ADR-0052): 42 dp tall (34 dp [small]), flat, in the accent (primary), with a hairline
 * (ghost), on the soft accent (soft) or in the danger colour. [icon] sits before the label in the label's colour.
 */
@Composable
fun FlowFolioButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ButtonStyle = ButtonStyle.PRIMARY,
    small: Boolean = false,
    enabled: Boolean = true,
    icon: Glyph? = null
) {
    val p = FlowFolio.palette
    val shape = RoundedCornerShape(50)
    val source = remember { MutableInteractionSource() }
    val (fill, ink) = when (style) {
        ButtonStyle.PRIMARY -> p.accent to p.accentInk
        ButtonStyle.GHOST -> Color.Transparent to p.ink
        ButtonStyle.SOFT -> p.accentSoft to p.accent
        ButtonStyle.DANGER -> p.danger to (if (p.dark) p.bg else Color.White)
    }
    Row(
        modifier
            .pressScale(source, BUTTON_PRESSED)
            .heightIn(min = if (small) SMALL_BUTTON else BUTTON)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clip(shape)
            .background(fill, shape)
            .then(if (style == ButtonStyle.GHOST) Modifier.border(EDGE_WIDTH, p.line, shape) else Modifier)
            .clickable(source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (small) 14.dp else 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
    ) {
        if (icon != null) FlowFolioGlyph(icon, contentDescription = null, color = ink, size = 16.dp, glow = false)
        Text(
            text,
            style = FlowFolioType.button.copy(color = ink, fontSize = if (small) 13.sp else 14.5.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** The primary pill button (`.btn`): flat accent, the accent's ink (ADR-0052); [icon] sits before the label. */
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
            .heightIn(min = BUTTON)
            .clip(shape)
            .background(p.accent, shape)
            .clickable(source, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (compact) 10.dp else 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(
            if (compact) 5.dp else 8.dp,
            Alignment.CenterHorizontally
        )
    ) {
        icon?.invoke()
        Text(
            text,
            style = FlowFolioType.button.copy(color = p.accentInk),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** The quiet pill button (`.btn.ghost`): ink on a hairline. */
@Composable
fun FlowFolioGhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) =
    FlowFolioButton(text, onClick, modifier, style = ButtonStyle.GHOST)

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

private const val ICON_PRESSED = 0.94f
private const val BUTTON_PRESSED = 0.97f
private const val DISABLED_ALPHA = 0.45f
private val BUTTON = 42.dp
private val SMALL_BUTTON = 34.dp
