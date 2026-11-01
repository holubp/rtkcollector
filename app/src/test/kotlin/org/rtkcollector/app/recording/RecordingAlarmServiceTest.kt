package org.rtkcollector.app.recording

import android.content.Intent
import android.content.Context
import android.app.NotificationManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import android.os.Looper
import android.hardware.usb.*
import android.net.Uri
import android.os.Parcelable
import android.provider.DocumentsContract
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.robolectric.annotation.Config
import org.rtkcollector.app.alarms.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33, 35])
class RecordingAlarmServiceTest {
    @Test
    @Config(shadows = [StartupUsbManagerShadow::class, StartupUsbConnectionShadow::class])
    fun `Stop rechecks late reconnect handle retirement after capture exits`() {
        val io = StartupIo("usb-open"); startupIo = io
        val controller = Robolectric.buildService(RecordingForegroundService::class.java).create()
        val service = controller.get()
        val delegate = PathRecordingSessionWriters.open(java.nio.file.Files.createTempDirectory("late-usb-close"))
        val transport = org.rtkcollector.app.usb.AndroidUsbSerialTransport(
            service.getSystemService(Context.USB_SERVICE) as UsbManager, testUsbDevice(service),
            org.rtkcollector.app.usb.UsbSerialOpenOptions(230400))
        val lease = requireNotNull(ProcessReceiverConnectionOwnership.tryAcquire())
        val worker = Thread {
            try { transport.open() } finally {
                runCatching { transport.close() }
                synchronized(field("captureThreadLock").get(service)) { field("captureThread").set(service, null) }
            }
        }
        try {
            field("writers").set(service, delegate); field("reconnectTransport").set(service, transport)
            field("receiverConnectionLease").set(service, lease); field("captureThread").set(service, worker)
            (field("running").get(service) as java.util.concurrent.atomic.AtomicBoolean).set(true)
            worker.start(); assertTrue(io.entered.await(5, TimeUnit.SECONDS))
            service.onStartCommand(Intent(service, RecordingForegroundService::class.java)
                .setAction(RecordingForegroundService.ACTION_STOP), 0, 1)
            val waitingUntil = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            fun coordinatorJoining(): Boolean = Thread.getAllStackTraces().any { (thread, frames) ->
                thread.name == "rtkcollector-stop-coordinator" && thread.state == Thread.State.TIMED_WAITING &&
                    frames.any { it.methodName.startsWith("tryStopCapture") }
            }
            while (!coordinatorJoining() && System.nanoTime() < waitingUntil) Thread.sleep(5)
            assertTrue("Stop must complete initial retirement and reach the producer barrier", coordinatorJoining())
            io.closeFailures.set(100); io.release.countDown(); worker.join(5_000)
            assertFalse(worker.isAlive)
            val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (io.closes.get() < 2 && System.nanoTime() < until) Thread.sleep(10)
            assertTrue(io.closes.get() >= 2)
            assertTrue(transport.hasPendingConnection)
            assertTrue(ProcessReceiverConnectionOwnership.isOwned)
            assertNotNull(field("writers").get(service))
            io.closeFailures.set(0); awaitStop(service)
            assertFalse(transport.hasPendingConnection)
            assertFalse(ProcessReceiverConnectionOwnership.isOwned)
        } finally {
            io.closeFailures.set(0); io.release.countDown(); worker.join(5_000)
            controller.destroy(); awaitStop(service); lease.close(); delegate.closeAll(); startupIo = null
        }
    }

    @Test fun `final metadata write failure retains null-message exception and correlation`() {
        val controller = Robolectric.buildService(RecordingForegroundService::class.java).create()
        val service = controller.get()
        val directory = java.nio.file.Files.createTempDirectory("metadata-identity")
        val delegate = PathRecordingSessionWriters.open(directory.resolve("session"))
        val store = org.rtkcollector.app.diagnostics.DiagnosticsStore(directory.resolve("app").toFile())
        val diagnostics = org.rtkcollector.app.diagnostics.RuntimeDiagnostics(store, directWrites = true, enabled = { false })
        val invoked = java.util.concurrent.atomic.AtomicBoolean()
        try {
            field("runtimeDiagnostics\$delegate").set(service, lazyOf(diagnostics))
            seedMetadata(service, "metadata-test")
            field("writers").set(service, object : RecordingSessionWriters by delegate {
                override fun writeSessionJson(json: String) { invoked.set(true); throw java.io.IOException() }
            })
            (field("running").get(service) as java.util.concurrent.atomic.AtomicBoolean).set(true)
            service.onStartCommand(Intent(service, RecordingForegroundService::class.java)
                .setAction(RecordingForegroundService.ACTION_STOP), 0, 1)
            awaitStop(service)
            assertTrue("final writer must be invoked, not fail during provenance serialization", invoked.get())
            val entry = directory.resolve("app/diagnostics/essential/essential.jsonl").toFile().readLines()
                .map { org.json.JSONObject(it) }.single { it.getString("message") == "Session stop metadata could not be persisted." }
                .getJSONObject("attributes")
            assertEquals("java.io.IOException", entry.getString("exceptionClass"))
            assertEquals("session.json", entry.getString("artifact")); assertEquals("write", entry.getString("operation"))
            assertEquals("metadata-test", entry.getString("sessionId")); assertTrue(entry.has("processInstanceId"))
        } finally { controller.destroy(); awaitStop(service); delegate.closeAll() }
    }

