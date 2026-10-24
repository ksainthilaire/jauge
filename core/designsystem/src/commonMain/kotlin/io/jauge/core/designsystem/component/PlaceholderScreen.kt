package io.jauge.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.jauge.core.designsystem.resources.Res
import io.jauge.core.designsystem.resources.action_back
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
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (onBack != null) {
            TextButton(onClick = onBack) { Text(stringResource(Res.string.action_back)) }
        }
        Text(text = title, style = MaterialTheme.typography.headlineMedium)
        if (primaryActionLabel != null) {
            Button(onClick = onPrimaryAction) { Text(primaryActionLabel) }
        }
    }
}
