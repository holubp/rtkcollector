package org.rtkcollector.core.session

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SessionMetadataTest {
    @Test
    fun `session metadata field names match documented concepts`() {
        val metadata = SessionMetadata(
            appVersion = "0.1.0",
            androidDeviceModel = "test-device",
            androidVersion = "test-android",
            receiverDriverId = "generic-nmea-rtcm",
            receiverIdentification = null,
            usbVid = 0x1234,
            usbPid = 0x5678,
            baudRate = 921600,
            serialParameters = SerialParameters(),
            mode = SessionMode.ROVER,
            startedAt = "2026-06-06T15:00:00Z",
            stoppedAt = null,
            ntrip = NtripSessionMetadata(casterHost = "caster.example", mountpoint = "MOUNT"),
            antenna = AntennaMetadata(antennaHeightMeters = 2.0),
            sessionUuid = "00000000-0000-0000-0000-000000000001",
            linkedBaseSessionUuid = null,
        )

        assertEquals(SessionMode.ROVER, metadata.mode)
        assertEquals(921600, metadata.baudRate)
        assertNull(metadata.stoppedAt)
        assertEquals("caster.example", metadata.ntrip?.casterHost)
    }

    @Test
    fun `base position carries calibration provenance`() {
        val position = BasePositionMetadata(
            latitudeDegrees = 50.0,
            longitudeDegrees = 14.0,
            heightMeters = 300.0,
            ecefXMeters = 3970000.0,
            ecefYMeters = 1050000.0,
            ecefZMeters = 4850000.0,
            frame = "ETRF2000",
            epoch = "2026.4",
            method = BasePositionMethod.PPP_STATIC,
            durationSeconds = 86400,
            uncertaintyMeters = 0.02,
            antennaHeightMeters = 1.5,
            antennaReferencePoint = "ARP",
            sourceSessionReference = "session-uuid",
        )

        assertEquals(BasePositionMethod.PPP_STATIC, position.method)
        assertEquals("session-uuid", position.sourceSessionReference)
    }
}
