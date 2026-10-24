package io.jauge.core.billing

import io.jauge.core.common.configuration.AppEnvironment
import org.koin.dsl.module

/**
 * Chooses the store client for each call:
 * - no RevenueCat key: [UnavailableBillingClient] (purchases disabled cleanly);
 * - key present: [store], the RevenueCat client. Debug builds use the RevenueCat Test Store key, so purchases made
 *   while the offline mode is on are test purchases that never charge anyone.
 *
 * @param environment app environment.
 * @param store real store client.
 */
class SwitchingBillingClient(
    private val environment: AppEnvironment,
    private val store: BillingClient,
) : BillingClient {

    private val unavailable = UnavailableBillingClient()

    private val current: BillingClient
        get() = if (environment.isBillingConfigured) store else unavailable

    override val isAvailable: Boolean get() = current.isAvailable

    override suspend fun getOffer(): BillingOffer = current.getOffer()

    override suspend fun purchase(productId: String): BillingCustomerInfo = current.purchase(productId)

    override suspend fun restore(): BillingCustomerInfo = current.restore()

    override suspend fun customerInfo(): BillingCustomerInfo = current.customerInfo()

    override suspend fun identify(appUserId: String?) = current.identify(appUserId)

    override suspend fun managementUrl(): String = current.managementUrl()
}

/** Koin module of `core:billing`. */
val billingModule = module {
    single<BillingClient> {
        val environment: AppEnvironment = get()
        SwitchingBillingClient(
            environment = environment,
            store = RevenueCatBillingClient(
                apiKey = environment.revenueCatApiKey,
                platform = environment.platform,
                isDebugBuild = environment.isDebugBuild,
            ),
        )
    }
}
