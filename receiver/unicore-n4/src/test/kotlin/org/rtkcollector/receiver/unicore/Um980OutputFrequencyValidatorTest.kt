package org.rtkcollector.receiver.unicore

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class Um980OutputFrequencyValidatorTest {
    @Test
    fun `rejects unsupported continuous output period`() {
        val error = Um980OutputFrequencyValidator.validateCommands(
            listOf(
                "BESTNAVB COM1 0.25",
                "OBSVMCMPB COM1 0.2",
            ),
        )

        assertEquals(
            "Unsupported UM980 output frequency in `BESTNAVB COM1 0.25`: use 1, 2, 5, 10, 20, or 50 Hz.",
            error,
        )
    }

    @Test
    fun `accepts supported frequencies event driven outputs and slow ephemeris outputs`() {
        val error = Um980OutputFrequencyValidator.validateCommands(
            listOf(
                "BESTNAVB COM1 0.05",
                "OBSVMCMPB COM1 0.2",
                "ADRNAVB COM1 1",
                "GNGGA 0.2",
                "GPSEPHB COM1 300",
                "GALIONB ONCHANGED",
            ),
        )

        assertNull(error)
    }
}
