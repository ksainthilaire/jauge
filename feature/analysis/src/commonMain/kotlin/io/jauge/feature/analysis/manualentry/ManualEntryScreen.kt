package io.jauge.feature.analysis.manualentry

import androidx.compose.runtime.Composable
import io.jauge.core.designsystem.component.PlaceholderScreen
import io.jauge.feature.analysis.resources.Res
import io.jauge.feature.analysis.resources.manual_entry_title
import org.jetbrains.compose.resources.stringResource

/**
 * Manual entry of the job title and city when the offer cannot be read.
 *
 * Placeholder: the screen is built in a later phase, following `docs/ARCHITECTURE.md`.
 *
 * @param onBack called when the user leaves the screen.
 */
@Composable
fun ManualEntryScreen(
    onBack: () -> Unit,
) {
    PlaceholderScreen(title = stringResource(Res.string.manual_entry_title), onBack = onBack)
}
