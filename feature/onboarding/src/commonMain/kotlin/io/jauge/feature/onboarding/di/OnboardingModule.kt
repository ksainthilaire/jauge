package io.jauge.feature.onboarding.di

import io.jauge.feature.onboarding.alerts.AlertsPermissionViewModel
import io.jauge.feature.onboarding.welcome.WelcomeViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** Koin module of the onboarding feature (mockups 01 and 12), listed in `composeApp` (`JaugeKoin.kt`). */
val onboardingModule = module {
    viewModelOf(::WelcomeViewModel)
    viewModelOf(::AlertsPermissionViewModel)
}
