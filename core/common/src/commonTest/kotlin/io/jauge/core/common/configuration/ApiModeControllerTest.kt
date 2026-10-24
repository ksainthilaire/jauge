package io.jauge.core.common.configuration

import io.jauge.core.common.platform.Platform
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApiModeControllerTest {

    private fun environment(isDebugBuild: Boolean, offline: Boolean) = AppEnvironment(
        platform = Platform.ANDROID,
        isDebugBuild = isDebugBuild,
        apiBaseUrl = "https://api.jauge.io/",
        isOfflineModeByDefault = offline,
        supabaseUrl = "",
        supabasePublishableKey = "",
        revenueCatApiKey = "",
    )

    @Test
    fun startsWithTheBuildValue() {
        assertTrue(ApiModeController(environment(isDebugBuild = false, offline = true)).isOfflineMode.value)
        assertFalse(ApiModeController(environment(isDebugBuild = false, offline = false)).isOfflineMode.value)
    }

    @Test
    fun debugBuildsCanToggle() {
        val controller = ApiModeController(environment(isDebugBuild = true, offline = true))
        controller.toggleOfflineMode()
        assertFalse(controller.isOfflineMode.value)
    }

    @Test
    fun releaseBuildsIgnoreTheSwitch() {
        val controller = ApiModeController(environment(isDebugBuild = false, offline = true))
        controller.toggleOfflineMode()
        assertTrue(controller.isOfflineMode.value)
    }

    @Test
    fun servicesAreDisabledWithoutKeys() {
        val environment = environment(isDebugBuild = false, offline = true)
        assertFalse(environment.isBillingConfigured)
        assertFalse(environment.isSupabaseConfigured)
    }

    @Test
    fun debugBuildsPreferTheTestStoreKey() {
        val key = AppEnvironment.selectRevenueCatApiKey(Platform.IOS, true, "goog_a", "appl_b", "test_c")
        kotlin.test.assertEquals("test_c", key)
        val release = AppEnvironment.selectRevenueCatApiKey(Platform.IOS, false, "goog_a", "appl_b", "test_c")
        kotlin.test.assertEquals("appl_b", release)
        val android = AppEnvironment.selectRevenueCatApiKey(Platform.ANDROID, true, "goog_a", "appl_b", "")
        kotlin.test.assertEquals("goog_a", android)
    }
}
