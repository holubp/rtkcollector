# UM980 RTK Monitoring And Editor Input Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add V1 UM980 in-device RTK monitoring, keep PPP status tied to explicit PPP logs, update COM1 default profiles, add explicit persistent receiver config writing, and fix profile-editor hardware-keyboard text navigation.

**Architecture:** Extend the existing advisory UM980 mixed-stream parser and recording service state without changing the byte-exact raw capture path. RTK, PPP and future RTKLIB remain separate dashboard concepts: BESTNAV/ADRNAV drive receiver fix and RTK status, PPPNAV drives PPP status, and RTKLIB stays a V2 placeholder. Profile defaults and editor actions remain user-configurable and never persist receiver configuration unless the user explicitly triggers the warned persistent-write action.

**Tech Stack:** Kotlin/JVM receiver modules, Android foreground service, Jetpack Compose UI, JUnit 5 tests, existing Gradle Android project.

---

## File Structure

- Modify `receiver/unicore-n4/src/main/kotlin/org/rtkcollector/receiver/unicore/Um980Telemetry.kt`
  - Add explicit RTK/RTCM diagnostic fields while preserving the existing generic telemetry shape.
- Modify `receiver/unicore-n4/src/main/kotlin/org/rtkcollector/receiver/unicore/Um980BinaryParser.kt`
  - Add `ADRNAVB`, `RTKSTATUSB` and `RTCMSTATUSB` parsing.
- Modify `receiver/unicore-n4/src/main/kotlin/org/rtkcollector/receiver/unicore/Um980LiveParsers.kt`
  - Add ASCII `RTKSTATUSA` and `RTCMSTATUSA` parser support through the existing line-oriented parser style.
- Modify `receiver/unicore-n4/src/test/kotlin/org/rtkcollector/receiver/unicore/Um980BinaryParserTest.kt`
  - Add binary parser unit tests with synthetic CRC-valid frames.
- Modify `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt`
  - Keep advisory parser fanout, add RTK/RTCM state merging, and stop deriving PPP row from BESTNAV.
- Modify `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardModels.kt`
  - Add `rtkStatus` to `FixCardState`.
- Modify `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardServiceMapper.kt`
  - Map explicit service extras to PPP, RTK and RTKLIB rows.
- Modify `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/HomeDashboard.kt`
  - Render `RTK` between `PPP` and `RTKLIB`.
- Modify `app/src/test/kotlin/org/rtkcollector/app/ui/dashboard/DashboardServiceMapperTest.kt`
  - Add regression tests for PPP and RTK separation.
- Modify `app/src/main/kotlin/org/rtkcollector/app/profile/ProfileStores.kt`
  - Update default COM1 UM980 binary and ASCII scripts.
- Modify `app/src/test/kotlin/org/rtkcollector/app/profile/ProfileStoresTest.kt`
  - Add profile-default assertions.
- Modify `app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileScreens.kt`
  - Add persistent-write editor action and fix multiline field key behaviour.
- Modify `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`
  - Wire persistent-write intent/action to current selected command profile and USB/baud selection.
- Create: `app/src/test/kotlin/org/rtkcollector/app/ui/profiles/ProfileEditorModelsTest.kt`
  - Add pure model tests for persistent-write action visibility and warning copy.
- Modify `docs/user-workflows.md`
  - Mention receiver-internal RTK monitoring and explicit persistent writes.

---

### Task 1: Add UM980 RTK/RTCM Binary Parser Coverage

**Files:**
- Modify: `receiver/unicore-n4/src/main/kotlin/org/rtkcollector/receiver/unicore/Um980Telemetry.kt`
- Modify: `receiver/unicore-n4/src/main/kotlin/org/rtkcollector/receiver/unicore/Um980BinaryParser.kt`
- Test: `receiver/unicore-n4/src/test/kotlin/org/rtkcollector/receiver/unicore/Um980BinaryParserTest.kt`

- [ ] **Step 1: Write failing tests for ADRNAVB, RTKSTATUSB and RTCMSTATUSB**

Add these helper functions to the test file. Keep them test-local:

