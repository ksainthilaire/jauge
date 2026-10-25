package io.jauge.core.location

import io.jauge.core.domain.model.LocatedCity
import io.jauge.core.domain.model.LocationPermission
import io.jauge.core.domain.repository.LocationRepository
import kotlinx.coroutines.CancellationException

/**
 * [LocationRepository] that reads the coarse position of the phone and turns it into the nearest city with
 * [FrenchCities], on the phone. The position itself is never kept nor sent.
 *
 * @param provider position of the phone.
 */
class LocationRepositoryImplementation(private val provider: DevicePositionProvider) : LocationRepository {

    override suspend fun permission(): LocationPermission = safely(LocationPermission.DENIED) { provider.permission() }

    override suspend fun requestPermission(): LocationPermission = safely(LocationPermission.DENIED) { provider.requestPermission() }

    override suspend fun locateCity(): LocatedCity? = safely(null) {
        if (provider.permission() != LocationPermission.GRANTED) null else provider.currentPosition()?.let(FrenchCities::nearest)
    }

    override fun findCity(name: String): LocatedCity? = FrenchCities.byName(name)

    private suspend fun <T> safely(fallback: T, block: suspend () -> T): T = try {
        block()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Exception) {
        fallback
    }
}
