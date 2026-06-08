# Field-Test Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Harden the Android UM980 field-test recording path so raw RX is protected first, stop/restart is deterministic second, and field errors are actionable third.

**Architecture:** Keep byte-path invariants in JVM-testable core modules where possible, and keep Android-only SAF/service integration in `:app`. The foreground service continues to own live recording; the UI sends start/stop/NTRIP-control commands and observes redacted state. NTRIP runtime control becomes an explicit controller around the existing `NtripClient`, so config update/disable can happen without stopping raw capture.

**Tech Stack:** Kotlin, Android foreground service, existing Gradle multi-module project, JUnit 5 for JVM tests, Android SAF APIs in `:app`.

---

## File Structure

- Modify `core/session/src/main/kotlin/org/rtkcollector/core/session/SessionWriters.kt`
  - Add normal-start guard for non-empty session folders through a new `openNew(...)` entrypoint.
  - Keep existing append behaviour available only through an explicit `openAppendForRecovery(...)` name.
- Modify `core/session/src/test/kotlin/org/rtkcollector/core/session/SessionWritersTest.kt`
  - Add tests that raw RX is never truncated and normal starts reject non-empty folders.
- Create `core/session/src/main/kotlin/org/rtkcollector/core/session/SessionWriterLifecycle.kt`
  - Pure Kotlin close/finalisation issue model shared by app writer implementations.
- Create `core/session/src/test/kotlin/org/rtkcollector/core/session/SessionWriterLifecycleTest.kt`
  - Tests for close-report severity aggregation.
- Modify `core/capture/src/main/kotlin/org/rtkcollector/core/capture/CaptureRuntime.kt`
  - Make event-sink failures unable to throw out of advisory-error reporting.
- Modify `core/capture/src/main/kotlin/org/rtkcollector/core/capture/AdvisoryFanout.kt`
  - Protect advisory event reporting and queue-drop event reporting from sidecar/event-sink failure.
- Modify `core/capture/src/test/kotlin/org/rtkcollector/core/capture/CaptureRuntimeTest.kt`
  - Add tests that raw RX remains recorded when advisory/event sidecars fail.
- Create `core/correction/src/main/kotlin/org/rtkcollector/core/correction/NtripRuntimeController.kt`
  - Pure controller for start, update, disable, stop, retryable network degradation and terminal auth errors.
- Create `core/correction/src/test/kotlin/org/rtkcollector/core/correction/NtripRuntimeControllerTest.kt`
  - Tests for reconnect recovery, auth terminal behaviour, update, and disable.
- Modify `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingSessionWriters.kt`
  - Add `finish()` and close-report behaviour. Use `SessionWriterLifecycle` model.
  - Ensure raw writer is flushed/closed as highest-priority artifact.
- Modify `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt`
  - Add lifecycle states, idempotent stop, NTRIP update/disable actions, and service-side categorised errors.
- Modify `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingServiceState.kt`
  - Add lifecycle, error category/severity, raw safety flag, and corrections-active flag.
- Modify `app/src/main/kotlin/org/rtkcollector/app/MainActivity.kt`
  - Add live NTRIP update and disable controls while recording.
  - Render actionable status categories.
- Modify docs:
  - `docs/user-workflows.md`
  - `docs/android-background-operation.md`
  - `docs/ntrip-and-corrections.md`
  - `docs/session-format.md`

Local validation caveat: on this Termux/aarch64 environment, `:app:compileDebugKotlin` is expected to pass, while full `test` or `assembleDebug` may fail during Android resource linking because the runnable Termux `aapt2` is older than Android platform 36. Record exact output.

---

### Task 1: Protect Normal Session Open From Existing Artifacts

**Files:**
- Modify: `core/session/src/main/kotlin/org/rtkcollector/core/session/SessionWriters.kt`
- Modify: `core/session/src/test/kotlin/org/rtkcollector/core/session/SessionWritersTest.kt`
- Later app integration in Task 6 uses the new `openNew(...)` entrypoint.

- [ ] **Step 1: Write failing tests for raw append safety and non-empty normal-start rejection**

Add these tests to `SessionWritersTest`:

```kotlin
@Test
fun `openNew rejects a non-empty session directory`() {
    val sessionDirectory = Files.createTempDirectory("rtkcollector-existing-session")
    Files.write(
        sessionDirectory.resolve(SessionArtifactFile.RECEIVER_RX_RAW.fileName),
        byteArrayOf(0x01, 0x02),
        StandardOpenOption.CREATE,
        StandardOpenOption.APPEND,
    )

    val error = assertThrows(IllegalStateException::class.java) {
        SessionWriters.openNew(sessionDirectory)
    }

    assertTrue(error.message!!.contains("non-empty session directory"))
    assertArrayEquals(
        byteArrayOf(0x01, 0x02),
        Files.readAllBytes(sessionDirectory.resolve(SessionArtifactFile.RECEIVER_RX_RAW.fileName)),
    )
}

@Test
fun `openAppendForRecovery appends receiver rx without truncating`() {
    val sessionDirectory = Files.createTempDirectory("rtkcollector-recovery-session")
    Files.createDirectories(sessionDirectory)
    val rxPath = sessionDirectory.resolve(SessionArtifactFile.RECEIVER_RX_RAW.fileName)
    Files.write(rxPath, byteArrayOf(0x01, 0x02), StandardOpenOption.CREATE, StandardOpenOption.APPEND)

    SessionWriters.openAppendForRecovery(sessionDirectory).use { writers ->
        writers.appendReceiverRx(byteArrayOf(0x03))
        writers.flush()
    }

    assertArrayEquals(byteArrayOf(0x01, 0x02, 0x03), Files.readAllBytes(rxPath))
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK ANDROID_SDK_ROOT=/storage/3830-3863/Termux/AndroidSDK sh gradlew :core:session:test --tests org.rtkcollector.core.session.SessionWritersTest
```

