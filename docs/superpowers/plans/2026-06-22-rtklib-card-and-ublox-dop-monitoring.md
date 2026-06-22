# RTKLIB Card And u-blox DOP Monitoring Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show RTKLIB solution position/height/accuracy inside the RTKLIB dashboard card and fill u-blox M8T in-device UTC/DOP monitoring from UBX telemetry.

**Architecture:** RTKLIB solution values stay inside the RTKLIB card and do not feed the main Position/Fix cards. u-blox NAV-PVT remains the source for in-device position, UTC and H/V accuracy; a new NAV-DOP parser supplies PDOP/HDOP/VDOP. All parsing and dashboard state remain advisory and must not affect raw receiver recording.

**Tech Stack:** Kotlin/JVM tests, Android foreground service state extras, Compose dashboard models, u-blox UBX parser, Gradle `:receiver:ublox-m8:test`, `:app:testDebugUnitTest`, `:app:compileDebugKotlin`.

---

## Source Design

Implement the approved spec:

```text
docs/superpowers/specs/2026-06-22-rtklib-card-and-ublox-dop-monitoring-design.md
```

Do not modify or commit `samples/` captures. If local transfer settings must be edited, start from:

```text
samples/rtkcollector-settings-1781935653886-ublox-rtklib-updated-no-passwords.json
```

and verify no plaintext passwords or unrelated NTRIP changes are introduced. The sample file is local evidence and must remain unstaged unless the user explicitly changes the repository policy.

## File Structure

- Modify `app/src/main/kotlin/org/rtkcollector/app/recording/BestSolutionTickLogic.kt`
  - Add UTC to the advisory display delta copied from `SolutionCandidate` / `BestSolutionSnapshot`.
- Modify `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingServiceState.kt`
  - Add RTKLIB display fields and propagate UTC from selected solution deltas.
- Modify `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt`
  - Format RTKLIB latest solution fields into state and broadcast extras.
  - Parse u-blox NAV-DOP and update Fix-card DOP state.
  - Record NAV-DOP frequency.
- Modify `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardModels.kt`
  - Add RTKLIB card display fields.
  - Update default u-blox frequency line to include NAV-DOP.
- Modify `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardServiceMapper.kt`
  - Map new RTKLIB extras into `RtklibCardState`.
- Modify `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/HomeDashboard.kt`
  - Render the new RTKLIB solution metrics compactly.
- Modify `receiver/ublox-m8/src/main/kotlin/org/rtkcollector/receiver/ublox/UbloxTelemetry.kt`
  - Add a small `UbloxNavDopTelemetry` data class.
- Create `receiver/ublox-m8/src/main/kotlin/org/rtkcollector/receiver/ublox/UbloxNavDopParser.kt`
  - Decode UBX-NAV-DOP.
- Modify `receiver/ublox-m8/src/main/kotlin/org/rtkcollector/receiver/ublox/UbloxMessageFrequencyTracker.kt`
  - Add `NAV_DOP`.
- Modify `receiver/ublox-m8/src/main/kotlin/org/rtkcollector/receiver/ublox/UbloxM8tProfiles.kt`
  - Add NAV-DOP enable command to `raw5HzRtklibEx` and `rawStatusMock`; leave `raw1HzSafe` unchanged.
- Modify tests:
  - `app/src/test/kotlin/org/rtkcollector/app/ui/dashboard/DashboardServiceMapperTest.kt`
  - `app/src/test/kotlin/org/rtkcollector/app/profile/ProfileDefaultsTest.kt`
  - `app/src/test/kotlin/org/rtkcollector/app/recording/RecordingServiceStateTest.kt`
  - `receiver/ublox-m8/src/test/kotlin/org/rtkcollector/receiver/ublox/UbloxNavDopParserTest.kt`
  - `receiver/ublox-m8/src/test/kotlin/org/rtkcollector/receiver/ublox/UbloxMessageFrequencyTrackerTest.kt`

## Task 1: RTKLIB Dashboard State And Mapping

**Files:**
- Modify: `app/src/test/kotlin/org/rtkcollector/app/ui/dashboard/DashboardServiceMapperTest.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardModels.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardServiceMapper.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/HomeDashboard.kt`

- [ ] **Step 1: Add failing mapper test for RTKLIB solution display**

Append this test to `DashboardServiceMapperTest`:

