package io.jauge.feature.account.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.jauge.core.domain.error.JaugeError
import io.jauge.core.domain.model.AuthenticationProvider
import io.jauge.core.domain.model.AuthenticationState
import io.jauge.core.domain.model.Consents
import io.jauge.core.domain.model.Outcome
import io.jauge.core.domain.usecase.CompleteOnboardingStepUseCase
import io.jauge.core.domain.usecase.ObserveAuthenticationStateUseCase
import io.jauge.core.domain.usecase.SignInWithProviderUseCase
import io.jauge.core.domain.usecase.UpdateConsentsUseCase
import io.jauge.core.ui.error.isSilent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State of the account screen (mockup 11).
 *
 * @property marketingEmails state of the optional "conseils par e-mail" box.
 * @property signingInWith provider whose page is opening, null otherwise.
 * @property error friendly error to show, null when none.
 * @property isDone true once signed in or after "Continuer sans compte": the screen leaves.
 */
data class AccountUiState(
    val marketingEmails: Boolean = false,
    val signingInWith: AuthenticationProvider? = null,
    val error: JaugeError? = null,
    val isDone: Boolean = false,
) {
    /** True while a sign in starts: the buttons are disabled. */
    val isBusy: Boolean
        get() = signingInWith != null || isDone
}

/**
 * View model of the account screen (mockup 11): sign in with Apple or Google, or continue without account. Signing in
 * ends when the authentication state becomes signed in (the provider page may finish outside the app); the optional
 * consent is then saved and the account step recorded.
 *
 * @param observeAuthenticationState authentication state.
 * @param signInWithProvider opens the Apple or Google sign in.
 * @param updateConsents saves the consents of the account.
 * @param completeOnboardingStep records that the account step is done.
 */
class AccountViewModel(
    observeAuthenticationState: ObserveAuthenticationStateUseCase,
    private val signInWithProvider: SignInWithProviderUseCase,
    private val updateConsents: UpdateConsentsUseCase,
    private val completeOnboardingStep: CompleteOnboardingStepUseCase,
) : ViewModel() {
    private val state = MutableStateFlow(AccountUiState())
    private var isWaitingForSignIn = false

    /** Observed state of the screen. */
    val uiState: StateFlow<AccountUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            observeAuthenticationState().collect { authentication ->
                if (isWaitingForSignIn && authentication is AuthenticationState.SignedIn) {
                    isWaitingForSignIn = false
                    finishSignIn()
                }
            }
        }
    }

    /**
     * Ticks or unticks the optional consent.
     *
     * @param checked new value.
     */
    fun setMarketingEmails(checked: Boolean) {
        state.update { it.copy(marketingEmails = checked) }
    }

    /**
     * "Continuer avec Apple" or "Continuer avec Google".
     *
     * @param provider the provider.
     */
    fun signIn(provider: AuthenticationProvider) {
        if (state.value.isBusy) return
        isWaitingForSignIn = true
        state.update { it.copy(signingInWith = provider, error = null) }
        viewModelScope.launch {
            val outcome = signInWithProvider(provider)
            if (outcome is Outcome.Failure) {
                isWaitingForSignIn = false
                state.update { it.copy(error = outcome.error.takeUnless { error -> error.isSilent }) }
            }
            state.update { it.copy(signingInWith = null) }
        }
    }

    /** "Continuer sans compte". */
    fun continueWithoutAccount() {
        if (state.value.isBusy) return
        viewModelScope.launch {
            completeOnboardingStep.passAccount()
            state.update { it.copy(isDone = true) }
        }
    }

    /** The error was shown. */
    fun dismissError() {
        state.update { it.copy(error = null) }
    }

    private suspend fun finishSignIn() {
        if (state.value.marketingEmails) updateConsents(Consents(marketingEmails = true))
        completeOnboardingStep.passAccount()
        state.update { it.copy(signingInWith = null, isDone = true) }
    }
}
