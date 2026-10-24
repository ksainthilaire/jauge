package io.jauge.feature.analysis.manualentry

import androidx.lifecycle.ViewModel
import io.jauge.core.domain.model.OfferSource
import io.jauge.feature.analysis.pending.PendingAnalysisStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * State of the manual entry screen.
 *
 * @property jobTitle typed job title.
 * @property city typed city.
 * @property isIncomplete true when the user tried to continue with an empty field.
 * @property startedRequestId request to open on the reading screen.
 */
data class ManualEntryUiState(
    val jobTitle: String = "",
    val city: String = "",
    val isIncomplete: Boolean = false,
    val startedRequestId: String? = null,
)

/**
 * View model of the manual entry (mockup 10, "Saisir le poste et la ville à la main").
 *
 * @param pendingAnalyses keeps the offer for the reading screen.
 */
class ManualEntryViewModel(private val pendingAnalyses: PendingAnalysisStore) : ViewModel() {
    private val state = MutableStateFlow(ManualEntryUiState())

    /** Observed state of the screen. */
    val uiState: StateFlow<ManualEntryUiState> = state.asStateFlow()

    /**
     * The job title changed.
     *
     * @param jobTitle new text.
     */
    fun onJobTitleChange(jobTitle: String) {
        state.update { it.copy(jobTitle = jobTitle.take(MAXIMUM_LENGTH), isIncomplete = false) }
    }

    /**
     * The city changed.
     *
     * @param city new text.
     */
    fun onCityChange(city: String) {
        state.update { it.copy(city = city.take(MAXIMUM_LENGTH), isIncomplete = false) }
    }

    /** "Jauger cette offre". */
    fun analyze() {
        val current = state.value
        if (current.jobTitle.isBlank() || current.city.isBlank()) {
            state.update { it.copy(isIncomplete = true) }
            return
        }
        val requestId = pendingAnalyses.put(OfferSource.Manual(current.jobTitle.trim(), current.city.trim()))
        state.update { it.copy(startedRequestId = requestId) }
    }

    /** The reading screen was opened. */
    fun onReadingOpened() {
        state.update { it.copy(startedRequestId = null) }
    }

    private companion object {
        const val MAXIMUM_LENGTH = 120
    }
}
