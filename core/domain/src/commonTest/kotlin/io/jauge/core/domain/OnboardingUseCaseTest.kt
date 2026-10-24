package io.jauge.core.domain

import io.jauge.core.domain.model.OnboardingStep
import io.jauge.core.domain.model.SearchArea
import io.jauge.core.domain.model.UserPreferences
import io.jauge.core.domain.repository.PreferencesRepository
import io.jauge.core.domain.usecase.CompleteOnboardingStepUseCase
import io.jauge.core.domain.usecase.UpdateSettingsUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OnboardingUseCaseTest {
    private val preferences = MutableStateFlow(UserPreferences())
    private val repository = object : PreferencesRepository {
        override fun observePreferences(): Flow<UserPreferences> = preferences
        override suspend fun update(transform: (UserPreferences) -> UserPreferences) {
            preferences.value = transform(preferences.value)
        }
    }

    @Test
    fun stepsFollowTheAnswers() = runTest {
        val useCase = CompleteOnboardingStepUseCase(repository)
        assertEquals(OnboardingStep.WELCOME, preferences.value.onboardingStep)
        useCase.passWelcome()
        assertEquals(OnboardingStep.ACCOUNT, preferences.value.onboardingStep)
        useCase.passAccount()
        assertEquals(OnboardingStep.ALERTS, preferences.value.onboardingStep)
        useCase.answerAlerts(alertsEnabled = true)
        assertEquals(OnboardingStep.DONE, preferences.value.onboardingStep)
        assertTrue(preferences.value.calmAlertsEnabled)
    }

    @Test
    fun searchAreaIsCleanedAndClearedWhenBlank() = runTest {
        val useCase = UpdateSettingsUseCase(repository)
        useCase.setSearchArea("  Lyon ", 500)
        assertEquals(SearchArea("Lyon", 200), preferences.value.searchArea)
        useCase.setSearchArea(" ", 30)
        assertNull(preferences.value.searchArea)
    }
}