```kotlin
private fun unicoreFrame(messageId: Int, payload: ByteArray): ByteArray {
    val header = ByteArray(24)
    header[0] = 0xAA.toByte()
    header[1] = 0x44.toByte()
    header[2] = 0xB5.toByte()
    header.putU16(4, messageId)
    header.putU16(6, payload.size)
    header.putU16(10, 2300)
    header.putU32(12, 100_000)
    val body = header + payload
    return body + body.unicoreCrc32().toLittleEndianBytes()
}

private fun ByteArray.putU16(offset: Int, value: Int) {
    this[offset] = (value and 0xff).toByte()
    this[offset + 1] = ((value ushr 8) and 0xff).toByte()
}

private fun ByteArray.putU32(offset: Int, value: Int) {
    this[offset] = (value and 0xff).toByte()
    this[offset + 1] = ((value ushr 8) and 0xff).toByte()
    this[offset + 2] = ((value ushr 16) and 0xff).toByte()
    this[offset + 3] = ((value ushr 24) and 0xff).toByte()
}

private fun UInt.toLittleEndianBytes(): ByteArray =
    byteArrayOf(
        (this and 0xffu).toByte(),
        ((this shr 8) and 0xffu).toByte(),
        ((this shr 16) and 0xffu).toByte(),
        ((this shr 24) and 0xffu).toByte(),
    )

private fun ByteArray.unicoreCrc32(): UInt {
    var crc = 0u
    for (byte in this) {
        crc = crc xor (byte.toUInt() and 0xffu)
        repeat(8) {
            crc = if ((crc and 1u) != 0u) {
                (crc shr 1) xor 0xEDB88320u
            } else {
                crc shr 1
            }
        }
    }
    return crc
}
```

Add the tests:

```kotlin
@Test
fun `parses adrnavb common navigation payload as rtk view`() {
    val payload = ByteBuffer.allocate(112).order(ByteOrder.LITTLE_ENDIAN).apply {
        putInt(0, 0)
        putInt(4, 50)
        putDouble(8, 50.087)
        putDouble(16, 14.421)
        putDouble(24, 287.3)
        putFloat(32, 44.0f)
        putFloat(40, 0.012f)
        putFloat(44, 0.013f)
        putFloat(48, 0.025f)
        position(52)
        put("TUBO".encodeToByteArray())
        putFloat(56, 1.2f)
        putFloat(60, 0.3f)
        put(64, 21)
        put(65, 17)
    }.array()

    val telemetry = Um980BinaryParser.parseAdrnavb(unicoreFrame(142, payload))

    requireNotNull(telemetry)
    assertEquals("ADRNAVB", telemetry.source)
    assertEquals("SOL_COMPUTED", telemetry.solutionStatus)
    assertEquals("NARROW_INT", telemetry.positionType)
    assertEquals("TUBO", telemetry.stationId)
    assertEquals(17, telemetry.satellitesUsed)
    assertEquals(21, telemetry.satellitesInView)
}

@Test
fun `parses rtkstatusb calculate status and adr number`() {
    val payload = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN).apply {
        putInt(0, 0b11)
        putInt(8, 0b1)
        putInt(20, 0b10)
        putInt(28, 0b100)
        putInt(36, 0b1000)
        putInt(44, 34)
        putInt(48, 5)
        put(52, 0)
        put(54, 23)
    }.array()

    val telemetry = Um980BinaryParser.parseRtkstatusb(unicoreFrame(509, payload))

    requireNotNull(telemetry)
    assertEquals("RTKSTATUSB", telemetry.source)
    assertEquals("NARROW_FLOAT", telemetry.rtkPositionType)
    assertEquals(5, telemetry.rtkCalculateStatus)
    assertEquals(23, telemetry.adrNumber)
}

@Test
fun `parses rtcmstatusb decoded message status`() {
    val payload = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN).apply {
        putInt(0, 1077)
        putInt(4, 42)
        putInt(8, 9901)
        putInt(12, 14)
        put(16, 14)
        put(17, 14)
        put(18, 0)
        put(19, 0)
        put(20, 0)
        put(21, 0)
    }.array()

    val telemetry = Um980BinaryParser.parseRtcmstatusb(unicoreFrame(2125, payload))

    requireNotNull(telemetry)
    assertEquals("RTCMSTATUSB", telemetry.source)
    assertEquals(1077, telemetry.rtcmMessageId)
    assertEquals(9901, telemetry.rtcmBaseId)
    assertEquals(14, telemetry.rtcmSatelliteCount)
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run:

```bash
sh gradlew :receiver:unicore-n4:test --tests org.rtkcollector.receiver.unicore.Um980BinaryParserTest
```

Expected: compilation fails because `parseAdrnavb`, `parseRtkstatusb`, `parseRtcmstatusb` and the new telemetry fields are missing.

- [ ] **Step 3: Extend `Um980Telemetry`**

Add fields to the data class:

```kotlin
val rtkPositionType: String? = null,
val rtkCalculateStatus: Int? = null,
val rtkCalculateStatusDescription: String? = null,
val ionDetected: Boolean? = null,
val adrNumber: Int? = null,
val rtcmMessageId: Int? = null,
val rtcmMessageCount: Int? = null,
val rtcmBaseId: Int? = null,
val rtcmSatelliteCount: Int? = null,
val rtcmObservableCounts: List<Int> = emptyList(),
```

- [ ] **Step 4: Implement binary parsers**

In `Um980BinaryParser`, add message IDs:

```kotlin
private const val ADRNAVB_MESSAGE_ID = 142
private const val RTKSTATUSB_MESSAGE_ID = 509
private const val RTCMSTATUSB_MESSAGE_ID = 2125
private const val RTKSTATUSB_MIN_PAYLOAD_LENGTH = 56
private const val RTCMSTATUSB_MIN_PAYLOAD_LENGTH = 22
```

Add public parse functions:

```kotlin
fun parseAdrnavb(frame: ByteArray): Um980Telemetry? {
    if (!isValidFrame(frame)) return null
    if (messageId(frame) != ADRNAVB_MESSAGE_ID) return null
    return parseNavPayload(frame, source = "ADRNAVB")
}

