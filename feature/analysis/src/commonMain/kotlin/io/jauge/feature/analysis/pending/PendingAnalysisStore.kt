package io.jauge.feature.analysis.pending

import io.jauge.core.domain.model.OfferSource
import kotlin.random.Random

/**
 * Keeps the offer to analyze between the screen that chose it (home, unreadable offer, manual entry) and the reading
 * screen. Routes only carry the request identifier, because an image is too large for a route.
 */
class PendingAnalysisStore {
    private val sources = mutableMapOf<String, OfferSource>()

    /**
     * Saves a source and returns its request identifier.
     *
     * @param source link, image or manual entry.
     */
    fun put(source: OfferSource): String {
        val requestId = "request-" + Random.nextLong(from = 1, until = Long.MAX_VALUE).toString(radix = 36)
        sources[requestId] = source
        return requestId
    }

    /**
     * The source of a request, null when it is unknown (the app was restarted).
     *
     * @param requestId identifier returned by [put].
     */
    fun get(requestId: String): OfferSource? = sources[requestId]
}
