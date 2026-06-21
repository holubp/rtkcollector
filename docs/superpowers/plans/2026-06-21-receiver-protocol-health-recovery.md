# Receiver Protocol Health Recovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Detect and recover from sessions where USB continues delivering bytes but the selected receiver protocol stops producing valid UM980/u-blox/NMEA frames.

**Architecture:** Raw receiver bytes remain authoritative and are always appended before any health logic runs. Existing byte-level health still detects complete USB silence. This plan adds protocol-level health as a separate advisory signal: parser/fanout code records timestamps of valid receiver frames, the service detects "bytes without valid protocol frames" after a conservative threshold, writes a session event, marks USB degraded, and reuses the existing USB reconnect/re-init path without stopping the recording session.

**Tech Stack:** Kotlin/JVM tests, Android foreground service, existing `RecordingHealthMonitor`, `Um980StreamParser`, `UbloxStreamParser`, `RecordingForegroundService`, Gradle `:core:capture:test` and `:app:compileDebugKotlin`.

---

## Background And Current Failure Mode

The session `samples/debug/session-2026-06-21T08-16-32.324161Z-407a4c3d-4af4-4b2c-b92d-26356e9d4659-1782064340631.zip` showed:

- `receiver-rx.raw` continued growing to about 86.9 MB.
- Valid UM980 frames stopped after byte offset about 13.9 MB.
- The remaining raw bytes had no UM980 binary sync (`AA 44 B5`), no NMEA (`$GP`/`$GN`) and no RTCM3 preamble (`D3`).
- `receiver-solution.nmea` was complete for recognized `BESTNAVB` frames, but it naturally stopped when valid `BESTNAVB` stopped.

Current code in `RecordingHealthMonitor.recordReceiverRead(...)` treats any positive byte count as healthy receiver RX. That correctly avoids stopping raw capture on parser failures, but it misses the "garbage bytes / no protocol sync" case.

## File Structure

- Modify `docs/specification/android-runtime.md`
  - Add a formal requirement for protocol-level receiver stalls while bytes continue to arrive.
- Modify `docs/specification/verification-matrix.md`
  - Add verification row/status for the new requirement.
- Modify `core/capture/src/main/kotlin/org/rtkcollector/core/capture/RecordingHealthMonitor.kt`
  - Extend existing health events and monitor state with protocol-health detection.
- Modify `core/capture/src/test/kotlin/org/rtkcollector/core/capture/RecordingHealthMonitorTest.kt`
  - Add focused tests for bytes-without-valid-frame stalls and recovery.
- Modify `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt`
  - Reset protocol health on start/reconnect.
  - Record receiver bytes into protocol health after raw append.
  - Mark valid protocol frames from UM980, u-blox and generic NMEA advisory parsers.
  - Handle protocol stall/recovery events with existing degraded USB state and reconnect path.

No samples or debug captures should be committed.

## Task 1: Specification Alignment

**Files:**
- Modify: `docs/specification/android-runtime.md`
- Modify: `docs/specification/verification-matrix.md`

- [ ] **Step 1: Add formal Android runtime requirement**

In `docs/specification/android-runtime.md`, after `ANDROID-USB-003`, add:

```markdown
### ANDROID-USB-004: Receiver Protocol Stalls Are Degraded USB Failures

Status: Normative

During an active recording, receiver-originated bytes that do not produce valid
frames for the selected receiver protocol MUST NOT be treated as healthy
recording indefinitely. If bytes continue to arrive but no valid UM980 binary
or ASCII/NMEA frame, u-blox UBX/NMEA frame, or generic NMEA frame is observed
for the configured protocol-stall threshold, the foreground service MUST mark
USB capture as degraded, write a session event, keep recording raw bytes
byte-exactly, and attempt to reopen the selected receiver and rerun the
required receiver init/baud sequence. Parser failures and invalid bytes MUST
NOT be written into `receiver-rx.raw` as metadata and MUST NOT stop the
recording session while storage and transport still accept bytes.

Verification:
- Automated: recording health monitor protocol-stall/recovery tests.
- Automated: service compile check showing protocol-health hooks are advisory.
- Manual: long-running UM980 binary session where USB keeps producing bytes but
  valid UM980 sync disappears.
```

