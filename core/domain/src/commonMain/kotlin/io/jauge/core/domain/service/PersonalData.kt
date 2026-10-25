package io.jauge.core.domain.service

/**
 * Removes the personal data an offer may hold before its text leaves the phone: e-mail addresses, phone numbers
 * (French and international formats) and the names of people when they are found without doubt (after "Contact :",
 * "Recruteur :", a civility such as "Mme", or in a signature such as "Marie Dupont, Talent Acquisition"). Each one is
 * replaced by a neutral marker.
 *
 * What the analysis needs is always kept: job title, company, city, salary, skills, dates, offer references and links.
 * Regular expressions only: fast, offline, and the same rules as the API (`supabase/functions/api/src/text/personal_data.ts`).
 */
object PersonalData {
    /** Replaces an e-mail address. */
    const val EMAIL_MARKER = "[e-mail retiré]"

    /** Replaces a phone number. */
    const val PHONE_MARKER = "[téléphone retiré]"

    /** Replaces the name of a person. */
    const val NAME_MARKER = "[nom retiré]"

    private val FRENCH_SPEAKING_COUNTRY_CODES = setOf("33", "32", "41", "352", "377", "262", "590", "594", "596")

    private const val SEPARATOR = "[ .\\-\\u00A0\\u202F]"
    private const val WORD = "(?:\\p{Lu}\\.|\\p{Lu}[\\p{Ll}\\p{Lu}'’]*(?:-\\p{Lu}[\\p{Ll}\\p{Lu}'’]*)*)"
    private const val PARTICLE = "(?:[dD]e|[dD]u|[dD]es|[vV]an|[vV]on|[dD]er|[dD]en|[dD]i|[dD]a|[lL]e|[lL]a)"
    private const val CIVILITY = "(?:M\\.|Mr\\.?|Mme\\.?|Mlle\\.?|MME|MLLE|Madame|Monsieur|Mademoiselle|Mrs\\.?|Ms\\.?)"
    private const val NAME = "(?:($CIVILITY)[ \\t]+)?$WORD(?:[ \\t]+(?:$PARTICLE[ \\t]+){0,2}$WORD|[ \\t]+d['’]$WORD){0,3}"

    private val email = Regex("[A-Za-z0-9._%+'\\-]+@[A-Za-z0-9\\-]+(?:\\.[A-Za-z0-9\\-]+)*\\.[A-Za-z]{2,}")
    private val link = Regex("(?:https?://|www\\.)\\S+|[A-Za-z0-9\\-]+(?:\\.[A-Za-z0-9\\-]+)*\\.[A-Za-z]{2,}/\\S*", RegexOption.IGNORE_CASE)

    /** A French number: 10 digits starting with 0, in pairs with one same separator (or none). */
    private val frenchPhone = Regex(
        "(?<![\\p{L}\\p{N}+/_\\-])0[1-9]($SEPARATOR?)\\d{2}(?:\\1\\d{2}){3}(?![\\p{L}\\p{N}/_\\-]|[.,]\\d)",
    )

    /** An international number: `+` or `00` and a country code, then 6 to 12 digits with separators. */
    private val internationalPhone = Regex(
        "(?<![\\p{L}\\p{N}+])(?:\\+[1-9]\\d{0,2}|00[1-9]\\d{0,2})$SEPARATOR?(?:\\(\\d{1,4}\\)$SEPARATOR?)?\\d(?:$SEPARATOR?\\d){5,12}(?![\\p{N}])",
    )
    private val digitGroups = Regex("\\d+")
    private val amountAfter = Regex("^[ \\u00A0\\u202F]*(?:€|\\$|%|£|k\\b|k€|euros?\\b|eur\\b|usd\\b|chf\\b)", RegexOption.IGNORE_CASE)
    private val referenceBefore = Regex("(?<![\\p{L}])(?:r[ée]f[ée]?r?e?n?c?e?|n°|no\\.|num[ée]ro|siret|siren|id|code|offre)[^\\n]{0,12}$", RegexOption.IGNORE_CASE)
    private val phoneBefore = Regex("(?:t[ée]l|phone|portable|mobile|appel|joindre|fixe)[^\\n]{0,20}$", RegexOption.IGNORE_CASE)

