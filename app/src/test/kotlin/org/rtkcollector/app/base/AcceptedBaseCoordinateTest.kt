package org.rtkcollector.app.base

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class AcceptedBaseCoordinateTest {
    @Test
    fun um980FixedBaseCommandUsesMslAltitude() {
        val coordinate = sampleCoordinate()

        coordinate.validate()

        assertEquals(
            "MODE BASE 49.4637593130 15.4512544790 707.8000",
            coordinate.toFixedBaseModeCommand(),
        )
    }

    @Test
    fun commandWithoutMslAltitudeThrows() {
        val coordinate = sampleCoordinate(ellipsoidalHeightM = 752.9215, mslAltitudeM = null)

        val error = assertThrows(IllegalArgumentException::class.java, coordinate::toFixedBaseModeCommand)

        assertEquals("UM980 fixed base requires MSL altitude.", error.message)
    }

    @Test
    fun rejectsNonFiniteMslAltitude() {
        val coordinate = sampleCoordinate(mslAltitudeM = Double.NaN)

        val error = assertThrows(IllegalArgumentException::class.java, coordinate::toUm980FixedBaseModeCommand)

        assertEquals("Accepted base MSL altitude must be finite.", error.message)
    }

    @Test
    fun rejectsNonFiniteGeoidSeparation() {
        val coordinate = sampleCoordinate(geoidSeparationM = Double.POSITIVE_INFINITY)

        val error = assertThrows(IllegalArgumentException::class.java, coordinate::toUm980FixedBaseModeCommand)

        assertEquals("Accepted base geoid separation must be finite.", error.message)
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
        latDeg: Double = 49.463759313,
        lonDeg: Double = 15.451254479,
        ellipsoidalHeightM: Double? = 752.9215,
        mslAltitudeM: Double? = 707.8,
        geoidSeparationM: Double? = 45.1215,
        horizontalUncertaintyM: Double? = 0.02,
    ): AcceptedBaseCoordinate =
        AcceptedBaseCoordinate(
            id = "base-1",
            name = "Car roof base",
            latDeg = latDeg,
            lonDeg = lonDeg,
            ellipsoidalHeightM = ellipsoidalHeightM,
            mslAltitudeM = mslAltitudeM,
            geoidSeparationM = geoidSeparationM,
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