- [ ] **Step 2: Add verification matrix row**

In `docs/specification/verification-matrix.md`, add this row near `ANDROID-USB-003`:

```markdown
| `ANDROID-USB-004` | Automated + manual | Recording health monitor protocol-stall tests; UM980 garbage-bytes session regression | Needs field validation | Detect bytes-without-valid-protocol-frame and reconnect without stopping raw capture. |
```

- [ ] **Step 3: Review documentation diff**

Run:

```bash
git diff -- docs/specification/android-runtime.md docs/specification/verification-matrix.md
```

Expected: only the new USB protocol-health requirement and matrix row are changed.

- [ ] **Step 4: Commit checkpoint**

Run:

```bash
git add docs/specification/android-runtime.md docs/specification/verification-matrix.md
git commit -m "docs: specify receiver protocol health recovery"
```

## Task 2: Core Protocol Health Monitor Tests

**Files:**
- Modify: `core/capture/src/test/kotlin/org/rtkcollector/core/capture/RecordingHealthMonitorTest.kt`

- [ ] **Step 1: Write failing protocol-stall test**

Append this test to `RecordingHealthMonitorTest`:

```kotlin
@Test
fun `receiver protocol stall is reported when bytes arrive without valid frames`() {
    val monitor = RecordingHealthMonitor(
        receiverProtocolStallMillis = 15_000,
        repeatMillis = 5_000,
    )
    monitor.reset(nowMillis = 1_000)

    monitor.recordReceiverProtocolBytes(byteCount = 4096, nowMillis = 5_000)

    assertTrue(
        monitor.checkReceiverProtocol(
            nowMillis = 15_999,
            protocolFramesExpected = true,
            receiverFamily = "um980-n4",
        ).isEmpty(),
    )

    val first = monitor.checkReceiverProtocol(
        nowMillis = 16_000,
        protocolFramesExpected = true,
        receiverFamily = "um980-n4",
    )

    assertEquals(
        listOf(
            RecordingHealthEvent.ReceiverProtocolStalled(
                receiverFamily = "um980-n4",
                staleForMillis = 15_000,
                bytesSinceLastValidFrame = 4096,
            ),
        ),
        first,
    )

    assertTrue(
        monitor.checkReceiverProtocol(
            nowMillis = 20_999,
            protocolFramesExpected = true,
            receiverFamily = "um980-n4",
        ).isEmpty(),
    )

    val repeated = monitor.checkReceiverProtocol(
        nowMillis = 21_000,
        protocolFramesExpected = true,
        receiverFamily = "um980-n4",
    )

    assertEquals(
        listOf(
            RecordingHealthEvent.ReceiverProtocolStalled(
                receiverFamily = "um980-n4",
                staleForMillis = 20_000,
                bytesSinceLastValidFrame = 4096,
            ),
        ),
        repeated,
    )
}
```

- [ ] **Step 2: Write failing no-bytes guard test**

Append:

```kotlin
@Test
fun `receiver protocol stall is not reported when no receiver bytes arrived`() {
    val monitor = RecordingHealthMonitor(
        receiverProtocolStallMillis = 15_000,
        repeatMillis = 5_000,
    )
    monitor.reset(nowMillis = 1_000)

    val events = monitor.checkReceiverProtocol(
        nowMillis = 30_000,
        protocolFramesExpected = true,
        receiverFamily = "um980-n4",
    )

    assertTrue(events.isEmpty())
}
```

- [ ] **Step 3: Write failing protocol recovery test**

Append:

