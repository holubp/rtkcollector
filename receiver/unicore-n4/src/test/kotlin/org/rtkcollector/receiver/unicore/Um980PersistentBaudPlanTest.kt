package org.rtkcollector.receiver.unicore

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class Um980PersistentBaudPlanTest {
    @Test
    fun `persistent baud commands configure all com ports before saveconfig`() {
        assertEquals(
            listOf(
                "CONFIG COM1 230400",
                "CONFIG COM2 230400",
                "CONFIG COM3 230400",
                "SAVECONFIG",
            ),
            Um980PersistentBaudPlan.commands(230400),
        )
    }

    @Test
    fun `different current and target baud reconfigures host before saveconfig`() {
        assertEquals(
            listOf(
                Um980PersistentBaudStep.SendCommands(listOf("CONFIG COM1 230400")),
                Um980PersistentBaudStep.PauseAfterDeviceBaudCommands,
                Um980PersistentBaudStep.ReconfigureHostBaud(230400),
                Um980PersistentBaudStep.VerifyReceiverAtTargetBaud,
                Um980PersistentBaudStep.SendCommands(
                    listOf(
                        "CONFIG COM2 230400",
                        "CONFIG COM3 230400",
                    ),
                ),
                Um980PersistentBaudStep.SendCommands(listOf("SAVECONFIG")),
                Um980PersistentBaudStep.ExpectSaveConfigOk,
            ),
            Um980PersistentBaudPlan.build(currentHostBaud = 115200, targetBaud = 230400).steps,
        )
    }

    @Test
    fun `same current and target baud sends saveconfig without host reconfigure`() {
        assertEquals(
            listOf(
                Um980PersistentBaudStep.SendCommands(
                    listOf(
                        "CONFIG COM1 230400",
                        "CONFIG COM2 230400",
                        "CONFIG COM3 230400",
                    ),
                ),
                Um980PersistentBaudStep.SendCommands(listOf("SAVECONFIG")),
                Um980PersistentBaudStep.ExpectSaveConfigOk,
            ),
            Um980PersistentBaudPlan.build(currentHostBaud = 230400, targetBaud = 230400).steps,
        )
    }
}
