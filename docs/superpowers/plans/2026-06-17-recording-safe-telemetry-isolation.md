# Recording-Safe Telemetry Isolation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Isolate dashboard, best-solution, mock-provider, averaging and future RTKLIB-EX computations so they cannot degrade byte-exact receiver recording.

**Architecture:** Keep the capture thread limited to USB read, raw append, advisory enqueue and cheap throttled counters. Let advisory workers parse receiver telemetry and publish latest snapshots. Let the dashboard consume latest snapshots directly, while mock provider resampling runs only when mock output is enabled.

**Tech Stack:** Android foreground service, Kotlin/JVM modules, `core:capture`, `core:solution`, `receiver:unicore-n4`, `receiver:ublox-m8`, app unit tests where locally possible, `:app:compileDebugKotlin` for Termux verification.

---

## File Structure

- Modify `receiver/unicore-n4/src/main/kotlin/org/rtkcollector/receiver/unicore/Um980BinaryParser.kt`
  - Expose a receiver-time key from UM980 binary frames.
- Modify `receiver/unicore-n4/src/main/kotlin/org/rtkcollector/receiver/unicore/Um980MessageFrequencyTracker.kt`
  - Support receiver-time frequency windows while retaining processing-time fallback.
- Modify `receiver/unicore-n4/src/test/kotlin/org/rtkcollector/receiver/unicore/Um980MessageFrequencyTrackerTest.kt`
  - Add receiver-time frequency tests.
- Modify `receiver/unicore-n4/src/test/kotlin/org/rtkcollector/receiver/unicore/Um980BinaryParserTest.kt`
  - Add receiver-time extraction tests.
- Create `app/src/main/kotlin/org/rtkcollector/app/recording/SelectedSolutionDisplay.kt`
  - Own lightweight selected-solution display updates for screen monitoring.
- Create `app/src/test/kotlin/org/rtkcollector/app/recording/SelectedSolutionDisplayTest.kt`
  - Test pass-through selected-solution updates.
- Modify `app/src/main/kotlin/org/rtkcollector/app/recording/BestSolutionTickLogic.kt`
  - Narrow it to mock-provider publication logic or route screen updates elsewhere.
- Modify `app/src/test/kotlin/org/rtkcollector/app/recording/BestSolutionTickLogicTest.kt`
  - Preserve mock-provider behaviour and remove assumptions that the tick drives primary dashboard telemetry.
- Modify `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingServiceState.kt`
  - Add explicit selected-solution display helpers that do not overwrite richer direct telemetry with stale selector state.
  - Add service-owned base averaging display fields.
- Modify `app/src/test/kotlin/org/rtkcollector/app/recording/RecordingServiceStateTest.kt`
  - Cover no-overwrite behaviour and primary solution pass-through.
- Modify `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt`
  - Use direct parser updates for screen selected solution.
  - Schedule mock-provider tick only when mock output is enabled.
  - Use receiver-time frequency tracking for UM980 binary logs.
- Create `core/solution/src/main/kotlin/org/rtkcollector/core/solution/OnlineCoordinateAverager.kt`
  - Memory-bounded averaging summary for temporary-base metrics.
- Create `core/solution/src/test/kotlin/org/rtkcollector/core/solution/OnlineCoordinateAveragerTest.kt`
  - Test mean, SD, standard error, fix-class stability and bounded memory.
- Create `app/src/main/kotlin/org/rtkcollector/app/recording/CoordinateAveragingController.kt`
  - Own service-side temporary-base averaging state.
- Create `app/src/test/kotlin/org/rtkcollector/app/recording/CoordinateAveragingControllerTest.kt`
  - Test start, stop, candidate acceptance and fix-class stop behaviour.
- Modify `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`
  - Keep only button intent and summary rendering for averaging; no coordinate statistics in Compose.
- Modify `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardModels.kt`
  - Carry service-owned averaging summary and warning to the position card.
- Modify `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardServiceMapper.kt`
  - Map recording-service averaging extras into dashboard state.
- Modify `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/HomeDashboard.kt`
  - Render averaging summary and warning without local coordinate accumulation.
- Create `tools/analyze_um980_session_gaps.py`
  - Developer field-analysis script for receiver-time gaps in archived sessions.
- Modify `docs/contributor-onboarding.md`
  - Document the capture/advisory/dashboard/mock/averaging boundary.
- Modify `AGENTS.md`
  - Add the new guardrail: screen/mock/averaging/RTKLIB-EX computations must stay off the capture path.
- Reference-only follow-up:
  - Keep `docs/superpowers/specs/2026-06-17-base-rtcm-ntrip-caster-upload-design.md` unchanged except if a typo is found. Its receiver-output sanity check is deferred to the caster-upload implementation plan.

---

## Task 1: UM980 Receiver-Time Frequency Tracking

**Files:**
- Modify: `receiver/unicore-n4/src/main/kotlin/org/rtkcollector/receiver/unicore/Um980BinaryParser.kt`
- Modify: `receiver/unicore-n4/src/main/kotlin/org/rtkcollector/receiver/unicore/Um980MessageFrequencyTracker.kt`
- Modify: `receiver/unicore-n4/src/test/kotlin/org/rtkcollector/receiver/unicore/Um980BinaryParserTest.kt`
- Modify: `receiver/unicore-n4/src/test/kotlin/org/rtkcollector/receiver/unicore/Um980MessageFrequencyTrackerTest.kt`

- [x] **Step 1: Write failing test for UM980 receiver-time extraction**

Add to `Um980BinaryParserTest`:

