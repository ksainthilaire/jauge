package io.jauge.feature.account.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.jauge.core.domain.error.JaugeError
import io.jauge.core.domain.model.Account
import io.jauge.core.domain.model.AnalysisSummary
import io.jauge.core.domain.model.AuthenticatedUser
import io.jauge.core.domain.model.AuthenticationState
import io.jauge.core.domain.model.Outcome
import io.jauge.core.domain.model.Subscription
import io.jauge.core.domain.model.UserPreferences
import io.jauge.core.domain.model.getOrNull
import io.jauge.core.domain.usecase.GetAccountUseCase
import io.jauge.core.domain.usecase.GetAnalysisHistoryUseCase
import io.jauge.core.domain.usecase.GetSubscriptionManagementUrlUseCase
import io.jauge.core.domain.usecase.GetSubscriptionOfferUseCase
import io.jauge.core.domain.usecase.GetSubscriptionUseCase
import io.jauge.core.domain.usecase.ObserveAuthenticationStateUseCase
import io.jauge.core.domain.usecase.ObservePreferencesUseCase
import io.jauge.core.domain.usecase.RestorePurchasesUseCase
import io.jauge.core.domain.usecase.SignOutUseCase
import io.jauge.core.ui.error.isSilent
import io.jauge.core.ui.state.ContentState
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What the profile shows. A part that could not be loaded is null and shown as an empty field.
 *
 * @property account the account, null when it could not be loaded.
 * @property subscription the subscription, null when it could not be loaded.
 * @property formattedPrice monthly price given by the store ("6,99 €"), null when unknown.
 * @property latestAnalysisId newest analysis, for "Comment on estime", null without analysis.
 */
data class ProfileContent(
    val account: Account?,
    val subscription: Subscription?,
    val formattedPrice: String?,
    val latestAnalysisId: String?,
)

/** Short messages shown at the bottom of the profile. */
enum class ProfileMessage {
    /** "Restaurer les achats" found an active Jauge Plus subscription. */
    PURCHASES_RESTORED,

    /** The user signed out. */
    SIGNED_OUT,

    /** There is no analysis to export. */
    NOTHING_TO_EXPORT,
}

/**
 * State of the profile screen (mockup 13).
 *
 * @property content loaded content.
 * @property user signed in user, null without account.
 * @property preferences device preferences (alerts, search area).
 * @property isRestoring true while purchases are restored.
 * @property message short confirmation to show, null when none.
 * @property actionError friendly error of an action (restore, manage, sign out), null when none.
 * @property managementUrl store page to open, consumed by [ProfileViewModel.onUrlOpened].
 * @property exportedAnalyses analyses to share as a file, consumed by [ProfileViewModel.onExportShared].
 */
data class ProfileUiState(
    val content: ContentState<ProfileContent> = ContentState(),
    val user: AuthenticatedUser? = null,
    val preferences: UserPreferences = UserPreferences(),
    val isRestoring: Boolean = false,
    val message: ProfileMessage? = null,
    val actionError: JaugeError? = null,
    val managementUrl: String? = null,
    val exportedAnalyses: List<AnalysisSummary>? = null,
)

/**
 * View model of the profile and subscription screen (mockup 13).
 *
 * @param observeAuthenticationState authentication state.
 * @param observePreferences device preferences.
 * @param getAccount the account.
 * @param getSubscription the subscription.
 * @param getOffer the monthly offer with its store price.
 * @param getHistory analyses of the user.
 * @param restorePurchases "Restaurer les achats".
 * @param getManagementUrl store page of the subscription.
 * @param signOut "Se déconnecter".
 */
