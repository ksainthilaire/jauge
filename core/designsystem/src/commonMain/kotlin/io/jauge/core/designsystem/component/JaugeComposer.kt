package io.jauge.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.jauge.core.designsystem.icon.JaugeIcons
import io.jauge.core.designsystem.motion.rememberReducedMotion
import io.jauge.core.designsystem.theme.JaugeTheme

/** Minimum touch target of the composer controls (Material and Apple guidelines). */
private val TouchTarget = 48.dp

/** Tone of the line shown under the composer text ([ComposerStatus]). */
enum class ComposerStatusTone {
    /** Recognized input (mint, check mark). */
    SUCCESS,

    /** Gentle advice while the input is not usable yet (secondary text). */
    HINT,

    /** Friendly error after an attempt (coral). */
    ERROR,
}

/**
 * Hero input of the app, built like the AI composers: one calm surface holding the text field, an optional status
 * line, and a toolbar with the other ways to give the input ([tools]) and one main [action] on the right.
 *
 * The outline stays thin; it only darkens while the field has the focus, so the composer does not shout before the
 * person has typed anything. The toolbar wraps on several lines with large text sizes instead of cutting labels.
 *
 * @param value current text.
 * @param onValueChange called with the new text.
 * @param placeholder hint shown while empty.
 * @param modifier modifier of the composer.
 * @param fieldLabel accessibility label of the field ("Lien de l'offre"), read with its content.
 * @param leadingIcon icon before the text.
 * @param clearLabel accessibility label of the clear button, shown while the text is not empty.
 * @param onClear tap on the clear button.
 * @param isError true to draw the outline in coral.
 * @param keyboardOptions keyboard type and action.
 * @param keyboardActions keyboard action callbacks.
 * @param status optional line under the text (usually a [ComposerStatus]).
 * @param tools secondary ways to give the input ([ComposerToolButton]).
 * @param action main action on the right of the toolbar ([ComposerActionButton]).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JaugeComposer(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    fieldLabel: String = placeholder,
    leadingIcon: ImageVector? = null,
    clearLabel: String? = null,
    onClear: () -> Unit = {},
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    status: (@Composable () -> Unit)? = null,
    tools: @Composable FlowRowScope.() -> Unit = {},
    action: @Composable () -> Unit,
) {
    val colors = JaugeTheme.colors
    val typography = JaugeTheme.typography
    val reducedMotion = rememberReducedMotion()
    var isFocused by remember { mutableStateOf(false) }
    val targetOutline = when {
        isError -> colors.crowded.accent
        isFocused -> colors.textSecondary
        else -> colors.outline
    }
    val outline by animateColorAsState(targetOutline, animationSpec = if (reducedMotion) snap() else tween(180), label = "outline")
    val shape = JaugeTheme.radii.cardShape
    val textStyle = typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 22.sp, color = colors.textPrimary)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface, shape)
            .border(if (isFocused || isError) 1.5.dp else 1.dp, outline, shape)
            .padding(10.dp)
            .then(if (reducedMotion) Modifier else Modifier.animateContentSize(tween(200))),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = TouchTarget)) {
            leadingIcon?.let {
                Icon(it, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.padding(start = 8.dp).size(20.dp))
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = textStyle,
                cursorBrush = SolidColor(colors.action),
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp, vertical = 10.dp)
                    .onFocusChanged { isFocused = it.isFocused }
                    .semantics { contentDescription = fieldLabel },
                decorationBox = { innerTextField ->
                    Box {
                        if (value.isEmpty()) Text(text = placeholder, style = textStyle.copy(color = colors.textTertiary), maxLines = 2)
                        innerTextField()
                    }
                },
            )
            if (value.isNotEmpty() && clearLabel != null) {
                Surface(
                    onClick = onClear,
                    shape = CircleShape,
                    color = Color.Transparent,
                    modifier = Modifier.size(TouchTarget).semantics { contentDescription = clearLabel },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Box(Modifier.size(24.dp).background(colors.backgroundAlternative, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(JaugeIcons.Close, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
        if (status != null) {
            Box(modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp)) { status() }
        }
        ComposerToolbar(tools = tools, action = action)
    }
}

/**
 * Toolbar of [JaugeComposer]: the tools on the left (wrapping when needed), the action on the right of the same line
 * when its whole label fits, else alone on the next line, on the right. The decision uses the full width of the
 * action label each time it is measured, so a label that grows ("Coller" to "Coller le lien copié") moves at once.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ComposerToolbar(tools: @Composable FlowRowScope.() -> Unit, action: @Composable () -> Unit) {
    val spacing = 8.dp
    Layout(
        contents = listOf(
            { FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing), verticalArrangement = Arrangement.spacedBy(spacing), content = tools) },
            action,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) { (toolsMeasurables, actionMeasurables), constraints ->
        val gap = spacing.roundToPx()
        val maxWidth = constraints.maxWidth
        val toolsPlaceables = toolsMeasurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val toolsWidth = toolsPlaceables.maxOfOrNull { it.width } ?: 0
        val toolsHeight = toolsPlaceables.maxOfOrNull { it.height } ?: 0
        val actionMeasurable = actionMeasurables.firstOrNull()
        val actionWidth = actionMeasurable?.maxIntrinsicWidth(Constraints.Infinity) ?: 0
        val sameLine = toolsWidth == 0 || toolsWidth + gap + actionWidth <= maxWidth
        val actionMaxWidth = if (sameLine && toolsWidth > 0) maxWidth - toolsWidth - gap else maxWidth
        val actionPlaceable = actionMeasurable?.measure(Constraints(maxWidth = actionMaxWidth.coerceAtLeast(0)))
        val actionHeight = actionPlaceable?.height ?: 0
        val height = if (sameLine) maxOf(toolsHeight, actionHeight) else toolsHeight + gap + actionHeight
        layout(maxWidth, height) {
            toolsPlaceables.forEach { it.placeRelative(0, if (sameLine) (height - toolsHeight) / 2 else 0) }
            actionPlaceable?.placeRelative(
                x = maxWidth - actionPlaceable.width,
                y = if (sameLine) (height - actionHeight) / 2 else toolsHeight + gap,
            )
        }
    }
}

/**
 * Line under the composer text: a recognized link, a hint or a friendly error.
 *
 * @param text message, from `strings.xml`.
 * @param tone color and icon.
 * @param modifier modifier of the line.
 */
