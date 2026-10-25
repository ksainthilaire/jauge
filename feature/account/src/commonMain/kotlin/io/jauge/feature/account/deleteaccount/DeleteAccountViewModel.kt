package io.jauge.feature.account.deleteaccount

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.jauge.core.domain.error.JaugeError
import io.jauge.core.domain.model.Outcome
import io.jauge.core.domain.usecase.DeleteAccountUseCase
import io.jauge.core.domain.usecase.GetSubscriptionManagementUrlUseCase
import io.jauge.core.domain.usecase.SignOutUseCase
import io.jauge.core.domain.usecase.UpdateSettingsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State of the account deletion screen.
 *
 * @property isConfirmed true once "Je comprends que la suppression est définitive" is ticked.
 * @property isDeleting true while the deletion runs.
 * @property error friendly error, null when none.
 * @property isDeleted true once the account is deleted: the app goes back to the welcome screen.
 * @property managementUrl store page to open, consumed by [DeleteAccountViewModel.onUrlOpened].
 */
data class DeleteAccountUiState(
    val isConfirmed: Boolean = false,
    val isDeleting: Boolean = false,
    val error: JaugeError? = null,
    val isDeleted: Boolean = false,
    val managementUrl: String? = null,
)

/**
 * View model of the account deletion, required by the App Store and Google Play: deletes the account and its data on
 * the server, signs out and forgets the preferences of the device. Accounts signed in with Apple see the Apple sheet
 * once more on iOS, so that the server can revoke the Apple tokens ([DeleteAccountUseCase]).
 *
 * @param deleteAccount deletes the account on the server.
 * @param signOut signs out on this device.
 * @param updateSettings resets the device preferences.
 * @param getManagementUrl store page of the subscription.
 */
class DeleteAccountViewModel(
    private val deleteAccount: DeleteAccountUseCase,
    private val signOut: SignOutUseCase,
    private val updateSettings: UpdateSettingsUseCase,
    private val getManagementUrl: GetSubscriptionManagementUrlUseCase,
) : ViewModel() {
    private val state = MutableStateFlow(DeleteAccountUiState())

    /** Observed state of the screen. */
    val uiState: StateFlow<DeleteAccountUiState> = state.asStateFlow()

    /**
     * Ticks or unticks the confirmation.
     *
     * @param confirmed new value.
     */
    fun setConfirmed(confirmed: Boolean) {
        state.update { it.copy(isConfirmed = confirmed, error = null) }
    }

    /** "Supprimer définitivement", only once confirmed. */
    fun delete() {
        val current = state.value
        if (!current.isConfirmed || current.isDeleting) return
        viewModelScope.launch {
            state.update { it.copy(isDeleting = true, error = null) }
            when (val outcome = deleteAccount()) {
                is Outcome.Success -> {
                    signOut()
                    updateSettings.resetAll()
                    state.update { it.copy(isDeleting = false, isDeleted = true) }
                }
                // Closing the Apple sheet (accounts signed in with Apple) keeps the account: nothing to say.
                is Outcome.Failure -> state.update {
                    it.copy(isDeleting = false, error = outcome.error.takeUnless { error -> error == JaugeError.SignInCancelled })
                }
            }
        }
    }

    /** "Gérer mon abonnement". */
    fun manageSubscription() {
        viewModelScope.launch {
            when (val outcome = getManagementUrl()) {
                is Outcome.Success -> state.update { it.copy(managementUrl = outcome.value) }
                is Outcome.Failure -> state.update { it.copy(error = outcome.error) }
            }
        }
    }

    /** The store page was opened. */
    fun onUrlOpened() {
        state.update { it.copy(managementUrl = null) }
    }
}
