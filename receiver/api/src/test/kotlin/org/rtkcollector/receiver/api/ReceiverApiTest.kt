package org.rtkcollector.receiver.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReceiverApiTest {
    @Test
    fun `capabilities describe safe bootstrap receiver support`() {
        val capabilities = ReceiverCapabilities(
            supportsRoverMode = true,
            supportsBaseMode = false,
            supportsFixedBaseMode = false,
            supportsRtcmInput = true,
            supportsRtcmOutput = false,
            supportsNativeRawObservation = false,
            supportsCustomInitScripts = true,
        )

        assertTrue(capabilities.supportsRoverMode)
        assertTrue(capabilities.supportsRtcmInput)
        assertEquals(false, capabilities.supportsFixedBaseMode)
    }

    @Test
    fun `receiver command preserves exact byte payload`() {
        val payload = byteArrayOf(0x24, 0x47, 0x50)
        val command = ReceiverCommand(
            label = "example",
            payload = payload,
            recordsToTxSidecar = true,
        )

        assertTrue(command.payload.contentEquals(payload))
        assertTrue(command.recordsToTxSidecar)
    }
}
