package io.jauge.feature.account

import io.jauge.core.domain.model.Outcome
import io.jauge.core.domain.service.AppleAuthorization
import io.jauge.core.domain.service.NoAppleAuthorization
import io.jauge.core.domain.error.JaugeError
import io.jauge.core.domain.model.AnalysisSummary
import io.jauge.core.domain.model.AuthenticationState
import io.jauge.core.domain.model.SignInMethod
import io.jauge.core.domain.model.Subscription
import io.jauge.core.domain.model.SubscriptionStatus
import io.jauge.core.domain.usecase.DeleteAccountUseCase
import io.jauge.core.domain.usecase.GetAccountUseCase
import io.jauge.core.domain.usecase.GetAnalysisHistoryUseCase
import io.jauge.core.domain.usecase.GetSubscriptionManagementUrlUseCase
import io.jauge.core.domain.usecase.GetSubscriptionOfferUseCase
import io.jauge.core.domain.usecase.GetSubscriptionUseCase
import io.jauge.core.domain.usecase.ObserveAuthenticationStateUseCase
import io.jauge.core.domain.usecase.ObservePreferencesUseCase
import io.jauge.core.domain.usecase.RestorePurchasesUseCase
import io.jauge.core.domain.usecase.SignOutUseCase
import io.jauge.core.domain.usecase.UpdateSettingsUseCase
import io.jauge.feature.account.deleteaccount.DeleteAccountViewModel
import io.jauge.feature.account.profile.ProfileMessage
import io.jauge.feature.account.profile.ProfileViewModel
import io.jauge.feature.account.profile.analysesToCsv
import io.jauge.feature.account.settings.SettingsViewModel
import io.jauge.core.domain.repository.UnavailableLocationRepository
import io.jauge.core.domain.usecase.LocateSearchAreaUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {
    private val preferences = FakePreferencesRepository()
    private val authentication = FakeAuthenticationRepository()
    private val account = FakeAccountRepository()
    private val subscriptions = FakeSubscriptionRepository()
    private val analyses = FakeAnalysisRepository()
    private val notifications = FakeNotificationRepository()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun profileViewModel() = ProfileViewModel(
        observeAuthenticationState = ObserveAuthenticationStateUseCase(authentication),
        observePreferences = ObservePreferencesUseCase(preferences),
        getAccount = GetAccountUseCase(account),
        getSubscription = GetSubscriptionUseCase(subscriptions),
        getOffer = GetSubscriptionOfferUseCase(subscriptions),
        getHistory = GetAnalysisHistoryUseCase(analyses),
        restorePurchases = RestorePurchasesUseCase(subscriptions),
        getManagementUrl = GetSubscriptionManagementUrlUseCase(subscriptions),
        signOut = SignOutUseCase(authentication, notifications),
    )

    @Test
    fun profileLoadsAccountSubscriptionAndPrice() {
        authentication.completeFromOutside(SignInMethod.APPLE)
        analyses.history.value = listOf(summary("analysis-1"))
        val state = profileViewModel().uiState.value
        assertEquals("Inès Bertrand", state.user?.displayName)
        assertEquals(12, state.content.content?.account?.analysisCount)
        assertEquals("6,99 €", state.content.content?.formattedPrice)
        assertEquals("analysis-1", state.content.content?.latestAnalysisId)
    }

    @Test
    fun accountFailureKeepsTheSubscriptionAndShowsNoWrongValue() {
        account.error = JaugeError.NoConnection
        val state = profileViewModel().uiState.value
        assertNull(state.content.content?.account)
        assertEquals(SubscriptionStatus.FREE, state.content.content?.subscription?.status)
        assertEquals(JaugeError.NoConnection, state.content.error)
    }

    @Test
    fun restoreUpdatesTheSubscription() {
        val viewModel = profileViewModel()
        subscriptions.subscription = Subscription(SubscriptionStatus.ACTIVE, null, LocalDate(2026, 11, 26), 0)
        viewModel.restore()
        assertEquals(ProfileMessage.PURCHASES_RESTORED, viewModel.uiState.value.message)
        assertEquals(SubscriptionStatus.ACTIVE, viewModel.uiState.value.content.content?.subscription?.status)
        subscriptions.restoreError = JaugeError.NothingToRestore
        viewModel.restore()
        assertEquals(JaugeError.NothingToRestore, viewModel.uiState.value.actionError)
    }

    @Test
    fun restoreWithoutActivePlusSaysNothingToRestore() {
        val viewModel = profileViewModel()
        viewModel.restore()
        val state = viewModel.uiState.value
        assertNull(state.message)
        assertEquals(JaugeError.NothingToRestore, state.actionError)
        assertFalse(state.isRestoring)
        assertEquals(SubscriptionStatus.FREE, state.content.content?.subscription?.status)
    }

    @Test
    fun restoreNetworkFailureShowsTheFriendlyErrorAndNoSuccess() {
        val viewModel = profileViewModel()
        subscriptions.restoreError = JaugeError.NoConnection
        viewModel.restore()
        val state = viewModel.uiState.value
        assertNull(state.message)
        assertEquals(JaugeError.NoConnection, state.actionError)
        assertFalse(state.isRestoring)
    }

    @Test
    fun restoreIsLoadingWhileTheStoreAnswers() {
        val gate = CompletableDeferred<Unit>()
        subscriptions.restoreGate = gate
        subscriptions.subscription = Subscription(SubscriptionStatus.ACTIVE, null, LocalDate(2026, 11, 26), 0)
        val viewModel = profileViewModel()
        viewModel.restore()
        assertTrue(viewModel.uiState.value.isRestoring)
        gate.complete(Unit)
        assertFalse(viewModel.uiState.value.isRestoring)
        assertEquals(ProfileMessage.PURCHASES_RESTORED, viewModel.uiState.value.message)
    }

    @Test
    fun manageOpensTheStorePage() {
        val viewModel = profileViewModel()
        viewModel.manageSubscription()
        assertEquals(FakeSubscriptionRepository.MANAGEMENT_URL, viewModel.uiState.value.managementUrl)
        viewModel.onUrlOpened()
        assertNull(viewModel.uiState.value.managementUrl)
    }

    @Test
    fun exportNeedsAnalyses() {
        val viewModel = profileViewModel()
        viewModel.export()
        assertEquals(ProfileMessage.NOTHING_TO_EXPORT, viewModel.uiState.value.message)
        analyses.history.value = listOf(summary("analysis-1"))
        viewModel.export()
        assertEquals(1, viewModel.uiState.value.exportedAnalyses?.size)
    }

    @Test
    fun signOutUnregistersTheDevice() {
        authentication.completeFromOutside(SignInMethod.GOOGLE)
        val viewModel = profileViewModel()
        viewModel.confirmSignOut()
        assertNull(viewModel.uiState.value.user)
        assertEquals(1, notifications.unregistrations)
        assertEquals(ProfileMessage.SIGNED_OUT, viewModel.uiState.value.message)
    }

    @Test
    fun exportIsASpreadsheetFile() {
        val csv = analysesToCsv("Date;Poste", listOf(summary("1", company = "Sorel; et fils")), TimeZone.UTC)
        val lines = csv.trim().lines()
        assertEquals("Date;Poste", lines[0])
        assertEquals("2026-10-23;Chargée de marketing digital;\"Sorel; et fils\";Lyon 3e;78", lines[1])
    }

    @Test
    fun settingsSaveAlertsAndArea() {
        val viewModel = SettingsViewModel(ObservePreferencesUseCase(preferences), UpdateSettingsUseCase(preferences), LocateSearchAreaUseCase(preferences, UnavailableLocationRepository()))
        viewModel.setCalmAlertsEnabled(true)
        viewModel.onCityChange("Lyon")
        viewModel.onRadiusChange(50)
        viewModel.saveSearchArea()
        assertTrue(preferences.preferences.value.calmAlertsEnabled)
        assertEquals(50, preferences.preferences.value.searchArea?.radiusKilometers)
        assertTrue(viewModel.uiState.value.isSaved)
    }

    @Test
    fun deletionNeedsConfirmationThenSignsOutAndResets() {
        authentication.completeFromOutside(SignInMethod.APPLE)
        preferences.preferences.value = preferences.preferences.value.copy(hasPassedWelcome = true, hasPassedAccount = true)
        val viewModel = deleteAccountViewModel { Outcome.Success("apple-code-123456") }
        viewModel.delete()
        assertFalse(account.deleted)
        viewModel.setConfirmed(true)
        viewModel.delete()
        assertTrue(account.deleted)
        assertEquals("apple-code-123456", account.receivedAppleAuthorizationCode)
        assertTrue(viewModel.uiState.value.isDeleted)
        assertFalse(preferences.preferences.value.hasPassedWelcome)
        assertEquals(AuthenticationState.SignedOut, authentication.state.value)
    }

    @Test
    fun deletionFailureIsShown() {
        account.error = JaugeError.ServerUnavailable
        val viewModel = deleteAccountViewModel()
        viewModel.setConfirmed(true)
        viewModel.delete()
        assertEquals(JaugeError.ServerUnavailable, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isDeleted)
    }

    @Test
    fun closingTheAppleSheetKeepsTheAccountWithoutAnError() {
        authentication.completeFromOutside(SignInMethod.APPLE)
        val viewModel = deleteAccountViewModel { Outcome.Failure(JaugeError.SignInCancelled) }
        viewModel.setConfirmed(true)
        viewModel.delete()
        assertFalse(account.deleted)
        assertNull(viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isDeleting)
    }

    @Test
    fun appleFailuresAndOtherAccountsDeleteWithoutCode() {
        authentication.completeFromOutside(SignInMethod.APPLE)
        val failing = deleteAccountViewModel { Outcome.Failure(JaugeError.SignInFailed) }
        failing.setConfirmed(true)
        failing.delete()
        assertTrue(account.deleted)
        assertNull(account.receivedAppleAuthorizationCode)
    }

    @Test
    fun accountsNotSignedInWithAppleNeverShowTheAppleSheet() {
        authentication.completeFromOutside(SignInMethod.GOOGLE)
        var asked = false
        val viewModel = deleteAccountViewModel { asked = true; Outcome.Success("apple-code-123456") }
        viewModel.setConfirmed(true)
        viewModel.delete()
        assertFalse(asked)
        assertTrue(account.deleted)
        assertNull(account.receivedAppleAuthorizationCode)
    }

    private fun deleteAccountViewModel(appleAuthorization: AppleAuthorization = NoAppleAuthorization) = DeleteAccountViewModel(
        DeleteAccountUseCase(account, authentication, appleAuthorization),
        SignOutUseCase(authentication, notifications),
        UpdateSettingsUseCase(preferences),
        GetSubscriptionManagementUrlUseCase(subscriptions),
    )

    private fun summary(id: String, company: String = "Atelier Sorel") = AnalysisSummary(
        id = id,
        createdAt = LocalDate(2026, 10, 23).atStartOfDayIn(TimeZone.UTC),
        jobTitle = "Chargée de marketing digital",
        company = company,
        place = "Lyon 3e",
        score = 78,
    )
}
