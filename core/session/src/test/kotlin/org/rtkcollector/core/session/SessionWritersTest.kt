package org.rtkcollector.core.session

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.io.OutputStream
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
    fun `receiver rx flush does not flush sidecar streams`() {
        val operations = mutableListOf<String>()
        val streams = mutableMapOf<String, TrackingOutputStream>()
        val writers = SessionWriters.openAppendForRecovery(tempDir) { path ->
            TrackingOutputStream(path.fileName.toString(), operations).also {
                streams[path.fileName.toString()] = it
            }
        }

        writers.flushReceiverRx()

        assertEquals(listOf("receiver-rx.raw:flush"), operations)
        assertEquals(1, streams.getValue("receiver-rx.raw").flushCount)
        assertEquals(0, streams.getValue("tx-to-receiver.raw").flushCount)
        writers.closeAll()
    }

    @Test
    fun `closeAll reports each artifact failure and still finalises every stream`() {
        val operations = mutableListOf<String>()
        val streams = mutableMapOf<String, TrackingOutputStream>()
        val writers = SessionWriters.openAppendForRecovery(tempDir) { path ->
            val name = path.fileName.toString()
            TrackingOutputStream(
                name = name,
                operations = operations,
                failFlush = name == SessionArtifactFile.RECEIVER_RX_RAW.fileName,
                failClose = name == SessionArtifactFile.TX_TO_RECEIVER_RAW.fileName,
            ).also { streams[name] = it }
        }

        val report = writers.closeAll()

        assertEquals(
            listOf(
                "receiver-rx.raw:flush",
                "receiver-rx.raw:close",
                "tx-to-receiver.raw:flush",
                "tx-to-receiver.raw:close",
            ),
            operations.take(4),
        )
        assertTrue(streams.values.all { it.closeCount == 1 })
        assertEquals(2, report.issues.size)
        assertEquals(SessionArtifactFile.RECEIVER_RX_RAW.fileName, report.issues[0].artifact)
        assertEquals(SessionWriterIssueCategory.RAW_RX, report.issues[0].category)
        assertEquals(SessionWriterIssueSeverity.FATAL, report.issues[0].severity)
        assertEquals(SessionArtifactFile.TX_TO_RECEIVER_RAW.fileName, report.issues[1].artifact)
        assertEquals(SessionWriterIssueCategory.BINARY_SIDECAR, report.issues[1].category)
        assertEquals(SessionWriterIssueSeverity.DEGRADED, report.issues[1].severity)
    }

    @Test
    fun `closeAll finalises each stream at most once`() {
        val operations = mutableListOf<String>()
        val streams = mutableListOf<TrackingOutputStream>()
        val writers = SessionWriters.openAppendForRecovery(tempDir) { path ->
            TrackingOutputStream(path.fileName.toString(), operations).also(streams::add)
        }

        val first = writers.closeAll()
        val second = writers.closeAll()
        writers.close()

        assertEquals(first, second)
        assertTrue(streams.all { it.flushCount == 1 && it.closeCount == 1 })
    }

    @Test
    fun `optional absent artifact is not reported during finalisation`() {
        val writers = SessionWriters.openAppendForRecovery(tempDir) { path ->
            if (path.fileName.toString() == SessionArtifactFile.CORRECTION_INPUT_RTCM3.fileName) {
                throw IOException("optional artifact unavailable")
            }
            TrackingOutputStream(path.fileName.toString(), mutableListOf())
        }

        val report = writers.closeAll()

        assertTrue(report.issues.isEmpty())
    }

    @Test
    fun `open failure closes every stream opened earlier`() {
        val operations = mutableListOf<String>()
        val opened = mutableListOf<TrackingOutputStream>()

        val error = assertThrows(IOException::class.java) {
            SessionWriters.openAppendForRecovery(tempDir) { path ->
                if (path.fileName.toString() == SessionArtifactFile.CORRECTION_INPUT_RAW.fileName) {
                    throw IOException("injected open failure")
                }
                TrackingOutputStream(
                    name = path.fileName.toString(),
                    operations = operations,
                    failClose = opened.isEmpty(),
                ).also(opened::add)
            }
        }

        assertEquals("injected open failure", error.message)
        assertEquals(2, opened.size)
        assertTrue(opened.all { it.flushCount == 1 })
        assertTrue(opened.all { it.closeCount == 1 })
        assertEquals(1, error.suppressed.size)
        assertTrue(operations.contains("tx-to-receiver.raw:flush"))
        assertTrue(operations.contains("tx-to-receiver.raw:close"))
        assertTrue(error.suppressed.single().message!!.contains(SessionArtifactFile.RECEIVER_RX_RAW.fileName))
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
    fun exportedSessionMetadataContainsCurrentV1FieldsAndNoPassword() {
        val json = exportSessionMetadata(
            SessionMetadata(
                appVersion = "0.1.0",
                androidDeviceModel = "test-device",
                androidVersion = "36",
                receiverDriverId = "unicore-n4",
                receiverIdentification = null,
                usbVid = 1027,
                usbPid = 24597,
                baudRate = 230400,
                serialParameters = SerialParameters(),
                mode = SessionMode.ROVER,
                startedAt = "2026-06-14T00:00:00Z",
                stoppedAt = null,
                ntrip = NtripSessionMetadata(
                    casterHost = "caster.example",
                    casterPort = 2101,
                    mountpoint = "MOUNT",
                    usernamePresent = true,
                    ggaUploadEnabled = false,
                    secretRef = "ntrip:caster.example:caster:user",
                    protocol = "NTRIP_V2_PREFERRED_WITH_COMPATIBILITY",
                    finalStatus = null,
                ),
                antenna = AntennaMetadata(),
                sessionUuid = "session-1",
                linkedBaseSessionUuid = null,
                workflowId = "rover-ntrip",
                workflowName = "Rover with NTRIP",
                receiverRole = "ROVER",
                um980ProfileId = "um980-binary",
                commandProfileId = "cmd-1",
                usbBaudProfileId = "usb-1",
                ntripCasterProfileId = "caster-1",
                ntripMountpointProfileId = "mount-1",
                recordingPolicyId = "recording-1",
                storageProfileId = "storage-1",
                storageKind = "APP_PRIVATE",
                coordinateSource = "receiver",
                validationSummary = "valid",
                expectedArtifacts = listOf("receiver-rx.raw", "correction-input.raw"),
            ),
        )

        assertTrue(json.contains("\"appVersion\":\"0.1.0\""))
        assertTrue(json.contains("\"workflowId\":\"rover-ntrip\""))
        assertTrue(json.contains("\"workflowName\":\"Rover with NTRIP\""))
        assertTrue(json.contains("\"receiverRole\":\"ROVER\""))
        assertTrue(json.contains("\"um980ProfileId\":\"um980-binary\""))
        assertTrue(json.contains("\"expectedArtifacts\":[\"receiver-rx.raw\",\"correction-input.raw\"]"))
        assertTrue(json.contains("\"ntrip\":{"))
        assertTrue(json.contains("\"secretRef\":\"ntrip:caster.example:caster:user\""))
        assertFalse(json.contains("password", ignoreCase = true))
        assertFalse(json.contains("token", ignoreCase = true))
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

    private class TrackingOutputStream(
        private val name: String,
        private val operations: MutableList<String>,
        private val failFlush: Boolean = false,
        private val failClose: Boolean = false,
    ) : OutputStream() {
        var flushCount: Int = 0
            private set
        var closeCount: Int = 0
            private set

        override fun write(value: Int) = Unit

        override fun flush() {
            flushCount += 1
            operations += "$name:flush"
            if (failFlush) throw IOException("$name flush failed")
        }

        override fun close() {
            closeCount += 1
            operations += "$name:close"
            if (failClose) throw IOException("$name close failed")
        }
    }
}
