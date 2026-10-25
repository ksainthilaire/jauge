package io.jauge.feature.account

import io.jauge.core.domain.error.JaugeError
import io.jauge.core.domain.model.AuthenticationProvider
import io.jauge.core.domain.model.OnboardingStep
import io.jauge.core.domain.model.SignInMethod
import io.jauge.core.domain.usecase.CompleteOnboardingStepUseCase
import io.jauge.core.domain.usecase.ObserveAuthenticationStateUseCase
import io.jauge.core.domain.usecase.SendMagicLinkUseCase
import io.jauge.core.domain.usecase.SignInWithProviderUseCase
import io.jauge.core.domain.usecase.UpdateConsentsUseCase
import io.jauge.feature.account.account.AccountViewModel
import io.jauge.feature.account.email.EmailSignInStep
import io.jauge.feature.account.email.EmailSignInViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SignInViewModelTest {
    private val preferences = FakePreferencesRepository()
    private val authentication = FakeAuthenticationRepository()
    private val account = FakeAccountRepository()

    private val scheduler = TestCoroutineScheduler()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher(scheduler))

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun accountViewModel() = AccountViewModel(
        ObserveAuthenticationStateUseCase(authentication),
        SignInWithProviderUseCase(authentication),
        UpdateConsentsUseCase(account),
        CompleteOnboardingStepUseCase(preferences),
    )

    private fun emailViewModel(marketingEmails: Boolean = false) = EmailSignInViewModel(
        ObserveAuthenticationStateUseCase(authentication),
        SendMagicLinkUseCase(authentication),
        UpdateConsentsUseCase(account),
        CompleteOnboardingStepUseCase(preferences),
        marketingEmails,
    )

    @Test
    fun appleSignInSavesTheConsentAndPassesTheStep() {
        val viewModel = accountViewModel()
        viewModel.setMarketingEmails(true)
        viewModel.signIn(AuthenticationProvider.APPLE)
        assertTrue(viewModel.uiState.value.isDone)
        assertEquals(true, account.savedConsents?.marketingEmails)
        assertEquals(OnboardingStep.ALERTS, preferences.preferences.value.onboardingStep)
    }

    @Test
    fun providerSignInFinishedOutsideTheAppIsFollowed() {
        authentication.finishesAtOnce = false
        val viewModel = accountViewModel()
        viewModel.signIn(AuthenticationProvider.GOOGLE)
        assertFalse(viewModel.uiState.value.isDone)
        authentication.completeFromOutside(SignInMethod.GOOGLE)
        assertTrue(viewModel.uiState.value.isDone)
    }

    @Test
    fun cancelledSignInIsSilent() {
        authentication.nextError = JaugeError.SignInCancelled
        val viewModel = accountViewModel()
        viewModel.signIn(AuthenticationProvider.GOOGLE)
        assertFalse(viewModel.uiState.value.isDone)
        assertNull(viewModel.uiState.value.error)
        assertNull(viewModel.uiState.value.signingInWith)
    }

    @Test
    fun failureIsShownAsAFriendlyError() {
        authentication.nextError = JaugeError.SignInFailed
        val viewModel = accountViewModel()
        viewModel.signIn(AuthenticationProvider.APPLE)
        assertEquals(JaugeError.SignInFailed, viewModel.uiState.value.error)
        viewModel.dismissError()
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun continuingWithoutAccountPassesTheStep() {
        val viewModel = accountViewModel()
        viewModel.continueWithoutAccount()
        assertTrue(viewModel.uiState.value.isDone)
        assertTrue(preferences.preferences.value.hasPassedAccount)
    }

    @Test
    fun invalidAddressIsRefusedLocally() {
        val viewModel = emailViewModel()
        viewModel.onEmailChange("ines@")
        viewModel.sendLink()
        assertTrue(viewModel.uiState.value.isAddressInvalid)
        assertNull(authentication.linkSentTo)
    }

    @Test
    fun openingTheLinkSignsIn() {
        val viewModel = emailViewModel(marketingEmails = true)
        viewModel.onEmailChange(" ines@exemple.fr ")
        viewModel.sendLink()
        assertEquals(EmailSignInStep.LINK_SENT, viewModel.uiState.value.step)
        assertEquals("ines@exemple.fr", authentication.linkSentTo)
        scheduler.advanceTimeBy(60_000)
        scheduler.runCurrent()
        viewModel.resendLink()
        assertTrue(viewModel.uiState.value.linkSentAgain)
        authentication.completeFromOutside(SignInMethod.EMAIL)
        assertTrue(viewModel.uiState.value.isDone)
        assertEquals(true, account.savedConsents?.marketingEmails)
        viewModel.changeAddress()
        assertEquals(EmailSignInStep.ADDRESS, viewModel.uiState.value.step)
    }

    @Test
    fun resendWaitsSixtySecondsAfterEachSending() {
        val viewModel = emailViewModel()
        viewModel.onEmailChange("ines@exemple.fr")
        viewModel.sendLink()
        assertEquals(60, viewModel.uiState.value.resendAvailableInSeconds)
        viewModel.resendLink()
        assertFalse(viewModel.uiState.value.linkSentAgain)
        scheduler.advanceTimeBy(18_000)
        scheduler.runCurrent()
        assertEquals(42, viewModel.uiState.value.resendAvailableInSeconds)
        scheduler.advanceTimeBy(42_000)
        scheduler.runCurrent()
        assertEquals(0, viewModel.uiState.value.resendAvailableInSeconds)
        viewModel.resendLink()
        assertTrue(viewModel.uiState.value.linkSentAgain)
        assertEquals(60, viewModel.uiState.value.resendAvailableInSeconds)
        viewModel.changeAddress()
        assertEquals(0, viewModel.uiState.value.resendAvailableInSeconds)
    }
}
