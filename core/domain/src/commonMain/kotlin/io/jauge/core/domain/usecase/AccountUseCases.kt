package io.jauge.core.domain.usecase

import io.jauge.core.domain.error.JaugeError
import io.jauge.core.domain.model.Account
import io.jauge.core.domain.model.AuthenticationState
import io.jauge.core.domain.model.SignInMethod
import io.jauge.core.domain.model.Consents
import io.jauge.core.domain.model.Outcome
import io.jauge.core.domain.model.Subscription
import io.jauge.core.domain.model.SubscriptionOffer
import io.jauge.core.domain.model.TrialTimeline
import io.jauge.core.domain.repository.AccountRepository
import io.jauge.core.domain.repository.AuthenticationRepository
import io.jauge.core.domain.service.AppleAuthorization
import io.jauge.core.domain.repository.SubscriptionRepository
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/**
 * The current account (mockup 13).
 *
 * @param repository account repository.
 */
class GetAccountUseCase(private val repository: AccountRepository) {
    /** Loads the account. */
    suspend operator fun invoke(): Outcome<Account> = repository.getAccount()
}

/**
 * Updates the consents (mockup 11).
 *
 * @param repository account repository.
 */
class UpdateConsentsUseCase(private val repository: AccountRepository) {
    /**
     * Saves the consents.
     *
     * @param consents new consents.
     */
    suspend operator fun invoke(consents: Consents): Outcome<Account> = repository.updateConsents(consents)
}

/**
 * Deletes the account and its data (required by the stores).
 *
 * For an account signed in with Apple, a fresh authorization code is asked first ([appleAuthorization]) and sent
 * with the deletion so that the server revokes the Apple tokens. If the user closes the Apple sheet, nothing is
 * deleted ([JaugeError.SignInCancelled], shown silently). If Apple cannot answer, the deletion goes on without the
 * code: deleting the account always matters more.
 *
 * @param repository account repository.
 * @param authenticationRepository tells how the user signed in.
 * @param appleAuthorization asks Apple for the code.
 */
class DeleteAccountUseCase(
    private val repository: AccountRepository,
    private val authenticationRepository: AuthenticationRepository,
    private val appleAuthorization: AppleAuthorization,
) {
    /** Deletes the account. */
    suspend operator fun invoke(): Outcome<Unit> {
        val user = (authenticationRepository.state.value as? AuthenticationState.SignedIn)?.user
        val appleCode = if (user?.signInMethod == SignInMethod.APPLE) {
            when (val outcome = appleAuthorization.requestAuthorizationCode()) {
                is Outcome.Success -> outcome.value?.takeIf { it.isNotBlank() }
                is Outcome.Failure -> if (outcome.error == JaugeError.SignInCancelled) return outcome else null
            }
        } else {
            null
        }
        return repository.deleteAccount(appleCode)
    }
}

/**
 * Current subscription (mockup 13).
 *
 * @param repository subscription repository.
 */
class GetSubscriptionUseCase(private val repository: SubscriptionRepository) {
    /** Loads the subscription. */
    suspend operator fun invoke(): Outcome<Subscription> = repository.getSubscription()
}

/**
 * The single monthly offer of the paywall (mockup 08).
 *
 * @param repository subscription repository.
 */
class GetSubscriptionOfferUseCase(private val repository: SubscriptionRepository) {
    /** Loads the offer with its store price. */
    suspend operator fun invoke(): Outcome<SubscriptionOffer> = repository.getOffer()
}

/**
 * Starts the free trial of Jauge Plus.
 *
 * @param repository subscription repository.
 */
class PurchaseSubscriptionUseCase(private val repository: SubscriptionRepository) {
    /**
     * Buys the offer. A cancellation comes back as [JaugeError.PurchaseCancelled].
     *
     * @param offer the offer shown on the paywall.
     */
    suspend operator fun invoke(offer: SubscriptionOffer): Outcome<Subscription> =
        if (offer.productId.isBlank()) Outcome.Failure(JaugeError.PurchaseUnavailable) else repository.purchase(offer)
}

/**
 * Restores previous purchases ("Restaurer les achats").
 *
 * @param repository subscription repository.
 */
class RestorePurchasesUseCase(private val repository: SubscriptionRepository) {
    /** Restores the purchases. */
    suspend operator fun invoke(): Outcome<Subscription> = repository.restorePurchases()
}

/**
 * Store page to manage or cancel Jauge Plus ("Gérer l'abonnement").
 *
 * @param repository subscription repository.
 */
class GetSubscriptionManagementUrlUseCase(private val repository: SubscriptionRepository) {
    /** Loads the address of the page. */
    suspend operator fun invoke(): Outcome<String> = repository.getManagementUrl()
}

/**
 * Computes the trial timeline of the paywall from today's date: free today, first payment at the end of the trial
 * (today 23 Oct, payment 26 Oct for a 3 day trial).
 *
 * @param clock source of the current time.
 * @param timeZone time zone of the user.
 */
class ComputeTrialTimelineUseCase(
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    /**
     * Computes the timeline.
     *
     * @param trialDays length of the trial.
     */
    operator fun invoke(trialDays: Int = SubscriptionOffer.TRIAL_DAYS): TrialTimeline = timelineFrom(clock.todayIn(timeZone), trialDays)

    /** Pure computation, shared with tests. */
    companion object {
        /**
         * Timeline of a trial starting on [startsOn].
         *
         * @param startsOn first day of the trial.
         * @param trialDays length of the trial, at least 1.
         */
        fun timelineFrom(startsOn: LocalDate, trialDays: Int): TrialTimeline {
            val days = trialDays.coerceAtLeast(1)
            val firstPayment = startsOn.plus(DatePeriod(days = days))
            return TrialTimeline(startsOn = startsOn, firstPaymentOn = firstPayment)
        }
    }
}
