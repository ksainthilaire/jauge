package io.jauge.core.authentication

/**
 * Deep link used by Supabase Auth to come back to the app after Apple, Google or an email link:
 * `io.jauge.app://login-callback`. It must be listed in the "Redirect URLs" of the Supabase project
 * (`docs/supabase.md`), in the Android manifest and in the iOS `CFBundleURLTypes`.
 */
object AuthenticationLinks {
    /** URL scheme of the app. */
    const val SCHEME = "io.jauge.app"

    /** Host of the sign in callback. */
    const val HOST = "login-callback"

    /** Full redirect URL given to Supabase. */
    const val REDIRECT_URL = "$SCHEME://$HOST"

    /**
     * True when [url] is the sign in callback of the app.
     *
     * @param url a deep link opened by the system.
     */
    fun isSignInLink(url: String): Boolean = url.startsWith(REDIRECT_URL)
}
