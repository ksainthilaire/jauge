package io.jauge.core.designsystem.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.jauge.core.designsystem.icon.JaugeIcons
import io.jauge.core.designsystem.resources.Res
import io.jauge.core.designsystem.resources.action_back
import io.jauge.core.designsystem.theme.JaugeTheme
import org.jetbrains.compose.resources.stringResource

/**
 * Temporary screen showing only a title, used until the real screen is built.
 *
 * @param title the screen title, from the feature `strings.xml`.
 * @param onBack back action, or null for screens without back navigation (tab roots).
 * @param modifier modifier of the root layout.
 * @param primaryActionLabel label of an optional main action.
 * @param onPrimaryAction the optional main action, shown when [primaryActionLabel] is not null.
 */
@Composable
fun PlaceholderScreen(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    primaryActionLabel: String? = null,
    onPrimaryAction: () -> Unit = {},
) {
    JaugeScreenScaffold(
        modifier = modifier,
        showTopBar = onBack != null,
        navigationIcon = onBack?.let { back ->
            { RoundIconButton(icon = JaugeIcons.Back, contentDescription = stringResource(Res.string.action_back), onClick = back) }
        },
        bottomBar = primaryActionLabel?.let { label ->
            { JaugeButton(text = label, onClick = onPrimaryAction, modifier = Modifier.fillMaxWidth()) }
        },
    ) {
        Text(
            text = title,
            style = JaugeTheme.typography.headline,
            color = JaugeTheme.colors.textPrimary,
            modifier = Modifier.padding(horizontal = JaugeTheme.spacing.screenGutter, vertical = JaugeTheme.spacing.section),
        )
    }
}
