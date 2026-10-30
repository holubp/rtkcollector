package org.rtkcollector.app.recording

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.rtkcollector.app.testing.TestFiles
import org.rtkcollector.core.rtklib.RtklibPostprocessBackend
import org.rtkcollector.core.rtklib.RtklibPostprocessMode
import org.rtkcollector.core.rtklib.RtklibPostprocessRequest
import org.rtkcollector.core.rtklib.RtklibPostprocessResult

class RtklibSessionPostprocessorTest {
    @Test
    fun `backup cleanup failure preserves the complete published pair`() {
        val session = createTempDirectory("rtklib-publish-cleanup")
        val temporaryNmea = TestFiles.writeString(session.resolve("new.nmea.tmp"), "new nmea\n")
        val temporaryPos = TestFiles.writeString(session.resolve("new.pos.tmp"), "new pos\n")
        val outputNmea = TestFiles.writeString(session.resolve("new.nmea"), "old nmea\n")
        val outputPos = TestFiles.writeString(session.resolve("new.pos"), "old pos\n")
        val fileOperations = FaultingPublicationFileOperations(
            failDelete = { path -> path == outputPos.resolveSibling("new.pos.bak") && Files.exists(path) },
        )

        val result = RtklibSessionPostprocessor.publishOutputPair(
            temporaryNmea,
            temporaryPos,
            outputNmea,
            outputPos,
            fileOperations,
        )

        assertTrue(result.success)
        assertEquals("new nmea\n", TestFiles.readString(outputNmea))
        assertEquals("new pos\n", TestFiles.readString(outputPos))
        assertTrue(Files.exists(outputPos.resolveSibling("new.pos.bak")))
    }

    @Test
    fun `publication failure restores both previous outputs`() {
        val session = createTempDirectory("rtklib-publish-failure")
        val temporaryNmea = TestFiles.writeString(session.resolve("new.nmea.tmp"), "new nmea\n")
        val temporaryPos = TestFiles.writeString(session.resolve("new.pos.tmp"), "new pos\n")
        val outputNmea = TestFiles.writeString(session.resolve("new.nmea"), "old nmea\n")
        val outputPos = TestFiles.writeString(session.resolve("new.pos"), "old pos\n")
        val fileOperations = FaultingPublicationFileOperations(
            failMove = { source, target -> source == temporaryPos && target == outputPos },
        )

        val result = RtklibSessionPostprocessor.publishOutputPair(
            temporaryNmea,
            temporaryPos,
            outputNmea,
            outputPos,
            fileOperations,
        )

        assertFalse(result.success)
        assertEquals("old nmea\n", TestFiles.readString(outputNmea))
        assertEquals("old pos\n", TestFiles.readString(outputPos))
    }

    @Test
    fun `forward postprocess uses receiver raw and rtcm3 corrections`() {
        val session = ubloxRtklibSession()
        val backend = CapturingPostprocessBackend()

        val result = RtklibSessionPostprocessor.postprocessFilesystemSession(
            sessionDirectory = session,
            mode = RtklibPostprocessMode.FORWARD,
            backend = backend,
        )

        assertTrue(result.success)
        val request = backend.requests.single()
        assertEquals(session.resolve("receiver-rx.raw"), request.receiverRxRaw)
        assertEquals(session.resolve("correction-input.rtcm3"), request.correctionRtcm3)
        assertEquals(session.resolve("rtklib-postprocessed-forward.nmea.tmp"), request.outputNmea)
        assertEquals(session.resolve("rtklib-postprocessed-forward.pos.tmp"), request.outputPos)
        assertEquals("UBX_RXM_RAWX_SFRBX", request.roverFormat)
        assertEquals(1, request.frequencyCount)
        assertEquals(RtklibPostprocessMode.FORWARD, request.mode)
        assertTrue(Files.isRegularFile(session.resolve("rtklib-postprocessed-forward.nmea")))
        assertTrue(Files.isRegularFile(session.resolve("rtklib-postprocessed-forward.pos")))
        assertFalse(Files.exists(request.outputNmea))
        assertFalse(Files.exists(request.outputPos))
    }

    @Test
    fun `combined postprocess writes combined artifact names`() {
        val session = ubloxRtklibSession()
        val backend = CapturingPostprocessBackend()

        RtklibSessionPostprocessor.postprocessFilesystemSession(
            sessionDirectory = session,
            mode = RtklibPostprocessMode.FORWARD_BACKWARD,
            backend = backend,
        )

        val request = backend.requests.single()
        assertEquals(session.resolve("rtklib-postprocessed-combined.nmea.tmp"), request.outputNmea)
        assertEquals(session.resolve("rtklib-postprocessed-combined.pos.tmp"), request.outputPos)
        assertEquals(RtklibPostprocessMode.FORWARD_BACKWARD, request.mode)
        assertTrue(Files.isRegularFile(session.resolve("rtklib-postprocessed-combined.nmea")))
        assertTrue(Files.isRegularFile(session.resolve("rtklib-postprocessed-combined.pos")))
    }

