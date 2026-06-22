package org.rtkcollector.app.recording

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.rtkcollector.core.rtklib.RtklibPostprocessBackend
import org.rtkcollector.core.rtklib.RtklibPostprocessMode
import org.rtkcollector.core.rtklib.RtklibPostprocessRequest
import org.rtkcollector.core.rtklib.RtklibPostprocessResult

class RtklibSessionPostprocessorTest {
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
        assertEquals(session.resolve("rtklib-postprocessed-forward.nmea"), request.outputNmea)
        assertEquals(session.resolve("rtklib-postprocessed-forward.pos"), request.outputPos)
        assertEquals("UBX_RXM_RAWX_SFRBX", request.roverFormat)
        assertEquals(1, request.frequencyCount)
        assertEquals(RtklibPostprocessMode.FORWARD, request.mode)
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
        assertEquals(session.resolve("rtklib-postprocessed-combined.nmea"), request.outputNmea)
        assertEquals(session.resolve("rtklib-postprocessed-combined.pos"), request.outputPos)
        assertEquals(RtklibPostprocessMode.FORWARD_BACKWARD, request.mode)
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
        Files.writeString(session.resolve("rtklib-postprocessed-forward.nmea"), "old nmea\n")
        Files.writeString(session.resolve("rtklib-postprocessed-forward.pos"), "old pos\n")

        val result = RtklibSessionPostprocessor.postprocessFilesystemSession(
            sessionDirectory = session,
            mode = RtklibPostprocessMode.FORWARD,
            backend = EmptyPosPostprocessBackend(),
        )

        assertFalse(result.success)
        assertEquals("old nmea\n", Files.readString(session.resolve("rtklib-postprocessed-forward.nmea")))
        assertEquals("old pos\n", Files.readString(session.resolve("rtklib-postprocessed-forward.pos")))
    }

    @Test
    fun `postprocess reports malformed session metadata as structured failure`() {
        val session = ubloxRtklibSession()
        Files.writeString(session.resolve("session.json"), "{")

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
        Files.writeString(session.resolve("receiver-rx.raw"), "receiver")
        Files.writeString(session.resolve("correction-input.rtcm3"), "correction")
        Files.writeString(
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
            Files.writeString(request.outputNmea, "\$GPGGA,postprocessed\n")
            Files.writeString(request.outputPos, "postprocessed pos\n")
            return RtklibPostprocessResult.success()
        }
    }

    private class ThrowingPostprocessBackend : RtklibPostprocessBackend {
        override fun postprocess(request: RtklibPostprocessRequest): RtklibPostprocessResult {
            Files.writeString(request.outputNmea, "\$GPGGA,partial\n")
            Files.writeString(request.outputPos, "partial pos\n")
            error("native failure")
        }
    }

    private class EmptyPosPostprocessBackend : RtklibPostprocessBackend {
        override fun postprocess(request: RtklibPostprocessRequest): RtklibPostprocessResult {
            Files.writeString(request.outputNmea, "\$GPGGA,new\n")
            Files.writeString(request.outputPos, "")
            return RtklibPostprocessResult.success()
        }
    }
}
