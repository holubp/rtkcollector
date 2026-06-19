package org.rtkcollector.core.session

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

class SessionWritersTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `session writers append to distinct artifact files`() {
        val writers = SessionWriters.open(tempDir)

        writers.writeSessionJson("""{"sessionUuid":"test-session"}""")
        writers.appendReceiverRx(byteArrayOf(0x01, 0x02))
        writers.appendReceiverRx(byteArrayOf(0x03))
        writers.appendTxToReceiver(byteArrayOf(0x10))
        writers.appendTxToReceiver(byteArrayOf(0x11, 0x12))
        writers.appendCorrectionInput(byteArrayOf(0x20, 0x21))
        writers.appendCorrectionInput(byteArrayOf(0x22))
        writers.appendBaseCasterUploadRtcm(byteArrayOf(0x30, 0x31))
        writers.appendBaseCasterUploadRtcm(byteArrayOf(0x32))
        writers.appendEventJson("""{"event":"started"}""")
        writers.appendEventJson("""{"event":"stopped"}""")
        writers.appendQualityLiveJson("""{"fix":"float"}""")
        writers.appendQualityLiveJson("""{"fix":"rtk"}""")
        writers.appendReceiverSolutionJson("""{"source":"GGA"}""")
        writers.appendReceiverPppSolutionJson("""{"source":"PPP"}""")
        writers.appendRtklibSolutionNmea("\$GPGGA,rtklib*00\r\n")
        writers.appendRtklibSolutionPos("% pos header\n")
        writers.appendRtklibStatusJson("""{"state":"RUNNING"}""")
        writers.appendExtractedRtcm(byteArrayOf(0xd3.toByte(), 0x00, 0x00, 0x47, 0x00, 0x00))
        writers.writeBasePositionJson("""{"frame":"ETRF2000"}""")
        writers.flush()
        writers.close()

