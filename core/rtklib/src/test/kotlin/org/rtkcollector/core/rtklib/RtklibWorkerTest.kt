package org.rtkcollector.core.rtklib

import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun `shutdown timeout keeps resources open until an uncooperative feed terminates`() {
        val nmea = CloseTrackingOutputStream()
        val pos = CloseTrackingOutputStream()
        val backend = UncooperativeBackend()
        val worker = RtklibWorker(
            backendFactory = object : RtklibBackendFactory {
                override fun create(): RtklibBackend = backend
            },
            outputWriters = RtklibOutputWriters(nmea, pos),
        )

        assertTrue(worker.start(validConfig()).started)
        assertTrue(worker.offerRoverBytes(byteArrayOf(1), 10L).accepted)
        assertTrue(backend.awaitFeedStarted())
        assertTrue(worker.offerRoverBytes(byteArrayOf(2, 3), 20L).accepted)

        assertFalse(worker.shutdown(timeoutMillis = 20L))
        assertTrue(backend.awaitCancellationRequest())
        assertTrue(backend.cancellationRequested)
        assertFalse(backend.closed)
        assertFalse(nmea.closed)
        assertFalse(pos.closed)
        assertEquals(0, worker.snapshot().roverQueueBytes)
        assertEquals(2L, worker.snapshot().droppedRoverBytes)

        backend.releaseFeed()

        assertTrue(worker.shutdown(timeoutMillis = 2_000L))
        assertTrue(backend.closed)
        assertTrue(nmea.closed)
        assertTrue(pos.closed)
        assertEquals("", nmea.toString(Charsets.US_ASCII.name()))
    }

    @Test
    fun `shutdown finalizes never-started output writers exactly once`() {
        val nmea = CloseTrackingOutputStream()
        val pos = CloseTrackingOutputStream()
        val worker = RtklibWorker(
            backendFactory = object : RtklibBackendFactory {
                override fun create(): RtklibBackend = error("A never-started worker must not create a backend")
            },
            outputWriters = RtklibOutputWriters(nmea, pos),
        )

        assertTrue(worker.shutdown(timeoutMillis = 0L))
        assertTrue(worker.shutdown(timeoutMillis = 0L))

        assertEquals(1, nmea.closeCalls)
        assertEquals(1, pos.closeCalls)
    }

    @Test
    fun `shutdown finalizes failed-start outputs without reclosing the backend`() {
        val nmea = CloseTrackingOutputStream()
        val pos = CloseTrackingOutputStream()
        val backend = StartRefusingBackend()
        val worker = RtklibWorker(
            backendFactory = object : RtklibBackendFactory {
                override fun create(): RtklibBackend = backend
            },
            outputWriters = RtklibOutputWriters(nmea, pos),
        )

        assertFalse(worker.start(validConfig()).started)
        assertEquals(1, backend.closeCalls)
        assertTrue(worker.shutdown(timeoutMillis = 0L))
        assertTrue(worker.shutdown(timeoutMillis = 0L))

        assertEquals(1, backend.closeCalls)
        assertEquals(1, nmea.closeCalls)
        assertEquals(1, pos.closeCalls)
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
        val ggaFields = nmeaText.lineSequence().first().substringAfter('$').substringBefore('*').split(',')
        assertEquals("", ggaFields[9], "Synthetic GGA must not put ellipsoidal height in the MSL altitude field.")
        assertEquals("", ggaFields[10], "Synthetic GGA must not claim an altitude unit without an MSL altitude.")
        assertEquals("", ggaFields[11], "Synthetic GGA must not claim a zero geoid separation for ellipsoidal height.")
        assertEquals("", ggaFields[12], "Synthetic GGA must not claim a geoid unit without geoid separation.")
        assertTrue(posText.contains("50.123456789"))
        assertTrue(posText.contains("310.2500"))
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

    @Test
    fun `native bridge parses optional RTKLIB satellite usage field`() {
        val api = object : FakeNativeApi() {
            override fun snapshot(handle: Long): Array<String> = arrayOf(
                "RUNNING",
                "",
                "",
                "",
                "",
                "RTK_FIXED",
                "1234",
                "50.1",
                "14.2",
                "300.0",
                "0.01",
                "0.02",
                "2",
                "4",
                "5",
                "G07,1C,45.5;E11,5X,39",
            )
        }
        val backend = RtklibNativeBridge(loadLibrary = {}, nativeApi = api).create()

        assertTrue(backend.start(validConfig()).started)
        val snapshot = backend.snapshot()

        assertEquals(4, snapshot.decodedRoverEpochs)
        assertEquals(5, snapshot.decodedCorrectionMessages)
        assertEquals(
            listOf(
                RtklibSatelliteUsage("G07", "1C", 45.5),
                RtklibSatelliteUsage("E11", "5X", 39.0),
            ),
            snapshot.latestSolution?.satelliteUsages,
        )
        backend.close()
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

    private class UncooperativeBackend : RtklibBackend {
        private val feedStarted = CountDownLatch(1)
        private val releaseFeed = CountDownLatch(1)
        private val cancellationRequestedLatch = CountDownLatch(1)

        @Volatile
        var cancellationRequested = false
            private set

        @Volatile
        var closed = false
            private set

        override fun start(config: RtklibConfig): RtklibStartResult = RtklibStartResult.started()

        override fun feed(chunk: RtklibInputChunk): RtklibNativeOutputBatch {
            feedStarted.countDown()
            releaseFeed.await()
            return RtklibNativeOutputBatch(nmeaLines = listOf("\$GPGGA,late"))
        }

        override fun snapshot(): RtklibEngineSnapshot =
            RtklibEngineSnapshot(state = RtklibEngineState.RUNNING)

        override fun stop() {
            cancellationRequested = true
            cancellationRequestedLatch.countDown()
        }

        override fun close() {
            closed = true
        }

        fun awaitFeedStarted(): Boolean = feedStarted.await(2, TimeUnit.SECONDS)

        fun awaitCancellationRequest(): Boolean = cancellationRequestedLatch.await(2, TimeUnit.SECONDS)

        fun releaseFeed() {
            releaseFeed.countDown()
        }
    }

    private class CloseTrackingOutputStream : ByteArrayOutputStream() {
        @Volatile
        var closeCalls = 0
            private set

        val closed: Boolean
            get() = closeCalls > 0

        override fun close() {
            closeCalls += 1
            super.close()
        }
    }

    private class StartRefusingBackend : RtklibBackend {
        var closeCalls = 0
            private set

        override fun start(config: RtklibConfig): RtklibStartResult =
            RtklibStartResult.failed("fixture backend refused to start")

        override fun feed(chunk: RtklibInputChunk): RtklibNativeOutputBatch = RtklibNativeOutputBatch()

        override fun snapshot(): RtklibEngineSnapshot =
            RtklibEngineSnapshot(state = RtklibEngineState.FAILED)

        override fun stop() = Unit

        override fun close() {
            closeCalls += 1
        }
    }

    private open class FakeNativeApi : RtklibNativeBridge.NativeApi {
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

        override open fun snapshot(handle: Long): Array<String> =
            arrayOf("RUNNING", "", "", "", "", "", "", "", "", "", "", "", "", "0", "0")

        override fun postprocess(
            preset: String,
            roverFormat: String,
            frequencyCount: Int,
            solutionType: String,
            receiverRxRaw: String,
            correctionRtcm3: String,
            outputNmea: String,
            outputPos: String,
        ): String? = null

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

        override fun postprocess(
            preset: String,
            roverFormat: String,
            frequencyCount: Int,
            solutionType: String,
            receiverRxRaw: String,
            correctionRtcm3: String,
            outputNmea: String,
            outputPos: String,
        ): String? = null

        override fun stop(handle: Long) = Unit
        override fun destroy(handle: Long) = Unit
    }
}
