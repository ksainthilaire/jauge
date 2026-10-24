package io.jauge.feature.account.di

import io.jauge.feature.account.account.AccountViewModel
import io.jauge.feature.account.email.EmailSignInViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** Koin module of the account feature, listed in `composeApp` (`JaugeKoin.kt`). */
val accountModule = module {
    viewModelOf(::AccountViewModel)
    viewModel { parameters ->
        EmailSignInViewModel(
            observeAuthenticationState = get(),
            sendMagicLink = get(),
            updateConsents = get(),
            completeOnboardingStep = get(),
            marketingEmails = parameters.get(),
        )
    }
}
