package io.jauge.core.data.preferences

import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSUserDefaults

/** [KeyValueStore] backed by the standard NSUserDefaults. */
class UserDefaultsKeyValueStore : KeyValueStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun getString(key: String): String? = defaults.stringForKey(key)

    override fun putString(key: String, value: String?) {
        if (value == null) defaults.removeObjectForKey(key) else defaults.setObject(value, forKey = key)
    }
}

/** iOS: NSUserDefaults. */
actual val platformDataModule: Module = module {
    single<KeyValueStore> { UserDefaultsKeyValueStore() }
}