class ProfileViewModel(
    observeAuthenticationState: ObserveAuthenticationStateUseCase,
    observePreferences: ObservePreferencesUseCase,
    private val getAccount: GetAccountUseCase,
    private val getSubscription: GetSubscriptionUseCase,
    private val getOffer: GetSubscriptionOfferUseCase,
    private val getHistory: GetAnalysisHistoryUseCase,
    private val restorePurchases: RestorePurchasesUseCase,
    private val getManagementUrl: GetSubscriptionManagementUrlUseCase,
    private val signOut: SignOutUseCase,
) : ViewModel() {
    private val state = MutableStateFlow(ProfileUiState())

    /** Observed state of the screen. */
    val uiState: StateFlow<ProfileUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            observeAuthenticationState().collect { authentication ->
                state.update { it.copy(user = (authentication as? AuthenticationState.SignedIn)?.user) }
            }
        }
        viewModelScope.launch {
            observePreferences().collect { preferences -> state.update { it.copy(preferences = preferences) } }
        }
        refresh()
    }

    /** Loads the account, the subscription and the offer (first load, pull to refresh, "Réessayer"). */
    fun refresh() {
        viewModelScope.launch {
            state.update {
                val content = it.content
                it.copy(content = content.copy(isLoading = content.content == null, isRefreshing = content.content != null, error = null))
            }
            val account = async { getAccount() }
            val subscription = async { getSubscription() }
            val offer = async { getOffer() }
            val history = async { getHistory.refresh() }
            val accountOutcome = account.await()
            val subscriptionOutcome = subscription.await()
            history.await()
            val latest = runCatching { getHistory().first().firstOrNull()?.id }.getOrNull()
            val loaded = ProfileContent(
                account = accountOutcome.getOrNull(),
                subscription = subscriptionOutcome.getOrNull(),
                formattedPrice = offer.await().getOrNull()?.formattedPrice,
                latestAnalysisId = latest,
            )
            val error = (accountOutcome as? Outcome.Failure)?.error ?: (subscriptionOutcome as? Outcome.Failure)?.error
            val nothingLoaded = loaded.account == null && loaded.subscription == null
            state.update {
                it.copy(
                    content = ContentState(
                        content = if (nothingLoaded) it.content.content else loaded,
                        isLoading = false,
                        isRefreshing = false,
                        error = error,
                    ),
                )
            }
        }
    }

    /**
     * "Restaurer les achats", like the paywall: a success message only when an active Jauge Plus entitlement
     * comes back, [JaugeError.NothingToRestore] when the store has nothing, friendly errors otherwise.
     */
    fun restore() {
        if (state.value.isRestoring) return
        viewModelScope.launch {
            state.update { it.copy(isRestoring = true, actionError = null) }
            when (val outcome = restorePurchases()) {
                is Outcome.Success -> state.update { current ->
                    val content = current.content.content?.copy(subscription = outcome.value)
                    val isRestored = outcome.value.isPlus
                    current.copy(
                        isRestoring = false,
                        message = if (isRestored) ProfileMessage.PURCHASES_RESTORED else null,
                        actionError = if (isRestored) null else JaugeError.NothingToRestore,
                        content = current.content.copy(content = content ?: current.content.content),
                    )
                }
                is Outcome.Failure -> state.update {
                    it.copy(isRestoring = false, actionError = outcome.error.takeUnless { error -> error.isSilent })
                }
            }
        }
    }

    /** "Gérer ou annuler l'abonnement": asks the store page to open. */
    fun manageSubscription() {
        viewModelScope.launch {
            when (val outcome = getManagementUrl()) {
                is Outcome.Success -> state.update { it.copy(managementUrl = outcome.value) }
                is Outcome.Failure -> state.update { it.copy(actionError = outcome.error) }
            }
        }
    }

    /** The store page was opened. */
    fun onUrlOpened() {
        state.update { it.copy(managementUrl = null) }
    }

    /** "Exporter mes analyses". */
    fun export() {
        viewModelScope.launch {
            val analyses = runCatching { getHistory().first() }.getOrDefault(emptyList())
            state.update {
                if (analyses.isEmpty()) it.copy(message = ProfileMessage.NOTHING_TO_EXPORT) else it.copy(exportedAnalyses = analyses)
            }
        }
    }

    /** The export was handed to the share sheet. */
    fun onExportShared() {
        state.update { it.copy(exportedAnalyses = null) }
    }

    /** "Se déconnecter", confirmed. */
    fun confirmSignOut() {
        viewModelScope.launch {
            when (val outcome = signOut()) {
                is Outcome.Success -> state.update { it.copy(message = ProfileMessage.SIGNED_OUT) }
                is Outcome.Failure -> state.update { it.copy(actionError = outcome.error) }
            }
        }
    }

    /** The message or the error was shown. */
    fun dismissMessage() {
        state.update { it.copy(message = null, actionError = null) }
    }
}
