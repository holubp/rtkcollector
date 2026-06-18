package org.rtkcollector.core.correction

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class NtripCasterUploadControllerTest {
    @Test
    fun `offer returns false and records dropped bytes when queue is full`() {
        val enteredUpload = CountDownLatch(1)
        val controller = NtripCasterUploadController(
            capacityChunks = 1,
            uploadOnce = { _, onState, _ ->
                onState(NtripConnectionState.STREAMING)
                enteredUpload.countDown()
                Thread.sleep(300)
                retryableFailure()
            },
            delay = {},
        )
        controller.start(runtimeConfig())
        assertTrue(enteredUpload.await(2, TimeUnit.SECONDS))

        val first = controller.offer(byteArrayOf(1))
        val second = controller.offer(byteArrayOf(2, 3))

        controller.stop()
        assertTrue(first)
        assertFalse(second)
        assertEquals(2, controller.snapshot().bytesDropped)
    }

    @Test
    fun `auth error stops retries`() {
        val attempts = AtomicInteger()
        val controller = NtripCasterUploadController(
            uploadOnce = { _, _, _ ->
                attempts.incrementAndGet()
                NtripCasterUploadResult.Failure(
                    NtripCasterUploadFailure(
                        kind = NtripCasterUploadFailureKind.AUTHORIZATION_FAILED,
                        message = "Forbidden",
                        state = NtripConnectionState.AUTHENTICATING,
                    ),
                )
            },
            delay = {},
        )

        controller.start(runtimeConfig())
        waitUntil { controller.snapshot().state == "AUTH_ERROR" }

        assertEquals(1, attempts.get())
        assertEquals("Forbidden", controller.snapshot().lastError)
    }

    @Test
    fun `network error retries while running`() {
        val attempts = AtomicInteger()
        val controller = NtripCasterUploadController(
            uploadOnce = { _, _, _ ->
                attempts.incrementAndGet()
                retryableFailure()
            },
            delay = {},
        )

        controller.start(runtimeConfig())
        waitUntil { attempts.get() >= 2 }
        controller.stop()

        assertTrue(attempts.get() >= 2)
    }

    @Test
    fun `stop terminates worker and uploaded bytes are counted`() {
        val output = ByteArrayOutputStream()
        val started = CountDownLatch(1)
        val controller = NtripCasterUploadController(
            uploadOnce = { _, onState, writeRtcmBytes ->
                onState(NtripConnectionState.STREAMING)
                started.countDown()
                writeRtcmBytes(output)
                NtripCasterUploadResult.Completed(output.size().toLong())
            },
            delay = {},
        )

        controller.start(runtimeConfig())
        assertTrue(started.await(2, TimeUnit.SECONDS))
        assertTrue(controller.offer(byteArrayOf(1, 2, 3)))
        waitUntil { controller.snapshot().bytesUploaded >= 3 }
        controller.stop()

        assertEquals("STOPPED", controller.snapshot().state)
        assertEquals(3, controller.snapshot().bytesUploaded)
        assertEquals(byteArrayOf(1, 2, 3).toList(), output.toByteArray().toList())
    }

    private fun runtimeConfig(): NtripCasterUploadRuntimeConfig =
        NtripCasterUploadRuntimeConfig(
            request = NtripCasterUploadRequest(
                host = "caster.example",
                port = 2101,
                mountpoint = "BASE",
                credentials = null,
            ),
            reconnectDelayMillis = 0,
        )

    private fun retryableFailure(): NtripCasterUploadResult.Failure =
        NtripCasterUploadResult.Failure(
            NtripCasterUploadFailure(
                kind = NtripCasterUploadFailureKind.CONNECT_FAILED,
                message = "network down",
                state = NtripConnectionState.CONNECTING,
            ),
        )

    private fun waitUntil(predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 2_000
        while (System.currentTimeMillis() < deadline) {
            if (predicate()) return
            Thread.sleep(10)
        }
        error("Condition was not met before timeout.")
    }
}
