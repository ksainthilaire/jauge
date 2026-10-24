package io.jauge.core.domain.usecase

import io.jauge.core.domain.error.JaugeError
import io.jauge.core.domain.model.Analysis
import io.jauge.core.domain.model.AnalysisProgress
import io.jauge.core.domain.model.AnalysisSummary
import io.jauge.core.domain.model.OfferSource
import io.jauge.core.domain.model.Outcome
import io.jauge.core.domain.repository.AnalysisRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Analyzes an offer (mockups 02 and 03). Checks the input first: a pasted text that is not a web link, or an empty
 * manual entry, fails at once without calling the server.
 *
 * @param repository analyses repository.
 */
class AnalyzeOfferUseCase(private val repository: AnalysisRepository) {

    /**
     * Starts the analysis.
     *
     * @param source link, image or manual entry.
     * @return the progress, ending with [AnalysisProgress.Completed] or [AnalysisProgress.Failed].
     */
    operator fun invoke(source: OfferSource): Flow<AnalysisProgress> = when (source) {
        is OfferSource.Link -> {
            val link = normalizeLink(source.url)
            if (link == null) flowOf(AnalysisProgress.Failed(JaugeError.InvalidLink)) else repository.analyze(OfferSource.Link(link))
        }
        is OfferSource.Manual -> {
            if (source.jobTitle.isBlank() || source.city.isBlank()) {
                flowOf(AnalysisProgress.Failed(JaugeError.InvalidData))
            } else {
                repository.analyze(OfferSource.Manual(source.jobTitle.trim(), source.city.trim()))
            }
        }
        is OfferSource.Image -> {
            if (source.bytes.isEmpty()) {
                flowOf(AnalysisProgress.Failed(JaugeError.UnreadableOffer()))
            } else if (source.bytes.size > MAXIMUM_IMAGE_BYTES) {
                flowOf(AnalysisProgress.Failed(JaugeError.ImageTooLarge))
            } else {
                repository.analyze(source)
            }
        }
    }

    /** Limits and helpers. */
    companion object {
        /** Largest image accepted by the API: 10 MB. */
        const val MAXIMUM_IMAGE_BYTES = 10 * 1024 * 1024

        private val webAddress = Regex("""^(https?://)?([\p{L}\p{N}-]+\.)+\p{L}{2,}(/\S*)?$""", RegexOption.IGNORE_CASE)

        /**
         * Cleans a pasted link: trims it, adds `https://` when missing and checks that it looks like a web address.
         *
         * @param text the pasted text.
         * @return the link, or null when the text is not a link.
         */
        fun normalizeLink(text: String): String? {
            val trimmed = text.trim()
            if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() } || !webAddress.matches(trimmed)) return null
            return if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
                trimmed
            } else {
                "https://$trimmed"
            }
        }
    }
}

/**
 * Analyses of the user (mockup 02, "Mes offres jaugées").
 *
 * @param repository analyses repository.
 */
class GetAnalysisHistoryUseCase(private val repository: AnalysisRepository) {
    /** Observed list, newest first. */
    operator fun invoke(): Flow<List<AnalysisSummary>> = repository.observeHistory()

    /** Reloads the list from the server (pull to refresh). */
    suspend fun refresh(): Outcome<Unit> = repository.refreshHistory()
}

/**
 * One analysis (mockup 04).
 *
 * @param repository analyses repository.
 */
class GetAnalysisUseCase(private val repository: AnalysisRepository) {
    /**
     * Loads the analysis.
     *
     * @param analysisId identifier of the analysis.
     */
    suspend operator fun invoke(analysisId: String): Outcome<Analysis> =
        if (analysisId.isBlank()) Outcome.Failure(JaugeError.NotFound) else repository.getAnalysis(analysisId)
}

/**
 * Deletes an analysis.
 *
 * @param repository analyses repository.
 */
class DeleteAnalysisUseCase(private val repository: AnalysisRepository) {
    /**
     * Deletes the analysis.
     *
     * @param analysisId identifier of the analysis.
     */
    suspend operator fun invoke(analysisId: String): Outcome<Unit> = repository.deleteAnalysis(analysisId)
}
