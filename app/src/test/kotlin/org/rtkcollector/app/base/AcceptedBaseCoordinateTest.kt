package org.rtkcollector.app.base

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class AcceptedBaseCoordinateTest {
    @Test
    fun validCoordinateGeneratesUm980FixedBaseCommand() {
        val coordinate = sampleCoordinate()

        coordinate.validate()

        assertEquals(
            "MODE BASE 50.1234567890 14.9876543210 287.1234",
            coordinate.toFixedBaseModeCommand(),
        )
    }

    @Test
    fun rejectsInvalidLatitude() {
        val coordinate = sampleCoordinate(latDeg = 91.0)

        assertThrows(IllegalArgumentException::class.java, coordinate::validate)
    }

    @Test
    fun rejectsInvalidLongitude() {
        val coordinate = sampleCoordinate(lonDeg = -181.0)

        assertThrows(IllegalArgumentException::class.java, coordinate::validate)
    }

    @Test
    fun rejectsInvalidHeight() {
        val coordinate = sampleCoordinate(ellipsoidalHeightM = Double.NaN)

        assertThrows(IllegalArgumentException::class.java, coordinate::validate)
    }

    @Test
    fun rejectsNegativeUncertainty() {
        val coordinate = sampleCoordinate(horizontalUncertaintyM = -0.001)

        assertThrows(IllegalArgumentException::class.java, coordinate::validate)
    }

    private fun sampleCoordinate(
        latDeg: Double = 50.123456789,
        lonDeg: Double = 14.987654321,
        ellipsoidalHeightM: Double = 287.1234,
        horizontalUncertaintyM: Double? = 0.02,
    ): AcceptedBaseCoordinate =
        AcceptedBaseCoordinate(
            id = "base-1",
            name = "Car roof base",
            latDeg = latDeg,
            lonDeg = lonDeg,
            ellipsoidalHeightM = ellipsoidalHeightM,
            frame = "ETRS89",
            epoch = "2026.46",
            method = "RECEIVER_PPP",
            durationSeconds = 900,
            horizontalUncertaintyM = horizontalUncertaintyM,
            verticalUncertaintyM = 0.04,
            antennaHeightM = 1.5,
            antennaReferencePoint = "ARP",
            sourceSessionId = "session-1",
            sourceDescription = "Temporary base average",
        )
}
