package io.jauge.core.billing

import io.jauge.core.common.platform.Platform
import kotlinx.coroutines.delay

/**
 * In memory store for tests and previews: the trial starts at once, nothing is charged.
 *
 * @param latencyMilliseconds simulated store delay.
 * @param nowEpochMilliseconds source of the current time.
 */
class SimulatedBillingClient(
    private val latencyMilliseconds: Long = 600,
    private val nowEpochMilliseconds: () -> Long,
) : BillingClient {

    private var customer = BillingCustomerInfo(isPlusActive = false, isInTrial = false, expiresAtEpochMilliseconds = null)

    /** When true, the next purchase behaves as if the user closed the sheet. */
    var cancelNextPurchase: Boolean = false

    override val isAvailable: Boolean = true

    override suspend fun getOffer(): BillingOffer {
        delay(latencyMilliseconds)
        return BillingOffer(productId = MONTHLY_PRODUCT_ID, formattedPrice = "6,99 €", trialDays = TRIAL_DAYS)
    }

    override suspend fun purchase(productId: String): BillingCustomerInfo {
        delay(latencyMilliseconds)
        if (cancelNextPurchase) {
            cancelNextPurchase = false
            throw BillingFailure.Cancelled()
        }
        customer = BillingCustomerInfo(
            isPlusActive = true,
            isInTrial = true,
            expiresAtEpochMilliseconds = nowEpochMilliseconds() + TRIAL_DAYS * MILLISECONDS_PER_DAY,
        )
        return customer
    }

    override suspend fun restore(): BillingCustomerInfo {
        delay(latencyMilliseconds)
        return customer
    }

    override suspend fun customerInfo(): BillingCustomerInfo = customer

    override suspend fun managementUrl(): String = storeSubscriptionsUrl(Platform.ANDROID, MONTHLY_PRODUCT_ID)

    private companion object {
        const val MONTHLY_PRODUCT_ID = "jauge_plus_monthly"
        const val TRIAL_DAYS = 3
        const val MILLISECONDS_PER_DAY = 86_400_000L
    }
}