    @Test fun `Stop preserves in-flight raw append flush and NTRIP correction writes`() {
        for (phase in listOf("raw-append", "raw-flush", "correction-append", "raw-append-failure", "raw-flush-failure")) {
            val controller = Robolectric.buildService(RecordingForegroundService::class.java).create()
            val service = controller.get()
            val directory = java.nio.file.Files.createTempDirectory("in-flight-writer")
            val delegate = PathRecordingSessionWriters.open(directory)
            val entered = CountDownLatch(1); val release = CountDownLatch(1)
            val inWriter = java.util.concurrent.atomic.AtomicBoolean()
            val first = java.util.concurrent.atomic.AtomicBoolean(true)
            val interrupted = java.util.concurrent.atomic.AtomicBoolean()
            val closes = java.util.concurrent.atomic.AtomicInteger()
            val failure = java.util.concurrent.atomic.AtomicReference<Throwable>()
            fun block() {
                if (!first.getAndSet(false)) return
                inWriter.set(true); entered.countDown()
                try { assertTrue(release.await(10, TimeUnit.SECONDS)) }
                catch (error: InterruptedException) { interrupted.set(true); throw error }
                finally { interrupted.set(interrupted.get() || Thread.currentThread().isInterrupted); inWriter.set(false) }
                if (phase.endsWith("failure")) throw java.io.IOException()
            }
            val writers = object : RecordingSessionWriters by delegate {
                override fun appendReceiverRx(bytes: ByteArray) {
                    if (phase.startsWith("raw-append")) block()
                    delegate.appendReceiverRx(bytes)
                }
                override fun flushRaw() { if (phase.startsWith("raw-flush")) block(); delegate.flushRaw() }
                override fun appendCorrectionInput(bytes: ByteArray) {
                    if (phase == "correction-append") block()
                    delegate.appendCorrectionInput(bytes)
                }
                override fun closeNonEventStreams(): org.rtkcollector.core.session.SessionWriterCloseReport {
                    assertFalse("writer still in flight", inWriter.get()); closes.incrementAndGet()
                    return delegate.closeNonEventStreams()
                }
            }
            val recorderClass = Class.forName("org.rtkcollector.app.recording.RecordingForegroundService\$SessionRawRecorder")
            val recorder = recorderClass.declaredConstructors.single { it.parameterCount == 4 }.apply { isAccessible = true }
                .newInstance(writers, true, 0L, org.rtkcollector.core.capture.RawRecordingFlushPolicy())
            val payload = byteArrayOf(1, 2, 3, 4, 5, 6)
            var worker: Thread? = null
            var intake: org.rtkcollector.core.correction.NtripRuntimeController? = null
            try {
                field("writers").set(service, writers); field("activeRecorder").set(service, recorder)
                seedMetadata(service, "in-flight-test")
                (field("running").get(service) as java.util.concurrent.atomic.AtomicBoolean).set(true)
                if (phase == "correction-append") {
                    val process = RecordingForegroundService::class.java.declaredMethods.single {
                        it.name == "processNtripCorrectionBytes" }.apply { isAccessible = true }
                    intake = org.rtkcollector.core.correction.NtripRuntimeController(
                        clientFactory = { object : org.rtkcollector.core.correction.NtripRuntimeClient {
                            override fun run(ggaLines: Iterable<String>,
                                onState: (org.rtkcollector.core.correction.CorrectionStatus) -> Unit,
                                onRtcmBytes: (ByteArray) -> Unit): org.rtkcollector.core.correction.NtripConnectionResult {
                                onRtcmBytes(payload)
                                return org.rtkcollector.core.correction.NtripConnectionResult.Completed(payload.size.toLong())
                            }
                            override fun cancel() = Unit
                        } }, emit = {}, onRtcmBytes = {
                            process.invoke(service, it, recorder, org.rtkcollector.core.correction.Rtcm3Extractor())
                        })
                    field("ntripController").set(service, intake)
                    intake.start(org.rtkcollector.core.correction.NtripRuntimeConfig(
                        org.rtkcollector.core.correction.NtripRequest(
                            policy = org.rtkcollector.core.correction.NtripEndpointSecurityPolicy.systemTrust("caster.example", 443),
                            mountpoint = "TEST")))
                } else {
                    worker = Thread {
                        runCatching {
                            (recorder as org.rtkcollector.core.capture.RawRecorder).appendReceiverBytes(payload)
                            if (phase.startsWith("raw-flush")) recorderClass.getDeclaredMethod("flushRawNow", Long::class.javaPrimitiveType)
                                .apply { isAccessible = true }.invoke(recorder, 1L)
                        }.onFailure {
                            if (phase.endsWith("failure")) {
                                val actual = (it as? java.lang.reflect.InvocationTargetException)?.targetException ?: it
                                RecordingForegroundService::class.java.declaredMethods.single {
                                    it.name == "handleCaptureReadOrRawFlushFailure" }.apply { isAccessible = true }
                                    .invoke(service, actual, recorder)
                            } else failure.set(it)
                        }
                    }
                    field("captureThread").set(service, worker); worker.start()
                }
                assertTrue("writer did not enter $phase", entered.await(5, TimeUnit.SECONDS))
                service.onStartCommand(Intent(service, RecordingForegroundService::class.java)
                    .setAction(RecordingForegroundService.ACTION_STOP), 0, 1)
                Thread.sleep(100)
                assertEquals(0, closes.get()); assertTrue(inWriter.get()); assertFalse(interrupted.get())
                release.countDown(); awaitStop(service); worker?.join(5_000)
                assertNull(failure.get()); assertFalse(interrupted.get()); assertEquals(1, closes.get())
                val artifact = if (phase == "correction-append") "correction-input.raw" else "receiver-rx.raw"
                assertArrayEquals(if (phase == "raw-append-failure") byteArrayOf() else payload,
                    java.nio.file.Files.readAllBytes(directory.resolve(artifact)))
                if (phase.endsWith("failure")) {
                    val stop = field("stopContext").get(service) as RecordingStopContext
                    assertEquals(RecordingStopReason.USER, stop.initiator)
                    assertTrue(stop.contributors.contains(RecordingStopReason.RAW_STORAGE_FAILURE))
                    assertEquals(RecordingErrorSeverity.FATAL, (field("state").get(service) as RecordingServiceState).errorSeverity)
                    assertEquals("CLOSE_FAILED", org.json.JSONObject(directory.resolve("session.json").toFile().readText()).getString("stopOutcome"))
                }
            } finally {
                release.countDown(); worker?.join(5_000); intake?.stop(5_000)
                controller.destroy(); awaitStop(service); delegate.closeAll()
            }
        }
    }

    @Test
    @Config(shadows = [StartupUsbManagerShadow::class, StartupUsbConnectionShadow::class])
    fun `claim failure retains USB handle when first cleanup close fails`() {
        val io = StartupIo("claim").apply { failClaim = true; closeFailures.set(1) }; startupIo = io
        val controller = Robolectric.buildService(RecordingForegroundService::class.java).create()
        val service = controller.get()
        val transport = org.rtkcollector.app.usb.AndroidUsbSerialTransport(
            service.getSystemService(Context.USB_SERVICE) as UsbManager, testUsbDevice(service),
            org.rtkcollector.app.usb.UsbSerialOpenOptions(230400))
        try {
            val error = runCatching { transport.open() }.exceptionOrNull()
            assertNotNull(error)
            assertTrue(error!!.suppressed.isNotEmpty())
            assertTrue(transport.hasPendingConnection)
            assertFalse(transport.isOpen)
            transport.close()
            assertFalse(transport.hasPendingConnection)
            assertEquals(2, io.closes.get())
        } finally { io.closeFailures.set(0); transport.close(); controller.destroy(); startupIo = null }
    }

    @Test fun `raw fatal remains fatal when only TX sidecar close fails`() {
        val controller = Robolectric.buildService(RecordingForegroundService::class.java).create()
        val service = controller.get()
        val delegate = PathRecordingSessionWriters.open(java.nio.file.Files.createTempDirectory("mixed-failure"))
        val writers = object : RecordingSessionWriters by delegate {
            override fun appendReceiverRx(bytes: ByteArray) { throw java.io.IOException() }
            override fun closeNonEventStreams(): org.rtkcollector.core.session.SessionWriterCloseReport {
                val closed = delegate.closeNonEventStreams()
                return org.rtkcollector.core.session.SessionWriterCloseReport(closed.issues +
                    org.rtkcollector.core.session.SessionWriterIssue("tx-to-receiver.raw",
                        org.rtkcollector.core.session.SessionWriterIssueCategory.BINARY_SIDECAR,
                        org.rtkcollector.core.session.SessionWriterIssueSeverity.DEGRADED, "TX close failed"))
            }
        }
        try {
            field("writers").set(service, writers)
            (field("running").get(service) as java.util.concurrent.atomic.AtomicBoolean).set(true)
            val failure = runCatching { writers.appendReceiverRx(byteArrayOf(1)) }.exceptionOrNull()!!
            RecordingForegroundService::class.java.getDeclaredMethod("stopForFatalRawStorageFailure", Throwable::class.java)
                .apply { isAccessible = true }.invoke(service,
                    org.rtkcollector.core.capture.RawRecordingStorageException("raw failed", failure,
                        "receiver-rx.raw", "append"))
            awaitStop(service)
            val state = field("state").get(service) as RecordingServiceState
            assertEquals(RecordingErrorSeverity.FATAL, state.errorSeverity)
            assertEquals(RecordingErrorCategory.STORAGE, state.errorCategory)
            assertEquals(RecordingLifecycleState.FAILED, state.lifecycle)
        } finally { controller.destroy(); awaitStop(service); delegate.closeAll() }
    }

