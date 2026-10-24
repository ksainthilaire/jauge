package io.jauge.feature.analysis.home

import androidx.compose.runtime.Composable
import io.jauge.core.designsystem.component.PlaceholderScreen
import io.jauge.feature.analysis.resources.Res
import io.jauge.feature.analysis.resources.home_title
import org.jetbrains.compose.resources.stringResource

/**
 * Home: paste a link, pick a screenshot or take a photo, and see the latest analyses (mockup 02).
 *
 * Placeholder: the screen is built in a later phase, following `docs/ARCHITECTURE.md`.
 *
 */
@Composable
fun HomeScreen(
) {
    PlaceholderScreen(title = stringResource(Res.string.home_title), onBack = null)
}
