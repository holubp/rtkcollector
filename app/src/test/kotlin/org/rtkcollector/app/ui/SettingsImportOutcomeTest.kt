package org.rtkcollector.app.ui

import org.rtkcollector.app.profile.PreferenceCommitException
import org.rtkcollector.app.profile.PreferenceRollbackStatus
import org.rtkcollector.app.profile.SecretStoreImportOutcome
import org.rtkcollector.app.profile.classifySecretStoreImportFailure
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SettingsImportOutcomeTest {
    @Test
    fun `every credential failure reports restored profiles and disabled bindings without secret values`() {
        SecretStoreImportOutcome.entries.filter { it != SecretStoreImportOutcome.STORED }.forEach { outcome ->
            assertTrue(outcome.userMessage.orEmpty().contains("Profiles restored"))
            assertTrue(outcome.userMessage.orEmpty().contains("bindings remain disabled"))
            assertFalse(outcome.userMessage.orEmpty().contains("secret-password"))
        }
    }

    @Test
    fun `successful secret import has no warning`() {
        assertNull(SecretStoreImportOutcome.STORED.userMessage)
    }

    @Test
    fun `primary failure with successful rollback is reported as restored`() {
        val outcome = classifySecretStoreImportFailure(
            PreferenceCommitException(
                "Unable to commit imported NTRIP secrets. Previous preference values were restored synchronously.",
                PreferenceRollbackStatus.RESTORED,
            ),
        )

        assertEquals(SecretStoreImportOutcome.PRIMARY_COMMIT_FAILED_ROLLBACK_SUCCEEDED, outcome)
        assertFalse(outcome.userMessage.orEmpty().contains("secret-password"))
        assertFalse(outcome.userMessage.orEmpty().contains("Unable to commit"))
    }

    @Test
    fun `primary and rollback failure is reported as uncertain state`() {
        val outcome = classifySecretStoreImportFailure(
            PreferenceCommitException(
                "Unable to commit imported NTRIP secrets. Rollback also failed; persisted preference state may be inconsistent.",
                PreferenceRollbackStatus.FAILED,
            ),
        )

        assertEquals(SecretStoreImportOutcome.PRIMARY_AND_ROLLBACK_FAILED, outcome)
        assertEquals(
            "Profiles restored, but NTRIP password state is uncertain because password rollback failed. Imported credential bindings remain disabled. Check and enter the passwords again.",
            outcome.userMessage,
        )
    }

    @Test
    fun `unexpected failure gets generic sanitized message`() {
        val outcome = classifySecretStoreImportFailure(
            IllegalStateException("ciphertext=secret-password"),
        )

        assertEquals(SecretStoreImportOutcome.FAILED, outcome)
        assertEquals(
            "Profiles restored, but NTRIP passwords could not be stored securely. Imported credential bindings remain disabled. Enter the passwords again.",
            outcome.userMessage,
        )
    }
}