    @Test fun `startup storage failure survives Stop before queued main report`() {
        val controller = Robolectric.buildService(RecordingForegroundService::class.java).create()
        val service = controller.get()
        val startup = RecordingStartupOwner()
        try {
            field("startupOwner").set(service, startup)
            (field("running").get(service) as java.util.concurrent.atomic.AtomicBoolean).set(true)
            val report = RecordingForegroundService::class.java.getDeclaredMethod("reportStartupFailure",
                RecordingStartupLease::class.java, Throwable::class.java).apply { isAccessible = true }
            assertTrue(startup.start("failure-first", {
                throw org.rtkcollector.core.capture.RawRecordingStorageException("raw append failed",
                    java.io.IOException("disk failure"))
            }, { lease, error -> report.invoke(service, lease, error) }))
            assertTrue(startup.awaitStopped(5_000))
            // Do not drain the main looper: Stop overtakes failure presentation.
            service.onStartCommand(Intent(service, RecordingForegroundService::class.java)
                .setAction(RecordingForegroundService.ACTION_STOP), 0, 1)
            awaitStop(service)
            val state = field("state").get(service) as RecordingServiceState
            assertEquals(RecordingErrorCategory.STORAGE, state.errorCategory)
            assertEquals(RecordingErrorSeverity.FATAL, state.errorSeverity)
            val close = field("retainedStorageFailureReport").get(service) as org.rtkcollector.core.session.SessionWriterCloseReport
            assertTrue(close.hasFatalIssue)
            val stop = field("stopContext").get(service) as RecordingStopContext
            assertEquals(RecordingStopReason.RAW_STORAGE_FAILURE, stop.initiator)
        } finally { startup.cancel(); startup.awaitStopped(5_000); controller.destroy(); awaitStop(service) }
    }

    @Test
    @Config(shadows = [StartupUsbManagerShadow::class, StartupUsbConnectionShadow::class])
    fun `real startup Stop remains responsive during USB open command phases and RX drain`() {
        for (phase in listOf("usb-open", "UNLOG", "CONFIG", "rx-drain", "BESTNAV")) {
            val io = StartupIo(phase)
            startupIo = io
            val controller = Robolectric.buildService(RecordingForegroundService::class.java).create()
            val service = controller.get()
            val base = RecordingSetupBridgeTest.snapshot(ntripEnabled = false)
            val snapshot = base.copy(sessionId = java.util.UUID.randomUUID().toString(),
                config = base.config.copy(ntrip = base.config.ntrip.copy(enabled = false),
                    profileBaud = 230400, serialBaud = 921600,
                    initCommands = listOf("UNLOG COM1"), baudSwitchCommands = listOf("CONFIG COM1 921600"),
                    modeCommands = listOf("BESTNAVB COM1 1"), shutdownCommands = emptyList()))
            try {
                startWithUsb(service, snapshot)
                assertTrue("startup never reached $phase", io.entered.await(10, TimeUnit.SECONDS))
                if (phase == "usb-open") {
                    val facade = Class.forName("org.rtkcollector.app.ui.MainActivityKt")
                    facade.getDeclaredMethod("writeCommandProfilePersistentlyToDevice", Context::class.java,
                        List::class.java, String::class.java, String::class.java, String::class.java,
                        Boolean::class.javaPrimitiveType).apply { isAccessible = true }
                        .invoke(null, service, emptyList<Any>(), "set", "commands", "", false)
                    assertTrue(org.robolectric.shadows.ShadowToast.getTextOfLatestToast().toString().contains("starting", true))
                    facade.getDeclaredMethod("writeUsbBaudPersistentlyToDevice", Context::class.java,
                        String::class.java, Map::class.java, Boolean::class.javaPrimitiveType)
                        .apply { isAccessible = true }.invoke(null, service, "baud", emptyMap<String, String>(), false)
                    assertTrue(org.robolectric.shadows.ShadowToast.getTextOfLatestToast().toString().contains("starting", true))
                    assertEquals("maintenance attempted a second USB open", 1, io.opens.get())
                }
                val started = System.nanoTime()
                service.onStartCommand(Intent(service, RecordingForegroundService::class.java)
                    .setAction(RecordingForegroundService.ACTION_QUERY), 0, 2)
                service.onStartCommand(Intent(service, RecordingForegroundService::class.java)
                    .setAction(RecordingForegroundService.ACTION_STOP), 0, 3)
                assertTrue("Android callback blocked in $phase", System.nanoTime() - started < TimeUnit.SECONDS.toNanos(2))
                assertTrue((field("startupOwner").get(service) as RecordingStartupOwner).isBusy)
                io.release.countDown()
                awaitStop(service)
                assertFalse(io.interrupted)
                assertEquals("canceled initialization reopened USB", 1, io.opens.get())
                assertNull(field("captureThread").get(service))
                assertNull(field("ntripController").get(service))
                if (phase == "rx-drain") {
                    val files = service.getExternalFilesDir("sessions")!!.listFiles().orEmpty()
                    val raw = files.map { it.resolve("receiver-rx.raw") }.filter { it.isFile }
                    assertTrue(raw.any { it.readBytes().contentEquals(byteArrayOf(9, 8, 7, 6)) })
                }
            } finally {
                io.release.countDown(); controller.destroy()
                RecordingSetupBridge.stop(snapshot.sessionId)
                startupIo = null
            }
        }
    }

