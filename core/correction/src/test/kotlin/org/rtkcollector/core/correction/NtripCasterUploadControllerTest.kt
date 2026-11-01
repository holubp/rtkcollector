package org.rtkcollector.core.correction

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicBoolean

class NtripCasterUploadControllerTest {
    @Test fun `unexpected retry-wait interruption does not bypass minimum reconnect delay`() {
        val retry = CountDownLatch(1)
        val secondAttempt = CountDownLatch(1)
        val attempts = AtomicInteger()
        val controller = NtripCasterUploadController(uploadOnce = { _, _, _ ->
            if (attempts.incrementAndGet() > 1) secondAttempt.countDown()
            retryableFailure()
        }, eventSink = { if (it.kind == "retry_scheduled") retry.countDown() })
        try {
            controller.start(runtimeConfig())
            assertTrue(retry.await(2, TimeUnit.SECONDS))
            val field = controller.javaClass.getDeclaredField("worker").apply { isAccessible = true }
            val worker = field.get(controller) as Thread
            waitUntil { worker.state == Thread.State.TIMED_WAITING }
            worker.interrupt()
            assertFalse(secondAttempt.await(100, TimeUnit.MILLISECONDS))
        } finally { controller.stop(2_000) }
    }

    @Test fun `Stop cannot deliver a stale injected-delay interrupt to a later event writer`() {
        val delayEntered = CountDownLatch(1); val releaseDelay = CountDownLatch(1)
        val summaryEntered = CountDownLatch(1); val releaseSummary = CountDownLatch(1)
        val interrupted = AtomicBoolean()
        val controller = NtripCasterUploadController(uploadOnce = { _, _, _ -> retryableFailure() },
            delay = { delayEntered.countDown(); releaseDelay.await() }, eventSink = {
                if (it.kind == "final_summary") {
                    summaryEntered.countDown()
                    try { releaseSummary.await() } catch (_: InterruptedException) { interrupted.set(true) }
                }
            })
        val stopper = Thread { controller.stop(0) }
        try {
            controller.start(runtimeConfig())
            assertTrue(delayEntered.await(2, TimeUnit.SECONDS))
            val wake = controller.javaClass.getDeclaredField("retryWaitLock").apply { isAccessible = true }.get(controller)
            val running = controller.javaClass.getDeclaredField("running").apply { isAccessible = true }
            synchronized(wake) {
                stopper.start()
                waitUntil { !running.getBoolean(controller) }
                releaseDelay.countDown()
                assertTrue(summaryEntered.await(2, TimeUnit.SECONDS))
            }
            stopper.join(2_000)
            releaseSummary.countDown()
            assertTrue(controller.stop(2_000))
            assertFalse(interrupted.get())
        } finally {
            releaseDelay.countDown(); releaseSummary.countDown()
            stopper.join(2_000); controller.stop(2_000)
        }
    }

    @Test
    fun `stop does not interrupt event writer callback and emits final summary once`() {
        val callbackStarted = CountDownLatch(1)
        val releaseCallback = CountDownLatch(1)
        val callbackInterrupted = AtomicBoolean(false)
        val finalSummaries = AtomicInteger()
        val controller = NtripCasterUploadController(
            uploadOnce = { _, _, _ -> retryableFailure() },
            eventSink = { event ->
                if (event.kind == "connect_attempt") {
                    callbackStarted.countDown()
                    try {
                        releaseCallback.await()
                    } catch (_: InterruptedException) {
                        callbackInterrupted.set(true)
                    }
                }
                if (event.kind == "final_summary") finalSummaries.incrementAndGet()
            },
        )

        try {
            controller.start(runtimeConfig())
            assertTrue(callbackStarted.await(2, TimeUnit.SECONDS))
            assertFalse(controller.stop(timeoutMillis = 0))
            assertFalse(controller.stop(timeoutMillis = 20))
            assertFalse(callbackInterrupted.get())
            assertEquals(0, finalSummaries.get())

            releaseCallback.countDown()
            waitUntil { finalSummaries.get() == 1 }
            assertTrue(controller.stop(timeoutMillis = 2_000))
            assertTrue(controller.stop(timeoutMillis = 2_000))
            assertEquals(1, finalSummaries.get())
            assertFalse(callbackInterrupted.get())
        } finally {
            releaseCallback.countDown()
            controller.stop(timeoutMillis = 2_000)
        }
    }

