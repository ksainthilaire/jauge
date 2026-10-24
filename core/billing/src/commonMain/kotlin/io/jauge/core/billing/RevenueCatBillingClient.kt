package io.jauge.core.billing

import com.revenuecat.purchases.kmp.LogLevel
import com.revenuecat.purchases.kmp.Purchases
import com.revenuecat.purchases.kmp.configure
import com.revenuecat.purchases.kmp.ktx.awaitCustomerInfo
import com.revenuecat.purchases.kmp.ktx.awaitLogIn
import com.revenuecat.purchases.kmp.ktx.awaitLogOut
import com.revenuecat.purchases.kmp.ktx.awaitOfferings
import com.revenuecat.purchases.kmp.ktx.awaitPurchase
import com.revenuecat.purchases.kmp.ktx.awaitRestore
import com.revenuecat.purchases.kmp.ktx.awaitTrialOrIntroPriceEligibility
import com.revenuecat.purchases.kmp.models.CustomerInfo
import com.revenuecat.purchases.kmp.models.DiscountPaymentMode
import com.revenuecat.purchases.kmp.models.IntroEligibilityStatus
import com.revenuecat.purchases.kmp.models.Package
import com.revenuecat.purchases.kmp.models.PackageType
import com.revenuecat.purchases.kmp.models.PeriodType
import com.revenuecat.purchases.kmp.models.PeriodUnit
import com.revenuecat.purchases.kmp.models.PurchasesException
import com.revenuecat.purchases.kmp.models.PurchasesTransactionException
import com.revenuecat.purchases.kmp.models.StoreProduct
import com.revenuecat.purchases.kmp.models.freePhase
import io.jauge.core.common.platform.Platform
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Store purchases through RevenueCat (official purchases-kmp SDK). Debug builds use the RevenueCat Test Store key,
 * release builds the public key of the platform (`goog_`, `appl_`). Only public SDK keys reach the app.
 *
 * Jauge sells a single product: the monthly Jauge Plus subscription of the current offering (package `$rc_monthly`),
 * with a free trial, which unlocks the [ENTITLEMENT_ID] entitlement. See `docs/revenuecat.md`.
 *
 * @param apiKey public SDK key of this build.
 * @param platform the running platform (the trial is read differently on each store).
 * @param isDebugBuild true to log the SDK activity.
 */
