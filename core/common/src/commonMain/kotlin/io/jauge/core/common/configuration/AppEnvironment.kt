package io.jauge.core.common.configuration

import io.jauge.core.common.platform.Platform

/**
 * Runtime environment of the app, built once at start up from [BuildConfiguration] and the running platform.
 *
 * @property platform the operating system the app runs on.
 * @property isDebugBuild true for debug builds (enables developer tools such as the hidden offline switch).
 * @property apiBaseUrl base URL of the Jauge API.
 * @property isOfflineModeByDefault true when the app starts without calling the API (sample data). Always false in
 * release builds: the offline mode is a development tool only.
 * @property supabaseUrl Supabase project URL, empty when notifications are not configured.
 * @property supabasePublishableKey Supabase publishable key, empty when not configured.
 * @property revenueCatApiKey RevenueCat public SDK key used by this build, empty when purchases are disabled. Debug
 * builds use the Test Store key when it is set, release builds use the store key of the platform (`goog_`, `appl_`).
 */
data class AppEnvironment(
    val platform: Platform,
    val isDebugBuild: Boolean,
    val apiBaseUrl: String,
    val isOfflineModeByDefault: Boolean,
    val supabaseUrl: String,
    val supabasePublishableKey: String,
    val revenueCatApiKey: String,
) {
    /** True when Supabase (notifications and realtime alerts) can be used. */
    val isSupabaseConfigured: Boolean
        get() = supabaseUrl.isNotBlank() && supabasePublishableKey.isNotBlank()

    /** True when the simulated services (offline data, simulated sign in) may be used: debug builds only. */
    val canUseSimulatedServices: Boolean
        get() = isDebugBuild

    /** True when in app purchases can be used. */
    val isBillingConfigured: Boolean
        get() = revenueCatApiKey.isNotBlank()

    /** Factory for the environment of the running build. */
    companion object {
        /**
         * Builds the environment from the generated [BuildConfiguration].
         *
         * @param platform the running platform.
         * @param isDebugBuild true for debug builds.
         */
        fun fromBuildConfiguration(platform: Platform, isDebugBuild: Boolean): AppEnvironment = AppEnvironment(
            platform = platform,
            isDebugBuild = isDebugBuild,
            apiBaseUrl = secureUrlOrEmpty(BuildConfiguration.API_BASE_URL, isDebugBuild).ifEmpty { DEFAULT_API_BASE_URL },
            isOfflineModeByDefault = BuildConfiguration.OFFLINE_MODE && isDebugBuild,
            supabaseUrl = secureUrlOrEmpty(BuildConfiguration.SUPABASE_URL, isDebugBuild),
            supabasePublishableKey = BuildConfiguration.SUPABASE_PUBLISHABLE_KEY,
            revenueCatApiKey = selectRevenueCatApiKey(
                platform = platform,
                isDebugBuild = isDebugBuild,
                googleApiKey = BuildConfiguration.REVENUECAT_GOOGLE_API_KEY,
                appleApiKey = BuildConfiguration.REVENUECAT_APPLE_API_KEY,
                testStoreApiKey = BuildConfiguration.REVENUECAT_TEST_STORE_API_KEY,
            ),
        )

        /** API used when the configured base URL is refused. */
        const val DEFAULT_API_BASE_URL: String = "https://api.jauge.io/"

        /**
         * Returns [url] when it may be used by this build: `https` always, `http` only in debug builds and only for
         * the local development hosts (`localhost`, `127.0.0.1`, `10.0.2.2` of the Android emulator). Any other
         * value gives an empty string, which disables the service instead of sending data in clear text.
         *
         * @param url the configured URL.
         * @param isDebugBuild true for debug builds.
         */
        fun secureUrlOrEmpty(url: String, isDebugBuild: Boolean): String {
            val trimmed = url.trim()
            return when {
                trimmed.startsWith("https://", ignoreCase = true) -> trimmed
                isDebugBuild && localDevelopmentUrl.matches(trimmed) -> trimmed
                else -> ""
            }
        }

        private val localDevelopmentUrl = Regex("^http://(localhost|127\\.0\\.0\\.1|10\\.0\\.2\\.2)(:[0-9]{1,5})?(/.*)?$", RegexOption.IGNORE_CASE)

        /**
         * Picks the RevenueCat key: the Test Store key in debug builds when it is set, otherwise the store key of
         * the platform. Returns an empty string when no key applies (purchases disabled).
         *
         * @param platform the running platform.
         * @param isDebugBuild true for debug builds.
         * @param googleApiKey production key for Google Play (`goog_`).
         * @param appleApiKey production key for the App Store (`appl_`).
         * @param testStoreApiKey Test Store key (`test_`), debug builds only.
         */
        fun selectRevenueCatApiKey(
            platform: Platform,
            isDebugBuild: Boolean,
            googleApiKey: String,
            appleApiKey: String,
            testStoreApiKey: String,
        ): String = when {
            isDebugBuild && testStoreApiKey.isNotBlank() -> testStoreApiKey
            platform == Platform.ANDROID -> googleApiKey
            else -> appleApiKey
        }
    }
}
