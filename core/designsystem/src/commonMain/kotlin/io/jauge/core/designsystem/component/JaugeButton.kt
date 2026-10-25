package io.jauge.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.jauge.core.designsystem.motion.shine
import io.jauge.core.designsystem.theme.JaugeTheme

/** Visual styles of [JaugeButton]. */
enum class JaugeButtonStyle {
    /** Coral call to action ("Analyser ma première offre", "Partager"). */
    PRIMARY,

    /** Ink button ("Jauger cette offre"). */
    NEUTRAL,

    /** Transparent button with an outline ("Détails"). */
    OUTLINED,
}

/** Heights of [JaugeButton]. */
enum class JaugeButtonSize {
    /** 56 dp, main actions at the bottom of a screen. */
    LARGE,

    /** 50 dp, actions inside a card. */
    MEDIUM,
}

/**
 * Button of the app.
 *
 * @param text the label, from `strings.xml`.
 * @param onClick called on tap.
 * @param modifier modifier of the button.
 * @param style visual style.
 * @param size height preset.
 * @param leadingIcon optional icon before the label.
 * @param trailingIcon optional icon after the label.
 * @param enabled false to disable the button.
 * @param isLoading true to show a progress indicator instead of the content (the button is then not clickable).
 * @param shine true to sweep a light reflection across the button every few seconds (main offer only), drawn behind
 *   the label; off while disabled or loading and when the system asks for fewer animations.
 * @param shineProgress fixed position of the reflection for screenshots, null to animate.
 */
@Composable
fun JaugeButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: JaugeButtonStyle = JaugeButtonStyle.PRIMARY,
    size: JaugeButtonSize = JaugeButtonSize.LARGE,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    shine: Boolean = false,
    shineProgress: Float? = null,
) {
    val colors = JaugeTheme.colors
    val (container, content) = when (style) {
        JaugeButtonStyle.PRIMARY -> colors.action to colors.onAction
        JaugeButtonStyle.NEUTRAL -> colors.neutralAction to colors.onNeutralAction
        JaugeButtonStyle.OUTLINED -> Color.Transparent to colors.textPrimary
    }
    val height = when (size) {
        JaugeButtonSize.LARGE -> 56.dp
        JaugeButtonSize.MEDIUM -> 50.dp
    }
    val shape = JaugeTheme.radii.buttonShape
    val isShining = shine && enabled && !isLoading && style != JaugeButtonStyle.OUTLINED
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        // Minimum height only: with large text sizes (accessibility settings) the label wraps instead of being cut.
        modifier = modifier
            .heightIn(min = height)
            // The shining button draws its own background, then the reflection, then the label on top.
            .then(if (isShining) Modifier.background(container, shape).shine(shape, fixedProgress = shineProgress) else Modifier),
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isShining) Color.Transparent else container,
            contentColor = content,
            disabledContainerColor = if (style == JaugeButtonStyle.OUTLINED) Color.Transparent else container.copy(alpha = 0.45f),
            disabledContentColor = content.copy(alpha = 0.7f),
        ),
        border = if (style == JaugeButtonStyle.OUTLINED) BorderStroke(1.5.dp, colors.outline) else null,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        elevation = null,
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), color = content, strokeWidth = 2.5.dp)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                leadingIcon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(20.dp)) }
                Text(text = text, style = JaugeTheme.typography.button, textAlign = TextAlign.Center)
                trailingIcon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(20.dp)) }
            }
        }
    }
}