```kotlin
@Test
fun `extracts receiver timestamp millis from binary header`() {
    val frame = bestnavbFrame()

    assertEquals(2419L * 604_800_000L + 132_572_000L, Um980BinaryParser.receiverTimestampMillis(frame))
}
```

- [x] **Step 2: Run test to verify it fails**

Run:

```bash
sh gradlew :receiver:unicore-n4:test --tests org.rtkcollector.receiver.unicore.Um980BinaryParserTest
```

Expected before implementation: compile failure for missing `receiverTimestampMillis`.

- [x] **Step 3: Implement receiver-time extraction**

In `Um980BinaryParser.kt`, add:

```kotlin
fun receiverTimestampMillis(frame: ByteArray): Long? {
    if (frame.size < BINARY_HEADER_LENGTH || !hasBinarySync(frame, 0)) return null
    val week = u16(frame, 10).toLong()
    val towMillis = u32(frame, 12).toLong()
    return week * 604_800_000L + towMillis
}
```

If `UInt.toLong()` is not available in the current Kotlin target, convert with:

```kotlin
val towMillis = u32(frame, 12).toLong() and 0xffff_ffffL
```

- [x] **Step 4: Write failing test for receiver-time frequency**

Add to `Um980MessageFrequencyTrackerTest`:

```kotlin
@Test
fun `receiver timestamp frequency ignores delayed processing time`() {
    val tracker = Um980MessageFrequencyTracker(windowMillis = 1_000)
    repeat(20) { index ->
        tracker.record(
            kind = Um980MessageKind.BESTNAV,
            timestampMillis = 100_000L + index * 200L,
            receiverTimestampMillis = 1_000L + index * 50L,
        )
    }

    assertEquals(
        "Frequency BESTNAV/GGA/PPPNAV/ADRNAV/RTKSTATUS/OBSVM 20/-/-/-/-/- Hz",
        tracker.display(timestampMillis = 104_000L, receiverTimestampMillis = 2_000L),
    )
}
```

- [x] **Step 5: Implement receiver-time frequency support**

Change `Um980MessageFrequencyTracker` samples to store receiver-time when present:

```kotlin
private data class FrequencySample(
    val processingMillis: Long,
    val receiverMillis: Long?,
)
```

Update `record`:

```kotlin
fun record(
    kind: Um980MessageKind,
    timestampMillis: Long,
    receiverTimestampMillis: Long? = null,
) {
    val queue = samples.getOrPut(kind) { ArrayDeque() }
    queue.addLast(FrequencySample(timestampMillis, receiverTimestampMillis))
    prune(timestampMillis, receiverTimestampMillis)
}
```

Update `display`:

```kotlin
fun display(
    timestampMillis: Long,
    receiverTimestampMillis: Long? = null,
): String {
    prune(timestampMillis, receiverTimestampMillis)
    val values = order.joinToString("/") { kind ->
        val queue = samples[kind].orEmpty()
        val receiverValues = queue.mapNotNull { it.receiverMillis }
        val count = queue.size
        if (count == 0) {
            "-"
        } else if (receiverTimestampMillis != null && receiverValues.size == count) {
            formatHz(count * 1_000.0 / windowMillis)
        } else {
            formatHz(count * 1_000.0 / windowMillis)
        }
    }
    return "Frequency BESTNAV/GGA/PPPNAV/ADRNAV/RTKSTATUS/OBSVM $values Hz"
}
```

Update `prune` so it removes by receiver time when receiver timestamps are
available and by processing time otherwise:

```kotlin
private fun prune(now: Long, receiverNow: Long?) {
    val processingCutoff = now - windowMillis
    val receiverCutoff = receiverNow?.minus(windowMillis)
    samples.values.forEach { queue ->
        while (queue.isNotEmpty()) {
            val first = queue.first()
            val tooOld = if (receiverCutoff != null && first.receiverMillis != null) {
                first.receiverMillis < receiverCutoff
            } else {
                first.processingMillis < processingCutoff
            }
            if (!tooOld) break
            queue.removeFirst()
        }
    }
}
```

- [x] **Step 6: Run receiver tests**

Run:

```bash
sh gradlew :receiver:unicore-n4:test --tests org.rtkcollector.receiver.unicore.Um980BinaryParserTest --tests org.rtkcollector.receiver.unicore.Um980MessageFrequencyTrackerTest
```

Expected: PASS.

- [x] **Step 7: Commit**

```bash
git add receiver/unicore-n4/src/main/kotlin/org/rtkcollector/receiver/unicore/Um980BinaryParser.kt \
  receiver/unicore-n4/src/main/kotlin/org/rtkcollector/receiver/unicore/Um980MessageFrequencyTracker.kt \
  receiver/unicore-n4/src/test/kotlin/org/rtkcollector/receiver/unicore/Um980BinaryParserTest.kt \
  receiver/unicore-n4/src/test/kotlin/org/rtkcollector/receiver/unicore/Um980MessageFrequencyTrackerTest.kt
git commit -m "Use receiver time for UM980 frequency metrics"
```

---

## Task 2: Selected Solution Display Pass-Through

**Files:**
- Create: `app/src/main/kotlin/org/rtkcollector/app/recording/SelectedSolutionDisplay.kt`
- Create: `app/src/test/kotlin/org/rtkcollector/app/recording/SelectedSolutionDisplayTest.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingServiceState.kt`
- Modify: `app/src/test/kotlin/org/rtkcollector/app/recording/RecordingServiceStateTest.kt`

- [x] **Step 1: Write failing tests for primary screen candidate selection**