    @Test
    fun `stop wakes production retry wait without worker interruption`() {
        val retryScheduled = CountDownLatch(1)
        val attempts = AtomicInteger()
        val controller = NtripCasterUploadController(
            uploadOnce = { _, _, _ ->
                attempts.incrementAndGet()
                retryableFailure()
            },
            eventSink = { if (it.kind == "retry_scheduled") retryScheduled.countDown() },
        )

        controller.start(
            runtimeConfig(
                policy = NtripCasterUploadPolicy(
                    retry = NtripCasterUploadRetryPolicy(
                        mode = NtripCasterUploadRetryMode.FIXED,
                        fixedReconnectDelayMillis = 60_000,
                        stopAfterFailuresEnabled = false,
                    ),
                ),
            ),
        )
        assertTrue(retryScheduled.await(2, TimeUnit.SECONDS))

        val stoppedAt = System.nanoTime()
        assertTrue(controller.stop(timeoutMillis = 500))
        val stopMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - stoppedAt)

        assertTrue(stopMillis < 500)
        assertEquals(1, attempts.get())
    }

    @Test
    fun `stop preserves final accepted upload bytes while output callback is blocked`() {
        val callbackStarted = CountDownLatch(1)
        val releaseCallback = CountDownLatch(1)
        val callbackInterrupted = AtomicBoolean(false)
        val written = Collections.synchronizedList(mutableListOf<Byte>())
        val controller = NtripCasterUploadController(
            uploadOnce = { _, onState, writeRtcmBytes ->
                onState(NtripConnectionState.STREAMING)
                writeRtcmBytes(object : OutputStream() {
                    override fun write(value: Int) {
                        callbackStarted.countDown()
                        try {
                            releaseCallback.await()
                        } catch (_: InterruptedException) {
                            callbackInterrupted.set(true)
                        }
                        written += value.toByte()
                    }
                })
                NtripCasterUploadResult.Completed(written.size.toLong())
            },
        )

        try {
            controller.start(runtimeConfig())
            waitUntil { controller.snapshot().state == "STREAMING" }
            assertTrue(controller.offer(byteArrayOf(0x41, 0x42)))
            assertTrue(callbackStarted.await(2, TimeUnit.SECONDS))
            assertFalse(controller.stop(timeoutMillis = 20))
            assertFalse(callbackInterrupted.get())
            assertTrue(written.isEmpty())

            releaseCallback.countDown()
            waitUntil { controller.snapshot().bytesUploaded == 2L }
            assertTrue(controller.stop(timeoutMillis = 2_000))
            assertEquals(listOf(0x41.toByte(), 0x42.toByte()), written)
            assertFalse(callbackInterrupted.get())
        } finally {
            releaseCallback.countDown()
            controller.stop(timeoutMillis = 2_000)
        }
    }

    @Test
    fun `offer returns false and records dropped bytes when queue is full`() {
        val enteredUpload = CountDownLatch(1)
        val eventKinds = Collections.synchronizedList(mutableListOf<String>())
        val controller = NtripCasterUploadController(
            capacityChunks = 1,
            uploadOnce = { _, onState, _ ->
                onState(NtripConnectionState.STREAMING)
                enteredUpload.countDown()
                Thread.sleep(300)
                retryableFailure()
            },
            delay = {},
            eventSink = { eventKinds += it.kind },
        )

        controller.start(runtimeConfig())
        assertTrue(enteredUpload.await(2, TimeUnit.SECONDS))

        val first = controller.offer(byteArrayOf(1), messageType = 1005)
        val second = controller.offer(byteArrayOf(2, 3), messageType = 1077)

        controller.stop()
        assertTrue(first)
        assertFalse(second)
        assertEquals(2, controller.snapshot().bytesDropped)
        assertTrue(eventKinds.contains("queue_drop"))
    }

    @Test
    fun `fixed retry uses configured delay of at least ten seconds`() {
        val delays = Collections.synchronizedList(mutableListOf<Long>())
        val expectedRetriesScheduled = CountDownLatch(1)
        val holdRetryWorker = CountDownLatch(1)
        val controller = NtripCasterUploadController(
            uploadOnce = { _, _, _ -> retryableFailure() },
            delay = { retryDelayMillis ->
                delays += retryDelayMillis
                if (delays.size == 3) {
                    expectedRetriesScheduled.countDown()
                    holdRetryWorker.await()
                }
            },
        )

        controller.start(
            runtimeConfig(
                policy = NtripCasterUploadPolicy(
                    retry = NtripCasterUploadRetryPolicy(
                        mode = NtripCasterUploadRetryMode.FIXED,
                        fixedReconnectDelayMillis = 10_000,
                        stopAfterFailuresEnabled = false,
                    ),
                ),
            ),
        )
        assertTrue(expectedRetriesScheduled.await(2, TimeUnit.SECONDS))
        assertTrue(controller.stop())

        assertEquals(listOf(10_000L, 10_000L, 10_000L), delays.take(3))
        assertEquals(10_000L, controller.snapshot().currentRetryDelayMillis)
    }

    @Test
    fun `adaptive retry backs off to configured maximum`() {
        val delays = Collections.synchronizedList(mutableListOf<Long>())
        val expectedRetriesScheduled = CountDownLatch(1)
        val holdRetryWorker = CountDownLatch(1)
        val controller = NtripCasterUploadController(
            uploadOnce = { _, _, _ -> retryableFailure() },
            delay = { retryDelayMillis ->
                delays += retryDelayMillis
                if (delays.size == 4) {
                    expectedRetriesScheduled.countDown()
                    holdRetryWorker.await()
                }
            },
        )

        controller.start(
            runtimeConfig(
                policy = NtripCasterUploadPolicy(
                    retry = NtripCasterUploadRetryPolicy(
                        mode = NtripCasterUploadRetryMode.ADAPTIVE,
                        adaptiveInitialDelayMillis = 10_000,
                        adaptiveMaxDelayMillis = 25_000,
                        stopAfterFailuresEnabled = false,
                    ),
                ),
            ),
        )
        assertTrue(expectedRetriesScheduled.await(2, TimeUnit.SECONDS))
        assertTrue(controller.stop())

        assertEquals(listOf(10_000L, 20_000L, 25_000L, 25_000L), delays.take(4))
    }

    @Test
    fun `auth failure stops immediately without retry`() {
        val attempts = AtomicInteger()
        val eventKinds = Collections.synchronizedList(mutableListOf<String>())
        val eventMessages = Collections.synchronizedList(mutableListOf<String>())
        val hostileMessage = "Authorization: Basic secret POST /PRIVATE HTTP/1.1 certificate-marker private-key-marker"
        val controller = NtripCasterUploadController(
            uploadOnce = { _, _, _ ->
                attempts.incrementAndGet()
                NtripCasterUploadResult.Failure(
                    NtripCasterUploadFailure(
                        kind = NtripCasterUploadFailureKind.AUTHORIZATION_FAILED,
                        message = hostileMessage,
                        state = NtripConnectionState.AUTHENTICATING,
                    ),
                )
            },
            delay = {},
            eventSink = {
                eventKinds += it.kind
                eventMessages += it.message
            },
        )

        controller.start(runtimeConfig())
        waitUntil { controller.snapshot().state == "AUTH_ERROR" }

        assertEquals(1, attempts.get())
        assertEquals("NTRIP caster upload authorization failed.", controller.snapshot().lastError)
        assertFalse(eventMessages.joinToString().contains(hostileMessage))
        assertFalse(eventKinds.contains("retry_scheduled"))
        assertTrue(eventKinds.contains("connect_attempt"))
        assertTrue(eventKinds.contains("auth_stop"))
        assertTrue(eventKinds.contains("final_summary"))
    }

    @Test
    fun `unexpected upload exception text cannot reach controller status or events`() {
        val markers = listOf("password-marker", "POST /PRIVATE HTTP/1.1", "certificate-marker", "private-key-marker")
        val events = Collections.synchronizedList(mutableListOf<NtripCasterUploadEvent>())
        val controller = NtripCasterUploadController(
            uploadOnce = { _, _, _ -> throw IOException(markers.joinToString(" ")) },
            delay = {},
            eventSink = { events += it },
        )
        controller.start(runtimeConfig(policy = NtripCasterUploadPolicy(
            retry = NtripCasterUploadRetryPolicy(stopAfterConsecutiveFailures = 1),
        )))
        waitUntil { controller.snapshot().stopReason == NtripCasterUploadStopReason.RETRY_LIMIT.name }
        controller.stop()

        val exposed = controller.snapshot().lastError.orEmpty() + events.joinToString { it.message }
        markers.forEach { assertFalse(exposed.contains(it)) }
        assertTrue(controller.snapshot().lastError.orEmpty().contains("stream failed"))
    }

    @Test
    fun `stop after consecutive retryable failures`() {
        val attempts = AtomicInteger()
        val controller = NtripCasterUploadController(
            uploadOnce = { _, _, _ ->
                attempts.incrementAndGet()
                retryableFailure()
            },
            delay = {},
        )

        controller.start(
            runtimeConfig(
                policy = NtripCasterUploadPolicy(
                    retry = NtripCasterUploadRetryPolicy(
                        mode = NtripCasterUploadRetryMode.FIXED,
                        fixedReconnectDelayMillis = 10_000,
                        stopAfterFailuresEnabled = true,
                        stopAfterConsecutiveFailures = 2,
                    ),
                ),
            ),
        )
        waitUntil { controller.snapshot().stopReason == NtripCasterUploadStopReason.RETRY_LIMIT.name }

        assertEquals(2, attempts.get())
        assertEquals("STOPPED", controller.snapshot().state)
        assertEquals(2, controller.snapshot().consecutiveFailures)
    }

    @Test
    fun `connected upload with no RTCM for watchdog interval stops upload`() {
        val attempts = AtomicInteger()
        val eventKinds = Collections.synchronizedList(mutableListOf<String>())
        val clockStartedAtNanos = System.nanoTime()
        val enteredStreaming = CountDownLatch(1)
        val controller = NtripCasterUploadController(
            uploadOnce = { _, onState, writeRtcmBytes ->
                attempts.incrementAndGet()
                onState(NtripConnectionState.STREAMING)
                enteredStreaming.countDown()
                writeRtcmBytes(ByteArrayOutputStream())
                NtripCasterUploadResult.Completed(0)
            },
            delay = {},
            clockMillis = {
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - clockStartedAtNanos) * 100L
            },
            eventSink = { eventKinds += it.kind },
        )

        controller.start(
            runtimeConfig(
                policy = NtripCasterUploadPolicy(
                    retry = NtripCasterUploadRetryPolicy(
                        mode = NtripCasterUploadRetryMode.FIXED,
                        fixedReconnectDelayMillis = 10_000,
                        stopAfterFailuresEnabled = false,
                    ),
                    safety = NtripCasterUploadSafetyPolicy(noDataTimeoutMillis = 12_000),
                ),
            ),
        )
        assertTrue(enteredStreaming.await(2, TimeUnit.SECONDS))
        waitUntil { controller.snapshot().state == "STOPPED" }

        assertEquals(1, attempts.get())
        assertEquals(NtripCasterUploadStopReason.NO_RTCM_DATA.name, controller.snapshot().stopReason)
        assertNull(controller.snapshot().lastError)
        assertTrue(eventKinds.contains("no_data"))
        assertTrue(eventKinds.contains("safety_stop"))
        assertFalse(eventKinds.contains("retry_scheduled"))
        controller.stop()
    }

    @Test
    fun `successful uploaded RTCM resets consecutive failure count`() {
        val attempts = AtomicInteger()
        val enteredStreaming = CountDownLatch(1)
        val controller = NtripCasterUploadController(
            uploadOnce = { _, onState, writeRtcmBytes ->
                when (attempts.incrementAndGet()) {
                    1 -> retryableFailure()
                    else -> {
                        onState(NtripConnectionState.STREAMING)
                        enteredStreaming.countDown()
                        writeRtcmBytes(ByteArrayOutputStream())
                        NtripCasterUploadResult.Completed(0)
                    }
                }
            },
            delay = {},
        )

        controller.start(
            runtimeConfig(
                policy = NtripCasterUploadPolicy(
                    retry = NtripCasterUploadRetryPolicy(
                        mode = NtripCasterUploadRetryMode.ADAPTIVE,
                        adaptiveInitialDelayMillis = 10_000,
                        adaptiveMaxDelayMillis = 20_000,
                        stopAfterFailuresEnabled = false,
                    ),
                ),
            ),
        )
        assertTrue(enteredStreaming.await(2, TimeUnit.SECONDS))
        waitUntil { controller.snapshot().consecutiveFailures == 1 }
        assertTrue(controller.offer(byteArrayOf(1, 2, 3), messageType = 1005))
        waitUntil { controller.snapshot().bytesUploaded >= 3 }

        assertEquals(0, controller.snapshot().consecutiveFailures)
        controller.stop()
    }

    @Test
    fun `statistics count only actually written uploaded chunks by message type`() {
        val enteredStreaming = CountDownLatch(1)
        val output = FailOnWriteNumberOutputStream(failOnWriteNumber = 3)
        val controller = NtripCasterUploadController(
            uploadOnce = { _, onState, writeRtcmBytes ->
                onState(NtripConnectionState.STREAMING)
                enteredStreaming.countDown()
                runCatching { writeRtcmBytes(output) }
                    .fold(
                        onSuccess = { NtripCasterUploadResult.Completed(output.totalBytesWritten.toLong()) },
                        onFailure = { retryableFailure(it) },
                    )
            },
            delay = {},
        )

        controller.start(
            runtimeConfig(
                policy = NtripCasterUploadPolicy(
                    retry = NtripCasterUploadRetryPolicy(
                        mode = NtripCasterUploadRetryMode.FIXED,
                        fixedReconnectDelayMillis = 10_000,
                        stopAfterFailuresEnabled = false,
                    ),
                ),
            ),
        )
        assertTrue(enteredStreaming.await(2, TimeUnit.SECONDS))

        val first = byteArrayOf(1, 2, 3, 4)
        val second = byteArrayOf(5, 6, 7, 8, 9)
        val third = byteArrayOf(10, 11, 12, 13, 14, 15)
        assertTrue(controller.offer(first, messageType = 1005))
        assertTrue(controller.offer(second, messageType = 1077))
        assertTrue(controller.offer(third, messageType = 1077))
        waitUntil { controller.snapshot().bytesUploaded == (first.size + second.size).toLong() }

        val snapshot = controller.snapshot()
        assertEquals((first.size + second.size).toLong(), snapshot.bytesUploaded)
        assertTrue(snapshot.totalRtcmHz > 0.0)
        assertEquals(setOf(1005, 1077), snapshot.messageRates.map { it.messageType }.toSet())
        assertTrue(snapshot.messageRates.all { it.hz > 0.0 })
        controller.stop()
    }

    @Test
    fun `bitrate safety stop stops upload and reports safety reason`() {
        val enteredStreaming = CountDownLatch(1)
        val eventKinds = Collections.synchronizedList(mutableListOf<String>())
        val clock = AtomicLong(0L)
        val controller = NtripCasterUploadController(
            uploadOnce = { _, onState, writeRtcmBytes ->
                onState(NtripConnectionState.STREAMING)
                enteredStreaming.countDown()
                writeRtcmBytes(ByteArrayOutputStream())
                NtripCasterUploadResult.Completed(0)
            },
            delay = {},
            clockMillis = { clock.get() },
            eventSink = { eventKinds += it.kind },
        )

        controller.start(
            runtimeConfig(
                policy = NtripCasterUploadPolicy(
                    retry = NtripCasterUploadRetryPolicy(stopAfterFailuresEnabled = false),
                    safety = NtripCasterUploadSafetyPolicy(
                        enabled = true,
                        maxBitrateKbps = 1.0,
                        bitrateWindowMillis = 1_000,
                        maxSessionUploadBytes = 1024 * 1024,
                    ),
                ),
            ),
        )
        assertTrue(enteredStreaming.await(2, TimeUnit.SECONDS))
        assertTrue(controller.offer(ByteArray(1_000) { 0x55 }, messageType = 1077))
        waitUntil { controller.snapshot().stopReason == NtripCasterUploadStopReason.BITRATE_LIMIT.name }

        assertEquals(NtripCasterUploadStopReason.BITRATE_LIMIT.name, controller.snapshot().stopReason)
        assertTrue(eventKinds.contains("safety_stop"))
    }

    @Test
    fun `session volume safety stop stops upload and reports safety reason`() {
        val enteredStreaming = CountDownLatch(1)
        val controller = NtripCasterUploadController(
            uploadOnce = { _, onState, writeRtcmBytes ->
                onState(NtripConnectionState.STREAMING)
                enteredStreaming.countDown()
                writeRtcmBytes(ByteArrayOutputStream())
                NtripCasterUploadResult.Completed(0)
            },
            delay = {},
        )

        controller.start(
            runtimeConfig(
                policy = NtripCasterUploadPolicy(
                    retry = NtripCasterUploadRetryPolicy(stopAfterFailuresEnabled = false),
                    safety = NtripCasterUploadSafetyPolicy(
                        enabled = true,
                        maxBitrateKbps = 10_000.0,
                        bitrateWindowMillis = 1_000,
                        maxSessionUploadBytes = 3,
                    ),
                ),
            ),
        )
        assertTrue(enteredStreaming.await(2, TimeUnit.SECONDS))
        assertTrue(controller.offer(byteArrayOf(1, 2, 3, 4), messageType = 1006))
        waitUntil { controller.snapshot().stopReason == NtripCasterUploadStopReason.SESSION_VOLUME_LIMIT.name }

        assertEquals(NtripCasterUploadStopReason.SESSION_VOLUME_LIMIT.name, controller.snapshot().stopReason)
    }

    @Test
    fun `stop reports false until an uncooperative upload worker has terminated`() {
        val enteredSession = CountDownLatch(1)
        val releaseSession = CountDownLatch(1)
        val eventKinds = Collections.synchronizedList(mutableListOf<String>())
        val controller = NtripCasterUploadController(
            sessionFactory = {
                object : CasterUploadSession {
                    override fun connectOnce(
                        onState: (NtripConnectionState) -> Unit,
                        writeRtcmBytes: (OutputStream) -> Unit,
                    ): NtripCasterUploadResult {
                        onState(NtripConnectionState.STREAMING)
                        enteredSession.countDown()
                        while (releaseSession.count > 0L) {
                            runCatching { releaseSession.await(50, TimeUnit.MILLISECONDS) }
                        }
                        return NtripCasterUploadResult.Completed(0)
                    }

                    override fun cancel() = Unit
                }
            },
            eventSink = { eventKinds += it.kind },
        )

        controller.start(runtimeConfig())
        assertTrue(enteredSession.await(2, TimeUnit.SECONDS))

        assertFalse(controller.stop(timeoutMillis = 20))
        assertFalse(eventKinds.contains("final_summary"))

        releaseSession.countDown()
        assertTrue(controller.stop(timeoutMillis = 2_000))
        assertTrue(eventKinds.contains("final_summary"))
        val eventCountAfterConfirmedStop = eventKinds.size
        Thread.sleep(50)
        assertEquals(eventCountAfterConfirmedStop, eventKinds.size)
    }

    private fun runtimeConfig(
        policy: NtripCasterUploadPolicy = NtripCasterUploadPolicy(
            retry = NtripCasterUploadRetryPolicy(
                mode = NtripCasterUploadRetryMode.FIXED,
                fixedReconnectDelayMillis = 10_000,
                stopAfterFailuresEnabled = false,
            ),
        ),
    ): NtripCasterUploadRuntimeConfig =
        NtripCasterUploadRuntimeConfig(
            request = NtripCasterUploadRequest(
                host = "caster.example",
                port = 2101,
                mountpoint = "BASE",
                credentials = null,
            ),
            policy = policy,
        )

    private fun retryableFailure(cause: Throwable? = null): NtripCasterUploadResult.Failure =
        NtripCasterUploadResult.Failure(
            NtripCasterUploadFailure(
                kind = NtripCasterUploadFailureKind.CONNECT_FAILED,
                message = "network down",
                state = NtripConnectionState.CONNECTING,
                cause = cause,
            ),
        )

    private fun waitUntil(predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            if (predicate()) return
            Thread.sleep(10)
        }
        error("Condition was not met before timeout.")
    }
}

private class FailOnWriteNumberOutputStream(
    private val failOnWriteNumber: Int,
) : OutputStream() {
    private var writeCount: Int = 0
    var totalBytesWritten: Int = 0
        private set

    override fun write(b: Int) {
        write(byteArrayOf(b.toByte()))
    }

    override fun write(buffer: ByteArray, offset: Int, length: Int) {
        writeCount += 1
        if (writeCount == failOnWriteNumber) {
            throw IOException("Simulated write failure")
        }
        totalBytesWritten += length
    }
}
