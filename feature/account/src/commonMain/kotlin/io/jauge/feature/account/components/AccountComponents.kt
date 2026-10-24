package io.jauge.feature.account.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import io.jauge.core.designsystem.icon.JaugeIcons
import io.jauge.core.designsystem.theme.JaugeTheme
import io.jauge.core.domain.error.JaugeError
import io.jauge.core.ui.error.toFriendlyMessage
import org.jetbrains.compose.resources.stringResource

/**
 * Large sign in button of mockup 11, with the colors of the provider.
 *
 * @param text the label.
 * @param icon the provider logo.
 * @param onClick called on tap.
 * @param containerColor background.
 * @param contentColor text color.
 * @param modifier modifier of the button.
 * @param borderColor optional outline.
 * @param iconTint tint of [icon], [Color.Unspecified] to keep its own colors.
 * @param enabled false while another sign in runs.
 * @param isLoading true while this sign in runs.
 */
@Composable
fun ProviderButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    borderColor: Color? = null,
    iconTint: Color = Color.Unspecified,
    enabled: Boolean = true,
    isLoading: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        modifier = modifier.fillMaxWidth().height(56.dp),
        shape = JaugeTheme.radii.buttonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor.copy(alpha = 0.6f),
            disabledContentColor = contentColor.copy(alpha = 0.7f),
        ),
        border = borderColor?.let { BorderStroke(1.5.dp, it) },
        contentPadding = PaddingValues(horizontal = 20.dp),
        elevation = null,
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), color = contentColor, strokeWidth = 2.5.dp)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(19.dp))
                Text(text = text, style = JaugeTheme.typography.button)
            }
        }
    }
}

/**
 * Small friendly error block shown inside a form (never a technical message).
 *
 * @param error the error to explain.
 * @param modifier modifier of the block.
 */
@Composable
fun InlineError(error: JaugeError, modifier: Modifier = Modifier) {
    val message = error.toFriendlyMessage()
    val colors = JaugeTheme.colors
    val shape = RoundedCornerShape(JaugeTheme.radii.medium)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.disputed.container, shape)
            .border(1.dp, colors.disputed.accent.copy(alpha = 0.5f), shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(JaugeIcons.Info, contentDescription = null, tint = colors.disputed.content, modifier = Modifier.size(18.dp))
        Column {
            Text(text = stringResource(message.title), style = JaugeTheme.typography.bodyStrong, color = colors.textPrimary)
            Text(
                text = stringResource(message.message),
                style = JaugeTheme.typography.bodySmall,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/**
 * Square check box of mockup 11 (`.cc`): an outlined 22 dp square, mint with a check when ticked.
 *
 * @param checked current value.
 * @param modifier modifier of the box.
 */
@Composable
fun ConsentCheckBox(checked: Boolean, modifier: Modifier = Modifier) {
    val colors = JaugeTheme.colors
    val shape = RoundedCornerShape(7.dp)
    Box(
        modifier = modifier
            .size(22.dp)
            .background(if (checked) colors.calm.accent else Color.Transparent, shape)
            .border(2.dp, if (checked) colors.calm.accent else colors.textTertiary, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(JaugeIcons.Check, contentDescription = null, tint = colors.background, modifier = Modifier.size(15.dp))
        }
    }
}
