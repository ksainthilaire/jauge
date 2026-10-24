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
     * Accepts `http` or `https`, `www.`, a trailing slash, a query or a fragment.
     *
     * @param url the opened link.
     */
    fun analysisIdOrNull(url: String?): String? {
        val trimmed = url?.trim().orEmpty()
        val withoutScheme = when {
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed.substring(8)
            trimmed.startsWith("http://", ignoreCase = true) -> trimmed.substring(7)
            else -> return null
        }
        val withoutExtras = withoutScheme.substringBefore('#').substringBefore('?')
        val host = withoutExtras.substringBefore('/').substringBefore(':').lowercase()
        if (host !in hosts) return null
        val segments = withoutExtras.substringAfter('/', missingDelimiterValue = "").split('/').filter { it.isNotEmpty() }
        if (segments.size != 2 || segments[0] != "a") return null
        return segments[1].takeIf { identifier.matches(it) }
    }
}