Expected: compilation fails because `SessionWriters.openNew` and `SessionWriters.openAppendForRecovery` do not exist.

- [ ] **Step 3: Implement explicit open modes**

In `SessionWriters.kt`, replace the current companion object with this shape:

```kotlin
companion object {
    fun openNew(sessionDirectory: Path): SessionWriters {
        requireNewSessionDirectory(sessionDirectory)
        return openAppendForRecovery(sessionDirectory)
    }

    fun openAppendForRecovery(sessionDirectory: Path): SessionWriters {
        Files.createDirectories(sessionDirectory)
        return SessionWriters(
            sessionDirectory = sessionDirectory,
            receiverRx = sessionDirectory.appendStream(SessionArtifactFile.RECEIVER_RX_RAW.fileName),
            txToReceiver = sessionDirectory.appendStream(SessionArtifactFile.TX_TO_RECEIVER_RAW.fileName),
            correctionInput = sessionDirectory.appendStream(SessionArtifactFile.CORRECTION_INPUT_RAW.fileName),
            events = sessionDirectory.appendStream(SessionArtifactFile.EVENTS_JSONL.fileName),
            qualityLive = sessionDirectory.appendStream(SessionArtifactFile.QUALITY_LIVE_JSONL.fileName),
            receiverSolutionNmea = sessionDirectory.appendStream(SessionArtifactFile.RECEIVER_SOLUTION_NMEA.fileName),
            receiverSolution = sessionDirectory.appendStream(SessionArtifactFile.RECEIVER_SOLUTION_JSONL.fileName),
            receiverPppSolution = sessionDirectory.appendStream(SessionArtifactFile.RECEIVER_PPP_SOLUTION_JSONL.fileName),
            extractedRtcm = sessionDirectory.appendStream(SessionArtifactFile.RTCM_EXTRACTED_RTCM3.fileName),
        )
    }

    fun open(sessionDirectory: Path): SessionWriters = openNew(sessionDirectory)

    private fun requireNewSessionDirectory(sessionDirectory: Path) {
        if (Files.exists(sessionDirectory) && Files.list(sessionDirectory).use { it.findAny().isPresent }) {
            error("Refusing to open non-empty session directory for a new recording: $sessionDirectory")
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run:

```bash
ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK ANDROID_SDK_ROOT=/storage/3830-3863/Termux/AndroidSDK sh gradlew :core:session:test --tests org.rtkcollector.core.session.SessionWritersTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector add core/session/src/main/kotlin/org/rtkcollector/core/session/SessionWriters.kt core/session/src/test/kotlin/org/rtkcollector/core/session/SessionWritersTest.kt
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector commit -m "Harden new session writer opening"
```

---

### Task 2: Add Writer Lifecycle Reports And Finalisation Model

**Files:**
- Create: `core/session/src/main/kotlin/org/rtkcollector/core/session/SessionWriterLifecycle.kt`
- Create: `core/session/src/test/kotlin/org/rtkcollector/core/session/SessionWriterLifecycleTest.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingSessionWriters.kt`

- [ ] **Step 1: Write failing lifecycle model tests**

Create `SessionWriterLifecycleTest.kt`:

```kotlin
package org.rtkcollector.core.session

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SessionWriterLifecycleTest {
    @Test
    fun `report with only sidecar issue is degraded but raw safe`() {
        val report = SessionWriterCloseReport(
            issues = listOf(
                SessionWriterIssue(
                    artifact = SessionArtifactFile.RECEIVER_SOLUTION_JSONL.fileName,
                    category = SessionWriterIssueCategory.LINE_SIDECAR,
                    severity = SessionWriterIssueSeverity.DEGRADED,
                    message = "solution sidecar close failed",
                ),
            ),
        )

        assertTrue(report.rawRecordingSafe)
        assertFalse(report.hasFatalIssue)
    }

    @Test
    fun `report with raw issue is fatal and raw unsafe`() {
        val report = SessionWriterCloseReport(
            issues = listOf(
                SessionWriterIssue(
                    artifact = SessionArtifactFile.RECEIVER_RX_RAW.fileName,
                    category = SessionWriterIssueCategory.RAW_RX,
                    severity = SessionWriterIssueSeverity.FATAL,
                    message = "raw flush failed",
                ),
            ),
        )

        assertFalse(report.rawRecordingSafe)
        assertTrue(report.hasFatalIssue)
        assertEquals("raw flush failed", report.userMessage)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK ANDROID_SDK_ROOT=/storage/3830-3863/Termux/AndroidSDK sh gradlew :core:session:test --tests org.rtkcollector.core.session.SessionWriterLifecycleTest
```

Expected: compilation fails because lifecycle model classes do not exist.

- [ ] **Step 3: Implement lifecycle model**

Create `SessionWriterLifecycle.kt`:

```kotlin
package org.rtkcollector.core.session

enum class SessionWriterIssueCategory {
    RAW_RX,
    BINARY_SIDECAR,
    LINE_SIDECAR,
    STRUCTURED_SIDECAR,
    METADATA,
}

enum class SessionWriterIssueSeverity {
    DEGRADED,
    FATAL,
}

data class SessionWriterIssue(
    val artifact: String,
    val category: SessionWriterIssueCategory,
    val severity: SessionWriterIssueSeverity,
    val message: String,
)

data class SessionWriterCloseReport(
    val issues: List<SessionWriterIssue> = emptyList(),
) {
    val rawRecordingSafe: Boolean =
        issues.none { it.category == SessionWriterIssueCategory.RAW_RX || it.severity == SessionWriterIssueSeverity.FATAL }

    val hasFatalIssue: Boolean =
        issues.any { it.severity == SessionWriterIssueSeverity.FATAL }

    val userMessage: String? =
        issues.firstOrNull { it.severity == SessionWriterIssueSeverity.FATAL }?.message
            ?: issues.firstOrNull()?.message
}
```

- [ ] **Step 4: Update app writer interface to return close reports**

In `RecordingSessionWriters.kt`, import lifecycle types and change the interface:

```kotlin
import org.rtkcollector.core.session.SessionWriterCloseReport
import org.rtkcollector.core.session.SessionWriterIssue
import org.rtkcollector.core.session.SessionWriterIssueCategory
import org.rtkcollector.core.session.SessionWriterIssueSeverity
```

Add methods to `RecordingSessionWriters`:

```kotlin
fun finish(): SessionWriterCloseReport = SessionWriterCloseReport()
fun flushRaw()
fun closeAll(): SessionWriterCloseReport
```

Implement `flushRaw()` for path writer as `delegate.flush()` for now because core `SessionWriters` does not expose individual streams yet. Implement `closeAll()` by calling `flush()` then `close()` and returning `SessionWriterCloseReport()` on success. Implement SAF `closeAll()` by closing raw first, then other streams, collecting issues with `RAW_RX`/`FATAL` for raw close failures and sidecar categories with `DEGRADED` for other close failures.

- [ ] **Step 5: Compile app integration**

Run:

```bash
ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK ANDROID_SDK_ROOT=/storage/3830-3863/Termux/AndroidSDK sh gradlew :core:session:test :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector add core/session/src/main/kotlin/org/rtkcollector/core/session/SessionWriterLifecycle.kt core/session/src/test/kotlin/org/rtkcollector/core/session/SessionWriterLifecycleTest.kt app/src/main/kotlin/org/rtkcollector/app/recording/RecordingSessionWriters.kt
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector commit -m "Add recording writer lifecycle reports"
```

---

### Task 3: Isolate Advisory And Event Sidecar Failures From Raw Capture

**Files:**
- Modify: `core/capture/src/main/kotlin/org/rtkcollector/core/capture/CaptureRuntime.kt`
- Modify: `core/capture/src/main/kotlin/org/rtkcollector/core/capture/AdvisoryFanout.kt`
- Modify: `core/capture/src/test/kotlin/org/rtkcollector/core/capture/CaptureRuntimeTest.kt`

- [ ] **Step 1: Write failing tests for event-sink failure isolation**

Add tests to `CaptureRuntimeTest.kt`:

```kotlin
@Test
fun `advisory failure plus event sink failure does not hide recorded rx bytes`() {
    val recorder = MemoryRecorder()
    val transport = FakeSerialTransport(reads = queueOf(byteArrayOf(0x51, 0x52)))
    val runtime = CaptureRuntime(
        transport = transport,
        recorder = recorder,
        eventSink = ThrowingEvents(),
        advisoryReceiverBytes = { error("parser failed after raw write") },
    )

    runtime.open()
    val count = runtime.readOnce(maxBytes = 1024)

    assertEquals(2, count)
    assertArrayEquals(byteArrayOf(0x51, 0x52), recorder.receiverBytes())
    assertEquals(true, transport.isOpen)
}

@Test
fun `advisory fanout consumer failure plus event sink failure does not throw`() {
    val fanout = AdvisoryFanout(
        eventSink = ThrowingEvents(),
        consumers = listOf(AdvisoryConsumer("bad-sidecar") { error("sidecar failed") }),
    )

    fanout.accept(byteArrayOf(0x61))
}
```

Add this helper class:

```kotlin
private class ThrowingEvents : CaptureEventSink {
    override fun recordEvent(event: CaptureEvent) {
        error("event sidecar failed")
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK ANDROID_SDK_ROOT=/storage/3830-3863/Termux/AndroidSDK sh gradlew :core:capture:test --tests org.rtkcollector.core.capture.CaptureRuntimeTest
```

Expected: at least one new test fails because `eventSink.recordEvent(...)` can throw from error-reporting paths.

- [ ] **Step 3: Protect event reporting**

In `CaptureRuntime.kt`, change `recordEvent`:

```kotlin
private fun recordEvent(type: String, message: String) {
    runCatching {
        eventSink.recordEvent(
            CaptureEvent(
                timestamp = Instant.now().toString(),
                type = type,
                message = message,
            ),
        )
    }
}
```

In `AdvisoryFanout.kt`, add a private helper in both `AdvisoryFanout` and `AsyncAdvisoryFanout` or a top-level helper:

```kotlin
private fun CaptureEventSink.recordEventSafely(type: String, message: String) {
    runCatching {
        recordEvent(
            CaptureEvent(
                timestamp = Instant.now().toString(),
                type = type,
                message = message,
            ),
        )
    }
}
```

Replace direct `eventSink.recordEvent(...)` calls inside advisory error paths with `eventSink.recordEventSafely(...)`.

- [ ] **Step 4: Run tests to verify they pass**

Run:

```bash
ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK ANDROID_SDK_ROOT=/storage/3830-3863/Termux/AndroidSDK sh gradlew :core:capture:test
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector add core/capture/src/main/kotlin/org/rtkcollector/core/capture/CaptureRuntime.kt core/capture/src/main/kotlin/org/rtkcollector/core/capture/AdvisoryFanout.kt core/capture/src/test/kotlin/org/rtkcollector/core/capture/CaptureRuntimeTest.kt
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector commit -m "Isolate advisory sidecar failures from raw capture"
```

---

### Task 4: Add Pure NTRIP Runtime Controller

**Files:**
- Create: `core/correction/src/main/kotlin/org/rtkcollector/core/correction/NtripRuntimeController.kt`
- Create: `core/correction/src/test/kotlin/org/rtkcollector/core/correction/NtripRuntimeControllerTest.kt`

- [ ] **Step 1: Write failing tests for disable and terminal auth**

Create `NtripRuntimeControllerTest.kt` with these tests:

```kotlin
package org.rtkcollector.core.correction

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
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
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK ANDROID_SDK_ROOT=/storage/3830-3863/Termux/AndroidSDK sh gradlew :core:correction:test --tests org.rtkcollector.core.correction.NtripRuntimeControllerTest
```

Expected: compilation fails because controller types do not exist.

- [ ] **Step 3: Implement controller model**

Create `NtripRuntimeController.kt`:

```kotlin
package org.rtkcollector.core.correction

enum class NtripRuntimeState {
    DISABLED,
    CONNECTING,
    AUTHENTICATING,
    STREAMING,
    RECONNECT_WAIT,
    STOPPED,
    AUTH_ERROR,
    NETWORK_ERROR,
}

data class NtripRuntimeConfig(
    val request: NtripRequest,
    val ggaLines: List<String> = emptyList(),
)

data class NtripRuntimeSnapshot(
    val state: NtripRuntimeState,
    val rawRecordingActive: Boolean,
    val correctionsActive: Boolean,
    val message: String? = null,
)

interface NtripRuntimeClient {
    fun run(
        ggaLines: Iterable<String>,
        onState: (CorrectionStatus) -> Unit,
        onRtcmBytes: (ByteArray) -> Unit,
    ): NtripConnectionResult

    fun cancel()
}

class DefaultNtripRuntimeClient(private val delegate: NtripClient) : NtripRuntimeClient {
    override fun run(
        ggaLines: Iterable<String>,
        onState: (CorrectionStatus) -> Unit,
        onRtcmBytes: (ByteArray) -> Unit,
    ): NtripConnectionResult =
        delegate.runWithReconnect(ggaLines = ggaLines, onState = onState, onRtcmBytes = onRtcmBytes)

    override fun cancel() {
        delegate.cancel()
    }
}

class NtripRuntimeController(
    private val clientFactory: (NtripRuntimeConfig) -> NtripRuntimeClient,
    private val emit: (NtripRuntimeSnapshot) -> Unit,
    private val onRtcmBytes: (ByteArray) -> Unit = {},
) {
    @Volatile
    private var activeClient: NtripRuntimeClient? = null
    @Volatile
    private var worker: Thread? = null

    fun start(config: NtripRuntimeConfig) {
        startWorker(config)
    }

    private fun startWorker(config: NtripRuntimeConfig) {
        worker = Thread({ runClient(config) }, "rtkcollector-ntrip-runtime").also { it.start() }
    }

    private fun runClient(config: NtripRuntimeConfig) {
        val client = clientFactory(config)
        activeClient = client
        emit(NtripRuntimeSnapshot(NtripRuntimeState.CONNECTING, rawRecordingActive = true, correctionsActive = false))
        val result = client.run(
            ggaLines = config.ggaLines,
            onState = { status -> emit(status.toRuntimeSnapshot()) },
            onRtcmBytes = { bytes ->
                emit(NtripRuntimeSnapshot(NtripRuntimeState.STREAMING, rawRecordingActive = true, correctionsActive = true))
                onRtcmBytes(bytes)
            },
        )
        emit(result.toFinalSnapshot())
    }

    fun update(config: NtripRuntimeConfig) {
        activeClient?.cancel()
        worker?.join(1_500)
        startWorker(config)
    }

    fun disable(message: String = "NTRIP disabled") {
        activeClient?.cancel()
        worker?.join(1_500)
        activeClient = null
        emit(
            NtripRuntimeSnapshot(
                state = NtripRuntimeState.DISABLED,
                rawRecordingActive = true,
                correctionsActive = false,
                message = message,
            ),
        )
    }

    fun stop() {
        activeClient?.cancel()
        worker?.join(1_500)
        activeClient = null
        emit(NtripRuntimeSnapshot(NtripRuntimeState.STOPPED, rawRecordingActive = true, correctionsActive = false))
    }

    private fun CorrectionStatus.toRuntimeSnapshot(): NtripRuntimeSnapshot =
        NtripRuntimeSnapshot(
            state = when (state) {
                NtripConnectionState.CONNECTING -> NtripRuntimeState.CONNECTING
                NtripConnectionState.AUTHENTICATING -> NtripRuntimeState.AUTHENTICATING
                NtripConnectionState.STREAMING -> NtripRuntimeState.STREAMING
                NtripConnectionState.RECONNECT_WAIT -> NtripRuntimeState.RECONNECT_WAIT
                NtripConnectionState.STOPPED -> NtripRuntimeState.STOPPED
                else -> NtripRuntimeState.NETWORK_ERROR
            },
            rawRecordingActive = true,
            correctionsActive = state == NtripConnectionState.STREAMING,
            message = lastError,
        )

    private fun NtripConnectionResult.toFinalSnapshot(): NtripRuntimeSnapshot =
        when (this) {
            is NtripConnectionResult.Completed -> NtripRuntimeSnapshot(NtripRuntimeState.STOPPED, true, false)
            is NtripConnectionResult.Failure -> when (failure.kind) {
                NtripFailureKind.AUTHENTICATION_FAILED,
                NtripFailureKind.AUTHORIZATION_FAILED,
                -> NtripRuntimeSnapshot(NtripRuntimeState.AUTH_ERROR, true, false, failure.message)
                NtripFailureKind.CANCELLED -> NtripRuntimeSnapshot(NtripRuntimeState.STOPPED, true, false, failure.message)
                else -> NtripRuntimeSnapshot(NtripRuntimeState.NETWORK_ERROR, true, false, failure.message)
            }
        }
}
```

- [ ] **Step 4: Add update test**

Add this test to `NtripRuntimeControllerTest.kt`:

```kotlin
@Test
fun `update cancels old client and runs replacement config`() {
    val first = BlockingRuntimeClient()
    val second = FakeRuntimeClient(NtripConnectionResult.Completed(0))
    val requests = mutableListOf<NtripRequest>()
    val clients = ArrayDeque(listOf(first, second))
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
```

- [ ] **Step 5: Run tests to verify controller passes**

Run:

```bash
ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK ANDROID_SDK_ROOT=/storage/3830-3863/Termux/AndroidSDK sh gradlew :core:correction:test --tests org.rtkcollector.core.correction.NtripRuntimeControllerTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector add core/correction/src/main/kotlin/org/rtkcollector/core/correction/NtripRuntimeController.kt core/correction/src/test/kotlin/org/rtkcollector/core/correction/NtripRuntimeControllerTest.kt
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector commit -m "Add NTRIP runtime controller"
```

---

### Task 5: Add Service Lifecycle And Error Status Model

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingServiceState.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/MainActivity.kt`

- [ ] **Step 1: Add service status enums and fields**

Replace `RecordingServiceState.kt` with:

```kotlin
package org.rtkcollector.app.recording

enum class RecordingLifecycleState {
    IDLE,
    STARTING,
    RECORDING,
    STOPPING,
    STOPPED,
    FAILED,
}

enum class RecordingErrorCategory {
    NONE,
    USB,
    STORAGE,
    NTRIP,
    RECEIVER_COMMAND,
    PARSER_EXPORT,
    SERVICE_LIFECYCLE,
}

enum class RecordingErrorSeverity {
    NONE,
    INFO,
    DEGRADED,
    FATAL,
}

data class RecordingServiceState(
    val running: Boolean = false,
    val lifecycle: RecordingLifecycleState = RecordingLifecycleState.IDLE,
    val sessionPath: String? = null,
    val receiverRxBytes: Long = 0,
    val txToReceiverBytes: Long = 0,
    val correctionInputBytes: Long = 0,
    val ntripState: String = "Not configured",
    val ggaFixQuality: Int? = null,
    val bestnavPositionType: String? = null,
    val pppStatus: String? = null,
    val rtcmFrames: Long = 0,
    val lastError: String? = null,
    val errorCategory: RecordingErrorCategory = RecordingErrorCategory.NONE,
    val errorSeverity: RecordingErrorSeverity = RecordingErrorSeverity.NONE,
    val rawRecordingActive: Boolean = false,
    val correctionsActive: Boolean = false,
)
```

- [ ] **Step 2: Update broadcasts**

In `RecordingForegroundService.broadcastState()`, add extras:

```kotlin
putExtra(EXTRA_STATE_LIFECYCLE, state.lifecycle.name)
putExtra(EXTRA_STATE_ERROR_CATEGORY, state.errorCategory.name)
putExtra(EXTRA_STATE_ERROR_SEVERITY, state.errorSeverity.name)
putExtra(EXTRA_STATE_RAW_ACTIVE, state.rawRecordingActive)
putExtra(EXTRA_STATE_CORRECTIONS_ACTIVE, state.correctionsActive)
```

Add constants:

```kotlin
const val EXTRA_STATE_LIFECYCLE = "lifecycle"
const val EXTRA_STATE_ERROR_CATEGORY = "errorCategory"
const val EXTRA_STATE_ERROR_SEVERITY = "errorSeverity"
const val EXTRA_STATE_RAW_ACTIVE = "rawRecordingActive"
const val EXTRA_STATE_CORRECTIONS_ACTIVE = "correctionsActive"
```

- [ ] **Step 3: Update service lifecycle transitions**

In `startRecording(...)`:

```kotlin
state = state.copy(lifecycle = RecordingLifecycleState.STARTING, errorCategory = RecordingErrorCategory.NONE, errorSeverity = RecordingErrorSeverity.NONE)
broadcastState()
```

After capture runtime is open and before starting threads:

```kotlin
state = state.copy(
    running = true,
    lifecycle = RecordingLifecycleState.RECORDING,
    sessionPath = openedSession.displayPath,
    ntripState = "Not configured",
    rawRecordingActive = true,
)
```

In the `catch` block:

```kotlin
state = state.copy(
    running = false,
    lifecycle = RecordingLifecycleState.FAILED,
    lastError = error.message,
    errorCategory = classifyStartError(error),
    errorSeverity = RecordingErrorSeverity.FATAL,
    rawRecordingActive = false,
)
```

Add:

```kotlin
private fun classifyStartError(error: Throwable): RecordingErrorCategory =
    when {
        error.message.orEmpty().contains("USB", ignoreCase = true) -> RecordingErrorCategory.USB
        error.message.orEmpty().contains("SAF", ignoreCase = true) -> RecordingErrorCategory.STORAGE
        error.message.orEmpty().contains("storage", ignoreCase = true) -> RecordingErrorCategory.STORAGE
        error.message.orEmpty().contains("command", ignoreCase = true) -> RecordingErrorCategory.RECEIVER_COMMAND
        else -> RecordingErrorCategory.SERVICE_LIFECYCLE
    }
```

- [ ] **Step 4: Render status in MainActivity**

In `buildServiceStateText(...)`, add lines:

```kotlin
appendLine("Lifecycle: ${intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_LIFECYCLE) ?: "n/a"}")
appendLine("Raw recording active: ${intent.getBooleanExtra(RecordingForegroundService.EXTRA_STATE_RAW_ACTIVE, false)}")
appendLine("Corrections active: ${intent.getBooleanExtra(RecordingForegroundService.EXTRA_STATE_CORRECTIONS_ACTIVE, false)}")
appendLine("Error category: ${intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_ERROR_CATEGORY) ?: "NONE"}")
appendLine("Error severity: ${intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_ERROR_SEVERITY) ?: "NONE"}")
```

- [ ] **Step 5: Compile app integration**

Run:

```bash
ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK ANDROID_SDK_ROOT=/storage/3830-3863/Termux/AndroidSDK sh gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector add app/src/main/kotlin/org/rtkcollector/app/recording/RecordingServiceState.kt app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt app/src/main/kotlin/org/rtkcollector/app/MainActivity.kt
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector commit -m "Add recording lifecycle status"
```

---

### Task 6: Make Stop Idempotent And Writer Closing Reported

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingSessionWriters.kt`

- [ ] **Step 1: Add stop guard fields**

In `RecordingForegroundService`, add:

```kotlin
private val stopping = AtomicBoolean(false)
private val shutdownSent = AtomicBoolean(false)
```

Reset these in successful start before sending init commands:

```kotlin
stopping.set(false)
shutdownSent.set(false)
```

- [ ] **Step 2: Make stop transition once**

At the top of `stopRecording(...)`, replace the current guard with:

```kotlin
if (!stopping.compareAndSet(false, true)) {
    broadcastState()
    return
}
if (!running.getAndSet(false) && runtime == null) {
    stopping.set(false)
    return
}
state = state.copy(lifecycle = RecordingLifecycleState.STOPPING, running = false)
broadcastState()
```

- [ ] **Step 3: Send shutdown commands at most once**

Replace the shutdown block with:

```kotlin
if (sendShutdown && shutdownSent.compareAndSet(false, true)) {
    runCatching {
        runtime?.let { captureRuntime ->
            synchronized(runtimeLock) {
                sendCommandLines(captureRuntime, shutdownCommands)
            }
        }
    }.onFailure { error ->
        state = state.copy(
            lastError = error.message,
            errorCategory = RecordingErrorCategory.RECEIVER_COMMAND,
            errorSeverity = RecordingErrorSeverity.DEGRADED,
        )
        broadcastState()
    }
}
```

- [ ] **Step 4: Use writer close report**

Replace the writer close section with:

```kotlin
val closeReport = runCatching { writers?.closeAll() }
    .getOrElse { error ->
        org.rtkcollector.core.session.SessionWriterCloseReport(
            issues = listOf(
                org.rtkcollector.core.session.SessionWriterIssue(
                    artifact = "session-writers",
                    category = org.rtkcollector.core.session.SessionWriterIssueCategory.RAW_RX,
                    severity = org.rtkcollector.core.session.SessionWriterIssueSeverity.FATAL,
                    message = error.message ?: "Writer close failed.",
                ),
            ),
        )
    }
if (closeReport != null && closeReport.issues.isNotEmpty()) {
    state = state.copy(
        lastError = closeReport.userMessage,
        errorCategory = RecordingErrorCategory.STORAGE,
        errorSeverity = if (closeReport.hasFatalIssue) RecordingErrorSeverity.FATAL else RecordingErrorSeverity.DEGRADED,
        rawRecordingActive = false,
    )
}
```

At the end of stop:

```kotlin
state = state.copy(
    running = false,
    lifecycle = if (state.errorSeverity == RecordingErrorSeverity.FATAL) RecordingLifecycleState.FAILED else RecordingLifecycleState.STOPPED,
    rawRecordingActive = false,
    correctionsActive = false,
)
stopping.set(false)
```

- [ ] **Step 5: Compile app integration**

Run:

```bash
ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK ANDROID_SDK_ROOT=/storage/3830-3863/Termux/AndroidSDK sh gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector add app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt app/src/main/kotlin/org/rtkcollector/app/recording/RecordingSessionWriters.kt
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector commit -m "Make recording stop idempotent"
```

---

### Task 7: Wire NTRIP Runtime Controller Into Foreground Service

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt`
- Modify: `core/correction/src/main/kotlin/org/rtkcollector/core/correction/NtripRuntimeController.kt`

- [ ] **Step 1: Add service field**

In `RecordingForegroundService`, replace `private var ntripClient: NtripClient? = null` with:

```kotlin
private var ntripController: org.rtkcollector.core.correction.NtripRuntimeController? = null
```

Remove the `ntripThread` field and its interrupt/join cleanup because the
controller owns the NTRIP worker thread.

- [ ] **Step 2: Replace NTRIP startup**

In `maybeStartNtrip(...)`, construct a `NtripRuntimeController`:

```kotlin
val config = org.rtkcollector.core.correction.NtripRuntimeConfig(
    request = request,
    ggaLines = listOfNotNull(ggaLine),
)
val controller = org.rtkcollector.core.correction.NtripRuntimeController(
    clientFactory = { runtimeConfig ->
        org.rtkcollector.core.correction.DefaultNtripRuntimeClient(
            NtripClient(
                request = runtimeConfig.request,
                reconnectPolicy = NtripReconnectPolicy(maxAttempts = Int.MAX_VALUE, delayMillis = 5_000),
            ),
        )
    },
    emit = { snapshot ->
        state = state.copy(
            ntripState = snapshot.state.name,
            lastError = snapshot.message,
            errorCategory = if (snapshot.state == org.rtkcollector.core.correction.NtripRuntimeState.AUTH_ERROR || snapshot.state == org.rtkcollector.core.correction.NtripRuntimeState.NETWORK_ERROR) RecordingErrorCategory.NTRIP else state.errorCategory,
            errorSeverity = if (snapshot.state == org.rtkcollector.core.correction.NtripRuntimeState.AUTH_ERROR || snapshot.state == org.rtkcollector.core.correction.NtripRuntimeState.NETWORK_ERROR) RecordingErrorSeverity.DEGRADED else state.errorSeverity,
            correctionsActive = snapshot.correctionsActive,
        )
        broadcastState()
    },
    onRtcmBytes = { bytes ->
        if (running.get()) {
            captureRuntime.injectCorrectionBytes(bytes)
            state = state.copy(
                correctionInputBytes = recorder.correctionInputBytes,
                txToReceiverBytes = recorder.txToReceiverBytes,
                correctionsActive = true,
            )
            broadcastState()
        }
    },
)
ntripController = controller
controller.start(config)
```

- [ ] **Step 3: Stop controller in service stop**

Replace `runCatching { ntripClient?.cancel() }` with:

```kotlin
runCatching { ntripController?.stop() }
```

Remove these lines from stop cleanup:

```kotlin
runCatching { ntripThread?.interrupt() }
runCatching { ntripThread?.join(1500) }
```

Replace `ntripClient = null` cleanup with:

```kotlin
ntripController = null
```

- [ ] **Step 4: Compile app and run core correction tests**

Run:

```bash
ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK ANDROID_SDK_ROOT=/storage/3830-3863/Termux/AndroidSDK sh gradlew :core:correction:test :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector add core/correction/src/main/kotlin/org/rtkcollector/core/correction/NtripRuntimeController.kt app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector commit -m "Route service NTRIP through runtime controller"
```

---

### Task 8: Add Live NTRIP Update And Disable Actions

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/MainActivity.kt`

- [ ] **Step 1: Add service actions and extras**

In `RecordingForegroundService.companion object`, add:

```kotlin
const val ACTION_UPDATE_NTRIP = "org.rtkcollector.app.recording.UPDATE_NTRIP"
const val ACTION_DISABLE_NTRIP = "org.rtkcollector.app.recording.DISABLE_NTRIP"
```

In `onStartCommand(...)`, add cases:

```kotlin
ACTION_UPDATE_NTRIP -> updateNtrip(intent)
ACTION_DISABLE_NTRIP -> disableNtrip()
```

- [ ] **Step 2: Add update and disable methods**

Add methods in `RecordingForegroundService`:

```kotlin
private fun updateNtrip(intent: Intent) {
    if (!running.get() || runtime == null) {
        state = state.copy(
            lastError = "Cannot update NTRIP: no active recording.",
            errorCategory = RecordingErrorCategory.NTRIP,
            errorSeverity = RecordingErrorSeverity.DEGRADED,
        )
        broadcastState()
        return
    }
    val host = intent.getStringExtra(EXTRA_NTRIP_HOST).orEmpty()
    val mountpoint = intent.getStringExtra(EXTRA_NTRIP_MOUNTPOINT).orEmpty()
    if (host.isBlank() || mountpoint.isBlank()) {
        state = state.copy(
            lastError = "Cannot update NTRIP: host and mountpoint are required.",
            errorCategory = RecordingErrorCategory.NTRIP,
            errorSeverity = RecordingErrorSeverity.DEGRADED,
        )
        broadcastState()
        return
    }
    val request = NtripRequest(
        host = host,
        port = validatePort(intent.getIntExtra(EXTRA_NTRIP_PORT, 2101)),
        mountpoint = mountpoint,
        credentials = intent.getStringExtra(EXTRA_NTRIP_USERNAME)?.takeIf { it.isNotBlank() }?.let { username ->
            NtripCredentials(username = username, password = intent.getStringExtra(EXTRA_NTRIP_PASSWORD).orEmpty())
        },
    )
    ntripController?.update(
        org.rtkcollector.core.correction.NtripRuntimeConfig(
            request = request,
            ggaLines = listOfNotNull(intent.getStringExtra(EXTRA_NTRIP_GGA)?.takeIf { it.isNotBlank() }),
        ),
    )
    writers?.appendEventJson("""{"type":"ntrip-config-updated","host":"${host.jsonEscape()}","mountpoint":"${mountpoint.jsonEscape()}","usernamePresent":${!intent.getStringExtra(EXTRA_NTRIP_USERNAME).isNullOrBlank()}}""")
}

private fun disableNtrip() {
    ntripController?.disable("User disabled NTRIP during recording.")
    writers?.appendEventJson("""{"type":"ntrip-disabled","reason":"user"}""")
    state = state.copy(ntripState = "DISABLED", correctionsActive = false)
    broadcastState()
}
```

If `jsonEscape()` is private below service methods, move it above or keep the existing private helper accessible inside the class.

- [ ] **Step 3: Add UI buttons**

In `MainActivity`, add fields:

```kotlin
private lateinit var updateNtripButton: Button
private lateinit var disableNtripButton: Button
```

Create buttons in `onCreate`:

```kotlin
updateNtripButton = Button(this).apply { text = "Update NTRIP while recording" }
disableNtripButton = Button(this).apply { text = "Disable NTRIP now" }
```

Add listeners:

```kotlin
updateNtripButton.setOnClickListener { updateNtripWhileRecording() }
disableNtripButton.setOnClickListener { startService(Intent(this, RecordingForegroundService::class.java).setAction(RecordingForegroundService.ACTION_DISABLE_NTRIP)) }
```

Add views near NTRIP controls:

```kotlin
root.addView(updateNtripButton)
root.addView(disableNtripButton)
```

- [ ] **Step 4: Add UI update method**

Add:

```kotlin
private fun updateNtripWhileRecording() {
    val host = ntripHostEdit.text.toString().trim()
    val port = parseIntField(ntripPortEdit, "NTRIP port", 1..65535) ?: return
    val mountpoint = ntripMountpointEdit.text.toString().trim()
    val username = ntripUsernameEdit.text.toString().trim()
    val secretRef = selectedNtripCasterProfile()?.secretId?.takeIf { it.isNotBlank() }
        ?: ntripCasterSecretRef(host, username)
    val runtimePassword = resolveNtripPassword(secretRef)
    val intent = Intent(this, RecordingForegroundService::class.java).apply {
        action = RecordingForegroundService.ACTION_UPDATE_NTRIP
        putExtra(RecordingForegroundService.EXTRA_NTRIP_HOST, host)
        putExtra(RecordingForegroundService.EXTRA_NTRIP_PORT, port)
        putExtra(RecordingForegroundService.EXTRA_NTRIP_MOUNTPOINT, mountpoint)
        putExtra(RecordingForegroundService.EXTRA_NTRIP_USERNAME, username)
        putExtra(RecordingForegroundService.EXTRA_NTRIP_PASSWORD, runtimePassword)
        putExtra(RecordingForegroundService.EXTRA_NTRIP_SECRET_REF, secretRef)
        putExtra(RecordingForegroundService.EXTRA_NTRIP_GGA, ntripGgaEdit.text.toString())
    }
    startService(intent)
    monitorText.text = "Requested live NTRIP update."
}
```

- [ ] **Step 5: Compile app integration**

Run:

```bash
ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK ANDROID_SDK_ROOT=/storage/3830-3863/Termux/AndroidSDK sh gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector add app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt app/src/main/kotlin/org/rtkcollector/app/MainActivity.kt
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector commit -m "Add live NTRIP recording controls"
```

---

### Task 9: Update Documentation For Hardened Field Operation

**Files:**
- Modify: `docs/user-workflows.md`
- Modify: `docs/android-background-operation.md`
- Modify: `docs/ntrip-and-corrections.md`
- Modify: `docs/session-format.md`

- [ ] **Step 1: Update user workflow docs**

In `docs/user-workflows.md`, add a subsection under Rover With NTRIP:

```markdown
### Live NTRIP Changes During Recording

For NTRIP-enabled workflows, the UI can request a live caster/mountpoint update
without stopping raw receiver recording. The service cancels the old NTRIP
client, records a redacted event, starts the new NTRIP client and continues
writing `receiver-rx.raw`. `correction-input.raw` and `tx-to-receiver.raw`
remain append-only across the switch.

The user can also disable NTRIP during recording. This stops correction feeding
but leaves raw receiver capture active.
```

- [ ] **Step 2: Update NTRIP docs**

In `docs/ntrip-and-corrections.md`, add:

```markdown
Authentication and authorisation failures (`401` and `403`) are terminal for
the active NTRIP attempt and must not be retried indefinitely. Network failures
are degraded retry states. In both cases, receiver recording continues unless
USB or raw storage fails.
```

- [ ] **Step 3: Update Android background docs**

In `docs/android-background-operation.md`, add:

```markdown
Stop is idempotent. Repeated stop requests must not duplicate receiver shutdown
commands or surface expected thread interruption as a user error. Raw writer
flush/close is prioritised over derived sidecar finalisation.
```

- [ ] **Step 4: Update session format docs**

In `docs/session-format.md`, add:

```markdown
Runtime NTRIP changes are represented as redacted `events.jsonl` entries, not
as markers in `receiver-rx.raw`. Future structured exports such as GPX must be
finalised with best-effort closing syntax; failure to finalise them is a
sidecar error and does not invalidate raw RX.
```

- [ ] **Step 5: Commit docs**

```bash
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector add docs/user-workflows.md docs/android-background-operation.md docs/ntrip-and-corrections.md docs/session-format.md
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector commit -m "Document hardened field recording behaviour"
```

---

### Task 10: Final Validation, Review, Commit, Push

**Files:**
- No new files expected beyond previous tasks.
- Check all changed source and docs.

- [ ] **Step 1: Run focused JVM tests**

Run:

```bash
ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK ANDROID_SDK_ROOT=/storage/3830-3863/Termux/AndroidSDK sh gradlew :core:capture:test :core:correction:test :core:session:test
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run broader pure module tests**

Run:

```bash
ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK ANDROID_SDK_ROOT=/storage/3830-3863/Termux/AndroidSDK sh gradlew :core:transport:test :core:capture:test :core:correction:test :core:session:test :core:quality:test :core:workflow:test :receiver:api:test :receiver:generic-nmea-rtcm:test :receiver:unicore-n4:test :receiver:ublox-m8:test
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Run Android Kotlin compile**

Run:

```bash
ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK ANDROID_SDK_ROOT=/storage/3830-3863/Termux/AndroidSDK sh gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Attempt full Android build and record local limitation**

Run:

```bash
ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK ANDROID_SDK_ROOT=/storage/3830-3863/Termux/AndroidSDK sh gradlew -Pandroid.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2 assembleDebug
```

Expected on this Termux device: may fail at Android resource linking with the known `aapt2`/android-36 compatibility limitation. If it fails, record the exact failure in the final report. If it passes on a host with compatible Android build tools, report APK path.

- [ ] **Step 5: Review current diff**

Run:

```bash
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector diff --check
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector status --short
```

Expected: no whitespace errors; only intended tracked changes and local `.superpowers/` if still present.

- [ ] **Step 6: Final commit if previous tasks were not already committed**

If any source/docs remain uncommitted:

```bash
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector add <changed-files>
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector commit -m "Harden field recording lifecycle"
```

- [ ] **Step 7: Push**

Run:

```bash
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector push
```

Expected: branch pushed to `origin/main`.

---

## Self-Review Checklist

- Spec coverage:
  - raw RX protection is covered by Tasks 1, 2, 3 and 6;
  - finalisable sidecars are covered by Task 2 and Task 9;
  - deterministic stop/restart is covered by Tasks 5 and 6;
  - NTRIP reconnect/auth/update/disable is covered by Tasks 4, 7 and 8;
  - non-NTRIP workflows avoiding caster connections is preserved by existing `EXTRA_NTRIP_ENABLED` and verified during Task 7 review;
  - actionable field errors are covered by Task 5 and Task 9.
- Placeholder scan: the plan contains no intentionally incomplete sections.
- Type consistency: names introduced in early tasks are reused consistently in later service/UI tasks.
