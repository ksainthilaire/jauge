package io.jauge.core.data.preferences

import org.koin.core.module.Module

/** Small key value storage kept on the device (SharedPreferences on Android, NSUserDefaults on iOS). */
interface KeyValueStore {
    /**
     * Reads a text value.
     *
     * @param key the key.
     * @return the value, or null when absent.
     */
    fun getString(key: String): String?

    /**
     * Writes a text value, or removes it when [value] is null.
     *
     * @param key the key.
     * @param value the new value.
     */
    fun putString(key: String, value: String?)
}

/** [KeyValueStore] kept in memory, used by tests and as a fallback. */
class InMemoryKeyValueStore : KeyValueStore {
    private val values = mutableMapOf<String, String>()

    override fun getString(key: String): String? = values[key]

    override fun putString(key: String, value: String?) {
        if (value == null) values.remove(key) else values[key] = value
    }
}

/** Koin module providing the platform [KeyValueStore]. */
expect val platformDataModule: Module