fun parseRtkstatusb(frame: ByteArray): Um980Telemetry? {
    if (!isValidFrame(frame)) return null
    if (messageId(frame) != RTKSTATUSB_MESSAGE_ID) return null
    val payloadLength = u16(frame, 6)
    if (payloadLength < RTKSTATUSB_MIN_PAYLOAD_LENGTH) return null
    val payload = ByteBuffer
        .wrap(frame.copyOfRange(BINARY_HEADER_LENGTH, BINARY_HEADER_LENGTH + payloadLength))
        .order(ByteOrder.LITTLE_ENDIAN)
    val positionType = payload.getInt(44)
    val calculateStatus = payload.getInt(48)
    return Um980Telemetry(
        source = "RTKSTATUSB",
        utcTime = gpsWeekTowUtc(frame),
        rtkPositionType = positionTypeNames[positionType] ?: "TYPE_$positionType",
        rtkCalculateStatus = calculateStatus,
        rtkCalculateStatusDescription = rtkCalculateStatusNames[calculateStatus] ?: "STATUS_$calculateStatus",
        ionDetected = payload.get(52).toInt() != 0,
        adrNumber = payload.get(54).toInt() and 0xff,
    )
}

fun parseRtcmstatusb(frame: ByteArray): Um980Telemetry? {
    if (!isValidFrame(frame)) return null
    if (messageId(frame) != RTCMSTATUSB_MESSAGE_ID) return null
    val payloadLength = u16(frame, 6)
    if (payloadLength < RTCMSTATUSB_MIN_PAYLOAD_LENGTH) return null
    val payload = ByteBuffer
        .wrap(frame.copyOfRange(BINARY_HEADER_LENGTH, BINARY_HEADER_LENGTH + payloadLength))
        .order(ByteOrder.LITTLE_ENDIAN)
    return Um980Telemetry(
        source = "RTCMSTATUSB",
        utcTime = gpsWeekTowUtc(frame),
        rtcmMessageId = payload.getInt(0),
        rtcmMessageCount = payload.getInt(4),
        rtcmBaseId = payload.getInt(8),
        rtcmSatelliteCount = payload.getInt(12),
        rtcmObservableCounts = (16..21).map { offset -> payload.get(offset).toInt() and 0xff },
    )
}
```

Add status names:

```kotlin
private val rtkCalculateStatusNames = mapOf(
    0 to "NO_DIFFERENTIAL_DATA",
    1 to "INSUFFICIENT_BASE_OBSERVATION",
    2 to "HIGH_CORRECTION_LATENCY",
    3 to "ACTIVE_IONOSPHERE",
    4 to "INSUFFICIENT_ROVER_OBSERVATION",
    5 to "RTK_SOLUTION_AVAILABLE",
)
```

- [ ] **Step 5: Run parser tests**

Run:

```bash
sh gradlew :receiver:unicore-n4:test --tests org.rtkcollector.receiver.unicore.Um980BinaryParserTest
```

Expected: PASS.

- [ ] **Step 6: Commit parser changes**

```bash
git add receiver/unicore-n4/src/main/kotlin/org/rtkcollector/receiver/unicore/Um980Telemetry.kt \
  receiver/unicore-n4/src/main/kotlin/org/rtkcollector/receiver/unicore/Um980BinaryParser.kt \
  receiver/unicore-n4/src/test/kotlin/org/rtkcollector/receiver/unicore/Um980BinaryParserTest.kt
git commit -m "Parse UM980 RTK and RTCM status logs"
```

---

### Task 2: Merge RTK/RTCM Advisory State In Recording Service

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt`
- Test: use parser tests from Task 1 and dashboard mapper tests from Task 3

- [ ] **Step 1: Add service state fields**

In the recording service state data class, add:

```kotlin
val receiverRtkStatus: String = "n/a",
val rtkPositionType: String? = null,
val rtkCalculateStatus: Int? = null,
val rtkCalculateStatusDescription: String? = null,
val rtcmDecodedAtMillis: Long? = null,
val rtcmLastMessageId: Int? = null,
val rtcmLastBaseId: Int? = null,
```

