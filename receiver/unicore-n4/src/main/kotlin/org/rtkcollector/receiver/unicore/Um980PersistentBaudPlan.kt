package org.rtkcollector.receiver.unicore

data class Um980PersistentBaudPlan(
    val steps: List<Um980PersistentBaudStep>,
) {
    companion object {
        fun commands(targetBaud: Int): List<String> {
            require(targetBaud in SUPPORTED_BAUD_RATES) {
                "Target baud must be one of ${SUPPORTED_BAUD_RATES.sorted().joinToString()}."
            }
            return listOf(
                "CONFIG COM1 $targetBaud",
                "CONFIG COM2 $targetBaud",
                "CONFIG COM3 $targetBaud",
                "SAVECONFIG",
            )
        }

        fun build(currentHostBaud: Int, targetBaud: Int): Um980PersistentBaudPlan {
            require(currentHostBaud in SUPPORTED_BAUD_RATES) {
                "Current host baud must be one of ${SUPPORTED_BAUD_RATES.sorted().joinToString()}."
            }
            val allCommands = commands(targetBaud)
            if (currentHostBaud == targetBaud) {
                return Um980PersistentBaudPlan(
                    steps = listOf(
                        Um980PersistentBaudStep.SendCommands(allCommands.dropLast(1)),
                        Um980PersistentBaudStep.SendCommands(listOf("SAVECONFIG")),
                        Um980PersistentBaudStep.ExpectSaveConfigOk,
                    ),
                )
            }
            return Um980PersistentBaudPlan(
                steps = listOf(
                    Um980PersistentBaudStep.SendCommands(listOf(allCommands.first())),
                    Um980PersistentBaudStep.PauseAfterDeviceBaudCommands,
                    Um980PersistentBaudStep.ReconfigureHostBaud(targetBaud),
                    Um980PersistentBaudStep.VerifyReceiverAtTargetBaud,
                    Um980PersistentBaudStep.SendCommands(allCommands.drop(1).dropLast(1)),
                    Um980PersistentBaudStep.SendCommands(listOf("SAVECONFIG")),
                    Um980PersistentBaudStep.ExpectSaveConfigOk,
                ),
            )
        }

        private val SUPPORTED_BAUD_RATES: Set<Int> = setOf(
            4800,
            9600,
            14400,
            19200,
            38400,
            57600,
            115200,
            128000,
            230400,
            256000,
            460800,
            921600,
        )
    }
}

sealed class Um980PersistentBaudStep {
    data class SendCommands(val commands: List<String>) : Um980PersistentBaudStep()
    data object PauseAfterDeviceBaudCommands : Um980PersistentBaudStep()
    data class ReconfigureHostBaud(val baud: Int) : Um980PersistentBaudStep()
    data object VerifyReceiverAtTargetBaud : Um980PersistentBaudStep()
    data object ExpectSaveConfigOk : Um980PersistentBaudStep()
}