```kotlin
@Test
fun `receiver protocol recovery is reported after a stalled protocol frame resumes`() {
    val monitor = RecordingHealthMonitor(
        receiverProtocolStallMillis = 1_000,
        repeatMillis = 1_000,
    )
    monitor.reset(nowMillis = 0)
    monitor.recordReceiverProtocolBytes(byteCount = 128, nowMillis = 100)
    monitor.checkReceiverProtocol(
        nowMillis = 1_000,
        protocolFramesExpected = true,
        receiverFamily = "ublox-m8t",
    )

    val events = monitor.recordValidReceiverProtocolFrame(
        nowMillis = 1_100,
        receiverFamily = "ublox-m8t",
    )

    assertEquals(
        listOf(RecordingHealthEvent.ReceiverProtocolRecovered(receiverFamily = "ublox-m8t")),
        events,
    )
    assertTrue(
        monitor.checkReceiverProtocol(
            nowMillis = 1_500,
            protocolFramesExpected = true,
            receiverFamily = "ublox-m8t",
        ).isEmpty(),
    )
}
```

- [ ] **Step 4: Run test and verify failure**

Run:

```bash
sh gradlew :core:capture:test --tests org.rtkcollector.core.capture.RecordingHealthMonitorTest
```

Expected before implementation: Kotlin compilation fails because `receiverProtocolStallMillis`, `ReceiverProtocolStalled`, `ReceiverProtocolRecovered`, `recordReceiverProtocolBytes`, `checkReceiverProtocol` and `recordValidReceiverProtocolFrame` do not exist.

## Task 3: Core Protocol Health Monitor Implementation

**Files:**
- Modify: `core/capture/src/main/kotlin/org/rtkcollector/core/capture/RecordingHealthMonitor.kt`

- [ ] **Step 1: Add protocol events**

Modify `RecordingHealthEvent` to:

```kotlin
sealed class RecordingHealthEvent {
    data class ReceiverRxStalled(val staleForMillis: Long) : RecordingHealthEvent()
    data object ReceiverRxRecovered : RecordingHealthEvent()
    data class ReceiverProtocolStalled(
        val receiverFamily: String,
        val staleForMillis: Long,
        val bytesSinceLastValidFrame: Long,
    ) : RecordingHealthEvent()
    data class ReceiverProtocolRecovered(val receiverFamily: String) : RecordingHealthEvent()
    data class CorrectionsStalled(val staleForMillis: Long) : RecordingHealthEvent()
    data object CorrectionsRecovered : RecordingHealthEvent()
}
```

- [ ] **Step 2: Add constructor parameter and state**

Change the constructor and state block to:

```kotlin
class RecordingHealthMonitor(
    private val receiverStallMillis: Long = DEFAULT_RECEIVER_STALL_MILLIS,
    private val receiverProtocolStallMillis: Long = DEFAULT_RECEIVER_PROTOCOL_STALL_MILLIS,
    private val correctionStallMillis: Long = DEFAULT_CORRECTION_STALL_MILLIS,
    private val repeatMillis: Long = DEFAULT_REPEAT_MILLIS,
) {
    private var lastReceiverBytesAtMillis: Long = 0L
    private var lastReceiverProtocolFrameAtMillis: Long = 0L
    private var receiverBytesSinceLastProtocolFrame: Long = 0L
    private var lastCorrectionBytesAtMillis: Long = 0L
    private var lastReceiverStallEventAtMillis: Long? = null
    private var lastReceiverProtocolStallEventAtMillis: Long? = null
    private var lastCorrectionStallEventAtMillis: Long? = null
    private var receiverStalled: Boolean = false
    private var receiverProtocolStalled: Boolean = false
    private var correctionsStalled: Boolean = false
```

- [ ] **Step 3: Reset protocol state**

Inside `reset(nowMillis)`, add:

```kotlin
lastReceiverProtocolFrameAtMillis = nowMillis
receiverBytesSinceLastProtocolFrame = 0L
lastReceiverProtocolStallEventAtMillis = null
receiverProtocolStalled = false
```

The full reset method should become:

