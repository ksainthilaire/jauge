package io.jauge.feature.verdict.verdict

import androidx.compose.runtime.Composable
import io.jauge.core.designsystem.component.PlaceholderScreen
import io.jauge.feature.verdict.resources.Res
import io.jauge.feature.verdict.resources.verdict_title
import org.jetbrains.compose.resources.stringResource

/**
 * Competition gauge 0 to 100 and key figures (mockup 04).
 *
 * Placeholder: the screen is built in a later phase, following `docs/ARCHITECTURE.md`.
 *
 * @param onBack called when the user leaves the screen.
 */
@Composable
fun VerdictScreen(
    onBack: () -> Unit,
) {
    PlaceholderScreen(title = stringResource(Res.string.verdict_title), onBack = onBack)
}
