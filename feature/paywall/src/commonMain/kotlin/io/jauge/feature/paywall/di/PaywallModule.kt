package io.jauge.feature.paywall.di

import io.jauge.core.common.configuration.AppEnvironment
import io.jauge.feature.paywall.paywall.PaywallViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Koin module of the paywall feature, listed in `composeApp` (`JaugeKoin.kt`). */
val paywallModule = module {
    viewModel {
        PaywallViewModel(
            platform = get<AppEnvironment>().platform,
            getOffer = get(),
            getSubscription = get(),
            purchaseSubscription = get(),
            restorePurchases = get(),
            getManagementUrl = get(),
            computeTrialTimeline = get(),
        )
    }
}