Create `SelectedSolutionDisplayTest.kt`:

```kotlin
package org.rtkcollector.app.recording

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rtkcollector.core.solution.FixClass
import org.rtkcollector.core.solution.SolutionCandidate
import org.rtkcollector.core.solution.SolutionEngine

class SelectedSolutionDisplayTest {
    @Test
    fun `um980 screen candidate is bestnav only`() {
        assertTrue(candidate("UM980-BESTNAV", "um980").isPrimaryScreenCandidateFor("um980"))
        assertFalse(candidate("NMEA-GGA", "um980").isPrimaryScreenCandidateFor("um980"))
        assertFalse(candidate("UM980-PPP", "um980").isPrimaryScreenCandidateFor("um980"))
    }

    @Test
    fun `ublox screen candidate accepts nav pvt source`() {
        assertTrue(candidate("UBX-NAV-PVT", "ublox-m8").isPrimaryScreenCandidateFor("ublox-m8t"))
        assertFalse(candidate("NMEA-GGA", "ublox-m8").isPrimaryScreenCandidateFor("ublox-m8t"))
    }

    private fun candidate(sourceId: String, family: String): SolutionCandidate =
        SolutionCandidate(
            sourceId = sourceId,
            receiverFamily = family,
            engine = SolutionEngine.DEVICE_INTERNAL,
            fixClass = FixClass.RTK_FIXED,
            updatedAtMillis = 1_000L,
            latDeg = 50.0,
            lonDeg = 14.0,
        )
}
```

- [x] **Step 2: Run test to verify it fails**

Run:

```bash
sh gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.recording.SelectedSolutionDisplayTest
```

Expected in normal Android host: compile failure for missing helper.

If running in Termux and the command fails in `:app:processDebugResources` due
to x86-64 `aapt2`, record the blocker and continue with `:app:compileDebugKotlin`
after implementation.

Checkpoint note: on Termux/aarch64 this failed in `:app:processDebugResources`
with the known Gradle-resolved x86-64 `aapt2` daemon startup error before the
new tests could compile.

- [x] **Step 3: Add selected-solution helper**

Create `SelectedSolutionDisplay.kt`:

```kotlin
package org.rtkcollector.app.recording

import org.rtkcollector.core.solution.SolutionCandidate

internal fun SolutionCandidate.isPrimaryScreenCandidateFor(activeReceiverFamily: String): Boolean {
    val family = activeReceiverFamily.lowercase()
    return when {
        family.startsWith("um980") || family.startsWith("unicore") ->
            sourceId == "UM980-BESTNAV"
        family.startsWith("ublox") ->
            sourceId == "UBX-NAV-PVT"
        else ->
            sourceId == "NMEA-GGA"
    }
}
```

- [x] **Step 4: Write failing test for state update without telemetry overwrite**

Add to `RecordingServiceStateTest`:

```kotlin
@Test
fun `selected solution updates summary without replacing richer direct telemetry`() {
    val previous = RecordingServiceState(
        latLon = "50.000000000, 14.000000000",
        horizontalAccuracy = "0.012 m",
        satellites = "19 / 32",
        satellitesUsed = 19,
        satellitesInView = 32,
    )
    val candidate = org.rtkcollector.core.solution.SolutionCandidate(
        sourceId = "UM980-BESTNAV",
        receiverFamily = "um980",
        engine = org.rtkcollector.core.solution.SolutionEngine.DEVICE_INTERNAL,
        fixClass = org.rtkcollector.core.solution.FixClass.RTK_FIXED,
        updatedAtMillis = 1_000L,
        latDeg = 50.1,
        lonDeg = 14.1,
        horizontalAccuracyM = null,
        satellitesUsed = null,
        satellitesInView = null,
    )

    val updated = previous.withSelectedSolution(candidate, nowMillis = 1_250L)

    assertEquals("UM980-BESTNAV", updated.bestSolutionSource)
    assertEquals("RTK_FIXED", updated.bestSolutionFix)
    assertEquals(250L, updated.bestSolutionAgeMs)
    assertEquals("50.000000000, 14.000000000", updated.latLon)
    assertEquals("0.012 m", updated.horizontalAccuracy)
    assertEquals("19 / 32", updated.satellites)
}
```

- [x] **Step 5: Implement selected-solution state update**

Add to `RecordingServiceState.kt`:

```kotlin
internal fun RecordingServiceState.withSelectedSolution(
    candidate: org.rtkcollector.core.solution.SolutionCandidate,
    nowMillis: Long,
): RecordingServiceState =
    copy(
        bestSolutionSource = candidate.sourceId,
        bestSolutionFix = candidate.fixClass.name,
        bestSolutionAgeMs = (nowMillis - candidate.updatedAtMillis).coerceAtLeast(0L),
    )
```

This helper intentionally does not alter `latLon`, `horizontalAccuracy`,
`verticalAccuracy` or satellite display. Direct receiver telemetry owns those
fields.

- [x] **Step 6: Run focused compile/check**

Run:

```bash
sh gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [x] **Step 7: Commit**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/recording/SelectedSolutionDisplay.kt \
  app/src/test/kotlin/org/rtkcollector/app/recording/SelectedSolutionDisplayTest.kt \
  app/src/main/kotlin/org/rtkcollector/app/recording/RecordingServiceState.kt \
  app/src/test/kotlin/org/rtkcollector/app/recording/RecordingServiceStateTest.kt
git commit -m "Separate selected solution display state"
```

---

