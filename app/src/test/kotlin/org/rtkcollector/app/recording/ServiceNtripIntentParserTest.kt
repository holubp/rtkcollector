package org.rtkcollector.app.recording

import android.content.Intent
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ServiceNtripIntentParserTest {
    @Test
    fun `real request factories reject malformed typed extras`() {
        for ((valid, keys, construct) in listOf(
            Triple(correctionIntent(), CORRECTION_NTRIP_INTENT_KEYS, { intent: Intent -> correctionNtripRequestFromIntent(intent, false).mountpoint }),
            Triple(uploadIntent(), UPLOAD_NTRIP_INTENT_KEYS, { intent: Intent -> uploadNtripRequestFromIntent(intent, false).mountpoint }),
        )) {
            assertEquals("MOUNT", construct(valid))
            val malformed = listOf(
                Intent(valid).apply { removeExtra(keys.host) },
                Intent(valid).putExtra(keys.host, 42),
                Intent(valid).putExtra(keys.host, " "),
                Intent(valid).putExtra(keys.host, "bad host"),
                Intent(valid).apply { removeExtra(keys.mountpoint) },
                Intent(valid).putExtra(keys.mountpoint, 42),
                Intent(valid).putExtra(keys.mountpoint, " "),
                Intent(valid).apply { removeExtra(keys.port) },
                Intent(valid).putExtra(keys.port, "2101"),
                Intent(valid).putExtra(keys.port, 0),
                Intent(valid).putExtra(keys.port, 65536),
                Intent(valid).apply { removeExtra(keys.transportMode) },
                Intent(valid).putExtra(keys.transportMode, 42),
                Intent(valid).putExtra(keys.transportMode, "BOGUS"),
                Intent(valid).putExtra(keys.transportMode, "PLAINTEXT"),
                Intent(valid).apply { removeExtra(keys.tlsVerification) },
                Intent(valid).putExtra(keys.tlsVerification, 42),
                Intent(valid).putExtra(keys.tlsVerification, "BOGUS"),
                Intent(valid).putExtra(keys.tlsVerification, "UNSAFE"),
                Intent(valid).apply { removeExtra(keys.unsafeTlsAcknowledged) },
                Intent(valid).putExtra(keys.unsafeTlsAcknowledged, "false"),
            )
            for (intent in malformed) {
                assertFailsWith<IllegalArgumentException> { construct(intent) }
            }
        }
    }

    @Test
    fun `sideload unsafe TLS requires typed true acknowledgement for both requests`() {
        for ((valid, keys, construct) in listOf(
            Triple(correctionIntent(), CORRECTION_NTRIP_INTENT_KEYS, { intent: Intent -> correctionNtripRequestFromIntent(intent, true).mountpoint }),
            Triple(uploadIntent(), UPLOAD_NTRIP_INTENT_KEYS, { intent: Intent -> uploadNtripRequestFromIntent(intent, true).mountpoint }),
        )) {
            val unsafe = Intent(valid).putExtra(keys.tlsVerification, "UNSAFE")
            assertFailsWith<IllegalArgumentException> { construct(unsafe) }
            assertEquals("MOUNT", construct(Intent(unsafe).putExtra(keys.unsafeTlsAcknowledged, true)))
            assertFailsWith<IllegalArgumentException> {
                construct(Intent(unsafe).putExtra(keys.unsafeTlsAcknowledged, "true"))
            }
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
