package io.jauge.core.common.configuration

import io.jauge.core.common.platform.Platform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppEnvironmentSecurityTest {

    @Test
    fun httpsUrlsAreKept() {
        assertEquals("https://api.jauge.io/", AppEnvironment.secureUrlOrEmpty("https://api.jauge.io/", isDebugBuild = false))
        assertEquals("https://abc.supabase.co", AppEnvironment.secureUrlOrEmpty(" https://abc.supabase.co ", isDebugBuild = true))
    }

    @Test
    fun clearTextUrlsAreRefusedInRelease() {
        assertEquals("", AppEnvironment.secureUrlOrEmpty("http://api.jauge.io/", isDebugBuild = false))
        assertEquals("", AppEnvironment.secureUrlOrEmpty("http://10.0.2.2:54321", isDebugBuild = false))
    }

    @Test
    fun onlyLocalClearTextUrlsAreAllowedInDebug() {
        assertEquals("http://10.0.2.2:54321", AppEnvironment.secureUrlOrEmpty("http://10.0.2.2:54321", isDebugBuild = true))
        assertEquals("http://localhost/", AppEnvironment.secureUrlOrEmpty("http://localhost/", isDebugBuild = true))
        assertEquals("", AppEnvironment.secureUrlOrEmpty("http://localhost.evil.example/", isDebugBuild = true))
        assertEquals("", AppEnvironment.secureUrlOrEmpty("http://api.jauge.io/", isDebugBuild = true))
        assertEquals("", AppEnvironment.secureUrlOrEmpty("ftp://api.jauge.io/", isDebugBuild = true))
    }

    @Test
    fun simulatedServicesAndTheOfflineModeAreForDebugBuildsOnly() {
        val release = AppEnvironment.fromBuildConfiguration(Platform.ANDROID, isDebugBuild = false)
        assertFalse(release.isOfflineModeByDefault)
        assertFalse(release.canUseSimulatedServices)
        assertFalse(ApiModeController(release).canSwitchAtRuntime)
        assertTrue(release.apiBaseUrl.startsWith("https://"))

        val debug = AppEnvironment.fromBuildConfiguration(Platform.ANDROID, isDebugBuild = true)
        assertTrue(debug.canUseSimulatedServices)
        assertTrue(ApiModeController(debug).canSwitchAtRuntime)
    }

    @Test
    fun releaseBuildsAlwaysCallApiJaugeIo() {
        assertEquals("https://api.jauge.io/", AppEnvironment.selectApiBaseUrl("https://abc.supabase.co/functions/v1/api/", isDebugBuild = false))
        assertEquals("https://api.jauge.io/", AppEnvironment.selectApiBaseUrl("", isDebugBuild = false))
        assertFalse(AppEnvironment.selectOfflineMode("true", "", isDebugBuild = false))
    }

    @Test
    fun debugBuildsMayCallTheSupabaseFunction() {
        assertEquals(
            "https://abc.supabase.co/functions/v1/api/",
            AppEnvironment.selectApiBaseUrl("https://abc.supabase.co/functions/v1/api", isDebugBuild = true),
        )
        assertEquals("https://api.jauge.io/", AppEnvironment.selectApiBaseUrl("", isDebugBuild = true))
        assertEquals("https://api.jauge.io/", AppEnvironment.selectApiBaseUrl("http://evil.example/", isDebugBuild = true))
    }

    @Test
    fun aConfiguredApiTurnsTheOfflineModeOffUnlessAskedOtherwise() {
        val supabase = "https://abc.supabase.co/functions/v1/api/"
        assertFalse(AppEnvironment.selectOfflineMode("", supabase, isDebugBuild = true))
        assertTrue(AppEnvironment.selectOfflineMode("true", supabase, isDebugBuild = true))
        assertTrue(AppEnvironment.selectOfflineMode("", "", isDebugBuild = true), "no API configured: sample data")
        assertFalse(AppEnvironment.selectOfflineMode("false", "", isDebugBuild = true))
        assertTrue(AppEnvironment.selectOfflineMode("", "http://evil.example/", isDebugBuild = true), "refused URL: sample data")
    }
}
