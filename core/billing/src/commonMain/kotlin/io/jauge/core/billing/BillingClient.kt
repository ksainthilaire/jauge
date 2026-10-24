package io.jauge.core.billing

import io.jauge.core.common.platform.Platform

/**
 * Store purchases of Jauge Plus. [RevenueCatBillingClient] (purchases-kmp, public SDK keys only) when a RevenueCat key
 * is configured, [UnavailableBillingClient] otherwise; [SimulatedBillingClient] serves tests and previews.
 */
interface BillingClient {
    /** True when purchases can be made. */
    val isAvailable: Boolean

    /**
     * The monthly offer with its store price. Throws [BillingFailure] on failure.
     */
    suspend fun getOffer(): BillingOffer

    /**
     * Buys a product. Throws [BillingFailure.Cancelled] when the user cancels.
     *
     * @param productId store product identifier.
     */
    suspend fun purchase(productId: String): BillingCustomerInfo

    /** Restores previous purchases. Throws [BillingFailure] on failure. */
    suspend fun restore(): BillingCustomerInfo

    /** Current entitlements. Throws [BillingFailure] on failure. */
    suspend fun customerInfo(): BillingCustomerInfo

    /**
     * Links the purchases to the signed in Jauge account (RevenueCat app user id), or back to an anonymous user
     * after sign out, so that Jauge Plus follows the account across devices.
     *
     * @param appUserId the Supabase user id, null when signed out.
     */
    suspend fun identify(appUserId: String?) {}

    /**
     * Store page where the user manages or cancels the subscription. Throws [BillingFailure.Unavailable] when
     * purchases are disabled.
     */
    suspend fun managementUrl(): String = throw BillingFailure.Unavailable()
}

/**
 * Subscriptions page of the store of the phone, used when the store gives no page for the subscription itself.
 *
 * @param platform the running platform.
 * @param productId store product of Jauge Plus, to open its page directly on Google Play.
 */
fun storeSubscriptionsUrl(platform: Platform, productId: String?): String = when (platform) {
    Platform.ANDROID -> buildString {
        append("https://play.google.com/store/account/subscriptions?package=")
        append(ANDROID_APPLICATION_ID)
        productId?.substringBefore(':')?.takeIf { it.isNotBlank() }?.let { append("&sku=").append(it) }
    }
    Platform.IOS -> "https://apps.apple.com/account/subscriptions"
}

/** Application id of the Android app (`androidApp/build.gradle.kts`), used in Google Play links. */
const val ANDROID_APPLICATION_ID = "io.jauge.app"

/**
 * An offer of the store.
 *
 * @property productId store product identifier.
 * @property formattedPrice price formatted by the store ("6,99 €").
 * @property trialDays free trial length.
 */
data class BillingOffer(
    val productId: String,
    val formattedPrice: String,
    val trialDays: Int,
)

/**
 * Entitlements of the user, as known by the store.
 *
 * @property isPlusActive true when the `plus` entitlement is active.
 * @property isInTrial true during the free trial.
 * @property expiresAtEpochMilliseconds end of the current period, if any.
 * @property willRenew false when the user cancelled: Jauge Plus stops at [expiresAtEpochMilliseconds].
 * @property managementUrl store page where the user manages or cancels the subscription, if known.
 */
data class BillingCustomerInfo(
    val isPlusActive: Boolean,
    val isInTrial: Boolean,
    val expiresAtEpochMilliseconds: Long?,
    val willRenew: Boolean = true,
    val managementUrl: String? = null,
)

/**
 * Failures of the store. Mapped to friendly messages by the data layer.
 *
 * @param description developer description.
 */
sealed class BillingFailure(description: String) : Exception(description) {
    /** The user closed the purchase sheet. */
    class Cancelled : BillingFailure("Purchase cancelled")

    /** Purchases are not configured or not allowed on this device. */
    class Unavailable : BillingFailure("Billing unavailable")

    /** The payment waits for an approval. */
    class Pending : BillingFailure("Payment pending")

    /** The store could not be reached. */
    class Network : BillingFailure("Store unreachable")

    /** The subscription is already active on this store account. */
    class AlreadyOwned : BillingFailure("Product already owned")

    /** The store receipt belongs to another app user. */
    class OwnedByAnotherAccount : BillingFailure("Receipt used by another user")

    /** The device or the store account cannot buy (parental control, restricted account). */
    class NotAllowed : BillingFailure("Purchase not allowed")

    /** Any other store problem: payment declined, store error. Nothing was charged. */
    class Store : BillingFailure("Store problem")
}