```kotlin
fun reset(nowMillis: Long) {
    lastReceiverBytesAtMillis = nowMillis
    lastReceiverProtocolFrameAtMillis = nowMillis
    receiverBytesSinceLastProtocolFrame = 0L
    lastCorrectionBytesAtMillis = nowMillis
    lastReceiverStallEventAtMillis = null
    lastReceiverProtocolStallEventAtMillis = null
    lastCorrectionStallEventAtMillis = null
    receiverStalled = false
    receiverProtocolStalled = false
    correctionsStalled = false
}
```

- [ ] **Step 4: Add receiver protocol byte tracking**

Add these methods after `recordReceiverRead(...)`:

```kotlin
@Synchronized
fun recordReceiverProtocolBytes(byteCount: Int, nowMillis: Long) {
    if (byteCount > 0) {
        receiverBytesSinceLastProtocolFrame += byteCount.toLong()
    }
}

@Synchronized
fun recordValidReceiverProtocolFrame(
    nowMillis: Long,
    receiverFamily: String,
): List<RecordingHealthEvent> {
    lastReceiverProtocolFrameAtMillis = nowMillis
    receiverBytesSinceLastProtocolFrame = 0L
    lastReceiverProtocolStallEventAtMillis = null
    return if (receiverProtocolStalled) {
        receiverProtocolStalled = false
        listOf(RecordingHealthEvent.ReceiverProtocolRecovered(receiverFamily))
    } else {
        emptyList()
    }
}

@Synchronized
fun checkReceiverProtocol(
    nowMillis: Long,
    protocolFramesExpected: Boolean,
    receiverFamily: String,
): List<RecordingHealthEvent> {
    if (!protocolFramesExpected || receiverBytesSinceLastProtocolFrame <= 0L) {
        return emptyList()
    }
    val staleForMillis = (nowMillis - lastReceiverProtocolFrameAtMillis).coerceAtLeast(0L)
    if (staleForMillis < receiverProtocolStallMillis ||
        !shouldRepeat(lastReceiverProtocolStallEventAtMillis, nowMillis)
    ) {
        return emptyList()
    }
    receiverProtocolStalled = true
    lastReceiverProtocolStallEventAtMillis = nowMillis
    return listOf(
        RecordingHealthEvent.ReceiverProtocolStalled(
            receiverFamily = receiverFamily,
            staleForMillis = staleForMillis,
            bytesSinceLastValidFrame = receiverBytesSinceLastProtocolFrame,
        ),
    )
}
```

Keep these methods synchronized because the capture loop and asynchronous advisory fanout run on different threads.

- [ ] **Step 5: Add default constant**

In the companion object, add:

```kotlin
const val DEFAULT_RECEIVER_PROTOCOL_STALL_MILLIS: Long = 15_000L
```

The companion object should contain:

```kotlin
companion object {
    const val DEFAULT_RECEIVER_STALL_MILLIS: Long = 10_000L
    const val DEFAULT_RECEIVER_PROTOCOL_STALL_MILLIS: Long = 15_000L
    const val DEFAULT_CORRECTION_STALL_MILLIS: Long = 15_000L
    const val DEFAULT_REPEAT_MILLIS: Long = 10_000L
}
```

- [ ] **Step 6: Run core capture tests**

Run:

```bash
sh gradlew :core:capture:test --tests org.rtkcollector.core.capture.RecordingHealthMonitorTest
```

Expected: all `RecordingHealthMonitorTest` tests pass.

- [ ] **Step 7: Commit checkpoint**

Run:

```bash
git add core/capture/src/main/kotlin/org/rtkcollector/core/capture/RecordingHealthMonitor.kt \
  core/capture/src/test/kotlin/org/rtkcollector/core/capture/RecordingHealthMonitorTest.kt
git commit -m "feat: detect receiver protocol stalls"
```

