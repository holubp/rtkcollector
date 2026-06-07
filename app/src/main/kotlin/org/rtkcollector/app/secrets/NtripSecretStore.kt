package org.rtkcollector.app.secrets

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

class NtripSecretStore(
    context: Context,
    private val preferences: SharedPreferences = context.getSharedPreferences("ntrip-secrets", Context.MODE_PRIVATE),
) {
    private val keyAlias = "rtkcollector-ntrip-secrets"
    private val keyStoreType = "AndroidKeyStore"
    private val cipherTransformation = "AES/GCM/NoPadding"

    fun putPassword(secretId: String, password: String) {
        require(secretId.isNotBlank()) { "NTRIP secret id must not be blank." }
        val cipher = Cipher.getInstance(cipherTransformation)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(password.toByteArray(Charsets.UTF_8))
        preferences.edit()
            .putString("$secretId.iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("$secretId.ciphertext", Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .apply()
    }

    fun getPassword(secretId: String): String? {
        val iv = preferences.getString("$secretId.iv", null)?.let { Base64.decode(it, Base64.NO_WRAP) }
            ?: return null
        val ciphertext = preferences.getString("$secretId.ciphertext", null)?.let { Base64.decode(it, Base64.NO_WRAP) }
            ?: return null
        val cipher = Cipher.getInstance(cipherTransformation)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
    }

    fun hasPassword(secretId: String): Boolean =
        preferences.contains("$secretId.iv") && preferences.contains("$secretId.ciphertext")

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(keyStoreType).apply { load(null) }
        (keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, keyStoreType)
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }
}