    private val PARTICLES = setOf("de", "du", "des", "van", "von", "der", "den", "di", "da", "le", "la")
    private val nameAt = Regex(NAME)
    private val nameBeforeRole = Regex("$WORD(?:[ \\t]+(?:$PARTICLE[ \\t]+){0,2}$WORD|[ \\t]+d['’]$WORD){0,4}[ \\t]*[,|·\\-\\u2013\\u2014]")
    private val wordInName = Regex(WORD)
    private val civilityAnywhere = Regex("(?<![\\p{L}\\p{N}])$CIVILITY[ \\t]+(?=\\p{Lu})")
    private val label = Regex(
        "(?<![\\p{L}])(?:contact(?:ez|er)?|votre contact|personne [àa] contacter|interlocut(?:eur|rice)|r[ée]f[ée]rente?|" +
            "recruteu(?:r|se)|recruiter|charg[ée]e? (?:de|du) recrutement|responsable (?:du |des )?recrutement|responsable rh|" +
            "talent acquisition(?: manager| partner| specialist)?|hiring manager|posted by|publi[ée]e? par|auteur de l'offre|" +
            "job poster|aupr[èe]s de|s'adresser [àa]|[ée]crire [àa]|envoy(?:er|ez) (?:votre candidature|votre cv|vos candidatures) [àa])" +
            "[ \\t]*(?:[:\\-\\u2013\\u2014][ \\t]*)?",
        RegexOption.IGNORE_CASE,
    )
    private val labelAtEnd = Regex("(?:${label.pattern}|[ée]quipe de recrutement|hiring team)[ \\t]*:?[ \\t]*$", RegexOption.IGNORE_CASE)
    private val role = Regex(
        "^[ \\t·•|,\\-\\u2013\\u2014]*(?:talent acquisition|recruteu(?:r|se)|recruiter|recruitment|recrutement|" +
            "charg[ée]e? (?:de|du) recrutement|responsable (?:du |des )?recrutement|responsable rh|responsable des ressources humaines|" +
            "directeur des ressources humaines|directrice des ressources humaines|ressources humaines|human resources|" +
            "(?:rh|hr|drh)(?![\\p{L}])|people|hiring|job poster|auteur de l'offre|ceo(?![\\p{L}])|co-?fondat(?:eur|rice)|" +
            "fondat(?:eur|rice)|founder|co-?founder|head of (?:people|talent|recruitment|hr)|g[ée]rante?|assistante? rh|consultante? (?:en )?recrutement|" +
            "hr business partner|business partner rh)",
        RegexOption.IGNORE_CASE,
    )
    private val standaloneSuffix = Regex(
        "^[ \\t]*(?:[,|·•\\-\\u2013\\u2014][ \\t]*(.*))?$",
    )
    private val connectionDegree = Regex("^(?:1er|2e|3e|1st|2nd|3rd|\\d+e?)$", RegexOption.IGNORE_CASE)

    /**
     * Returns [text] without its personal data.
     *
     * @param text text of an offer (read on a capture, pasted or typed).
     * @return the same text with every e-mail address, phone number and safely found person name replaced by a
     *   marker; the rest is unchanged.
     */
    fun scrub(text: String): String {
        if (text.isBlank()) return text
        val withoutEmails = email.replace(text, EMAIL_MARKER)
        val withoutPhones = removePhones(withoutEmails)
        return removeNames(withoutPhones)
    }

    private fun removePhones(text: String): String {
        val international = replaceOutsideLinks(text, internationalPhone) { value, match ->
            if (looksLikeInternationalPhone(value, match)) PHONE_MARKER else null
        }
        return replaceOutsideLinks(international, frenchPhone) { value, match ->
            val separated = match.groupValues[1].isNotEmpty()
            val before = value.substring(maxOf(0, match.range.first - 40), match.range.first)
            if (!separated && referenceBefore.containsMatchIn(before) && !phoneBefore.containsMatchIn(before)) null else PHONE_MARKER
        }
    }

    private fun looksLikeInternationalPhone(text: String, match: MatchResult): Boolean {
        val groups = digitGroups.findAll(match.value).map { it.value }.toList()
        val digits = groups.sumOf { it.length }
        if (digits < 8 || digits > 15) return false
        val afterCountryCode = groups.drop(1)
        val frenchSpeakingNumber = groups.first().trimStart('0') in FRENCH_SPEAKING_COUNTRY_CODES && afterCountryCode.sumOf { it.length } == 9
        // "+25 000 000" or "00 500 000": thousands of an amount, not a phone number.
        if (!frenchSpeakingNumber && afterCountryCode.size >= 2 && afterCountryCode.all { it.length == 3 }) return false
        return !amountAfter.containsMatchIn(text.substring(match.range.last + 1))
    }

    private fun replaceOutsideLinks(text: String, regex: Regex, replacement: (String, MatchResult) -> String?): String {
        val links = link.findAll(text).map { it.range }.toList()
        return regex.replace(text) { match ->
            if (links.any { it.first <= match.range.last && match.range.first <= it.last }) {
                match.value
            } else {
                replacement(text, match) ?: match.value
            }
        }
    }

    private fun removeNames(text: String): String {
        val lines = text.split('\n')
        return lines.indices.joinToString("\n") { index -> removeNamesInLine(lines, index) }
    }

