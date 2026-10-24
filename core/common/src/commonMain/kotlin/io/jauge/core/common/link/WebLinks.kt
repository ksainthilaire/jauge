package io.jauge.core.common.link

/**
 * Policy for the links the app opens in another app (browser, store, mail). Links that come from the network (twin
 * offers of the API, subscription management page of the store) are only opened when they are plain `https` web
 * pages, so that a wrong or hostile value can never open `javascript:`, `intent:`, `file:` or a custom scheme.
 */
object WebLinks {
    private const val SCHEME = "https://"
    private const val MAXIMUM_LENGTH = 2048
    private val forbiddenCharacters = Regex("[\\s\\\\<>\"{}|^`]")

    /**
     * True when [url] is a `https` link to a web page with a host and no user information.
     *
     * @param url the link to check.
     */
    fun isSafeWebLink(url: String?): Boolean {
        if (url.isNullOrEmpty() || url.length > MAXIMUM_LENGTH) return false
        if (!url.startsWith(SCHEME, ignoreCase = true) || forbiddenCharacters.containsMatchIn(url)) return false
        val authority = url.substring(SCHEME.length).substringBefore('/').substringBefore('?').substringBefore('#')
        if (authority.isEmpty() || authority.contains('@')) return false
        val host = authority.substringBefore(':')
        return host.isNotEmpty() && host.all { it.isLetterOrDigit() || it == '.' || it == '-' }
    }

    /**
     * Returns [url] when it is a safe web link, null otherwise.
     *
     * @param url the link to check.
     */
    fun safeWebLinkOrNull(url: String?): String? = url?.trim()?.takeIf { isSafeWebLink(it) }
}
