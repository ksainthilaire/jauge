package io.jauge.core.domain.model

/** Where the search area comes from. */
enum class SearchAreaOrigin {
    /** Found from the position of the phone (city only, the position never leaves the phone). */
    DEVICE,

    /** Typed by the user in the settings: it wins over the position of the phone. */
    MANUAL,

    /** Default city (Paris), used while the position is not allowed or not available. */
    DEFAULT,
}

/**
 * Area where the user looks for a job, used for the analyses, the twin offers ("à moins de 30 km") and the settings
 * (mockup 13, "Zone de recherche"). Only the city and the department are sent to the server, never a position.
 *
 * @property city city at the center of the area ("Lyon").
 * @property radiusKilometers radius around the city.
 * @property departmentCode French department of the city ("69", "2A", "974"), null when unknown.
 * @property origin where the area comes from.
 */
data class SearchArea(
    val city: String,
    val radiusKilometers: Int,
    val departmentCode: String? = null,
    val origin: SearchAreaOrigin = SearchAreaOrigin.MANUAL,
) {
    /** Companion with the allowed radii and the default area. */
    companion object {
        /** Radii offered by the settings screen. */
        val RADII_KILOMETERS: List<Int> = listOf(10, 30, 50, 100)

        /** Radius used until the user chooses one. */
        const val DEFAULT_RADIUS_KILOMETERS: Int = 30

        /** Area used when the position is refused or not available: Paris, changeable in the settings. */
        val DEFAULT: SearchArea = SearchArea("Paris", DEFAULT_RADIUS_KILOMETERS, "75", SearchAreaOrigin.DEFAULT)
    }
}

/**
 * City found from the position of the phone.
 *
 * @property name name of the city ("Lyon").
 * @property departmentCode French department ("69").
 */
data class LocatedCity(val name: String, val departmentCode: String)

/** Answer of the system to the location permission (Android `ACCESS_COARSE_LOCATION`, iOS "When In Use"). */
enum class LocationPermission {
    /** Not asked yet: the app may explain, then ask. */
    NOT_DETERMINED,

    /** Allowed. */
    GRANTED,

    /** Refused, or restricted by the phone: the default city is used. */
    DENIED,
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
 * @property searchArea search area, null until chosen or found ([effectiveSearchArea] gives the default then).
 * @property hasAnsweredLocation true once the location explanation was answered ("Autoriser" or "Plus tard").
 */
data class UserPreferences(
    val hasPassedWelcome: Boolean = false,
    val hasPassedAccount: Boolean = false,
    val hasAnsweredAlerts: Boolean = false,
    val calmAlertsEnabled: Boolean = false,
    val searchArea: SearchArea? = null,
    val hasAnsweredLocation: Boolean = false,
) {
    /** Search area to use: the chosen or found one, else [SearchArea.DEFAULT] (Paris). */
    val effectiveSearchArea: SearchArea
        get() = searchArea ?: SearchArea.DEFAULT

    /** Next step of the first launch. */
    val onboardingStep: OnboardingStep
        get() = when {
            !hasPassedWelcome -> OnboardingStep.WELCOME
            !hasPassedAccount -> OnboardingStep.ACCOUNT
            !hasAnsweredAlerts -> OnboardingStep.ALERTS
            else -> OnboardingStep.DONE
        }
}
