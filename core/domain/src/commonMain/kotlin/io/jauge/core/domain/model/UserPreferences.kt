package io.jauge.core.domain.model

/**
 * Area where the user looks for a job, used to find twin offers (mockup 13, "Zone de recherche").
 *
 * @property city city at the center of the area ("Lyon").
 * @property radiusKilometers radius around the city.
 */
data class SearchArea(val city: String, val radiusKilometers: Int) {
    /** Companion with the allowed radii. */
    companion object {
        /** Radii offered by the settings screen. */
        val RADII_KILOMETERS: List<Int> = listOf(10, 30, 50, 100)
    }
}

/** Steps of the first launch: welcome (01), account (11), alerts (12), then the home screen (02). */
enum class OnboardingStep {
    /** The welcome screen has not been passed yet. */
    WELCOME,

    /** The user still has to sign in or continue without account. */
    ACCOUNT,

    /** The user still has to answer the alerts screen. */
    ALERTS,

    /** The first launch is over: the app opens on the home screen. */
    DONE,
}

/**
 * Preferences kept on the device.
 *
 * @property hasPassedWelcome true once the welcome screen was passed.
 * @property hasPassedAccount true once the user signed in or chose to continue without account.
 * @property hasAnsweredAlerts true once the alerts screen was answered ("Continuer" or "Plus tard").
 * @property calmAlertsEnabled true when the user wants calm alerts.
 * @property searchArea search area, null until chosen.
 */
data class UserPreferences(
    val hasPassedWelcome: Boolean = false,
    val hasPassedAccount: Boolean = false,
    val hasAnsweredAlerts: Boolean = false,
    val calmAlertsEnabled: Boolean = false,
    val searchArea: SearchArea? = null,
) {
    /** Next step of the first launch. */
    val onboardingStep: OnboardingStep
        get() = when {
            !hasPassedWelcome -> OnboardingStep.WELCOME
            !hasPassedAccount -> OnboardingStep.ACCOUNT
            !hasAnsweredAlerts -> OnboardingStep.ALERTS
            else -> OnboardingStep.DONE
        }
}
