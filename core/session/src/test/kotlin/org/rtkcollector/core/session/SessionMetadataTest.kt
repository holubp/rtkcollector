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
    fun `session modes match version one workflow names`() {
        assertEquals(SessionMode.ROVER, SessionMode.valueOf("ROVER"))
        assertEquals(SessionMode.FIXED_BASE, SessionMode.valueOf("FIXED_BASE"))
        assertEquals(
            SessionMode.TEMPORARY_BASE_PREPARATION,
            SessionMode.valueOf("TEMPORARY_BASE_PREPARATION"),
        )
        assertEquals(SessionMode.REPLAY_TEST, SessionMode.valueOf("REPLAY_TEST"))
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

    @Test
    fun `base position method includes receiver PPP candidate`() {
        assertEquals(BasePositionMethod.RECEIVER_PPP, BasePositionMethod.valueOf("RECEIVER_PPP"))
    }

    @Test
    fun `session metadata exports workflow coordinate source and redacted NTRIP secret reference`() {
        val metadata = SessionMetadata(
            appVersion = "0.2.0",
            androidDeviceModel = "test-device",
            androidVersion = "15",
            receiverDriverId = "um980-n4",
            receiverIdentification = null,
            usbVid = 0x0403,
            usbPid = 0x6015,
            baudRate = 230400,
            serialParameters = SerialParameters(),
            mode = SessionMode.FIXED_BASE,
            startedAt = "2026-06-07T10:00:00Z",
            stoppedAt = null,
            ntrip = NtripSessionMetadata(
                casterHost = "caster.example.org",
                casterPort = 2101,
                mountpoint = "CORS01",
                usernamePresent = true,
                ggaUploadEnabled = true,
                secretRef = "ntrip/CORS01",
            ),
            antenna = AntennaMetadata(antennaHeightMeters = 1.5, antennaReferencePoint = "ARP"),
            sessionUuid = "session-001",
            linkedBaseSessionUuid = null,
            workflowId = "fixed-base-rtcm-output",
            workflowName = "Fixed base RTCM output",
            receiverRole = "FIXED_BASE",
            um980ProfileId = "um980-fixed-base-rtcm",
            coordinateSource = "IMPORTED_BASE_POSITION_JSON",
            validationSummary = "valid",
            expectedArtifacts = listOf("receiver-rx.raw", "rtcm-extracted.rtcm3"),
        )

        val json = exportSessionMetadata(metadata)

        assertEquals(true, json.contains("fixed-base-rtcm-output"))
        assertEquals(true, json.contains("IMPORTED_BASE_POSITION_JSON"))
        assertEquals(true, json.contains("ntrip/CORS01"))
        assertEquals(false, json.contains("password", ignoreCase = true))
        assertEquals(false, json.contains("secret-token"))
    }
}
