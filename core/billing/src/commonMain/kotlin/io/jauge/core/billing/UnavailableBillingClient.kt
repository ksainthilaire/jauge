package io.jauge.core.billing

/** Billing used when no RevenueCat key is configured: purchases are disabled cleanly. */
class UnavailableBillingClient : BillingClient {
    override val isAvailable: Boolean = false

    override suspend fun getOffer(): BillingOffer = throw BillingFailure.Unavailable()

    override suspend fun purchase(productId: String): BillingCustomerInfo = throw BillingFailure.Unavailable()

    override suspend fun restore(): BillingCustomerInfo = throw BillingFailure.Unavailable()

    override suspend fun customerInfo(): BillingCustomerInfo = BillingCustomerInfo(isPlusActive = false, isInTrial = false, expiresAtEpochMilliseconds = null)
}