```kotlin
@Test
fun `rtklib card maps position height and accuracy`() {
    val intent = Intent(RecordingForegroundService.ACTION_STATE).apply {
        putExtra(RecordingForegroundService.EXTRA_STATE_RUNNING, true)
        putExtra(RecordingForegroundService.EXTRA_STATE_RTKLIB_STATE, "RUNNING")
        putExtra(RecordingForegroundService.EXTRA_STATE_RTKLIB_FIX_CLASS, "RTK_FIXED")
        putExtra(RecordingForegroundService.EXTRA_STATE_RTKLIB_SOLUTION_AGE_MS, 120L)
        putExtra(RecordingForegroundService.EXTRA_STATE_RTKLIB_LAT_LON, "50.087451200, 14.421253400")
        putExtra(RecordingForegroundService.EXTRA_STATE_RTKLIB_ELLIPSOIDAL_HEIGHT, "287.423 m")
        putExtra(RecordingForegroundService.EXTRA_STATE_RTKLIB_ACCURACY_HV, "8.0 mm / 12.0 mm")
    }

    val state = dashboardStateFromRecordingIntent(intent)

    assertEquals("RUNNING", state.rtklib?.state)
    assertEquals("RTK_FIXED", state.rtklib?.fixClass)
    assertEquals("120 ms", state.rtklib?.age)
    assertEquals("50.087451200, 14.421253400", state.rtklib?.latLon)
    assertEquals("287.423 m", state.rtklib?.ellipsoidalHeight)
    assertEquals("8.0 mm / 12.0 mm", state.rtklib?.accuracyHv)
}
```

- [ ] **Step 2: Run mapper test and verify failure**

Run:

```bash
sh gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.ui.dashboard.DashboardServiceMapperTest
```

Expected before implementation: Kotlin compilation fails because the new RTKLIB extras and `RtklibCardState` fields do not exist.

- [ ] **Step 3: Extend `RtklibCardState`**

In `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardModels.kt`, update `RtklibCardState` to include:

```kotlin
data class RtklibCardState(
    val state: String = "Disabled",
    val routePlan: String = "n/a",
    val snapshotId: String = "n/a",
    val lastError: String = "n/a",
    val fixClass: String = "n/a",
    val age: String = "n/a",
    val latLon: String = "n/a",
    val ellipsoidalHeight: String = "n/a",
    val accuracyHv: String = "n/a",
    val roverQueue: String = "0 B",
    val correctionQueue: String = "0 B",
    val dropped: String = "0 B / 0 B",
    val decoded: String = "0 rover / 0 corr",
    val outputs: String = "0 NMEA / 0 POS",
)
```

- [ ] **Step 4: Map new RTKLIB extras**

In `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardServiceMapper.kt`, update `rtklibCardFrom(...)`:

```kotlin
return RtklibCardState(
    state = state,
    routePlan = intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_RTKLIB_ROUTE_PLAN) ?: "n/a",
    snapshotId = intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_RTKLIB_SNAPSHOT_ID) ?: "n/a",
    lastError = intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_RTKLIB_LAST_ERROR) ?: "n/a",
    fixClass = intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_RTKLIB_FIX_CLASS) ?: "n/a",
    age = positiveLongExtra(intent, RecordingForegroundService.EXTRA_STATE_RTKLIB_SOLUTION_AGE_MS)
        ?.let { "${it} ms" }
        ?: "n/a",
    latLon = intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_RTKLIB_LAT_LON) ?: "n/a",
    ellipsoidalHeight = intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_RTKLIB_ELLIPSOIDAL_HEIGHT) ?: "n/a",
    accuracyHv = intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_RTKLIB_ACCURACY_HV) ?: "n/a",
    roverQueue = formatBytes(intent.getIntExtra(RecordingForegroundService.EXTRA_STATE_RTKLIB_ROVER_QUEUE_BYTES, 0).toLong()),
    correctionQueue = formatBytes(
        intent.getIntExtra(RecordingForegroundService.EXTRA_STATE_RTKLIB_CORRECTION_QUEUE_BYTES, 0).toLong(),
    ),
    dropped = formatBytes(
        intent.getLongExtra(RecordingForegroundService.EXTRA_STATE_RTKLIB_DROPPED_ROVER_BYTES, 0),
    ) + " / " + formatBytes(
        intent.getLongExtra(RecordingForegroundService.EXTRA_STATE_RTKLIB_DROPPED_CORRECTION_BYTES, 0),
    ),
    decoded = "${intent.getLongExtra(RecordingForegroundService.EXTRA_STATE_RTKLIB_DECODED_ROVER_EPOCHS, 0)} rover / " +
        "${intent.getLongExtra(RecordingForegroundService.EXTRA_STATE_RTKLIB_DECODED_CORRECTION_MESSAGES, 0)} corr",
    outputs = "${intent.getLongExtra(RecordingForegroundService.EXTRA_STATE_RTKLIB_OUTPUT_NMEA_LINES, 0)} NMEA / " +
        "${intent.getLongExtra(RecordingForegroundService.EXTRA_STATE_RTKLIB_OUTPUT_POS_LINES, 0)} POS",
)
```