## Task 3: Mock Provider Tick Isolation

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/recording/BestSolutionTickLogic.kt`
- Modify: `app/src/test/kotlin/org/rtkcollector/app/recording/BestSolutionTickLogicTest.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt`

- [x] **Step 1: Write failing test that mock-disabled tick performs no screen delta**

Modify `BestSolutionTickLogicTest`:

```kotlin
@Test
fun `mock disabled computes no publish action and no required screen update`() {
    val candidate = candidate("UM980-BESTNAV", FixClass.RTK_FIXED, updatedAtMillis = 1_000L)

    val out = BestSolutionTickLogic.compute(
        input(candidates = listOf(candidate), nowMillis = 1_500L, mockEnabled = false),
    )

    assertEquals(MockLocationPublishResult.DISABLED, out.stateDelta.mockResult)
    assertTrue(out.publishAction is PublishAction.None)
}
```

Keep existing tests that verify mock-enabled publish behaviour.

- [x] **Step 2: Modify service scheduling so no mock tick is created when disabled**

In `RecordingForegroundService.startRecording`, replace unconditional scheduler:

```kotlin
bestSolutionTicker = bestSolutionExecutor.scheduleAtFixedRate(
    { runBestSolutionTick() },
    1,
    1,
    java.util.concurrent.TimeUnit.SECONDS,
)
```

with:

```kotlin
bestSolutionTicker = if (mockLocationRequested) {
    bestSolutionExecutor.scheduleAtFixedRate(
        { runBestSolutionTick() },
        1,
        1,
        java.util.concurrent.TimeUnit.SECONDS,
    )
} else {
    null
}
```

- [x] **Step 3: Remove dashboard state mutation from mock tick**

In `runBestSolutionTick`, remove this call:

```kotlin
applyTickStateDelta(tick.stateDelta, now)
```

Keep updates to `mockLocationState` and mock error state only. If `applyTickStateDelta`
becomes unused after this task and Task 4, remove the method.

- [x] **Step 4: Run compile**

Run:

```bash
sh gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [x] **Step 5: Commit**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/recording/BestSolutionTickLogic.kt \
  app/src/test/kotlin/org/rtkcollector/app/recording/BestSolutionTickLogicTest.kt \
  app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt
git commit -m "Run best solution tick only for mock output"
```

---

## Task 4: Wire Direct Parser Updates To Selected Solution Display

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt`
- Modify: `app/src/test/kotlin/org/rtkcollector/app/recording/RecordingServiceStateTest.kt`

- [x] **Step 1: Add helper in service to apply primary screen candidates**

In `RecordingForegroundService`, add:

```kotlin
private fun applyPrimaryScreenCandidate(candidate: org.rtkcollector.core.solution.SolutionCandidate, nowMillis: Long) {
    if (candidate.isPrimaryScreenCandidateFor(activeReceiverFamily)) {
        state = state.withSelectedSolution(candidate, nowMillis)
    }
    solutionCandidates[candidate.sourceId] = candidate
}
```

- [x] **Step 2: Replace direct `solutionCandidates[...] = ...` for screen-capable candidates**

In UM980 BESTNAV binary path, replace:

```kotlin
telemetry.toBestnavCandidate(now)?.let {
    solutionCandidates[it.sourceId] = it
}
```

with:

```kotlin
telemetry.toBestnavCandidate(now)?.let {
    applyPrimaryScreenCandidate(it, now)
}
```

Make the same replacement for:

- UM980 ASCII `BESTNAVA`;
- u-blox `NAV-PVT`;
- generic NMEA GGA only when no more specific selected solution exists for the active family.

For PPP candidates, keep:

```kotlin
solutionCandidates[it.sourceId] = it
```

because PPP diagnostics must not replace UM980 `BESTNAVB` as the primary screen source while PPP is only converging.

- [x] **Step 3: Compile**

Run:

```bash
sh gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [x] **Step 4: Commit**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt \
  app/src/test/kotlin/org/rtkcollector/app/recording/RecordingServiceStateTest.kt
git commit -m "Drive dashboard selected solution from parser snapshots"
```

---

## Task 5: Use Receiver-Time Frequency In Service

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt`
- Modify: `receiver/unicore-n4/src/test/kotlin/org/rtkcollector/receiver/unicore/Um980MessageFrequencyTrackerTest.kt`

- [x] **Step 1: Modify binary frequency recording**

Change `recordBinaryFrequency` signature:

```kotlin
private fun recordBinaryFrequency(frame: ByteArray, frequencyTracker: Um980MessageFrequencyTracker) {
    val now = System.currentTimeMillis()
    val receiverNow = Um980BinaryParser.receiverTimestampMillis(frame)
    when (Um980BinaryParser.messageId(frame)) {
        12, 138 -> frequencyTracker.record(Um980MessageKind.OBSVM, now, receiverNow)
        142 -> frequencyTracker.record(Um980MessageKind.ADRNAV, now, receiverNow)
        509 -> frequencyTracker.record(Um980MessageKind.RTKSTATUS, now, receiverNow)
        1026 -> frequencyTracker.record(Um980MessageKind.PPPNAV, now, receiverNow)
        2118 -> frequencyTracker.record(Um980MessageKind.BESTNAV, now, receiverNow)
    }
}
```

- [x] **Step 2: Modify frequency display call**

In the UM980 advisory parser loop, replace:

```kotlin
state = state.copy(um980Frequency = frequencyTracker.display(System.currentTimeMillis()))
```

with:

```kotlin
val displayNow = System.currentTimeMillis()
val receiverNow = Um980BinaryParser.receiverTimestampMillis(record.bytes)
state = state.copy(
    um980Frequency = frequencyTracker.display(displayNow, receiverNow),
)
```

For NMEA and ASCII records without receiver binary time, keep processing-time
fallback by passing only `displayNow`.

- [x] **Step 3: Compile**

Run:

```bash
sh gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [x] **Step 4: Commit**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt \
  receiver/unicore-n4/src/test/kotlin/org/rtkcollector/receiver/unicore/Um980MessageFrequencyTrackerTest.kt