## Task 4: Foreground Service Protocol Health Integration

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt`

- [ ] **Step 1: Record receiver bytes into protocol health**

In `captureLoop(...)`, immediately after:

```kotlin
val nowMillis = SystemClock.elapsedRealtime()
val receiverHealthEvents = recordingHealthMonitor.recordReceiverRead(bytesRead, nowMillis)
```

add:

```kotlin
recordingHealthMonitor.recordReceiverProtocolBytes(bytesRead, nowMillis)
val receiverProtocolHealthEvents = recordingHealthMonitor.checkReceiverProtocol(
    nowMillis = nowMillis,
    protocolFramesExpected = shouldExpectReceiverProtocolFrames(),
    receiverFamily = activeReceiverFamily,
)
```

Then after:

```kotlin
handleRecordingHealthEvents(receiverHealthEvents, recorder)
```

add:

```kotlin
handleRecordingHealthEvents(receiverProtocolHealthEvents, recorder)
```

The order should remain:

```kotlin
handleRecordingHealthEvents(receiverHealthEvents, recorder)
handleRecordingHealthEvents(receiverProtocolHealthEvents, recorder)
handleRecordingHealthEvents(correctionHealthEvents, recorder)
```

- [ ] **Step 2: Add receiver-family gate**

Add this helper near `shouldExpectCorrectionBytes()`:

```kotlin
private fun shouldExpectReceiverProtocolFrames(): Boolean =
    running.get() &&
        (
            activeReceiverFamily.startsWith("um980", ignoreCase = true) ||
                activeReceiverFamily.startsWith("unicore", ignoreCase = true) ||
                activeReceiverFamily.startsWith("ublox", ignoreCase = true) ||
                activeReceiverFamily.contains("nmea", ignoreCase = true)
        )