- [ ] **Step 5: Render metrics inside the RTKLIB card**

In `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/HomeDashboard.kt`, update `RtklibCard(...)` so the top part is:

```kotlin
MajorValue(state.state)
Metric("Fix", state.fixClass)
Metric("Age", state.age)
Metric("Lat/Lon", state.latLon)
Metric("Ell h", state.ellipsoidalHeight)
Metric("Acc H/V", state.accuracyHv)
Metric("Route", state.routePlan)
Metric("Snapshot", state.snapshotId)
```

Keep the existing error block and queue/decoded/output metrics after this. Do not add altitude.

- [ ] **Step 6: Defer the mapper test until Task 2 adds service extras**

Do not run the mapper test at the end of Task 1. The test references new `RecordingForegroundService` constants that are introduced in Task 2. Run the command in Task 2 Step 7 after those constants and broadcast extras exist:

```bash
sh gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.ui.dashboard.DashboardServiceMapperTest
```

Expected after Task 2: the RTKLIB mapper test compiles and passes.

## Task 2: RTKLIB Service Extras And Formatting

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingServiceState.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt`

- [ ] **Step 1: Add RTKLIB display fields to service state**

In `RecordingServiceState`, add fields after `rtklibSolutionAgeMs`:

```kotlin
val rtklibLatLon: String = "n/a",
val rtklibEllipsoidalHeight: String = "n/a",
val rtklibAccuracyHv: String = "n/a",
```

- [ ] **Step 2: Add formatting helper for RTKLIB H/V accuracy**

In `RecordingForegroundService`, near existing display helpers, add:

```kotlin
private fun accuracyPairDisplay(horizontalM: Double?, verticalM: Double?): String =
    if (horizontalM != null && verticalM != null) {
        "${metersDisplay(horizontalM)} / ${metersDisplay(verticalM)}"
    } else {
        "n/a"
    }
```

Use the existing `metersDisplay` helper; do not add a new unit system.

- [ ] **Step 3: Copy RTKLIB latest solution into state**

In `RecordingForegroundService.broadcastState()`, replace the RTKLIB `state.copy(...)` block with one that first stores `val solution = snapshot.latestSolution`, then includes:

```kotlin
val solution = snapshot.latestSolution
state = state.copy(
    rtklibState = snapshot.state.name,
    rtklibLastError = snapshot.lastError ?: state.rtklibLastError,
    rtklibFixClass = solution?.fixClass?.name ?: state.rtklibFixClass,
    rtklibSolutionAgeMs = solution?.timestampMillis?.let { timestamp ->
        (System.currentTimeMillis() - timestamp).coerceAtLeast(0L)
    } ?: state.rtklibSolutionAgeMs,
    rtklibLatLon = if (solution?.latDeg != null && solution.lonDeg != null) {
        latLonDisplay(solution.latDeg, solution.lonDeg)
    } else {
        state.rtklibLatLon
    },
    rtklibEllipsoidalHeight = solution?.ellipsoidalHeightM?.let(::metersDisplay)
        ?: state.rtklibEllipsoidalHeight,
    rtklibAccuracyHv = accuracyPairDisplay(
        solution?.horizontalAccuracyM,
        solution?.verticalAccuracyM,
    ).takeUnless { it == "n/a" } ?: state.rtklibAccuracyHv,
    rtklibRoverQueueBytes = snapshot.roverQueueBytes,
    rtklibCorrectionQueueBytes = snapshot.correctionQueueBytes,
    rtklibDroppedRoverBytes = snapshot.droppedRoverBytes,
    rtklibDroppedCorrectionBytes = snapshot.droppedCorrectionBytes,
    rtklibDecodedRoverEpochs = snapshot.decodedRoverEpochs,
    rtklibDecodedCorrectionMessages = snapshot.decodedCorrectionMessages,
    rtklibOutputNmeaLines = snapshot.outputNmeaLines,
    rtklibOutputPosLines = snapshot.outputPosLines,
)
```

- [ ] **Step 4: Add RTKLIB extras constants**

In `RecordingForegroundService` companion object, after `EXTRA_STATE_RTKLIB_SOLUTION_AGE_MS`, add:

```kotlin
const val EXTRA_STATE_RTKLIB_LAT_LON = "rtklibLatLon"
const val EXTRA_STATE_RTKLIB_ELLIPSOIDAL_HEIGHT = "rtklibEllipsoidalHeight"
const val EXTRA_STATE_RTKLIB_ACCURACY_HV = "rtklibAccuracyHv"
```

- [ ] **Step 5: Broadcast new RTKLIB extras**

In the `Intent(ACTION_STATE).apply { ... }` block, after `EXTRA_STATE_RTKLIB_SOLUTION_AGE_MS`, add:

```kotlin
putExtra(EXTRA_STATE_RTKLIB_LAT_LON, state.rtklibLatLon)
putExtra(EXTRA_STATE_RTKLIB_ELLIPSOIDAL_HEIGHT, state.rtklibEllipsoidalHeight)
putExtra(EXTRA_STATE_RTKLIB_ACCURACY_HV, state.rtklibAccuracyHv)
```

- [ ] **Step 6: Include fields in RTKLIB status JSONL**

In `maybeAppendRtklibStatus(...)`, extend the JSON string with non-secret solution fields:

```kotlin
,"latDeg":${snapshot.latestSolution?.latDeg ?: "null"},"lonDeg":${snapshot.latestSolution?.lonDeg ?: "null"},"ellipsoidalHeightM":${snapshot.latestSolution?.ellipsoidalHeightM ?: "null"},"horizontalAccuracyM":${snapshot.latestSolution?.horizontalAccuracyM ?: "null"},"verticalAccuracyM":${snapshot.latestSolution?.verticalAccuracyM ?: "null"}
```

Keep this bounded at the existing one-line-per-second cadence.

- [ ] **Step 7: Run RTKLIB dashboard mapper test**

Run:

```bash
sh gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.ui.dashboard.DashboardServiceMapperTest
```

Expected: the new RTKLIB mapper test passes.

- [ ] **Step 8: Commit checkpoint**

Run:

```bash
git add app/src/main/kotlin/org/rtkcollector/app/recording/RecordingServiceState.kt \
  app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt \
  app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardModels.kt \
  app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardServiceMapper.kt \
  app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/HomeDashboard.kt \
  app/src/test/kotlin/org/rtkcollector/app/ui/dashboard/DashboardServiceMapperTest.kt
