package org.rtkcollector.receiver.unicore

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class Um980RuntimeCommandValidatorTest {
    @Test
    fun runtimeValidatorRejectsPersistentAndResetCommands() {
        listOf(
            "SAVECONFIG",
            "RESET",
            "FRESET",
            "CONFIG RESTORE",
            "UPDATEAPP",
            "FORMAT",
        ).forEach { command ->
            assertThrows(IllegalArgumentException::class.java) {
                Um980RuntimeCommandValidator.validateRuntimeCommand(command)
            }
        }
    }

    @Test
    fun runtimeValidatorAcceptsDocumentedRuntimeCommandFamilies() {
        listOf(
            "UNLOG COM1",
            "MODE ROVER",
            "CONFIG MMP ENABLE",
            "BESTNAVB COM1 1",
            "OBSVMCMPB COM1 1",
            "RTCM1074 COM1 1",
        ).forEach(Um980RuntimeCommandValidator::validateRuntimeCommand)
    }
}
