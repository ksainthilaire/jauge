package io.jauge.core.domain.model

import kotlinx.datetime.LocalDate

/** How the user signed in (mockup 11). */
enum class SignInMethod {
    /** Sign in with Apple. */
    APPLE,

    /** Sign in with Google. */
    GOOGLE,

    /** Email link. */
    EMAIL,

    /** No account yet ("Continuer sans compte"). */
    NONE,
}

/**
 * Consents given by the user (mockup 11).
 *
 * @property marketingEmails true to receive search tips by email (once a month).
 */
data class Consents(val marketingEmails: Boolean)

/**
 * The user account (mockups 11 and 13).
 *
 * @property id identifier.
 * @property displayName name shown in the profile, null without account.
 * @property email email address, if known.
 * @property signInMethod how the user signed in.
 * @property analysisCount number of analyses made.
 * @property consents current consents.
 */
data class Account(
    val id: String,
    val displayName: String?,
    val email: String?,
    val signInMethod: SignInMethod,
    val analysisCount: Int,
    val consents: Consents,
) {
    /** Initials for the avatar ("IB" for "Inès Bertrand"), empty when there is no name. */
    val initials: String
        get() = displayName.orEmpty()
            .split(' ', '-')
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
}

/** State of the Jauge Plus subscription. */
enum class SubscriptionStatus {
    /** Free plan: one complete analysis per week. */
    FREE,

    /** Free trial of Jauge Plus. */
    TRIAL,

    /** Paid Jauge Plus. */
    ACTIVE,

    /** Jauge Plus ended. */
    EXPIRED,
}

/**
 * Subscription of the user (mockup 13).
 *
 * @property status current state.
 * @property trialEndsOn last day of the trial, when in trial.
 * @property renewsOn next renewal date, when active.
 * @property freeAnalysesLeftThisWeek free analyses left this week on the free plan.
 */
data class Subscription(
    val status: SubscriptionStatus,
    val trialEndsOn: LocalDate?,
    val renewsOn: LocalDate?,
    val freeAnalysesLeftThisWeek: Int,
) {
    /** True when Jauge Plus is unlocked (trial or paid). */
    val isPlus: Boolean
        get() = status == SubscriptionStatus.TRIAL || status == SubscriptionStatus.ACTIVE
}

/**
 * The single Jauge Plus offer: monthly subscription with a free trial (mockup 08, adapted).
 *
 * @property productId store product identifier.
 * @property formattedPrice price formatted by the store in the user currency ("6,99 €").
 * @property trialDays length of the free trial.
 */
data class SubscriptionOffer(
    val productId: String,
    val formattedPrice: String,
    val trialDays: Int,
) {
    /** Product identifiers. */
    companion object {
        /** Monthly Jauge Plus product. */
        const val MONTHLY_PRODUCT_ID = "jauge_plus_monthly"

        /** Length of the free trial. */
        const val TRIAL_DAYS = 3
    }
}

/**
 * Timeline of the free trial shown on the paywall: today, the reminder, the first payment.
 *
 * @property startsOn first day of the trial.
 * @property reminderOn day of the reminder (the day before the end).
 * @property firstPaymentOn day of the first payment if the user keeps Jauge Plus.
 */
data class TrialTimeline(
    val startsOn: LocalDate,
    val reminderOn: LocalDate,
    val firstPaymentOn: LocalDate,
)
