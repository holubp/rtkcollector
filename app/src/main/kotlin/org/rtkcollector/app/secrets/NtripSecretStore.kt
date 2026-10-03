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

sealed interface StoredNtripPassword {
    data object Missing : StoredNtripPassword
    data class Available(val value: String) : StoredNtripPassword {
        override fun toString(): String = "Available(redacted)"
    }
    data object Unreadable : StoredNtripPassword
}

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

    /** Publication-only path: a staged owner binding must never replace a live entry. */
    fun putNewPasswords(passwordsBySecretId: Map<String, String>) = synchronized(preferences) {
        requireUnallocated(passwordsBySecretId.keys)
        putPasswords(passwordsBySecretId)
    }

    fun requireUnallocated(secretIds: Set<String>) = synchronized(preferences) {
        secretIds.forEach { id ->
            require(id.isNotBlank() && !preferences.contains("$id.iv") && !preferences.contains("$id.ciphertext")) {
                "NTRIP secret binding is already allocated."
            }
        }
    }

    fun putPasswords(passwordsBySecretId: Map<String, String>) = synchronized(preferences) {
        if (passwordsBySecretId.isEmpty()) return@synchronized
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

    fun getPassword(secretId: String): String? = when (val stored = readPassword(secretId)) {
        StoredNtripPassword.Missing -> null
        is StoredNtripPassword.Available -> stored.value
        StoredNtripPassword.Unreadable -> throw IllegalStateException("Stored NTRIP password cannot be decrypted.")
    }

    fun readPassword(secretId: String): StoredNtripPassword {
        return try {
            val ivText = preferences.getString("$secretId.iv", null)
            val ciphertextText = preferences.getString("$secretId.ciphertext", null)
            if (ivText == null && ciphertextText == null) return StoredNtripPassword.Missing
            if (ivText == null || ciphertextText == null) return StoredNtripPassword.Unreadable
            val iv = Base64.decode(ivText, Base64.NO_WRAP)
            val ciphertext = Base64.decode(ciphertextText, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(cipherTransformation)
            val key = existingKey() ?: return StoredNtripPassword.Unreadable
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            StoredNtripPassword.Available(cipher.doFinal(ciphertext).toString(Charsets.UTF_8))
        } catch (_: Exception) {
            StoredNtripPassword.Unreadable
        }
    }

    fun hasPassword(secretId: String): Boolean =
        preferences.contains("$secretId.iv") && preferences.contains("$secretId.ciphertext")

    fun knownSecretIds(): Set<String> =
        preferences.all.keys
            .filter { it.endsWith(".iv") }
            .map { it.removeSuffix(".iv") }
            .toSet()

    private fun getOrCreateKey(): SecretKey {
        existingKey()?.let { return it }

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

    private fun existingKey(): SecretKey? {
        val keyStore = KeyStore.getInstance(keyStoreType).apply { load(null) }
        return (keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry)?.secretKey
    }

    private data class EncodedSecret(
        val iv: String,
        val ciphertext: String,
    )
}
