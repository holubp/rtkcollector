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
                solution = RtklibSolutionSnapshot(
                    fixClass = RtklibFixClass.RTK_FIXED,
                    timestampMillis = 1_234L,
                    latDeg = 50.1,
                    lonDeg = 14.2,
                    ellipsoidalHeightM = 300.0,
                    horizontalAccuracyM = 0.01,
                    verticalAccuracyM = 0.02,
                    satellitesUsed = 12,
                ),
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
        val snapshot = waitForSolution(worker)
        assertEquals(RtklibFixClass.RTK_FIXED, snapshot.latestSolution?.fixClass)
        assertEquals(50.1, snapshot.latestSolution?.latDeg)
        assertEquals(14.2, snapshot.latestSolution?.lonDeg)
        assertEquals(0.01, snapshot.latestSolution?.horizontalAccuracyM)

        assertEquals("\$GPGGA,fixture\n", nmea.toString(Charsets.US_ASCII.name()))
        assertTrue(pos.toString(Charsets.US_ASCII.name()).contains("2026/01/01"))
        assertEquals(1, worker.snapshot().outputNmeaLines)
        assertEquals(2, worker.snapshot().outputPosLines)
        worker.stop()
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
    fun `worker synthesizes output lines when native text streams are empty`() {
        val nmea = ByteArrayOutputStream()
        val pos = ByteArrayOutputStream()
        val backend = FakeBackend(
            output = RtklibNativeOutputBatch(
                solution = RtklibSolutionSnapshot(
                    fixClass = RtklibFixClass.RTK_FLOAT,
                    timestampMillis = 1_782_122_587_188L,
                    latDeg = 50.123456789,
                    lonDeg = 14.987654321,
                    ellipsoidalHeightM = 310.25,
                    horizontalAccuracyM = 0.123,
                    verticalAccuracyM = 0.456,
                    satellitesUsed = 14,
                ),
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
        assertTrue(waitForSolution(worker).latestSolution != null)
        worker.stop()

        val nmeaText = nmea.toString(Charsets.US_ASCII.name())
        val posText = pos.toString(Charsets.US_ASCII.name())
        assertTrue(nmeaText.startsWith("\$GPGGA,"))
        assertTrue(nmeaText.contains(",5,14,"))
        assertTrue(posText.contains("50.123456789"))
        assertTrue(posText.contains("RTK_FLOAT"))
        assertEquals(1, worker.snapshot().outputNmeaLines)
        assertEquals(1, worker.snapshot().outputPosLines)
    }

    @Test
    fun `worker does not repeat identical synthetic solution output`() {
        val nmea = ByteArrayOutputStream()
        val pos = ByteArrayOutputStream()
        val solution = RtklibSolutionSnapshot(
            fixClass = RtklibFixClass.RTK_FLOAT,
            timestampMillis = 1_782_122_587_188L,
            latDeg = 50.123456789,
            lonDeg = 14.987654321,
            ellipsoidalHeightM = 310.25,
            horizontalAccuracyM = 0.123,
            verticalAccuracyM = 0.456,
            satellitesUsed = 14,
        )
        val backend = FakeBackend(
            output = RtklibNativeOutputBatch(solution = solution),
            latch = CountDownLatch(2),
        )
        val worker = RtklibWorker(
            backendFactory = object : RtklibBackendFactory {
                override fun create(): RtklibBackend = backend
            },
            outputWriters = RtklibOutputWriters(nmea, pos),
        )

        assertTrue(worker.start(validConfig()).started)
        assertTrue(worker.offerRoverBytes(byteArrayOf(1), 10L).accepted)
        assertTrue(worker.offerRoverBytes(byteArrayOf(2), 20L).accepted)
        assertTrue(backend.await())
        worker.stop()

        assertEquals(1, nmea.toString(Charsets.US_ASCII.name()).lineSequence().filter(String::isNotBlank).count())
        assertEquals(1, pos.toString(Charsets.US_ASCII.name()).lineSequence().filter(String::isNotBlank).count())
        assertEquals(1, worker.snapshot().outputNmeaLines)
        assertEquals(1, worker.snapshot().outputPosLines)
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

    @Test
    fun `native bridge passes rtklib server runtime parameters`() {
        val api = FakeNativeApi()
        val bridge = RtklibNativeBridge(
            loadLibrary = {},
            nativeApi = api,
        )
        val backend = bridge.create()

        backend.start(
            validConfig().copy(
                frequencyCount = 1,
                serverCycleMillis = 50,
                serverBufferBytes = 65536,
                solutionBufferBytes = 65536,
            ),
        )

        assertEquals(1, api.frequencyCount)
        assertEquals(50, api.serverCycleMillis)
        assertEquals(65536, api.serverBufferBytes)
        assertEquals(65536, api.solutionBufferBytes)
    }

    @Test
    fun `native bridge exposes only new lines from cumulative text outputs`() {
        val api = CumulativeOutputNativeApi()
        val bridge = RtklibNativeBridge(
            loadLibrary = {},
            nativeApi = api,
        )
        val backend = bridge.create()
        assertTrue(backend.start(validConfig()).started)

        val first = backend.feed(RtklibInputChunk(RtklibInputStreamKind.ROVER, byteArrayOf(1), 1L))
        val second = backend.feed(RtklibInputChunk(RtklibInputStreamKind.ROVER, byteArrayOf(2), 2L))

        assertEquals(listOf("\$GPGGA,first"), first.nmeaLines)
        assertEquals(listOf("pos-first"), first.posLines)
        assertEquals(listOf("\$GPGGA,second"), second.nmeaLines)
        assertEquals(listOf("pos-second"), second.posLines)
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

    private fun waitForSolution(worker: RtklibWorker): RtklibEngineSnapshot {
        repeat(20) {
            val snapshot = worker.snapshot()
            if (snapshot.latestSolution != null) return snapshot
            Thread.sleep(25L)
        }
        return worker.snapshot()
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
        var frequencyCount: Int = -1
        var serverCycleMillis: Int = -1
        var serverBufferBytes: Int = -1
        var solutionBufferBytes: Int = -1

        override fun version(): String = "test"
        override fun create(): Long = 1L
        override fun start(
            handle: Long,
            preset: String,
            roverFormat: String,
            correctionFormat: String,
            outputNmea: Boolean,
            outputPos: Boolean,
            frequencyCount: Int,
            serverCycleMillis: Int,
            serverBufferBytes: Int,
            solutionBufferBytes: Int,
        ): String? {
            this.frequencyCount = frequencyCount
            this.serverCycleMillis = serverCycleMillis
            this.serverBufferBytes = serverBufferBytes
            this.solutionBufferBytes = solutionBufferBytes
            return null
        }

        override fun feed(handle: Long, streamKind: Int, bytes: ByteArray): Array<String> =
            arrayOf("RUNNING", "", "", "", "", "", "", "", "", "", "", "", "", "0", "0")

        override fun snapshot(handle: Long): Array<String> =
            arrayOf("RUNNING", "", "", "", "", "", "", "", "", "", "", "", "", "0", "0")

        override fun stop(handle: Long) = Unit
        override fun destroy(handle: Long) = Unit
    }

    private class CumulativeOutputNativeApi : RtklibNativeBridge.NativeApi {
        private var feeds = 0

        override fun version(): String = "test"
        override fun create(): Long = 1L
        override fun start(
            handle: Long,
            preset: String,
            roverFormat: String,
            correctionFormat: String,
            outputNmea: Boolean,
            outputPos: Boolean,
            frequencyCount: Int,
            serverCycleMillis: Int,
            serverBufferBytes: Int,
            solutionBufferBytes: Int,
        ): String? = null

        override fun feed(handle: Long, streamKind: Int, bytes: ByteArray): Array<String> {
            feeds += 1
            val nmea = if (feeds == 1) {
                "\$GPGGA,first\n"
            } else {
                "\$GPGGA,first\n\$GPGGA,second\n"
            }
            val pos = if (feeds == 1) {
                "pos-first\n"
            } else {
                "pos-first\npos-second\n"
            }
            return arrayOf("RUNNING", "", "", nmea, pos, "", "", "", "", "", "", "", "", "0", "0")
        }

        override fun snapshot(handle: Long): Array<String> =
            arrayOf("RUNNING", "", "", "", "", "", "", "", "", "", "", "", "", "0", "0")

        override fun stop(handle: Long) = Unit
        override fun destroy(handle: Long) = Unit
    }
}
