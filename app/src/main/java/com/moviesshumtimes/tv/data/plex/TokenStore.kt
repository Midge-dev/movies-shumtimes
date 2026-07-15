package com.moviesshumtimes.tv.data.plex

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.first

private val Context.tokenDataStore by preferencesDataStore(name = "plex_token")

// androidx.security:security-crypto (EncryptedSharedPreferences) was
// deprecated in 2025 in favor of using the Android Keystore directly, so
// this hand-rolls the same idea: an AES-GCM key that never leaves hardware
// (or a software fallback on older devices), encrypting the Plex account
// token before it's persisted in DataStore.
object TokenStore {
    private const val KEY_ALIAS = "shumtimes_token_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128

    private val TOKEN_KEY = stringPreferencesKey("encrypted_token")
    private val IV_KEY = stringPreferencesKey("token_iv")

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    suspend fun saveToken(context: Context, token: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val ciphertext = cipher.doFinal(token.toByteArray(Charsets.UTF_8))

        context.tokenDataStore.edit { prefs ->
            prefs[TOKEN_KEY] = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
            prefs[IV_KEY] = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        }
    }

    suspend fun loadToken(context: Context): String? {
        val prefs = context.tokenDataStore.data.first()
        val ciphertext = prefs[TOKEN_KEY]?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return null
        val iv = prefs[IV_KEY]?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return null

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    suspend fun clearToken(context: Context) {
        context.tokenDataStore.edit { it.clear() }
    }
}
