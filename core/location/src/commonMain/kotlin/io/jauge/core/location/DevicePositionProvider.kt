package io.jauge.core.location

import io.jauge.core.domain.model.LocationPermission

/**
 * Coarse position of the phone (Android `ACCESS_COARSE_LOCATION`, iOS "When In Use" with reduced accuracy). It is
 * only used on the phone by [LocationRepositoryImplementation].
 */
interface DevicePositionProvider {
    /** Current answer of the system, without asking. */
    suspend fun permission(): LocationPermission

    /** Shows the system request and returns the answer. */
    suspend fun requestPermission(): LocationPermission

    /** Current coarse position, or null when it is not allowed or not available in a few seconds. */
    suspend fun currentPosition(): DevicePosition?
}
