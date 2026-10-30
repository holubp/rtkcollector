package org.rtkcollector.core.rtklib

import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.rtkcollector.core.workflow.ReceiverCapabilityFixtures
import org.rtkcollector.core.workflow.RtklibInputRouter
import org.rtkcollector.core.workflow.WorkflowExamples

class RtklibWorkerTest {
    @Test
    fun `failed native snapshot fences input and publishes failure without blocking producers`() {
        val sampling = CountDownLatch(1)
        val release = CountDownLatch(1)
        val failedStatus = CountDownLatch(1)
        val closed = CountDownLatch(1)
        val feeds = java.util.concurrent.atomic.AtomicInteger()
        val backend = object : RtklibBackend {
            override fun start(config: RtklibConfig) = RtklibStartResult.started()
            override fun feed(chunk: RtklibInputChunk): RtklibNativeOutputBatch {
                feeds.incrementAndGet()
                return RtklibNativeOutputBatch()
            }
            override fun snapshot(): RtklibEngineSnapshot {
                sampling.countDown()
                release.await()
                return RtklibEngineSnapshot(state = RtklibEngineState.FAILED, lastError = "native worker exited")
            }
            override fun stop() = Unit
            override fun close() { closed.countDown() }
        }
        val worker = RtklibWorker(object : RtklibBackendFactory { override fun create() = backend },
            RtklibOutputWriters(null, null), statusSink = {
                if (it.state == RtklibEngineState.FAILED) failedStatus.countDown()
            })
        try {
            assertTrue(worker.startAsync(validConfig()).started)
            assertTrue(sampling.await(2, TimeUnit.SECONDS))
            assertTrue(worker.offerRoverBytes(byteArrayOf(1, 2), 10).accepted)
            repeat(100) { assertEquals(RtklibEngineState.RUNNING, worker.snapshot().state) }
            release.countDown()
            assertTrue(failedStatus.await(2, TimeUnit.SECONDS))
            assertEquals(RtklibEngineState.FAILED, worker.snapshot().state)
            assertEquals("native worker exited", worker.snapshot().lastError)
            assertFalse(worker.offerRoverBytes(byteArrayOf(3), 11).accepted)
            assertEquals(0, feeds.get())
            assertEquals(2L, worker.snapshot().droppedRoverBytes)
            assertTrue(closed.await(2, TimeUnit.SECONDS))
        } finally {
            release.countDown()
            assertTrue(worker.shutdown(2_000))
        }
    }

    @Test
    fun `native failed feed result is not an empty successful batch`() {
        val api = object : FakeNativeApi() {
            override fun feed(handle: Long, streamKind: Int, bytes: ByteArray): Array<String> =
                Array(16) { "" }.also { it[0] = "FAILED"; it[2] = "native feed failed" }
        }
        val backend = RtklibNativeBridge(loadLibrary = {}, nativeApi = api).create()
        try {
            assertTrue(backend.start(validConfig()).started)
            val error = assertFailsWith<IllegalStateException> {
                backend.feed(RtklibInputChunk(RtklibInputStreamKind.ROVER, byteArrayOf(1), 10))
            }
            assertEquals("native feed failed", error.message)
        } finally { backend.close() }
    }

    @Test
    fun `feed failure caused by deliberate Stop does not report unexpected failure`() {
        val feeding = CountDownLatch(1)
        val release = CountDownLatch(1)
        val backend = object : RtklibBackend {
            override fun start(config: RtklibConfig) = RtklibStartResult.started()
            override fun feed(chunk: RtklibInputChunk): RtklibNativeOutputBatch {
                feeding.countDown()
                release.await()
                error("normal cancellation")
            }
            override fun snapshot() = RtklibEngineSnapshot(state = RtklibEngineState.RUNNING)
            override fun stop() { release.countDown() }
        }
        val worker = RtklibWorker(object : RtklibBackendFactory { override fun create() = backend },
            RtklibOutputWriters(null, null))
        try {
            assertTrue(worker.start(validConfig()).started)
            assertTrue(worker.offerRoverBytes(byteArrayOf(1), 10).accepted)
            assertTrue(feeding.await(2, TimeUnit.SECONDS))
            assertTrue(worker.shutdown(2_000))
            assertEquals(RtklibEngineState.STOPPED, worker.snapshot().state)
            assertEquals(null, worker.snapshot().lastError)
        } finally {
            release.countDown()
            assertTrue(worker.shutdown(2_000))
        }
    }