        assertEquals("""{"sessionUuid":"test-session"}""", Files.readString(tempDir.resolve("session.json")).trim())
        assertArrayEquals(byteArrayOf(0x01, 0x02, 0x03), Files.readAllBytes(tempDir.resolve("receiver-rx.raw")))
        assertArrayEquals(byteArrayOf(0x10, 0x11, 0x12), Files.readAllBytes(tempDir.resolve("tx-to-receiver.raw")))
        assertArrayEquals(byteArrayOf(0x20, 0x21, 0x22), Files.readAllBytes(tempDir.resolve("correction-input.raw")))
        assertArrayEquals(byteArrayOf(0x20, 0x21, 0x22), Files.readAllBytes(tempDir.resolve("correction-input.rtcm3")))
        assertArrayEquals(byteArrayOf(0x30, 0x31, 0x32), Files.readAllBytes(tempDir.resolve("base-caster-upload.rtcm3")))
        assertEquals(
            listOf("""{"event":"started"}""", """{"event":"stopped"}"""),
            Files.readAllLines(tempDir.resolve("events.jsonl")),
        )
        assertEquals(
            listOf("""{"fix":"float"}""", """{"fix":"rtk"}"""),
            Files.readAllLines(tempDir.resolve("quality-live.jsonl")),
        )
        assertEquals(listOf("""{"source":"GGA"}"""), Files.readAllLines(tempDir.resolve("receiver-solution.jsonl")))
        assertEquals(listOf("""{"source":"PPP"}"""), Files.readAllLines(tempDir.resolve("receiver-ppp-solution.jsonl")))
        assertEquals("\$GPGGA,rtklib*00\r\n", Files.readString(tempDir.resolve("rtklib-solution.nmea")))
        assertEquals("% pos header\n", Files.readString(tempDir.resolve("rtklib-solution.pos")))
        assertEquals(listOf("""{"state":"RUNNING"}"""), Files.readAllLines(tempDir.resolve("rtklib-status.jsonl")))
        assertArrayEquals(
            byteArrayOf(0xd3.toByte(), 0x00, 0x00, 0x47, 0x00, 0x00),
            Files.readAllBytes(tempDir.resolve("rtcm-extracted.rtcm3")),
        )
        assertEquals("""{"frame":"ETRF2000"}""", Files.readString(tempDir.resolve("base-position.json")).trim())
    }

    @Test
    fun `openNew rejects a non-empty session directory`() {
        val sessionDirectory = Files.createTempDirectory("rtkcollector-existing-session")
        Files.write(
            sessionDirectory.resolve(SessionArtifactFile.RECEIVER_RX_RAW.fileName),
            byteArrayOf(0x01, 0x02),
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND,
        )

        val error = assertThrows(IllegalStateException::class.java) {
            SessionWriters.openNew(sessionDirectory)
        }

        assertTrue(error.message!!.contains("non-empty session directory"))
        assertArrayEquals(
            byteArrayOf(0x01, 0x02),
            Files.readAllBytes(sessionDirectory.resolve(SessionArtifactFile.RECEIVER_RX_RAW.fileName)),
        )
    }

    @Test
    fun `openAppendForRecovery appends receiver rx without truncating`() {
        val sessionDirectory = Files.createTempDirectory("rtkcollector-recovery-session")
        Files.createDirectories(sessionDirectory)
        val rxPath = sessionDirectory.resolve(SessionArtifactFile.RECEIVER_RX_RAW.fileName)
        Files.write(rxPath, byteArrayOf(0x01, 0x02), StandardOpenOption.CREATE, StandardOpenOption.APPEND)

        SessionWriters.openAppendForRecovery(sessionDirectory).use { writers ->
            writers.appendReceiverRx(byteArrayOf(0x03))
            writers.flush()
        }

        assertArrayEquals(byteArrayOf(0x01, 0x02, 0x03), Files.readAllBytes(rxPath))
    }

    @Test
    fun `correction input raw remains writable when rtcm3 mirror cannot be opened`() {
        val sessionDirectory = Files.createTempDirectory("rtkcollector-rtcm3-mirror-blocked")
        Files.createDirectory(sessionDirectory.resolve(SessionArtifactFile.CORRECTION_INPUT_RTCM3.fileName))

        SessionWriters.openAppendForRecovery(sessionDirectory).use { writers ->
            writers.appendCorrectionInput(byteArrayOf(0x20, 0x21, 0x22))
            writers.flush()
        }

        assertArrayEquals(
            byteArrayOf(0x20, 0x21, 0x22),
            Files.readAllBytes(sessionDirectory.resolve(SessionArtifactFile.CORRECTION_INPUT_RAW.fileName)),
        )
    }

    @Test
    fun `export metadata contains redacted ntrip metadata only`() {
        val metadata = SessionMetadata(
            appVersion = "0.1.0",
            androidDeviceModel = "test-device",
            androidVersion = "test-android",
            receiverDriverId = "generic-nmea-rtcm",
            receiverIdentification = null,
            usbVid = null,
            usbPid = null,
            baudRate = 921600,
            serialParameters = SerialParameters(),
            mode = SessionMode.ROVER,
            startedAt = "2026-06-07T10:00:00Z",
            stoppedAt = null,
            ntrip = NtripSessionMetadata(
                casterHost = "caster.example",
                mountpoint = "MOUNT",
                usernamePresent = true,
                secretRef = "ntrip/MOUNT",
                protocol = "NTRIP_V2_PREFERRED_WITH_COMPATIBILITY",
                finalStatus = "AUTHORIZATION_FAILED",
            ),
            antenna = AntennaMetadata(),
            sessionUuid = "00000000-0000-0000-0000-000000000001",
            linkedBaseSessionUuid = null,
            workflowId = "rover-ntrip-to-receiver",
            workflowName = "Rover + NTRIP to receiver",
            receiverRole = "ROVER",
            um980ProfileId = "um980-rover-ntrip",
            commandProfileId = "um980-default-commands",
            usbBaudProfileId = "um980-230400",
            ntripCasterProfileId = "ntrip-caster-default",
            ntripMountpointProfileId = "ntrip-mountpoint-default",
            recordingPolicyId = "default-record-everything",
            storageProfileId = "app-private",
            storageKind = "APP_PRIVATE",
            coordinateSource = null,
            baseCasterUploadEnabled = true,
            baseCasterUploadHost = "upload.example",
            baseCasterUploadPort = 2101,
            baseCasterUploadMountpoint = "BASEOUT",
            baseCasterUploadUsernamePresent = true,
            baseCasterUploadSecretRef = "ntrip-caster-upload-profile/upload",
            baseCasterUploadFinalStatus = "STREAMING",
            validationSummary = "valid",
            expectedArtifacts = listOf("receiver-rx.raw", "tx-to-receiver.raw", "base-caster-upload.rtcm3"),
        )

        val exported = exportSessionMetadata(metadata)

        assertFalse(exported.contains("password", ignoreCase = true))
        assertFalse(exported.contains("token", ignoreCase = true))
        assertEquals(true, exported.contains("usernamePresent"))
        assertTrue(exported.contains("ntrip/MOUNT"))
        assertTrue(exported.contains("rover-ntrip-to-receiver"))
        assertTrue(exported.contains("um980-default-commands"))
        assertTrue(exported.contains("ntrip-caster-default"))
        assertTrue(exported.contains("NTRIP_V2_PREFERRED_WITH_COMPATIBILITY"))
        assertTrue(exported.contains("AUTHORIZATION_FAILED"))
        assertTrue(exported.contains("tx-to-receiver.raw"))
        assertTrue(exported.contains("upload.example"))
        assertTrue(exported.contains("BASEOUT"))
        assertTrue(exported.contains("ntrip-caster-upload-profile/upload"))
        assertTrue(exported.contains("base-caster-upload.rtcm3"))
    }

    @Test
    fun `nmea solution sidecar appends without truncating existing file`() {
        SessionWriters.openNew(tempDir).use { writers ->
            writers.appendReceiverSolutionNmea("\$GPGGA,1*00\r\n")
        }
        SessionWriters.openAppendForRecovery(tempDir).use { writers ->
            writers.appendReceiverSolutionNmea("\$GPGGA,2*00\r\n")
        }

        assertEquals(
            "\$GPGGA,1*00\r\n\$GPGGA,2*00\r\n",
            Files.readString(tempDir.resolve("receiver-solution.nmea")),
        )
    }

    @Test
    fun `session json rewrite leaves complete json and removes temporary file`() {
        SessionWriters.open(tempDir).use { writers ->
            writers.writeSessionJson("""{"sessionUuid":"one","stoppedAt":null}""")
            writers.writeSessionJson("""{"sessionUuid":"one","stoppedAt":"now"}""")
        }

        assertEquals(
            """{"sessionUuid":"one","stoppedAt":"now"}""",
            Files.readString(tempDir.resolve("session.json")).trim(),
        )
        assertFalse(Files.exists(tempDir.resolve("session.json.tmp")))
    }

    @Test
    fun `production session writers avoid Android-incompatible text file APIs`() {
        val source = Files.readAllLines(
            Path.of("src/main/kotlin/org/rtkcollector/core/session/SessionWriters.kt"),
        ).joinToString("\n")

        assertFalse(source.contains("Files.writeString"))
        assertFalse(source.contains("Files.readString"))
        assertFalse(source.contains(".writeText("))
        assertFalse(source.contains(".readText("))
        assertTrue(source.contains("StandardOpenOption.WRITE"))
    }
}
