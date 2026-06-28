package org.rtkcollector.app.base

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FixedBaseProfileMaterializerTest {
    @Test
    fun `replaces existing mode base line`() {
        val result = FixedBaseProfileMaterializer.materialize(
            runtimeScript = "UNLOG COM1\nMODE BASE TIME 120 2.5\nGNGGA 1",
            modeBaseCommand = "MODE BASE 49.4637593130 15.4512544790 707.8000",
        )

        assertEquals("MODE BASE TIME 120 2.5", result.replacedLine)
        assertEquals(
            "UNLOG COM1\nMODE BASE 49.4637593130 15.4512544790 707.8000\nGNGGA 1",
            result.runtimeScript,
        )
    }

    @Test
    fun `inserts mode base after unlog when no mode line exists`() {
        val result = FixedBaseProfileMaterializer.materialize(
            runtimeScript = "UNLOG COM1\nGNGGA 1",
            modeBaseCommand = "MODE BASE 49.4637593130 15.4512544790 707.8000",
        )

        assertEquals(null, result.replacedLine)
        assertEquals(
            "UNLOG COM1\nMODE BASE 49.4637593130 15.4512544790 707.8000\nGNGGA 1",
            result.runtimeScript,
        )
    }

    @Test
    fun `inserts mode base at top when script has no unlog`() {
        val result = FixedBaseProfileMaterializer.materialize(
            runtimeScript = "GNGGA 1",
            modeBaseCommand = "MODE BASE 49.4637593130 15.4512544790 707.8000",
        )

        assertEquals(
            "MODE BASE 49.4637593130 15.4512544790 707.8000\nGNGGA 1",
            result.runtimeScript,
        )
    }
}
