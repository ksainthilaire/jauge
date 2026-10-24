package io.jauge.feature.verdict.methodology

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.jauge.core.domain.error.JaugeError
import io.jauge.core.domain.model.Methodology
import io.jauge.core.domain.model.Outcome
import io.jauge.core.domain.usecase.GetMethodologyUseCase
import io.jauge.core.domain.usecase.SubmitActualApplicantsUseCase
import io.jauge.core.ui.state.ContentState
import io.jauge.core.ui.state.reduce
import io.jauge.core.ui.state.refreshing
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State of the "Tu connais le vrai chiffre ?" dialog.
 *
 * @property isOpen true while the dialog is shown.
 * @property text number typed by the user.
 * @property isInvalid true when the typed number is not between 1 and 100 000.
 * @property isSending true while the number is sent.
 */
data class FeedbackState(
    val isOpen: Boolean = false,
    val text: String = "",
    val isInvalid: Boolean = false,
    val isSending: Boolean = false,
)

/**
 * Observed state of the methodology screen.
 *
 * @property methodology how the estimate was made.
 * @property feedback state of the feedback dialog.
 */
data class MethodologyUiState(
    val methodology: ContentState<Methodology> = ContentState(),
    val feedback: FeedbackState = FeedbackState(),
)

/** One time messages of the methodology screen. */
sealed interface MethodologyEvent {
    /** The real number was sent: thank the user. */
    data object FeedbackSent : MethodologyEvent

    /**
     * Sending failed.
     *
     * @property error the reason, shown as a friendly message.
     */
    data class FeedbackFailed(val error: JaugeError) : MethodologyEvent
}

/**
 * State holder of "Comment on estime" (mockup 09).
 *
 * @param analysisId identifier of the analysis.
 * @param getMethodology loads how the estimate was made.
 * @param submitActualApplicants sends the real number known by the user.
 */
class MethodologyViewModel(
    private val analysisId: String,
    private val getMethodology: GetMethodologyUseCase,
    private val submitActualApplicants: SubmitActualApplicantsUseCase,
) : ViewModel() {

    private val state = MutableStateFlow(MethodologyUiState())
    private val eventChannel = Channel<MethodologyEvent>(Channel.BUFFERED)

    /** Observed state of the screen. */
    val uiState: StateFlow<MethodologyUiState> = state.asStateFlow()

    /** One time messages (confirmation or failure of the feedback). */
    val events: Flow<MethodologyEvent> = eventChannel.receiveAsFlow()

    init {
        refresh()
    }

    /** Loads the methodology; also called by pull to refresh and by "Réessayer". */
    fun refresh() {
        viewModelScope.launch {
            state.update { it.copy(methodology = it.methodology.refreshing()) }
            val outcome = getMethodology(analysisId)
            state.update { it.copy(methodology = it.methodology.reduce(outcome)) }
        }
    }

    /** Opens the feedback dialog. */
    fun openFeedback() {
        state.update { it.copy(feedback = FeedbackState(isOpen = true)) }
    }

    /** Closes the feedback dialog without sending. */
    fun dismissFeedback() {
        state.update { it.copy(feedback = FeedbackState()) }
    }

    /**
     * Updates the typed number, keeping digits only.
     *
     * @param text the new text.
     */
    fun onFeedbackTextChange(text: String) {
        val digits = text.filter { it.isDigit() }.take(MAXIMUM_DIGITS)
        state.update { it.copy(feedback = it.feedback.copy(text = digits, isInvalid = false)) }
    }

    /** Sends the typed number when it is plausible. */
    fun sendFeedback() {
        val feedback = state.value.feedback
        val number = feedback.text.toIntOrNull()
        if (number == null || number !in 1..MAXIMUM_APPLICANTS) {
            state.update { it.copy(feedback = it.feedback.copy(isInvalid = true)) }
            return
        }
        if (feedback.isSending) return
        viewModelScope.launch {
            state.update { it.copy(feedback = it.feedback.copy(isSending = true)) }
            when (val outcome = submitActualApplicants(analysisId, number)) {
                is Outcome.Success -> {
                    state.update { it.copy(feedback = FeedbackState()) }
                    eventChannel.send(MethodologyEvent.FeedbackSent)
                }
                is Outcome.Failure -> {
                    state.update { it.copy(feedback = it.feedback.copy(isSending = false)) }
                    eventChannel.send(MethodologyEvent.FeedbackFailed(outcome.error))
                }
            }
        }
    }

    private companion object {
        const val MAXIMUM_DIGITS = 6
        const val MAXIMUM_APPLICANTS = 100_000
    }
}
