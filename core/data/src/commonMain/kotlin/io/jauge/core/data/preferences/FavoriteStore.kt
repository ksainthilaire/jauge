package io.jauge.core.data.preferences

/**
 * Set of favorite analysis identifiers kept in a [KeyValueStore], one identifier per line. A value that cannot be
 * read is an empty set.
 *
 * @param store device storage.
 * @param key key of the set in [store].
 */
class FavoriteStore(private val store: KeyValueStore, private val key: String = KEY) {

    /** Reads the saved identifiers. */
    fun read(): Set<String> = runCatching {
        store.getString(key).orEmpty().lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }.getOrDefault(emptySet())

    /**
     * Saves the identifiers, replacing the previous set.
     *
     * @param identifiers favorite analysis identifiers.
     */
    fun write(identifiers: Set<String>) {
        val clean = identifiers.map { it.trim() }.filter { it.isNotEmpty() && '\n' !in it && '\r' !in it }
        runCatching { store.putString(key, clean.takeIf { it.isNotEmpty() }?.joinToString("\n")) }
    }

    /** Keys used by the app. */
    companion object {
        /** Favorites of the user, cached on the device. */
        const val KEY = "analyses.favorites"

        /** Favorites known by the offline API (mode sans API), kept apart from the cache of the user. */
        const val OFFLINE_KEY = "offline.analyses.favorites"
    }
}
