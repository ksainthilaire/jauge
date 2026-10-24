package io.jauge.core.billing

import io.jauge.core.common.configuration.ApiModeController
import io.jauge.core.common.configuration.AppEnvironment
import org.koin.dsl.module
import kotlin.time.Clock

/**
 * Chooses the store client for each call:
 * - no RevenueCat key: [UnavailableBillingClient] (purchases disabled cleanly);
 * - key present and offline mode on: [SimulatedBillingClient];
 * - key present and real API: the RevenueCat client, wired in a later phase ([UnavailableBillingClient] until then).
 *
 * @param environment app environment.
 * @param apiModeController current API mode.
 * @param simulated client of the offline mode.
 * @param store real store client.
 */
class SwitchingBillingClient(
    private val environment: AppEnvironment,
    private val apiModeController: ApiModeController,
    private val simulated: BillingClient,
    private val store: BillingClient,
) : BillingClient {

    private val current: BillingClient
        get() = when {
            !environment.isBillingConfigured -> UnavailableBillingClient()
            apiModeController.isOfflineMode.value -> simulated
            else -> store
        }

    override val isAvailable: Boolean get() = current.isAvailable

    override suspend fun getOffer(): BillingOffer = current.getOffer()

    override suspend fun purchase(productId: String): BillingCustomerInfo = current.purchase(productId)

    override suspend fun restore(): BillingCustomerInfo = current.restore()

    override suspend fun customerInfo(): BillingCustomerInfo = current.customerInfo()
}

/** Koin module of `core:billing`. */
val billingModule = module {
    single<BillingClient> {
        SwitchingBillingClient(
            environment = get(),
            apiModeController = get(),
            simulated = SimulatedBillingClient { Clock.System.now().toEpochMilliseconds() },
            store = UnavailableBillingClient(),
        )
    }
}
