package io.jauge.core.authentication

import com.russhwolf.settings.ExperimentalSettingsImplementation
import com.russhwolf.settings.KeychainSettings
import kotlinx.cinterop.ExperimentalForeignApi
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.CFBridgingRetain
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrService

/**
 * [SecureStore] of iOS: values are kept in the Keychain of the app, readable after the first unlock of the phone and
 * never synchronized with iCloud or moved to another phone by a backup (`ThisDeviceOnly`).
 */
@OptIn(ExperimentalSettingsImplementation::class, ExperimentalForeignApi::class)
class KeychainSecureStore : SecureStore {

    private val keychain = KeychainSettings(
        kSecAttrService to CFBridgingRetain(SERVICE),
        kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
    )

    override fun read(key: String): String? = keychain.getStringOrNull(key)

    override fun write(key: String, value: String) {
        keychain.putString(key, value)
    }

    override fun delete(key: String) {
        keychain.remove(key)
    }

    private companion object {
        const val SERVICE = "io.jauge.app.session"
    }
}

internal actual fun platformSecureStoreModule(): Module = module {
    single<SecureStore> { KeychainSecureStore() }
}