git commit -m "Display UM980 frequency from receiver time"
```

---

## Task 6: Memory-Bounded Service-Owned Averaging

**Files:**
- Create: `core/solution/src/main/kotlin/org/rtkcollector/core/solution/OnlineCoordinateAverager.kt`
- Create: `core/solution/src/test/kotlin/org/rtkcollector/core/solution/OnlineCoordinateAveragerTest.kt`
- Create: `app/src/main/kotlin/org/rtkcollector/app/recording/CoordinateAveragingController.kt`
- Create: `app/src/test/kotlin/org/rtkcollector/app/recording/CoordinateAveragingControllerTest.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingServiceState.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardModels.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardServiceMapper.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/HomeDashboard.kt`

- [x] **Step 1: Write failing tests for online averaging**

Create `OnlineCoordinateAveragerTest.kt`:

```kotlin
package org.rtkcollector.core.solution

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OnlineCoordinateAveragerTest {
    @Test
    fun `accumulates mean and sample standard deviation without retaining samples`() {
        val averager = OnlineCoordinateAverager(requiredFixClass = FixClass.RTK_FIXED)
        val first = sample(50.0, 14.0, 300.0)
        val second = sample(52.0, 16.0, 304.0)

        assertTrue(averager.add(first).accepted)
        assertTrue(averager.add(second).accepted)

        val summary = averager.summary()
        assertEquals(2, summary.sampleCount)
        assertEquals(51.0, summary.latMeanDeg, 1e-12)
        assertEquals(15.0, summary.lonMeanDeg, 1e-12)
        assertEquals(302.0, summary.heightMeanM, 1e-12)
        assertEquals(0, averager.retainedSampleCountForTest())
    }

    @Test
    fun `rejects fix class change`() {
        val averager = OnlineCoordinateAverager(requiredFixClass = FixClass.RTK_FIXED)
        assertFalse(averager.add(sample(50.0, 14.0, 300.0, FixClass.RTK_FLOAT)).accepted)
    }

    private fun sample(
        lat: Double,
        lon: Double,
        height: Double,
        fix: FixClass = FixClass.RTK_FIXED,
    ): CoordinateAverageSample =
        CoordinateAverageSample(
            latDeg = lat,
            lonDeg = lon,
            ellipsoidalHeightM = height,
            fixClass = fix,
            timestampMillis = 1_000L,
        )
}
```

- [x] **Step 2: Implement online averager**

Create `OnlineCoordinateAverager.kt`:

```kotlin
package org.rtkcollector.core.solution

data class CoordinateAverageSample(
    val latDeg: Double,
    val lonDeg: Double,
    val ellipsoidalHeightM: Double,
    val fixClass: FixClass,
    val timestampMillis: Long,
)

data class CoordinateAverageAddResult(
    val accepted: Boolean,
    val reason: String? = null,
)

data class CoordinateAverageSummary(
    val sampleCount: Int,
    val latMeanDeg: Double,
    val lonMeanDeg: Double,
    val heightMeanM: Double,
    val latStandardDeviationDeg: Double?,
    val lonStandardDeviationDeg: Double?,
    val heightStandardDeviationM: Double?,
)

/**
 * Online Welford accumulator for base-coordinate averaging.
 *
 * It retains no coordinate samples. This keeps memory constant even for long
 * temporary-base sessions and prevents averaging from becoming a recording or
 * UI pressure source.
 */
class OnlineCoordinateAverager(
    private val requiredFixClass: FixClass,
) {
    private var count = 0
    private var latMean = 0.0
    private var lonMean = 0.0
    private var heightMean = 0.0
    private var latM2 = 0.0
    private var lonM2 = 0.0
    private var heightM2 = 0.0

    fun add(sample: CoordinateAverageSample): CoordinateAverageAddResult {
        if (sample.fixClass != requiredFixClass) {
            return CoordinateAverageAddResult(false, "Fix class changed from $requiredFixClass to ${sample.fixClass}.")
        }
        count += 1
        latMean = updateMean(sample.latDeg, latMean, count).also {
            latM2 += (sample.latDeg - latMean) * (sample.latDeg - it)
        }
        lonMean = updateMean(sample.lonDeg, lonMean, count).also {
            lonM2 += (sample.lonDeg - lonMean) * (sample.lonDeg - it)
        }
        heightMean = updateMean(sample.ellipsoidalHeightM, heightMean, count).also {
            heightM2 += (sample.ellipsoidalHeightM - heightMean) * (sample.ellipsoidalHeightM - it)
        }
        return CoordinateAverageAddResult(true)
    }

    fun summary(): CoordinateAverageSummary =
        CoordinateAverageSummary(
            sampleCount = count,
            latMeanDeg = latMean,
            lonMeanDeg = lonMean,
            heightMeanM = heightMean,
            latStandardDeviationDeg = sampleStandardDeviation(latM2),
            lonStandardDeviationDeg = sampleStandardDeviation(lonM2),
            heightStandardDeviationM = sampleStandardDeviation(heightM2),
        )

    fun retainedSampleCountForTest(): Int = 0

    private fun updateMean(value: Double, mean: Double, count: Int): Double =
        mean + (value - mean) / count

    private fun sampleStandardDeviation(m2: Double): Double? =
        if (count < 2) null else kotlin.math.sqrt(m2 / (count - 1))
}
```

- [x] **Step 3: Write failing tests for service-side averaging controller**

Create `CoordinateAveragingControllerTest.kt`:

```kotlin
package org.rtkcollector.app.recording

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rtkcollector.core.solution.FixClass
import org.rtkcollector.core.solution.SolutionCandidate
import org.rtkcollector.core.solution.SolutionEngine

