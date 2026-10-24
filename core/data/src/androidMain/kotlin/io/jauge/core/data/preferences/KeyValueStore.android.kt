package io.jauge.core.data.preferences

import android.content.Context
import android.content.SharedPreferences
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * [KeyValueStore] backed by private SharedPreferences.
 *
 * @param context application context.
 */
class SharedPreferencesKeyValueStore(context: Context) : KeyValueStore {
    private val preferences: SharedPreferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    override fun getString(key: String): String? = preferences.getString(key, null)

    override fun putString(key: String, value: String?) {
        preferences.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply()
    }

    private companion object {
        const val FILE_NAME = "jauge_preferences"
    }
}

/** Android: SharedPreferences, with the application context registered by `androidContext`. */
actual val platformDataModule: Module = module {
    single<KeyValueStore> { SharedPreferencesKeyValueStore(get()) }
}
