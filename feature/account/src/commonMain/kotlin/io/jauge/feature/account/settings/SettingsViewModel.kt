package io.jauge.feature.account.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.jauge.core.domain.model.SearchArea
import io.jauge.core.domain.model.SearchAreaOrigin
import io.jauge.core.domain.usecase.LocateSearchAreaUseCase
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
 * @property origin where the city in use comes from (position of the phone, typed, or Paris by default).
 * @property isLocating true while "Utiliser ma position" looks for the city of the phone.
 */
data class SettingsUiState(
    val calmAlertsEnabled: Boolean = false,
    val city: String = "",
    val radiusKilometers: Int = DEFAULT_RADIUS_KILOMETERS,
    val isSaved: Boolean = false,
    val origin: SearchAreaOrigin = SearchAreaOrigin.DEFAULT,
    val isLocating: Boolean = false,
) {
    /** Defaults. */
    companion object {
        /** Radius proposed before the user chooses one. */
        const val DEFAULT_RADIUS_KILOMETERS = SearchArea.DEFAULT_RADIUS_KILOMETERS
    }
}

/**
 * View model of the settings (mockup 13, "Alertes « calme »" and "Zone de recherche").
 *
 * @param observePreferences device preferences.
 * @param updateSettings saves the settings.
 * @param locateSearchArea finds the city of the phone ("Utiliser ma position").
 */
class SettingsViewModel(
    private val observePreferences: ObservePreferencesUseCase,
    private val updateSettings: UpdateSettingsUseCase,
    private val locateSearchArea: LocateSearchAreaUseCase,
) : ViewModel() {
    private val state = MutableStateFlow(SettingsUiState())

    /** Observed state of the screen. */
    val uiState: StateFlow<SettingsUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            val preferences = observePreferences().first()
            val area = preferences.effectiveSearchArea
            state.value = SettingsUiState(
                calmAlertsEnabled = preferences.calmAlertsEnabled,
                city = area.city,
                radiusKilometers = area.radiusKilometers,
                origin = area.origin,
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

    /** "Enregistrer": saves the search area (a blank city clears it: the city of the phone or Paris again). */
    fun saveSearchArea() {
        viewModelScope.launch {
            updateSettings.setSearchArea(state.value.city, state.value.radiusKilometers)
            val area = observePreferences().first().effectiveSearchArea
            state.update { it.copy(city = area.city, origin = area.origin, isSaved = true) }
        }
    }

    /** "Utiliser ma position": asks the permission if needed, then uses the city of the phone (or Paris without it). */
    fun useDevicePosition() {
        if (state.value.isLocating) return
        state.update { it.copy(isLocating = true, isSaved = false) }
        viewModelScope.launch {
            val area = locateSearchArea.useDevicePosition()
            state.update { it.copy(city = area.city, radiusKilometers = area.radiusKilometers, origin = area.origin, isLocating = false) }
        }
    }

    private companion object {
        const val MAXIMUM_CITY_LENGTH = 80
    }
}
