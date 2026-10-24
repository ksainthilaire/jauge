package io.jauge.feature.analysis.unreadableoffer

import androidx.compose.runtime.Composable
import io.jauge.core.designsystem.component.PlaceholderScreen
import io.jauge.feature.analysis.resources.Res
import io.jauge.feature.analysis.resources.unreadable_offer_title
import org.jetbrains.compose.resources.stringResource

/**
 * Friendly error when a screenshot cannot be read (mockup 10).
 *
 * Placeholder: the screen is built in a later phase, following `docs/ARCHITECTURE.md`.
 *
 * @param onBack called when the user leaves the screen.
 */
@Composable
fun UnreadableOfferScreen(
    onBack: () -> Unit,
) {
    PlaceholderScreen(title = stringResource(Res.string.unreadable_offer_title), onBack = onBack)
}