    @Test
    fun `blocked startup accepts bounded early rover and correction input then feeds FIFO only after init`() {
        val initEntered = CountDownLatch(1)
        val releaseInit = CountDownLatch(1)
        val offered = CountDownLatch(1)
        val fed = CountDownLatch(4)
        val received = java.util.concurrent.CopyOnWriteArrayList<RtklibInputChunk>()
        val producerFailure = java.util.concurrent.atomic.AtomicReference<Throwable?>()
        val nativeSnapshots = java.util.concurrent.atomic.AtomicInteger()
        val backend = object : RtklibBackend {
            override fun start(config: RtklibConfig): RtklibStartResult {
                initEntered.countDown()
                releaseInit.await()
                return RtklibStartResult.started()
            }
            override fun feed(chunk: RtklibInputChunk): RtklibNativeOutputBatch {
                received.add(chunk)
                fed.countDown()
                return RtklibNativeOutputBatch()
            }
            override fun snapshot(): RtklibEngineSnapshot {
                nativeSnapshots.incrementAndGet()
                return RtklibEngineSnapshot(state = RtklibEngineState.RUNNING)
            }
            override fun stop() = Unit
        }
        val worker = RtklibWorker(object : RtklibBackendFactory { override fun create() = backend },
            RtklibOutputWriters(null, null))
        val producer = Thread {
            try {
                val initialRover = byteArrayOf(1, 2)
                assertTrue(worker.offerRoverBytes(initialRover, 11, 0).accepted)
                initialRover[0] = 99
                assertTrue(worker.offerCorrectionBytes(byteArrayOf(8), 12, 3).accepted)
                assertTrue(worker.offerRoverBytes(byteArrayOf(3), 13, 2).accepted)
                assertTrue(worker.offerCorrectionBytes(byteArrayOf(9), 14, 4).accepted)
                assertEquals(RtklibOfferStatus.DROPPED_FULL, worker.offerRoverBytes(byteArrayOf(4), 15).status)
                assertEquals(RtklibOfferStatus.DROPPED_FULL, worker.offerCorrectionBytes(byteArrayOf(10, 11), 16).status)
                repeat(100) { worker.snapshot() }
            } catch (error: Throwable) { producerFailure.set(error) }
            finally { offered.countDown() }
        }
        try {
            assertTrue(worker.startAsync(validConfig().copy(maxRoverQueueBytes = 3, maxCorrectionQueueBytes = 2)).started)
            assertTrue(initEntered.await(2, TimeUnit.SECONDS))
            producer.start()
            assertTrue(offered.await(1, TimeUnit.SECONDS), "Early admission cannot wait for native init")
            assertEquals(null, producerFailure.get())
            val queued = worker.snapshot()
            assertEquals(RtklibEngineState.STARTING, queued.state)
            assertEquals(3, queued.roverQueueBytes)
            assertEquals(2, queued.correctionQueueBytes)
            assertEquals(1L, queued.droppedRoverBytes)
            assertEquals(2L, queued.droppedCorrectionBytes)
            assertEquals(0, nativeSnapshots.get())
            assertTrue(received.isEmpty(), "No backend feed before successful native init")
            releaseInit.countDown()
            assertTrue(fed.await(2, TimeUnit.SECONDS))
            assertEquals(listOf(listOf<Byte>(1, 2), listOf<Byte>(8), listOf<Byte>(3), listOf<Byte>(9)), received.map { it.bytes.toList() })
            assertEquals(listOf(RtklibInputStreamKind.ROVER, RtklibInputStreamKind.CORRECTION,
                RtklibInputStreamKind.ROVER, RtklibInputStreamKind.CORRECTION), received.map { it.streamKind })
            assertEquals(listOf(11L, 12L, 13L, 14L), received.map { it.timestampMillis })
            assertEquals(listOf(0L, 3L, 2L, 4L), received.map { it.sessionOffsetBytes })
            assertEquals(0, worker.snapshot().roverQueueBytes)
            assertEquals(0, worker.snapshot().correctionQueueBytes)
        } finally {
            releaseInit.countDown()
            producer.join(2_000)
            assertTrue(worker.shutdown(2_000))
        }
    }

