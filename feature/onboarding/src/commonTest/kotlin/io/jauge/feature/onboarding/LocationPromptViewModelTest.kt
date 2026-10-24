package io.jauge.feature.onboarding

import io.jauge.core.domain.model.LocatedCity
import io.jauge.core.domain.model.LocationPermission
import io.jauge.core.domain.model.SearchAreaOrigin
import io.jauge.core.domain.repository.LocationRepository
import io.jauge.core.domain.usecase.LocateSearchAreaUseCase
import io.jauge.feature.onboarding.location.LocationPromptViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Position of the phone with a programmable answer. */
class FakeLocationRepository(
    var permission: LocationPermission = LocationPermission.NOT_DETERMINED,
    var answer: LocationPermission = LocationPermission.GRANTED,
) : LocationRepository {
    /** Number of system requests. */
    var requests = 0

    override suspend fun permission() = permission

    override suspend fun requestPermission(): LocationPermission {
        requests++
        permission = answer
        return answer
    }

    override suspend fun locateCity() = if (permission == LocationPermission.GRANTED) LocatedCity("Lyon", "69") else null

    override fun findCity(name: String): LocatedCity? = null
}

@OptIn(ExperimentalCoroutinesApi::class)
class LocationPromptViewModelTest {
    private val preferences = FakePreferencesRepository()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(location: LocationRepository) = LocationPromptViewModel(LocateSearchAreaUseCase(preferences, location))

    @Test
    fun explanationIsShownBeforeTheSystemRequest() {
        val location = FakeLocationRepository()
        val viewModel = viewModel(location)
        assertTrue(viewModel.uiState.value.isVisible)
        assertEquals(0, location.requests)
        viewModel.allow()
        assertFalse(viewModel.uiState.value.isVisible)
        assertEquals(1, location.requests)
        assertEquals(SearchAreaOrigin.DEVICE, preferences.preferences.value.searchArea?.origin)
    }

    @Test
    fun laterKeepsParisAndNeverAsksTheSystem() {
        val location = FakeLocationRepository()
        val viewModel = viewModel(location)
        viewModel.decline()
        assertFalse(viewModel.uiState.value.isVisible)
        assertEquals(0, location.requests)
        assertNull(preferences.preferences.value.searchArea)
        assertEquals("Paris", preferences.preferences.value.effectiveSearchArea.city)
        assertFalse(viewModel(location).uiState.value.isVisible)
    }

    @Test
    fun alreadyAllowedUpdatesTheCityWithoutExplanation() {
        val viewModel = viewModel(FakeLocationRepository(permission = LocationPermission.GRANTED))
        assertFalse(viewModel.uiState.value.isVisible)
        assertEquals("Lyon", preferences.preferences.value.searchArea?.city)
    }

    @Test
    fun refusedBySystemIsNotExplainedAgain() {
        val viewModel = viewModel(FakeLocationRepository(permission = LocationPermission.DENIED))
        assertFalse(viewModel.uiState.value.isVisible)
    }
}
