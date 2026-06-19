package org.rtkcollector.core.rtklib

import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.rtkcollector.core.workflow.ReceiverCapabilityFixtures
import org.rtkcollector.core.workflow.RtklibInputRouter
import org.rtkcollector.core.workflow.WorkflowExamples

class RtklibWorkerTest {
    @Test
    fun `worker writes backend NMEA and POS output separately`() {
        val nmea = ByteArrayOutputStream()
        val pos = ByteArrayOutputStream()
        val backend = FakeBackend(
            output = RtklibNativeOutputBatch(
                nmeaLines = listOf("\$GPGGA,fixture"),
                posLines = listOf("%  GPST latitude(deg) longitude(deg)", "2026/01/01 1.0 2.0"),
            ),
            latch = CountDownLatch(1),
        )
        val worker = RtklibWorker(
            backendFactory = object : RtklibBackendFactory {
                override fun create(): RtklibBackend = backend
            },
            outputWriters = RtklibOutputWriters(nmea, pos),
        )

        assertTrue(worker.start(validConfig()).started)
        assertTrue(worker.offerRoverBytes(byteArrayOf(1, 2, 3), 10L).accepted)
        assertTrue(backend.await())
        worker.stop()

        assertEquals("\$GPGGA,fixture\n", nmea.toString(Charsets.US_ASCII.name()))
        assertTrue(pos.toString(Charsets.US_ASCII.name()).contains("2026/01/01"))
        assertEquals(1, worker.snapshot().outputNmeaLines)
        assertEquals(2, worker.snapshot().outputPosLines)
    }

    @Test
    fun `worker drops advisory input when rover queue is full`() {
        val backend = BlockingBackend()
        val worker = RtklibWorker(
            backendFactory = object : RtklibBackendFactory {
                override fun create(): RtklibBackend = backend
            },
            outputWriters = RtklibOutputWriters(null, null),
        )
        val config = validConfig().copy(maxRoverQueueBytes = 2)

        assertTrue(worker.start(config).started)
        val result = worker.offerRoverBytes(byteArrayOf(1, 2, 3, 4), 10L)
        worker.stop()

        assertEquals(RtklibOfferStatus.DROPPED_FULL, result.status)
        assertEquals(4, result.droppedBytes)
    }

    @Test
    fun `worker does not write outputs disabled by config`() {
        val nmea = ByteArrayOutputStream()
        val pos = ByteArrayOutputStream()
        val backend = FakeBackend(
            output = RtklibNativeOutputBatch(
                nmeaLines = listOf("\$GPGGA,fixture"),
                posLines = listOf("2026/01/01 1.0 2.0"),
            ),
            latch = CountDownLatch(1),
        )
        val worker = RtklibWorker(
            backendFactory = object : RtklibBackendFactory {
                override fun create(): RtklibBackend = backend
            },
            outputWriters = RtklibOutputWriters(nmea, pos),
        )
        val config = validConfig().copy(outputNmea = true, outputPos = false)

        assertTrue(worker.start(config).started)
        assertTrue(worker.offerRoverBytes(byteArrayOf(1, 2, 3), 10L).accepted)
        assertTrue(backend.await())
        worker.stop()

        assertEquals("\$GPGGA,fixture\n", nmea.toString(Charsets.US_ASCII.name()))
        assertEquals("", pos.toString(Charsets.US_ASCII.name()))
        assertEquals(1, worker.snapshot().outputNmeaLines)
        assertEquals(0, worker.snapshot().outputPosLines)
    }

    @Test
    fun `native bridge loads lazily only when a backend is created`() {
        var loads = 0
        val bridge = RtklibNativeBridge(
            loadLibrary = { loads++ },
            nativeApi = FakeNativeApi(),
        )

        assertEquals(0, loads)
        bridge.create()
        assertEquals(1, loads)
    }

    private fun validConfig(): RtklibConfig {
        val workflow = WorkflowExamples.roverWithRtklibRealtime(ReceiverCapabilityFixtures.ubloxM8p0())
        return RtklibConfig(
            routePlan = RtklibInputRouter().plan(workflow),
            preset = RtklibPreset.ROVER_KINEMATIC_RTK,
            receiverProfileId = "ublox-m8p",
            baseContextSummary = "NTRIP CORS01",
        )
    }

    private class FakeBackend(
        private val output: RtklibNativeOutputBatch,
        private val latch: CountDownLatch,
    ) : RtklibBackend {
        override fun start(config: RtklibConfig): RtklibStartResult = RtklibStartResult.started()

        override fun feed(chunk: RtklibInputChunk): RtklibNativeOutputBatch {
            latch.countDown()
            return output
        }

        override fun snapshot(): RtklibEngineSnapshot =
            RtklibEngineSnapshot(state = RtklibEngineState.RUNNING)

        override fun stop() = Unit

        fun await(): Boolean = latch.await(2, TimeUnit.SECONDS)
    }

    private class BlockingBackend : RtklibBackend {
        override fun start(config: RtklibConfig): RtklibStartResult = RtklibStartResult.started()

        override fun feed(chunk: RtklibInputChunk): RtklibNativeOutputBatch {
            Thread.sleep(500L)
            return RtklibNativeOutputBatch()
        }

        override fun snapshot(): RtklibEngineSnapshot =
            RtklibEngineSnapshot(state = RtklibEngineState.RUNNING)

        override fun stop() = Unit
    }

    private class FakeNativeApi : RtklibNativeBridge.NativeApi {
        override fun version(): String = "test"
        override fun create(): Long = 1L
        override fun start(
            handle: Long,
            preset: String,
            roverFormat: String,
            correctionFormat: String,
            outputNmea: Boolean,
            outputPos: Boolean,
        ): String? = null

        override fun feed(handle: Long, streamKind: Int, bytes: ByteArray): Array<String> =
            arrayOf("RUNNING", "", "", "", "", "", "", "", "", "", "", "", "", "0", "0")

        override fun snapshot(handle: Long): Array<String> =
            arrayOf("RUNNING", "", "", "", "", "", "", "", "", "", "", "", "", "0", "0")

        override fun stop(handle: Long) = Unit
        override fun destroy(handle: Long) = Unit
    }
}