    @Test
    fun `Stop discards early queued input and retains ownership until init and close quiesce`() {
        val initEntered = CountDownLatch(1)
        val releaseInit = CountDownLatch(1)
        val closeEntered = CountDownLatch(1)
        val releaseClose = CountDownLatch(1)
        val feeds = java.util.concurrent.atomic.AtomicInteger()
        val outputs = CloseTrackingOutputStream()
        val backend = object : RtklibBackend {
            override fun start(config: RtklibConfig): RtklibStartResult {
                initEntered.countDown(); releaseInit.await()
                return RtklibStartResult.started()
            }
            override fun feed(chunk: RtklibInputChunk): RtklibNativeOutputBatch {
                feeds.incrementAndGet(); return RtklibNativeOutputBatch()
            }
            override fun snapshot() = RtklibEngineSnapshot(state = RtklibEngineState.RUNNING)
            override fun stop() = Unit
            override fun close() { closeEntered.countDown(); releaseClose.await() }
        }
        val worker = RtklibWorker(object : RtklibBackendFactory { override fun create() = backend },
            RtklibOutputWriters(outputs, null))
        try {
            assertTrue(worker.startAsync(validConfig().copy(maxRoverQueueBytes = 3, maxCorrectionQueueBytes = 3)).started)
            assertTrue(initEntered.await(2, TimeUnit.SECONDS))
            assertTrue(worker.offerRoverBytes(byteArrayOf(1, 2), 10).accepted)
            assertTrue(worker.offerCorrectionBytes(byteArrayOf(3, 4, 5), 11).accepted)
            assertEquals(RtklibOfferStatus.DROPPED_FULL, worker.offerRoverBytes(byteArrayOf(6, 7), 12).status)
            assertEquals(RtklibOfferStatus.DROPPED_FULL, worker.offerCorrectionBytes(byteArrayOf(8), 13).status)
            assertFalse(worker.shutdown(20))
            val discarded = worker.snapshot()
            assertEquals(0, discarded.roverQueueBytes)
            assertEquals(0, discarded.correctionQueueBytes)
            assertEquals(4L, discarded.droppedRoverBytes)
            assertEquals(4L, discarded.droppedCorrectionBytes)
            assertEquals(RtklibOfferStatus.STOPPED, worker.offerRoverBytes(byteArrayOf(9), 14).status)
            assertFalse(outputs.closed)
            releaseInit.countDown()
            assertTrue(closeEntered.await(2, TimeUnit.SECONDS))
            assertFalse(worker.shutdown(20))
            assertFalse(outputs.closed)
            assertEquals(0, feeds.get())
            assertFalse(worker.snapshot().state == RtklibEngineState.RUNNING)
        } finally {
            releaseInit.countDown(); releaseClose.countDown()
            assertTrue(worker.shutdown(2_000))
        }
        assertEquals(0, feeds.get())
        assertEquals(4L, worker.snapshot().droppedRoverBytes)
        assertEquals(4L, worker.snapshot().droppedCorrectionBytes)
        assertEquals(1, outputs.closeCalls)
    }