git commit -m "feat: show rtklib solution details on dashboard"
```

## Task 3: u-blox NAV-DOP Parser

**Files:**
- Create: `receiver/ublox-m8/src/test/kotlin/org/rtkcollector/receiver/ublox/UbloxNavDopParserTest.kt`
- Create: `receiver/ublox-m8/src/main/kotlin/org/rtkcollector/receiver/ublox/UbloxNavDopParser.kt`
- Modify: `receiver/ublox-m8/src/main/kotlin/org/rtkcollector/receiver/ublox/UbloxTelemetry.kt`

- [ ] **Step 1: Add failing NAV-DOP parser test**

Create `receiver/ublox-m8/src/test/kotlin/org/rtkcollector/receiver/ublox/UbloxNavDopParserTest.kt`:

```kotlin
package org.rtkcollector.receiver.ublox

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class UbloxNavDopParserTest {
    @Test
    fun `parses nav dop centi dop values`() {
        val payload = ByteArray(18)
        ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(0, 123_000)
            putShort(4, 210)
            putShort(6, 180)
            putShort(8, 90)
            putShort(10, 160)
            putShort(12, 120)
            putShort(14, 130)
            putShort(16, 140)
        }
        val frame = UbloxFrame.build(0x01, 0x04, payload)

        val telemetry = UbloxNavDopParser.parse(frame, nowMillis = 4_000L)

        assertEquals(4_000L, telemetry?.updatedAtMillis)
        assertEquals(2.10, telemetry?.gdop)
        assertEquals(1.80, telemetry?.pdop)
        assertEquals(0.90, telemetry?.tdop)
        assertEquals(1.60, telemetry?.vdop)
        assertEquals(1.20, telemetry?.hdop)
        assertEquals(1.30, telemetry?.ndop)
        assertEquals(1.40, telemetry?.edop)
    }

    @Test
    fun `rejects truncated nav dop payload`() {
        val frame = UbloxFrame.build(0x01, 0x04, ByteArray(17))

        assertNull(UbloxNavDopParser.parse(frame, nowMillis = 0L))
    }

    @Test
    fun `rejects non nav dop frame`() {
        val frame = UbloxFrame.build(0x01, 0x07, ByteArray(18))

        assertNull(UbloxNavDopParser.parse(frame, nowMillis = 0L))
    }
}
```

- [ ] **Step 2: Run parser test and verify failure**

Run:

```bash
sh gradlew :receiver:ublox-m8:test --tests org.rtkcollector.receiver.ublox.UbloxNavDopParserTest
```

Expected before implementation: compile fails because `UbloxNavDopParser` does not exist.

- [ ] **Step 3: Add telemetry model**

In `UbloxTelemetry.kt`, after `UbloxNavSatTelemetry`, add:

```kotlin
data class UbloxNavDopTelemetry(
    val updatedAtMillis: Long,
    val gdop: Double,
    val pdop: Double,
    val tdop: Double,
    val vdop: Double,
    val hdop: Double,
    val ndop: Double,
    val edop: Double,
)
```

- [ ] **Step 4: Implement NAV-DOP parser**

Create `receiver/ublox-m8/src/main/kotlin/org/rtkcollector/receiver/ublox/UbloxNavDopParser.kt`:

```kotlin
package org.rtkcollector.receiver.ublox

