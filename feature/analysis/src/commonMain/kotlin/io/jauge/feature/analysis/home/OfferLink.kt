package io.jauge.feature.analysis.home

import io.jauge.core.domain.usecase.AnalyzeOfferUseCase

/** What the link field holds, as shown under it on the home screen. */
sealed interface OfferLinkStatus {
    /** Nothing typed yet. */
    data object Empty : OfferLinkStatus

    /** Some text, but not a web address yet: "Jauger" stays off. */
    data object Incomplete : OfferLinkStatus

    /**
     * A web address that can be gauged.
     *
     * @property link the cleaned link, with `https://`.
     * @property host the site, without `www.` ("emplois-rhone.fr").
     * @property jobBoardName the name of a known job site ("Indeed"), null for another site.
     */
    data class Recognized(val link: String, val host: String, val jobBoardName: String?) : OfferLinkStatus
}

/** Reads a typed or pasted text as an offer link and names the job sites people use the most in France. */
object OfferLinkRecognizer {
    private val jobBoards = listOf(
        "welcometothejungle.com" to "Welcome to the Jungle",
        "indeed.com" to "Indeed",
        "indeed.fr" to "Indeed",
        "linkedin.com" to "LinkedIn",
        "francetravail.fr" to "France Travail",
        "pole-emploi.fr" to "France Travail",
        "hellowork.com" to "HelloWork",
        "apec.fr" to "Apec",
        "cadremploi.fr" to "Cadremploi",
        "glassdoor.fr" to "Glassdoor",
        "glassdoor.com" to "Glassdoor",
        "monster.fr" to "Monster",
        "jobteaser.com" to "JobTeaser",
        "meteojob.com" to "Meteojob",
        "leboncoin.fr" to "leboncoin",
        "malt.fr" to "Malt",
    )

    /**
     * Status of [text].
     *
     * @param text content of the link field.
     * @return [OfferLinkStatus.Empty], [OfferLinkStatus.Incomplete] or [OfferLinkStatus.Recognized].
     */
    fun recognize(text: String): OfferLinkStatus {
        if (text.isBlank()) return OfferLinkStatus.Empty
        val link = AnalyzeOfferUseCase.normalizeLink(text) ?: return OfferLinkStatus.Incomplete
        val host = hostOf(link)
        return OfferLinkStatus.Recognized(link = link, host = host, jobBoardName = jobBoardNameOf(host))
    }

    /**
     * Site of a cleaned link, lower case and without the `www.` or `m.` prefix.
     *
     * @param link a link starting with `http://` or `https://`.
     */
    fun hostOf(link: String): String = link
        .substringAfter("://")
        .substringBefore('/')
        .substringBefore('?')
        .substringBefore('#')
        .substringBefore(':')
        .lowercase()
        .removePrefix("www.")
        .removePrefix("m.")

    /**
     * Name of a known job site, also for its sub domains ("fr.indeed.com").
     *
     * @param host the site, as given by [hostOf].
     * @return the name, null for another site.
     */
    fun jobBoardNameOf(host: String): String? =
        jobBoards.firstOrNull { (domain, _) -> host == domain || host.endsWith(".$domain") }?.second
}
