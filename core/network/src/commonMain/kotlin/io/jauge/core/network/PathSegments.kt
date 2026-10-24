package io.jauge.core.network

/**
 * Percent encodes [value] for use as one path segment of an API URL: every byte other than letters, digits, `-`, `_`
 * and `~` is encoded, including `/`, `?`, `#` and `.`, so that an identifier can never change the path (`../account`)
 * or add a query.
 *
 * @param value an identifier (analysis, alert, share).
 */
fun pathSegment(value: String): String = buildString {
    value.encodeToByteArray().forEach { byte ->
        val character = byte.toInt().toChar()
        if (byte >= 0 && (character.isLetterOrDigit() || character == '-' || character == '_' || character == '~')) {
            append(character)
        } else {
            append('%')
            append(HEXADECIMAL_DIGITS[(byte.toInt() shr 4) and 0x0F])
            append(HEXADECIMAL_DIGITS[byte.toInt() and 0x0F])
        }
    }
}

private const val HEXADECIMAL_DIGITS = "0123456789ABCDEF"