import java.nio.ByteBuffer
import java.nio.ByteOrder

object UbloxNavDopParser {
    fun parse(frame: ByteArray, nowMillis: Long): UbloxNavDopTelemetry? {
        if (!UbloxFrame.isValid(frame)) return null
        if ((frame[2].toInt() and 0xff) != 0x01 || (frame[3].toInt() and 0xff) != 0x04) return null
        val length = (frame[4].toInt() and 0xff) or ((frame[5].toInt() and 0xff) shl 8)
        if (length < PAYLOAD_LENGTH) return null
        val payload = ByteBuffer.wrap(frame.copyOfRange(6, 6 + length)).order(ByteOrder.LITTLE_ENDIAN)
        return UbloxNavDopTelemetry(
            updatedAtMillis = nowMillis,
            gdop = payload.centiDop(4),
            pdop = payload.centiDop(6),
            tdop = payload.centiDop(8),
            vdop = payload.centiDop(10),
            hdop = payload.centiDop(12),
            ndop = payload.centiDop(14),
            edop = payload.centiDop(16),
        )
    }

    private fun ByteBuffer.centiDop(offset: Int): Double =
        (getShort(offset).toInt() and 0xffff) / 100.0

    private const val PAYLOAD_LENGTH = 18
}
```

- [ ] **Step 5: Run parser test**

Run:

```bash
sh gradlew :receiver:ublox-m8:test --tests org.rtkcollector.receiver.ublox.UbloxNavDopParserTest
```

Expected: all NAV-DOP parser tests pass.

## Task 4: u-blox Frequency And Built-In Profile Updates

**Files:**
- Modify: `receiver/ublox-m8/src/main/kotlin/org/rtkcollector/receiver/ublox/UbloxMessageFrequencyTracker.kt`
- Modify: `receiver/ublox-m8/src/test/kotlin/org/rtkcollector/receiver/ublox/UbloxMessageFrequencyTrackerTest.kt`
- Modify: `receiver/ublox-m8/src/main/kotlin/org/rtkcollector/receiver/ublox/UbloxM8tProfiles.kt`
- Modify: `app/src/test/kotlin/org/rtkcollector/app/profile/ProfileDefaultsTest.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingServiceState.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardModels.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt`

- [ ] **Step 1: Update expected frequency strings in tests first**

In `UbloxMessageFrequencyTrackerTest`, change expected headers from:

```text
Frequency RAWX/SFRBX/TM2/NAV-PVT/NAV-SAT/GGA
```

to:

```text
Frequency RAWX/SFRBX/TM2/NAV-PVT/NAV-SAT/NAV-DOP/GGA
```

For example, the first expected value becomes:

```kotlin
"Frequency RAWX/SFRBX/TM2/NAV-PVT/NAV-SAT/NAV-DOP/GGA 2/-/-/-/-/-/- Hz"
```

Also update app-side default frequency expectations in:

- `DashboardServiceMapperTest`
- `RecordingServiceStateTest`

from six fields to seven fields:

```text
Frequency RAWX/SFRBX/TM2/NAV-PVT/NAV-SAT/NAV-DOP/GGA -/-/-/-/-/-/- Hz
```

- [ ] **Step 2: Run frequency tests and verify failure**

Run:

```bash
sh gradlew :receiver:ublox-m8:test --tests org.rtkcollector.receiver.ublox.UbloxMessageFrequencyTrackerTest
```

Expected before implementation: assertions fail because `NAV-DOP` is not in `UbloxMessageKind`.

- [ ] **Step 3: Add NAV-DOP to the frequency enum**

In `UbloxMessageFrequencyTracker.kt`, update the enum:

```kotlin
enum class UbloxMessageKind(val label: String) {
    RAWX("RAWX"),
    SFRBX("SFRBX"),
    TM2("TM2"),
    NAV_PVT("NAV-PVT"),
    NAV_SAT("NAV-SAT"),
    NAV_DOP("NAV-DOP"),
    GGA("GGA"),
}
```

- [ ] **Step 4: Update default u-blox frequency strings**

Update the default string in:

- `RecordingServiceState.ubloxFrequency`
- `RecordingForegroundService.DEFAULT_UBLOX_FREQUENCY_DISPLAY`
- both inline `ubloxFrequency = "Frequency RAWX/SFRBX/TM2/NAV-PVT/NAV-SAT/GGA -/-/-/-/-/- Hz"` reset strings in `RecordingForegroundService.stopRecording(...)`
- `DashboardModels.DefaultUbloxReceiverFrequency`

to:

```text
Frequency RAWX/SFRBX/TM2/NAV-PVT/NAV-SAT/NAV-DOP/GGA -/-/-/-/-/-/- Hz
```

- [ ] **Step 5: Add failing built-in profile tests**

In `ProfileDefaultsTest`, add:

```kotlin
@Test
fun `m8t live monitoring profiles enable nav dop for dop display`() {
    val navDopCommand = "!UBX CFG-MSG 1 4 0 0 0 1 0 0"

    assertTrue(ProfileStores.UBLOX_M8T_RAW_5HZ_RTKLIB_EX_SCRIPT.contains(navDopCommand))
    assertTrue(ProfileStores.UBLOX_M8T_RAW_STATUS_MOCK_SCRIPT.contains(navDopCommand))
}

