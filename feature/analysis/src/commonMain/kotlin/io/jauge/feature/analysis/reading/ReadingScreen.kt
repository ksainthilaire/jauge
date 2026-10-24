package io.jauge.feature.analysis.reading

import androidx.compose.runtime.Composable
import io.jauge.core.designsystem.component.PlaceholderScreen
import io.jauge.feature.analysis.resources.Res
import io.jauge.feature.analysis.resources.reading_title
import org.jetbrains.compose.resources.stringResource

/**
 * Progress while the offer is read and compared (mockup 03).
 *
 * Placeholder: the screen is built in a later phase, following `docs/ARCHITECTURE.md`.
 *
 * @param onBack called when the user leaves the screen.
 */
@Composable
fun ReadingScreen(
    onBack: () -> Unit,
) {
    PlaceholderScreen(title = stringResource(Res.string.reading_title), onBack = onBack)
}
