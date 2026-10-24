package io.jauge.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.jauge.core.designsystem.theme.JaugeTheme

/**
 * Card of the app: off white surface, 22 dp corners and a thin outline (`.carte` in the mockups).
 *
 * @param modifier modifier of the card.
 * @param onClick optional tap action; the whole card becomes clickable.
 * @param containerColor background color.
 * @param borderColor outline color, or [Color.Transparent] for none.
 * @param borderWidth outline width (2 dp for the highlighted paste card).
 * @param contentPadding inner padding.
 * @param content content of the card.
 */
@Composable
fun JaugeCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = JaugeTheme.colors.surface,
    borderColor: Color = JaugeTheme.colors.outline,
    borderWidth: Dp = 1.dp,
    contentPadding: PaddingValues = PaddingValues(JaugeTheme.spacing.extraLarge),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = JaugeTheme.radii.cardShape
    val border = BorderStroke(borderWidth, borderColor)
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = shape, color = containerColor, border = border) {
            Column(modifier = Modifier.padding(contentPadding), content = content)
        }
    } else {
        Surface(modifier = modifier, shape = shape, color = containerColor, border = border) {
            Column(modifier = Modifier.padding(contentPadding), content = content)
        }
    }
}

/**
 * Small figure tile of the verdict (`.tuile`): a label, a big number and a caption.
 *
 * @param label what the figure is ("Candidats estimés").
 * @param value the figure, already formatted ("≈ 210").
 * @param caption precision under the figure ("fourchette 160 à 260").
 * @param modifier modifier of the tile.
 * @param valueColor color of the figure.
 * @param onClick optional tap action.
 */
@Composable
fun MetricTile(
    label: String,
    value: String,
    caption: String,
    modifier: Modifier = Modifier,
    valueColor: Color = JaugeTheme.colors.textPrimary,
    onClick: (() -> Unit)? = null,
) {
    JaugeCard(
        modifier = modifier,
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = JaugeTheme.spacing.large, vertical = 13.dp),
    ) {
        androidx.compose.material3.Text(text = label, style = JaugeTheme.typography.label, color = JaugeTheme.colors.textSecondary)
        androidx.compose.material3.Text(
            text = value,
            style = JaugeTheme.typography.numberMedium,
            color = valueColor,
            modifier = Modifier.padding(top = 5.dp),
        )
        androidx.compose.material3.Text(
            text = caption,
            style = JaugeTheme.typography.caption,
            color = JaugeTheme.colors.textSecondary,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}
