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
    fun `google play editor disables and explains incompatible transport choices`() {
        val fields = ntripSecurityEditorFields(
            transportMode = NtripTransportMode.TLS,
            tlsVerification = NtripTlsVerification.SystemTrust,
            unsafeTlsAcknowledged = false,
            allowInsecure = false,
        )

        val transport = fields.first { it.key == "transportMode" }
        val verification = fields.first { it.key == "tlsVerification" }

        assertFalse(transport.optionItems.first { it.value == NtripTransportMode.PLAINTEXT.name }.enabled)
        assertTrue(transport.helperText.orEmpty().contains("Google Play", ignoreCase = true))
        assertFalse(verification.optionItems.first { it.value == NtripTlsVerification.Unsafe.storageValue }.enabled)
        assertTrue(verification.helperText.orEmpty().contains("sideload", ignoreCase = true))
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
    fun `plaintext editor disables unsafe verification and preserves core valid selection`() {
        val fields = ntripSecurityEditorFields(
            NtripTransportMode.PLAINTEXT, NtripTlsVerification.SystemTrust, false, true,
        )
        val verification = fields.first { it.key == "tlsVerification" }
        assertFalse(verification.optionItems.first { it.value == NtripTlsVerification.Unsafe.storageValue }.enabled)

        val changed = updatedNtripSecurityEditorValues(
            mapOf("transportMode" to "PLAINTEXT", "tlsVerification" to NtripTlsVerification.SystemTrust.storageValue),
            "tlsVerification", NtripTlsVerification.Unsafe.storageValue,
        )
        assertEquals(NtripTlsVerification.SystemTrust.storageValue, changed["tlsVerification"])
    }

    @Test
    fun `opening stale plaintext unsafe profile shows normalized verification`() {
        val fields = ntripSecurityEditorFields(
            NtripTransportMode.PLAINTEXT, NtripTlsVerification.Unsafe, true, true,
        )
        assertEquals(NtripTlsVerification.SystemTrust.storageValue,
            fields.first { it.key == "tlsVerification" }.value)
        assertEquals("false", fields.first { it.key == "unsafeTlsAcknowledged" }.value)
    }

    @Test
    fun `unsafe verification option follows transport changes in open editor`() {
        val fromTls = ntripSecurityEditorFields(
            NtripTransportMode.TLS, NtripTlsVerification.SystemTrust, false, true,
        ).first { it.key == "tlsVerification" }
        val plaintext = fromTls.withRuntimeProfileValidation(mapOf("transportMode" to "PLAINTEXT"))
        assertFalse(plaintext.optionItems.first { it.value == NtripTlsVerification.Unsafe.storageValue }.enabled)

        val fromPlaintext = ntripSecurityEditorFields(
            NtripTransportMode.PLAINTEXT, NtripTlsVerification.SystemTrust, false, true,
        ).first { it.key == "tlsVerification" }
        val tls = fromPlaintext.withRuntimeProfileValidation(mapOf("transportMode" to "TLS"))
        assertTrue(tls.optionItems.first { it.value == NtripTlsVerification.Unsafe.storageValue }.enabled)
    }

    @Test
    fun `fresh unsafe acknowledgement survives until a later endpoint edit`() {
        val changed = updatedNtripSecurityEditorValues(
            mapOf("host" to "old.example", "transportMode" to "TLS",
                "tlsVerification" to NtripTlsVerification.Unsafe.storageValue,
                "unsafeTlsAcknowledged" to "true"),
            "host", "new.example",
        )
        assertEquals("false", changed["unsafeTlsAcknowledged"])
        val acknowledged = updatedNtripSecurityEditorValues(changed, "unsafeTlsAcknowledged", "true")
        assertEquals("true", acknowledged["unsafeTlsAcknowledged"])
        assertEquals("false", updatedNtripSecurityEditorValues(acknowledged, "port", "2201")["unsafeTlsAcknowledged"])
    }

    @Test
    fun `unsafe tls acknowledgement is required and reset after endpoint or security changes`() {
        val fields = ntripSecurityEditorFields(
            transportMode = NtripTransportMode.TLS,
            tlsVerification = NtripTlsVerification.Unsafe,
            unsafeTlsAcknowledged = false,
            allowInsecure = true,
        )
        val acknowledgement = fields.first { it.key == "unsafeTlsAcknowledged" }

        assertTrue(acknowledgement.boolean)
        assertTrue(acknowledgement.danger)
        assertTrue(acknowledgement.visibleWhenUnsafeTls)
        assertTrue(acknowledgement.helperText.orEmpty().contains("certificate", ignoreCase = true))

        listOf("host" to "new-caster.example", "tlsVerification" to NtripTlsVerification.SystemTrust.storageValue)
            .forEach { (key, value) ->
                val changed = updatedNtripSecurityEditorValues(
                    values = mapOf(
                        "transportMode" to NtripTransportMode.TLS.name,
                        "tlsVerification" to NtripTlsVerification.Unsafe.storageValue,
                        "unsafeTlsAcknowledged" to "true",
                    ),
                    key = key,
                    value = value,
                )
                assertEquals("false", changed["unsafeTlsAcknowledged"], key)
            }
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
