package io.jauge.core.billing

import com.revenuecat.purchases.kmp.models.PeriodUnit
import com.revenuecat.purchases.kmp.models.PurchasesErrorCode
import io.jauge.core.common.configuration.AppEnvironment
import io.jauge.core.common.platform.Platform
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs

class RevenueCatBillingTest {

    @Test
    fun cancellationWinsOverAnyCode() {
        assertIs<BillingFailure.Cancelled>(revenueCatFailure(PurchasesErrorCode.StoreProblemError, userCancelled = true))
        assertIs<BillingFailure.Cancelled>(revenueCatFailure(PurchasesErrorCode.PurchaseCancelledError))
    }

    @Test
    fun everyErrorCodeBecomesAFriendlyFailure() {
        assertIs<BillingFailure.Pending>(revenueCatFailure(PurchasesErrorCode.PaymentPendingError))
        assertIs<BillingFailure.Network>(revenueCatFailure(PurchasesErrorCode.NetworkError))
        assertIs<BillingFailure.Network>(revenueCatFailure(PurchasesErrorCode.OfflineConnectionError))
        assertIs<BillingFailure.AlreadyOwned>(revenueCatFailure(PurchasesErrorCode.ProductAlreadyPurchasedError))
        assertIs<BillingFailure.OwnedByAnotherAccount>(revenueCatFailure(PurchasesErrorCode.ReceiptAlreadyInUseError))
        assertIs<BillingFailure.NotAllowed>(revenueCatFailure(PurchasesErrorCode.PurchaseNotAllowedError))
        assertIs<BillingFailure.Unavailable>(revenueCatFailure(PurchasesErrorCode.ConfigurationError))
        assertIs<BillingFailure.Store>(revenueCatFailure(PurchasesErrorCode.StoreProblemError))
        assertIs<BillingFailure.Store>(revenueCatFailure(PurchasesErrorCode.TestStoreSimulatedPurchaseError))
        PurchasesErrorCode.entries.forEach { code -> revenueCatFailure(code) }
    }

    @Test
    fun trialPeriodsAreConvertedToDays() {
        assertEquals(3, RevenueCatBillingClient.daysOf(3, PeriodUnit.DAY))
        assertEquals(7, RevenueCatBillingClient.daysOf(1, PeriodUnit.WEEK))
        assertEquals(0, RevenueCatBillingClient.daysOf(1, PeriodUnit.UNKNOWN))
    }

    @Test
    fun storePagesOfEachPlatform() {
        assertEquals(
            "https://play.google.com/store/account/subscriptions?package=io.jauge.app&sku=jauge_plus_monthly",
            storeSubscriptionsUrl(Platform.ANDROID, "jauge_plus_monthly:monthly"),
        )
        assertEquals("https://apps.apple.com/account/subscriptions", storeSubscriptionsUrl(Platform.IOS, "jauge_plus_monthly"))
    }

    @Test
    fun withoutKeyPurchasesAreDisabledCleanly() = runTest {
        val environment = AppEnvironment(Platform.ANDROID, true, "https://api.jauge.io/", true, "", "", revenueCatApiKey = "")
        val client = SwitchingBillingClient(environment, RevenueCatBillingClient("", Platform.ANDROID, isDebugBuild = true))
        assertFalse(client.isAvailable)
        assertFailsWith<BillingFailure.Unavailable> { client.getOffer() }
        assertFailsWith<BillingFailure.Unavailable> { client.managementUrl() }
        assertFalse(client.customerInfo().isPlusActive)
    }
}
