package io.jauge.feature.analysis.history

import androidx.compose.runtime.Composable
import io.jauge.core.designsystem.component.PlaceholderScreen
import io.jauge.feature.analysis.resources.Res
import io.jauge.feature.analysis.resources.history_title
import org.jetbrains.compose.resources.stringResource

/**
 * Every analysis of the user ("Tout voir" on mockup 02).
 *
 * Placeholder: the screen is built in a later phase, following `docs/ARCHITECTURE.md`.
 *
 * @param onBack called when the user leaves the screen.
 */
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
) {
    PlaceholderScreen(title = stringResource(Res.string.history_title), onBack = onBack)
}
