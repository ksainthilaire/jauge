package io.jauge.core.data.preferences

import io.jauge.core.domain.model.SearchArea
import io.jauge.core.domain.repository.PreferencesRepository
import io.jauge.core.network.payload.AreaPayload
import kotlinx.coroutines.flow.first

/** Search area of the user, read by the repositories before the calls that depend on it. */
fun interface SearchAreaSource {
    /** Current area, or null to send none. */
    suspend fun current(): SearchArea?

    /** Sources. */
    companion object {
        /** Sends no area: the server uses the place of the offer. */
        val NONE: SearchAreaSource = SearchAreaSource { null }

        /**
         * Area kept in the preferences: the typed city, the city of the phone, or Paris by default.
         *
         * @param preferences preferences repository.
         */
        fun fromPreferences(preferences: PreferencesRepository): SearchAreaSource =
            SearchAreaSource { preferences.observePreferences().first().effectiveSearchArea }
    }
}

/** Area sent to the API: the city, its department and the radius, never a position. */
internal fun SearchArea.toPayload(): AreaPayload = AreaPayload(city = city, departmentCode = departmentCode, radiusKilometers = radiusKilometers)