Add broadcast extra constants near the other `EXTRA_STATE_*` constants:

```kotlin
const val EXTRA_STATE_RECEIVER_RTK_STATUS = "receiver_rtk_status"
const val EXTRA_STATE_RTK_POSITION_TYPE = "rtk_position_type"
const val EXTRA_STATE_RTK_CALCULATE_STATUS = "rtk_calculate_status"
const val EXTRA_STATE_RTK_CALCULATE_STATUS_DESCRIPTION = "rtk_calculate_status_description"
const val EXTRA_STATE_RTCM_LAST_MESSAGE_ID = "rtcm_last_message_id"
const val EXTRA_STATE_RTCM_LAST_BASE_ID = "rtcm_last_base_id"
```

Ensure `broadcastState()` writes these extras.

- [ ] **Step 2: Route new binary parser outputs**

Inside the `"unicore_binary"` record handling block, add calls after BESTNAVB/PPPNAVB parsing:

```kotlin
Um980BinaryParser.parseAdrnavb(record.bytes)?.let { telemetry ->
    if (exportJsonSolution) {
        sessionWriters.appendReceiverSolutionJson(telemetry.toJson())
    }
    state = state.withUm980Telemetry(telemetry)
        .withReceiverRtkTelemetry(telemetry)
}
Um980BinaryParser.parseRtkstatusb(record.bytes)?.let { telemetry ->
    if (exportJsonSolution) {
        sessionWriters.appendQualityLiveJson(telemetry.toJson())
    }
    state = state.withReceiverRtkTelemetry(telemetry)
}
Um980BinaryParser.parseRtcmstatusb(record.bytes)?.let { telemetry ->
    if (exportJsonSolution) {
        sessionWriters.appendQualityLiveJson(telemetry.toJson())
    }
    state = state.withRtcmDecodedTelemetry(telemetry)
}
```

- [ ] **Step 3: Stop deriving PPP row from generic telemetry**

In `withUm980Telemetry`, remove:

```kotlin
pppStatus = telemetry.positionType?.takeIf { it.startsWith("PPP", ignoreCase = true) } ?: pppStatus,
```

Keep PPP status updates only in the explicit `parsePppnavb` block:

```kotlin
state = state.copy(
    pppStatus = telemetry.positionType ?: state.pppStatus,
)
```

For ASCII `PPPNAVA`, keep or add equivalent logic only when `solution.logName == "PPPNAVA"`.

- [ ] **Step 4: Add RTK state helpers**

Add helper methods near `withUm980Telemetry`:

```kotlin
private fun RecordingServiceState.withReceiverRtkTelemetry(telemetry: Um980Telemetry): RecordingServiceState {
    val positionType = telemetry.rtkPositionType ?: telemetry.positionType
    val calculateStatus = telemetry.rtkCalculateStatus ?: rtkCalculateStatus
    val diffAge = telemetry.differentialAgeS
    val status = classifyReceiverRtkStatus(
        positionType = positionType,
        solutionStatus = telemetry.solutionStatus,
        calculateStatus = calculateStatus,
        differentialAgeS = diffAge,
        recentRtcmDecoded = rtcmDecodedAtMillis?.let { android.os.SystemClock.elapsedRealtime() - it < 10_000 } == true,
    )
    return copy(
        receiverRtkStatus = status,
        rtkPositionType = positionType ?: rtkPositionType,
        rtkCalculateStatus = calculateStatus,
        rtkCalculateStatusDescription = telemetry.rtkCalculateStatusDescription ?: rtkCalculateStatusDescription,
    )
}

private fun RecordingServiceState.withRtcmDecodedTelemetry(telemetry: Um980Telemetry): RecordingServiceState =
    copy(
        rtcmDecodedAtMillis = android.os.SystemClock.elapsedRealtime(),
        rtcmLastMessageId = telemetry.rtcmMessageId ?: rtcmLastMessageId,
        rtcmLastBaseId = telemetry.rtcmBaseId ?: rtcmLastBaseId,
        receiverRtkStatus = if (receiverRtkStatus == "n/a" || receiverRtkStatus == "No RTCM") {
            "RTCM decoded"
        } else {
            receiverRtkStatus
        },
    )

private fun classifyReceiverRtkStatus(
    positionType: String?,
    solutionStatus: String?,
    calculateStatus: Int?,
    differentialAgeS: Double?,
    recentRtcmDecoded: Boolean,
): String {
    if (differentialAgeS != null && differentialAgeS > 5.0) return "RTK stale"
    if (solutionStatus.equals("SOL_COMPUTED", ignoreCase = true)) {
        when (positionType?.uppercase()) {
            "NARROW_INT", "INS_RTKFIXED" -> return "RTK fixed"
            "NARROW_FLOAT", "IONOFREE_FLOAT", "L1_FLOAT", "INS_RTKFLOAT" -> return "RTK float"
        }
    }
    return when (calculateStatus) {
        0 -> "No RTCM"
        1 -> "Base obs insufficient"
        2 -> "RTK stale"
        4 -> "Rover obs insufficient"
        5 -> if (recentRtcmDecoded) "RTCM decoded" else "n/a"
        else -> if (recentRtcmDecoded) "RTCM decoded" else "n/a"
    }
}
```

