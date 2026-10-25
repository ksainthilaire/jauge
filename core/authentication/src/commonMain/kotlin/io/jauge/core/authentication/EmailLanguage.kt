package io.jauge.core.authentication

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Language of the emails sent by Supabase Auth (sign in link). Supabase keeps one template per email type, so the
 * template `supabase/templates/magic_link.html` picks French or English with `{{ if eq .Data.locale "en" }}`, where
 * `.Data` is the user metadata. The app sends the language in the metadata of the sign in request, French by default.
 */
object EmailLanguage {

    /** Key of the language in the Supabase user metadata, read by the email templates as `.Data.locale`. */
    const val METADATA_KEY: String = "locale"

    /** French, the default language of Jauge. */
    const val FRENCH: String = "fr"

    /** English. */
    const val ENGLISH: String = "en"

    /**
     * Maps a language tag of the device (for example `en-GB`, `fr-FR`, `de`) to a language of the emails: English
     * for every English tag, French for everything else (the app only exists in French and English).
     *
     * @param languageTag language tag of the device, or null when unknown.
     */
    fun fromLanguageTag(languageTag: String?): String {
        val normalized = languageTag?.trim()?.lowercase().orEmpty()
        return if (normalized == ENGLISH || normalized.startsWith("$ENGLISH-") || normalized.startsWith("${ENGLISH}_")) ENGLISH else FRENCH
    }

    /**
     * User metadata sent with the sign in request: `{"locale": "<language>"}`.
     *
     * @param language language of the emails, [FRENCH] or [ENGLISH].
     */
    fun userData(language: String): JsonObject = buildJsonObject { put(METADATA_KEY, language) }
}

/** Language tag of the device (for example `fr-FR`), or null when the platform does not give one. */
internal expect fun platformLanguageTag(): String?
