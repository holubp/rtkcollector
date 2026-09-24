package org.rtkcollector.app.recording

import android.content.Intent
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ServiceNtripIntentParserTest {
    @Test
    fun `valid correction and upload extras construct TLS requests`() {
        for ((intent, keys) in listOf(
            correctionIntent() to CORRECTION_NTRIP_INTENT_KEYS,
            uploadIntent() to UPLOAD_NTRIP_INTENT_KEYS,
        )) {
            var constructed = 0
            val mountpoint = withValidatedServiceNtripIntent(intent, keys, false) { policy, mount ->
                constructed++
                assertEquals("caster.example", policy.endpoint.host)
                mount
            }
            assertEquals("MOUNT", mountpoint)
            assertEquals(1, constructed)
        }
    }

    @Test
    fun `start and update correction reject incomplete and mistyped endpoint extras before construction`() {
        for (action in listOf("start", "update")) {
            val valid = correctionIntent()
            val malformed = listOf(
                Intent(valid).apply { removeExtra(RecordingForegroundService.EXTRA_NTRIP_PORT) },
                Intent(valid).putExtra(RecordingForegroundService.EXTRA_NTRIP_PORT, "2101"),
                Intent(valid).apply { removeExtra(RecordingForegroundService.EXTRA_NTRIP_UNSAFE_TLS_ACKNOWLEDGED) },
                Intent(valid).putExtra(RecordingForegroundService.EXTRA_NTRIP_UNSAFE_TLS_ACKNOWLEDGED, "false"),
            )
            for (intent in malformed) {
                var constructed = false
                assertFailsWith<IllegalArgumentException>(action) {
                    withValidatedServiceNtripIntent(intent, CORRECTION_NTRIP_INTENT_KEYS, false) { _, _ ->
                        constructed = true
                    }
                }
                assertFalse(constructed, action)
            }
        }
    }

    @Test
    fun `upload rejects incomplete and mistyped endpoint extras before construction`() {
        val valid = uploadIntent()
        val malformed = listOf(
            Intent(valid).apply { removeExtra(RecordingForegroundService.EXTRA_BASE_CASTER_UPLOAD_PORT) },
            Intent(valid).putExtra(RecordingForegroundService.EXTRA_BASE_CASTER_UPLOAD_PORT, "2101"),
            Intent(valid).apply { removeExtra(RecordingForegroundService.EXTRA_BASE_CASTER_UPLOAD_UNSAFE_TLS_ACKNOWLEDGED) },
            Intent(valid).putExtra(RecordingForegroundService.EXTRA_BASE_CASTER_UPLOAD_UNSAFE_TLS_ACKNOWLEDGED, "false"),
        )
        for (intent in malformed) {
            var constructed = false
            assertFailsWith<IllegalArgumentException> {
                withValidatedServiceNtripIntent(intent, UPLOAD_NTRIP_INTENT_KEYS, false) { _, _ ->
                    constructed = true
                }
            }
            assertFalse(constructed)
        }
    }

    private fun correctionIntent() = Intent().apply {
        putExtra(RecordingForegroundService.EXTRA_NTRIP_HOST, "caster.example")
        putExtra(RecordingForegroundService.EXTRA_NTRIP_PORT, 2101)
        putExtra(RecordingForegroundService.EXTRA_NTRIP_MOUNTPOINT, "MOUNT")
        putExtra(RecordingForegroundService.EXTRA_NTRIP_TRANSPORT_MODE, "TLS")
        putExtra(RecordingForegroundService.EXTRA_NTRIP_TLS_VERIFICATION, "SYSTEM_TRUST")
        putExtra(RecordingForegroundService.EXTRA_NTRIP_UNSAFE_TLS_ACKNOWLEDGED, false)
    }

    private fun uploadIntent() = Intent().apply {
        putExtra(RecordingForegroundService.EXTRA_BASE_CASTER_UPLOAD_HOST, "caster.example")
        putExtra(RecordingForegroundService.EXTRA_BASE_CASTER_UPLOAD_PORT, 2101)
        putExtra(RecordingForegroundService.EXTRA_BASE_CASTER_UPLOAD_MOUNTPOINT, "MOUNT")
        putExtra(RecordingForegroundService.EXTRA_BASE_CASTER_UPLOAD_TRANSPORT_MODE, "TLS")
        putExtra(RecordingForegroundService.EXTRA_BASE_CASTER_UPLOAD_TLS_VERIFICATION, "SYSTEM_TRUST")
        putExtra(RecordingForegroundService.EXTRA_BASE_CASTER_UPLOAD_UNSAFE_TLS_ACKNOWLEDGED, false)
    }
}
