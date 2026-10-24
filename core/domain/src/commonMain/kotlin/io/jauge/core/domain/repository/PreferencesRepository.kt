package io.jauge.core.domain.repository

import io.jauge.core.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow

/** Preferences kept on the device. Implementations never throw. */
interface PreferencesRepository {
    /** Current preferences, updated after every change. */
    fun observePreferences(): Flow<UserPreferences>

    /**
     * Changes the preferences.
     *
     * @param transform builds the new preferences from the current ones.
     */
    suspend fun update(transform: (UserPreferences) -> UserPreferences)
}