    @Test
    fun `refused or throwing init fences and counts early discard without feeding or releasing active close`() {
        for (throws in listOf(false, true)) {
            val initEntered = CountDownLatch(1)
            val releaseInit = CountDownLatch(1)
            val closeEntered = CountDownLatch(1)
            val releaseClose = CountDownLatch(1)
            val feeds = java.util.concurrent.atomic.AtomicInteger()
            val outputs = CloseTrackingOutputStream()
            val backend = object : RtklibBackend {
                override fun start(config: RtklibConfig): RtklibStartResult {
                    initEntered.countDown(); releaseInit.await()
                    if (throws) error("init failed")
                    return RtklibStartResult.failed("init refused")
                }
                override fun feed(chunk: RtklibInputChunk): RtklibNativeOutputBatch {
                    feeds.incrementAndGet(); return RtklibNativeOutputBatch()
                }
                override fun snapshot() = RtklibEngineSnapshot(state = RtklibEngineState.FAILED)
                override fun stop() = Unit
                override fun close() { closeEntered.countDown(); releaseClose.await() }
            }
            val worker = RtklibWorker(object : RtklibBackendFactory { override fun create() = backend },
                RtklibOutputWriters(outputs, null))
            try {
                assertTrue(worker.startAsync(validConfig().copy(maxRoverQueueBytes = 2, maxCorrectionQueueBytes = 1)).started)
                assertTrue(initEntered.await(2, TimeUnit.SECONDS))
                assertTrue(worker.offerRoverBytes(byteArrayOf(1, 2), 10).accepted)
                assertTrue(worker.offerCorrectionBytes(byteArrayOf(3), 11).accepted)
                assertEquals(RtklibOfferStatus.DROPPED_FULL, worker.offerRoverBytes(byteArrayOf(4), 12).status)
                assertEquals(RtklibOfferStatus.DROPPED_FULL, worker.offerCorrectionBytes(byteArrayOf(5, 6), 13).status)
                releaseInit.countDown()
                assertTrue(closeEntered.await(2, TimeUnit.SECONDS))
                val failed = worker.snapshot()
                assertEquals(RtklibEngineState.FAILED, failed.state)
                assertEquals(0, failed.roverQueueBytes)
                assertEquals(0, failed.correctionQueueBytes)
                assertEquals(3L, failed.droppedRoverBytes)
                assertEquals(3L, failed.droppedCorrectionBytes)
                assertEquals(RtklibOfferStatus.STOPPED, worker.offerRoverBytes(byteArrayOf(7), 14).status)
                assertFalse(worker.shutdown(20))
                assertFalse(outputs.closed)
                assertEquals(0, feeds.get())
            } finally {
                releaseInit.countDown(); releaseClose.countDown()
                assertTrue(worker.shutdown(2_000))
            }
            assertEquals(0, feeds.get())
            assertEquals(1, outputs.closeCalls)
            assertFalse(worker.startAsync(validConfig()).started)
        }
    }

    @Test
    fun `Stop during blocked factory retains ownership and skips late native start`() {
        val creating = CountDownLatch(1)
        val releaseFactory = CountDownLatch(1)
        val starts = java.util.concurrent.atomic.AtomicInteger()
        val closes = java.util.concurrent.atomic.AtomicInteger()
        val outputs = CloseTrackingOutputStream()
        val backend = object : RtklibBackend {
            override fun start(config: RtklibConfig): RtklibStartResult {
                starts.incrementAndGet()
                return RtklibStartResult.started()
            }
            override fun feed(chunk: RtklibInputChunk) = RtklibNativeOutputBatch()
            override fun snapshot() = RtklibEngineSnapshot(state = RtklibEngineState.RUNNING)
            override fun stop() = Unit
            override fun close() { closes.incrementAndGet() }
        }
        val worker = RtklibWorker(object : RtklibBackendFactory {
            override fun create(): RtklibBackend {
                creating.countDown()
                releaseFactory.await()
                return backend
            }
        }, RtklibOutputWriters(outputs, null))
        try {
            assertTrue(worker.startAsync(validConfig()).started)
            assertTrue(creating.await(2, TimeUnit.SECONDS))
            assertFalse(worker.shutdown(20))
            assertFalse(outputs.closed)
            assertEquals(RtklibOfferStatus.STOPPED, worker.offerRoverBytes(byteArrayOf(1), 10).status)
            assertFalse(worker.startAsync(validConfig()).started)
        } finally {
            releaseFactory.countDown()
            assertTrue(worker.shutdown(2_000))
        }
        assertEquals(0, starts.get())
        assertEquals(1, closes.get())
        assertEquals(1, outputs.closeCalls)
        assertEquals(RtklibEngineState.STOPPED, worker.snapshot().state)
    }

    @Test
    fun `factory load failure is optional FAILED status and leaves raw writer available`() {
        val failed = CountDownLatch(1)
        val raw = CloseTrackingOutputStream()
        val worker = RtklibWorker(object : RtklibBackendFactory {
            override fun create(): RtklibBackend = throw UnsatisfiedLinkError("native unavailable")
        }, RtklibOutputWriters(null, null), statusSink = {
            if (it.state == RtklibEngineState.FAILED) failed.countDown()
        })
        try {
            assertTrue(worker.startAsync(validConfig()).started)
            repeat(100) { raw.write(it); worker.snapshot() }
            assertTrue(failed.await(2, TimeUnit.SECONDS))
            assertEquals(100, raw.size())
            assertFalse(raw.closed)
            assertEquals(RtklibEngineState.FAILED, worker.snapshot().state)
            assertTrue(worker.snapshot().lastError.orEmpty().contains("native unavailable"))
        } finally {
            assertTrue(worker.shutdown(2_000))
            raw.close()
        }
    }

