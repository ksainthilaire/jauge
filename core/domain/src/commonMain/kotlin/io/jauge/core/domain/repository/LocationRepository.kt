package io.jauge.core.domain.repository

import io.jauge.core.domain.model.LocatedCity
import io.jauge.core.domain.model.LocationPermission

/**
 * Position of the phone, reduced to a city on the phone itself: the position never leaves the phone, only the city
 * and its department are used by the app. Implementations never throw.
 */
interface LocationRepository {
    /** Current answer of the system to the location permission, without asking. */
    suspend fun permission(): LocationPermission

    /** Shows the system request (after the explanation of the app) and returns the answer. */
    suspend fun requestPermission(): LocationPermission

    /** City of the phone, or null when the position is not allowed, not available or outside France. */
    suspend fun locateCity(): LocatedCity?

    /**
     * Finds a city typed by the user, to know its department.
     *
     * @param name name of the city, any case and accents.
     * @return the city, or null when it is not known.
     */
    fun findCity(name: String): LocatedCity?
}

/** [LocationRepository] of a phone without location: always the default city. */
class UnavailableLocationRepository : LocationRepository {
    override suspend fun permission(): LocationPermission = LocationPermission.DENIED

    override suspend fun requestPermission(): LocationPermission = LocationPermission.DENIED

    override suspend fun locateCity(): LocatedCity? = null

    override fun findCity(name: String): LocatedCity? = null
}
