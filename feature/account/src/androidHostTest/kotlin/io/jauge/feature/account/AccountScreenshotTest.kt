package io.jauge.feature.account

import androidx.compose.runtime.Composable
import com.github.takahirom.roborazzi.captureRoboImage
import io.jauge.core.designsystem.theme.JaugeTheme
import io.jauge.core.domain.model.Account
import io.jauge.core.domain.model.AuthenticatedUser
import io.jauge.core.domain.model.Consents
import io.jauge.core.domain.model.SearchArea
import io.jauge.core.domain.model.SignInMethod
import io.jauge.core.domain.model.Subscription
import io.jauge.core.domain.model.SubscriptionStatus
import io.jauge.core.domain.model.UserPreferences
import io.jauge.core.ui.state.ContentState
import io.jauge.feature.account.about.AboutScreen
import io.jauge.feature.account.account.AccountContent
import io.jauge.feature.account.deleteaccount.DeleteAccountContent
import io.jauge.feature.account.deleteaccount.DeleteAccountUiState
import io.jauge.feature.account.legalnotice.LegalNoticeScreen
import io.jauge.feature.account.privacypolicy.PrivacyPolicyScreen
import io.jauge.feature.account.profile.ProfileContent
import io.jauge.feature.account.profile.ProfileNavigation
import io.jauge.feature.account.profile.ProfileUiState
import io.jauge.feature.account.settings.SettingsContent
import io.jauge.feature.account.settings.SettingsUiState
import kotlinx.datetime.LocalDate
import io.jauge.feature.account.account.AccountUiState
import io.jauge.feature.account.email.EmailSignInContent
import io.jauge.feature.account.email.EmailSignInStep
import io.jauge.feature.account.email.EmailSignInUiState
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.PreviewContextConfigurationEffect
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Screenshots of the account screens on an iPhone sized screen (393 x 852), compared with the mockups. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "fr-rFR-w393dp-h852dp-xxhdpi")
class AccountScreenshotTest {

    /** Mockup 11. */
    @Test
    fun account() = capture("11-compte-et-accords") {
        AccountContent(AccountUiState(), {}, {}, {}, {}, {}, {}, {})
    }

    /** Email link step of mockup 11. */
    @Test
    fun emailLink() = capture("11b-lien-par-email") {
        EmailSignInContent(EmailSignInUiState(step = EmailSignInStep.LINK_SENT, email = "ines@exemple.fr"), {}, {}, {}, {}, {})
    }

    /** Mockup 13. */
    @Test
    fun profile() = capture("13-profil-et-abonnement") { ProfileSample(isRestoring = false) }

    /** Mockup 13 while "Restaurer les achats" waits for the store. */
    @Test
    fun profileRestoring() = capture("13-profil-restauration-en-cours") { ProfileSample(isRestoring = true) }

    @Composable
    private fun ProfileSample(isRestoring: Boolean) {
        JaugeTheme(darkTheme = false) {
            ProfileContent(
                state = ProfileUiState(
                    content = ContentState(
                        content = ProfileContent(
                            account = Account("1", "Inès Bertrand", null, SignInMethod.APPLE, 12, Consents(false)),
                            subscription = Subscription(SubscriptionStatus.TRIAL, LocalDate(2026, 10, 26), null, 0),
                            formattedPrice = "6,99 €",
                            latestAnalysisId = "1",
                        ),
                        isLoading = false,
                    ),
                    user = AuthenticatedUser("1", null, "Inès Bertrand", SignInMethod.APPLE),
                    preferences = UserPreferences(calmAlertsEnabled = true, searchArea = SearchArea("Lyon", 30)),
                    isRestoring = isRestoring,
                ),
                navigation = ProfileNavigation({}, {}, {}, {}, {}, {}, {}, {}, {}),
                onRefresh = {}, onManageSubscription = {}, onRestore = {}, onExport = {}, onSignOut = {},
            )
        }
    }

    /** Privacy policy. */
    @Test
    fun privacyPolicy() = capture("legal-politique-de-confidentialite") {
        JaugeTheme(darkTheme = false) { PrivacyPolicyScreen(onBack = {}) }
    }

    /** Legal notice: publisher identity, publication director and host. */
    @Test
    fun legalNotice() = capture("legal-mentions-legales") {
        JaugeTheme(darkTheme = false) { LegalNoticeScreen(onBack = {}) }
    }

    /** Account deletion. */
    @Test
    fun deleteAccount() = capture("suppression-du-compte") {
        JaugeTheme(darkTheme = false) { DeleteAccountContent(DeleteAccountUiState(isConfirmed = true), {}, {}, {}, {}) }
    }

    /** Settings. */
    @Test
    fun settings() = capture("reglages") {
        JaugeTheme(darkTheme = false) { SettingsContent(SettingsUiState(calmAlertsEnabled = true, city = "Lyon"), {}, {}, {}, {}, {}) }
    }

    /** About. */
    @Test
    fun about() = capture("a-propos") {
        JaugeTheme(darkTheme = false) { AboutScreen({}, {}, {}, {}) }
    }

    @OptIn(ExperimentalResourceApi::class)
    internal fun capture(name: String, content: @Composable () -> Unit) {
        captureRoboImage(filePath = "build/screenshots/$name.png") {
            PreviewContextConfigurationEffect()
            content()
        }
    }
}