    @Test
    fun `owner waits for blocked cancellation before close and shutdown retains writers through both`() {
        val feedEntered = CountDownLatch(1)
        val releaseFeed = CountDownLatch(1)
        val feedReturned = CountDownLatch(1)
        val cancelEntered = CountDownLatch(1)
        val releaseCancel = CountDownLatch(1)
        val closeEntered = CountDownLatch(1)
        val releaseClose = CountDownLatch(1)
        val outputs = CloseTrackingOutputStream()
        val backend = object : RtklibBackend {
            override fun start(config: RtklibConfig) = RtklibStartResult.started()
            override fun feed(chunk: RtklibInputChunk): RtklibNativeOutputBatch {
                feedEntered.countDown()
                releaseFeed.await()
                feedReturned.countDown()
                return RtklibNativeOutputBatch()
            }
            override fun snapshot() = RtklibEngineSnapshot(state = RtklibEngineState.RUNNING)
            override fun stop() { cancelEntered.countDown(); releaseCancel.await() }
            override fun close() { closeEntered.countDown(); releaseClose.await() }
        }
        val worker = RtklibWorker(object : RtklibBackendFactory { override fun create() = backend },
            RtklibOutputWriters(outputs, null))
        try {
            assertTrue(worker.start(validConfig()).started)
            assertTrue(worker.offerRoverBytes(byteArrayOf(1), 10).accepted)
            assertTrue(feedEntered.await(2, TimeUnit.SECONDS))
            assertFalse(worker.shutdown(20))
            assertTrue(cancelEntered.await(2, TimeUnit.SECONDS))
            releaseFeed.countDown()
            assertTrue(feedReturned.await(2, TimeUnit.SECONDS))
            assertFalse(worker.shutdown(20))
            assertEquals(1L, closeEntered.count, "Native close cannot race active cancellation")
            assertFalse(outputs.closed)
            releaseCancel.countDown()
            assertTrue(closeEntered.await(2, TimeUnit.SECONDS))
            assertFalse(worker.shutdown(20))
            assertFalse(outputs.closed)
        } finally {
            releaseFeed.countDown()
            releaseCancel.countDown()
            releaseClose.countDown()
            assertTrue(worker.shutdown(2_000))
        }
        assertEquals(1, outputs.closeCalls)
    }

    @Test
    fun `never-started writer flush also runs only on owned cleanup thread`() {
        val flushing = CountDownLatch(1)
        val release = CountDownLatch(1)
        val output = object : ByteArrayOutputStream() {
            @Volatile var closed = false
            override fun flush() { flushing.countDown(); release.await() }
            override fun close() { closed = true }
        }
        val worker = RtklibWorker(object : RtklibBackendFactory {
            override fun create(): RtklibBackend = error("Cleanup must not initialize a backend")
        }, RtklibOutputWriters(output, null))
        try {
            assertFalse(worker.shutdown(20))
            assertTrue(flushing.await(2, TimeUnit.SECONDS))
            assertFalse(output.closed)
            assertFalse(worker.startAsync(validConfig()).started)
            assertFalse(worker.shutdown(0))
        } finally {
            release.countDown()
            assertTrue(worker.shutdown(2_000))
        }
        assertTrue(output.closed)
    }

