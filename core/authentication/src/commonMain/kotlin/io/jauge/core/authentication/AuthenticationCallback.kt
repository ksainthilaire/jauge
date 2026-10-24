package io.jauge.core.authentication

import io.jauge.core.domain.error.JaugeError

/**
 * Content of the sign in deep link sent back by Supabase (`io.jauge.app://login-callback?code=...`, or
 * `...?error=...&error_code=otp_expired` when the link is no longer valid). Parameters may be in the query or in the
 * fragment.
 */
sealed interface AuthenticationCallback {
    /**
     * PKCE authorization code to exchange for a session.
     *
     * @property code the code.
     */
    data class Code(val code: String) : AuthenticationCallback

    /**
     * Supabase refused the sign in.
     *
     * @property error the friendly error.
     */
    data class Failure(val error: JaugeError) : AuthenticationCallback

    /** Nothing usable in the link. */
    data object Unknown : AuthenticationCallback

    /** Parser of the callback. */
    companion object {
        /**
         * Reads a callback link.
         *
         * @param url the deep link.
         */
        fun parse(url: String): AuthenticationCallback {
            val parameters = parameters(url)
            parameters["code"]?.takeIf { it.isNotBlank() }?.let { return Code(it) }
            val errorCode = parameters["error_code"] ?: parameters["error"]
            return when {
                errorCode == null -> Unknown
                errorCode in expiredCodes -> Failure(JaugeError.SignInLinkExpired)
                errorCode == "access_denied" && parameters["error_code"] == null -> Failure(JaugeError.SignInCancelled)
                else -> Failure(JaugeError.SignInFailed)
            }
        }

        private val expiredCodes = setOf("otp_expired", "flow_state_expired", "flow_state_not_found", "bad_code_verifier")

        private fun parameters(url: String): Map<String, String> {
            val query = url.substringAfter('?', "").substringBefore('#')
            val fragment = url.substringAfter('#', "")
            return (query.split('&') + fragment.split('&'))
                .filter { it.contains('=') }
                .associate { pair -> pair.substringBefore('=') to decode(pair.substringAfter('=')) }
        }

        /** Percent decoding of a URL parameter (UTF-8), "+" read as a space. */
        private fun decode(value: String): String {
            val bytes = ArrayList<Byte>(value.length)
            var index = 0
            while (index < value.length) {
                val character = value[index]
                val code = if (character == '%' && index + 2 <= value.lastIndex) value.substring(index + 1, index + 3).toIntOrNull(HEXADECIMAL) else null
                when {
                    code != null -> {
                        bytes.add(code.toByte())
                        index += 3
                    }
                    character == '+' -> {
                        bytes.add(' '.code.toByte())
                        index++
                    }
                    else -> {
                        character.toString().encodeToByteArray().forEach { bytes.add(it) }
                        index++
                    }
                }
            }
            return bytes.toByteArray().decodeToString()
        }

        private const val HEXADECIMAL = 16
    }
}
