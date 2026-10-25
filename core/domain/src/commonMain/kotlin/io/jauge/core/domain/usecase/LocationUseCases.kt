package io.jauge.core.domain.usecase

import io.jauge.core.domain.model.LocationPermission
import io.jauge.core.domain.model.SearchArea
import io.jauge.core.domain.model.SearchAreaOrigin
import io.jauge.core.domain.repository.LocationRepository
import io.jauge.core.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Search area used by the analyses and the twin offers: the city typed in the settings, else the city of the phone,
 * else Paris ([SearchArea.DEFAULT]).
 *
 * @param preferences preferences repository.
 */
class ObserveSearchAreaUseCase(private val preferences: PreferencesRepository) {
    /** Observed area, never null. */
    operator fun invoke(): Flow<SearchArea> =
        preferences.observePreferences().map { it.effectiveSearchArea }.distinctUntilChanged()

    /** Current area, never null. */
    suspend fun current(): SearchArea = invoke().first()
}

/**
 * Finds the search area from the position of the phone, after the explanation of the app (pop-up "Où cherches-tu ?").
 * A city typed by the user in the settings always wins. A refused or unavailable position keeps the default city.
 *
 * @param preferences preferences repository.
 * @param location position of the phone, reduced to a city.
 */
class LocateSearchAreaUseCase(
    private val preferences: PreferencesRepository,
    private val location: LocationRepository,
) {
    /** True when the explanation must be shown: never answered and the system has not been asked yet. */
    suspend fun shouldExplain(): Boolean =
        !preferences.observePreferences().first().hasAnsweredLocation && location.permission() == LocationPermission.NOT_DETERMINED

    /**
     * Updates the area from the position when it is allowed (at every start, and after "Autoriser"). Does nothing
     * when the user typed a city.
     *
     * @return the area in use afterwards.
     */
    suspend fun refresh(): SearchArea {
        val current = preferences.observePreferences().first()
        val area = current.searchArea
        if (area?.origin == SearchAreaOrigin.MANUAL) return area
        if (location.permission() != LocationPermission.GRANTED) return current.effectiveSearchArea
        val city = location.locateCity() ?: return current.effectiveSearchArea
        val radius = area?.radiusKilometers ?: SearchArea.DEFAULT_RADIUS_KILOMETERS
        val found = SearchArea(city.name, radius, city.departmentCode, SearchAreaOrigin.DEVICE)
        preferences.update { it.copy(searchArea = found) }
        return found
    }

    /**
     * "Autoriser" on the explanation: shows the system request, then uses the city of the phone if allowed.
     *
     * @return the answer of the system.
     */
    suspend fun allow(): LocationPermission {
        preferences.update { it.copy(hasAnsweredLocation = true) }
        val permission = location.requestPermission()
        refresh()
        return permission
    }

    /** "Plus tard" on the explanation: the default city stays, changeable in the settings. */
    suspend fun decline() = preferences.update { it.copy(hasAnsweredLocation = true) }

    /**
     * "Utiliser ma position" in the settings: forgets the typed city, asks the permission if needed, then locates.
     *
     * @return the area in use afterwards.
     */
    suspend fun useDevicePosition(): SearchArea {
        preferences.update { preferences -> preferences.copy(searchArea = preferences.searchArea?.copy(origin = SearchAreaOrigin.DEFAULT)) }
        if (location.permission() != LocationPermission.GRANTED) {
            preferences.update { it.copy(hasAnsweredLocation = true) }
            location.requestPermission()
        }
        val area = refresh()
        if (area.origin != SearchAreaOrigin.DEVICE) {
            // No position: back to the default city, keeping the chosen radius.
            preferences.update { it.copy(searchArea = SearchArea.DEFAULT.copy(radiusKilometers = it.effectiveSearchArea.radiusKilometers)) }
        }
        return preferences.observePreferences().first().effectiveSearchArea
    }
}
