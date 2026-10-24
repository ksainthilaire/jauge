package io.jauge.feature.paywall

import io.jauge.core.common.platform.Platform
import io.jauge.core.domain.error.JaugeError
import io.jauge.core.domain.model.Outcome
import io.jauge.core.domain.model.Subscription
import io.jauge.core.domain.model.SubscriptionOffer
import io.jauge.core.domain.model.SubscriptionStatus
import io.jauge.core.domain.repository.SubscriptionRepository
import io.jauge.core.domain.usecase.ComputeTrialTimelineUseCase
import io.jauge.core.domain.usecase.GetSubscriptionManagementUrlUseCase
import io.jauge.core.domain.usecase.GetSubscriptionOfferUseCase
import io.jauge.core.domain.usecase.GetSubscriptionUseCase
import io.jauge.core.domain.usecase.PurchaseSubscriptionUseCase
import io.jauge.core.domain.usecase.RestorePurchasesUseCase
import io.jauge.feature.paywall.paywall.PaywallCompletion
import io.jauge.feature.paywall.paywall.PaywallViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class PaywallViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private class FakeSubscriptionRepository : SubscriptionRepository {
        var offer: Outcome<SubscriptionOffer> = Outcome.Success(SubscriptionOffer("jauge_plus_monthly", "6,99 €", 3))
        var subscription: Outcome<Subscription> = Outcome.Success(free)
        var purchase: Outcome<Subscription> = Outcome.Success(trial)
        var restore: Outcome<Subscription> = Outcome.Success(free)
        var purchases = 0

        override suspend fun getSubscription() = subscription
        override suspend fun getOffer() = offer
        override suspend fun purchase(offer: SubscriptionOffer): Outcome<Subscription> {
            purchases++
            return purchase
        }
        override suspend fun restorePurchases() = restore
        override suspend fun getManagementUrl(): Outcome<String> = Outcome.Success("https://apps.apple.com/account/subscriptions")

        companion object {
            val free = Subscription(SubscriptionStatus.FREE, null, null, 0)
            val trial = Subscription(SubscriptionStatus.TRIAL, LocalDate(2026, 10, 26), LocalDate(2026, 10, 26), 0)
        }
    }

    private object FixedClock : Clock {
        override fun now(): Instant = Instant.parse("2026-10-23T09:41:00Z")
    }

    private fun viewModel(repository: FakeSubscriptionRepository) = PaywallViewModel(
        platform = Platform.IOS,
        getOffer = GetSubscriptionOfferUseCase(repository),
        getSubscription = GetSubscriptionUseCase(repository),
        purchaseSubscription = PurchaseSubscriptionUseCase(repository),
        restorePurchases = RestorePurchasesUseCase(repository),
        getManagementUrl = GetSubscriptionManagementUrlUseCase(repository),
        computeTrialTimeline = ComputeTrialTimelineUseCase(FixedClock, TimeZone.UTC),
    )

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun test(block: suspend TestScope.() -> Unit) = runTest(dispatcher) { block() }

    @Test
    fun loadsTheOfferAndTheTrialTimeline() = test {
        val viewModel = viewModel(FakeSubscriptionRepository())
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals("6,99 €", state.offer.content?.formattedPrice)
        assertEquals(LocalDate(2026, 10, 25), state.timeline?.reminderOn)
        assertEquals(LocalDate(2026, 10, 26), state.timeline?.firstPaymentOn)
        assertTrue(state.canPurchase)
        assertFalse(state.isAlreadyPlus)
    }

    @Test
    fun noTrialMeansNoTimeline() = test {
        val repository = FakeSubscriptionRepository().apply { offer = Outcome.Success(SubscriptionOffer("jauge_plus_monthly", "6,99 €", 0)) }
        val viewModel = viewModel(repository)
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.timeline)
    }

    @Test
    fun purchaseUnlocksJaugePlus() = test {
        val viewModel = viewModel(FakeSubscriptionRepository())
        advanceUntilIdle()
        viewModel.purchase()
        advanceUntilIdle()
        assertEquals(PaywallCompletion.PURCHASED, viewModel.uiState.value.completion)
        assertNull(viewModel.uiState.value.dialogError)
    }

    @Test
    fun cancelledPurchaseIsSilent() = test {
        val repository = FakeSubscriptionRepository().apply { purchase = Outcome.Failure(JaugeError.PurchaseCancelled) }
        val viewModel = viewModel(repository)
        advanceUntilIdle()
        viewModel.purchase()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.dialogError)
        assertNull(viewModel.uiState.value.completion)
        assertFalse(viewModel.uiState.value.isPurchasing)
    }

    @Test
    fun purchaseFailuresAreShownAsFriendlyDialogs() = test {
        val repository = FakeSubscriptionRepository().apply { purchase = Outcome.Failure(JaugeError.PurchaseAlreadyOwned) }
        val viewModel = viewModel(repository)
        advanceUntilIdle()
        viewModel.purchase()
        advanceUntilIdle()
        assertEquals(JaugeError.PurchaseAlreadyOwned, viewModel.uiState.value.dialogError)
        viewModel.dismissError()
        assertNull(viewModel.uiState.value.dialogError)
    }

    @Test
    fun restoreWithoutSubscriptionSaysNothingToRestore() = test {
        val viewModel = viewModel(FakeSubscriptionRepository())
        advanceUntilIdle()
        viewModel.restore()
        advanceUntilIdle()
        assertEquals(JaugeError.NothingToRestore, viewModel.uiState.value.dialogError)
    }

    @Test
    fun restoreWithSubscriptionUnlocks() = test {
        val repository = FakeSubscriptionRepository().apply { restore = Outcome.Success(FakeSubscriptionRepository.trial) }
        val viewModel = viewModel(repository)
        advanceUntilIdle()
        viewModel.restore()
        advanceUntilIdle()
        assertEquals(PaywallCompletion.RESTORED, viewModel.uiState.value.completion)
    }

    @Test
    fun offerUnavailableKeepsThePurchaseDisabled() = test {
        val repository = FakeSubscriptionRepository().apply { offer = Outcome.Failure(JaugeError.PurchaseUnavailable) }
        val viewModel = viewModel(repository)
        advanceUntilIdle()
        viewModel.purchase()
        advanceUntilIdle()
        assertEquals(JaugeError.PurchaseUnavailable, viewModel.uiState.value.offer.error)
        assertFalse(viewModel.uiState.value.canPurchase)
        assertEquals(0, repository.purchases)
    }

    @Test
    fun alreadyPlusOffersToManageTheSubscription() = test {
        val repository = FakeSubscriptionRepository().apply { subscription = Outcome.Success(FakeSubscriptionRepository.trial) }
        val viewModel = viewModel(repository)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isAlreadyPlus)
        viewModel.manageSubscription()
        advanceUntilIdle()
        assertEquals("https://apps.apple.com/account/subscriptions", viewModel.uiState.value.managementUrl)
        viewModel.onManagementUrlOpened()
        assertNull(viewModel.uiState.value.managementUrl)
    }
}
