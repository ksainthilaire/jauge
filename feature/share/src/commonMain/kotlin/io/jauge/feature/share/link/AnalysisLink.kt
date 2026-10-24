package io.jauge.feature.share.link

/**
 * Reads the public links of analyses, `https://jauge.io/a/{analysisId}`, opened from outside the app (Android App
 * Links, iOS Universal Links).
 */
object AnalysisLink {
    private val hosts = setOf("jauge.io", "www.jauge.io")
    private val identifier = Regex("^[A-Za-z0-9_-]{1,64}$")

    /**
     * Identifier of the analysis of a public link, or null when the link is not an analysis link.
     * Only `https` links of `jauge.io` or `www.jauge.io` are accepted, without user information or port; a trailing
     * slash, a query or a fragment are allowed. The identifier must match `[A-Za-z0-9_-]{1,64}` (no encoded
     * characters), so it can be used as a path segment of the API as is.
     *
     * @param url the opened link.
     */
    fun analysisIdOrNull(url: String?): String? {
        val trimmed = url?.trim().orEmpty()
        if (trimmed.length > MAXIMUM_LINK_LENGTH || !trimmed.startsWith(SCHEME, ignoreCase = true)) return null
        val withoutExtras = trimmed.substring(SCHEME.length).substringBefore('#').substringBefore('?')
        val authority = withoutExtras.substringBefore('/')
        if (authority.lowercase() !in hosts) return null
        val segments = withoutExtras.substringAfter('/', missingDelimiterValue = "").split('/').filter { it.isNotEmpty() }
        if (segments.size != 2 || segments[0] != "a") return null
        return segments[1].takeIf { identifier.matches(it) }
    }

    private const val SCHEME = "https://"
    private const val MAXIMUM_LINK_LENGTH = 2048
}