    private fun removeNamesInLine(lines: List<String>, index: Int): String {
        val line = lines[index]
        if (line.isBlank()) return line
        val ranges = mutableListOf<IntRange>()
        standaloneName(lines, index)?.let { ranges += it }
        label.findAll(line).forEach { found -> nameAt(line, found.range.last + 1)?.let { ranges += it } }
        civilityAnywhere.findAll(line).forEach { found -> nameAt(line, found.range.first)?.let { ranges += it } }
        nameBeforeRole.findAll(line).forEach { found -> signatureIn(line, found)?.let { ranges += it } }
        if (ranges.isEmpty()) return line
        val links = link.findAll(line).map { it.range }.toList()
        val kept = ranges
            .filter { range -> links.none { it.first <= range.last && range.first <= it.last } }
            .sortedBy { it.first }
        val merged = mutableListOf<IntRange>()
        kept.forEach { range ->
            val last = merged.lastOrNull()
            if (last != null && range.first <= last.last + 1) {
                merged[merged.lastIndex] = last.first..maxOf(last.last, range.last)
            } else {
                merged += range
            }
        }
        return merged.asReversed().fold(line) { result, range -> result.replaceRange(range, NAME_MARKER) }
    }

    /**
     * A line that is only a name, with a recruiting role on the same line ("Marie Dupont, Talent Acquisition") or on
     * the next lines, or under a "Contact" or "hiring team" title.
     */
    private fun standaloneName(lines: List<String>, index: Int): IntRange? {
        val line = lines[index]
        val start = line.indexOfFirst { !it.isWhitespace() }
        val name = nameAt(line, start) ?: return null
        val suffix = standaloneSuffix.matchEntire(line.substring(name.last + 1)) ?: return null
        val rest = suffix.groupValues[1].trim()
        val roleOnLine = rest.isNotEmpty() && role.containsMatchIn(rest)
        if (rest.isNotEmpty() && !roleOnLine && !connectionDegree.matches(rest)) return null
        val following = lines.drop(index + 1).filter { it.isNotBlank() }.filterNot { connectionDegree.matches(it.trim()) }.take(2)
        val previous = lines.take(index).lastOrNull { it.isNotBlank() }
        val inContext = roleOnLine ||
            following.any { role.containsMatchIn(it) } ||
            (previous != null && labelAtEnd.containsMatchIn(previous))
        return if (inContext) name else null
    }

    /**
     * "avec Marie Dupont, Talent Acquisition": a name followed by a separator and a recruiting role, inside a line.
     * [found] holds capitalized words and the separator; the name may start at any of these words.
     */
    private fun signatureIn(line: String, found: MatchResult): IntRange? {
        if (!role.containsMatchIn(line.substring(found.range.last + 1))) return null
        val wordsEnd = found.range.last - 1
        return wordInName.findAll(line.substring(0, found.range.last), found.range.first)
            .mapNotNull { word -> nameAt(line, word.range.first) }
            .firstOrNull { name -> line.substring(name.last + 1, wordsEnd + 1).isBlank() }
    }

    /**
     * The name of a person starting at [start] in [line], or null. Without a civility, the first word must be a known
     * first name followed by a last name, and the whole must not be a brand named after a person ("Yves Rocher").
     */
    private fun nameAt(line: String, start: Int): IntRange? {
        if (start < 0 || start >= line.length) return null
        val match = nameAt.find(line, start)?.takeIf { it.range.first == start } ?: return null
        val civility = match.groups[1]
        val wordsStart = civility?.let { it.range.last + 1 } ?: start
        val words = wordInName.findAll(line.substring(0, match.range.last + 1), wordsStart).toList()
        val limit = if (civility != null) 3 else 2
        val kept = mutableListOf<MatchResult>()
        for (word in words) {
            val folded = fold(word.value.trimEnd('.'))
            if (folded in NameWords.stopWords || kept.size == limit) break
            // A capitalized particle ("de La Fontaine", "Le Gall") belongs to the next word.
            if (folded in PARTICLES && word != words.last()) continue
            kept += word
        }
        if (kept.isEmpty()) return null
        if (civility == null) {
            if (kept.size < 2) return null
            val firstName = fold(kept.first().value.substringBefore('-'))
            if (firstName !in NameWords.firstNames) return null
        }
        val end = kept.last().range.last
        val fullName = fold(line.substring(wordsStart, end + 1))
        if (NameWords.brands.any { fullName == it }) return null
        return start..end
    }

    /** Lower case without accents, for comparisons with the word lists. */
    internal fun fold(text: String): String = buildString(text.length) {
        text.lowercase().forEach { character -> append(ACCENTS[character] ?: character) }
    }.replace('’', '\'').replace(Regex("\\s+"), " ")

    private val ACCENTS: Map<Char, Char> = buildMap {
        "àâäáãå".forEach { put(it, 'a') }
        "çć".forEach { put(it, 'c') }
        "éèêë".forEach { put(it, 'e') }
        "íìîï".forEach { put(it, 'i') }
        "ñ".forEach { put(it, 'n') }
        "óòôöõø".forEach { put(it, 'o') }
        "úùûü".forEach { put(it, 'u') }
        "ýÿ".forEach { put(it, 'y') }
    }
}
