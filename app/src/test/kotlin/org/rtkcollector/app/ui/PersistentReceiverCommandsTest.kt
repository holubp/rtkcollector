package org.rtkcollector.app.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PersistentReceiverCommandsTest {
    @Test
    fun `persistent receiver commands strip comments and append saveconfig once`() {
        val commands = persistentReceiverCommands(
            """
            # comment
            MODE ROVER SURVEY

            SAVECONFIG
            BESTNAVB COM1 1
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                "MODE ROVER SURVEY",
                "BESTNAVB COM1 1",
                "SAVECONFIG",
            ),
            commands,
        )
    }
}
