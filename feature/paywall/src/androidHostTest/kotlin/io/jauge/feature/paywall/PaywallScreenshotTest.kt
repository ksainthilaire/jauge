package io.jauge.feature.paywall

import androidx.compose.runtime.Composable
import com.github.takahirom.roborazzi.captureRoboImage
import io.jauge.core.common.platform.Platform
import io.jauge.core.domain.error.JaugeError
import io.jauge.core.domain.model.Subscription
import io.jauge.core.domain.model.SubscriptionOffer
import io.jauge.core.domain.model.SubscriptionStatus
import io.jauge.core.domain.usecase.ComputeTrialTimelineUseCase
import io.jauge.core.ui.state.ContentState
import io.jauge.feature.paywall.paywall.PaywallActions
import io.jauge.feature.paywall.paywall.PaywallCompletion
import io.jauge.feature.paywall.paywall.PaywallContent
import io.jauge.feature.paywall.paywall.PaywallUiState
import kotlinx.datetime.LocalDate
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Captures the paywall (mockup 08, single monthly offer) in its main states, at the iPhone size of the mockups. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "fr-rFR-w393dp-h852dp-xhdpi")
class PaywallScreenshotTest {

    private val offer = SubscriptionOffer("jauge_plus_monthly", "6,99 €", 3)
    private val ready = PaywallUiState(
        offer = ContentState(content = offer, isLoading = false),
        timeline = ComputeTrialTimelineUseCase.timelineFrom(LocalDate(2026, 10, 23), 3),
        platform = Platform.IOS,
    )
    private val actions = PaywallActions({}, {}, {}, {}, {})

    private fun capture(name: String, content: @Composable () -> Unit) {
        captureRoboImage(System.getProperty("jauge.screenshots.directory") + "/$name.png") { content() }
    }

    @Composable
    private fun Paywall(state: PaywallUiState, showsQuotaReached: Boolean = true) {
        PaywallContent(state, showsQuotaReached, actions, {}, {}, {}, {}, {})
    }

    @Test
    fun offerWithTrial() = capture("08-paywall") { Paywall(ready) }

    @Test
    fun offerWithoutTrialOnAndroid() = capture("08-paywall-no-trial-android") {
        Paywall(ready.copy(offer = ContentState(content = offer.copy(trialDays = 0), isLoading = false), timeline = null, platform = Platform.ANDROID), showsQuotaReached = false)
    }

    @Test
    fun loadingOffer() = capture("08-paywall-loading") { Paywall(PaywallUiState(platform = Platform.IOS)) }

    @Test
    fun purchasesUnavailable() = capture("08-paywall-unavailable") {
        Paywall(PaywallUiState(offer = ContentState(isLoading = false, error = JaugeError.NoConnection)))
    }

    @Test
    fun purchaseError() = capture("08-paywall-error-already-owned") { Paywall(ready.copy(dialogError = JaugeError.PurchaseAlreadyOwned)) }

    @Test
    fun alreadyPlus() = capture("08-paywall-already-plus") {
        Paywall(ready.copy(subscription = Subscription(SubscriptionStatus.TRIAL, LocalDate(2026, 10, 26), LocalDate(2026, 10, 26), 0)), showsQuotaReached = false)
    }

    @Test
    fun unlocked() = capture("08-paywall-success") {
        Paywall(ready.copy(subscription = Subscription(SubscriptionStatus.TRIAL, LocalDate(2026, 10, 26), LocalDate(2026, 10, 26), 0), completion = PaywallCompletion.PURCHASED))
    }
}
