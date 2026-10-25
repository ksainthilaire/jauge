package io.jauge.core.data.preferences

import io.jauge.core.domain.model.SearchArea
import io.jauge.core.domain.model.SearchAreaOrigin
import io.jauge.core.domain.model.UserPreferences
import io.jauge.core.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * [PreferencesRepository] kept in a [KeyValueStore]. A value that cannot be read falls back to its default.
 *
 * @param store device storage.
 */
class PreferencesRepositoryImplementation(private val store: KeyValueStore) : PreferencesRepository {
    private val mutex = Mutex()
    private val preferences = MutableStateFlow(read())

    override fun observePreferences(): Flow<UserPreferences> = preferences.asStateFlow()

    override suspend fun update(transform: (UserPreferences) -> UserPreferences) {
        mutex.withLock {
            val updated = transform(preferences.value)
            write(updated)
            preferences.value = updated
        }
    }

    private fun read(): UserPreferences = runCatching {
        val city = store.getString(SEARCH_CITY)
        val radius = store.getString(SEARCH_RADIUS)?.toIntOrNull()
        UserPreferences(
            hasPassedWelcome = store.getString(WELCOME).toBoolean(),
            hasPassedAccount = store.getString(ACCOUNT).toBoolean(),
            hasAnsweredAlerts = store.getString(ALERTS).toBoolean(),
            calmAlertsEnabled = store.getString(CALM_ALERTS).toBoolean(),
            searchArea = if (city.isNullOrBlank() || radius == null) {
                null
            } else {
                SearchArea(
                    city = city,
                    radiusKilometers = radius,
                    departmentCode = store.getString(SEARCH_DEPARTMENT)?.takeIf { it.isNotBlank() },
                    origin = store.getString(SEARCH_ORIGIN)?.let { origin -> SearchAreaOrigin.entries.firstOrNull { it.name == origin } }
                        ?: SearchAreaOrigin.MANUAL,
                )
            },
            hasAnsweredLocation = store.getString(LOCATION).toBoolean(),
        )
    }.getOrDefault(UserPreferences())

    private fun write(value: UserPreferences) {
        runCatching {
            store.putString(WELCOME, value.hasPassedWelcome.toString())
            store.putString(ACCOUNT, value.hasPassedAccount.toString())
            store.putString(ALERTS, value.hasAnsweredAlerts.toString())
            store.putString(CALM_ALERTS, value.calmAlertsEnabled.toString())
            store.putString(SEARCH_CITY, value.searchArea?.city)
            store.putString(SEARCH_RADIUS, value.searchArea?.radiusKilometers?.toString())
            store.putString(SEARCH_DEPARTMENT, value.searchArea?.departmentCode)
            store.putString(SEARCH_ORIGIN, value.searchArea?.origin?.name)
            store.putString(LOCATION, value.hasAnsweredLocation.toString())
        }
    }

    private companion object {
        const val WELCOME = "onboarding.welcome"
        const val ACCOUNT = "onboarding.account"
        const val ALERTS = "onboarding.alerts"
        const val CALM_ALERTS = "settings.calmAlerts"
        const val SEARCH_CITY = "settings.searchArea.city"
        const val SEARCH_RADIUS = "settings.searchArea.radius"
        const val SEARCH_DEPARTMENT = "settings.searchArea.department"
        const val SEARCH_ORIGIN = "settings.searchArea.origin"
        const val LOCATION = "onboarding.location"
    }
}
