package io.jauge.feature.onboarding.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.jauge.feature.onboarding.welcome.WelcomeScreen
import kotlinx.serialization.Serializable

/** Route of [io.jauge.feature.onboarding.welcome.WelcomeScreen]. */
@Serializable
data object WelcomeRoute

/**
 * Adds the screens of the onboarding feature (first launch of the app (mockup 01)) to the navigation graph.
 *
 * Navigation to other features goes through callbacks wired in `composeApp`: add a parameter here when a screen
 * needs to open another feature.
 *
 * @param onStartAnalysis opens the home screen of the analysis feature.
 */
fun NavGraphBuilder.onboardingGraph(onStartAnalysis: () -> Unit) {
    composable<WelcomeRoute> {
        WelcomeScreen(onStartAnalysis = onStartAnalysis)
    }
}
