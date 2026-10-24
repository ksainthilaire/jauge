package io.jauge.feature.account.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import io.jauge.feature.account.about.AboutScreen
import io.jauge.feature.account.account.AccountScreen
import io.jauge.feature.account.deleteaccount.DeleteAccountScreen
import io.jauge.feature.account.email.EmailSignInScreen
import io.jauge.feature.account.legalnotice.LegalNoticeScreen
import io.jauge.feature.account.privacypolicy.PrivacyPolicyScreen
import io.jauge.feature.account.profile.ProfileScreen
import io.jauge.feature.account.settings.SettingsScreen
import io.jauge.feature.account.termsofuse.TermsOfUseScreen
import kotlinx.serialization.Serializable

/**
 * Route of [io.jauge.feature.account.account.AccountScreen] (mockup 11).
 *
 * @property fromOnboarding true during the first launch: the alerts step comes next.
 */
@Serializable
data class AccountRoute(val fromOnboarding: Boolean = false)

/**
 * Route of [io.jauge.feature.account.email.EmailSignInScreen].
 *
 * @property fromOnboarding true during the first launch.
 * @property marketingEmails optional consent ticked on the account screen.
 */
@Serializable
data class EmailSignInRoute(val fromOnboarding: Boolean, val marketingEmails: Boolean)

/** Route of [io.jauge.feature.account.profile.ProfileScreen]. */
@Serializable
data object ProfileRoute

/** Route of [io.jauge.feature.account.settings.SettingsScreen]. */
@Serializable
data object SettingsRoute

/** Route of [io.jauge.feature.account.legalnotice.LegalNoticeScreen]. */
@Serializable
data object LegalNoticeRoute

/** Route of [io.jauge.feature.account.privacypolicy.PrivacyPolicyScreen]. */
@Serializable
data object PrivacyPolicyRoute

/** Route of [io.jauge.feature.account.termsofuse.TermsOfUseScreen]. */
@Serializable
data object TermsOfUseRoute

/** Route of [io.jauge.feature.account.deleteaccount.DeleteAccountScreen]. */
@Serializable
data object DeleteAccountRoute

/** Route of [io.jauge.feature.account.about.AboutScreen]. */
@Serializable
data object AboutRoute

/**
 * Adds the screens of the account feature (account, profile, settings and legal pages (mockups 11 and 13)) to the navigation graph.
 *
 * Navigation to other features goes through callbacks wired in `composeApp`: add a parameter here when a screen
 * needs to open another feature.
 *
 * @param onBack pops the current screen.
 * @param onOpenEmailSignIn opens the email sign in.
 * @param onAccountDone called once signed in or after "Continuer sans compte", with `fromOnboarding` of the route.
 * @param onOpenTerms opens the terms of use.
 * @param onOpenPrivacyPolicy opens the privacy policy.
 */
fun NavGraphBuilder.accountGraph(
    onBack: () -> Unit,
    onOpenEmailSignIn: (EmailSignInRoute) -> Unit,
    onAccountDone: (fromOnboarding: Boolean) -> Unit,
    onOpenTerms: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
) {
    composable<AccountRoute> { entry ->
        val route = entry.toRoute<AccountRoute>()
        AccountScreen(
            onBack = onBack,
            onDone = { onAccountDone(route.fromOnboarding) },
            onContinueWithEmail = { marketingEmails -> onOpenEmailSignIn(EmailSignInRoute(route.fromOnboarding, marketingEmails)) },
            onOpenTerms = onOpenTerms,
            onOpenPrivacyPolicy = onOpenPrivacyPolicy,
        )
    }
    composable<EmailSignInRoute> { entry ->
        val route = entry.toRoute<EmailSignInRoute>()
        EmailSignInScreen(
            marketingEmails = route.marketingEmails,
            onBack = onBack,
            onSignedIn = { onAccountDone(route.fromOnboarding) },
        )
    }
    composable<ProfileRoute> {
        ProfileScreen()
    }
    composable<SettingsRoute> {
        SettingsScreen(onBack = onBack)
    }
    composable<LegalNoticeRoute> {
        LegalNoticeScreen(onBack = onBack)
    }
    composable<PrivacyPolicyRoute> {
        PrivacyPolicyScreen(onBack = onBack)
    }
    composable<TermsOfUseRoute> {
        TermsOfUseScreen(onBack = onBack)
    }
    composable<DeleteAccountRoute> {
        DeleteAccountScreen(onBack = onBack)
    }
    composable<AboutRoute> {
        AboutScreen(onBack = onBack)
    }
}
