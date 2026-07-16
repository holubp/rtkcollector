package org.rtkcollector.app.ui

import org.rtkcollector.app.profile.PreferenceCommitException
import org.rtkcollector.app.profile.PreferenceRollbackStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SettingsImportOutcomeTest {
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
            "NTRIP passwords could not be stored securely; persisted password state may be inconsistent. Check and enter them again.",
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
            "NTRIP passwords could not be stored securely; enter them again.",
            outcome.userMessage,
        )
    }
}