@Test
fun `m8t safe raw profile does not enable nav dop in this pass`() {
    assertTrue(!ProfileStores.UBLOX_M8T_RAW_1HZ_SCRIPT.contains("!UBX CFG-MSG 1 4 0 0 0 1 0 0"))
}
```

- [ ] **Step 6: Run profile defaults test and verify failure**

Run:

```bash
sh gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.profile.ProfileDefaultsTest
```

Expected before implementation: the live monitoring profile test fails because the NAV-DOP command is missing.

- [ ] **Step 7: Add NAV-DOP command to built-in profiles**

In `UbloxM8tProfiles.raw5HzRtklibEx`, add after the NAV-SAT command:

```text
!UBX CFG-MSG 1 4 0 0 0 1 0 0
```

In `UbloxM8tProfiles.rawStatusMock`, add after the NAV-SAT command:

```text
!UBX CFG-MSG 1 4 0 0 0 1 0 0
```

Do not add this line to `raw1HzSafe`.

- [ ] **Step 8: Run profile and frequency tests**

Run:

```bash
sh gradlew :receiver:ublox-m8:test --tests org.rtkcollector.receiver.ublox.UbloxMessageFrequencyTrackerTest
sh gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.profile.ProfileDefaultsTest
```

Expected: both focused tests pass.

- [ ] **Step 9: Commit checkpoint**

Run:

```bash
git add receiver/ublox-m8/src/main/kotlin/org/rtkcollector/receiver/ublox/UbloxMessageFrequencyTracker.kt \
  receiver/ublox-m8/src/test/kotlin/org/rtkcollector/receiver/ublox/UbloxMessageFrequencyTrackerTest.kt \
  receiver/ublox-m8/src/main/kotlin/org/rtkcollector/receiver/ublox/UbloxM8tProfiles.kt \
  app/src/test/kotlin/org/rtkcollector/app/profile/ProfileDefaultsTest.kt \
  app/src/main/kotlin/org/rtkcollector/app/recording/RecordingServiceState.kt \
  app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt \
  app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardModels.kt \
  app/src/test/kotlin/org/rtkcollector/app/ui/dashboard/DashboardServiceMapperTest.kt \
  app/src/test/kotlin/org/rtkcollector/app/recording/RecordingServiceStateTest.kt
git commit -m "feat: enable ublox nav dop monitoring profile output"
```

## Task 5: Service u-blox NAV-DOP And UTC Wiring

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/recording/BestSolutionTickLogic.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingServiceState.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt`
- Modify: `app/src/test/kotlin/org/rtkcollector/app/recording/RecordingServiceStateTest.kt`

- [ ] **Step 1: Add failing UTC delta test**

In `RecordingServiceStateTest`, add:

```kotlin
@Test
fun `best solution delta propagates utc time`() {
    val state = RecordingServiceState()

    val updated = state.applyBestSolutionDisplayDelta(
        delta = BestSolutionStateDelta(
            bestSolutionSource = "UBX-NAV-PVT",
            bestSolutionFix = "DGPS",
            bestSolutionAgeMs = 10,
            latDeg = 50.0874512,
            lonDeg = 14.4212534,
            ellipsoidalHeightM = 287.423,
            mslAltitudeM = 243.812,
            horizontalAccuracyM = 0.8,
            verticalAccuracyM = 1.2,
            satellitesUsed = 14,
            satellitesInView = 18,
            utcTime = "2026-06-19T21:00:48Z",
            mockResult = MockLocationPublishResult.PUBLISHED,
        ),
        ubloxFrequency = "Frequency RAWX/SFRBX/TM2/NAV-PVT/NAV-SAT/NAV-DOP/GGA -/-/-/-/-/-/- Hz",
        formatLatLon = { lat, lon -> "$lat, $lon" },
        formatMeters = { "$it m" },
        formatSatellites = { used, view -> "$used/$view" },
    )

    assertEquals("2026-06-19T21:00:48Z", updated.utcTime)
}
```

