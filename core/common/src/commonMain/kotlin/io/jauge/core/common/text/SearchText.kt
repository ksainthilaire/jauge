package io.jauge.core.common.text

/**
 * Text search shared by the lists of the app and by the offline mode: the search ignores case, accents, ligatures and
 * the marks of inclusive writing, so that "chargee" finds "Chargé·e" and "ecully" finds "Écully".
 */
object SearchText {

    /** Lists up to this size hide their search field so that they stay uncluttered; longer lists show it. */
    const val LIST_SIZE_WITHOUT_SEARCH = 6

    /** Delay after the last key before the list is filtered, in milliseconds (instant to the eye, calm while typing). */
    const val TYPING_DELAY_MILLISECONDS = 250L

    /**
     * Folds [text] for comparison: lower case, accents removed (precomposed letters and combining marks), ligatures
     * spelled out ("œ" becomes "oe"), middle dots of inclusive writing removed and blanks collapsed.
     *
     * @param text text to fold.
     * @return the folded text.
     */
    fun normalize(text: String): String {
        val builder = StringBuilder(text.length)
        for (character in text.lowercase()) {
            when {
                character in COMBINING_MARKS -> Unit
                character in IGNORED_MARKS -> Unit
                character.isWhitespace() -> if (builder.isNotEmpty() && builder.last() != ' ') builder.append(' ')
                else -> builder.append(FOLDED_LETTERS[character] ?: character.toString())
            }
        }
        return builder.toString().trim()
    }

    /**
     * True when every word of [query] appears in one of [fields] (in any order), accents and case ignored. A blank
     * query matches everything.
     *
     * @param query text typed by the user.
     * @param fields searchable texts of one element (job title, company, city); null fields are skipped.
     * @return true when the element should stay in the list.
     */
    fun matches(query: String, vararg fields: String?): Boolean {
        val words = normalize(query).split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return true
        val haystack = fields.filterNotNull().joinToString(" ") { normalize(it) }
        return words.all { word -> haystack.contains(word) }
    }

    /** Combining diacritical marks, present when a text arrives decomposed (NFD). */
    private val COMBINING_MARKS = '̀'..'ͯ'

    /** Marks of inclusive writing and soft separators, removed so that "chargé·e" reads as "chargee". */
    private val IGNORED_MARKS = setOf('\u00B7', '\u2027', '\u22C5', '\u2022', '\u00AD')

    /** Accented and special Latin letters with their plain spelling. */
    private val FOLDED_LETTERS: Map<Char, String> = buildMap {
        fun fold(letters: String, plain: String) = letters.forEach { put(it, plain) }
        fold("àáâãäåāăą", "a")
        fold("çćĉċč", "c")
        fold("ďđ", "d")
        fold("èéêëēĕėęě", "e")
        fold("ĝğġģ", "g")
        fold("ĥħ", "h")
        fold("ìíîïĩīĭįı", "i")
        fold("ĵ", "j")
        fold("ķ", "k")
        fold("ĺļľŀł", "l")
        fold("ñńņňŉ", "n")
        fold("òóôõöøōŏő", "o")
        fold("ŕŗř", "r")
        fold("śŝşš", "s")
        fold("ţťŧ", "t")
        fold("ùúûüũūŭůűų", "u")
        fold("ŵ", "w")
        fold("ýÿŷ", "y")
        fold("źżž", "z")
        put('œ', "oe")
        put('æ', "ae")
        put('ß', "ss")
        put('’', "'")
    }
}
