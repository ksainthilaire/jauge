package io.jauge.core.data.offline

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Situations the offline mode can simulate, to build and test every screen state without a server. */
enum class OfflineScenario {
    /** Everything works, with realistic data. */
    NORMAL,

    /** Every call fails as if the phone were offline. */
    NO_CONNECTION,

    /** Every call times out. */
    TIMEOUT,

    /** The server answers 503. */
    SERVER_UNAVAILABLE,

    /** The session has expired (401). */
    SESSION_EXPIRED,

    /** A free user without quota: new analyses and calm alerts fail with 402 (paywall). */
    QUOTA_REACHED,

    /** Screenshots and photos cannot be read (mockup 10). */
    UNREADABLE_OFFER,

    /** The server sends data that cannot be decoded. */
    INVALID_RESPONSE,

    /** Lists are empty (history, twins, shares, alerts) to show the empty states. */
    EMPTY,
}

/**
 * Holds the simulated scenario and the simulated latency of the offline mode. A debug menu (or a test) changes them
 * at runtime.
 *
 * @param initialScenario scenario at start up.
 * @param latencyMilliseconds simulated network delay range.
 */
class OfflineScenarioController(
    initialScenario: OfflineScenario = OfflineScenario.NORMAL,
    val latencyMilliseconds: LongRange = DEFAULT_LATENCY,
) {
    private val state = MutableStateFlow(initialScenario)

    /** Current scenario. */
    val scenario: StateFlow<OfflineScenario> = state.asStateFlow()

    /**
     * Changes the scenario.
     *
     * @param scenario the new scenario.
     */
    fun select(scenario: OfflineScenario) {
        state.value = scenario
    }

    /** Default values. */
    companion object {
        /** Realistic delay of a mobile network. */
        val DEFAULT_LATENCY: LongRange = 350L..900L

        /** No delay, for unit tests. */
        val NO_LATENCY: LongRange = 0L..0L
    }
}
