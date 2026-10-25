package io.jauge.core.domain.usecase

import io.jauge.core.domain.error.JaugeError
import io.jauge.core.domain.model.Analysis
import io.jauge.core.domain.model.AnalysisProgress
import io.jauge.core.domain.model.AnalysisSummary
import io.jauge.core.domain.model.ImageOrigin
import io.jauge.core.domain.model.OfferSource
import io.jauge.core.domain.model.Outcome
import io.jauge.core.domain.repository.AnalysisRepository
import io.jauge.core.domain.service.PersonalData
import io.jauge.core.domain.service.RecognizedText
import io.jauge.core.domain.service.TextRecognizer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlin.random.Random

/**
 * Analyzes an offer (mockups 02 and 03). Checks the input first: a pasted text that is not a web link, or an empty
 * manual entry, fails at once without calling the server.
 *
 * A screenshot or a photo is read on the phone by [textRecognizer] and only its text is sent
 * ([OfferSource.Text]): the image never leaves the phone. When nothing usable is read, the analysis fails with
 * [JaugeError.UnreadableOffer] (mockup 10) without calling the server, so the free analysis of the week is kept.
 *
 * Every text and manual entry is cleaned of personal data on the phone before it is sent ([PersonalData.scrub]):
 * e-mail addresses, phone numbers and names of recruiters become neutral markers.
 *
 * @param repository analyses repository.
 * @param textRecognizer reads the text of images on the phone.
 */
class AnalyzeOfferUseCase(
    private val repository: AnalysisRepository,
    private val textRecognizer: TextRecognizer,
) {

    /**
     * Starts the analysis.
     *
     * @param source link, image, text or manual entry.
     * @param requestKey key of this request ([AnalysisRequestKeys.newKey]); give the same key again when the user
     *   retries the same request, so that the server gives back the analysis already created.
     * @return the progress, ending with [AnalysisProgress.Completed] or [AnalysisProgress.Failed].
     */
    operator fun invoke(source: OfferSource, requestKey: String = AnalysisRequestKeys.newKey()): Flow<AnalysisProgress> = when (source) {
        is OfferSource.Link -> {
            val link = normalizeLink(source.url)
            if (link == null) flowOf(AnalysisProgress.Failed(JaugeError.InvalidLink)) else repository.analyze(OfferSource.Link(link), requestKey)
        }
        is OfferSource.Manual -> {
            if (source.jobTitle.isBlank() || source.city.isBlank()) {
                flowOf(AnalysisProgress.Failed(JaugeError.InvalidData))
            } else {
                val jobTitle = PersonalData.scrub(source.jobTitle.trim())
                val city = PersonalData.scrub(source.city.trim())
                repository.analyze(OfferSource.Manual(jobTitle, city), requestKey)
            }
        }
        is OfferSource.Text -> analyzeText(source.text, source.origin, requestKey)
        is OfferSource.Image -> {
            if (source.bytes.isEmpty()) {
                flowOf(AnalysisProgress.Failed(JaugeError.UnreadableOffer()))
            } else if (source.bytes.size > MAXIMUM_IMAGE_BYTES) {
                flowOf(AnalysisProgress.Failed(JaugeError.ImageTooLarge))
            } else {
                flow {
                    when (val recognized = textRecognizer.recognize(source.bytes, source.mimeType)) {
                        is Outcome.Failure -> emit(AnalysisProgress.Failed(JaugeError.UnreadableOffer()))
                        is Outcome.Success -> emitAll(analyzeText(recognized.value, source.origin, requestKey))
                    }
                }
            }
        }
    }

    private fun analyzeText(text: String, origin: ImageOrigin, requestKey: String): Flow<AnalysisProgress> {
        val cleaned = PersonalData.scrub(RecognizedText.clean(text)).take(RecognizedText.MAXIMUM_LENGTH).trim()
        return if (RecognizedText.isReadable(cleaned)) {
            repository.analyze(OfferSource.Text(cleaned, origin), requestKey)
        } else {
            flowOf(AnalysisProgress.Failed(JaugeError.UnreadableOffer()))
        }
    }

    /** Limits and helpers. */
    companion object {
        /** Largest image read on the phone: 10 MB (the pickers reduce images to about 2048 pixels). */
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
 * Keys of analysis requests, sent as the `Idempotency-Key` header of `POST /v1/analyses`. One key per tap: a retry of
 * the same request keeps it, so a lost answer never creates a second analysis nor uses the free analysis twice.
 */
object AnalysisRequestKeys {
    private const val ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"

    /** Length of a key (the API accepts 8 to 64 letters, digits, `-` and `_`). */
    const val LENGTH = 32

    /**
     * A new random key.
     *
     * @param random source of randomness.
     */
    fun newKey(random: Random = Random.Default): String = buildString(LENGTH) {
        repeat(LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
    }

    /**
     * True when retrying after [error] may reach an analysis the server already created with the same key: the
     * answer was lost (no connection, timeout) or the server was busy. Any other failure starts a new request.
     *
     * @param error the failure of the previous attempt.
     */
    fun keepsKeyOnRetry(error: JaugeError): Boolean = when (error) {
        JaugeError.NoConnection, JaugeError.Timeout, JaugeError.ServerUnavailable, JaugeError.TooManyRequests -> true
        else -> false
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

/**
 * Favorite analyses (star on the verdict 04 and on the lists of 02 and "Tout voir").
 *
 * @param repository analyses repository.
 */
class FavoriteAnalysisUseCase(private val repository: AnalysisRepository) {
    /**
     * Whether one analysis is a favorite, updated at once after every change.
     *
     * @param analysisId identifier of the analysis.
     */
    fun observe(analysisId: String): Flow<Boolean> =
        repository.observeFavoriteIds().map { analysisId in it }.distinctUntilChanged()

    /**
     * Adds or removes the star. Optimistic: the star changes at once and comes back when the server refuses.
     *
     * @param analysisId identifier of the analysis.
     * @param isFavorite true to add the star, false to remove it.
     */
    suspend operator fun invoke(analysisId: String, isFavorite: Boolean): Outcome<Unit> =
        if (analysisId.isBlank()) Outcome.Failure(JaugeError.NotFound) else repository.setFavorite(analysisId, isFavorite)
}

/** Filter of the list of analyses ("Tout voir"). */
enum class AnalysisFilter {
    /** Every analysis. */
    ALL,

    /** Only the favorites. */
    FAVORITES,
    ;

    /**
     * Applies the filter.
     *
     * @param analyses analyses to filter, order kept.
     */
    fun apply(analyses: List<AnalysisSummary>): List<AnalysisSummary> = when (this) {
        ALL -> analyses
        FAVORITES -> analyses.filter { it.isFavorite }
    }
}
