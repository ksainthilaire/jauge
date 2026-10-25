package io.jauge.core.domain.model

/**
 * What public posts say about the company and the job (Reddit, X), summed up by the AI of the server
 * (`result.reputation` of the API). Absent when nothing was found.
 *
 * @property summary two sentences at most, in the language of the analysis.
 * @property tone overall tone of the posts.
 * @property mentionCount number of posts read.
 * @property sources sites where the posts were found.
 * @property links public posts the summary relies on (5 at most, no usernames); empty when the server sent none.
 */
data class Reputation(
    val summary: String,
    val tone: ReputationTone,
    val mentionCount: Int,
    val sources: List<ReputationSource>,
    val links: List<ReputationLink> = emptyList(),
)

/**
 * A public post read for the reputation.
 *
 * @property source site of the post.
 * @property url link of the post (`https` only).
 */
data class ReputationLink(val source: ReputationSource, val url: String)

/** Overall tone of the public posts about a company. */
enum class ReputationTone {
    /** Mostly good feedback. */
    POSITIVE,

    /** Good and bad feedback. */
    MIXED,

    /** Mostly bad feedback. */
    NEGATIVE,

    /** Too few or unclear posts. */
    UNKNOWN,
}

/**
 * A public site read for the reputation.
 *
 * @property searchUrlPrefix beginning of the public search page of the site; the encoded company name follows.
 */
enum class ReputationSource(val searchUrlPrefix: String) {
    /** Reddit. */
    REDDIT("https://www.reddit.com/search/?q="),

    /** X (formerly Twitter). */
    X("https://x.com/search?f=live&q="),
    ;

    /**
     * Public search page of the site for [query], opened in the browser from the reputation card.
     *
     * @param query what to look for, usually the company name.
     */
    fun searchUrl(query: String): String = searchUrlPrefix + encodeQueryComponent(query.trim())

    private companion object {
        private const val HEXADECIMAL_DIGITS = "0123456789ABCDEF"

        /** Percent encodes [value] as UTF-8 for a query string (unreserved characters kept, space as `%20`). */
        fun encodeQueryComponent(value: String): String = buildString {
            value.encodeToByteArray().forEach { byte ->
                val code = byte.toInt() and 0xFF
                val character = code.toChar()
                if (code < 0x80 && (character.isLetterOrDigit() || character in "-._~")) {
                    append(character)
                } else {
                    append('%').append(HEXADECIMAL_DIGITS[code shr 4]).append(HEXADECIMAL_DIGITS[code and 0x0F])
                }
            }
        }
    }
}
