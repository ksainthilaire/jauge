package io.jauge.feature.twins.twinoffers

import androidx.compose.runtime.Composable
import io.jauge.core.designsystem.component.PlaceholderScreen
import io.jauge.feature.twins.resources.Res
import io.jauge.feature.twins.resources.twin_offers_title
import org.jetbrains.compose.resources.stringResource

/**
 * Same job, fewer applicants, and the calm alert (mockup 06, tab "Veille").
 *
 * Placeholder: the screen is built in a later phase, following `docs/ARCHITECTURE.md`.
 *
 */
@Composable
fun TwinOffersScreen(
) {
    PlaceholderScreen(title = stringResource(Res.string.twin_offers_title), onBack = null)
}