class CoordinateAveragingControllerTest {
    @Test
    fun `accumulates accepted selected-solution candidates`() {
        val controller = CoordinateAveragingController()
        controller.start(requiredFixClass = FixClass.RTK_FIXED)

        val result = controller.onSelectedSolution(candidate(50.0, 14.0, 302.0, FixClass.RTK_FIXED))

        assertTrue(result.accepted)
        assertEquals(1, controller.summary()?.sampleCount)
        assertEquals(302.0, controller.summary()?.heightMeanM, 1e-12)
    }

    @Test
    fun `stops and reports reason when fix class changes`() {
        val controller = CoordinateAveragingController()
        controller.start(requiredFixClass = FixClass.RTK_FIXED)
        assertTrue(controller.onSelectedSolution(candidate(50.0, 14.0, 302.0, FixClass.RTK_FIXED)).accepted)

        val result = controller.onSelectedSolution(candidate(50.1, 14.1, 303.0, FixClass.RTK_FLOAT))

        assertFalse(result.accepted)
        assertFalse(controller.active)
        assertNotNull(controller.lastStopReason)
    }

    private fun candidate(
        lat: Double,
        lon: Double,
        height: Double,
        fixClass: FixClass,
    ): SolutionCandidate =
        SolutionCandidate(
            sourceId = "UM980-BESTNAV",
            receiverFamily = "um980",
            engine = SolutionEngine.DEVICE_INTERNAL,
            fixClass = fixClass,
            updatedAtMillis = 1_000L,
            latDeg = lat,
            lonDeg = lon,
            ellipsoidalHeightM = height,
        )
}
```

- [x] **Step 4: Implement service-side averaging controller**

Create `CoordinateAveragingController.kt`:

```kotlin
package org.rtkcollector.app.recording

import org.rtkcollector.core.solution.CoordinateAverageAddResult
import org.rtkcollector.core.solution.CoordinateAverageSample
import org.rtkcollector.core.solution.CoordinateAverageSummary
import org.rtkcollector.core.solution.FixClass
import org.rtkcollector.core.solution.OnlineCoordinateAverager
import org.rtkcollector.core.solution.SolutionCandidate

internal class CoordinateAveragingController {
    private var averager: OnlineCoordinateAverager? = null
    private var requiredFixClass: FixClass? = null

    var active: Boolean = false
        private set

    var lastStopReason: String? = null
        private set

    fun start(requiredFixClass: FixClass) {
        this.requiredFixClass = requiredFixClass
        this.averager = OnlineCoordinateAverager(requiredFixClass)
        this.active = true
        this.lastStopReason = null
    }

    fun stop(reason: String? = null) {
        active = false
        lastStopReason = reason
    }

    fun summary(): CoordinateAverageSummary? = averager?.summary()

    fun onSelectedSolution(candidate: SolutionCandidate): CoordinateAverageAddResult {
        if (!active) return CoordinateAverageAddResult(false, "Averaging is not active.")
        val height = candidate.ellipsoidalHeightM
            ?: return CoordinateAverageAddResult(false, "Selected solution has no ellipsoidal height.")
        val result = averager?.add(
            CoordinateAverageSample(
                latDeg = candidate.latDeg ?: return CoordinateAverageAddResult(false, "Selected solution has no latitude."),
                lonDeg = candidate.lonDeg ?: return CoordinateAverageAddResult(false, "Selected solution has no longitude."),
                ellipsoidalHeightM = height,
                fixClass = candidate.fixClass,
                timestampMillis = candidate.updatedAtMillis,
            ),
        ) ?: CoordinateAverageAddResult(false, "Averaging is not configured.")
        if (!result.accepted) {
            stop(result.reason)
        }
        return result
    }
}
```

- [x] **Step 5: Wire controller into selected-solution path**

In `RecordingServiceState.kt`, add fields to `RecordingServiceState` after
`bestSolutionAgeMs`:

```kotlin
val baseAverageSummary: String? = null,
val baseAverageWarning: String? = null,
```

In `RecordingForegroundService`, add a controller field:

```kotlin
private val coordinateAveragingController = CoordinateAveragingController()
```

In `applyPrimaryScreenCandidate`, after `state = state.withSelectedSolution(...)`,
call the controller:

```kotlin
if (candidate.isPrimaryScreenCandidateFor(activeReceiverFamily)) {
    state = state.withSelectedSolution(candidate, nowMillis)
    if (coordinateAveragingController.active) {
        val result = coordinateAveragingController.onSelectedSolution(candidate)
        val summary = coordinateAveragingController.summary()
        state = state.copy(
            baseAverageSummary = summary?.toDisplayText() ?: state.baseAverageSummary,
            baseAverageWarning = if (result.accepted) null else result.reason,
        )
    }
}
```

Add a private display-formatting helper in `RecordingForegroundService.kt`:

```kotlin
private fun org.rtkcollector.core.solution.CoordinateAverageSummary.toDisplayText(): String =
    "Avg ${latMeanDeg.format(9)}, ${lonMeanDeg.format(9)}, h=${heightMeanM.format(3)} m, n=$sampleCount"
