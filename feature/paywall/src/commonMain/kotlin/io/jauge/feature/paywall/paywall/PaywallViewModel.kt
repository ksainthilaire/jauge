package io.jauge.feature.paywall.paywall

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.jauge.core.common.platform.Platform
import io.jauge.core.domain.error.JaugeError
import io.jauge.core.domain.model.Outcome
import io.jauge.core.domain.model.Subscription
import io.jauge.core.domain.model.SubscriptionOffer
import io.jauge.core.domain.model.TrialTimeline
import io.jauge.core.domain.model.getOrNull
import io.jauge.core.domain.usecase.ComputeTrialTimelineUseCase
import io.jauge.core.domain.usecase.GetSubscriptionManagementUrlUseCase
import io.jauge.core.domain.usecase.GetSubscriptionOfferUseCase
import io.jauge.core.domain.usecase.GetSubscriptionUseCase
import io.jauge.core.domain.usecase.PurchaseSubscriptionUseCase
import io.jauge.core.domain.usecase.RestorePurchasesUseCase
import io.jauge.core.ui.error.isSilent
import io.jauge.core.ui.state.ContentState
import io.jauge.core.ui.state.reduce
import io.jauge.core.ui.state.refreshing
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How the paywall ended with Jauge Plus unlocked. */
enum class PaywallCompletion {
    /** The trial or the subscription just started. */
    PURCHASED,

    /** A previous purchase was restored. */
    RESTORED,
}

/**
 * Observed state of the paywall (mockup 08, single monthly offer).
 *
 * @property offer the monthly offer with its store price, loaded from the store.
 * @property timeline trial timeline (today, reminder, first payment), null when the user gets no trial.
 * @property subscription current subscription, null until known.
 * @property platform store of the phone, for the legal text.
 * @property isPurchasing true while the store purchase sheet is open.
 * @property isRestoring true while purchases are restored.
 * @property dialogError error to show in a dialog, null when there is none.
 * @property completion set when Jauge Plus was just unlocked: the success screen replaces the offer.
 * @property managementUrl store page to open, consumed by the screen.
 */
data class PaywallUiState(
    val offer: ContentState<SubscriptionOffer> = ContentState(),
    val timeline: TrialTimeline? = null,
    val subscription: Subscription? = null,
    val platform: Platform = Platform.ANDROID,
    val isPurchasing: Boolean = false,
    val isRestoring: Boolean = false,
    val dialogError: JaugeError? = null,
    val completion: PaywallCompletion? = null,
    val managementUrl: String? = null,
) {
    /** True when the user already has Jauge Plus (trial or paid): the paywall offers to manage it instead. */
    val isAlreadyPlus: Boolean
        get() = completion == null && subscription?.isPlus == true

    /** True when the main button can start a purchase. */
    val canPurchase: Boolean
        get() = offer.content != null && !isPurchasing && !isRestoring
}

/**
 * State holder of the paywall: loads the offer and the subscription, buys, restores, opens the store management page.
 * Every failure becomes a friendly dialog; a cancelled purchase is silent.
 *
 * @param platform store of the phone.
 * @param getOffer loads the monthly offer.
 * @param getSubscription loads the current subscription.
 * @param purchaseSubscription starts the trial.
 * @param restorePurchases restores previous purchases.
 * @param getManagementUrl store page of the subscription.
 * @param computeTrialTimeline trial dates from today.
 */
class PaywallViewModel(
    platform: Platform,
    private val getOffer: GetSubscriptionOfferUseCase,
    private val getSubscription: GetSubscriptionUseCase,
    private val purchaseSubscription: PurchaseSubscriptionUseCase,
    private val restorePurchases: RestorePurchasesUseCase,
    private val getManagementUrl: GetSubscriptionManagementUrlUseCase,
    private val computeTrialTimeline: ComputeTrialTimelineUseCase,
) : ViewModel() {

    private val state = MutableStateFlow(PaywallUiState(platform = platform))

    /** Observed state of the screen. */
    val uiState: StateFlow<PaywallUiState> = state.asStateFlow()

    init {
        refresh()
    }

    /** Loads the offer and the subscription; also called by pull to refresh and "Réessayer". */
    fun refresh() {
        viewModelScope.launch {
            state.update { it.copy(offer = it.offer.refreshing()) }
            val subscription = async { getSubscription() }
            val offer = getOffer()
            state.update { current ->
                val loadedOffer = current.offer.reduce(offer)
                current.copy(
                    offer = loadedOffer,
                    timeline = loadedOffer.content?.takeIf { it.trialDays > 0 }?.let { computeTrialTimeline(it.trialDays) },
                    subscription = subscription.await().getOrNull() ?: current.subscription,
                )
            }
        }
    }

    /** Starts the trial (or the subscription when the user gets no trial). */
    fun purchase() {
        val offer = state.value.offer.content ?: return
        if (!state.value.canPurchase) return
        viewModelScope.launch {
            state.update { it.copy(isPurchasing = true) }
            val outcome = purchaseSubscription(offer)
            state.update { it.afterStoreCall(outcome, PaywallCompletion.PURCHASED, notPlusError = JaugeError.PurchasePending) }
        }
    }

    /** Restores previous purchases ("Restaurer les achats"). */
    fun restore() {
        if (state.value.isRestoring || state.value.isPurchasing) return
        viewModelScope.launch {
            state.update { it.copy(isRestoring = true, dialogError = null) }
            val outcome = restorePurchases()
            state.update { it.afterStoreCall(outcome, PaywallCompletion.RESTORED, notPlusError = JaugeError.NothingToRestore) }
        }
    }

    /** Asks for the store page of the subscription ("Gérer mon abonnement"). */
    fun manageSubscription() {
        viewModelScope.launch {
            when (val outcome = getManagementUrl()) {
                is Outcome.Success -> state.update { it.copy(managementUrl = outcome.value) }
                is Outcome.Failure -> state.update { it.copy(dialogError = outcome.error) }
            }
        }
    }

    /** The screen opened [PaywallUiState.managementUrl]. */
    fun onManagementUrlOpened() {
        state.update { it.copy(managementUrl = null) }
    }

    /** Closes the error dialog. */
    fun dismissError() {
        state.update { it.copy(dialogError = null) }
    }

    private fun PaywallUiState.afterStoreCall(
        outcome: Outcome<Subscription>,
        completion: PaywallCompletion,
        notPlusError: JaugeError,
    ): PaywallUiState {
        val idle = copy(isPurchasing = false, isRestoring = false)
        return when (outcome) {
            is Outcome.Success -> if (outcome.value.isPlus) {
                idle.copy(subscription = outcome.value, completion = completion, dialogError = null)
            } else {
                idle.copy(subscription = outcome.value, dialogError = notPlusError)
            }
            is Outcome.Failure -> idle.copy(dialogError = outcome.error.takeUnless { it.isSilent })
        }
    }
}
