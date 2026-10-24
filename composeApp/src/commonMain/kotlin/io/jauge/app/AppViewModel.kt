package io.jauge.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.jauge.core.common.configuration.ApiModeController
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
 */
class AppViewModel(private val apiModeController: ApiModeController) : ViewModel() {

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
