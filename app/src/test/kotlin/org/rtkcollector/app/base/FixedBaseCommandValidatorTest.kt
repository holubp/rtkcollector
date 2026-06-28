package org.rtkcollector.app.base

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.rtkcollector.app.profile.CommandProfile
import org.rtkcollector.app.profile.ProfileReference
import org.rtkcollector.app.profile.RecordingSettingsSet

class FixedBaseCommandValidatorTest {
    @Test
    fun `fixed base materialization rejects non um980 receiver family`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            FixedBaseCommandValidator.requireSupportedReceiverFamily("ublox-m8t")
        }

        assertEquals("Fixed base requires a UM980/Unicore command profile.", error.message)
    }

    @Test
    fun `fixed base start accepts matching coordinate mode base line`() {
        val profile = CommandProfile(
            id = "fixed",
            name = "Fixed",
            receiverFamily = "um980-n4",
            runtimeScript = "UNLOG COM1\nMODE BASE 49.4637593130 15.4512544790 707.8000\nGNGGA 1",
        )

        assertDoesNotThrow {
            FixedBaseCommandValidator.validateSelectedCoordinateMatchesProfile(
                commandProfile = profile,
                selectedBaseCoordinate = sampleCoordinate(),
            )
        }
    }

    @Test
    fun `fixed base start rejects mismatched coordinate mode base line`() {
        val profile = CommandProfile(
            id = "fixed",
            name = "Fixed",
            receiverFamily = "um980-n4",
            runtimeScript = "UNLOG COM1\nMODE BASE 49.4637593130 15.4512544790 707.8000\nGNGGA 1",
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            FixedBaseCommandValidator.validateSelectedCoordinateMatchesProfile(
                commandProfile = profile,
                selectedBaseCoordinate = sampleCoordinate(mslAltitudeM = 707.8010),
            )
        }

        assertEquals("Selected base coordinate does not match command profile MODE BASE.", error.message)
    }

    @Test
    fun `fixed base start rejects survey mode base time line`() {
        val profile = CommandProfile(
            id = "fixed",
            name = "Fixed",
            receiverFamily = "unicore",
            runtimeScript = "UNLOG COM1\nMODE BASE TIME 120 2.5\nGNGGA 1",
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            FixedBaseCommandValidator.validateSelectedCoordinateMatchesProfile(
                commandProfile = profile,
                selectedBaseCoordinate = sampleCoordinate(),
            )
        }

        assertEquals(
            "Fixed base command profile must contain MODE BASE <lat> <lon> <msl-altitude>.",
            error.message,
        )
    }

    @Test
    fun `fixed base start uses later valid mode base line when survey line appears first`() {
        val profile = CommandProfile(
            id = "fixed",
            name = "Fixed",
            receiverFamily = "unicore",
            runtimeScript = "UNLOG COM1\nMODE BASE TIME 120 2.5\nMODE BASE 49.463759313 15.451254479 707.8\nGNGGA 1",
        )

        assertDoesNotThrow {
            FixedBaseCommandValidator.validateSelectedCoordinateMatchesProfile(
                commandProfile = profile,
                selectedBaseCoordinate = sampleCoordinate(),
            )
        }
    }

    @Test
    fun `fixed base overwrite detects command profile shared by another settings set`() {
        val settingsSets = listOf(
            sampleSettingsSet(id = "current", commandProfileId = "commands-1"),
            sampleSettingsSet(id = "other", commandProfileId = "commands-1"),
        )

        assertEquals(
            true,
            FixedBaseCommandValidator.isCommandProfileUsedByOtherSettingsSet(
                settingsSets = settingsSets,
                selectedSettingsSetId = "current",
                commandProfileId = "commands-1",
            ),
        )
    }

    @Test
    fun `fixed base overwrite allows command profile used only by selected settings set`() {
        val settingsSets = listOf(
            sampleSettingsSet(id = "current", commandProfileId = "commands-1"),
            sampleSettingsSet(id = "other", commandProfileId = "commands-2"),
        )

        assertEquals(
            false,
            FixedBaseCommandValidator.isCommandProfileUsedByOtherSettingsSet(
                settingsSets = settingsSets,
                selectedSettingsSetId = "current",
                commandProfileId = "commands-1",
            ),
        )
    }

    private fun sampleCoordinate(
        latDeg: Double = 49.463759313,
        lonDeg: Double = 15.451254479,
        ellipsoidalHeightM: Double? = 752.9215,
        mslAltitudeM: Double? = 707.8,
        geoidSeparationM: Double? = 45.1215,
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
            horizontalUncertaintyM = 0.02,
            verticalUncertaintyM = 0.04,
            antennaHeightM = 1.5,
            antennaReferencePoint = "ARP",
            sourceSessionId = "session-1",
            sourceDescription = "Temporary base average",
        )

    private fun sampleSettingsSet(
        id: String,
        commandProfileId: String,
    ): RecordingSettingsSet =
        RecordingSettingsSet(
            id = id,
            name = id,
            workflowId = "workflow-rover",
            receiverProfileId = "receiver-1",
            commandProfileRef = ProfileReference(commandProfileId, commandProfileId),
            usbBaudProfileRef = ProfileReference("baud-1", "Baud"),
            recordingOutputProfileRef = ProfileReference("output-1", "Output"),
            storageProfileRef = ProfileReference("storage-1", "Storage"),
        )
}
