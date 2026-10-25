package io.jauge.feature.analysis.reading

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.jauge.core.domain.error.JaugeError
import io.jauge.core.domain.model.AnalysisProgress
import io.jauge.core.domain.model.OfferSource
import io.jauge.core.domain.usecase.AnalysisRequestKeys
import io.jauge.core.domain.usecase.AnalyzeOfferUseCase
import io.jauge.feature.analysis.pending.PendingAnalysisStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State of the reading screen (mockup 03).
 *
 * @property source what is being read, null when the request is unknown.
 * @property progress last progress received, null before the first one.
 * @property error friendly error when the analysis failed (other than an unreadable offer), null otherwise.
 * @property completedAnalysisId identifier of the finished analysis: the verdict opens.
 * @property unreadable set when the offer could not be read: the unreadable offer screen opens.
 */
data class ReadingUiState(
    val source: OfferSource? = null,
    val progress: AnalysisProgress.Reading? = null,
    val error: JaugeError? = null,
    val completedAnalysisId: String? = null,
    val unreadable: JaugeError.UnreadableOffer? = null,
)

/**
 * View model of the reading screen (mockup 03): runs the analysis of the offer kept in [PendingAnalysisStore] and
 * follows its progress.
 *
 * The request has one key ([AnalysisRequestKeys]), sent as `Idempotency-Key`. "Réessayer" keeps it after a lost
 * answer (no connection, timeout, busy server), so the server gives back the analysis it may already have created
 * instead of a second one; after any other failure a new key starts a new request.
 *
 * @param requestId identifier of the request in the store.
 * @param pendingAnalyses keeps the offer chosen on the previous screen.
 * @param analyzeOffer runs the analysis.
 */
class ReadingViewModel(
    private val requestId: String,
    private val pendingAnalyses: PendingAnalysisStore,
    private val analyzeOffer: AnalyzeOfferUseCase,
) : ViewModel() {
    private val state = MutableStateFlow(ReadingUiState(source = pendingAnalyses.get(requestId)))
    private var job: Job? = null
    private var requestKey = AnalysisRequestKeys.newKey()

    /** Key of the current request, sent as `Idempotency-Key` (visible for tests). */
    val currentRequestKey: String
        get() = requestKey

    /** Observed state of the screen. */
    val uiState: StateFlow<ReadingUiState> = state.asStateFlow()

    init {
        start()
    }

    /** Starts the analysis again after an error ("Réessayer"). */
    fun retry() {
        val previousError = state.value.error
        if (previousError != null && !AnalysisRequestKeys.keepsKeyOnRetry(previousError)) requestKey = AnalysisRequestKeys.newKey()
        start()
    }

    /** The next screen was opened: the one shot results are cleared. */
    fun onNavigated() {
        state.update { it.copy(completedAnalysisId = null, unreadable = null) }
    }

    private fun start() {
        val source = state.value.source
        if (source == null) {
            state.update { it.copy(error = JaugeError.NotFound) }
            return
        }
        job?.cancel()
        state.update { it.copy(progress = null, error = null) }
        job = viewModelScope.launch {
            analyzeOffer(source, requestKey).collect { progress ->
                when (progress) {
                    is AnalysisProgress.Reading -> state.update { it.copy(progress = progress) }
                    is AnalysisProgress.Completed -> state.update { it.copy(completedAnalysisId = progress.analysis.id) }
                    is AnalysisProgress.Failed -> {
                        val error = progress.error
                        if (error is JaugeError.UnreadableOffer) {
                            state.update { it.copy(unreadable = error) }
                        } else {
                            state.update { it.copy(error = error) }
                        }
                    }
                }
            }
        }
    }
}
