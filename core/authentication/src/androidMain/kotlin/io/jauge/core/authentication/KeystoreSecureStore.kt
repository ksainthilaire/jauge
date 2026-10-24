package io.jauge.core.authentication

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * [SecureStore] of Android: each value is encrypted with AES-256-GCM by a key of the Android Keystore (it cannot be
 * read or exported, even on a rooted phone with a hardware backed keystore), then kept in private preferences
 * excluded from backups (`data_extraction_rules.xml`). A value that cannot be decrypted, for example after a restore
 * on another phone, is deleted and read as missing: the user simply signs in again.
 *
 * @param context application context.
 */
class KeystoreSecureStore(context: Context) : SecureStore {

    private val preferences: SharedPreferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    @Synchronized
    override fun read(key: String): String? {
        val saved = preferences.getString(key, null) ?: return null
        return runCatching {
            val bytes = Base64.decode(saved, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
            cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES).decodeToString()
        }.onFailure { delete(key) }.getOrNull()
    }

    @Synchronized
    override fun write(key: String, value: String) {
        runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            val encrypted = cipher.iv + cipher.doFinal(value.encodeToByteArray())
            preferences.edit().putString(key, Base64.encodeToString(encrypted, Base64.NO_WRAP)).apply()
        }.onFailure { delete(key) }
    }

    @Synchronized
    override fun delete(key: String) {
        preferences.edit().remove(key).apply()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_BITS)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val FILE_NAME = "jauge_secure_session"
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "io.jauge.session.key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BITS = 256
        const val TAG_BITS = 128
        const val IV_BYTES = 12
    }
}

internal actual fun platformSecureStoreModule(): Module = module {
    single<SecureStore> { KeystoreSecureStore(androidContext()) }
}
