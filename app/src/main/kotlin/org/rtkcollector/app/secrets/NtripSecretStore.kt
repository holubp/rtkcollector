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
import org.rtkcollector.app.profile.commitStringChangesWithRollback

class NtripSecretStore(
    context: Context,
    private val preferences: SharedPreferences = context.getSharedPreferences("ntrip-secrets", Context.MODE_PRIVATE),
) {
    private val keyAlias = "rtkcollector-ntrip-secrets"
    private val keyStoreType = "AndroidKeyStore"
    private val cipherTransformation = "AES/GCM/NoPadding"

    fun putPassword(secretId: String, password: String) {
        putPasswords(mapOf(secretId to password))
    }

    fun putPasswords(passwordsBySecretId: Map<String, String>) {
        if (passwordsBySecretId.isEmpty()) return
        passwordsBySecretId.keys.forEach { secretId ->
            require(secretId.isNotBlank()) { "NTRIP secret id must not be blank." }
        }
        val key = getOrCreateKey()
        val encrypted = passwordsBySecretId.mapValues { (_, password) ->
            val cipher = Cipher.getInstance(cipherTransformation)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val ciphertext = cipher.doFinal(password.toByteArray(Charsets.UTF_8))
            EncodedSecret(
                iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP),
                ciphertext = Base64.encodeToString(ciphertext, Base64.NO_WRAP),
            )
        }
        val changes = linkedMapOf<String, String?>()
        encrypted.forEach { (secretId, secret) ->
            changes["$secretId.iv"] = secret.iv
            changes["$secretId.ciphertext"] = secret.ciphertext
        }
        preferences.commitStringChangesWithRollback(
            changes = changes,
            failureMessage = "Unable to commit imported NTRIP secrets.",
        )
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

    fun knownSecretIds(): Set<String> =
        preferences.all.keys
            .filter { it.endsWith(".iv") }
            .map { it.removeSuffix(".iv") }
            .toSet()

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

    private data class EncodedSecret(
        val iv: String,
        val ciphertext: String,
    )
}
