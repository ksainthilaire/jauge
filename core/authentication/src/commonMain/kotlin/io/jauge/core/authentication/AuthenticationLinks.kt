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
     * True when [url] is exactly the sign in callback of the app: `io.jauge.app://login-callback`, optionally followed
     * by a path, a query or a fragment. A longer host such as `io.jauge.app://login-callback.evil` is refused.
     *
     * @param url a deep link opened by the system.
     */
    fun isSignInLink(url: String): Boolean {
        if (url.length > MAXIMUM_LINK_LENGTH || !url.startsWith(REDIRECT_URL, ignoreCase = true)) return false
        val rest = url.substring(REDIRECT_URL.length)
        return rest.isEmpty() || rest[0] == '/' || rest[0] == '?' || rest[0] == '#'
    }

    private const val MAXIMUM_LINK_LENGTH = 4096
}
