package io.jauge.core.common.configuration

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds whether the app talks to the real API or uses the built in offline data ("mode sans API").
 *
 * The initial value comes from the build configuration. In debug builds a hidden switch can flip it at runtime
 * (see `docs/ARCHITECTURE.md`); release builds ignore [setOfflineMode].
 *
 * @param environment the app environment.
 */
class ApiModeController(private val environment: AppEnvironment) {

    private val offlineMode = MutableStateFlow(environment.isOfflineModeByDefault)

    /** True while the offline data is used instead of the API. */
    val isOfflineMode: StateFlow<Boolean> = offlineMode.asStateFlow()

    /** True when the hidden runtime switch is available (debug builds only). */
    val canSwitchAtRuntime: Boolean
        get() = environment.isDebugBuild

    /**
     * Turns the offline mode on or off. Has no effect outside debug builds.
     *
     * @param enabled true to use the offline data.
     */
    fun setOfflineMode(enabled: Boolean) {
        if (canSwitchAtRuntime) offlineMode.value = enabled
    }

    /** Flips the offline mode (debug builds only). */
    fun toggleOfflineMode() {
        setOfflineMode(!offlineMode.value)
    }
}
