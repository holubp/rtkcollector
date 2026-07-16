package org.rtkcollector.core.correction

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class NtripRuntimeControllerTest {
    @Test
    fun `auth error stops ntrip attempt but leaves recording active`() {
        val states = mutableListOf<NtripRuntimeSnapshot>()
        val terminal = CountDownLatch(1)
        val controller = NtripRuntimeController(
            clientFactory = {
                FakeRuntimeClient(
                    NtripConnectionResult.Failure(
                        NtripFailure(
                            kind = NtripFailureKind.AUTHORIZATION_FAILED,
                            state = NtripConnectionState.AUTHENTICATING,
                            message = "HTTP 403 Forbidden",
                        ),
                    ),
                )
            },
            emit = {
                states += it
                if (it.state == NtripRuntimeState.AUTH_ERROR) {
                    terminal.countDown()
                }
            },
        )

        controller.start(NtripRuntimeConfig(defaultRequest()))

        assertTrue(terminal.await(2, TimeUnit.SECONDS))
        assertEquals(NtripRuntimeState.AUTH_ERROR, states.last().state)
        assertTrue(states.last().rawRecordingActive)
        assertFalse(states.last().correctionsActive)
    }

    @Test
    fun `disable cancels active client and keeps raw recording active`() {
        val fakeClient = BlockingRuntimeClient()
        val states = mutableListOf<NtripRuntimeSnapshot>()
        val controller = NtripRuntimeController(clientFactory = { fakeClient }, emit = states::add)

        controller.start(NtripRuntimeConfig(defaultRequest()))
        assertTrue(fakeClient.started.await(2, TimeUnit.SECONDS))
        assertTrue(controller.disable("user disabled ntrip"))

        assertTrue(fakeClient.cancelled)
        assertEquals(NtripRuntimeState.DISABLED, states.last().state)
        assertTrue(states.last().rawRecordingActive)
        assertFalse(states.last().correctionsActive)
    }

    @Test
    fun `update cancels old client and runs replacement config`() {
        val first = BlockingRuntimeClient()
        val second = FakeRuntimeClient(NtripConnectionResult.Completed(0))
        val requests = mutableListOf<NtripRequest>()
        val clients = ArrayDeque(listOf<NtripRuntimeClient>(first, second))
        val controller = NtripRuntimeController(
            clientFactory = { config ->
                requests += config.request
                clients.removeFirst()
            },
            emit = {},
        )

        controller.start(NtripRuntimeConfig(defaultRequest()))
        assertTrue(first.started.await(2, TimeUnit.SECONDS))
        assertTrue(
            controller.update(
                NtripRuntimeConfig(NtripRequest(host = "caster.example", port = 2101, mountpoint = "NEW")),
            ),
        )

        assertTrue(first.cancelled)
        assertEquals(listOf("MOUNT", "NEW"), requests.map { it.mountpoint })
    }

    @Test
    fun `disable ignores late state and bytes from stale client`() {
        val staleClient = LateEmittingRuntimeClient()
        val states = mutableListOf<NtripRuntimeSnapshot>()
        val bytes = mutableListOf<ByteArray>()
        val controller = NtripRuntimeController(
            clientFactory = { staleClient },
            emit = states::add,
            onRtcmBytes = bytes::add,
        )

        controller.start(NtripRuntimeConfig(defaultRequest()))
        assertTrue(staleClient.started.await(2, TimeUnit.SECONDS))
        assertFalse(controller.disable("disabled", timeoutMillis = 20))
        staleClient.emitStreamingAndBytes()

        assertEquals(NtripRuntimeState.DISABLED, states.last().state)
        assertTrue(bytes.isEmpty())

        staleClient.finish()
        assertTrue(controller.stop(timeoutMillis = 2_000))
    }

    @Test
    fun `rtcm callback exception emits terminal network error instead of leaving streaming`() {
        val states = mutableListOf<NtripRuntimeSnapshot>()
        val terminal = CountDownLatch(1)
        val controller = NtripRuntimeController(
            clientFactory = { CallbackRuntimeClient() },
            emit = { snapshot ->
                states += snapshot
                if (snapshot.state == NtripRuntimeState.NETWORK_ERROR) terminal.countDown()
            },
            onRtcmBytes = { throw IllegalStateException("correction sink failed") },
        )

        controller.start(NtripRuntimeConfig(defaultRequest()))

        assertTrue(terminal.await(2, TimeUnit.SECONDS))
        assertEquals(NtripRuntimeState.NETWORK_ERROR, states.last().state)
        assertTrue(states.last().rawRecordingActive)
        assertFalse(states.last().correctionsActive)
        assertEquals("correction sink failed", states.last().message)
    }

    @Test
    fun `stop does not wait on a blocked rtcm callback lock`() {
        val callbackEntered = CountDownLatch(1)
        val releaseCallback = CountDownLatch(1)
        val stopReturned = CountDownLatch(1)
        val stopResult = AtomicReference<Boolean?>()
        val client = CallbackRuntimeClient()
        val controller = NtripRuntimeController(
            clientFactory = { client },
            emit = {},
            onRtcmBytes = {
                callbackEntered.countDown()
                releaseCallback.await(2, TimeUnit.SECONDS)
            },
        )

        controller.start(NtripRuntimeConfig(defaultRequest()))
        assertTrue(callbackEntered.await(2, TimeUnit.SECONDS))
        val stopThread = Thread {
            stopResult.set(controller.stop(timeoutMillis = 20))
            stopReturned.countDown()
        }
        stopThread.start()

        try {
            assertTrue(stopReturned.await(500, TimeUnit.MILLISECONDS))
            assertEquals(false, stopResult.get())
        } finally {
            releaseCallback.countDown()
            stopThread.join(2_000)
        }

        assertTrue(controller.stop(timeoutMillis = 2_000))
    }

    @Test
    fun `stop retains stubborn worker ownership until termination is confirmed`() {
        val client = StubbornRuntimeClient()
        val controller = NtripRuntimeController(clientFactory = { client }, emit = {})

        controller.start(NtripRuntimeConfig(defaultRequest()))
        assertTrue(client.started.await(2, TimeUnit.SECONDS))

        assertFalse(controller.stop(timeoutMillis = 20))
        assertEquals(1, client.cancelCalls.get())

        client.release.countDown()
        assertTrue(controller.stop(timeoutMillis = 2_000))
        assertEquals(2, client.cancelCalls.get())
    }

    @Test
    fun `stop waits for asynchronous client callbacks after worker termination`() {
        val callbackEntered = CountDownLatch(1)
        val releaseCallback = CountDownLatch(1)
        val client = AsyncCallbackRuntimeClient()
        val controller = NtripRuntimeController(
            clientFactory = { client },
            emit = {},
            onRtcmBytes = {
                callbackEntered.countDown()
                releaseCallback.await(2, TimeUnit.SECONDS)
            },
        )

        controller.start(NtripRuntimeConfig(defaultRequest()))
        assertTrue(callbackEntered.await(2, TimeUnit.SECONDS))

        assertFalse(controller.stop(timeoutMillis = 20))

        releaseCallback.countDown()
        assertTrue(client.callbackFinished.await(2, TimeUnit.SECONDS))
        assertTrue(controller.stop(timeoutMillis = 2_000))
    }

    private fun defaultRequest(): NtripRequest =
        NtripRequest(host = "caster.example", port = 2101, mountpoint = "MOUNT")

    private class FakeRuntimeClient(private val result: NtripConnectionResult) : NtripRuntimeClient {
        var cancelled = false

        override fun run(
            ggaLines: Iterable<String>,
            onState: (CorrectionStatus) -> Unit,
            onRtcmBytes: (ByteArray) -> Unit,
        ): NtripConnectionResult = result

        override fun cancel() {
            cancelled = true
        }
    }

    private class BlockingRuntimeClient : NtripRuntimeClient {
        val started = CountDownLatch(1)

        @Volatile
        var cancelled = false

        override fun run(
            ggaLines: Iterable<String>,
            onState: (CorrectionStatus) -> Unit,
            onRtcmBytes: (ByteArray) -> Unit,
        ): NtripConnectionResult {
            started.countDown()
            while (!cancelled) {
                Thread.sleep(10)
            }
            return NtripConnectionResult.Failure(
                NtripFailure(
                    kind = NtripFailureKind.CANCELLED,
                    state = NtripConnectionState.STOPPED,
                    message = "cancelled",
                ),
            )
        }

        override fun cancel() {
            cancelled = true
        }
    }

    private class CallbackRuntimeClient : NtripRuntimeClient {
        override fun run(
            ggaLines: Iterable<String>,
            onState: (CorrectionStatus) -> Unit,
            onRtcmBytes: (ByteArray) -> Unit,
        ): NtripConnectionResult {
            onState(CorrectionStatus(NtripConnectionState.STREAMING))
            onRtcmBytes(byteArrayOf(0x01, 0x02))
            return NtripConnectionResult.Completed(2)
        }

        override fun cancel() = Unit
    }

    private class StubbornRuntimeClient : NtripRuntimeClient {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val cancelCalls = AtomicInteger(0)

        override fun run(
            ggaLines: Iterable<String>,
            onState: (CorrectionStatus) -> Unit,
            onRtcmBytes: (ByteArray) -> Unit,
        ): NtripConnectionResult {
            started.countDown()
            release.await(3, TimeUnit.SECONDS)
            return NtripConnectionResult.Failure(
                NtripFailure(
                    kind = NtripFailureKind.CANCELLED,
                    state = NtripConnectionState.STOPPED,
                    message = "released",
                ),
            )
        }

        override fun cancel() {
            cancelCalls.incrementAndGet()
        }
    }

    private class AsyncCallbackRuntimeClient : NtripRuntimeClient {
        val callbackFinished = CountDownLatch(1)

        override fun run(
            ggaLines: Iterable<String>,
            onState: (CorrectionStatus) -> Unit,
            onRtcmBytes: (ByteArray) -> Unit,
        ): NtripConnectionResult {
            Thread {
                try {
                    onRtcmBytes(byteArrayOf(0x01, 0x02))
                } finally {
                    callbackFinished.countDown()
                }
            }.start()
            return NtripConnectionResult.Completed(0)
        }

        override fun cancel() = Unit
    }

    private class LateEmittingRuntimeClient : NtripRuntimeClient {
        val started = CountDownLatch(1)
        private val finish = CountDownLatch(1)
        private lateinit var stateCallback: (CorrectionStatus) -> Unit
        private lateinit var bytesCallback: (ByteArray) -> Unit

        override fun run(
            ggaLines: Iterable<String>,
            onState: (CorrectionStatus) -> Unit,
            onRtcmBytes: (ByteArray) -> Unit,
        ): NtripConnectionResult {
            stateCallback = onState
            bytesCallback = onRtcmBytes
            started.countDown()
            finish.await(3, TimeUnit.SECONDS)
            return NtripConnectionResult.Failure(
                NtripFailure(
                    kind = NtripFailureKind.CANCELLED,
                    state = NtripConnectionState.STOPPED,
                    message = "finished",
                ),
            )
        }

        override fun cancel() = Unit

        fun emitStreamingAndBytes() {
            stateCallback(CorrectionStatus(NtripConnectionState.STREAMING))
            bytesCallback(byteArrayOf(0x01, 0x02))
        }

        fun finish() {
            finish.countDown()
        }
    }
}
