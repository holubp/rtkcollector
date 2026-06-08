package org.rtkcollector.app.recording

data class Um980BaudTransitionPlan(
    val steps: List<Um980BaudStep>,
) {
    companion object {
        fun build(
            profileBaud: Int,
            serialBaud: Int,
            initCommands: List<String>,
            baudSwitchCommands: List<String>,
            modeCommands: List<String>,
        ): Um980BaudTransitionPlan {
            require(profileBaud in BAUD_RANGE) { "Profile baud must be 9600..921600." }
            require(serialBaud in BAUD_RANGE) { "Serial baud must be 9600..921600." }
            require(profileBaud == serialBaud || baudSwitchCommands.isNotEmpty()) {
                "Profile baud differs from recording baud but no receiver baud-switch command was supplied."
            }
            val steps = mutableListOf<Um980BaudStep>()
            steps += Um980BaudStep.OpenHostAtProfileBaud(profileBaud)
            if (initCommands.isNotEmpty()) {
                steps += Um980BaudStep.SendCommands(initCommands)
            }
            if (profileBaud != serialBaud) {
                steps += Um980BaudStep.SendCommands(baudSwitchCommands)
                steps += Um980BaudStep.PauseAfterDeviceBaudCommand
                steps += Um980BaudStep.ReconfigureHostBaud(serialBaud)
                steps += Um980BaudStep.DrainTransitionalRx
            }
            if (modeCommands.isNotEmpty()) {
                steps += Um980BaudStep.SendCommands(modeCommands)
            }
            return Um980BaudTransitionPlan(steps)
        }
    }
}

sealed class Um980BaudStep {
    data class OpenHostAtProfileBaud(val baud: Int) : Um980BaudStep()
    data class SendCommands(val commands: List<String>) : Um980BaudStep()
    data object PauseAfterDeviceBaudCommand : Um980BaudStep()
    data class ReconfigureHostBaud(val baud: Int) : Um980BaudStep()
    data object DrainTransitionalRx : Um980BaudStep()
}

private val BAUD_RANGE = 9600..921600
