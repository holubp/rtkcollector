package org.rtkcollector.core.session

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SessionMetadataExportTest {
    @Test
    fun `export includes RTKLIB reproducibility metadata`() {
        val json = exportSessionMetadata(
            SessionMetadata(
                appVersion = "0.1.0",
                androidDeviceModel = "device",
                androidVersion = "15",
                receiverDriverId = "um980-n4",
                receiverIdentification = null,
                usbVid = 1,
                usbPid = 2,
                baudRate = 230400,
                serialParameters = SerialParameters(),
                mode = SessionMode.ROVER,
                startedAt = "2026-06-19T00:00:00Z",
                stoppedAt = null,
                ntrip = null,
                antenna = AntennaMetadata(),
                sessionUuid = "session",
                linkedBaseSessionUuid = null,
                rtklibEnabled = true,
                rtklibPreset = "ROVER_KINEMATIC_RTK",
                rtklibSnapshotId = "rtklib-ex-2.5.0@commit",
                rtklibRoutePlan = "rover=input_unicore(UNICORE_OBSVMB); correction=input_rtcm3(RTCM3)",
                rtklibValidationSummary = "valid; snapshot=rtklib-ex-2.5.0@commit",
                solutionPolicyProfileId = "solution-auto-best",
                solutionScreenPolicy = "AUTO_BEST",
                solutionMockPolicy = "RTKLIB_ONLY",
            ),
        )

        assertTrue(json.contains("\"rtklibSnapshotId\":\"rtklib-ex-2.5.0@commit\""))
        assertTrue(json.contains("\"rtklibRoutePlan\":\"rover=input_unicore(UNICORE_OBSVMB); correction=input_rtcm3(RTCM3)\""))
        assertTrue(json.contains("\"rtklibValidationSummary\":\"valid; snapshot=rtklib-ex-2.5.0@commit\""))
        assertTrue(json.contains("\"solutionPolicyProfileId\":\"solution-auto-best\""))
        assertTrue(json.contains("\"solutionMockPolicy\":\"RTKLIB_ONLY\""))
    }
}
