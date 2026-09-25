package org.rtkcollector.app.ui.profiles

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rtkcollector.app.profile.storageValue
import org.rtkcollector.core.correction.NtripTlsVerification
import org.rtkcollector.core.correction.NtripTransportMode

class ProfileEditorModelsTest {
    @Test
    fun `plaintext transport warning follows selection and GGA policy in either build`() {
        val fields = ntripSecurityEditorFields(
            NtripTransportMode.TLS, NtripTlsVerification.SystemTrust, false, false,
            ggaUploadEnabled = true,
        )
        val transport = fields.first { it.key == "transportMode" }
        assertTrue(transport.optionItems.first { it.value == "PLAINTEXT" }.enabled)
        assertFalse(transport.withRuntimeProfileValidation(mapOf("transportMode" to "TLS"))
            .helperText.orEmpty().contains("unencrypted"))
        assertTrue(transport.withRuntimeProfileValidation(mapOf("transportMode" to "PLAINTEXT"))
            .helperText.orEmpty().contains("credentials and GGA position are sent unencrypted"))
    }

    @Test
    fun `legacy unsafe TLS needs an explicit new transport selection`() {
        val initial = mapOf("requiresTlsVerificationChoice" to "true", "transportMode" to "TLS",
            "tlsVerification" to "UNSAFE")
        val selected = updatedNtripSecurityEditorValues(initial, "transportMode", "PLAINTEXT")
        assertEquals("false", selected["requiresTlsVerificationChoice"])
        assertEquals("SYSTEM_TRUST", selected["tlsVerification"])
    }
    @Test
    fun `migrated custom CA remains guarded until verification is explicitly selected`() {
        val initial = mapOf("requiresTlsVerificationChoice" to "true", "transportMode" to "TLS",
            "tlsVerification" to "UNSAFE")
        assertEquals("true", updatedNtripSecurityEditorValues(initial, "name", "Renamed")["requiresTlsVerificationChoice"])
        assertEquals("false", updatedNtripSecurityEditorValues(initial, "tlsVerification", "SYSTEM_TRUST")["requiresTlsVerificationChoice"])
    }
    @Test
    fun `command profile editor exposes persistent write action with warning text`() {
        val action = persistentReceiverWriteAction(onClick = {})

        assertEquals("Write init config persistently to device", action.label)
        assertTrue(action.warningTitle.orEmpty().contains("receiver", ignoreCase = true))
        assertTrue(action.warningBody.orEmpty().contains("non-volatile", ignoreCase = true))
        assertTrue(action.warningBody.orEmpty().contains("other apps", ignoreCase = true))
        assertEquals("Write persistently", action.confirmLabel)
    }

    @Test
    fun `usb baud editor exposes persistent target baud write action with warning text`() {
        val action = persistentBaudWriteAction(
            initialBaud = 230400,
            targetBaud = 460800,
            usbDeviceLabel = "FTDI UM980 0403:6015",
            onClick = {},
        )

        assertEquals("Write target baud persistently to device", action.label)
        assertTrue(action.warningTitle.orEmpty().contains("baud", ignoreCase = true))
        assertTrue(action.warningBody.orEmpty().contains("230400"))
        assertTrue(action.warningBody.orEmpty().contains("460800"))
        assertTrue(action.warningBody.orEmpty().contains("FTDI UM980 0403:6015"))
        assertTrue(action.warningBody.orEmpty().contains("UM980"))
        assertEquals("Write persistently", action.confirmLabel)
    }

    @Test
    fun `command persistent warning mentions active recording connection`() {
        val action = persistentReceiverWriteAction(onClick = {})

        assertTrue(action.warningBody.orEmpty().contains("active recording connection", ignoreCase = true))
    }

    @Test
    fun `both distributions offer plaintext and only system trusted TLS`() {
        val fields = ntripSecurityEditorFields(
            transportMode = NtripTransportMode.TLS,
            tlsVerification = NtripTlsVerification.SystemTrust,
            unsafeTlsAcknowledged = false,
            allowInsecure = false,
        )
        val transport = fields.first { it.key == "transportMode" }
        val verification = fields.first { it.key == "tlsVerification" }
        assertTrue(transport.optionItems.first { it.value == NtripTransportMode.PLAINTEXT.name }.enabled)
        assertTrue(verification.readOnly)
        assertTrue(verification.optionItems.isEmpty())
        assertTrue(fields.none { it.key == "unsafeTlsAcknowledged" })
    }

