package io.jauge.app

import io.jauge.app.di.appModule
import io.jauge.app.di.featureModules
import io.jauge.core.billing.BillingClient
import io.jauge.core.billing.billingModule
import io.jauge.core.common.commonModule
import io.jauge.core.common.configuration.AppEnvironment
import io.jauge.core.common.platform.Platform
import io.jauge.core.common.platform.currentPlatform
import io.jauge.core.data.dataModule
import io.jauge.core.domain.domainModule
import io.jauge.core.domain.repository.AnalysisRepository
import io.jauge.core.domain.repository.SubscriptionRepository
import io.jauge.core.domain.usecase.AnalyzeOfferUseCase
import io.jauge.core.domain.usecase.PurchaseSubscriptionUseCase
import io.jauge.core.network.JaugeApi
import io.jauge.core.network.networkModule
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertFalse

class KoinGraphTest {

    @Test
    fun everyLayerResolves() {
        val environment = AppEnvironment(
            platform = currentPlatform,
            isDebugBuild = true,
            apiBaseUrl = "https://api.jauge.io/",
            isOfflineModeByDefault = true,
            supabaseUrl = "",
            supabasePublishableKey = "",
            revenueCatApiKey = "",
        )
        val koin = koinApplication {
            modules(module { single { environment } }, commonModule, networkModule, billingModule, dataModule, domainModule, appModule)
            modules(featureModules)
        }.koin
        koin.get<JaugeApi>()
        koin.get<AnalysisRepository>()
        koin.get<SubscriptionRepository>()
        koin.get<AnalyzeOfferUseCase>()
        koin.get<PurchaseSubscriptionUseCase>()
        koin.get<AppViewModel>()
        assertFalse(koin.get<BillingClient>().isAvailable, "No RevenueCat key: purchases must be disabled")
        check(environment.platform == Platform.ANDROID || environment.platform == Platform.IOS)
    }
}