- [ ] **Step 2: Run state test and verify failure**

Run:

```bash
sh gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.recording.RecordingServiceStateTest
```

Expected before implementation: the test fails because `utcTime` remains `n/a`.

- [ ] **Step 3: Add UTC to selected solution display delta**

In `BestSolutionTickLogic.kt`, update `BestSolutionStateDelta`:

```kotlin
data class BestSolutionStateDelta(
    val bestSolutionSource: String,
    val bestSolutionFix: String,
    val bestSolutionAgeMs: Long?,
    val latDeg: Double?,
    val lonDeg: Double?,
    val ellipsoidalHeightM: Double?,
    val mslAltitudeM: Double?,
    val horizontalAccuracyM: Double?,
    val verticalAccuracyM: Double?,
    val satellitesUsed: Int?,
    val satellitesInView: Int?,
    val utcTime: String?,
    val mockResult: MockLocationPublishResult,
)
```

In `stateDeltaForCandidate(...)`, add immediately before `mockResult = mockResult`:

```kotlin
utcTime = candidate.utcTime,
```

In `stateDeltaForSnapshot(...)`, add immediately before `mockResult = mockResult`:

```kotlin
utcTime = snapshot?.utcTime,
```

- [ ] **Step 4: Propagate UTC in selected solution display state**

In `RecordingServiceState.applyBestSolutionDisplayDelta(...)`, add to the final `copy(...)`:

```kotlin
utcTime = delta.utcTime ?: utcTime,
```

Do not substitute Android wall-clock time.

- [ ] **Step 5: Add NAV-DOP branch in u-blox advisory parsing**

In `RecordingForegroundService.parseUbloxAdvisory(...)`, inside the `record.kind == "ubx"` `when` block, add:

```kotlin
record.bytes.getOrNull(2) == 0x01.toByte() && record.bytes.getOrNull(3) == 0x04.toByte() -> {
    ubloxFrequencyTracker.record(UbloxMessageKind.NAV_DOP, nowMillis)
    UbloxNavDopParser.parse(record.bytes, nowMillis)?.let { telemetry ->
        state = state.copy(
            pdop = "%.1f".format(java.util.Locale.US, telemetry.pdop),
            vdop = telemetry.vdop,
            hdopVdop = dopPairDisplay(telemetry.hdop, telemetry.vdop),
        )
    }
}
```

Use the existing `dopPairDisplay(...)` helper. Add imports only if the file does not already import the parser or enum names.

- [ ] **Step 6: Run service state test**

Run:

```bash
sh gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.recording.RecordingServiceStateTest
```

Expected: UTC propagation test passes.

- [ ] **Step 7: Commit checkpoint**

Run:

```bash
git add app/src/main/kotlin/org/rtkcollector/app/recording/BestSolutionTickLogic.kt \
  app/src/main/kotlin/org/rtkcollector/app/recording/RecordingServiceState.kt \
  app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt \
  app/src/test/kotlin/org/rtkcollector/app/recording/RecordingServiceStateTest.kt
git commit -m "feat: map ublox nav dop and utc monitoring"
```

## Task 6: Local Settings JSON Update Check

**Files:**
- Local-only: `samples/rtkcollector-settings-1781935653886-ublox-rtklib-updated-no-passwords.json`

- [ ] **Step 1: Verify local no-passwords source exists**

Run:

```bash
test -f samples/rtkcollector-settings-1781935653886-ublox-rtklib-updated-no-passwords.json
```

Expected: command exits `0`.

- [ ] **Step 2: Update only relevant u-blox command scripts**

Run this local-only updater:

```bash
python3 - <<'PY'
import json
from pathlib import Path

path = Path("samples/rtkcollector-settings-1781935653886-ublox-rtklib-updated-no-passwords.json")
data = json.loads(path.read_text())
before_ntrip = {
    "ntripCasterProfiles": data.get("ntripCasterProfiles"),
    "ntripMountpointProfiles": data.get("ntripMountpointProfiles"),
    "lastActiveNtripMountpointProfileId": data.get("lastActiveNtripMountpointProfileId"),
}
command = "!UBX CFG-MSG 1 4 0 0 0 1 0 0"
targets = {
    "ublox-m8t-raw-5hz-rtklib-ex",
    "ublox-m8t-raw-status-mock",
}
for profile in data["commandProfiles"]:
    profile_id = profile.get("id")
    if profile_id in targets:
        lines = [line.rstrip() for line in profile.get("runtimeScript", "").splitlines()]
        if command not in lines:
            insert_at = lines.index("!UBX CFG-MSG 1 53 0 0 0 1 0 0") + 1
            lines.insert(insert_at, command)
            profile["runtimeScript"] = "\n".join(lines).rstrip() + "\n"
    elif profile_id == "ublox-m8t-raw-1hz-safe":
        assert command not in profile.get("runtimeScript", ""), "safe profile must remain without NAV-DOP"
after_ntrip = {
    "ntripCasterProfiles": data.get("ntripCasterProfiles"),
    "ntripMountpointProfiles": data.get("ntripMountpointProfiles"),
    "lastActiveNtripMountpointProfileId": data.get("lastActiveNtripMountpointProfileId"),
}
assert before_ntrip == after_ntrip, "NTRIP configuration changed unexpectedly"
path.write_text(json.dumps(data, indent=2) + "\n")
PY
```