    @Test
    fun `postprocess rejects sessions without rtcm3 corrections`() {
        val session = ubloxRtklibSession()
        Files.delete(session.resolve("correction-input.rtcm3"))

        val result = RtklibSessionPostprocessor.postprocessFilesystemSession(
            sessionDirectory = session,
            mode = RtklibPostprocessMode.FORWARD,
            backend = CapturingPostprocessBackend(),
        )

        assertFalse(result.success)
        assertTrue(result.message.orEmpty().contains("correction-input.rtcm3"))
    }

    @Test
    fun `postprocess cleans temporary outputs when backend throws`() {
        val session = ubloxRtklibSession()

        val result = RtklibSessionPostprocessor.postprocessFilesystemSession(
            sessionDirectory = session,
            mode = RtklibPostprocessMode.FORWARD,
            backend = ThrowingPostprocessBackend(),
        )

        assertFalse(result.success)
        assertFalse(Files.exists(session.resolve("rtklib-postprocessed-forward.nmea.tmp")))
        assertFalse(Files.exists(session.resolve("rtklib-postprocessed-forward.pos.tmp")))
        assertFalse(Files.exists(session.resolve("rtklib-postprocessed-forward.nmea")))
        assertFalse(Files.exists(session.resolve("rtklib-postprocessed-forward.pos")))
    }

    @Test
    fun `postprocess replaces final artifact pair only after both outputs are valid`() {
        val session = ubloxRtklibSession()
        TestFiles.writeString(session.resolve("rtklib-postprocessed-forward.nmea"), "old nmea\n")
        TestFiles.writeString(session.resolve("rtklib-postprocessed-forward.pos"), "old pos\n")

        val result = RtklibSessionPostprocessor.postprocessFilesystemSession(
            sessionDirectory = session,
            mode = RtklibPostprocessMode.FORWARD,
            backend = EmptyPosPostprocessBackend(),
        )

        assertFalse(result.success)
        assertEquals("old nmea\n", TestFiles.readString(session.resolve("rtklib-postprocessed-forward.nmea")))
        assertEquals("old pos\n", TestFiles.readString(session.resolve("rtklib-postprocessed-forward.pos")))
    }

    @Test
    fun `postprocess reports malformed session metadata as structured failure`() {
        val session = ubloxRtklibSession()
        TestFiles.writeString(session.resolve("session.json"), "{")

        val result = RtklibSessionPostprocessor.postprocessFilesystemSession(
            sessionDirectory = session,
            mode = RtklibPostprocessMode.FORWARD,
            backend = CapturingPostprocessBackend(),
        )

        assertFalse(result.success)
        assertTrue(result.message.orEmpty().contains("session.json"))
    }

    private fun ubloxRtklibSession(): Path {
        val session = createTempDirectory("rtklib-postprocess-test")
        TestFiles.writeString(session.resolve("receiver-rx.raw"), "receiver")
        TestFiles.writeString(session.resolve("correction-input.rtcm3"), "correction")
        TestFiles.writeString(
            session.resolve("session.json"),
            """
            {
              "receiverDriverId": "ublox-m8t",
              "rtklibPreset": "ROVER_KINEMATIC_RTK",
              "rtklibRoutePlan": "rover=input_ubx(UBX_RXM_RAWX_SFRBX); correction=input_rtcm3(RTCM3); direction=FORWARD_ONLY",
              "rtklibFrequencyCount": 1
            }
            """.trimIndent(),
        )
        return session
    }

    private class CapturingPostprocessBackend : RtklibPostprocessBackend {
        val requests = mutableListOf<RtklibPostprocessRequest>()

        override fun postprocess(request: RtklibPostprocessRequest): RtklibPostprocessResult {
            requests += request
            TestFiles.writeString(request.outputNmea, "\$GPGGA,postprocessed\n")
            TestFiles.writeString(request.outputPos, "postprocessed pos\n")
            return RtklibPostprocessResult.success()
        }
    }

    private class ThrowingPostprocessBackend : RtklibPostprocessBackend {
        override fun postprocess(request: RtklibPostprocessRequest): RtklibPostprocessResult {
            TestFiles.writeString(request.outputNmea, "\$GPGGA,partial\n")
            TestFiles.writeString(request.outputPos, "partial pos\n")
            error("native failure")
        }
    }

    private class EmptyPosPostprocessBackend : RtklibPostprocessBackend {
        override fun postprocess(request: RtklibPostprocessRequest): RtklibPostprocessResult {
            TestFiles.writeString(request.outputNmea, "\$GPGGA,new\n")
            TestFiles.writeString(request.outputPos, "")
            return RtklibPostprocessResult.success()
        }
    }

    private class FaultingPublicationFileOperations(
        private val failDelete: (Path) -> Boolean = { false },
        private val failMove: (Path, Path) -> Boolean = { _, _ -> false },
    ) : PublicationFileOperations {
        override fun exists(path: Path): Boolean = Files.exists(path)

        override fun move(source: Path, target: Path) {
            if (failMove(source, target)) error("injected publication failure")
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
        }

        override fun deleteIfExists(path: Path) {
            if (failDelete(path)) error("injected backup cleanup failure")
            Files.deleteIfExists(path)
        }
    }
}
