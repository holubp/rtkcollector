package org.rtkcollector.app.recording

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Um980BaudTransitionTest {
    @Test
    fun `different profile and serial baud uses device first host second ordering`() {
        val plan = Um980BaudTransitionPlan.build(
            profileBaud = 230400,
            serialBaud = 921600,
            initCommands = listOf("LOG VERSIONA ONCE"),
            baudSwitchCommands = listOf("CONFIG COM1 921600"),
            modeCommands = listOf("BESTNAVB COM1 0.05"),
        )

        assertEquals(
            listOf(
                Um980BaudStep.OpenHostAtProfileBaud(230400),
                Um980BaudStep.SendCommands(listOf("LOG VERSIONA ONCE")),
                Um980BaudStep.SendCommands(listOf("CONFIG COM1 921600")),
                Um980BaudStep.PauseAfterDeviceBaudCommand,
                Um980BaudStep.ReconfigureHostBaud(921600),
                Um980BaudStep.DrainTransitionalRx,
                Um980BaudStep.SendCommands(listOf("BESTNAVB COM1 0.05")),
            ),
            plan.steps,
        )
    }

    @Test
    fun `same baud sends init and mode without host reconfigure`() {
        val plan = Um980BaudTransitionPlan.build(
            profileBaud = 230400,
            serialBaud = 230400,
            initCommands = listOf("GPGGA COM1 1"),
            baudSwitchCommands = emptyList(),
            modeCommands = listOf("BESTNAVB COM1 1"),
        )

        assertTrue(plan.steps.none { it is Um980BaudStep.ReconfigureHostBaud })
        assertTrue(plan.steps.none { it == Um980BaudStep.DrainTransitionalRx })
    }

    @Test
    fun `different baud requires explicit device baud switch command`() {
        assertThrows(IllegalArgumentException::class.java) {
            Um980BaudTransitionPlan.build(
                profileBaud = 230400,
                serialBaud = 921600,
                initCommands = emptyList(),
                baudSwitchCommands = emptyList(),
                modeCommands = emptyList(),
            )
        }
    }
}
