package io.jauge.feature.onboarding.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.jauge.core.domain.usecase.CompleteOnboardingStepUseCase
import io.jauge.core.domain.usecase.RegisterDeviceUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * State of the alerts screen.
 *
 * @property isWaitingForSystem true while the system request is shown.
 */
data class AlertsPermissionUiState(val isWaitingForSystem: Boolean = false)

/**
 * View model of the alerts screen (mockup 12): records the answer once the system request is closed, or "Plus tard".
 *
 * The system request itself is shown by the screen (`rememberNotificationPermissionRequest`), because Android needs
 * the activity; once granted, the device is registered for push.
 *
 * @param completeOnboardingStep records the first launch answers.
 * @param registerDevice saves the push token of this device.
 */
class AlertsPermissionViewModel(
    private val completeOnboardingStep: CompleteOnboardingStepUseCase,
    private val registerDevice: RegisterDeviceUseCase,
) : ViewModel() {
    private val state = MutableStateFlow(AlertsPermissionUiState())

    /** Observed state of the screen. */
    val uiState: StateFlow<AlertsPermissionUiState> = state.asStateFlow()

    /** "Continuer": the system request is about to be shown. */
    fun onSystemRequestShown() {
        state.value = AlertsPermissionUiState(isWaitingForSystem = true)
    }

    /**
     * The system request was answered.
     *
     * @param granted true when notifications are allowed.
     * @param onDone called once the answer is saved.
     */
    fun onSystemAnswer(granted: Boolean, onDone: () -> Unit) {
        viewModelScope.launch {
            if (granted) registerDevice()
            completeOnboardingStep.answerAlerts(alertsEnabled = granted)
            state.value = AlertsPermissionUiState(isWaitingForSystem = false)
            onDone()
        }
    }

    /**
     * "Plus tard": no request now, alerts stay off.
     *
     * @param onDone called once the answer is saved.
     */
    fun later(onDone: () -> Unit) {
        viewModelScope.launch {
            completeOnboardingStep.answerAlerts(alertsEnabled = false)
            onDone()
        }
    }
}