- [ ] **Step 5: Include new telemetry fields in JSON**

Extend `Um980Telemetry.toJson()` with:

```kotlin
,"rtkPositionType":${rtkPositionType.jsonStringOrNull()},"rtkCalculateStatus":${rtkCalculateStatus ?: "null"},"rtkCalculateStatusDescription":${rtkCalculateStatusDescription.jsonStringOrNull()},"ionDetected":${ionDetected ?: "null"},"adrNumber":${adrNumber ?: "null"},"rtcmMessageId":${rtcmMessageId ?: "null"},"rtcmMessageCount":${rtcmMessageCount ?: "null"},"rtcmBaseId":${rtcmBaseId ?: "null"},"rtcmSatelliteCount":${rtcmSatelliteCount ?: "null"},"rtcmObservableCounts":[${rtcmObservableCounts.joinToString(",")}]
```

- [ ] **Step 6: Compile service**

Run:

```bash
sh gradlew :app:compileDebugKotlin
```

Expected: PASS.

- [ ] **Step 7: Commit service state changes**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt
git commit -m "Track receiver RTK and decoded RTCM status"
```

---

### Task 3: Display Separate PPP, RTK And RTKLIB Rows

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardModels.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardServiceMapper.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/HomeDashboard.kt`
- Test: `app/src/test/kotlin/org/rtkcollector/app/ui/dashboard/DashboardServiceMapperTest.kt`

- [ ] **Step 1: Write failing dashboard mapper tests**

Add tests:

```kotlin
@Test
fun `explicit service rtk status is shown separately from ppp and rtklib`() {
    val intent = Intent(RecordingForegroundService.ACTION_STATE).apply {
        putExtra(RecordingForegroundService.EXTRA_STATE_RUNNING, true)
        putExtra(RecordingForegroundService.EXTRA_STATE_BESTNAV_POSITION_TYPE, "NARROW_FLOAT")
        putExtra(RecordingForegroundService.EXTRA_STATE_PPP_STATUS, "PPP_CONVERGING")
        putExtra(RecordingForegroundService.EXTRA_STATE_RECEIVER_RTK_STATUS, "RTK float")
    }

    val state = dashboardStateFromRecordingIntent(intent)

    assertEquals("RTK float", state.fix.fixType)
    assertEquals("PPP_CONVERGING", state.fix.pppStatus)
    assertEquals("RTK float", state.fix.rtkStatus)
    assertEquals("Not configured", state.fix.rtklibStatus)
}

@Test
fun `bestnav ppp converging does not populate explicit ppp row`() {
    val intent = Intent(RecordingForegroundService.ACTION_STATE).apply {
        putExtra(RecordingForegroundService.EXTRA_STATE_RUNNING, true)
        putExtra(RecordingForegroundService.EXTRA_STATE_BESTNAV_POSITION_TYPE, "PPP_CONVERGING")
    }

    val state = dashboardStateFromRecordingIntent(intent)

    assertEquals("PPP converging", state.fix.fixType)
    assertEquals("n/a", state.fix.pppStatus)
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
sh gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.ui.dashboard.DashboardServiceMapperTest
```

Expected on normal host: FAIL until model/mapper is updated. On Termux, this may be blocked before test execution by the known `aapt2` native binary issue; if blocked, run `sh gradlew :app:compileDebugKotlin` after implementation.

- [ ] **Step 3: Add `rtkStatus` to model**

In `FixCardState`, add:

```kotlin
val rtkStatus: String = "n/a",
```

- [ ] **Step 4: Update mapper**

In `dashboardStateFromRecordingIntent`, map:

```kotlin
rtkStatus = intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_RECEIVER_RTK_STATUS) ?: "n/a",
```

Change `displayPppStatus` so it does not fall back to BESTNAV:

```kotlin
private fun displayPppStatus(pppStatus: String?): String =
    pppStatus?.takeIf(::isMeaningfulSolutionStatus) ?: "n/a"
```

Update the call site:

```kotlin
pppStatus = displayPppStatus(pppStatus),
```

- [ ] **Step 5: Update dashboard UI**

In `FixCard`, change:

```kotlin
Metric("PPP", state.fix.pppStatus)
Metric("RTKLIB", state.fix.rtklibStatus)
```