    @Test
    @Config(shadows = [StartupUsbManagerShadow::class, StartupUsbConnectionShadow::class,
        StartupDocumentsShadow::class])
    fun `Stop during actual SAF open retains late writer close failure and final metadata`() {
        val io = StartupIo("storage-open").apply { failRawClose = true }
        startupIo = io
        val controller = Robolectric.buildService(RecordingForegroundService::class.java).create()
        val service = controller.get()
        val tree = Uri.parse("content://recording-test/tree/root")
        service.contentResolver.takePersistableUriPermission(tree,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        val base = RecordingSetupBridgeTest.snapshot(ntripEnabled = false)
        val snapshot = base.copy(sessionId = java.util.UUID.randomUUID().toString(),
            config = base.config.copy(ntrip = base.config.ntrip.copy(enabled = false),
                storage = base.config.storage.copy(kind = "SAF_TREE", treeUri = tree.toString()),
                profileBaud = 230400, serialBaud = 230400, initCommands = emptyList(),
                baudSwitchCommands = emptyList(), modeCommands = emptyList(), shutdownCommands = emptyList()))
        try {
            startWithUsb(service, snapshot)
            assertTrue(io.entered.await(10, TimeUnit.SECONDS))
            assertTrue(org.rtkcollector.app.sessions.ActiveRecordingSessionRegistry.isActive(io.sessionUri!!))
            service.onStartCommand(Intent(service, RecordingForegroundService::class.java)
                .setAction(RecordingForegroundService.ACTION_STOP), 0, 2)
            assertTrue((field("startupOwner").get(service) as RecordingStartupOwner).isBusy)
            io.release.countDown()
            awaitStop(service)
            assertFalse(io.interrupted)
            val metadata = org.json.JSONObject(io.streams.getValue("session.json").toString("UTF-8"))
            assertEquals("CLOSE_FAILED", metadata.getString("stopOutcome"))
            assertEquals("USER", metadata.getString("stopReason"))
            assertTrue(metadata.getString("stopContributingReasons").contains("WRITER_CLOSE_FAILURE"))
            assertFalse(org.rtkcollector.app.sessions.ActiveRecordingSessionRegistry.isActive(io.sessionUri!!))
        } finally {
            io.release.countDown(); controller.destroy()
            RecordingSetupBridge.stop(snapshot.sessionId); startupIo = null
        }
    }

    @Test
    @Config(shadows = [StartupUsbManagerShadow::class, StartupUsbConnectionShadow::class])
    fun `Stop preserves persistent command writer until its actual USB write returns`() {
        val io = StartupIo("CONFIG"); startupIo = io
        val controller = Robolectric.buildService(RecordingForegroundService::class.java).create()
        val service = controller.get()
        val base = RecordingSetupBridgeTest.snapshot(ntripEnabled = false)
        val snapshot = base.copy(sessionId = java.util.UUID.randomUUID().toString(),
            config = base.config.copy(ntrip = base.config.ntrip.copy(enabled = false),
                profileBaud = 230400, serialBaud = 230400, initCommands = emptyList(),
                baudSwitchCommands = emptyList(), modeCommands = emptyList(), shutdownCommands = emptyList()))
        try {
            startWithUsb(service, snapshot)
            val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            while ((field("state").get(service) as RecordingServiceState).lifecycle != RecordingLifecycleState.RECORDING &&
                System.nanoTime() < until) Thread.sleep(10)
            assertEquals(RecordingLifecycleState.RECORDING, (field("state").get(service) as RecordingServiceState).lifecycle)
            val path = field("activeSessionLocation").get(service) as String
            service.onStartCommand(Intent(service, RecordingForegroundService::class.java)
                .setAction(RecordingForegroundService.ACTION_WRITE_PERSISTENT_RECEIVER_CONFIG)
                .putStringArrayListExtra(RecordingForegroundService.EXTRA_PERSISTENT_COMMANDS,
                    arrayListOf("CONFIG COM1 230400")), 0, 2)
            assertTrue(io.entered.await(10, TimeUnit.SECONDS))
            service.onStartCommand(Intent(service, RecordingForegroundService::class.java)
                .setAction(RecordingForegroundService.ACTION_STOP), 0, 3)
            assertNotNull(field("writers").get(service))
            io.release.countDown()
            awaitStop(service)
            assertFalse(io.interrupted)
            assertEquals("CONFIG COM1 230400\r\n", java.io.File(path, "tx-to-receiver.raw").readText())
        } finally {
            io.release.countDown(); controller.destroy()
            RecordingSetupBridge.stop(snapshot.sessionId); startupIo = null
        }
    }

    @Test
    @Config(shadows = [StartupUsbManagerShadow::class, StartupUsbConnectionShadow::class, StartupDocumentsShadow::class])
    fun `real storage failure returned after Stop still preserves partial cleanup failure`() {
        val io = StartupIo("storage-open").apply { failRawClose = true; failOpenArtifact = "tx-to-receiver.raw" }
        startupIo = io
        val controller = Robolectric.buildService(RecordingForegroundService::class.java).create()
        val service = controller.get()
        val tree = Uri.parse("content://recording-test/tree/root")
        service.contentResolver.takePersistableUriPermission(tree,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        val base = RecordingSetupBridgeTest.snapshot(ntripEnabled = false)
        val snapshot = base.copy(sessionId = java.util.UUID.randomUUID().toString(),
            config = base.config.copy(ntrip = base.config.ntrip.copy(enabled = false),
                storage = base.config.storage.copy(kind = "SAF_TREE", treeUri = tree.toString()),
                profileBaud = 230400, serialBaud = 230400, initCommands = emptyList(),
                baudSwitchCommands = emptyList(), modeCommands = emptyList(), shutdownCommands = emptyList()))
        try {
            startWithUsb(service, snapshot)
            assertTrue(io.entered.await(10, TimeUnit.SECONDS))
            service.onStartCommand(Intent(service, RecordingForegroundService::class.java)
                .setAction(RecordingForegroundService.ACTION_STOP), 0, 2)
            io.release.countDown(); awaitStop(service)
            val state = field("state").get(service) as RecordingServiceState
            assertEquals(RecordingErrorCategory.STORAGE, state.errorCategory)
            assertEquals(RecordingErrorSeverity.FATAL, state.errorSeverity)
            val report = field("retainedStorageFailureReport").get(service) as org.rtkcollector.core.session.SessionWriterCloseReport
            val cleanup = report.issues.single { it.operation == "close" }
            assertEquals("receiver-rx.raw", cleanup.artifact)
            assertEquals("java.nio.channels.ClosedChannelException", cleanup.exceptionClass)
            assertTrue(report.issues.any { it.artifact == "tx-to-receiver.raw" && it.operation == "open" })
            val stop = field("stopContext").get(service) as RecordingStopContext
            assertEquals(RecordingStopReason.USER, stop.initiator)
            assertTrue(stop.contributors.contains(RecordingStopReason.WRITER_CLOSE_FAILURE))
        } finally {
            io.release.countDown(); controller.destroy(); awaitStop(service)
            RecordingSetupBridge.stop(snapshot.sessionId); startupIo = null
        }
    }

    @Test fun `onDestroy preserves rejected diagnostic cleanup until startup relinquishes writers`() {
        val controller = Robolectric.buildService(RecordingForegroundService::class.java).create()
        val service = controller.get()
        val startup = RecordingStartupOwner()
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val directory = java.nio.file.Files.createTempDirectory("destroy-cleanup")
        val writers = PathRecordingSessionWriters.open(directory)
        val registry = org.rtkcollector.app.sessions.ActiveRecordingSessionRegistry
        registry.activate(directory.toString())
        val writerLease = registry.retainWriter(directory.toString())
        val maintenance = field("diagnosticMaintenance").get(service) as java.util.concurrent.ScheduledExecutorService
        val log = createSessionDiagnostics("partial", writers,
            org.rtkcollector.app.diagnostics.DiagnosticsStore(directory.resolve("logs").toFile()), writerLease,
            scheduleCleanup = { maintenance.execute(it) },
            startWorker = { throw java.util.concurrent.RejectedExecutionException() })
        try {
            field("startupOwner").set(service, startup)
            field("writers").set(service, writers)
            field("sessionDiagnostics").set(service, log)
            field("activeSessionLocation").set(service, directory.toString())
            (field("running").get(service) as java.util.concurrent.atomic.AtomicBoolean).set(true)
            startup.start("partial", { entered.countDown(); release.await() }, { _, error -> throw error })
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val start = System.nanoTime()
            controller.destroy()
            assertTrue(System.nanoTime() - start < TimeUnit.SECONDS.toNanos(2))
            assertTrue(maintenance.isShutdown)
            release.countDown()
            awaitStop(service)
            assertTrue(log.awaitClosed(5_000))
            registry.requireInactive(directory.toString(), "share")
        } finally {
            release.countDown(); startup.cancel(); startup.awaitStopped(5_000)
            log.seal()
        }
    }

    @Test
    @Config(shadows = [StartupUsbManagerShadow::class, StartupUsbConnectionShadow::class, StartupMockLocationShadow::class])
    fun `Stop during platform mock-provider acquisition cannot enable it after cancellation`() {
        val io = StartupIo("mock-provider"); startupIo = io
        val controller = Robolectric.buildService(RecordingForegroundService::class.java).create()
        val service = controller.get()
        val base = RecordingSetupBridgeTest.snapshot(ntripEnabled = false)
        val snapshot = base.copy(sessionId = java.util.UUID.randomUUID().toString(),
            recordingOutputProfile = base.recordingOutputProfile.copy(enableMockLocation = true),
            config = base.config.copy(ntrip = base.config.ntrip.copy(enabled = false),
                recording = base.config.recording.copy(enableMockLocation = true),
                profileBaud = 230400, serialBaud = 230400, initCommands = emptyList(),
                baudSwitchCommands = emptyList(), modeCommands = emptyList(), shutdownCommands = emptyList()))
        try {
            startWithUsb(service, snapshot)
            assertTrue(io.entered.await(10, TimeUnit.SECONDS))
            io.canceled = true
            val before = System.nanoTime()
            controller.destroy()
            assertTrue(System.nanoTime() - before < TimeUnit.SECONDS.toNanos(2))
            io.release.countDown()
            awaitStop(service)
            assertEquals(0, io.lateMockEnables.get())
            assertNull(field("mockLocationPublisher").get(service))
            assertNull(field("captureThread").get(service))
        } finally {
            io.release.countDown(); RecordingSetupBridge.stop(snapshot.sessionId); startupIo = null
        }
    }

    @Test
    @Config(shadows = [StartupUsbManagerShadow::class, StartupUsbConnectionShadow::class])
    fun `recording admission cannot compete with an already owned maintenance connection`() {
        val maintenance = requireNotNull(ProcessReceiverConnectionOwnership.tryAcquire())
        val io = StartupIo("usb-open"); startupIo = io
        val controller = Robolectric.buildService(RecordingForegroundService::class.java).create()
        val service = controller.get()
        val snapshot = RecordingSetupBridgeTest.snapshot().copy(sessionId = java.util.UUID.randomUUID().toString())
        try {
            startWithUsb(service, snapshot)
            assertEquals(0, io.opens.get())
            assertFalse((field("running").get(service) as java.util.concurrent.atomic.AtomicBoolean).get())
            assertFalse((field("startupOwner").get(service) as RecordingStartupOwner).isBusy)
            assertNull(RecordingSetupBridge.current())
        } finally {
            maintenance.close(); io.release.countDown(); controller.destroy()
            RecordingSetupBridge.stop(snapshot.sessionId); startupIo = null
        }
    }

    private fun field(name: String) = RecordingForegroundService::class.java.getDeclaredField(name).apply { isAccessible = true }

    private fun seedMetadata(service: RecordingForegroundService, id: String) {
        field("activeSessionMetadata").set(service, org.rtkcollector.core.session.SessionMetadata(
            appVersion = "test", androidDeviceModel = "test", androidVersion = "test", receiverDriverId = "test",
            receiverIdentification = null, usbVid = null, usbPid = null, baudRate = 230400,
            serialParameters = org.rtkcollector.core.session.SerialParameters(), mode = org.rtkcollector.core.session.SessionMode.ROVER,
            startedAt = "2026-11-01T00:00:00Z", stoppedAt = null, ntrip = null,
            antenna = org.rtkcollector.core.session.AntennaMetadata(), sessionUuid = id, linkedBaseSessionUuid = null))
        val provenance = SetupBridgeState(id, 1L, "test", emptyMap())
        field("startedSetupState").set(service, provenance); field("latestAcceptedSetupState").set(service, provenance)
    }

    private fun awaitStop(service: RecordingForegroundService) {
        val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        val stopping = field("stopping").get(service) as java.util.concurrent.atomic.AtomicBoolean
        while (stopping.get() && System.nanoTime() < until) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertFalse("recording did not finish cleanup", stopping.get())
    }

    private fun startWithUsb(service: RecordingForegroundService, snapshot: RunningSetupSnapshot) {
        val device = testUsbDevice(service)
        val token = RecordingSetupBridge.stageStart(snapshot)
        service.onStartCommand(Intent(service, RecordingForegroundService::class.java)
            .setAction(RecordingForegroundService.ACTION_START)
            .putExtra(RecordingForegroundService.EXTRA_SETUP_TOKEN, token)
            .putExtra(RecordingForegroundService.EXTRA_USB_DEVICE, device), 0, 1)
    }

    private fun testUsbDevice(service: RecordingForegroundService): UsbDevice {
        val input = ReflectionHelpers.callConstructor(UsbEndpoint::class.java,
            ClassParameter.from(Int::class.javaPrimitiveType, 0x81), ClassParameter.from(Int::class.javaPrimitiveType, 2),
            ClassParameter.from(Int::class.javaPrimitiveType, 64), ClassParameter.from(Int::class.javaPrimitiveType, 0))
        val output = ReflectionHelpers.callConstructor(UsbEndpoint::class.java,
            ClassParameter.from(Int::class.javaPrimitiveType, 2), ClassParameter.from(Int::class.javaPrimitiveType, 2),
            ClassParameter.from(Int::class.javaPrimitiveType, 64), ClassParameter.from(Int::class.javaPrimitiveType, 0))
        val usbInterface = ReflectionHelpers.callConstructor(UsbInterface::class.java,
            ClassParameter.from(Int::class.javaPrimitiveType, 0), ClassParameter.from(Int::class.javaPrimitiveType, 0),
            ClassParameter.from(String::class.java, "serial"), ClassParameter.from(Int::class.javaPrimitiveType, 10),
            ClassParameter.from(Int::class.javaPrimitiveType, 0), ClassParameter.from(Int::class.javaPrimitiveType, 0))
        ReflectionHelpers.callInstanceMethod<Unit>(usbInterface, "setEndpoints",
            ClassParameter.from(Array<Parcelable>::class.java, arrayOf<Parcelable>(input, output)))
        val configuration = ReflectionHelpers.callConstructor(UsbConfiguration::class.java,
            ClassParameter.from(Int::class.javaPrimitiveType, 1), ClassParameter.from(String::class.java, "serial"),
            ClassParameter.from(Int::class.javaPrimitiveType, 0), ClassParameter.from(Int::class.javaPrimitiveType, 0))
        ReflectionHelpers.callInstanceMethod<Unit>(configuration, "setInterfaces",
            ClassParameter.from(Array<Parcelable>::class.java, arrayOf<Parcelable>(usbInterface)))
        val device = org.robolectric.shadow.api.Shadow.newInstanceOf(UsbDevice::class.java)
        ReflectionHelpers.setField(device, "mName", "/dev/bus/usb/001/001")
        ReflectionHelpers.setField(device, "mConfigurations", arrayOf(configuration))
        shadowOf(service.getSystemService(Context.USB_SERVICE) as UsbManager).addOrUpdateUsbDevice(device, true)
        return device
    }

    class StartupIo(val phase: String) {
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val opens = java.util.concurrent.atomic.AtomicInteger()
        val closes = java.util.concurrent.atomic.AtomicInteger()
        val closeFailures = java.util.concurrent.atomic.AtomicInteger()
        @Volatile var failClaim = false
        val streams = java.util.concurrent.ConcurrentHashMap<String, java.io.ByteArrayOutputStream>()
        @Volatile var interrupted = false
        @Volatile var failRawClose = false
        @Volatile var failOpenArtifact: String? = null
        @Volatile var canceled = false
        val lateMockEnables = java.util.concurrent.atomic.AtomicInteger()
        @Volatile var sessionUri: String? = null
        fun block() {
            entered.countDown()
            try { check(release.await(10, TimeUnit.SECONDS)) { "fixture timed out" } }
            catch (error: InterruptedException) { interrupted = true; throw error }
            interrupted = interrupted || Thread.currentThread().isInterrupted
        }
    }

    @Implements(UsbManager::class)
    class StartupUsbManagerShadow : org.robolectric.shadows.ShadowUsbManager() {
        @Implementation override fun openDevice(device: UsbDevice): UsbDeviceConnection? {
            startupIo?.let { it.opens.incrementAndGet(); if (it.phase == "usb-open") it.block() }
            return super.openDevice(device)
        }
    }

    @Implements(UsbDeviceConnection::class)
    class StartupUsbConnectionShadow : org.robolectric.shadows.ShadowUsbDeviceConnection() {
        @Implementation override fun claimInterface(usbInterface: UsbInterface, force: Boolean): Boolean {
            if (startupIo?.failClaim == true) throw java.io.IOException("claim failed")
            return super.claimInterface(usbInterface, force)
        }
        @Implementation fun close() {
            startupIo?.let {
                it.closes.incrementAndGet()
                if (it.closeFailures.getAndDecrement() > 0) throw java.io.IOException("close failed")
            }
        }
        @Implementation override fun bulkTransfer(endpoint: UsbEndpoint, bytes: ByteArray, length: Int, timeout: Int): Int {
            val io = startupIo ?: return 0
            if (endpoint.direction == UsbConstants.USB_DIR_OUT) {
                if (String(bytes, 0, length, Charsets.US_ASCII).startsWith(io.phase)) io.block()
                return length
            }
            if (io.phase == "rx-drain" && io.entered.count != 0L) {
                io.block()
                byteArrayOf(9, 8, 7, 6).copyInto(bytes)
                return 4
            }
            if (Thread.currentThread().name == "rtkcollector-startup") {
                org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(100))
            } else Thread.sleep(5)
            return 0
        }
    }

    @Implements(DocumentsContract::class)
    class StartupDocumentsShadow {
        companion object {
            @JvmStatic @Implementation fun createDocument(resolver: android.content.ContentResolver,
                parent: Uri, mimeType: String, name: String): Uri {
                val uri = DocumentsContract.buildDocumentUriUsingTree(parent, name)
                val io = requireNotNull(startupIo)
                if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) io.sessionUri = uri.toString()
                else shadowOf(resolver).registerOutputStreamSupplier(uri) {
                    if (name == "receiver-rx.raw" && io.phase == "storage-open") io.block()
                    if (name == io.failOpenArtifact) throw java.nio.channels.ClosedChannelException()
                    object : java.io.ByteArrayOutputStream() {
                        override fun close() {
                            if (io.failRawClose && name == "receiver-rx.raw") throw java.nio.channels.ClosedChannelException()
                            super.close()
                        }
                    }.also { io.streams[name] = it }
                }
                return uri
            }
        }
    }

