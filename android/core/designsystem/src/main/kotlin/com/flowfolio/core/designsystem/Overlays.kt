package com.flowfolio.core.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * A long-press tooltip (`.tip`, ADR-0052) for an icon button or any control named only by a glyph: ink on the
 * background colour, 8 dp corners, above its anchor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlowFolioTooltip(text: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val p = FlowFolio.palette
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = {
            PlainTooltip(containerColor = p.ink, contentColor = p.bg, shape = RoundedCornerShape(8.dp)) {
                Text(text, style = FlowFolioType.body.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium))
            }
        },
        state = rememberTooltipState(),
        modifier = modifier,
        content = content
    )
}

/**
 * A bottom sheet (`.sheet`, ADR-0052): the surface with 28 dp top corners and a small handle, over the theme's scrim.
 * Material's sheet underneath gives it drag to dismiss and the predictive back gesture. [title] is set in Fraunces;
 * [trailing] sits at the title's end (a Done or ⋯ button).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlowFolioSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val p = FlowFolio.palette
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = state,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = p.surface,
        contentColor = p.ink,
        tonalElevation = 0.dp,
        scrimColor = p.scrim,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .background(p.line, CircleShape)
            )
        }
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 26.dp).navigationBarsPadding()) {
            if (title != null || trailing != null) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        title.orEmpty(),
                        style = FlowFolioType.dialogTitle.copy(color = p.ink),
                        modifier = Modifier.weight(1f)
                    )
                    trailing?.invoke()
                }
            }
            content()
        }
    }
}

/** A text field inside a [FlowFolioDialog] (`.dlg input.tf`). */
class DialogField(val value: String, val onValueChange: (String) -> Unit, val placeholder: String = "")

/** Colour swatches inside a [FlowFolioDialog] (`.swatchrow`): [selected] is ringed in ink. */
class DialogSwatches(val colors: List<Color>, val names: List<String>, val selected: Int, val onSelect: (Int) -> Unit)

/** One action button at the foot of a [FlowFolioDialog]. */
class DialogAction(val text: String, val style: ButtonStyle = ButtonStyle.GHOST, val onClick: () -> Unit)

/**
 * A dialog (`.dlg`, ADR-0052): an icon tile, a Fraunces title, a body in the second ink, then an optional
 * [field] and [swatches], and the [actions] at the end. [destructive] turns the tile to the danger colours; give its
 * confirming action [ButtonStyle.DANGER]. It settles in from 96 % with a fade.
 */
@Composable
fun FlowFolioDialog(
    onDismissRequest: () -> Unit,
    title: String,
    actions: List<DialogAction>,
    modifier: Modifier = Modifier,
    glyph: Glyph? = null,
    body: String? = null,
    destructive: Boolean = false,
    field: DialogField? = null,
    swatches: DialogSwatches? = null,
    extra: (@Composable ColumnScope.() -> Unit)? = null
) {
    val p = FlowFolio.palette
    val reduced = reducedMotion
    val enter = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(Motion.MEDIUM_MS - 40, easing = Motion.Emphasized)) }
    Dialog(onDismissRequest, DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier
                .widthIn(max = 340.dp)
                .padding(horizontal = 28.dp)
                .graphicsLayer {
                    val s = DIALOG_FROM + (1f - DIALOG_FROM) * enter.value
                    scaleX = s
                    scaleY = s
                    alpha = enter.value
                }
                .softShadow(RoundedCornerShape(26.dp), Color.Black.copy(alpha = 0.3f), blur = 60.dp, offsetY = 24.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(p.surface)
                .padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 16.dp)
        ) {
            if (glyph != null) {
                Box(
                    Modifier
                        .size(44.dp)
                        .background(if (destructive) p.dangerSoft else p.accentSoft, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    FlowFolioGlyph(glyph, null, color = if (destructive) p.danger else p.accent, size = 22.dp)
                }
                Spacer(Modifier.height(14.dp))
            }
            Text(title, style = FlowFolioType.dialogTitle.copy(fontSize = 20.sp, color = p.ink))
            if (body != null) {
                Spacer(Modifier.height(8.dp))
                Text(body, style = FlowFolioType.body.copy(color = p.ink2, lineHeight = 21.sp))
            }
            if (field != null) DialogTextField(field)
            if (swatches != null) DialogSwatchRow(swatches)
            extra?.invoke(this)
            Row(
                Modifier.fillMaxWidth().padding(top = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                actions.forEach { FlowFolioButton(it.text, it.onClick, style = it.style, small = true) }
            }
        }
    }
}

