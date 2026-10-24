package io.jauge.core.domain.usecase

import io.jauge.core.domain.model.SearchArea
import io.jauge.core.domain.model.UserPreferences
import io.jauge.core.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.Flow

/**
 * Preferences of the user, observed by the first launch and the settings.
 *
 * @param repository preferences repository.
 */
class ObservePreferencesUseCase(private val repository: PreferencesRepository) {
    /** Observed preferences. */
    operator fun invoke(): Flow<UserPreferences> = repository.observePreferences()
}

/**
 * Records the answers of the first launch (mockups 01, 11 and 12).
 *
 * @param repository preferences repository.
 */
class CompleteOnboardingStepUseCase(private val repository: PreferencesRepository) {
    /** The welcome screen was passed ("Analyser ma première offre" or "Me connecter"). */
    suspend fun passWelcome() = repository.update { it.copy(hasPassedWelcome = true) }

    /** The user signed in or chose to continue without account. */
    suspend fun passAccount() = repository.update { it.copy(hasPassedWelcome = true, hasPassedAccount = true) }

    /**
     * The alerts screen was answered.
     *
     * @param alertsEnabled true when the user accepted the notifications.
     */
    suspend fun answerAlerts(alertsEnabled: Boolean) = repository.update {
        it.copy(hasPassedWelcome = true, hasPassedAccount = true, hasAnsweredAlerts = true, calmAlertsEnabled = alertsEnabled)
    }

    /** "Passer" on the welcome screen: the whole first launch is skipped. */
    suspend fun skipAll() = repository.update {
        it.copy(hasPassedWelcome = true, hasPassedAccount = true, hasAnsweredAlerts = true)
    }
}

/**
 * Changes the settings of the user (mockup 13, "Réglages").
 *
 * @param repository preferences repository.
 */
class UpdateSettingsUseCase(private val repository: PreferencesRepository) {
    /**
     * Turns calm alerts on or off.
     *
     * @param enabled new value.
     */
    suspend fun setCalmAlertsEnabled(enabled: Boolean) = repository.update { it.copy(calmAlertsEnabled = enabled) }

    /**
     * Saves the search area. A blank city clears it.
     *
     * @param city city at the center.
     * @param radiusKilometers radius around the city.
     */
    suspend fun setSearchArea(city: String, radiusKilometers: Int) = repository.update {
        val trimmed = city.trim()
        it.copy(searchArea = if (trimmed.isEmpty()) null else SearchArea(trimmed, radiusKilometers.coerceIn(1, 200)))
    }

    /** Forgets every preference after the account is deleted: the app starts again from the welcome screen. */
    suspend fun resetAll() = repository.update { UserPreferences() }
}
