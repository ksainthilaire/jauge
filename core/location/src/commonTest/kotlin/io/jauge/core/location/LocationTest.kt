package io.jauge.core.location

import io.jauge.core.domain.model.LocatedCity
import io.jauge.core.domain.model.LocationPermission
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LocationTest {

    private class FakeProvider(
        var permission: LocationPermission = LocationPermission.GRANTED,
        var position: DevicePosition? = null,
        var failure: Exception? = null,
    ) : DevicePositionProvider {
        override suspend fun permission(): LocationPermission = failure?.let { throw it } ?: permission

        override suspend fun requestPermission(): LocationPermission = permission

        override suspend fun currentPosition(): DevicePosition? = failure?.let { throw it } ?: position
    }

    @Test
    fun positionBecomesTheNearestCity() {
        assertEquals(LocatedCity("Lyon", "69"), FrenchCities.nearest(DevicePosition(45.77, 4.88)))
        assertEquals(LocatedCity("Paris", "75"), FrenchCities.nearest(DevicePosition(48.86, 2.35)))
        assertEquals(LocatedCity("Ajaccio", "2A"), FrenchCities.nearest(DevicePosition(41.93, 8.74)))
        assertEquals(LocatedCity("Saint-Denis", "974"), FrenchCities.nearest(DevicePosition(-20.9, 55.45)))
    }

    @Test
    fun positionOutsideFranceHasNoCity() {
        assertNull(FrenchCities.nearest(DevicePosition(40.71, -74.0)))
        assertNull(FrenchCities.nearest(DevicePosition(Double.NaN, 2.0)))
    }

    @Test
    fun typedCityIsFoundWithoutAccentsOrDashes() {
        assertEquals(LocatedCity("Saint-Étienne", "42"), FrenchCities.byName(" saint etienne "))
        assertEquals(LocatedCity("Lyon", "69"), FrenchCities.byName("LYON"))
        assertNull(FrenchCities.byName("Villeneuve-Inconnue"))
        assertNull(FrenchCities.byName("  "))
    }

    @Test
    fun repositoryNeverReadsThePositionWithoutPermission() = runTest {
        val provider = FakeProvider(permission = LocationPermission.DENIED, position = DevicePosition(45.76, 4.84))
        assertNull(LocationRepositoryImplementation(provider).locateCity())
        provider.permission = LocationPermission.GRANTED
        assertEquals(LocatedCity("Lyon", "69"), LocationRepositoryImplementation(provider).locateCity())
    }

    @Test
    fun failuresBecomeNoCityAndRefusal() = runTest {
        val repository = LocationRepositoryImplementation(FakeProvider(failure = IllegalStateException("services off")))
        assertNull(repository.locateCity())
        assertEquals(LocationPermission.DENIED, repository.permission())
    }

    @Test
    fun referenceCitiesCoverEveryMainlandDepartment() {
        val departments = FrenchCities.all.map { it.departmentCode }.toSet()
        val expected = (1..95).filter { it != 20 }.map { it.toString().padStart(2, '0') } + listOf("2A", "2B")
        assertEquals(emptyList(), expected.filterNot { it in departments })
    }
}
