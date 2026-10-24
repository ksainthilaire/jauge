package io.jauge.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.jauge.core.designsystem.theme.JaugeTheme

/**
 * Text input of the app: a card with an optional label and icon, ink border when focused, coral border and a message
 * when invalid.
 *
 * @param value current text.
 * @param onValueChange called with the new text.
 * @param modifier modifier of the field.
 * @param label optional label above the text ("Lien collé").
 * @param placeholder hint shown while empty.
 * @param leadingIcon optional icon before the label.
 * @param errorMessage friendly message shown under the field when the value is invalid, null otherwise.
 * @param monospace true for links and figures (JetBrains Mono).
 * @param singleLine true for one line inputs.
 * @param keyboardOptions keyboard type and action.
 * @param keyboardActions keyboard action callbacks.
 * @param enabled false to make the field read only and dimmed.
 */
@Composable
fun JaugeTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    leadingIcon: ImageVector? = null,
    errorMessage: String? = null,
    monospace: Boolean = false,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    enabled: Boolean = true,
) {
    val colors = JaugeTheme.colors
    val typography = JaugeTheme.typography
    var isFocused by remember { mutableStateOf(false) }
    val borderColor = when {
        errorMessage != null -> colors.crowded.accent
        isFocused -> colors.textPrimary
        else -> colors.outline
    }
    val textStyle = (if (monospace) typography.numberSmall.copy(fontSize = typography.bodySmall.fontSize) else typography.body)
        .copy(color = if (enabled) colors.textPrimary else colors.textSecondary)
    val shape = JaugeTheme.radii.cardShape

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface, shape)
                .border(if (isFocused || errorMessage != null) 2.dp else 1.dp, borderColor, shape)
                .padding(horizontal = JaugeTheme.spacing.extraLarge, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (label != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    leadingIcon?.let { Icon(it, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(17.dp)) }
                    Text(text = label, style = typography.label, color = colors.textSecondary)
                }
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = singleLine,
                textStyle = textStyle,
                cursorBrush = SolidColor(colors.action),
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 22.dp)
                    .onFocusChanged { isFocused = it.isFocused },
                decorationBox = { innerTextField ->
                    Box {
                        if (value.isEmpty() && placeholder != null) {
                            Text(text = placeholder, style = textStyle.copy(color = colors.textTertiary))
                        }
                        innerTextField()
                    }
                },
            )
        }
        if (errorMessage != null) {
            Text(
                text = errorMessage,
                style = typography.bodySmall,
                color = colors.crowded.content,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}
