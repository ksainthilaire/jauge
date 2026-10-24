package io.jauge.feature.account.di

import io.jauge.feature.account.account.AccountViewModel
import io.jauge.feature.account.deleteaccount.DeleteAccountViewModel
import io.jauge.feature.account.email.EmailSignInViewModel
import io.jauge.feature.account.profile.ProfileViewModel
import io.jauge.feature.account.settings.SettingsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** Koin module of the account feature, listed in `composeApp` (`JaugeKoin.kt`). */
val accountModule = module {
    viewModelOf(::AccountViewModel)
    viewModelOf(::ProfileViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::DeleteAccountViewModel)
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