    @Test
    fun `blocked backend init cannot hold raw start control or resurrect after Stop`() {
        val initEntered = CountDownLatch(1)
        val releaseInit = CountDownLatch(1)
        val rawStarted = CountDownLatch(1)
        val raw = CloseTrackingOutputStream()
        val outputs = CloseTrackingOutputStream()
        val feeds = java.util.concurrent.atomic.AtomicInteger()
        val backend = object : RtklibBackend {
            override fun start(config: RtklibConfig): RtklibStartResult {
                initEntered.countDown()
                releaseInit.await()
                return RtklibStartResult.started()
            }
            override fun feed(chunk: RtklibInputChunk): RtklibNativeOutputBatch {
                feeds.incrementAndGet()
                return RtklibNativeOutputBatch()
            }
            override fun snapshot() = RtklibEngineSnapshot(state = RtklibEngineState.RUNNING)
            override fun stop() = Unit
        }
        val worker = RtklibWorker(object : RtklibBackendFactory { override fun create() = backend },
            RtklibOutputWriters(outputs, null))
        val control = Thread {
            worker.startAsync(validConfig())
            repeat(100) { raw.write(it); worker.snapshot(); worker.offerRoverBytes(byteArrayOf(1), 10) }
            rawStarted.countDown()
        }
        try {
            control.start()
            assertTrue(initEntered.await(2, TimeUnit.SECONDS))
            assertTrue(rawStarted.await(1, TimeUnit.SECONDS), "Raw start control must not wait for optional backend init")
            assertEquals(100, raw.size())
            assertEquals(RtklibEngineState.STARTING, worker.snapshot().state)
            repeat(20) { assertFalse(worker.startAsync(validConfig()).started) }
            val stopped = worker.shutdown(20)
            if (stopped) raw.close()
            assertFalse(stopped)
            assertFalse(raw.closed)
            assertFalse(outputs.closed)
        } finally {
            releaseInit.countDown()
            control.join(2_000)
            assertTrue(worker.shutdown(2_000))
        }
        assertEquals(0, feeds.get())
        assertEquals(RtklibEngineState.STOPPED, worker.snapshot().state)
        assertEquals(RtklibOfferStatus.STOPPED, worker.offerRoverBytes(byteArrayOf(1), 10).status)
        assertFalse(worker.startAsync(validConfig()).started)
        assertTrue(outputs.closed)
        raw.close()
    }

    @Test
    fun `throwing init and close are contained instead of preventing raw start`() {
        val raw = CloseTrackingOutputStream()
        val failure = java.util.concurrent.atomic.AtomicReference<Throwable?>()
        val complete = CountDownLatch(1)
        val failedStatus = CountDownLatch(1)
        val backend = object : RtklibBackend {
            override fun start(config: RtklibConfig): RtklibStartResult = error("init exploded")
            override fun feed(chunk: RtklibInputChunk) = RtklibNativeOutputBatch()
            override fun snapshot() = RtklibEngineSnapshot(state = RtklibEngineState.FAILED)
            override fun stop() = Unit
            override fun close(): Unit = error("close exploded")
        }
        val worker = RtklibWorker(
            object : RtklibBackendFactory { override fun create() = backend }, RtklibOutputWriters(null, null),
            statusSink = { if (it.state == RtklibEngineState.FAILED) failedStatus.countDown() },
        )
        val control = Thread {
            try { worker.startAsync(validConfig()); repeat(100) { raw.write(it) } }
            catch (error: Throwable) { failure.set(error) }
            finally { complete.countDown() }
        }
        try {
            control.start()
            assertTrue(complete.await(2, TimeUnit.SECONDS))
            assertEquals(null, failure.get(), "Optional init/close exceptions must not reach raw start")
            assertEquals(100, raw.size())
            assertFalse(raw.closed)
            assertTrue(failedStatus.await(2, TimeUnit.SECONDS))
            val snapshot = worker.snapshot()
            assertEquals(RtklibEngineState.FAILED, snapshot.state)
            assertTrue(snapshot.lastError.orEmpty().contains("init exploded"))
            assertTrue(snapshot.lastError.orEmpty().contains("close exploded"))
        } finally {
            control.join(2_000)
            assertTrue(worker.shutdown(2_000))
            raw.close()
        }
    }