    @Implements(android.location.LocationManager::class)
    class StartupMockLocationShadow : org.robolectric.shadows.ShadowLocationManager() {
        @Implementation fun addTestProvider(provider: String, requiresNetwork: Boolean, requiresSatellite: Boolean,
            requiresCell: Boolean, monetaryCost: Boolean, altitude: Boolean, speed: Boolean, bearing: Boolean,
            power: Int, accuracy: Int) { startupIo?.block() }
        @Implementation fun setTestProviderEnabled(provider: String, enabled: Boolean) {
            startupIo?.let { if (enabled && it.canceled) it.lateMockEnables.incrementAndGet() }
        }
        @Implementation fun removeTestProvider(provider: String) = Unit
    }

    companion object { @JvmField var startupIo: StartupIo? = null }

    @Test fun `Stop retains partial startup writers until its producer exits without interruption`() {
        val controller = Robolectric.buildService(RecordingForegroundService::class.java).create()
        val service = controller.get()
        fun field(name: String) = RecordingForegroundService::class.java.getDeclaredField(name).apply { isAccessible = true }
        val startup = RecordingStartupOwner()
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val failed = java.util.concurrent.atomic.AtomicReference<Throwable>()
        val directory = java.nio.file.Files.createTempDirectory("startup-stop")
        try {
            field("startupOwner").set(service, startup)
            (field("running").get(service) as java.util.concurrent.atomic.AtomicBoolean).set(true)
            assertTrue(startup.start("partial", { lease ->
                val writers = PathRecordingSessionWriters.open(directory)
                if (!startup.publish(lease) {
                    field("writers").set(service, writers)
                    org.rtkcollector.app.sessions.ActiveRecordingSessionRegistry.activate(directory.toString())
                    field("activeSessionLocation").set(service, directory.toString())
                }) { writers.closeAll(); return@start }
                writers.appendReceiverRx(byteArrayOf(1, 2, 3))
                entered.countDown()
                release.await(5, java.util.concurrent.TimeUnit.SECONDS)
                assertFalse(Thread.currentThread().isInterrupted)
                writers.appendReceiverRx(byteArrayOf(4, 5, 6))
            }, { _, error -> failed.set(error) }))
            assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS))
            service.onStartCommand(Intent(service, RecordingForegroundService::class.java)
                .setAction(RecordingForegroundService.ACTION_STOP), 0, 1)
            service.onStartCommand(Intent(service, RecordingForegroundService::class.java)
                .setAction(RecordingForegroundService.ACTION_QUERY), 0, 2)
            assertNotNull(field("writers").get(service))
            assertFalse(startup.awaitStopped(0))
            release.countDown()
            assertTrue(startup.awaitStopped(5_000))
            val until = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5)
            val stopping = field("stopping").get(service) as java.util.concurrent.atomic.AtomicBoolean
            while (stopping.get() && System.nanoTime() < until) Thread.sleep(10)
            assertFalse("Stop must finalize after startup exits", stopping.get())
            assertNull(failed.get())
            assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5, 6), java.nio.file.Files.readAllBytes(directory.resolve("receiver-rx.raw")))
            assertFalse(org.rtkcollector.app.sessions.ActiveRecordingSessionRegistry.isActive(directory.toString()))
        } finally {
            release.countDown(); startup.cancel(); startup.awaitStopped(5_000)
            controller.destroy()
        }
    }

    @Test fun `Start returns foreground while queued initialization remains owned and Stop fences it`() {
        val snapshot = RecordingSetupBridgeTest.snapshot().copy(sessionId = java.util.UUID.randomUUID().toString())
        val token = RecordingSetupBridge.stageStart(snapshot)
        val controller = Robolectric.buildService(RecordingForegroundService::class.java).create()
        val service = controller.get()
        lateinit var queued: Runnable
        val startup = RecordingStartupOwner(launch = { queued = it })
        fun field(name: String) = RecordingForegroundService::class.java.getDeclaredField(name).apply { isAccessible = true }
        try {
            field("startupOwner").set(service, startup)
            service.onStartCommand(Intent(service, RecordingForegroundService::class.java)
                .setAction(RecordingForegroundService.ACTION_START)
                .putExtra(RecordingForegroundService.EXTRA_SETUP_TOKEN, token)
                .putExtra(RecordingForegroundService.EXTRA_USB_DEVICE,
                    org.robolectric.shadow.api.Shadow.newInstanceOf(android.hardware.usb.UsbDevice::class.java)), 0, 1)
            assertTrue(startup.isBusy)
            assertNotNull(shadowOf(service).lastForegroundNotification)
            assertNull(field("writers").get(service))
            assertNull(field("captureThread").get(service))
            service.onStartCommand(Intent(service, RecordingForegroundService::class.java)
                .setAction(RecordingForegroundService.ACTION_QUERY), 0, 2)
            service.onStartCommand(Intent(service, RecordingForegroundService::class.java)
                .setAction(RecordingForegroundService.ACTION_STOP), 0, 3)
            assertTrue(startup.isBusy)
            assertFalse((field("running").get(service) as java.util.concurrent.atomic.AtomicBoolean).get())
            queued.run()
            assertTrue(startup.awaitStopped(0))
            assertNull(field("writers").get(service))
            assertNull(field("captureThread").get(service))
        } finally {
            startup.cancel()
            if (startup.isBusy) queued.run()
            controller.destroy()
            RecordingSetupBridge.stop(snapshot.sessionId)
        }
    }

    @Test fun `queued RX recovered health event cannot undo a later detach`() {
        val owner = Robolectric.buildService(RecordingForegroundService::class.java).create()
        val service = owner.get()
        fun field(name: String) = RecordingForegroundService::class.java.getDeclaredField(name).apply { isAccessible = true }
        try {
            val monitor = field("recordingHealthMonitor").get(service) as org.rtkcollector.core.capture.RecordingHealthMonitor
            monitor.reset(0)
            monitor.recordReceiverRead(0, 60_000)
            val recovered = monitor.recordReceiverRead(10, 60_001)
            assertTrue(recovered.contains(org.rtkcollector.core.capture.RecordingHealthEvent.ReceiverRxRecovered))
            val lost = field("alarmReceiverLost").get(service) as java.util.concurrent.atomic.AtomicBoolean
            lost.set(true)
            (field("usbRecoveryNeeded").get(service) as java.util.concurrent.atomic.AtomicBoolean).set(true)
            val before = field("state").get(service)
            RecordingForegroundService::class.java.declaredMethods.single { it.name == "handleRecordingHealthEvents" && it.parameterCount == 2 }
                .apply { isAccessible = true }.invoke(service, recovered, null)
            assertTrue(lost.get())
            assertSame(before, field("state").get(service))
        } finally { owner.destroy() }
    }
    @Test fun `accepted permission reply changes consent only and never repaints recovered service state`() {
        val owner = Robolectric.buildService(RecordingForegroundService::class.java).create()
        val service = owner.get()
        fun field(name: String) = RecordingForegroundService::class.java.getDeclaredField(name).apply { isAccessible = true }
        val active = field("running").get(service) as java.util.concurrent.atomic.AtomicBoolean
        try {
            val device = org.robolectric.shadow.api.Shadow.newInstanceOf(android.hardware.usb.UsbDevice::class.java)
            val consent = field("usbConsent").get(service) as UsbRecoveryConsent
            active.set(true)
            consent.begin(7); consent.observe(device.deviceId)
            val ticket = requireNotNull(consent.request())
            val before = field("state").get(service)
            val receiver = field("usbPermissionReceiver").get(service) as android.content.BroadcastReceiver
            receiver.onReceive(service, usbConsentIntent(service, ticket)
                .putExtra(android.hardware.usb.UsbManager.EXTRA_DEVICE, device)
                .putExtra(android.hardware.usb.UsbManager.EXTRA_PERMISSION_GRANTED, false))
            assertTrue(consent.denied)
            assertSame(before, field("state").get(service))
            consent.accessAvailable()
            receiver.onReceive(service, usbConsentIntent(service, ticket)
                .putExtra(android.hardware.usb.UsbManager.EXTRA_DEVICE, device))
            assertSame(before, field("state").get(service))
            assertFalse(consent.denied)
        } finally { active.set(false); owner.destroy() }
    }

    @Test fun `queued consent request is fenced by Stop or next session before main dispatch`() {
        val owner = Robolectric.buildService(RecordingForegroundService::class.java).create()
        val service = owner.get()
        fun field(name: String) = RecordingForegroundService::class.java.getDeclaredField(name).apply { isAccessible = true }
        val active = field("running").get(service) as java.util.concurrent.atomic.AtomicBoolean
        try {
            val device = org.robolectric.shadow.api.Shadow.newInstanceOf(android.hardware.usb.UsbDevice::class.java)
            val consent = field("usbConsent").get(service) as UsbRecoveryConsent
            active.set(true); consent.begin(7); consent.observe(device.deviceId)
            val request = RecordingForegroundService::class.java.getDeclaredMethod("requestRecoveryPermission", android.hardware.usb.UsbDevice::class.java)
                .apply { isAccessible = true }
            request.invoke(service, device)
            active.set(false); consent.stop()
            shadowOf(Looper.getMainLooper()).idle()
            assertNull(field("usbPermissionIntent").get(service))
            active.set(true); consent.begin(8); consent.observe(device.deviceId)
            request.invoke(service, device)
            consent.begin(9); consent.observe(device.deviceId)
            shadowOf(Looper.getMainLooper()).idle()
            assertNull(field("usbPermissionIntent").get(service))
        } finally { active.set(false); owner.destroy() }
    }
    @Test fun `USB recovery permission intent keeps ticket identity and uses Android device extra`() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val ticket = UsbConsentTicket(7, 20, 1)
        val intent = usbConsentIntent(context, ticket)
        assertEquals(context.packageName, intent.`package`)
        assertEquals(ticket, usbConsentTicket(intent))
        assertFalse(intent.filterEquals(usbConsentIntent(context, ticket.copy(nonce = 2))))
        assertTrue(usbConsentFilter().matchData(intent.type, intent.scheme, intent.data) >= 0)
        assertTrue(usbConsentPendingIntent(context, ticket) != usbConsentPendingIntent(context, ticket.copy(nonce = 2)))
        val device = org.robolectric.shadow.api.Shadow.newInstanceOf(android.hardware.usb.UsbDevice::class.java)
        assertSame(device, Intent().putExtra(android.hardware.usb.UsbManager.EXTRA_DEVICE, device).platformUsbDevice())
        assertNull(Intent().putExtra("usbDevice", device).platformUsbDevice())
    }
    @Test fun `deliberate Stop fences an already queued delivery failure`() {
        val owner = Robolectric.buildService(RecordingForegroundService::class.java).create()
        val service = owner.get()
        try {
            RecordingForegroundService::class.java.getDeclaredField("alarmGeneration")
                .apply { isAccessible = true }.setLong(service, 77)
            val old = AlarmDesiredState(77, 2, emptyList(), null, false)
            RecordingForegroundService::class.java.getDeclaredField("alarmLastDesired")
                .apply { isAccessible = true }.set(service, old)
            service.alarmDeliveryFailed("audio", old)
            service.onStartCommand(Intent(service, RecordingForegroundService::class.java)
                .setAction(RecordingForegroundService.ACTION_STOP), 0, 1)
            shadowOf(Looper.getMainLooper()).idle()
            val broadcast = RecordingForegroundService::class.java.getDeclaredField("alarmBroadcast").apply { isAccessible = true }
            assertNull((broadcast.get(service) as AlarmBroadcastSnapshot).delivery)
        } finally { owner.destroy() }
    }
    @Test fun `posted delivery failures cannot repaint a recovered revision`() {
        val owner = Robolectric.buildService(RecordingForegroundService::class.java).create()
        val service = owner.get()
        try {
            RecordingForegroundService::class.java.getDeclaredField("alarmGeneration")
                .apply { isAccessible = true }.setLong(service, 77)
            val recovered = AlarmDesiredState(77, 2, emptyList(), null, false)
            RecordingForegroundService::class.java.getDeclaredField("alarmLastDesired")
                .apply { isAccessible = true }.set(service, recovered)
            val broadcast = RecordingForegroundService::class.java.getDeclaredField("alarmBroadcast").apply { isAccessible = true }
            service.alarmDeliveryFailed("notification", recovered.copy(revision = 1))
            shadowOf(Looper.getMainLooper()).idle()
            assertNull((broadcast.get(service) as AlarmBroadcastSnapshot).delivery)
            service.alarmDeliveryFailed("notification", recovered)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals("Alarm notification delivery unavailable.", (broadcast.get(service) as AlarmBroadcastSnapshot).delivery)
        } finally { owner.destroy() }
    }
    @Test fun `rejected Start preserves the existing terminal alarm service`() {
        val owner = Robolectric.buildService(RecordingForegroundService::class.java)
        val service = owner.get()
        // Model the recording owner while arming, then isolate admission from audio-provider effects.
        (field("running").get(service) as java.util.concurrent.atomic.AtomicBoolean).set(true)
        owner.create()
        try {
            RecordingForegroundService::class.java.getDeclaredField("alarmGeneration")
                .apply { isAccessible = true }.setLong(service, 88)
            (field("alarmController").get(service) as RecordingAlarmController).close()
            val controller = RecordingAlarmController(android.os.SystemClock::elapsedRealtime, {})
            field("alarmController").set(service, controller)
            val now = android.os.SystemClock.elapsedRealtime()
            controller.post(AlarmCommand.Begin(88, AlarmSettingsSnapshot.defaults(), now))
            controller.post(AlarmCommand.TerminalStop(88, RecordingAlarmEvidence.STORAGE, "storage", now))
            val deadline = System.nanoTime() + 10_000_000_000L
            while (!controller.snapshot().terminalAlertPending && System.nanoTime() < deadline) Thread.sleep(5)
            assertTrue(controller.snapshot().terminalAlertPending)
            assertFalse("terminal fixture must be alive before the rejected Start", shadowOf(service).isStoppedBySelf)
            (field("running").get(service) as java.util.concurrent.atomic.AtomicBoolean).set(false)
            service.onStartCommand(Intent(service, RecordingForegroundService::class.java)
                .setAction(RecordingForegroundService.ACTION_START), 0, 1)
            assertFalse(shadowOf(service).isStoppedBySelf)
            assertEquals(88L, controller.snapshot().generation)
            assertTrue(controller.snapshot().terminalAlertPending)
        } finally { owner.destroy() }
    }
    @Test fun `Stop after alert service exited clears the retained visual alarm`() {
        val owner = Robolectric.buildService(RecordingForegroundService::class.java).create()
        val service = owner.get()
        val manager = service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val publisher = AlarmNotificationPublisher(service)
        val episode = AlarmEpisode(AlarmEpisodeId(0, RecordingAlarmEvidence.STORAGE, 1), "storage", true)
        publisher.apply(AlarmDesiredState(0, 1, listOf(episode), null, true)) { true }
        try {
            assertFalse(manager.activeNotifications.isEmpty())
            service.onStartCommand(Intent(service, RecordingForegroundService::class.java)
                .setAction(RecordingForegroundService.ACTION_STOP).putExtra(AlarmActions.GENERATION, 0L), 0, 1)
            val deadline = System.nanoTime() + 10_000_000_000L
            while (manager.activeNotifications.isNotEmpty() && System.nanoTime() < deadline) Thread.sleep(5)
            assertTrue(manager.activeNotifications.isEmpty())
        } finally { owner.destroy() }
    }
    @Test fun `deliberate Stop fences active alarms even without running capture`() {
        assertEquals("alarmTerminalPending", RecordingForegroundService.EXTRA_STATE_ALARM_TERMINAL_PENDING)
        val owner = Robolectric.buildService(RecordingForegroundService::class.java).create()
        val service = owner.get()
        try {
            val generation = RecordingForegroundService::class.java.getDeclaredField("alarmGeneration").apply { isAccessible = true }
            generation.setLong(service, 77)
            val controller = RecordingForegroundService::class.java.getDeclaredField("alarmController")
                .apply { isAccessible = true }.get(service) as RecordingAlarmController
            val defaults = AlarmSettingsSnapshot.defaults()
            controller.post(AlarmCommand.Begin(77, defaults.copy(categories = defaults.categories.mapValues { it.value.copy(acoustic = false) }), 0))
            controller.post(AlarmCommand.CauseRaised(77, RecordingAlarmEvidence.CONNECTION, "lost", 1))
            var deadline = System.nanoTime() + 10_000_000_000L
            while (controller.snapshot().visibleEpisodes.isEmpty() && System.nanoTime() < deadline) Thread.sleep(5)
            assertFalse(controller.snapshot().visibleEpisodes.isEmpty())
            service.onStartCommand(Intent(service, RecordingForegroundService::class.java).setAction(RecordingForegroundService.ACTION_STOP), 0, 1)
            deadline = System.nanoTime() + 10_000_000_000L
            while (controller.snapshot().visibleEpisodes.isNotEmpty() && System.nanoTime() < deadline) Thread.sleep(5)
            assertTrue(controller.snapshot().visibleEpisodes.isEmpty())
            assertFalse(controller.post(AlarmCommand.CauseRaised(77, RecordingAlarmEvidence.CONNECTION, "late", 2)))
        } finally { owner.destroy() }
    }
}