@Composable
fun ComposerStatus(text: String, tone: ComposerStatusTone, modifier: Modifier = Modifier) {
    val colors = JaugeTheme.colors
    val color = when (tone) {
        ComposerStatusTone.SUCCESS -> colors.calm.content
        ComposerStatusTone.HINT -> colors.textSecondary
        ComposerStatusTone.ERROR -> colors.crowded.content
    }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (tone == ComposerStatusTone.SUCCESS) {
            Box(Modifier.size(20.dp).background(colors.calm.container, CircleShape), contentAlignment = Alignment.Center) {
                Icon(JaugeIcons.Check, contentDescription = null, tint = colors.calm.content, modifier = Modifier.size(13.dp))
            }
        }
        Text(text = text, style = if (tone == ComposerStatusTone.HINT) JaugeTheme.typography.bodySmall else JaugeTheme.typography.label, color = color)
    }
}

/**
 * Secondary way to give the input, in the composer toolbar ("Capture", "Photo"): soft tile, icon and label, 48 dp high.
 *
 * @param text label, from `strings.xml`.
 * @param icon icon before the label.
 * @param onClick tap action.
 * @param modifier modifier of the button.
 */
@Composable
fun ComposerToolButton(text: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = JaugeTheme.colors
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(JaugeTheme.radii.medium),
        color = colors.backgroundAlternative,
        modifier = modifier.heightIn(min = TouchTarget),
    ) {
        Row(
            modifier = Modifier.heightIn(min = TouchTarget).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(icon, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
            Text(text = text, style = JaugeTheme.typography.label.copy(fontSize = 14.sp, lineHeight = 18.sp), color = colors.textPrimary)
        }
    }
}

/**
 * Main action of the composer. Soft while there is nothing to send ("Coller"), coral once the input can be sent
 * ("Jauger"); the new label fades in (no animation when the person reduces motion).
 *
 * @param text label, from `strings.xml`.
 * @param onClick tap action.
 * @param modifier modifier of the button.
 * @param icon optional icon: before the label when soft, after it when emphasized.
 * @param emphasized true for the coral call to action.
 * @param enabled false to dim the button and ignore taps.
 */
@Composable
fun ComposerActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    emphasized: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = JaugeTheme.colors
    val reducedMotion = rememberReducedMotion()
    val targetContainer = when {
        emphasized && enabled -> colors.action
        emphasized -> colors.action.copy(alpha = 0.32f)
        else -> colors.backgroundAlternative
    }
    val container by animateColorAsState(targetContainer, animationSpec = if (reducedMotion) snap() else tween(200), label = "action")
    val content = when {
        emphasized && enabled -> colors.onAction
        emphasized -> colors.onAction.copy(alpha = 0.75f)
        else -> colors.textPrimary
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(JaugeTheme.radii.medium),
        color = container,
        modifier = modifier.sizeIn(minHeight = TouchTarget, minWidth = TouchTarget),
    ) {
        // The new label fades in. A plain row (not AnimatedContent) keeps the real intrinsic width, which the
        // toolbar needs to move a long label ("Coller le lien copié") to its own line.
        val labelAlpha = remember(text, emphasized) { Animatable(if (reducedMotion) 1f else 0f) }
        LaunchedEffect(text, emphasized) { if (!reducedMotion) labelAlpha.animateTo(1f, tween(160)) }
        Row(
            modifier = Modifier
                .heightIn(min = TouchTarget)
                .padding(PaddingValues(horizontal = 16.dp, vertical = 6.dp))
                .graphicsLayer { alpha = labelAlpha.value },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            if (!emphasized) icon?.let { Icon(it, contentDescription = null, tint = content, modifier = Modifier.size(18.dp)) }
            Text(
                text = text,
                style = JaugeTheme.typography.button.copy(fontSize = 15.sp, lineHeight = 19.sp),
                color = content,
                textAlign = TextAlign.Center,
                // One line: when the label does not fit next to the tools, the toolbar moves the button to its own line.
                maxLines = 1,
                softWrap = false,
            )
            if (emphasized) icon?.let { Icon(it, contentDescription = null, tint = content, modifier = Modifier.size(18.dp)) }
        }
    }
}