    @Test
    fun `blocked backend close cannot overrun bounded shutdown or release writers`() {
        val closeEntered = CountDownLatch(1)
        val releaseClose = CountDownLatch(1)
        val stopReturned = CountDownLatch(1)
        val stopped = java.util.concurrent.atomic.AtomicBoolean(true)
        val outputs = CloseTrackingOutputStream()
        val backend = object : RtklibBackend {
            override fun start(config: RtklibConfig) = RtklibStartResult.started()
            override fun feed(chunk: RtklibInputChunk) = RtklibNativeOutputBatch()
            override fun snapshot() = RtklibEngineSnapshot(state = RtklibEngineState.RUNNING)
            override fun stop() = Unit
            override fun close() { closeEntered.countDown(); releaseClose.await() }
        }
        val worker = RtklibWorker(object : RtklibBackendFactory { override fun create() = backend },
            RtklibOutputWriters(outputs, null))
        assertTrue(worker.start(validConfig()).started)
        val stop = Thread { stopped.set(worker.shutdown(20)); stopReturned.countDown() }
        try {
            stop.start()
            assertTrue(closeEntered.await(2, TimeUnit.SECONDS))
            assertTrue(stopReturned.await(1, TimeUnit.SECONDS), "Shutdown caller must not execute blocked backend close")
            assertFalse(stopped.get())
            assertFalse(outputs.closed)
        } finally {
            releaseClose.countDown()
            stop.join(2_000)
            assertTrue(worker.shutdown(2_000))
        }
        assertTrue(outputs.closed)
    }

