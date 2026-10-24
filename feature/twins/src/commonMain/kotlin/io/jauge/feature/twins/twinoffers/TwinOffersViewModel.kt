package io.jauge.feature.twins.twinoffers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.jauge.core.domain.error.JaugeError
import io.jauge.core.domain.model.CalmAlert
import io.jauge.core.domain.model.Outcome
import io.jauge.core.domain.model.TwinOffers
import io.jauge.core.domain.model.TwinSort
import io.jauge.core.domain.usecase.CreateCalmAlertUseCase
import io.jauge.core.domain.usecase.DeleteCalmAlertUseCase
import io.jauge.core.domain.usecase.GetAnalysisHistoryUseCase
import io.jauge.core.domain.usecase.GetCalmAlertsUseCase
import io.jauge.core.domain.usecase.GetTwinOffersUseCase
import io.jauge.core.ui.state.ContentState
import io.jauge.core.ui.state.reduce
import io.jauge.core.ui.state.refreshing
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Observed state of the twin offers screen (mockup 06).
 *
 * @property analysisId analysis whose twins are shown, null until known.
 * @property twins the twin offers.
 * @property sort the selected order.
 * @property hasNoAnalysis true when the user has no analysis yet (empty state).
 * @property alerts calm alerts of the user.
 * @property alertSwitchOverride value shown by the switch while a change is being saved, null otherwise.
 */
data class TwinOffersUiState(
    val analysisId: String? = null,
    val twins: ContentState<TwinOffers> = ContentState(),
    val sort: TwinSort = TwinSort.LEAST_DISPUTED,
    val hasNoAnalysis: Boolean = false,
    val alerts: List<CalmAlert> = emptyList(),
    val alertSwitchOverride: Boolean? = null,
) {
    /** The active calm alert of [analysisId], if any. */
    val activeAlert: CalmAlert?
        get() = alerts.firstOrNull { it.analysisId == analysisId && it.isActive }

    /** Value of the "Alerte calme" switch. */
    val isAlertOn: Boolean
        get() = alertSwitchOverride ?: (activeAlert != null)

    /** Threshold shown in the alert card. */
    val alertThreshold: Int
        get() = activeAlert?.threshold ?: CalmAlert.DEFAULT_THRESHOLD
}

/** One time messages of the twin offers screen. */
sealed interface TwinOffersEvent {
    /**
     * The calm alert was turned on or off.
     *
     * @property isOn new state of the alert.
     * @property threshold score under which the user is notified.
     */
    data class AlertChanged(val isOn: Boolean, val threshold: Int) : TwinOffersEvent

    /**
     * The alert could not be changed; the switch went back.
     *
     * @property error the reason, shown as a friendly message.
     */
    data class AlertFailed(val error: JaugeError) : TwinOffersEvent
}

/**
 * State holder of the twin offers screen (mockup 06, tab "Veille"). Without an analysis identifier (tab opened from
 * the tab bar), it shows the twins of the most recent analysis.
 *
 * @param initialAnalysisId analysis passed by the route, null for the tab.
 * @param getTwinOffers loads the twin offers.
 * @param getAnalysisHistory finds the most recent analysis.
 * @param getCalmAlerts observes and reloads the calm alerts.
 * @param createCalmAlert turns a calm alert on.
 * @param deleteCalmAlert turns a calm alert off.
 */
class TwinOffersViewModel(
    private val initialAnalysisId: String?,
    private val getTwinOffers: GetTwinOffersUseCase,
    private val getAnalysisHistory: GetAnalysisHistoryUseCase,
    private val getCalmAlerts: GetCalmAlertsUseCase,
    private val createCalmAlert: CreateCalmAlertUseCase,
    private val deleteCalmAlert: DeleteCalmAlertUseCase,
) : ViewModel() {

    private val state = MutableStateFlow(TwinOffersUiState(analysisId = initialAnalysisId))
    private val eventChannel = Channel<TwinOffersEvent>(Channel.BUFFERED)
    private var loadJob: Job? = null

    /** Observed state of the screen. */
    val uiState: StateFlow<TwinOffersUiState> = state.asStateFlow()

    /** One time messages (alert turned on or off, alert failure). */
    val events: Flow<TwinOffersEvent> = eventChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            getCalmAlerts().collect { alerts -> state.update { it.copy(alerts = alerts) } }
        }
        refresh()
    }

    /** Reloads the twins and the alerts; also called by pull to refresh and by "Réessayer". */
    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            state.update { it.copy(twins = it.twins.refreshing(), hasNoAnalysis = false) }
            val analysisId = initialAnalysisId ?: findLatestAnalysisId() ?: return@launch
            state.update { it.copy(analysisId = analysisId) }
            launch { getCalmAlerts.refresh() }
            val outcome = getTwinOffers(analysisId, state.value.sort)
            state.update { it.copy(twins = it.twins.reduce(outcome)) }
        }
    }

    /**
     * Changes the order of the list and reloads it.
     *
     * @param sort the new order.
     */
    fun selectSort(sort: TwinSort) {
        if (sort == state.value.sort) return
        state.update { it.copy(sort = sort) }
        refresh()
    }

    /**
     * Turns the calm alert of the current analysis on or off. The switch moves at once and goes back on failure.
     *
     * @param isOn the wanted state.
     */
    fun setAlert(isOn: Boolean) {
        val current = state.value
        val analysisId = current.analysisId ?: return
        if (current.alertSwitchOverride != null || isOn == current.isAlertOn) return
        state.update { it.copy(alertSwitchOverride = isOn) }
        viewModelScope.launch {
            val outcome = if (isOn) {
                createCalmAlert(analysisId)
            } else {
                val alert = state.value.activeAlert
                if (alert == null) Outcome.Success(Unit) else deleteCalmAlert(alert.id)
            }
            when (outcome) {
                is Outcome.Success -> {
                    val created = outcome.value as? CalmAlert
                    state.update { previous ->
                        val alerts = when {
                            created != null -> listOf(created) + previous.alerts.filterNot { it.id == created.id }
                            else -> previous.alerts.filterNot { it.analysisId == analysisId }
                        }
                        previous.copy(alerts = alerts, alertSwitchOverride = null)
                    }
                    eventChannel.send(TwinOffersEvent.AlertChanged(isOn, created?.threshold ?: state.value.alertThreshold))
                }
                is Outcome.Failure -> {
                    state.update { it.copy(alertSwitchOverride = null) }
                    eventChannel.send(TwinOffersEvent.AlertFailed(outcome.error))
                }
            }
        }
    }

    /** The most recent analysis of the user, or null after updating the state (empty or error). */
    private suspend fun findLatestAnalysisId(): String? {
        val refreshOutcome = getAnalysisHistory.refresh()
        val latest = getAnalysisHistory().first().firstOrNull()?.id
        if (latest != null) return latest
        state.update {
            when (refreshOutcome) {
                is Outcome.Failure -> it.copy(twins = it.twins.reduce(refreshOutcome))
                is Outcome.Success -> it.copy(twins = ContentState(isLoading = false), hasNoAnalysis = true)
            }
        }
        return null
    }
}