    @Test
    fun `changing transport retains credentials and clears unsafe acknowledgement`() {
        val changed = updatedNtripSecurityEditorValues(
            values = mapOf(
                "transportMode" to NtripTransportMode.TLS.name,
                "tlsVerification" to NtripTlsVerification.Unsafe.storageValue,
                "unsafeTlsAcknowledged" to "true",
                "username" to "base-user",
                "password" to "source-password",
            ),
            key = "transportMode",
            value = NtripTransportMode.PLAINTEXT.name,
        )

        assertEquals("base-user", changed["username"])
        assertEquals("source-password", changed["password"])
        assertEquals("false", changed["unsafeTlsAcknowledged"])
        assertEquals(NtripTlsVerification.SystemTrust.storageValue, changed["tlsVerification"])
    }

    @Test
    fun `plaintext editor warns about unencrypted credentials without GGA`() {
        val fields = ntripSecurityEditorFields(
            NtripTransportMode.PLAINTEXT, NtripTlsVerification.SystemTrust, false, true,
        )
        val warning = fields.first { it.key == "transportMode" }
            .withRuntimeProfileValidation(mapOf("transportMode" to "PLAINTEXT")).helperText.orEmpty()
        assertTrue(warning.contains("username/password are sent unencrypted"))
        assertFalse(warning.contains("GGA position"))
    }

    @Test
    fun `opening legacy unsafe TLS profile shows required migration choice`() {
        val fields = ntripSecurityEditorFields(
            NtripTransportMode.TLS, NtripTlsVerification.Unsafe, true, true,
            requiresTlsVerificationChoice = true,
        )
        assertTrue(fields.first { it.key == "tlsVerification" }.helperText.orEmpty()
            .contains("Choose TLS or Plaintext"))
        assertFalse(fields.any { it.key == "unsafeTlsAcknowledged" })
    }

    @Test
    fun `explicit TLS choice clears legacy unsafe marker`() {
        val changed = updatedNtripSecurityEditorValues(
            mapOf("transportMode" to "TLS", "tlsVerification" to "UNSAFE",
                "requiresTlsVerificationChoice" to "true"),
            "transportMode", "TLS",
        )
        assertEquals("SYSTEM_TRUST", changed["tlsVerification"])
        assertEquals("false", changed["requiresTlsVerificationChoice"])
    }

    @Test
    fun `editing endpoint never grants unsafe TLS consent`() {
        val changed = updatedNtripSecurityEditorValues(
            mapOf("host" to "old.example", "transportMode" to "TLS",
                "tlsVerification" to NtripTlsVerification.Unsafe.storageValue,
                "unsafeTlsAcknowledged" to "true"),
            "host", "new.example",
        )
        assertEquals("false", changed["unsafeTlsAcknowledged"])
        assertEquals("UNSAFE", changed["tlsVerification"])
    }

    @Test
    fun `unsafe TLS cannot be selected through profile options`() {
        val fields = ntripSecurityEditorFields(
            transportMode = NtripTransportMode.TLS,
            tlsVerification = NtripTlsVerification.Unsafe,
            unsafeTlsAcknowledged = false,
            allowInsecure = true,
        )
        assertTrue(fields.first { it.key == "tlsVerification" }.readOnly)
        assertTrue(fields.first { it.key == "tlsVerification" }.optionItems.isEmpty())
        assertFalse(fields.any { it.key == "unsafeTlsAcknowledged" })
    }

    @Test
    fun `v1 source upload username is retained but visibly ignored`() {
        val field = EditableProfileField(
            key = "username",
            label = "Username",
            value = "v2-user",
            sourceUploadUsername = true,
        ).withRuntimeProfileValidation(mapOf("protocolPolicy" to "NTRIP_V1_ONLY", "username" to "v2-user"))

        assertEquals("v2-user", field.value)
        assertTrue(field.readOnly)
        assertTrue(field.label.contains("not used", ignoreCase = true))
        assertTrue(field.helperText.orEmpty().contains("retained", ignoreCase = true))
    }
}