class RevenueCatBillingClient(
    private val apiKey: String,
    private val platform: Platform,
    private val isDebugBuild: Boolean,
) : BillingClient {

    private val configurationLock = Mutex()
    private var lastPackage: Package? = null

    override val isAvailable: Boolean
        get() = apiKey.isNotBlank()

    override suspend fun getOffer(): BillingOffer = revenueCat {
        val monthly = loadMonthlyPackage()
        val product = monthly.storeProduct
        BillingOffer(
            productId = product.id,
            formattedPrice = product.price.formatted,
            trialDays = trialDays(product),
        )
    }

    override suspend fun purchase(productId: String): BillingCustomerInfo = revenueCat {
        val monthly = lastPackage?.takeIf { it.storeProduct.id == productId } ?: loadMonthlyPackage()
        purchases().awaitPurchase(monthly).customerInfo.toBillingCustomerInfo()
    }

    override suspend fun restore(): BillingCustomerInfo = revenueCat {
        purchases().awaitRestore().toBillingCustomerInfo()
    }

    override suspend fun customerInfo(): BillingCustomerInfo = revenueCat {
        purchases().awaitCustomerInfo().toBillingCustomerInfo()
    }

    override suspend fun identify(appUserId: String?) {
        revenueCat {
            val purchases = purchases()
            when {
                appUserId != null && purchases.appUserID != appUserId -> purchases.awaitLogIn(appUserId)
                appUserId == null && !purchases.isAnonymous -> purchases.awaitLogOut()
                else -> Unit
            }
        }
    }

    override suspend fun managementUrl(): String = revenueCat {
        val fromStore = runCatching { purchases().awaitCustomerInfo().managementUrlString }.getOrNull()
        fromStore?.takeIf { it.isNotBlank() } ?: storeSubscriptionsUrl(platform, lastPackage?.storeProduct?.id)
    }

    private suspend fun loadMonthlyPackage(): Package {
        val offering = purchases().awaitOfferings().current ?: throw BillingFailure.Unavailable()
        val monthly = offering.monthly
            ?: offering.availablePackages.firstOrNull { it.packageType == PackageType.MONTHLY }
            ?: offering.availablePackages.firstOrNull()
            ?: throw BillingFailure.Unavailable()
        lastPackage = monthly
        return monthly
    }

    /**
     * Free trial length offered to this user, 0 when the user is not eligible (trial already used). On Google Play
     * the default option of the product only contains the trial when the user is eligible; on the App Store the
     * eligibility is asked separately.
     */
    private suspend fun trialDays(product: StoreProduct): Int = when (platform) {
        Platform.ANDROID -> product.defaultOption?.freePhase?.billingPeriod?.let { daysOf(it.value, it.unit) } ?: 0
        Platform.IOS -> {
            val introductory = product.introductoryDiscount?.takeIf { it.paymentMode == DiscountPaymentMode.FREE_TRIAL }
            if (introductory == null) {
                0
            } else {
                val eligibility = runCatching { purchases().awaitTrialOrIntroPriceEligibility(listOf(product))[product] }
                    .getOrNull()
                if (eligibility == IntroEligibilityStatus.INELIGIBLE) {
                    0
                } else {
                    daysOf(introductory.subscriptionPeriod.value, introductory.subscriptionPeriod.unit) *
                        introductory.numberOfPeriods.toInt().coerceAtLeast(1)
                }
            }
        }
    }

    private suspend fun purchases(): Purchases = configurationLock.withLock {
        if (apiKey.isBlank()) throw BillingFailure.Unavailable()
        if (!Purchases.isConfigured) {
            Purchases.logLevel = if (isDebugBuild) LogLevel.DEBUG else LogLevel.WARN
            Purchases.configure(apiKey)
        }
        Purchases.sharedInstance
    }

    /** Runs a RevenueCat call and turns every failure into a [BillingFailure]. */
    private suspend fun <T> revenueCat(block: suspend () -> T): T = try {
        block()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: BillingFailure) {
        throw failure
    } catch (failure: PurchasesTransactionException) {
        throw revenueCatFailure(failure.code, failure.userCancelled)
    } catch (failure: PurchasesException) {
        throw revenueCatFailure(failure.code)
    } catch (failure: Exception) {
        throw BillingFailure.Store()
    }

    /** Values shared with the documentation and the tests. */
    companion object {
        /** Entitlement unlocked by Jauge Plus, created in the RevenueCat dashboard. */
        const val ENTITLEMENT_ID = "plus"

        /**
         * Length of a store period in days.
         *
         * @param value number of units.
         * @param unit unit of the period.
         */
        fun daysOf(value: Int, unit: PeriodUnit): Int = when (unit) {
            PeriodUnit.DAY -> value
            PeriodUnit.WEEK -> value * DAYS_PER_WEEK
            PeriodUnit.MONTH -> value * DAYS_PER_MONTH
            PeriodUnit.YEAR -> value * DAYS_PER_YEAR
            PeriodUnit.UNKNOWN -> 0
        }

        private const val DAYS_PER_WEEK = 7
        private const val DAYS_PER_MONTH = 30
        private const val DAYS_PER_YEAR = 365
    }
}

/**
 * Reads the `plus` entitlement of a RevenueCat customer.
 *
 * @receiver the customer returned by RevenueCat.
 */
internal fun CustomerInfo.toBillingCustomerInfo(): BillingCustomerInfo {
    val plus = entitlements.active[RevenueCatBillingClient.ENTITLEMENT_ID]
    return BillingCustomerInfo(
        isPlusActive = plus != null,
        isInTrial = plus?.periodType == PeriodType.TRIAL,
        expiresAtEpochMilliseconds = plus?.expirationDateMillis,
        willRenew = plus?.willRenew ?: false,
        managementUrl = managementUrlString,
    )
}
