package io.jauge.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.jauge.core.designsystem.theme.JaugeTheme
import org.jetbrains.compose.resources.stringResource

/**
 * Small rounded label (`.pill`).
 *
 * @param text the label.
 * @param containerColor background color.
 * @param contentColor text color.
 * @param modifier modifier of the pill.
 * @param dotColor optional colored dot before the text.
 */
@Composable
fun JaugePill(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    dotColor: Color? = null,
) {
    Row(
        modifier = modifier
            // Minimum height only: with large system text sizes the label wraps instead of being cut.
            .heightIn(min = 26.dp)
            .background(containerColor, JaugeTheme.radii.pillShape)
            .padding(horizontal = 10.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        dotColor?.let { Box(Modifier.size(8.dp).background(it, CircleShape)) }
        Text(text = text, style = JaugeTheme.typography.pill, color = contentColor)
    }
}

/**
 * Pill of a competition level: always the word with its color ("Calme", "Disputé", "Bondé").
 *
 * @param level the level.
 * @param modifier modifier of the pill.
 * @param text optional longer text ("Bondé · plus que 84 % des offres du Rhône"); the level word by default.
 * @param showDot true to show the colored dot.
 */
@Composable
fun LevelPill(
    level: GaugeLevel,
    modifier: Modifier = Modifier,
    text: String = stringResource(level.label),
    showDot: Boolean = false,
) {
    val colors = level.colors
    JaugePill(
        text = text,
        containerColor = colors.container,
        contentColor = colors.content,
        modifier = modifier,
        dotColor = if (showDot) colors.accent else null,
    )
}

/** Styles of [JaugeChip]. */
enum class JaugeChipStyle {
    /** Filter chip, mint when selected ("Les moins disputées", mockup 06). */
    FILTER,

    /** Segment, ink when selected ("Salaires / Compétences / Tendance", mockup 05). */
    SEGMENT,
}

/**
 * Selectable chip used for filters and segmented choices.
 *
 * @param text the label.
 * @param selected true when the chip is selected.
 * @param onClick called on tap.
 * @param modifier modifier of the chip.
 * @param style filter or segment.
 */
@Composable
fun JaugeChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: JaugeChipStyle = JaugeChipStyle.FILTER,
) {
    val colors = JaugeTheme.colors
    val (container, border, content) = when {
        !selected -> Triple(colors.surface, colors.outline, if (style == JaugeChipStyle.SEGMENT) colors.textSecondary else colors.textPrimary)
        style == JaugeChipStyle.FILTER -> Triple(colors.calm.container, colors.calm.accent, colors.calm.content)
        else -> Triple(colors.neutralAction, colors.neutralAction, colors.onNeutralAction)
    }
    val shape = JaugeTheme.radii.pillShape
    Box(
        modifier = modifier
            .height(36.dp)
            .clip(shape)
            .background(container, shape)
            .border(1.dp, border, shape)
            .semantics { this.selected = selected }
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = JaugeTheme.typography.label.copy(fontSize = JaugeTheme.typography.body.fontSize), color = content, maxLines = 1)
    }
}
