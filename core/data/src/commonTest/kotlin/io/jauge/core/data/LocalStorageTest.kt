package io.jauge.core.data

import io.jauge.core.data.preferences.InMemoryKeyValueStore
import io.jauge.core.data.preferences.PreferencesRepositoryImplementation
import io.jauge.core.domain.model.OnboardingStep
import io.jauge.core.domain.model.SearchArea
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LocalStorageTest {

    @Test
    fun preferencesSurviveARestart() = runTest {
        val store = InMemoryKeyValueStore()
        val first = PreferencesRepositoryImplementation(store)
        assertEquals(OnboardingStep.WELCOME, first.observePreferences().first().onboardingStep)
        first.update { it.copy(hasPassedWelcome = true, searchArea = SearchArea("Lyon", 30)) }
        val restarted = PreferencesRepositoryImplementation(store).observePreferences().first()
        assertEquals(OnboardingStep.ACCOUNT, restarted.onboardingStep)
        assertEquals(SearchArea("Lyon", 30), restarted.searchArea)
    }

    @Test
    fun unreadableValuesFallBackToDefaults() = runTest {
        val store = InMemoryKeyValueStore()
        store.putString("settings.searchArea.city", "Lyon")
        store.putString("settings.searchArea.radius", "beaucoup")
        assertNull(PreferencesRepositoryImplementation(store).observePreferences().first().searchArea)
    }
}
