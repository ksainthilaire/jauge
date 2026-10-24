package io.jauge.feature.onboarding

import io.jauge.core.domain.model.JaugeNotification
import io.jauge.core.domain.model.OnboardingStep
import io.jauge.core.domain.model.Outcome
import io.jauge.core.domain.model.PushPermissionStatus
import io.jauge.core.domain.model.UserPreferences
import io.jauge.core.domain.repository.NotificationRepository
import io.jauge.core.domain.repository.PreferencesRepository
import io.jauge.core.domain.usecase.CompleteOnboardingStepUseCase
import io.jauge.core.domain.usecase.RegisterDeviceUseCase
import io.jauge.feature.onboarding.alerts.AlertsPermissionViewModel
import io.jauge.feature.onboarding.welcome.WelcomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Preferences kept in memory for the tests. */
class FakePreferencesRepository : PreferencesRepository {
    /** Current value. */
    val preferences = MutableStateFlow(UserPreferences())

    override fun observePreferences(): Flow<UserPreferences> = preferences

    override suspend fun update(transform: (UserPreferences) -> UserPreferences) {
        preferences.value = transform(preferences.value)
    }
}

/** Notifications that only count the registrations. */
class FakeNotificationRepository : NotificationRepository {
    /** Number of registrations. */
    var registrations = 0

    override fun observeIncoming(): Flow<JaugeNotification> = emptyFlow()

    override suspend fun getNotifications(): Outcome<List<JaugeNotification>> = Outcome.Success(emptyList())

    override suspend fun markAsRead(notificationId: String): Outcome<Unit> = Outcome.Success(Unit)

    override suspend fun getPermissionStatus(): PushPermissionStatus = PushPermissionStatus.NOT_DETERMINED

    override suspend fun requestPermission(): Outcome<PushPermissionStatus> = Outcome.Success(PushPermissionStatus.GRANTED)

    override suspend fun registerDevice(): Outcome<Unit> {
        registrations++
        return Outcome.Success(Unit)
    }

    override suspend fun unregisterDevice(): Outcome<Unit> = Outcome.Success(Unit)
}

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    private val notifications = FakeNotificationRepository()
    private val repository = FakePreferencesRepository()
    private val completeStep = CompleteOnboardingStepUseCase(repository)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun startingTheFirstAnalysisLeadsToTheAccountStep() {
        var done = false
        WelcomeViewModel(completeStep).passWelcome { done = true }
        assertTrue(done)
        assertEquals(OnboardingStep.ACCOUNT, repository.preferences.value.onboardingStep)
    }

    @Test
    fun skippingEndsTheFirstLaunch() {
        WelcomeViewModel(completeStep).skip {}
        assertEquals(OnboardingStep.DONE, repository.preferences.value.onboardingStep)
        assertFalse(repository.preferences.value.calmAlertsEnabled)
    }

    @Test
    fun grantedPermissionTurnsCalmAlertsOn() {
        val viewModel = AlertsPermissionViewModel(completeStep, RegisterDeviceUseCase(notifications))
        viewModel.onSystemRequestShown()
        assertTrue(viewModel.uiState.value.isWaitingForSystem)
        var done = false
        viewModel.onSystemAnswer(granted = true) { done = true }
        assertTrue(done)
        assertFalse(viewModel.uiState.value.isWaitingForSystem)
        assertTrue(repository.preferences.value.calmAlertsEnabled)
        assertEquals(1, notifications.registrations)
        assertEquals(OnboardingStep.DONE, repository.preferences.value.onboardingStep)
    }

    @Test
    fun laterKeepsCalmAlertsOff() {
        AlertsPermissionViewModel(completeStep, RegisterDeviceUseCase(notifications)).later {}
        assertFalse(repository.preferences.value.calmAlertsEnabled)
        assertEquals(0, notifications.registrations)
        assertEquals(OnboardingStep.DONE, repository.preferences.value.onboardingStep)
    }
}
