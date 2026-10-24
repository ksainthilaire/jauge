package io.jauge.core.billing

/**
 * Store purchases of Jauge Plus. The RevenueCat implementation (purchases-kmp, public SDK keys only) plugs in here;
 * until then the app uses [UnavailableBillingClient] or, in offline mode, [SimulatedBillingClient].
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
}

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
 */
data class BillingCustomerInfo(
    val isPlusActive: Boolean,
    val isInTrial: Boolean,
    val expiresAtEpochMilliseconds: Long?,
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

    /** Any other store problem. */
    class Store : BillingFailure("Store problem")
}
