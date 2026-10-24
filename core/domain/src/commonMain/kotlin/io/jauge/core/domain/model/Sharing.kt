package io.jauge.core.domain.model

import kotlin.time.Instant

/** Formats of the share card (mockup 07). */
enum class ShareFormat {
    /** Vertical story card. */
    STORY,

    /** Square card. */
    SQUARE,

    /** Link only. */
    LINK,
}

/**
 * Options of a share.
 *
 * @property format format of the card.
 * @property hideCompany true to hide the company name.
 * @property showCalmTwin true to show the calm twin offer on the card.
 */
data class ShareOptions(
    val format: ShareFormat = ShareFormat.STORY,
    val hideCompany: Boolean = false,
    val showCalmTwin: Boolean = true,
)

/**
 * A shared analysis.
 *
 * @property id identifier of the share.
 * @property analysisId shared analysis.
 * @property url public link, `https://jauge.io/a/{analysisId}`.
 * @property imageUrl rendered card image, null for link only shares.
 * @property options options used.
 * @property createdAt creation time.
 */
data class Share(
    val id: String,
    val analysisId: String,
    val url: String,
    val imageUrl: String?,
    val options: ShareOptions,
    val createdAt: Instant,
)

/** Public links of the app. */
object ShareLinks {
    /** Base of the public analysis links. */
    const val ANALYSIS_LINK_BASE = "https://jauge.io/a/"

    /**
     * Public link of an analysis.
     *
     * @param analysisId identifier of the analysis.
     */
    fun forAnalysis(analysisId: String): String = ANALYSIS_LINK_BASE + analysisId.trim()
}
