package io.jauge.feature.onboarding.location

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.jauge.core.domain.usecase.LocateSearchAreaUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * State of the location explanation.
 *
 * @property isVisible true while the explanation is shown.
 * @property isLocating true while the system request is open or the city is being found.
 */
data class LocationPromptUiState(val isVisible: Boolean = false, val isLocating: Boolean = false)

/**
 * View model of the location explanation, shown once after the first launch when the system has not been asked yet.
 * When the position is already allowed, it only updates the city of the search area.
 *
 * @param locateSearchArea finds the search area from the position of the phone.
 */
class LocationPromptViewModel(private val locateSearchArea: LocateSearchAreaUseCase) : ViewModel() {
    private val state = MutableStateFlow(LocationPromptUiState())

    /** Observed state. */
    val uiState: StateFlow<LocationPromptUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            if (locateSearchArea.shouldExplain()) {
                state.value = LocationPromptUiState(isVisible = true)
            } else {
                locateSearchArea.refresh()
            }
        }
    }

    /** "Autoriser la localisation": closes the explanation, then shows the system request. */
    fun allow() {
        if (state.value.isLocating) return
        state.value = LocationPromptUiState(isVisible = false, isLocating = true)
        viewModelScope.launch {
            locateSearchArea.allow()
            state.value = LocationPromptUiState()
        }
    }

    /** "Plus tard": Paris stays the search area, changeable in the settings. */
    fun decline() {
        state.value = LocationPromptUiState()
        viewModelScope.launch { locateSearchArea.decline() }
    }
}
