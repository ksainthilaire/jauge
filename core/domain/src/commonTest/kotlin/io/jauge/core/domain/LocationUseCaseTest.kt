package io.jauge.core.domain

import io.jauge.core.domain.model.LocatedCity
import io.jauge.core.domain.model.LocationPermission
import io.jauge.core.domain.model.SearchArea
import io.jauge.core.domain.model.SearchAreaOrigin
import io.jauge.core.domain.model.UserPreferences
import io.jauge.core.domain.repository.LocationRepository
import io.jauge.core.domain.repository.PreferencesRepository
import io.jauge.core.domain.usecase.LocateSearchAreaUseCase
import io.jauge.core.domain.usecase.ObserveSearchAreaUseCase
import io.jauge.core.domain.usecase.UpdateSettingsUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocationUseCaseTest {
    private val preferences = MutableStateFlow(UserPreferences())
    private val repository = object : PreferencesRepository {
        override fun observePreferences(): Flow<UserPreferences> = preferences
        override suspend fun update(transform: (UserPreferences) -> UserPreferences) {
            preferences.value = transform(preferences.value)
        }
    }

    private class FakeLocation(
        var permission: LocationPermission = LocationPermission.NOT_DETERMINED,
        var answer: LocationPermission = LocationPermission.GRANTED,
        var city: LocatedCity? = LocatedCity("Lyon", "69"),
    ) : LocationRepository {
        var requests = 0

        override suspend fun permission() = permission

        override suspend fun requestPermission(): LocationPermission {
            requests += 1
            permission = answer
            return answer
        }

        override suspend fun locateCity() = if (permission == LocationPermission.GRANTED) city else null

        override fun findCity(name: String) = if (name.equals("saint etienne", ignoreCase = true)) LocatedCity("Saint-Étienne", "42") else null
    }

    @Test
    fun defaultAreaIsParis() = runTest {
        assertEquals(SearchArea("Paris", 30, "75", SearchAreaOrigin.DEFAULT), ObserveSearchAreaUseCase(repository).current())
    }

    @Test
    fun explanationThenAllowedUsesTheCityOfThePhone() = runTest {
        val location = FakeLocation()
        val useCase = LocateSearchAreaUseCase(repository, location)
        assertTrue(useCase.shouldExplain())
        assertEquals(LocationPermission.GRANTED, useCase.allow())
        assertEquals(SearchArea("Lyon", 30, "69", SearchAreaOrigin.DEVICE), preferences.value.searchArea)
        assertFalse(useCase.shouldExplain())
    }

    @Test
    fun refusedKeepsParisAndIsNotAskedAgain() = runTest {
        val location = FakeLocation(answer = LocationPermission.DENIED)
        val useCase = LocateSearchAreaUseCase(repository, location)
        useCase.allow()
        assertEquals(SearchArea.DEFAULT, ObserveSearchAreaUseCase(repository).current())
        assertFalse(useCase.shouldExplain())
    }

    @Test
    fun laterKeepsParisWithoutAskingTheSystem() = runTest {
        val location = FakeLocation()
        val useCase = LocateSearchAreaUseCase(repository, location)
        useCase.decline()
        assertEquals(0, location.requests)
        assertFalse(useCase.shouldExplain())
        assertEquals("Paris", ObserveSearchAreaUseCase(repository).current().city)
    }

    @Test
    fun unavailablePositionKeepsTheDefaultCity() = runTest {
        val location = FakeLocation(permission = LocationPermission.GRANTED, city = null)
        assertEquals(SearchArea.DEFAULT, LocateSearchAreaUseCase(repository, location).refresh())
    }

    @Test
    fun typedCityWinsOverThePosition() = runTest {
        val location = FakeLocation(permission = LocationPermission.GRANTED)
        UpdateSettingsUseCase(repository, location).setSearchArea("saint etienne", 50)
        assertEquals(SearchArea("Saint-Étienne", 50, "42", SearchAreaOrigin.MANUAL), preferences.value.searchArea)
        LocateSearchAreaUseCase(repository, location).refresh()
        assertEquals("Saint-Étienne", preferences.value.searchArea?.city)
    }

    @Test
    fun changingOnlyTheRadiusKeepsTheCityOfThePhone() = runTest {
        val location = FakeLocation(permission = LocationPermission.GRANTED)
        LocateSearchAreaUseCase(repository, location).refresh()
        UpdateSettingsUseCase(repository, location).setSearchArea("Lyon", 100)
        assertEquals(SearchArea("Lyon", 100, "69", SearchAreaOrigin.DEVICE), preferences.value.searchArea)
    }

    @Test
    fun useDevicePositionReplacesTheTypedCity() = runTest {
        val location = FakeLocation(permission = LocationPermission.DENIED)
        UpdateSettingsUseCase(repository, location).setSearchArea("Nantes", 10)
        location.answer = LocationPermission.GRANTED
        val area = LocateSearchAreaUseCase(repository, location).useDevicePosition()
        assertEquals(SearchArea("Lyon", 10, "69", SearchAreaOrigin.DEVICE), area)
    }

    @Test
    fun useDevicePositionWithoutPositionFallsBackToParis() = runTest {
        val location = FakeLocation(answer = LocationPermission.DENIED)
        UpdateSettingsUseCase(repository, location).setSearchArea("Nantes", 50)
        val area = LocateSearchAreaUseCase(repository, location).useDevicePosition()
        assertEquals(SearchArea.DEFAULT.copy(radiusKilometers = 50), area)
    }
}
