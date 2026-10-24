package io.jauge.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.jauge.core.designsystem.theme.JaugeTheme

/**
 * On and off switch (`.tgl`): mint when on.
 *
 * @param checked current value.
 * @param onCheckedChange called with the new value.
 * @param modifier modifier of the switch.
 * @param enabled false to disable it.
 */
@Composable
fun JaugeSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = JaugeTheme.colors
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = colors.calm.accent,
            checkedBorderColor = colors.calm.accent,
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = colors.textTertiary,
            uncheckedBorderColor = colors.textTertiary,
        ),
    )
}

/**
 * Thin progress bar (reading progress of mockup 03).
 *
 * @param progress value between 0 and 1.
 * @param modifier modifier of the bar.
 */
@Composable
fun JaugeProgressBar(progress: Float, modifier: Modifier = Modifier) {
    val animatedProgress by animateFloatAsState(progress.coerceIn(0f, 1f), label = "progress")
    val shape = JaugeTheme.radii.pillShape
    Box(modifier = modifier.fillMaxWidth().height(6.dp).background(JaugeTheme.colors.outline, shape)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(animatedProgress).background(JaugeTheme.colors.action, shape))
    }
}
