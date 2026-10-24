package io.jauge.feature.share.shares

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.jauge.core.domain.error.JaugeError
import io.jauge.core.domain.model.AnalysisSummary
import io.jauge.core.domain.model.Outcome
import io.jauge.core.domain.model.Share
import io.jauge.core.domain.usecase.DeleteShareUseCase
import io.jauge.core.domain.usecase.GetAnalysisHistoryUseCase
import io.jauge.core.domain.usecase.GetSharesUseCase
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
 * One row of the tab "Partages".
 *
 * @property share the share.
 * @property analysis summary of the shared analysis, null when it is not in the history any more.
 */
data class ShareItem(val share: Share, val analysis: AnalysisSummary?)

/**
 * Observed state of the tab "Partages".
 *
 * @property shares shares of the user, newest first.
 * @property shareToDelete share waiting for the confirmation of its deletion, null otherwise.
 */
data class SharesUiState(
    val shares: ContentState<List<ShareItem>> = ContentState(),
    val shareToDelete: ShareItem? = null,
)

/** One time messages of the tab "Partages". */
sealed interface SharesEvent {
    /** A share was deleted. */
    data object Deleted : SharesEvent

    /**
     * An action failed.
     *
     * @property error the reason, shown as a friendly message.
     */
    data class Failed(val error: JaugeError) : SharesEvent
}

/**
 * State holder of the tab "Partages": every shared analysis, with its public link.
 *
 * @param getShares loads the shares.
 * @param getAnalysisHistory gives the titles and scores of the shared analyses.
 * @param deleteShare deletes a share (the public link stops working).
 */
class SharesViewModel(
    private val getShares: GetSharesUseCase,
    private val getAnalysisHistory: GetAnalysisHistoryUseCase,
    private val deleteShare: DeleteShareUseCase,
) : ViewModel() {

    private val state = MutableStateFlow(SharesUiState())
    private val eventChannel = Channel<SharesEvent>(Channel.BUFFERED)
    private var history: List<AnalysisSummary> = emptyList()

    /** Observed state of the screen. */
    val uiState: StateFlow<SharesUiState> = state.asStateFlow()

    /** One time messages. */
    val events: Flow<SharesEvent> = eventChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            getAnalysisHistory().collect { analyses ->
                history = analyses
                state.update { current ->
                    val items = current.shares.content ?: return@update current
                    current.copy(shares = current.shares.copy(content = items.map { it.copy(analysis = summaryOf(it.share)) }))
                }
            }
        }
        refresh()
    }

    /** Reloads the shares and the history; also called by pull to refresh and by "Réessayer". */
    fun refresh() {
        viewModelScope.launch {
            state.update { it.copy(shares = it.shares.refreshing()) }
            launch { getAnalysisHistory.refresh() }
            val outcome = getShares()
            state.update { current ->
                val items = when (outcome) {
                    is Outcome.Success -> Outcome.Success(outcome.value.map { ShareItem(it, summaryOf(it)) })
                    is Outcome.Failure -> outcome
                }
                current.copy(shares = current.shares.reduce(items))
            }
        }
    }

    /**
     * Asks for the confirmation before deleting a share.
     *
     * @param item the share to delete.
     */
    fun askToDelete(item: ShareItem) {
        state.update { it.copy(shareToDelete = item) }
    }

    /** Closes the confirmation without deleting. */
    fun cancelDelete() {
        state.update { it.copy(shareToDelete = null) }
    }

    /** Deletes the share waiting for confirmation. */
    fun confirmDelete() {
        val item = state.value.shareToDelete ?: return
        state.update { it.copy(shareToDelete = null) }
        viewModelScope.launch {
            when (val outcome = deleteShare(item.share.id)) {
                is Outcome.Success -> {
                    state.update { current ->
                        val remaining = current.shares.content.orEmpty().filterNot { it.share.id == item.share.id }
                        current.copy(shares = current.shares.copy(content = remaining))
                    }
                    eventChannel.send(SharesEvent.Deleted)
                }
                is Outcome.Failure -> eventChannel.send(SharesEvent.Failed(outcome.error))
            }
        }
    }

    private fun summaryOf(share: Share): AnalysisSummary? = history.firstOrNull { it.id == share.analysisId }
}