to:

```kotlin
Metric("PPP", state.fix.pppStatus)
Metric("RTK", state.fix.rtkStatus)
Metric("RTKLIB", state.fix.rtklibStatus)
```

Update preview/sample `FixCardState` values to include `rtkStatus = "RTK float"` for recording and `rtkStatus = "n/a"` for idle.

- [ ] **Step 6: Run dashboard validation**

Run:

```bash
sh gradlew :app:compileDebugKotlin
sh gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.ui.dashboard.DashboardServiceMapperTest
```

Expected: compile PASS. Unit tests PASS on a host with runnable Android resource tooling; if Termux blocks at `aapt2`, record that exact blocker.

- [ ] **Step 7: Commit dashboard changes**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardModels.kt \
  app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardServiceMapper.kt \
  app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/HomeDashboard.kt \
  app/src/test/kotlin/org/rtkcollector/app/ui/dashboard/DashboardServiceMapperTest.kt
git commit -m "Show receiver RTK status separately"
```

---

### Task 4: Update Default UM980 Profiles

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/profile/ProfileStores.kt`
- Modify or create: `app/src/test/kotlin/org/rtkcollector/app/profile/ProfileStoresTest.kt`

- [ ] **Step 1: Write failing profile tests**

Create or extend `ProfileStoresTest` with pure string assertions:

```kotlin
@Test
fun `binary um980 profile includes rtk monitoring logs and no saveconfig`() {
    val script = ProfileStores.UM980_BINARY_MULTI_HZ_SCRIPT

    assertTrue(script.contains("MODE ROVER SURVEY"))
    assertTrue(script.contains("CONFIG RTK TIMEOUT 120"))
    assertTrue(script.contains("CONFIG RTK RELIABILITY 3 1"))
    assertTrue(script.contains("ADRNAVB COM1 1"))
    assertTrue(script.contains("RTKSTATUSB COM1 1"))
    assertTrue(script.contains("RTCMSTATUSB COM1 ONCHANGED"))
    assertFalse(script.contains("SAVECONFIG", ignoreCase = true))
    assertFalse(script.contains("NCOM20", ignoreCase = true))
}

@Test
fun `ascii um980 profile keeps explicit ppp and adr diagnostics`() {
    val script = ProfileStores.UM980_ASCII_PPP_NMEA_SCRIPT

    assertTrue(script.contains("PPPNAVA 10"))
    assertTrue(script.contains("ADRNAVA 10"))
    assertFalse(script.contains("SAVECONFIG", ignoreCase = true))
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
sh gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.profile.ProfileStoresTest
```

Expected on normal host: FAIL until profile script is updated. On Termux, `aapt2` may block before execution.

- [ ] **Step 3: Update binary profile script**

Replace the script body in `UM980_BINARY_MULTI_HZ_SCRIPT` with:

```kotlin
val UM980_BINARY_MULTI_HZ_SCRIPT: String = """
    UNLOG COM1
    MODE ROVER SURVEY
    CONFIG MMP ENABLE
    CONFIG RTK TIMEOUT 120
    CONFIG RTK RELIABILITY 3 1
    CONFIG PPP ENABLE E6-HAS
    CONFIG PPP DATUM WGS84
    CONFIG PPP TIMEOUT 120
    CONFIG PPP CONVERGE 15 30
    VERSIONB
    BESTNAVB COM1 0.05
    ADRNAVB COM1 1
    PPPNAVB COM1 1
    RTKSTATUSB COM1 1
    RTCMSTATUSB COM1 ONCHANGED
    OBSVMCMPB COM1 0.25
    STADOPB COM1 1
    GPSEPHB COM1 300
    GLOEPHB COM1 300
    GALEPHB COM1 300
    BDSEPHB COM1 300
    BD3EPHB COM1 300
    QZSSEPHB COM1 300
    GPSIONB ONCHANGED
    BDSIONB ONCHANGED
    BD3IONB ONCHANGED
    GALIONB ONCHANGED
    GPSUTCB ONCHANGED
    BDSUTCB ONCHANGED
    BD3UTCB ONCHANGED
    GALUTCB ONCHANGED
""".trimIndent()
```

- [ ] **Step 4: Run validation**

Run:

```bash
sh gradlew :app:compileDebugKotlin
sh gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.profile.ProfileStoresTest
```

Expected: compile PASS. Unit test PASS on normal host or blocked by Termux `aapt2` before execution.

- [ ] **Step 5: Commit profile changes**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/profile/ProfileStores.kt \
  app/src/test/kotlin/org/rtkcollector/app/profile/ProfileStoresTest.kt
