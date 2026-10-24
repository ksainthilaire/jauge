package io.jauge.app.di

import io.jauge.app.AppViewModel
import io.jauge.app.notifications.CalmAlertBannerViewModel
import io.jauge.app.session.SessionSynchronizer
import io.jauge.core.authentication.authenticationModule
import io.jauge.core.billing.billingModule
import io.jauge.core.common.commonModule
import io.jauge.core.common.configuration.AppEnvironment
import io.jauge.core.data.dataModule
import io.jauge.core.domain.domainModule
import io.jauge.core.network.networkModule
import io.jauge.core.location.locationModule
import io.jauge.core.notifications.notificationsModule
import io.jauge.feature.account.di.accountModule
import io.jauge.feature.analysis.di.analysisModule
import io.jauge.feature.onboarding.di.onboardingModule
import io.jauge.feature.paywall.di.paywallModule
import io.jauge.feature.share.di.shareModule
import io.jauge.feature.twins.di.twinsModule
import io.jauge.feature.verdict.di.verdictModule
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

/** Koin module of the app shell. */
val appModule = module {
    viewModelOf(::AppViewModel)
    viewModelOf(::CalmAlertBannerViewModel)
    single { SessionSynchronizer(get(), get(), get(), get()) }
}

/** Every feature module. Add new features here. */
val featureModules: List<Module> = listOf(
    onboardingModule,
    analysisModule,
    verdictModule,
    twinsModule,
    shareModule,
    paywallModule,
    accountModule,
)

/**
 * Starts Koin with every module of the app. Called once by each platform entry point.
 *
 * @param environment the runtime environment, built by the platform.
 * @param platformDeclaration extra platform setup, for example `androidContext(application)`.
 */
fun startJaugeKoin(environment: AppEnvironment, platformDeclaration: KoinAppDeclaration = {}) {
    startKoin {
        platformDeclaration()
        modules(
            module { single { environment } },
            commonModule,
            networkModule,
            billingModule,
            authenticationModule,
            notificationsModule,
            locationModule,
            dataModule,
            domainModule,
            appModule,
        )
        modules(featureModules)
    }.koin.get<SessionSynchronizer>().start()
}
