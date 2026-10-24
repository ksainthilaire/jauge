package io.jauge.core.authentication

import io.github.jan.supabase.auth.CodeVerifierCache
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.serialization.json.Json

/**
 * Keeps the Supabase session (access and refresh tokens) in the encrypted [SecureStore] instead of the plain text
 * preferences used by default by supabase-kt.
 *
 * @param store encrypted store of the platform.
 */
class SecureSessionManager(private val store: SecureStore) : SessionManager {

    override suspend fun saveSession(session: UserSession) {
        store.write(SESSION_KEY, sessionJson.encodeToString(UserSession.serializer(), session))
    }

    /** Reads the saved session; throws when there is none (supabase-kt calls it through `loadSessionOrNull`). */
    override suspend fun loadSession(): UserSession {
        val saved = store.read(SESSION_KEY) ?: throw NoSuchElementException("No saved session")
        return runCatching { sessionJson.decodeFromString(UserSession.serializer(), saved) }
            .onFailure { store.delete(SESSION_KEY) }
            .getOrThrow()
    }

    override suspend fun deleteSession() {
        store.delete(SESSION_KEY)
    }

    private companion object {
        const val SESSION_KEY = "supabase.session"
        val sessionJson = Json { ignoreUnknownKeys = true }
    }
}

/**
 * Keeps the PKCE code verifier of a sign in in progress in the encrypted [SecureStore], so that only this
 * installation can exchange the code of the sign in link for a session.
 *
 * @param store encrypted store of the platform.
 */
class SecureCodeVerifierCache(private val store: SecureStore) : CodeVerifierCache {

    override suspend fun saveCodeVerifier(codeVerifier: String) {
        store.write(CODE_VERIFIER_KEY, codeVerifier)
    }

    override suspend fun loadCodeVerifier(): String? = store.read(CODE_VERIFIER_KEY)

    override suspend fun deleteCodeVerifier() {
        store.delete(CODE_VERIFIER_KEY)
    }

    private companion object {
        const val CODE_VERIFIER_KEY = "supabase.codeVerifier"
    }
}
