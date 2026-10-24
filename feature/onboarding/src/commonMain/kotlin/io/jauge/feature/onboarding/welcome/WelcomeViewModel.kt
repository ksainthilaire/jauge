package io.jauge.feature.onboarding.welcome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.jauge.core.domain.usecase.CompleteOnboardingStepUseCase
import kotlinx.coroutines.launch

/**
 * View model of the welcome screen (mockup 01): records that the welcome was passed or skipped.
 *
 * @param completeOnboardingStep records the first launch answers.
 */
class WelcomeViewModel(private val completeOnboardingStep: CompleteOnboardingStepUseCase) : ViewModel() {

    /**
     * "Analyser ma première offre" or "Me connecter": the welcome is passed, the account step comes next.
     *
     * @param onDone called once the answer is saved.
     */
    fun passWelcome(onDone: () -> Unit) {
        viewModelScope.launch {
            completeOnboardingStep.passWelcome()
            onDone()
        }
    }

    /**
     * "Passer": the whole first launch is skipped.
     *
     * @param onDone called once the answer is saved.
     */
    fun skip(onDone: () -> Unit) {
        viewModelScope.launch {
            completeOnboardingStep.skipAll()
            onDone()
        }
    }
}
