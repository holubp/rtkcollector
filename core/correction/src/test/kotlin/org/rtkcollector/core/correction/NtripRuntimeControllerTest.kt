package org.rtkcollector.core.correction

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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
        controller.disable("user disabled ntrip")

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
        controller.update(NtripRuntimeConfig(NtripRequest(host = "caster.example", port = 2101, mountpoint = "NEW")))

        assertTrue(first.cancelled)
        assertEquals(listOf("MOUNT", "NEW"), requests.map { it.mountpoint })
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
}
