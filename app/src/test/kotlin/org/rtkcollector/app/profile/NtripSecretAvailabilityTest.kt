package org.rtkcollector.app.profile

import android.content.Context
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.rtkcollector.app.secrets.NtripSecretStore
import org.rtkcollector.app.secrets.StoredNtripPassword

@RunWith(RobolectricTestRunner::class)
class NtripSecretAvailabilityTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val prefs get() = context.getSharedPreferences("ntrip-secrets", Context.MODE_PRIVATE)

    @Test
    fun missingEntryIsDifferentFromIncompleteEncryptedEntry() {
        assertTrue(prefs.edit().clear().commit())
        val store = NtripSecretStore(context)
        assertTrue(store.readPassword("owner") is StoredNtripPassword.Missing)

        assertTrue(prefs.edit().putString("owner.iv", "not-complete").commit())
        assertTrue(store.readPassword("owner") is StoredNtripPassword.Unreadable)
    }

    @Test
    fun publicationCannotOverwriteAnAllocatedBinding() {
        assertTrue(prefs.edit().clear().putString("owner.iv", "existing").commit())

        try {
            NtripSecretStore(context).putNewPasswords(mapOf("owner" to "replacement"))
            fail("Existing binding should be rejected before encryption")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }

        assertTrue(prefs.getString("owner.iv", null) == "existing")
    }
}