```

- [ ] **Step 3: Add helper to mark valid receiver protocol frames**

Add this helper near `handleRecordingHealthEvents(...)`:

```kotlin
private fun markValidReceiverProtocolFrame(nowMillis: Long) {
    val events = recordingHealthMonitor.recordValidReceiverProtocolFrame(
        nowMillis = nowMillis,
        receiverFamily = activeReceiverFamily,
    )
    if (events.isNotEmpty()) {
        handleRecordingHealthEvents(events, activeRecorder)
    }
}
```

- [ ] **Step 4: Mark valid UM980/generic records**

In `buildAdvisoryFanout(...)`, inside the `"um980-mixed-stream"` consumer loop, add this at the beginning of `streamParser.accept(bytes).forEach { record ->`:

```kotlin
if (record.kind == "nmea" || record.kind == "unicore_ascii" || record.kind == "unicore_binary") {
    markValidReceiverProtocolFrame(android.os.SystemClock.elapsedRealtime())
}
```

This deliberately treats generic NMEA as valid protocol data because non-u-blox receiver families currently pass through this mixed-stream parser.

- [ ] **Step 5: Mark valid u-blox records**

In `parseUbloxAdvisory(...)`, inside `ubloxStreamParser.accept(bytes).forEach { record ->`, add before the existing `if (record.kind == "ubx")` block:

```kotlin
if (record.kind == "ubx" || record.kind == "nmea") {
    markValidReceiverProtocolFrame(nowMillis)
}
```

- [ ] **Step 6: Handle protocol stalled and recovered events**

In `handleRecordingHealthEvents(...)`, add cases after `ReceiverRxRecovered` and before `CorrectionsStalled`:

```kotlin
is RecordingHealthEvent.ReceiverProtocolStalled -> {
    val message = "Receiver bytes are arriving but no valid ${event.receiverFamily} protocol frames " +
        "were decoded for ${event.staleForMillis / 1000}s " +
        "(${event.bytesSinceLastValidFrame} bytes since last valid frame); reconnecting."
    state = state.copy(
        lastError = message,
        errorCategory = RecordingErrorCategory.USB,
        errorSeverity = RecordingErrorSeverity.DEGRADED,
        rawRecordingActive = false,
    )
    recordSessionEvent("receiver-protocol-stalled", message)
    recordRuntimeDiagnostic(
        category = DiagnosticCategory.USB,
        severity = RecordingErrorSeverity.DEGRADED.name,
        message = { message },
        attributes = {
            mapOf(
                "receiverFamily" to event.receiverFamily,
                "staleForMillis" to event.staleForMillis.toString(),
                "bytesSinceLastValidFrame" to event.bytesSinceLastValidFrame.toString(),
            )
        },
    )
    if (recorder != null && !tryReconnectUsb(recorder)) {
        recordSessionEvent("usb-reconnect-pending", "USB receiver reconnect attempt did not succeed yet.")
    }
    broadcastState()
    updateForegroundNotification(force = true)
}
is RecordingHealthEvent.ReceiverProtocolRecovered -> {
    val message = "Valid ${event.receiverFamily} receiver protocol frames resumed."
    recordSessionEvent("receiver-protocol-recovered", message)
    if (state.errorCategory == RecordingErrorCategory.USB &&
        state.errorSeverity == RecordingErrorSeverity.DEGRADED
    ) {
        state = state.copy(
            lastError = null,
            errorCategory = RecordingErrorCategory.NONE,
            errorSeverity = RecordingErrorSeverity.NONE,
            rawRecordingActive = true,
        )
    } else {
        state = state.copy(rawRecordingActive = true)
    }
    broadcastState()
    updateForegroundNotification(force = true)
}
```

- [ ] **Step 7: Reset protocol health after USB reconnect succeeds**

In `tryReconnectUsb(...)`, after the existing `recordSessionEvent("usb-reconnect-succeeded", ...)`, add:

```kotlin
recordingHealthMonitor.reset(SystemClock.elapsedRealtime())
```

This prevents immediate repeated protocol-stall events while the receiver is reopening and profile commands are being reapplied.

- [ ] **Step 8: Run app compile**

Run:

```bash
sh gradlew :app:compileDebugKotlin
```

Expected: Kotlin compilation passes.

- [ ] **Step 9: Commit checkpoint**

Run:

```bash
git add app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt
git commit -m "feat: reconnect on receiver protocol stalls"
```

## Task 5: Regression Test With Synthetic UM980 Garbage Stream

**Files:**
- Create: `tools/test_um980_protocol_gap_session.py`

- [ ] **Step 1: Add a source-level synthetic regression test**

Create `tools/test_um980_protocol_gap_session.py`:

```python
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SERVICE = ROOT / "app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt"
MONITOR = ROOT / "core/capture/src/main/kotlin/org/rtkcollector/core/capture/RecordingHealthMonitor.kt"


def test_service_records_protocol_bytes_and_checks_protocol_health():
    service = SERVICE.read_text(encoding="utf-8")
    assert "recordReceiverProtocolBytes(bytesRead, nowMillis)" in service
    assert "checkReceiverProtocol(" in service
    assert "shouldExpectReceiverProtocolFrames()" in service


def test_service_marks_valid_um980_and_ublox_protocol_frames():
    service = SERVICE.read_text(encoding="utf-8")
    assert 'record.kind == "nmea" || record.kind == "unicore_ascii" || record.kind == "unicore_binary"' in service
    assert 'record.kind == "ubx" || record.kind == "nmea"' in service
    assert "markValidReceiverProtocolFrame(" in service


def test_service_reconnects_on_protocol_stall_without_stopping_session():
    service = SERVICE.read_text(encoding="utf-8")
    assert "receiver-protocol-stalled" in service
    assert "tryReconnectUsb(recorder)" in service
    assert "Receiver bytes are arriving but no valid" in service
    assert "stopRecording(" not in service.split("receiver-protocol-stalled", 1)[1].split("receiver-protocol-recovered", 1)[0]


def test_monitor_has_protocol_stall_event_and_threshold():
    monitor = MONITOR.read_text(encoding="utf-8")
    assert "ReceiverProtocolStalled" in monitor
    assert "ReceiverProtocolRecovered" in monitor
    assert "DEFAULT_RECEIVER_PROTOCOL_STALL_MILLIS" in monitor
```

This is intentionally a source-structure regression test because the Android service is hard to unit-test directly in the current Termux environment. It complements the core JVM tests from Task 3.

- [ ] **Step 2: Run Python regression test**

Run:

```bash
python3 -m pytest tools/test_um980_protocol_gap_session.py
```

Expected: test passes after Task 4 is implemented.

- [ ] **Step 3: Commit checkpoint**

Run:

```bash
git add tools/test_um980_protocol_gap_session.py
git commit -m "test: cover receiver protocol stall service hooks"
```

## Task 6: Verification And Review

**Files:**
- Review all modified files.
- Do not add anything under `samples/`.

- [ ] **Step 1: Confirm samples are untouched**

Run:

```bash
git status --short
```

Expected: no `samples/` files staged or modified.

- [ ] **Step 2: Run whitespace check**

Run:

```bash
git diff --check
```

Expected: no whitespace errors.

- [ ] **Step 3: Run focused tests**

Run sequentially, not in parallel:

```bash
sh gradlew :core:capture:test --tests org.rtkcollector.core.capture.RecordingHealthMonitorTest
python3 -m pytest tools/test_um980_protocol_gap_session.py
sh gradlew :app:compileDebugKotlin
```

Expected:

- `RecordingHealthMonitorTest` passes.
- Python source-structure test passes.
- App Kotlin compilation passes.

- [ ] **Step 4: Manual field validation**

On Android with UM980:

1. Select a UM980 binary profile that emits `BESTNAVB`.
2. Start a rover or rover+NTRIP recording.
3. Confirm `receiver-rx.raw` grows and dashboard updates normally.
4. Induce the bad-stream condition if reproducible, or run a long session.
5. Expected if valid UM980 frames disappear while bytes continue:
   - session event `receiver-protocol-stalled`;
   - visible degraded USB error mentioning bytes without valid protocol frames;
   - USB reconnect/re-init attempt;
   - raw file continues to append bytes until reconnect;
   - after valid frames resume, session event `receiver-protocol-recovered`.

- [ ] **Step 5: Architecture review checklist**

Inspect:

```bash
rg -n "ReceiverProtocol|recordReceiverProtocol|receiver-protocol|tryReconnectUsb|appendReceiverBytes|stopRecording" \
  core/capture app/src/main/kotlin/org/rtkcollector/app/recording docs/specification tools
```

Expected:

- `appendReceiverBytes(...)` still happens before advisory parsing.
- Protocol-health code never writes metadata into `receiver-rx.raw`.
- Protocol stall handling calls `tryReconnectUsb(recorder)` and does not call `stopRecording(...)`.
- Valid-frame detection is in advisory parser paths.

- [ ] **Step 6: Request code review**

Use `superpowers:requesting-code-review` with reviewer scopes:

- Android robust background-capture engineer: verify protocol-health detection cannot block raw recording and reconnect does not discard session state.
- GNSS receiver-protocol reviewer: verify valid-frame markers are appropriate for UM980 binary/ASCII/NMEA and u-blox UBX/NMEA.
- Kotlin maintainer: verify the health monitor API is small, testable and thread-safe.

- [ ] **Step 7: Final commit if review changes were needed**

If review fixes are applied after previous checkpoints, run:

```bash
git add core/capture app/src/main/kotlin/org/rtkcollector/app/recording docs/specification tools
git commit -m "fix: address receiver protocol health review"
```

- [ ] **Step 8: Push after final validation**

Run:

```bash
git push
```

Expected: branch pushed with no `samples/` captures included.

## Risk Notes

- The protocol-health signal is advisory. It must never decide whether a byte is written to `receiver-rx.raw`; raw append already happened before this logic.
- Valid-frame detection runs through the existing asynchronous advisory fanout. If that queue is badly delayed or drops chunks, protocol health may become conservative and request reconnect. This is acceptable only because it is a degraded recovery path and must be visible through session events.
- The threshold is intentionally longer than normal UM980/u-blox message periods. UM980 binary profiles should emit valid frames many times per second; u-blox profiles should emit UBX/NMEA at least once per second.
- The reconnect path must reapply the same USB baud/init/runtime sequence as existing USB recovery. Do not add a separate receiver-reset command unless a later receiver-specific spec requires it.
- Do not treat RTCM/NTRIP bytes as receiver-protocol recovery. Correction health is handled separately.
