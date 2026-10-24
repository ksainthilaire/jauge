package io.jauge.feature.onboarding.welcome

import androidx.compose.runtime.Composable
import io.jauge.core.designsystem.component.PlaceholderScreen
import io.jauge.feature.onboarding.resources.Res
import io.jauge.feature.onboarding.resources.welcome_start
import io.jauge.feature.onboarding.resources.welcome_title
import org.jetbrains.compose.resources.stringResource

/**
 * Welcome screen with the promise of the app (mockup 01).
 *
 * Placeholder: the screen is built in a later phase, following `docs/ARCHITECTURE.md`.
 *
 * @param onStartAnalysis called when the user starts their first analysis.
 */
@Composable
fun WelcomeScreen(
    onStartAnalysis: () -> Unit,
) {
    PlaceholderScreen(
        title = stringResource(Res.string.welcome_title),
        onBack = null,
        primaryActionLabel = stringResource(Res.string.welcome_start),
        onPrimaryAction = onStartAnalysis,
    )
}
