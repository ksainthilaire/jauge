package io.jauge.app.debug

import io.jauge.core.data.offline.OfflineScenario
import io.jauge.core.data.offline.OfflineScenarioController
import org.koin.mp.KoinPlatform

/**
 * Test hook of debug builds: selects a simulated situation of the offline mode (no connection, unreadable offer,
 * quota reached, empty lists...) so that every screen state can be reached on a real device or an emulator.
 *
 * Only the debug Android activity calls it (intent extra `offline_scenario`); release builds never do.
 */
object OfflineScenarioSwitch {
    /**
     * Selects the scenario named [scenarioName] (an [OfflineScenario] name, case insensitive).
     *
     * @param scenarioName name of the scenario, for example `UNREADABLE_OFFER`.
     * @return true when the scenario exists and was selected.
     */
    fun select(scenarioName: String): Boolean {
        val scenario = OfflineScenario.entries.firstOrNull { it.name.equals(scenarioName, ignoreCase = true) } ?: return false
        val controller = KoinPlatform.getKoinOrNull()?.getOrNull<OfflineScenarioController>() ?: return false
        controller.select(scenario)
        return true
    }
}