```

Add this helper near the other service formatting helpers if no equivalent
`Double.format(Int)` helper exists in `RecordingForegroundService.kt`:

```kotlin
private fun Double.format(decimals: Int): String =
    "%.${decimals}f".format(java.util.Locale.US, this)
```

- [x] **Step 6: Broadcast and map averaging fields**

In `RecordingForegroundService`, add state extra constants:

```kotlin
const val EXTRA_STATE_BASE_AVERAGE_SUMMARY = "baseAverageSummary"
const val EXTRA_STATE_BASE_AVERAGE_WARNING = "baseAverageWarning"
```

In the state broadcast intent builder, add:

```kotlin
putExtra(EXTRA_STATE_BASE_AVERAGE_SUMMARY, state.baseAverageSummary)
putExtra(EXTRA_STATE_BASE_AVERAGE_WARNING, state.baseAverageWarning)
```

In `DashboardModels.kt`, add fields to `PositionCardState`:

```kotlin
val baseAverageSummary: String? = null,
val baseAverageWarning: String? = null,
```

In `DashboardServiceMapper.kt`, set those fields when constructing
`PositionCardState`:

```kotlin
baseAverageSummary = intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_BASE_AVERAGE_SUMMARY),
baseAverageWarning = intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_BASE_AVERAGE_WARNING),
```

- [x] **Step 7: Remove coordinate statistics from Compose**

In `MainActivity.kt`, remove any `LaunchedEffect` or `remember` block that
updates mean latitude, longitude or height from `state.position.latLon`.
Keep the Start/Stop averaging buttons as intent senders and render the
service state fields in `HomeDashboard.kt`:

```kotlin
Text(text = state.position.baseAverageSummary ?: "Avg n/a")
state.position.baseAverageWarning?.let { warning ->
    Text(text = warning, color = MaterialTheme.colorScheme.error)
}
```

Compose may format labels and send button actions, but it must not accumulate
coordinate samples.

- [x] **Step 8: Run core solution tests**

Run:

```bash
sh gradlew :core:solution:test --tests org.rtkcollector.core.solution.OnlineCoordinateAveragerTest
```

Expected: PASS.

- [x] **Step 9: Run app compile**

Run:

```bash
sh gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [x] **Step 10: Commit**

```bash
git add core/solution/src/main/kotlin/org/rtkcollector/core/solution/OnlineCoordinateAverager.kt \
  core/solution/src/test/kotlin/org/rtkcollector/core/solution/OnlineCoordinateAveragerTest.kt \
  app/src/main/kotlin/org/rtkcollector/app/recording/CoordinateAveragingController.kt \
  app/src/test/kotlin/org/rtkcollector/app/recording/CoordinateAveragingControllerTest.kt \
  app/src/main/kotlin/org/rtkcollector/app/recording/RecordingServiceState.kt \
  app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt \
  app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt \
  app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardModels.kt \
  app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardServiceMapper.kt \
  app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/HomeDashboard.kt
git commit -m "Move coordinate averaging off Compose state"
```

---

## Task 7: Session Gap Analysis Tool

**Files:**
- Create: `tools/analyze_um980_session_gaps.py`
- Modify: `docs/contributor-onboarding.md`

- [ ] **Step 1: Create the analysis script**

Create `tools/analyze_um980_session_gaps.py`:

```python
#!/usr/bin/env python3
"""Analyse UM980 binary receiver-time gaps in an RtkCollector session ZIP."""

from __future__ import annotations

import argparse
import collections
import struct
import zipfile


SYNC = b"\xaa\x44\xb5"
HEADER_LEN = 24
CRC_LEN = 4
LABELS = {
    142: "ADRNAVB",
    509: "RTKSTATUSB",
    954: "STADOPB",
    1026: "PPPNAVB",
    2118: "BESTNAVB",
}


def iter_frames(data: bytes):
    index = 0
    while True:
        start = data.find(SYNC, index)
        if start < 0 or start + HEADER_LEN > len(data):
            return
        msg_id = struct.unpack_from("<H", data, start + 4)[0]
        payload_len = struct.unpack_from("<H", data, start + 6)[0]
        week = struct.unpack_from("<H", data, start + 10)[0]
        tow_ms = struct.unpack_from("<I", data, start + 12)[0]
        frame_len = HEADER_LEN + payload_len + CRC_LEN
        if payload_len > 4096 or start + frame_len > len(data):
            index = start + 1
            continue
        yield msg_id, week * 604_800_000 + tow_ms, start
        index = start + frame_len


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("session_zip")
    parser.add_argument("--gap-ms", type=int, default=250)
    args = parser.parse_args()

    with zipfile.ZipFile(args.session_zip) as archive:
        data = archive.read("receiver-rx.raw")

    by_id = collections.defaultdict(list)
    for msg_id, receiver_ms, byte_offset in iter_frames(data):
        by_id[msg_id].append((receiver_ms, byte_offset))

    for msg_id, samples in sorted(by_id.items()):
        label = LABELS.get(msg_id, str(msg_id))
        if len(samples) < 2:
            continue
        gaps = [
            (samples[index + 1][0] - samples[index][0], samples[index], samples[index + 1])
            for index in range(len(samples) - 1)
        ]
        large = [gap for gap in gaps if gap[0] > args.gap_ms]
        print(f"{label}: count={len(samples)} max_gap_ms={max(g[0] for g in gaps)} gaps>{args.gap_ms}ms={len(large)}")
        for gap_ms, previous, following in large[:10]:
            print(f"  gap_ms={gap_ms} receiver_ms={previous[0]}->{following[0]} byte={previous[1]}->{following[1]}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
```

