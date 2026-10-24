package io.jauge.feature.verdict.methodology

import androidx.compose.runtime.Composable
import io.jauge.core.designsystem.component.PlaceholderScreen
import io.jauge.feature.verdict.resources.Res
import io.jauge.feature.verdict.resources.methodology_title
import org.jetbrains.compose.resources.stringResource

/**
 * How the estimate is built and how reliable it is (mockup 09).
 *
 * Placeholder: the screen is built in a later phase, following `docs/ARCHITECTURE.md`.
 *
 * @param onBack called when the user leaves the screen.
 */
@Composable
fun MethodologyScreen(
    onBack: () -> Unit,
) {
    PlaceholderScreen(title = stringResource(Res.string.methodology_title), onBack = onBack)
}
