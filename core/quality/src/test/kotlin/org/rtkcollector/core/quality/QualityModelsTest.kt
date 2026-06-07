package org.rtkcollector.core.quality

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class QualityModelsTest {
    @Test
    fun `quality snapshot records byte counters solution and warnings`() {
        val snapshot = RecordingQualitySnapshot(
            elapsedSeconds = 60,
            receiverRxBytes = 1200,
            txToReceiverBytes = 200,
            correctionInputBytes = 200,
            ntripState = "STREAMING",
            ggaFixQuality = 4,
            bestnavPositionType = "NARROW_INT",
            pppStatus = "CONVERGING",
            rtcmMessageCounts = mapOf(1006 to 1, 1074 to 60),
            rawObservationRateHz = 1.0,
            warnings = listOf(QualityWarning("PPP_SHORT_OCCUPATION", "PPP occupation is under 15 minutes.")),
        )

        assertEquals(1200, snapshot.receiverRxBytes)
        assertTrue(snapshot.warnings.any { it.code == "PPP_SHORT_OCCUPATION" })
    }
}