This is intentionally a local import-file update. Do not stage or commit the sample file.

- [ ] **Step 3: Verify no tracked samples are staged**

Run:

```bash
git status --short
```

Expected: no `samples/` file appears as staged or tracked. Ignored local sample modifications are acceptable only if the user wants the local JSON updated for manual import.

## Task 7: End-To-End Verification

**Files:**
- Review all touched files.

- [ ] **Step 1: Run whitespace check**

Run:

```bash
git diff --check
```

Expected: no whitespace errors.

- [ ] **Step 2: Run focused u-blox tests**

Run sequentially:

```bash
sh gradlew :receiver:ublox-m8:test --tests org.rtkcollector.receiver.ublox.UbloxNavDopParserTest
sh gradlew :receiver:ublox-m8:test --tests org.rtkcollector.receiver.ublox.UbloxMessageFrequencyTrackerTest
```

Expected: both test classes pass.

- [ ] **Step 3: Run focused app tests**

Run sequentially:

```bash
sh gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.profile.ProfileDefaultsTest
sh gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.ui.dashboard.DashboardServiceMapperTest
sh gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.recording.RecordingServiceStateTest
```

Expected: all focused app tests pass.

- [ ] **Step 4: Run app Kotlin compile**

Run:

```bash
sh gradlew :app:compileDebugKotlin
```

Expected: build succeeds.

- [ ] **Step 5: Architecture review checklist**

Run:

```bash
rg -n "NAV_DOP|UbloxNavDop|RTKLIB_LAT_LON|RTKLIB_ELLIPSOIDAL|RTKLIB_ACCURACY|CFG-MSG 1 4|utcTime = delta.utcTime" \
  app receiver docs/superpowers/specs/2026-06-22-rtklib-card-and-ublox-dop-monitoring-design.md
```

Expected:

- NAV-DOP appears in u-blox parser/profile/frequency/service code.
- RTKLIB solution extras appear only in RTKLIB dashboard/service paths.
- `CFG-MSG 1 4` appears in `raw5HzRtklibEx` and `rawStatusMock`, not in `raw1HzSafe`.
- `utcTime = delta.utcTime ?: utcTime` appears in selected-solution display state.

- [ ] **Step 6: Confirm samples are not tracked**

Run:

```bash
git status --short --ignored | sed -n '1,120p'
```

Expected: no tracked `samples/` changes. Ignored `samples/` entries may appear under `!! samples/`; do not stage them.

- [ ] **Step 7: Commit final review fixes when verification caused edits**

When Steps 1-6 expose a defect and the implementation is corrected, commit the correction with:

```bash
git add app receiver docs/superpowers/specs/2026-06-22-rtklib-card-and-ublox-dop-monitoring-design.md
git commit -m "fix: address rtklib and ublox monitoring verification"
```

When Steps 1-6 pass without additional edits, skip this commit step. Do not include `samples/` in this commit.

## Task 8: Manual Field Validation Notes

**Files:**
- No required code files.

- [ ] **Step 1: Install APK from an Android Studio/CI build**

Termux `:app:compileDebugKotlin` is not an APK packaging validation. Build/install a debug APK on a host with working Android SDK/NDK tools.

- [ ] **Step 2: Validate u-blox M8T profile**

On M8T:

1. Select `u-blox M8T raw 5 Hz RTKLIB-EX`.
2. Start a recording.
3. Confirm the Fix card shows UTC from NAV-PVT.
4. Confirm `PDOP` and `HDOP / VDOP` become non-`n/a`.
5. Confirm the u-blox frequency line includes `NAV-DOP` and shows a measured value after enough samples arrive.
6. Confirm `Lat error` and `Lon error` remain `n/a`.

- [ ] **Step 3: Validate RTKLIB card**

With an RTKLIB-enabled workflow:

1. Start a recording with corrections.
2. Wait for RTKLIB to produce a solution.
3. Confirm the RTKLIB card shows fix, lat/lon, ellipsoidal height and H/V accuracy.
4. Confirm the main Position/Fix cards continue to show the selected receiver/best-solution policy, not RTKLIB-only values unless a separate solution policy change is made later.