    @Test
    fun `snapshot and capture advisory offer do not enter a blocked backend snapshot`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val completed = CountDownLatch(1)
        val outputs = CloseTrackingOutputStream()
        val backend = object : RtklibBackend {
            override fun start(config: RtklibConfig) = RtklibStartResult.started()
            override fun feed(chunk: RtklibInputChunk) = RtklibNativeOutputBatch()
            override fun snapshot(): RtklibEngineSnapshot {
                entered.countDown()
                release.await()
                return RtklibEngineSnapshot(state = RtklibEngineState.RUNNING)
            }
            override fun stop() = Unit
        }
        val worker = RtklibWorker(object : RtklibBackendFactory {
            override fun create() = backend
        }, RtklibOutputWriters(outputs, null))
        assertTrue(worker.start(validConfig().copy(maxRoverQueueBytes = 2)).started)
        val capture = Thread {
            worker.snapshot()
            repeat(100) {
                worker.offerRoverBytes(byteArrayOf(1), 10L)
            }
            completed.countDown()
        }
        try {
            capture.start()
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            assertTrue(completed.await(1, TimeUnit.SECONDS), "Capture must not wait for backend snapshot")
            assertFalse(worker.shutdown(20), "Blocked snapshot still owns worker resources")
            assertFalse(outputs.closed)
            assertEquals(RtklibOfferStatus.STOPPED, worker.offerRoverBytes(byteArrayOf(1), 10L).status)
        } finally {
            release.countDown()
            capture.join(2_000)
            assertTrue(worker.shutdown(2_000))
            assertTrue(outputs.closed)
        }
    }

    @Test
    fun `blocked status sidecar allows raw append and bounded offers but fences writer release`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val completed = CountDownLatch(1)
        val sidecarFinished = CountDownLatch(1)
        val outputs = CloseTrackingOutputStream()
        val raw = CloseTrackingOutputStream()
        val backend = FakeBackend(RtklibNativeOutputBatch(), CountDownLatch(1))
        val worker = RtklibWorker(
            backendFactory = object : RtklibBackendFactory {
                override fun create() = backend
            },
            outputWriters = RtklibOutputWriters(outputs, null),
            statusSink = {
                entered.countDown()
                release.await()
                assertFalse(raw.closed, "Stop must retain session writers during status IO")
                outputs.write(42)
                sidecarFinished.countDown()
            },
        )
        assertTrue(worker.start(validConfig().copy(maxRoverQueueBytes = 2)).started)
        val capture = Thread {
            repeat(100) {
                raw.write(it)
                worker.snapshot()
                worker.offerRoverBytes(byteArrayOf(1), 10L)
            }
            completed.countDown()
        }
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            capture.start()
            assertTrue(completed.await(1, TimeUnit.SECONDS))
            assertEquals(100, raw.size())
            assertEquals(2, worker.snapshot().roverQueueBytes)
            assertEquals(98, worker.snapshot().droppedRoverBytes)
            // Mirrors the service's existing tryStopRtklib -> completeRecordingStop barrier.
            val stopped = worker.shutdown(20)
            if (stopped) raw.close()
            assertFalse(stopped)
            assertFalse(raw.closed)
            assertFalse(outputs.closed)
        } finally {
            release.countDown()
            capture.join(2_000)
            val stopped = worker.shutdown(2_000)
            if (stopped) raw.close()
            assertTrue(stopped)
        }
        assertEquals(0, sidecarFinished.count)
        assertTrue(raw.closed)
        assertEquals(1, outputs.closeCalls)
        assertTrue(worker.shutdown(0))
    }

    @Test
    fun `status sink failure remains advisory and snapshot contains its warning`() {
        val attempted = CountDownLatch(1)
        val backend = FakeBackend(RtklibNativeOutputBatch(), CountDownLatch(1))
        val worker = RtklibWorker(
            backendFactory = object : RtklibBackendFactory { override fun create() = backend },
            outputWriters = RtklibOutputWriters(null, null),
            statusSink = { attempted.countDown(); error("blocked provider failed") },
        )
        try {
            assertTrue(worker.start(validConfig()).started)
            assertTrue(attempted.await(2, TimeUnit.SECONDS))
            assertTrue(worker.offerRoverBytes(byteArrayOf(1), 10L).accepted)
            assertTrue(backend.await())
            assertTrue(worker.snapshot().lastWarning.orEmpty().contains("status write failed"))
        } finally {
            assertTrue(worker.shutdown(2_000))
        }
    }

    @Test
    fun `idle backend cache refresh and status sampling use the existing single worker`() {
        val now = java.util.concurrent.atomic.AtomicLong(1_000)
        val firstStatus = CountDownLatch(1)
        val nextStatus = CountDownLatch(1)
        val statuses = java.util.concurrent.atomic.AtomicInteger()
        val backend = object : RtklibBackend {
            override fun start(config: RtklibConfig) = RtklibStartResult.started()
            override fun feed(chunk: RtklibInputChunk) = RtklibNativeOutputBatch()
            override fun snapshot() = RtklibEngineSnapshot(
                state = RtklibEngineState.RUNNING, decodedRoverEpochs = now.get(),
            )
            override fun stop() = Unit
        }
        val worker = RtklibWorker(
            backendFactory = object : RtklibBackendFactory { override fun create() = backend },
            outputWriters = RtklibOutputWriters(null, null),
            clockMillis = now::get,
            statusSink = { snapshot ->
                check(Thread.currentThread().name == "rtkcollector-rtklib-worker")
                when (statuses.incrementAndGet()) {
                    1 -> { check(snapshot.decodedRoverEpochs == 1_000L); firstStatus.countDown() }
                    2 -> { check(snapshot.decodedRoverEpochs == 2_000L); nextStatus.countDown() }
                    else -> error("Frozen clock must not create further status work")
                }
            },
        )
        try {
            assertTrue(worker.start(validConfig()).started)
            assertTrue(firstStatus.await(2, TimeUnit.SECONDS))
            assertEquals(1_000L, worker.snapshot().decodedRoverEpochs)
            repeat(1_000) { worker.snapshot() }
            assertEquals(1, statuses.get(), "Snapshot reads cannot schedule jobs")
            now.set(2_000)
            assertTrue(nextStatus.await(2, TimeUnit.SECONDS), "Idle worker must refresh native-server telemetry")
            assertEquals(2_000L, worker.snapshot().decodedRoverEpochs)
        } finally {
            assertTrue(worker.shutdown(2_000))
        }
    }

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

        assertTrue(worker.shutdown(timeoutMillis = 2_000L))
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
        val nmea = StringBuilder()
        val pos = StringBuilder()
        val nmeaWritten = CountDownLatch(1)
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
            outputWriters = RtklibOutputWriters.fromCallbacks(
                appendNmeaLine = {
                    nmea.append(it)
                    nmeaWritten.countDown()
                },
                appendPosLine = pos::append,
            ),
        )
        val config = validConfig().copy(outputNmea = true, outputPos = false)

        assertTrue(worker.start(config).started)
        assertTrue(worker.offerRoverBytes(byteArrayOf(1, 2, 3), 10L).accepted)
        assertTrue(backend.await())
        assertTrue(nmeaWritten.await(2, TimeUnit.SECONDS))
        worker.stop()

        assertEquals("\$GPGGA,fixture\n", nmea.toString())
        assertEquals("", pos.toString())
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
