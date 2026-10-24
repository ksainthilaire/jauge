package io.jauge.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.jauge.core.designsystem.icon.JaugeIcons
import io.jauge.core.designsystem.theme.JaugeTheme

@Composable
private fun StateLayout(
    icon: ImageVector,
    iconTint: Color,
    iconBackground: Color,
    title: String,
    message: String,
    modifier: Modifier,
    actions: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = JaugeTheme.spacing.screenGutter, vertical = JaugeTheme.spacing.huge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(JaugeTheme.spacing.medium),
    ) {
        Box(
            modifier = Modifier.size(56.dp).background(iconBackground, JaugeTheme.radii.cardShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(26.dp))
        }
        Text(
            text = title,
            style = JaugeTheme.typography.sectionTitle,
            color = JaugeTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = JaugeTheme.spacing.small),
        )
        Text(
            text = message,
            style = JaugeTheme.typography.bodySmall,
            color = JaugeTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = JaugeTheme.spacing.large),
            verticalArrangement = Arrangement.spacedBy(JaugeTheme.spacing.medium),
        ) {
            actions()
        }
    }
}

/**
 * Empty state: nothing to show yet ("Aucune offre jaugée pour l'instant").
 *
 * @param title short title.
 * @param message one or two sentences telling what to do.
 * @param modifier modifier of the layout.
 * @param icon illustrative icon.
 * @param actionLabel optional label of the main action.
 * @param onAction the main action.
 */
@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = JaugeIcons.Gauge,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    StateLayout(
        icon = icon,
        iconTint = JaugeTheme.colors.calm.content,
        iconBackground = JaugeTheme.colors.calm.container,
        title = title,
        message = message,
        modifier = modifier,
    ) {
        if (actionLabel != null) {
            JaugeButton(text = actionLabel, onClick = onAction, modifier = Modifier.fillMaxWidth())
        }
    }
}

/**
 * Friendly error state (mockup 10): an amber icon, a human title, what happened and what to do, and up to two
 * actions. It never shows a technical error: pass texts produced by the error mapping of `core:ui`.
 *
 * @param title human title ("On n'a pas pu lire cette capture.").
 * @param message explanation and reassurance.
 * @param modifier modifier of the layout.
 * @param icon illustrative icon.
 * @param primaryActionLabel optional label of the main action ("Réessayer").
 * @param onPrimaryAction the main action.
 * @param secondaryActionLabel optional label of a second action.
 * @param onSecondaryAction the second action.
 */
@Composable
fun FriendlyErrorState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = JaugeIcons.Info,
    primaryActionLabel: String? = null,
    onPrimaryAction: () -> Unit = {},
    secondaryActionLabel: String? = null,
    onSecondaryAction: () -> Unit = {},
) {
    StateLayout(
        icon = icon,
        iconTint = JaugeTheme.colors.disputed.content,
        iconBackground = JaugeTheme.colors.disputed.container,
        title = title,
        message = message,
        modifier = modifier,
    ) {
        if (primaryActionLabel != null) {
            JaugeButton(
                text = primaryActionLabel,
                onClick = onPrimaryAction,
                style = JaugeButtonStyle.NEUTRAL,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (secondaryActionLabel != null) {
            JaugeButton(
                text = secondaryActionLabel,
                onClick = onSecondaryAction,
                style = JaugeButtonStyle.OUTLINED,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
