package io.jauge.core.common.configuration

import io.jauge.core.common.platform.Platform

/**
 * Runtime environment of the app, built once at start up from [BuildConfiguration] and the running platform.
 *
 * @property platform the operating system the app runs on.
 * @property isDebugBuild true for debug builds (enables developer tools such as the hidden offline switch).
 * @property apiBaseUrl base URL of the Jauge API.
 * @property isOfflineModeByDefault true when the app starts without calling the API (sample data). Always false in
 * release builds: the offline mode is a development tool only ([selectOfflineMode]).
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
            apiBaseUrl = selectApiBaseUrl(BuildConfiguration.API_BASE_URL, isDebugBuild),
            isOfflineModeByDefault = selectOfflineMode(BuildConfiguration.OFFLINE_MODE_SETTING, BuildConfiguration.API_BASE_URL, isDebugBuild),
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

        /** API of release builds, and of debug builds without a usable configured URL. */
        const val DEFAULT_API_BASE_URL: String = "https://api.jauge.io/"

        /**
         * Base URL of the API: always [DEFAULT_API_BASE_URL] in release builds; in debug builds the configured URL
         * (for example the URL of the Supabase function, `https://<project>.supabase.co/functions/v1/api/`) when it
         * is safe ([secureUrlOrEmpty]), otherwise [DEFAULT_API_BASE_URL].
         *
         * @param configuredUrl value of `jauge.api.baseUrl`, empty when not set.
         * @param isDebugBuild true for debug builds.
         */
        fun selectApiBaseUrl(configuredUrl: String, isDebugBuild: Boolean): String {
            if (!isDebugBuild) return DEFAULT_API_BASE_URL
            val url = secureUrlOrEmpty(configuredUrl, isDebugBuild = true)
            return if (url.isEmpty()) DEFAULT_API_BASE_URL else if (url.endsWith("/")) url else "$url/"
        }

        /**
         * Whether the app starts in offline mode (sample data). Release builds: never. Debug builds: `true` or
         * `false` when `jauge.api.offlineMode` says so; when it is not set, the real API is called as soon as an API
         * URL is configured, and the offline mode is kept otherwise.
         *
         * @param offlineModeSetting value of `jauge.api.offlineMode` (`true`, `false` or empty).
         * @param configuredUrl value of `jauge.api.baseUrl`, empty when not set.
         * @param isDebugBuild true for debug builds.
         */
        fun selectOfflineMode(offlineModeSetting: String, configuredUrl: String, isDebugBuild: Boolean): Boolean {
            if (!isDebugBuild) return false
            return when (offlineModeSetting.trim().lowercase()) {
                "true" -> true
                "false" -> false
                else -> secureUrlOrEmpty(configuredUrl, isDebugBuild = true).isEmpty()
            }
        }

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