git commit -m "Enable UM980 RTK monitoring in default profile"
```

---

### Task 5: Add Explicit Persistent Init Config Write Action

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileEditorModels.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileScreens.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`
- Test: `app/src/test/kotlin/org/rtkcollector/app/ui/profiles/ProfileEditorModelsTest.kt`

- [ ] **Step 1: Add pure model test for action labels**

Add:

```kotlin
@Test
fun `command profile editor exposes persistent write action with warning text`() {
    val action = ProfileEditorAction.PersistentReceiverWrite

    assertEquals("Write init config persistently to device", action.label)
    assertTrue(action.warningTitle.contains("receiver", ignoreCase = true))
    assertTrue(action.warningBody.contains("non-volatile", ignoreCase = true))
    assertTrue(action.warningBody.contains("other apps", ignoreCase = true))
}
```

- [ ] **Step 2: Implement model action**

In `ProfileEditorModels.kt`, add:

```kotlin
sealed class ProfileEditorAction {
    data object Save : ProfileEditorAction()
    data object Discard : ProfileEditorAction()
    data object Delete : ProfileEditorAction()
    data object PersistentReceiverWrite : ProfileEditorAction() {
        val label: String = "Write init config persistently to device"
        val warningTitle: String = "Write receiver configuration?"
        val warningBody: String =
            "This sends the current init script and SAVECONFIG to the receiver. " +
                "It writes receiver non-volatile memory and can affect other apps, tools and future receiver sessions until manually changed again."
    }
}
```

If `ProfileEditorAction` is already an enum or data class, adapt this exact label/body into the existing representation without changing other action names.

- [ ] **Step 3: Add UI button only for command profiles**

In `ProfileEditorScreen`, when editing a command-script profile, render:

```kotlin
TonalActionButton(
    text = "Write init config persistently to device",
    onClick = { pendingPersistentWriteWarning = true },
)
```

Use the existing app button style instead of adding a new visual style.

- [ ] **Step 4: Add modal warning**

Use existing Material alert/dialog style:

```kotlin
if (pendingPersistentWriteWarning) {
    AlertDialog(
        onDismissRequest = { pendingPersistentWriteWarning = false },
        title = { Text("Write receiver configuration?") },
        text = {
            Text(
                "This sends the current init script and SAVECONFIG to the receiver. " +
                    "It writes receiver non-volatile memory and can affect other apps, tools and future receiver sessions until manually changed again.",
            )
        },
        confirmButton = {
            TextButton(onClick = {
                pendingPersistentWriteWarning = false
                onAction(ProfileEditorAction.PersistentReceiverWrite)
            }) {
                Text("Write persistently")
            }
        },
        dismissButton = {
            TextButton(onClick = { pendingPersistentWriteWarning = false }) {
                Text("Cancel")
            }
        },
    )
}
```

- [ ] **Step 5: Wire MainActivity action**

In the command-profile editor action handler, handle `PersistentReceiverWrite` by:

1. Resolving the current command profile init script text.
2. Resolving selected USB device and baud profile.
3. Refusing with a visible message if no USB device is selected or permission is missing.
4. Sending init script lines, then `SAVECONFIG`.
5. Recording this as an event if a recording is active, otherwise showing a UI result message.

The command list must be:

```kotlin
val persistentCommands = profile.runtimeScript
    .lineSequence()
    .map(String::trim)
    .filter { it.isNotBlank() && !it.startsWith("#") }
    .toList() + "SAVECONFIG"
```

- [ ] **Step 6: Compile**

Run:

```bash
sh gradlew :app:compileDebugKotlin
```

Expected: PASS.

- [ ] **Step 7: Commit persistent write UI**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileEditorModels.kt \
  app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileScreens.kt \
  app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt \
  app/src/test/kotlin/org/rtkcollector/app/ui/profiles/ProfileEditorModelsTest.kt
git commit -m "Add warned persistent UM980 config write action"
```

---

### Task 6: Fix Profile Editor Hardware Keyboard Text Navigation

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileScreens.kt`
- Test: compile plus manual hardware-keyboard check

- [ ] **Step 1: Locate multiline script fields**

Find existing script fields:

```bash
rg -n "runtimeScript|shutdownScript|OutlinedTextField|onPreviewKeyEvent|focusProperties" app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileScreens.kt
```

Expected: command-script fields are rendered through `OutlinedTextField`.

- [ ] **Step 2: Add a reusable script text field wrapper**

Create a local composable in `ProfileScreens.kt`:

```kotlin
@Composable
private fun ScriptTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 160.dp)
            .onPreviewKeyEvent { event ->
                val key = event.key
                if (
                    key == Key.DirectionLeft ||
                    key == Key.DirectionRight ||
                    key == Key.DirectionUp ||
                    key == Key.DirectionDown
                ) {
                    false
                } else {
                    false
                }
            },
        label = { Text(label) },
        minLines = 8,
        keyboardOptions = KeyboardOptions.Default.copy(autoCorrectEnabled = false),
    )
}
```

