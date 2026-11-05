package app.peertube.tv

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Stores proxy credentials encrypted with a non-exportable Android Keystore key. */
internal object ProxyCredentialStore {
    private const val KEY_ALIAS = "peertube_proxy_password_v1"
    private const val ENCRYPTED_KEY = "proxyPasswordEncrypted"
    private const val LEGACY_KEY = "proxyPassword"
    private const val PREFIX = "v1:"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    @Synchronized
    fun readAndMigrate(context: Context, preferences: SharedPreferences): String {
        val stored = preferences.getString(ENCRYPTED_KEY, null)
        if (stored != null) return runCatching { decrypt(context, stored) }.getOrDefault("")
        val legacy = preferences.getString(LEGACY_KEY, "").orEmpty()
        if (legacy.isNotEmpty()) write(context, preferences, legacy)
        else preferences.edit().remove(LEGACY_KEY).apply()
        return legacy
    }

    @Synchronized
    fun write(context: Context, preferences: SharedPreferences, password: String) {
        val editor = preferences.edit().remove(LEGACY_KEY)
        if (password.isNotEmpty()) editor.putString(ENCRYPTED_KEY, encrypt(context, password))
        else editor.remove(ENCRYPTED_KEY)
        check(editor.commit()) { "Proxy password could not be stored securely" }
    }

    private fun encrypt(context: Context, value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return PREFIX + Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(context: Context, value: String): String {
        require(value.startsWith(PREFIX)) { "Unsupported encrypted proxy password" }
        val payload = Base64.decode(value.removePrefix(PREFIX), Base64.NO_WRAP)
        require(payload.size > 12) { "Invalid encrypted proxy password" }
        val iv = payload.copyOfRange(0, 12)
        val encrypted = payload.copyOfRange(12, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }
}
