package io.jauge.core.authentication

import org.koin.core.module.Module

/**
 * Small store for secrets of the session (Supabase access and refresh tokens, PKCE code verifier), encrypted at rest:
 * Android Keystore (AES-GCM key that never leaves the secure hardware) on Android, Keychain on iOS. Values are never
 * written in plain text, never logged and never included in backups.
 */
interface SecureStore {
    /**
     * Reads a value, or null when it is missing or cannot be decrypted (the value is then deleted).
     *
     * @param key name of the value.
     */
    fun read(key: String): String?

    /**
     * Writes a value, replacing the previous one.
     *
     * @param key name of the value.
     * @param value the secret to keep.
     */
    fun write(key: String, value: String)

    /**
     * Deletes a value. Does nothing when it is missing.
     *
     * @param key name of the value.
     */
    fun delete(key: String)
}

/** In memory [SecureStore], for tests: nothing is written to the disk. */
class InMemorySecureStore : SecureStore {
    private val values = mutableMapOf<String, String>()

    override fun read(key: String): String? = values[key]

    override fun write(key: String, value: String) {
        values[key] = value
    }

    override fun delete(key: String) {
        values.remove(key)
    }
}

/** Declares the [SecureStore] of the platform: Android Keystore on Android, Keychain on iOS. */
internal expect fun platformSecureStoreModule(): Module
