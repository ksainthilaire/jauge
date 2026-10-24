package io.jauge.core.data.repository

import io.jauge.core.billing.BillingClient
import io.jauge.core.billing.BillingCustomerInfo
import io.jauge.core.data.error.outcomeOf
import io.jauge.core.data.mapper.toDomain
import io.jauge.core.domain.model.Outcome
import io.jauge.core.domain.model.Subscription
import io.jauge.core.domain.model.SubscriptionOffer
import io.jauge.core.domain.model.SubscriptionStatus
import io.jauge.core.network.JaugeApi
import io.jauge.core.domain.repository.SubscriptionRepository
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * [SubscriptionRepository] combining the store ([BillingClient], source of truth for Jauge Plus) and the API (free
 * quota of the week).
 *
 * @param api the API client.
 * @param billing the store client.
 * @param timeZone time zone used to show the end of the trial.
 */
class SubscriptionRepositoryImplementation(
    private val api: JaugeApi,
    private val billing: BillingClient,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : SubscriptionRepository {

    override suspend fun getSubscription(): Outcome<Subscription> = outcomeOf {
        val server = api.getSubscription().toDomain()
        val customer = runCatching { billing.customerInfo() }.getOrNull()
        merge(server, customer)
    }

    override suspend fun getOffer(): Outcome<SubscriptionOffer> = outcomeOf {
        val offer = billing.getOffer()
        SubscriptionOffer(productId = offer.productId, formattedPrice = offer.formattedPrice, trialDays = offer.trialDays)
    }

    override suspend fun purchase(offer: SubscriptionOffer): Outcome<Subscription> = outcomeOf {
        val customer = billing.purchase(offer.productId)
        merge(Subscription(SubscriptionStatus.FREE, null, null, 0), customer)
    }

    override suspend fun restorePurchases(): Outcome<Subscription> = outcomeOf {
        val customer = billing.restore()
        merge(Subscription(SubscriptionStatus.FREE, null, null, 0), customer)
    }

    private fun merge(server: Subscription, customer: BillingCustomerInfo?): Subscription {
        if (customer == null || !customer.isPlusActive) return server
        val end = customer.expiresAtEpochMilliseconds?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(timeZone).date }
        return if (customer.isInTrial) {
            server.copy(status = SubscriptionStatus.TRIAL, trialEndsOn = end, renewsOn = end)
        } else {
            server.copy(status = SubscriptionStatus.ACTIVE, trialEndsOn = null, renewsOn = end)
        }
    }
}
