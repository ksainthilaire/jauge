package io.jauge.feature.account.email

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.jauge.core.domain.error.JaugeError
import io.jauge.core.domain.model.AuthenticationState
import io.jauge.core.domain.model.Consents
import io.jauge.core.domain.model.Outcome
import io.jauge.core.domain.usecase.CompleteOnboardingStepUseCase
import io.jauge.core.domain.usecase.ObserveAuthenticationStateUseCase
import io.jauge.core.domain.usecase.SendMagicLinkUseCase
import io.jauge.core.domain.usecase.UpdateConsentsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Steps of the email sign in. */
enum class EmailSignInStep {
    /** The user types the address. */
    ADDRESS,

    /** The link was sent: the user opens it from the mail app. */
    LINK_SENT,
}

/**
 * State of the email sign in screen.
 *
 * @property step current step.
 * @property email typed address.
 * @property isAddressInvalid true when the address does not look like an email address.
 * @property isLoading true while a request runs.
 * @property linkSentAgain true right after "Renvoyer le lien" succeeded.
 * @property error friendly error to show, null when none.
 * @property isDone true once signed in: the screen leaves.
 */
data class EmailSignInUiState(
    val step: EmailSignInStep = EmailSignInStep.ADDRESS,
    val email: String = "",
    val isAddressInvalid: Boolean = false,
    val isLoading: Boolean = false,
    val linkSentAgain: Boolean = false,
    val error: JaugeError? = null,
    val isDone: Boolean = false,
)

/**
 * View model of the email sign in (mockup 11, "Continuer avec un e-mail"): sends a sign in link, then waits for the
 * authentication state to become signed in when the user opens the link.
 *
 * @param observeAuthenticationState authentication state.
 * @param sendMagicLink sends the sign in link.
 * @param updateConsents saves the optional consent once signed in.
 * @param completeOnboardingStep records that the account step is done.
 * @param marketingEmails consent ticked on the account screen.
 */
class EmailSignInViewModel(
    observeAuthenticationState: ObserveAuthenticationStateUseCase,
    private val sendMagicLink: SendMagicLinkUseCase,
    private val updateConsents: UpdateConsentsUseCase,
    private val completeOnboardingStep: CompleteOnboardingStepUseCase,
    private val marketingEmails: Boolean,
) : ViewModel() {
    private val state = MutableStateFlow(EmailSignInUiState())

    /** Observed state of the screen. */
    val uiState: StateFlow<EmailSignInUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            observeAuthenticationState().collect { authentication ->
                if (state.value.step == EmailSignInStep.LINK_SENT && !state.value.isDone && authentication is AuthenticationState.SignedIn) {
                    if (marketingEmails) updateConsents(Consents(marketingEmails = true))
                    completeOnboardingStep.passAccount()
                    state.update { it.copy(isDone = true) }
                }
            }
        }
    }

    /**
     * The address changed.
     *
     * @param email new text.
     */
    fun onEmailChange(email: String) {
        state.update { it.copy(email = email.take(MAXIMUM_EMAIL_LENGTH), isAddressInvalid = false, error = null) }
    }

    /** "Recevoir un lien". */
    fun sendLink() {
        val current = state.value
        if (current.isLoading) return
        if (!SendMagicLinkUseCase.isValidEmail(current.email.trim())) {
            state.update { it.copy(isAddressInvalid = true) }
            return
        }
        send(again = false)
    }

    /** "Renvoyer le lien". */
    fun resendLink() {
        if (!state.value.isLoading) send(again = true)
    }

    /** "Changer d'adresse": back to the first step. */
    fun changeAddress() {
        state.update { it.copy(step = EmailSignInStep.ADDRESS, isAddressInvalid = false, error = null, linkSentAgain = false) }
    }

    private fun send(again: Boolean) {
        viewModelScope.launch {
            state.update { it.copy(isLoading = true, error = null, linkSentAgain = false) }
            when (val outcome = sendMagicLink(state.value.email)) {
                is Outcome.Success -> state.update {
                    it.copy(isLoading = false, step = EmailSignInStep.LINK_SENT, linkSentAgain = again)
                }
                is Outcome.Failure -> state.update {
                    it.copy(isLoading = false, error = outcome.error, isAddressInvalid = outcome.error == JaugeError.InvalidEmail)
                }
            }
        }
    }

    private companion object {
        const val MAXIMUM_EMAIL_LENGTH = 254
    }
}