If the file already has a text-field wrapper, modify that wrapper instead of adding another one. The important rule is that arrow keys are not intercepted for focus movement while the field is focused. Keep Tab/Shift+Tab focus traversal unchanged.

- [ ] **Step 3: Replace command script fields**

Replace multiline `OutlinedTextField` calls for init and shutdown scripts with:

```kotlin
ScriptTextField(
    label = "Init script",
    value = runtimeScript,
    onValueChange = { runtimeScript = it },
)

ScriptTextField(
    label = "Shutdown script",
    value = shutdownScript,
    onValueChange = { shutdownScript = it },
)
```

Ensure the empty shutdown script field has an empty `value = ""`, not placeholder text.

- [ ] **Step 4: Compile**

Run:

```bash
sh gradlew :app:compileDebugKotlin
```

Expected: PASS.

- [ ] **Step 5: Manual check on device**

Install the debug APK from a host build and verify:

- Arrow left/right move within the command script field.
- Arrow up/down move within multiline content.
- `Tab` moves to the next field.
- `Shift+Tab` moves to the previous field.
- `Ctrl+A/C/V/X` keep normal text editing behaviour.

- [ ] **Step 6: Commit keyboard fix**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileScreens.kt
git commit -m "Keep arrow keys inside profile script editors"
```

---

### Task 7: Update User Documentation

**Files:**
- Modify: `docs/user-workflows.md`
- Modify: `docs/receiver-profiles.md`

- [ ] **Step 1: Update workflow docs**

In `docs/user-workflows.md`, add a short subsection under rover + NTRIP:

```markdown
### UM980 In-Device RTK Monitoring

For UM980/N4 V1 rover use, RtkCollector runs the NTRIP client externally and
feeds raw RTCM3 bytes to the receiver over COM1. The receiver computes its own
RTK solution. The dashboard separates:

- main receiver fix from BESTNAV/GGA;
- in-device PPP status from PPPNAV;
- in-device RTK status from ADRNAV/RTKSTATUS/RTCMSTATUS;
- future RTKLIB status, which remains disabled in V1.

The normal recording start profile does not persist receiver settings. Persistent
receiver writes require the explicit warned action in the command-profile editor.
```

- [ ] **Step 2: Update receiver profile docs**

In `docs/receiver-profiles.md`, add to UM980/N4:

```markdown
The V1 COM1 binary monitoring profile should include BESTNAVB, ADRNAVB,
PPPNAVB, RTKSTATUSB, RTCMSTATUSB ONCHANGED, OBSVMCMPB and STADOPB. Android is
the NTRIP client; UM980 internal NTRIP-client commands are not part of the V1
profile.
```

- [ ] **Step 3: Commit docs**

```bash
git add docs/user-workflows.md docs/receiver-profiles.md
git commit -m "Document UM980 in-device RTK monitoring"
```

---

### Task 8: Final Verification And Push

**Files:**
- No new source files unless required by previous tasks.

- [ ] **Step 1: Inspect worktree**

Run:

```bash
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector status --short
```

Expected: only local untracked artifacts such as `.codex-tmp/`, `.superpowers/`, or `samples/` remain.

- [ ] **Step 2: Run diff check**

Run:

```bash
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector diff --check HEAD
```

Expected: no output.

- [ ] **Step 3: Run feasible Gradle validation**

Run:

```bash
sh gradlew :receiver:unicore-n4:test
sh gradlew :app:compileDebugKotlin
sh gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.ui.dashboard.DashboardServiceMapperTest
```

Expected:

- receiver tests pass;
- app debug Kotlin compile passes;
- dashboard tests pass on a host with runnable Android tooling, or are blocked in Termux by the known x86-64 `aapt2` failure before test execution.

- [ ] **Step 4: Review commit series**

Run:

```bash
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector log --oneline -8
```

Expected: recent commits correspond to parser, service, dashboard, profile, editor, docs and plan work.

- [ ] **Step 5: Push**

Run:

```bash
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector push origin main
```

Expected: push succeeds.

---

## Self-Review

- Spec coverage: The plan covers parser additions, dashboard RTK/PPP/RTKLIB separation, COM1 profile defaults, explicit persistent `SAVECONFIG`, keyboard editing behaviour, docs, validation and push.
- Scope check: The plan does not implement Android-side RTKLIB, UM980 internal NTRIP, caster upload, GIS/maps, or Android PPP/static solving.
- Placeholder scan: No `TBD`, unspecified test step, or open-ended implementation step remains.
- Type consistency: New telemetry fields are introduced in Task 1 before being used by the recording service in Task 2 and dashboard in Task 3.
