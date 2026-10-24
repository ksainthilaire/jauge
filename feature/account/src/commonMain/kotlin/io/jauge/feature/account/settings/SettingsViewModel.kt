package io.jauge.feature.account.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.jauge.core.domain.model.SearchArea
import io.jauge.core.domain.usecase.ObservePreferencesUseCase
import io.jauge.core.domain.usecase.UpdateSettingsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State of the settings screen.
 *
 * @property calmAlertsEnabled calm alerts switch.
 * @property city city of the search area being typed.
 * @property radiusKilometers chosen radius.
 * @property isSaved true right after the search area was saved.
 */
data class SettingsUiState(
    val calmAlertsEnabled: Boolean = false,
    val city: String = "",
    val radiusKilometers: Int = DEFAULT_RADIUS_KILOMETERS,
    val isSaved: Boolean = false,
) {
    /** Defaults. */
    companion object {
        /** Radius proposed before the user chooses one. */
        const val DEFAULT_RADIUS_KILOMETERS = 30
    }
}

/**
 * View model of the settings (mockup 13, "Alertes « calme »" and "Zone de recherche").
 *
 * @param observePreferences device preferences.
 * @param updateSettings saves the settings.
 */
class SettingsViewModel(
    private val observePreferences: ObservePreferencesUseCase,
    private val updateSettings: UpdateSettingsUseCase,
) : ViewModel() {
    private val state = MutableStateFlow(SettingsUiState())

    /** Observed state of the screen. */
    val uiState: StateFlow<SettingsUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            val preferences = observePreferences().first()
            state.value = SettingsUiState(
                calmAlertsEnabled = preferences.calmAlertsEnabled,
                city = preferences.searchArea?.city.orEmpty(),
                radiusKilometers = preferences.searchArea?.radiusKilometers ?: SettingsUiState.DEFAULT_RADIUS_KILOMETERS,
            )
        }
    }

    /**
     * Turns calm alerts on or off, saved at once.
     *
     * @param enabled new value.
     */
    fun setCalmAlertsEnabled(enabled: Boolean) {
        state.update { it.copy(calmAlertsEnabled = enabled) }
        viewModelScope.launch { updateSettings.setCalmAlertsEnabled(enabled) }
    }

    /**
     * The city changed.
     *
     * @param city new text.
     */
    fun onCityChange(city: String) {
        state.update { it.copy(city = city.take(MAXIMUM_CITY_LENGTH), isSaved = false) }
    }

    /**
     * A radius was chosen.
     *
     * @param radiusKilometers one of [SearchArea.RADII_KILOMETERS].
     */
    fun onRadiusChange(radiusKilometers: Int) {
        state.update { it.copy(radiusKilometers = radiusKilometers, isSaved = false) }
    }

    /** "Enregistrer": saves the search area (a blank city clears it). */
    fun saveSearchArea() {
        viewModelScope.launch {
            updateSettings.setSearchArea(state.value.city, state.value.radiusKilometers)
            state.update { it.copy(isSaved = true) }
        }
    }

    private companion object {
        const val MAXIMUM_CITY_LENGTH = 80
    }
}
