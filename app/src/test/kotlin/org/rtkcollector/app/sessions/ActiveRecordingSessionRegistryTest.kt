package org.rtkcollector.app.sessions

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class ActiveRecordingSessionRegistryTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `active filesystem session rejects destructive action across equivalent paths`() {
        val registry = ActiveSessionRegistry()
        val path = Paths.get("build", "sessions", "session-1").toAbsolutePath().normalize()

        registry.activate(path.toString())

        assertTrue(registry.isActive(path.parent.resolve(".").resolve(path.fileName).toString()))
        assertThrows(IllegalArgumentException::class.java) {
            registry.requireInactive(path.toString(), "archive")
        }
        assertThrows(IllegalArgumentException::class.java) {
            registry.withDestructiveOperation(path.toString(), "archive") {}
        }
        registry.deactivate(path.toString())
        assertFalse(registry.isActive(path.toString()))
    }

    @Test
    fun `active SAF session rejects delete until matching session is released`() {
        val registry = ActiveSessionRegistry()
        val location = "content://provider/tree/root/document/session-1"

        registry.activate(location)

        assertThrows(IllegalArgumentException::class.java) {
            registry.requireInactive(location, "delete")
        }
        registry.deactivate("content://provider/tree/root/document/other")
        assertTrue(registry.isActive(location))
        registry.deactivate(location)
        assertFalse(registry.isActive(location))
    }

    @Test
    fun `second different active session is rejected`() {
        val registry = ActiveSessionRegistry()
        registry.activate("content://provider/session-1")

        assertThrows(IllegalArgumentException::class.java) {
            registry.activate("content://provider/session-2")
        }
    }

    @Test
    fun `operation boundary rejects work while any recording is active`() {
        val registry = ActiveSessionRegistry()
        assertFalse(registry.isAnyActive())

        registry.activate("content://provider/session-1")

        assertTrue(registry.isAnyActive())
        assertThrows(IllegalArgumentException::class.java) {
            registry.requireNoActiveRecording("import settings")
        }

        registry.deactivate("content://provider/session-1")
        registry.requireNoActiveRecording("import settings")
    }

    @Test
    fun `activation at final delete boundary is rejected while destructive lease is held`() {
        val registry = ActiveSessionRegistry()
        val file = Files.write(tempDir.resolve("session-delete-boundary.raw"), byteArrayOf(1))
        val location = file.toString()
        val leaseAcquired = CountDownLatch(1)
        val releaseDelete = CountDownLatch(1)
        val workerFailure = AtomicReference<Throwable?>()
        val worker = Thread {
            try {
                registry.withDestructiveOperation(location, "delete") {
                    leaseAcquired.countDown()
                    check(releaseDelete.await(5, TimeUnit.SECONDS)) { "Timed out waiting to finish deletion." }
                    Files.delete(file)
                }
            } catch (error: Throwable) {
                workerFailure.set(error)
            }
        }

        worker.start()
        try {
            assertTrue(leaseAcquired.await(5, TimeUnit.SECONDS))
            assertThrows(IllegalArgumentException::class.java) {
                registry.activate(location)
            }
            assertTrue(Files.exists(file))
        } finally {
            releaseDelete.countDown()
            worker.join(5_000)
        }

        assertFalse(worker.isAlive)
        workerFailure.get()?.let { failure ->
            throw AssertionError("Destructive operation failed.").apply { initCause(failure) }
        }
        assertFalse(Files.exists(file))
        registry.activate(location)
        assertTrue(registry.isActive(location))
        registry.deactivate(location)
    }

    @Test
    fun `destructive lease is released when deletion fails`() {
        val registry = ActiveSessionRegistry()
        val location = "content://provider/session-delete-failure"

        assertThrows(IllegalStateException::class.java) {
            registry.withDestructiveOperation(location, "delete") {
                error("simulated deletion failure")
            }
        }

        registry.activate(location)
        assertTrue(registry.isActive(location))
        registry.deactivate(location)
    }
}
