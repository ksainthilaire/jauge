package io.jauge.core.domain.model

/** What the user gives Jauge to analyze (mockup 02). */
sealed interface OfferSource {
    /**
     * A link to an offer.
     *
     * @property url the pasted link.
     */
    data class Link(val url: String) : OfferSource

    /**
     * A screenshot or a photo of an offer.
     *
     * @property bytes encoded image.
     * @property mimeType type of the image, for example `image/jpeg`.
     * @property origin screenshot or photo.
     */
    class Image(val bytes: ByteArray, val mimeType: String, val origin: ImageOrigin) : OfferSource

    /**
     * Text of a screenshot or a photo, read on the phone by `TextRecognizer`. This is what is sent to the API for
     * an [Image]: the image itself never leaves the phone.
     *
     * @property text the text read, already cleaned.
     * @property origin screenshot or photo.
     */
    data class Text(val text: String, val origin: ImageOrigin) : OfferSource

    /**
     * Job title and city typed by the user when the offer could not be read (mockup 10).
     *
     * @property jobTitle the job title.
     * @property city the city.
     */
    data class Manual(val jobTitle: String, val city: String) : OfferSource
}

/** Where an image comes from. */
enum class ImageOrigin {
    /** A screenshot picked from the gallery. */
    SCREENSHOT,

    /** A photo taken with the camera. */
    PHOTO,
}

/** Steps shown while an offer is read (mockup 03). */
enum class ReadingStepKind {
    /** Job title, place and contract recognized. */
    POSITION_AND_PLACE,

    /** Skills found. */
    SKILLS,

    /** Comparison with the offers of the region. */
    COMPARISON,

    /** Estimation of the applicants. */
    APPLICANTS,
}

/** State of a reading step. */
enum class ReadingStepStatus {
    /** Not started. */
    PENDING,

    /** Running. */
    RUNNING,

    /** Done. */
    DONE,
}

/**
 * One reading step.
 *
 * @property kind which step.
 * @property status its state.
 * @property detail what was found ("Chargé(e) de marketing digital · Lyon 3e · CDI"), if any.
 */
data class ReadingStep(
    val kind: ReadingStepKind,
    val status: ReadingStepStatus,
    val detail: String?,
)

/** Progress of an analysis, emitted by `AnalyzeOfferUseCase`. */
sealed interface AnalysisProgress {
    /**
     * The offer is being read.
     *
     * @property analysisId identifier of the analysis being built.
     * @property percent progress between 0 and 100.
     * @property steps state of each step.
     * @property publishedDaysAgo publication age found so far, if any ("il y a 2 jours").
     * @property hasDisplayedSalary whether the offer shows a salary, null while unknown.
     */
    data class Reading(
        val analysisId: String,
        val percent: Int,
        val steps: List<ReadingStep>,
        val publishedDaysAgo: Int?,
        val hasDisplayedSalary: Boolean?,
    ) : AnalysisProgress

    /**
     * The analysis is ready.
     *
     * @property analysis the result.
     */
    data class Completed(val analysis: Analysis) : AnalysisProgress

    /**
     * The analysis failed.
     *
     * @property error the reason, for example [io.jauge.core.domain.error.JaugeError.UnreadableOffer].
     */
    data class Failed(val error: io.jauge.core.domain.error.JaugeError) : AnalysisProgress
}
