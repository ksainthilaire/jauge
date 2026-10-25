package io.jauge.core.domain.service

import io.jauge.core.domain.model.Outcome

/**
 * Reads the text of a screenshot or a photo on the phone itself: ML Kit Text Recognition (model bundled with the app)
 * on Android, Apple Vision on iOS. The image never leaves the phone: only the text read here is sent to the API.
 *
 * Implementations never throw: a picture that cannot be decoded or read gives a failure, an image without text gives
 * an empty text.
 */
fun interface TextRecognizer {
    /**
     * Reads the text of an image.
     *
     * @param image encoded image (JPEG or PNG).
     * @param mimeType type of the image, for example `image/jpeg`.
     * @return the text, lines separated by `\n`, in reading order.
     */
    suspend fun recognize(image: ByteArray, mimeType: String): Outcome<String>
}

/** Rules for the text read from a capture before it is sent (limits of `POST /v1/analyses` with `type: "text"`). */
object RecognizedText {
    /** Shortest text the API accepts: below, the capture is treated as unreadable (mockup 10) without calling it. */
    const val MINIMUM_LENGTH = 40

    /** Longest text the API accepts: longer texts are cut. */
    const val MAXIMUM_LENGTH = 20_000

    private val spaces = Regex("[ \\t\\u00A0]+")
    private val emptyLines = Regex("\\n{3,}")

    /**
     * Cleans a recognized text: trims every line, merges repeated spaces and empty lines, cuts it to
     * [MAXIMUM_LENGTH] characters.
     *
     * @param text raw text from the recognizer.
     * @return the cleaned text, empty when nothing was read.
     */
    fun clean(text: String): String = text
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .lines()
        .joinToString("\n") { it.replace(spaces, " ").trim() }
        .replace(emptyLines, "\n\n")
        .trim()
        .take(MAXIMUM_LENGTH)
        .trim()

    /**
     * True when the text is long enough to describe an offer.
     *
     * @param cleanedText text returned by [clean].
     */
    fun isReadable(cleanedText: String): Boolean = cleanedText.count { it.isLetterOrDigit() } >= MINIMUM_LENGTH / 2 &&
        cleanedText.length >= MINIMUM_LENGTH
}