@Composable
private fun DialogTextField(field: DialogField) {
    val p = FlowFolio.palette
    val shape = RoundedCornerShape(14.dp)
    BasicTextField(
        value = field.value,
        onValueChange = field.onValueChange,
        singleLine = true,
        textStyle = FlowFolioType.body.copy(fontSize = 15.sp, color = p.ink),
        cursorBrush = SolidColor(p.accent),
        keyboardOptions = KeyboardOptions.Default,
        modifier = Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .background(p.bg, shape)
            .border(1.5.dp, p.accent, shape)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        decorationBox = { inner ->
            Box {
                if (field.value.isEmpty()) {
                    Text(field.placeholder, style = FlowFolioType.body.copy(fontSize = 15.sp, color = p.ink3))
                }
                inner()
            }
        }
    )
}

@Composable
private fun DialogSwatchRow(swatches: DialogSwatches) {
    val p = FlowFolio.palette
    FlowRow(
        Modifier.padding(top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        swatches.colors.forEachIndexed { i, c ->
            val on = i == swatches.selected
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .then(if (on) Modifier.border(2.dp, p.ink, CircleShape) else Modifier)
                    .clickable(role = Role.RadioButton) { swatches.onSelect(i) }
                    .semantics {
                        contentDescription = swatches.names.getOrElse(i) { "" }
                        selected = on
                    }
                    .padding(4.dp)
                    .background(c, CircleShape)
            )
        }
    }
}

/** A snackbar's look and content: [glyph] before the message, and an optional action ("Undo"). */
class FlowFolioSnackbarVisuals(
    override val message: String,
    val glyph: Glyph = Glyph.Check,
    override val actionLabel: String? = null,
    override val duration: SnackbarDuration =
        if (actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short,
    override val withDismissAction: Boolean = false
) : SnackbarVisuals

/** Shows a FlowFolio snackbar; returns whether its action was tapped. */
suspend fun SnackbarHostState.showFlowFolio(
    message: String,
    glyph: Glyph = Glyph.Check,
    actionLabel: String? = null
): SnackbarResult = showSnackbar(FlowFolioSnackbarVisuals(message, glyph, actionLabel))

/** Where FlowFolio snackbars appear; plain snackbars sent to the same state are drawn the same way. */
@Composable
fun FlowFolioSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(state, modifier) { data -> FlowFolioSnackbar(data) }
}

/**
 * A snackbar (`.toast`, ADR-0052): ink with the background colour as its text, 16 dp corners, a glyph, the message
 * and the action in the soft accent. It rises 14 dp into place.
 */
@Composable
fun FlowFolioSnackbar(data: SnackbarData, modifier: Modifier = Modifier) {
    val p = FlowFolio.palette
    val visuals = data.visuals
    val glyph = (visuals as? FlowFolioSnackbarVisuals)?.glyph ?: Glyph.Check
    val reduced = reducedMotion
    val rise = remember(data) { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(data) { rise.animateTo(1f, tween(Motion.RISE_MS + 20, easing = Motion.Rise)) }
    Row(
        modifier
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .widthIn(max = 560.dp)
            .graphicsLayer {
                translationY = (1f - rise.value) * TOAST_RISE.toPx()
                alpha = rise.value
            }
            .softShadow(RoundedCornerShape(16.dp), Color.Black.copy(alpha = 0.25f), blur = 30.dp, offsetY = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(p.ink)
            .padding(start = 16.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        FlowFolioGlyph(glyph, null, color = p.bg, size = 18.dp, glow = false)
        Text(
            visuals.message,
            style = FlowFolioType.body.copy(fontSize = 13.5.sp, color = p.bg),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        visuals.actionLabel?.let { label ->
            Text(
                label.uppercase(),
                style = FlowFolioType.button.copy(fontSize = 13.sp, letterSpacing = 0.5.sp, color = p.accentSoft),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button) { data.performAction() }
                    .padding(horizontal = 6.dp, vertical = 6.dp)
            )
        }
    }
}

/** Padding inside sheets, for content that lays out its own rows edge to edge. */
val SheetContentPadding = PaddingValues(horizontal = 20.dp)

private const val DIALOG_FROM = 0.96f
private val TOAST_RISE = 14.dp