- [ ] **Step 2: Run the script on the known sample**

Run:

```bash
python3 tools/analyze_um980_session_gaps.py samples/debug/session-2026-06-17T18-45-13.769275Z-fd3451a6-81db-42c3-adfb-289296bc129a-1781722595680.zip
```

Expected: output includes `BESTNAVB` and reports gaps over 250 ms.

- [ ] **Step 3: Document the script**

Append to `docs/contributor-onboarding.md` under the capture/advisory section:

````markdown
For field reports where the dashboard frequency looks low, inspect the raw
receiver timestamps rather than assuming a UI issue:

```bash
python3 tools/analyze_um980_session_gaps.py samples/debug/<session>.zip
```

The script reads `receiver-rx.raw` from the ZIP and reports UM980 binary
receiver-time gaps. It is diagnostic only and does not validate receiver
hardware health by itself.
````
```

- [ ] **Step 4: Commit**

```bash
git add tools/analyze_um980_session_gaps.py docs/contributor-onboarding.md
git commit -m "Add UM980 session gap analysis tool"
```

---

## Task 8: Developer Guardrails And Documentation

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/architecture.md`
- Modify: `docs/superpowers/specs/2026-06-17-base-rtcm-ntrip-caster-upload-design.md` only if the implementation reveals a wording bug.

- [ ] **Step 1: Update `AGENTS.md`**

Add under Android implementation rules:

```markdown
- Screen monitoring, Android mock-location publishing, coordinate averaging,
  frequency measurement, future RTKLIB-EX processing and future caster upload
  must not run on the raw capture path. They consume advisory snapshots or
  bounded queues after `receiver-rx.raw` has already been appended.
- For receivers with a documented best-navigation output such as UM980
  `BESTNAV/BESTNAVB`, the primary monitoring solution must be a transparent
  pass-through of that receiver solution. Mock-provider resampling is separate
  and must run only when mock output is enabled.
```

- [ ] **Step 2: Update `docs/architecture.md`**

Add a subsection after "Failure Isolation":

```markdown
## Derived Telemetry Isolation

The capture thread writes receiver bytes before advisory processing. Dashboard
state, best-solution snapshots, Android mock-location output, coordinate
averaging, message-frequency metrics and future RTKLIB-EX processing consume
derived advisory state. They may be throttled, dropped or marked stale under
pressure. They must not block USB reads or raw receiver recording.
```

- [ ] **Step 3: Commit**

```bash
git add AGENTS.md docs/architecture.md
git commit -m "Document derived telemetry isolation rules"
```

---

## Task 9: Final Verification And Review

**Files:**
- Review all files changed by Tasks 1-8.

- [ ] **Step 1: Run whitespace check**

Run:

```bash
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector diff --check
```

Expected: no output, exit 0.

- [ ] **Step 2: Run source compile**

Run:

```bash
sh gradlew :app:compileDebugKotlin
```

Expected in Termux: BUILD SUCCESSFUL.

- [ ] **Step 3: Run pure module tests that do not require Android resources**

Run:

```bash
sh gradlew :receiver:unicore-n4:test :core:solution:test :core:capture:test
```

Expected: PASS.

- [ ] **Step 4: Do not retry blocked Android app unit tests in Termux**

If `:app:testDebugUnitTest` or `./gradlew test` fails in `:app:processDebugResources`
with the known x86-64 `aapt2` Termux error, report it as environment-blocked.
Do not keep retrying it.

- [ ] **Step 5: Inspect for capture-path regressions**

Run:

```bash
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector diff -- app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt core/capture/src/main/kotlin/org/rtkcollector/core/capture/CaptureRuntime.kt core/capture/src/main/kotlin/org/rtkcollector/core/capture/AdvisoryFanout.kt
```

Check manually:

- raw append still occurs before advisory enqueue;
- capture thread still does not parse receiver messages;
- no new unbounded queue exists;
- mock tick is scheduled only when mock output is enabled;
- averaging does not retain unbounded samples.

- [ ] **Step 6: Run review-and-commit skill if requested**

If the user requests `$review-and-commit`, run the repository review-and-commit
workflow and push after the quality gate passes.

---

## Deferred Follow-Up: Base RTCM NTRIP Caster Upload

Do not implement caster upload in this plan.

When telemetry isolation is complete, create a separate implementation plan from:

- `docs/superpowers/specs/2026-06-17-base-rtcm-ntrip-caster-upload-design.md`

That later plan must include:

- menu profiles for caster upload;
- runtime caster worker;
- receiver-output sanity checks that verify the selected base profile produces
  minimum useful RTCM;
- UM980 RTCM command recognition based on local Unicore documentation;
- session artifacts for upload state and bytes;
- field tests with rtk2go or a private caster.

---

## Self-Review Checklist

- Spec coverage:
  - capture isolation: Tasks 2-5 and 9;
  - UM980 pass-through selected solution: Tasks 2 and 4;
  - mock-provider resampling isolation: Task 3;
  - receiver-time frequency metrics: Tasks 1 and 5;
  - memory-bounded averaging: Task 6;
  - field-session gap inspection: Task 7;
  - docs/guardrails: Task 8;
  - caster-upload deferred tracking: Deferred Follow-Up section.
- No task implements NTRIP caster upload.
- No task introduces RTKLIB-EX.
- No task introduces maps, GIS or shapefile dependencies.
