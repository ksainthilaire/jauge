package io.jauge.feature.verdict.details

import androidx.compose.runtime.Composable
import io.jauge.core.designsystem.component.PlaceholderScreen
import io.jauge.feature.verdict.resources.Res
import io.jauge.feature.verdict.resources.details_title
import org.jetbrains.compose.resources.stringResource

/**
 * Salaries, skills and trend (mockup 05).
 *
 * Placeholder: the screen is built in a later phase, following `docs/ARCHITECTURE.md`.
 *
 * @param onBack called when the user leaves the screen.
 */
@Composable
fun DetailsScreen(
    onBack: () -> Unit,
) {
    PlaceholderScreen(title = stringResource(Res.string.details_title), onBack = onBack)
}
