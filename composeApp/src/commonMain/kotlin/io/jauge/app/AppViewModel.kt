package io.jauge.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.jauge.core.common.configuration.ApiModeController
import io.jauge.core.domain.model.OnboardingStep
import io.jauge.core.domain.usecase.ObservePreferencesUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * State of the app shell (outside any screen).
 *
 * @property isOfflineMode true while the app uses the offline data instead of the API.
 * @property canToggleOfflineMode true when the hidden debug switch is available.
 */
data class AppUiState(
    val isOfflineMode: Boolean,
    val canToggleOfflineMode: Boolean,
)

/**
 * View model of the app shell: exposes the offline mode indicator and the hidden debug switch.
 *
 * @param apiModeController holder of the current API mode.
 * @param observePreferences preferences of the user, for the first launch.
 */
class AppViewModel(
    private val apiModeController: ApiModeController,
    observePreferences: ObservePreferencesUseCase,
) : ViewModel() {

    /** Step of the first launch the app opens on, null until the preferences are read. */
    val onboardingStep: StateFlow<OnboardingStep?> = observePreferences()
        .map { it.onboardingStep }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLISECONDS), null)

    /** Observed state of the shell. */
    val uiState: StateFlow<AppUiState> = apiModeController.isOfflineMode
        .map { offline -> AppUiState(offline, apiModeController.canSwitchAtRuntime) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLISECONDS),
            initialValue = AppUiState(apiModeController.isOfflineMode.value, apiModeController.canSwitchAtRuntime),
        )

    /** Flips between the offline data and the real API (debug builds only). */
    fun toggleOfflineMode() {
        apiModeController.toggleOfflineMode()
    }

    private companion object {
        const val STOP_TIMEOUT_MILLISECONDS = 5_000L
    }
}
