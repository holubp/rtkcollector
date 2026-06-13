# u-blox M8T And Mock Provider Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add practical u-blox M8T recording/status support and Android mock-location publishing from the best currently available RtkCollector solution.

**Architecture:** Keep raw capture authoritative. `receiver:ublox-m8` owns UBX framing, `!UBX` compilation, M8T profiles and u-blox byte parsing. A new pure Kotlin `core:solution` module owns receiver-agnostic solution candidates, best-solution selection and mock-provider DTOs; `app` adapts those snapshots to dashboard state and Android mock location while recording.

**Tech Stack:** Kotlin/JVM modules, Android foreground service, Android `LocationManager` mock provider APIs, existing profile/settings infrastructure, JUnit 5 tests.

---

## File Structure

Create:

- `core/solution/build.gradle.kts` - pure Kotlin module for solution arbitration.
- `core/solution/src/main/kotlin/org/rtkcollector/core/solution/SolutionModels.kt` - canonical candidate/snapshot/fix-quality models.
- `core/solution/src/main/kotlin/org/rtkcollector/core/solution/BestSolutionSelector.kt` - ranking and freshness logic.
- `core/solution/src/test/kotlin/org/rtkcollector/core/solution/BestSolutionSelectorTest.kt` - ranking and staleness tests.
- `receiver/ublox-m8/src/main/kotlin/org/rtkcollector/receiver/ublox/UbloxFrame.kt` - UBX frame/checksum builder and verifier.
- `receiver/ublox-m8/src/main/kotlin/org/rtkcollector/receiver/ublox/UbloxScriptCompiler.kt` - `!UBX ...` script compiler.
- `receiver/ublox-m8/src/main/kotlin/org/rtkcollector/receiver/ublox/UbloxM8tProfiles.kt` - built-in M8T scripts.
- `receiver/ublox-m8/src/main/kotlin/org/rtkcollector/receiver/ublox/UbloxTelemetry.kt` - u-blox telemetry model.
- `receiver/ublox-m8/src/main/kotlin/org/rtkcollector/receiver/ublox/UbloxStreamParser.kt` - mixed UBX/NMEA stream parser.
- `receiver/ublox-m8/src/main/kotlin/org/rtkcollector/receiver/ublox/UbloxNavPvtParser.kt` - UBX `NAV-PVT` parsing to telemetry/candidate.
- `receiver/ublox-m8/src/main/kotlin/org/rtkcollector/receiver/ublox/UbloxMessageFrequencyTracker.kt` - compact u-blox frequency display.
- `receiver/ublox-m8/src/test/kotlin/org/rtkcollector/receiver/ublox/UbloxFrameTest.kt`
- `receiver/ublox-m8/src/test/kotlin/org/rtkcollector/receiver/ublox/UbloxScriptCompilerTest.kt`
- `receiver/ublox-m8/src/test/kotlin/org/rtkcollector/receiver/ublox/UbloxStreamParserTest.kt`
- `receiver/ublox-m8/src/test/kotlin/org/rtkcollector/receiver/ublox/UbloxNavPvtParserTest.kt`
- `receiver/ublox-m8/src/test/kotlin/org/rtkcollector/receiver/ublox/UbloxMessageFrequencyTrackerTest.kt`
- `app/src/main/kotlin/org/rtkcollector/app/mocklocation/MockLocationPublisher.kt` - Android mock-location adapter plus fakeable interface.
- `app/src/test/kotlin/org/rtkcollector/app/mocklocation/MockLocationPublisherTest.kt`

Modify:

- `settings.gradle.kts` - include `:core:solution`.
- `app/build.gradle.kts` - depend on `:core:solution`.
- `receiver/ublox-m8/build.gradle.kts` - depend on `:core:solution` if parser directly creates candidates.
- `app/src/main/kotlin/org/rtkcollector/app/profile/ProfileModels.kt` - add mock-provider recording policy fields.
- `app/src/main/kotlin/org/rtkcollector/app/profile/ProfileStores.kt` - add M8T default command profiles.
- `app/src/main/kotlin/org/rtkcollector/app/profile/ProfileStoreMigrations.kt` - migrate existing stores to include new built-ins.
- `app/src/main/kotlin/org/rtkcollector/app/profile/ActiveRecordingConfig.kt` - pass mock provider options into recording start.
- `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingServiceState.kt` - add best-solution and mock-provider status fields.
- `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt` - feed u-blox parser, selector and mock publisher from advisory parser path.
- `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardModels.kt` - expose best-solution/mock status on dashboard.
- `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardServiceMapper.kt` - map service best-solution state to dashboard.
- `app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileScreens.kt` - expose mock provider recording-policy checkbox.
- `docs/receiver-profiles.md` - document M8T practical profile support.
- `docs/user-workflows.md` - document mock provider as recording-scoped.

Validation:

- Termux-safe: `git diff --check`, pure module tests, `sh gradlew :app:compileDebugKotlin`.
- Do not repeatedly run `assembleDebug`, `:app:testDebugUnitTest`, or Android resource-packaging tasks in this Termux environment unless native `aapt2` availability changes.

---

### Task 1: Add Core Solution Module

**Files:**
- Modify: `settings.gradle.kts`
- Create: `core/solution/build.gradle.kts`
- Create: `core/solution/src/main/kotlin/org/rtkcollector/core/solution/SolutionModels.kt`
- Create: `core/solution/src/main/kotlin/org/rtkcollector/core/solution/BestSolutionSelector.kt`
- Create: `core/solution/src/test/kotlin/org/rtkcollector/core/solution/BestSolutionSelectorTest.kt`

- [ ] **Step 1: Add failing selector tests**

Create `core/solution/src/test/kotlin/org/rtkcollector/core/solution/BestSolutionSelectorTest.kt`:

```kotlin
package org.rtkcollector.core.solution

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class BestSolutionSelectorTest {
    @Test
    fun `selects higher quality fresh candidate`() {
        val now = 10_000L
        val single = candidate("single", FixClass.SINGLE, updatedAtMillis = now - 500)
        val rtkFloat = candidate("float", FixClass.RTK_FLOAT, updatedAtMillis = now - 900)

        val selected = BestSolutionSelector.select(listOf(single, rtkFloat), nowMillis = now)

        assertEquals("float", selected?.sourceId)
        assertEquals(FixClass.RTK_FLOAT, selected?.fixClass)
    }

    @Test
    fun `drops stale candidates`() {
        val now = 10_000L
        val stale = candidate("old", FixClass.RTK_FIXED, updatedAtMillis = now - 6_000)

        val selected = BestSolutionSelector.select(listOf(stale), nowMillis = now)

        assertNull(selected)
    }

    @Test
    fun `uses better accuracy when quality is tied`() {
        val now = 10_000L
        val coarse = candidate("coarse", FixClass.DGPS, updatedAtMillis = now - 100, horizontalAccuracyM = 2.0)
        val precise = candidate("precise", FixClass.DGPS, updatedAtMillis = now - 300, horizontalAccuracyM = 0.4)

        val selected = BestSolutionSelector.select(listOf(coarse, precise), nowMillis = now)

        assertEquals("precise", selected?.sourceId)
    }

    @Test
    fun `ppp converging does not outrank dgps`() {
        val now = 10_000L
        val dgps = candidate("dgps", FixClass.DGPS, updatedAtMillis = now - 500)
        val pppConverging = candidate("ppp", FixClass.PPP_CONVERGING, updatedAtMillis = now - 100)

        val selected = BestSolutionSelector.select(listOf(dgps, pppConverging), nowMillis = now)

        assertEquals("dgps", selected?.sourceId)
    }

    private fun candidate(
        id: String,
        fixClass: FixClass,
        updatedAtMillis: Long,
        horizontalAccuracyM: Double? = null,
    ): SolutionCandidate =
        SolutionCandidate(
            sourceId = id,
            receiverFamily = "test",
            engine = SolutionEngine.DEVICE_INTERNAL,
            fixClass = fixClass,
            updatedAtMillis = updatedAtMillis,
            latDeg = 50.0,
            lonDeg = 14.0,
            ellipsoidalHeightM = 300.0,
            mslAltitudeM = 255.0,
            horizontalAccuracyM = horizontalAccuracyM,
            verticalAccuracyM = null,
            satellitesUsed = 12,
            satellitesInView = 18,
        )
}
```

- [ ] **Step 2: Wire the new module**

Modify `settings.gradle.kts`:

```kotlin
include(":core:solution")
```

Create `core/solution/build.gradle.kts`:

```kotlin
plugins {
    kotlin("jvm")
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.13.4")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.13.4")
}

tasks.test {
    useJUnitPlatform()
}
```

- [ ] **Step 3: Add solution models**

Create `core/solution/src/main/kotlin/org/rtkcollector/core/solution/SolutionModels.kt`:

```kotlin
package org.rtkcollector.core.solution

enum class SolutionEngine {
    DEVICE_INTERNAL,
    RECEIVER_PPP,
    RTKLIB_REALTIME,
    GENERIC_NMEA,
}

enum class FixClass(val rank: Int) {
    NONE(0),
    PPP_CONVERGING(1),
    SINGLE(2),
    DGPS(3),
    SBAS(3),
    PPP_CONVERGED(4),
    RTK_FLOAT(5),
    RTK_FIXED(6),
}

data class SolutionCandidate(
    val sourceId: String,
    val receiverFamily: String,
    val engine: SolutionEngine,
    val fixClass: FixClass,
    val updatedAtMillis: Long,
    val utcTime: String? = null,
    val latDeg: Double,
    val lonDeg: Double,
    val ellipsoidalHeightM: Double? = null,
    val mslAltitudeM: Double? = null,
    val horizontalAccuracyM: Double? = null,
    val verticalAccuracyM: Double? = null,
    val satellitesUsed: Int? = null,
    val satellitesInView: Int? = null,
)

data class BestSolutionSnapshot(
    val sourceId: String,
    val receiverFamily: String,
    val engine: SolutionEngine,
    val fixClass: FixClass,
    val updatedAtMillis: Long,
    val utcTime: String?,
    val latDeg: Double,
    val lonDeg: Double,
    val ellipsoidalHeightM: Double?,
    val mslAltitudeM: Double?,
    val horizontalAccuracyM: Double?,
    val verticalAccuracyM: Double?,
    val satellitesUsed: Int?,
    val satellitesInView: Int?,
    val ageMillis: Long,
) {
    val isFresh: Boolean
        get() = ageMillis <= BestSolutionSelector.DEFAULT_MAX_AGE_MILLIS
}
```

- [ ] **Step 4: Add selector implementation**

Create `core/solution/src/main/kotlin/org/rtkcollector/core/solution/BestSolutionSelector.kt`:

```kotlin
package org.rtkcollector.core.solution

object BestSolutionSelector {
    const val DEFAULT_MAX_AGE_MILLIS: Long = 5_000L

    fun select(
        candidates: Iterable<SolutionCandidate>,
        nowMillis: Long,
        maxAgeMillis: Long = DEFAULT_MAX_AGE_MILLIS,
    ): BestSolutionSnapshot? =
        candidates
            .filter { it.fixClass != FixClass.NONE }
            .filter { nowMillis >= it.updatedAtMillis }
            .filter { nowMillis - it.updatedAtMillis <= maxAgeMillis }
            .maxWithOrNull(compareBy<SolutionCandidate> { it.fixClass.rank }
                .thenByDescending { it.horizontalAccuracyM ?: Double.MAX_VALUE }
                .thenBy { it.updatedAtMillis })
            ?.toSnapshot(nowMillis)

    private fun SolutionCandidate.toSnapshot(nowMillis: Long): BestSolutionSnapshot =
        BestSolutionSnapshot(
            sourceId = sourceId,
            receiverFamily = receiverFamily,
            engine = engine,
            fixClass = fixClass,
            updatedAtMillis = updatedAtMillis,
            utcTime = utcTime,
            latDeg = latDeg,
            lonDeg = lonDeg,
            ellipsoidalHeightM = ellipsoidalHeightM,
            mslAltitudeM = mslAltitudeM,
            horizontalAccuracyM = horizontalAccuracyM,
            verticalAccuracyM = verticalAccuracyM,
            satellitesUsed = satellitesUsed,
            satellitesInView = satellitesInView,
            ageMillis = nowMillis - updatedAtMillis,
        )
}
```

- [ ] **Step 5: Run module tests**

Run:

```sh
sh gradlew :core:solution:test
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```sh
git add settings.gradle.kts core/solution
git commit -m "Add best solution selection model"
```

---

### Task 2: Implement UBX Frame Building And Script Compilation

**Files:**
- Create: `receiver/ublox-m8/src/main/kotlin/org/rtkcollector/receiver/ublox/UbloxFrame.kt`
- Create: `receiver/ublox-m8/src/main/kotlin/org/rtkcollector/receiver/ublox/UbloxScriptCompiler.kt`
- Create: `receiver/ublox-m8/src/test/kotlin/org/rtkcollector/receiver/ublox/UbloxFrameTest.kt`
- Create: `receiver/ublox-m8/src/test/kotlin/org/rtkcollector/receiver/ublox/UbloxScriptCompilerTest.kt`

- [ ] **Step 1: Add failing UBX frame tests**

Create `receiver/ublox-m8/src/test/kotlin/org/rtkcollector/receiver/ublox/UbloxFrameTest.kt`:

```kotlin
package org.rtkcollector.receiver.ublox

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UbloxFrameTest {
    @Test
    fun `builds ubx frame with checksum`() {
        val frame = UbloxFrame.build(messageClass = 0x06, messageId = 0x08, payload = byteArrayOf(0xE8.toByte(), 0x03, 0x01, 0x00, 0x01, 0x00))

        assertArrayEquals(
            byteArrayOf(
                0xB5.toByte(), 0x62, 0x06, 0x08, 0x06, 0x00,
                0xE8.toByte(), 0x03, 0x01, 0x00, 0x01, 0x00,
                0x01, 0x39,
            ),
            frame,
        )
        assertTrue(UbloxFrame.isValid(frame))
    }
}
```

- [ ] **Step 2: Add UBX frame implementation**

Create `receiver/ublox-m8/src/main/kotlin/org/rtkcollector/receiver/ublox/UbloxFrame.kt`:

```kotlin
package org.rtkcollector.receiver.ublox

object UbloxFrame {
    fun build(messageClass: Int, messageId: Int, payload: ByteArray): ByteArray {
        require(messageClass in 0..255) { "UBX message class must be 0..255." }
        require(messageId in 0..255) { "UBX message id must be 0..255." }
        require(payload.size <= 65_535) { "UBX payload is too large: ${payload.size} bytes." }
        val headerAndPayload = ByteArray(4 + payload.size)
        headerAndPayload[0] = messageClass.toByte()
        headerAndPayload[1] = messageId.toByte()
        headerAndPayload[2] = (payload.size and 0xff).toByte()
        headerAndPayload[3] = ((payload.size ushr 8) and 0xff).toByte()
        payload.copyInto(headerAndPayload, destinationOffset = 4)
        val checksum = checksum(headerAndPayload)
        return byteArrayOf(0xB5.toByte(), 0x62) + headerAndPayload + checksum
    }

    fun isValid(frame: ByteArray): Boolean {
        if (frame.size < 8 || frame[0] != 0xB5.toByte() || frame[1] != 0x62.toByte()) return false
        val length = (frame[4].toInt() and 0xff) or ((frame[5].toInt() and 0xff) shl 8)
        if (frame.size != 8 + length) return false
        val expected = checksum(frame.copyOfRange(2, frame.size - 2))
        return frame[frame.size - 2] == expected[0] && frame[frame.size - 1] == expected[1]
    }

    fun checksum(classIdLengthPayload: ByteArray): ByteArray {
        var ckA = 0
        var ckB = 0
        classIdLengthPayload.forEach { byte ->
            ckA = (ckA + (byte.toInt() and 0xff)) and 0xff
            ckB = (ckB + ckA) and 0xff
        }
        return byteArrayOf(ckA.toByte(), ckB.toByte())
    }
}
```

- [ ] **Step 3: Add failing script compiler tests**

Create `receiver/ublox-m8/src/test/kotlin/org/rtkcollector/receiver/ublox/UbloxScriptCompilerTest.kt`:

```kotlin
package org.rtkcollector.receiver.ublox

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UbloxScriptCompilerTest {
    @Test
    fun `compiles cfg rate command`() {
        val commands = UbloxScriptCompiler.compile("!UBX CFG-RATE 1000 1 1")

        assertEquals(1, commands.size)
        assertTrue(UbloxFrame.isValid(commands.single().payload))
        assertEquals("UBX CFG-RATE", commands.single().label)
        assertEquals(6, commands.single().payloadLength())
    }

    @Test
    fun `compiles cfg msg rawx usb enable command`() {
        val commands = UbloxScriptCompiler.compile("!UBX CFG-MSG 2 21 0 0 0 1 0 0")

        val payload = commands.single().payload
        assertEquals(0x06.toByte(), payload[2])
        assertEquals(0x01.toByte(), payload[3])
        assertEquals(8, commands.single().payloadLength())
    }

    @Test
    fun `compiles cfg gnss command with one config block`() {
        val commands = UbloxScriptCompiler.compile("!UBX CFG-GNSS 0 32 32 1 0 8 16 0 65537")

        assertEquals(12, commands.single().payloadLength())
    }

    @Test
    fun `compiles cfg nav5 command to documented payload size`() {
        val commands = UbloxScriptCompiler.compile(
            "!UBX CFG-NAV5 1 3 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0",
        )

        assertEquals(36, commands.single().payloadLength())
    }

    @Test
    fun `rejects unsupported command with line number`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            UbloxScriptCompiler.compile("!UBX CFG-UNKNOWN 1 2 3")
        }

        assertTrue(error.message!!.contains("line 1"))
        assertTrue(error.message!!.contains("CFG-UNKNOWN"))
    }

    private fun org.rtkcollector.receiver.api.ReceiverCommand.payloadLength(): Int =
        (payload[4].toInt() and 0xff) or ((payload[5].toInt() and 0xff) shl 8)
}
```

- [ ] **Step 4: Add script compiler implementation**

Create `receiver/ublox-m8/src/main/kotlin/org/rtkcollector/receiver/ublox/UbloxScriptCompiler.kt`:

```kotlin
package org.rtkcollector.receiver.ublox

import org.rtkcollector.receiver.api.ReceiverCommand

object UbloxScriptCompiler {
    private val supportedMessages = mapOf(
        "CFG-MSG" to Pair(0x06, 0x01),
        "CFG-GNSS" to Pair(0x06, 0x3E),
        "CFG-NAV5" to Pair(0x06, 0x24),
        "CFG-RATE" to Pair(0x06, 0x08),
    )

    fun compile(script: String): List<ReceiverCommand> =
        script.lineSequence()
            .mapIndexedNotNull { index, rawLine -> compileLine(index + 1, rawLine) }
            .toList()

    private fun compileLine(lineNumber: Int, rawLine: String): ReceiverCommand? {
        val line = rawLine.trim()
        if (line.isBlank() || line.startsWith("#")) return null
        require(line.startsWith("!UBX ")) { "Unsupported u-blox script line $lineNumber: expected !UBX prefix." }
        val parts = line.split(Regex("\\s+"))
        require(parts.size >= 3) { "Malformed !UBX line $lineNumber: missing command name." }
        val commandName = parts[1]
        val classAndId = supportedMessages[commandName]
            ?: throw IllegalArgumentException("Unsupported !UBX command on line $lineNumber: $commandName")
        val args = parts.drop(2).map { token -> parseInteger(lineNumber, token) }
        val payload = payload(commandName, lineNumber, args)
        return ReceiverCommand(
            label = "UBX $commandName",
            payload = UbloxFrame.build(classAndId.first, classAndId.second, payload),
        )
    }

    private fun parseInteger(lineNumber: Int, token: String): Long {
        val value = token.toLongOrNull()
            ?: throw IllegalArgumentException("Malformed !UBX payload on line $lineNumber: '$token' is not an integer.")
        require(value in 0..4_294_967_295L) { "Malformed !UBX payload on line $lineNumber: '$token' is out of range." }
        return value
    }

    private fun payload(commandName: String, lineNumber: Int, args: List<Long>): ByteArray =
        when (commandName) {
            "CFG-MSG" -> packCfgMsg(lineNumber, args)
            "CFG-GNSS" -> packCfgGnss(lineNumber, args)
            "CFG-NAV5" -> packCfgNav5(lineNumber, args)
            "CFG-RATE" -> packCfgRate(lineNumber, args)
            else -> throw IllegalArgumentException("Unsupported !UBX command on line $lineNumber: $commandName")
        }

    private fun packCfgMsg(lineNumber: Int, args: List<Long>): ByteArray {
        require(args.size == 8) { "CFG-MSG on line $lineNumber requires 8 integer fields." }
        return args.map { u8(lineNumber, it) }.toByteArray()
    }

    private fun packCfgGnss(lineNumber: Int, args: List<Long>): ByteArray {
        require(args.size == 9) { "CFG-GNSS on line $lineNumber requires 9 integer fields for one GNSS block." }
        return byteArrayOf(
            u8(lineNumber, args[0]),
            u8(lineNumber, args[1]),
            u8(lineNumber, args[2]),
            u8(lineNumber, args[3]),
            u8(lineNumber, args[4]),
            u8(lineNumber, args[5]),
            u8(lineNumber, args[6]),
            u8(lineNumber, args[7]),
        ) + u32(lineNumber, args[8])
    }

    private fun packCfgRate(lineNumber: Int, args: List<Long>): ByteArray {
        require(args.size == 3) { "CFG-RATE on line $lineNumber requires measRate, navRate and timeRef." }
        return u16(lineNumber, args[0]) + u16(lineNumber, args[1]) + u16(lineNumber, args[2])
    }

    private fun packCfgNav5(lineNumber: Int, args: List<Long>): ByteArray {
        require(args.size >= 18 && args.size <= 24) {
            "CFG-NAV5 on line $lineNumber requires 18 base fields and up to 6 trailing reserved byte fields."
        }
        val reservedTail = args.drop(18).take(5)
        return u16(lineNumber, args[0]) +
            byteArrayOf(u8(lineNumber, args[1]), u8(lineNumber, args[2])) +
            i32(lineNumber, args[3]) +
            u32(lineNumber, args[4]) +
            byteArrayOf(u8(lineNumber, args[5]), u8(lineNumber, args[6])) +
            u16(lineNumber, args[7]) +
            u16(lineNumber, args[8]) +
            u16(lineNumber, args[9]) +
            u16(lineNumber, args[10]) +
            byteArrayOf(u8(lineNumber, args[11]), u8(lineNumber, args[12]), u8(lineNumber, args[13]), u8(lineNumber, args[14])) +
            u16(lineNumber, args[15]) +
            u16(lineNumber, args[16]) +
            byteArrayOf(u8(lineNumber, args[17])) +
            ByteArray(5) { index -> reservedTail.getOrNull(index)?.let { u8(lineNumber, it) } ?: 0 }
    }

    private fun u8(lineNumber: Int, value: Long): Byte {
        require(value in 0..255) { "Payload value on line $lineNumber does not fit uint8: $value" }
        return value.toByte()
    }

    private fun u16(lineNumber: Int, value: Long): ByteArray {
        require(value in 0..65_535) { "Payload value on line $lineNumber does not fit uint16: $value" }
        return byteArrayOf((value and 0xff).toByte(), ((value ushr 8) and 0xff).toByte())
    }

    private fun u32(lineNumber: Int, value: Long): ByteArray =
        byteArrayOf(
            (value and 0xff).toByte(),
            ((value ushr 8) and 0xff).toByte(),
            ((value ushr 16) and 0xff).toByte(),
            ((value ushr 24) and 0xff).toByte(),
        )

    private fun i32(lineNumber: Int, value: Long): ByteArray {
        require(value in Int.MIN_VALUE..Int.MAX_VALUE) { "Payload value on line $lineNumber does not fit int32: $value" }
        return u32(lineNumber, value and 0xffff_ffffL)
    }
}
```

- [ ] **Step 5: Run u-blox protocol tests**

Run:

```sh
sh gradlew :receiver:ublox-m8:test --tests org.rtkcollector.receiver.ublox.UbloxFrameTest --tests org.rtkcollector.receiver.ublox.UbloxScriptCompilerTest
```

Expected: `BUILD SUCCESSFUL`. If any test fails, fix the command-specific packer; do not weaken tests or send ambiguous bytes.

- [ ] **Step 6: Commit**

```sh
git add receiver/ublox-m8/src/main/kotlin/org/rtkcollector/receiver/ublox/UbloxFrame.kt receiver/ublox-m8/src/main/kotlin/org/rtkcollector/receiver/ublox/UbloxScriptCompiler.kt receiver/ublox-m8/src/test/kotlin/org/rtkcollector/receiver/ublox/UbloxFrameTest.kt receiver/ublox-m8/src/test/kotlin/org/rtkcollector/receiver/ublox/UbloxScriptCompilerTest.kt
git commit -m "Add UBX frame and script compiler"
```

---

### Task 3: Add M8T Built-In Command Profiles

**Files:**
- Create: `receiver/ublox-m8/src/main/kotlin/org/rtkcollector/receiver/ublox/UbloxM8tProfiles.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/profile/ProfileStores.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/profile/ProfileStoreMigrations.kt`
- Test: `app/src/test/kotlin/org/rtkcollector/app/profile/ProfileStoresTest.kt` if present; otherwise create `app/src/test/kotlin/org/rtkcollector/app/profile/ProfileDefaultsTest.kt`

- [ ] **Step 1: Add profile defaults tests**

Create `app/src/test/kotlin/org/rtkcollector/app/profile/ProfileDefaultsTest.kt`:

```kotlin
package org.rtkcollector.app.profile

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rtkcollector.receiver.ublox.UbloxScriptCompiler

class ProfileDefaultsTest {
    @Test
    fun `m8t default scripts compile to ubx commands`() {
        val scripts = listOf(
            ProfileStores.UBLOX_M8T_RAW_1HZ_SCRIPT,
            ProfileStores.UBLOX_M8T_RAW_5HZ_RTKLIB_EX_SCRIPT,
            ProfileStores.UBLOX_M8T_RAW_STATUS_MOCK_SCRIPT,
        )

        scripts.forEach { script ->
            assertTrue(UbloxScriptCompiler.compile(script).isNotEmpty())
        }
    }

    @Test
    fun `m8t high rate profile contains cfg rate 5 hz`() {
        assertTrue(ProfileStores.UBLOX_M8T_RAW_5HZ_RTKLIB_EX_SCRIPT.contains("!UBX CFG-RATE 200 1 1"))
    }
}
```

- [ ] **Step 2: Add receiver-side profile constants**

Create `receiver/ublox-m8/src/main/kotlin/org/rtkcollector/receiver/ublox/UbloxM8tProfiles.kt`:

```kotlin
package org.rtkcollector.receiver.ublox

object UbloxM8tProfiles {
    val raw1HzSafe: String = """
        !UBX CFG-RATE 1000 1 1
        !UBX CFG-MSG 2 21 0 0 0 1 0 0
        !UBX CFG-MSG 2 19 0 0 0 1 0 0
        !UBX CFG-MSG 13 3 0 0 0 1 0 0
        !UBX CFG-MSG 240 0 0 0 0 0 0 0
        !UBX CFG-MSG 240 1 0 0 0 0 0 0
        !UBX CFG-MSG 240 2 0 0 0 0 0 0
        !UBX CFG-MSG 240 3 0 0 0 0 0 0
        !UBX CFG-MSG 240 4 0 0 0 0 0 0
        !UBX CFG-MSG 240 5 0 0 0 0 0 0
        !UBX CFG-MSG 240 8 0 0 0 0 0 0
    """.trimIndent()

    val raw5HzRtklibEx: String = """
        !UBX CFG-MSG 2 21 0 0 0 1 0 0
        !UBX CFG-MSG 2 19 0 0 0 1 0 0
        !UBX CFG-MSG 13 3 0 0 0 1 0 0
        !UBX CFG-GNSS 0 32 32 1 0 8 16 0 65537
        !UBX CFG-GNSS 0 32 32 1 1 1 3 0 65537
        !UBX CFG-GNSS 0 32 32 1 2 4 8 0 65537
        !UBX CFG-GNSS 0 32 32 1 3 8 16 0 0
        !UBX CFG-GNSS 0 32 32 1 4 0 8 0 0
        !UBX CFG-GNSS 0 32 32 1 5 0 3 0 0
        !UBX CFG-GNSS 0 32 32 1 6 8 14 0 65537
        !UBX CFG-NAV5 1 3 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0
        !UBX CFG-MSG 240 0 0 0 0 0 0 0
        !UBX CFG-MSG 240 1 0 0 0 0 0 0
        !UBX CFG-MSG 240 2 0 0 0 0 0 0
        !UBX CFG-MSG 240 3 0 0 0 0 0 0
        !UBX CFG-MSG 240 4 0 0 0 0 0 0
        !UBX CFG-MSG 240 5 0 0 0 0 0 0
        !UBX CFG-MSG 240 8 0 0 0 0 0 0
        !UBX CFG-RATE 200 1 1
    """.trimIndent()

    val rawStatusMock: String = """
        !UBX CFG-RATE 1000 1 1
        !UBX CFG-MSG 2 21 0 0 0 1 0 0
        !UBX CFG-MSG 2 19 0 0 0 1 0 0
        !UBX CFG-MSG 1 7 0 0 0 1 0 0
        !UBX CFG-MSG 240 0 0 0 0 0 0 0
        !UBX CFG-MSG 240 1 0 0 0 0 0 0
        !UBX CFG-MSG 240 2 0 0 0 0 0 0
        !UBX CFG-MSG 240 3 0 0 0 0 0 0
        !UBX CFG-MSG 240 4 0 0 0 0 0 0
        !UBX CFG-MSG 240 5 0 0 0 0 0 0
    """.trimIndent()
}
```

- [ ] **Step 3: Expose profiles through app defaults**

Modify `app/src/main/kotlin/org/rtkcollector/app/profile/ProfileStores.kt`:

```kotlin
import org.rtkcollector.receiver.ublox.UbloxM8tProfiles
```

Add to `defaultCommandProfiles()`:

```kotlin
CommandProfile(
    id = "ublox-m8t-raw-1hz-safe",
    name = "u-blox M8T raw 1 Hz safe",
    receiverFamily = "ublox-m8t",
    runtimeScript = UBLOX_M8T_RAW_1HZ_SCRIPT,
),
CommandProfile(
    id = "ublox-m8t-raw-5hz-rtklib-ex",
    name = "u-blox M8T raw 5 Hz RTKLIB-EX",
    receiverFamily = "ublox-m8t",
    runtimeScript = UBLOX_M8T_RAW_5HZ_RTKLIB_EX_SCRIPT,
),
CommandProfile(
    id = "ublox-m8t-raw-status-mock",
    name = "u-blox M8T raw + status/mock",
    receiverFamily = "ublox-m8t",
    runtimeScript = UBLOX_M8T_RAW_STATUS_MOCK_SCRIPT,
),
```

Add companion constants:

```kotlin
const val UBLOX_M8T_RAW_1HZ_PROFILE_ID = "ublox-m8t-raw-1hz-safe"
const val UBLOX_M8T_RAW_5HZ_RTKLIB_EX_PROFILE_ID = "ublox-m8t-raw-5hz-rtklib-ex"
const val UBLOX_M8T_RAW_STATUS_MOCK_PROFILE_ID = "ublox-m8t-raw-status-mock"
val UBLOX_M8T_RAW_1HZ_SCRIPT: String = UbloxM8tProfiles.raw1HzSafe
val UBLOX_M8T_RAW_5HZ_RTKLIB_EX_SCRIPT: String = UbloxM8tProfiles.raw5HzRtklibEx
val UBLOX_M8T_RAW_STATUS_MOCK_SCRIPT: String = UbloxM8tProfiles.rawStatusMock
```

- [ ] **Step 4: Migrate existing profile lists**

Modify `ProfileStoreMigrations.commandProfiles(...)` to append missing u-blox built-ins by id. Use the existing migration style in that file. The required code shape is:

```kotlin
fun commandProfiles(profiles: List<CommandProfile>, defaults: List<CommandProfile>): List<CommandProfile> {
    val byId = profiles.associateBy { it.id }
    val requiredDefaults = defaults.filter { it.id.startsWith("ublox-m8t-") || it.id.startsWith("um980-") }
    return profiles + requiredDefaults.filter { byId[it.id] == null }
}
```

If the file already has more detailed UM980 migration logic, preserve it and add only the missing u-blox default append.

- [ ] **Step 5: Run default profile tests**

Run:

```sh
sh gradlew :receiver:ublox-m8:test :app:compileDebugKotlin
```

Expected: `:receiver:ublox-m8:test` passes; `:app:compileDebugKotlin` passes.

- [ ] **Step 6: Commit**

```sh
git add receiver/ublox-m8/src/main/kotlin/org/rtkcollector/receiver/ublox/UbloxM8tProfiles.kt app/src/main/kotlin/org/rtkcollector/app/profile/ProfileStores.kt app/src/main/kotlin/org/rtkcollector/app/profile/ProfileStoreMigrations.kt app/src/test/kotlin/org/rtkcollector/app/profile/ProfileDefaultsTest.kt
git commit -m "Add u-blox M8T command profiles"
```

---

### Task 4: Parse u-blox UBX Streams And NAV-PVT

**Files:**
- Create: `receiver/ublox-m8/src/main/kotlin/org/rtkcollector/receiver/ublox/UbloxTelemetry.kt`
- Create: `receiver/ublox-m8/src/main/kotlin/org/rtkcollector/receiver/ublox/UbloxStreamParser.kt`
- Create: `receiver/ublox-m8/src/main/kotlin/org/rtkcollector/receiver/ublox/UbloxNavPvtParser.kt`
- Create: `receiver/ublox-m8/src/test/kotlin/org/rtkcollector/receiver/ublox/UbloxStreamParserTest.kt`
- Create: `receiver/ublox-m8/src/test/kotlin/org/rtkcollector/receiver/ublox/UbloxNavPvtParserTest.kt`

- [ ] **Step 1: Add stream parser tests**

Create `receiver/ublox-m8/src/test/kotlin/org/rtkcollector/receiver/ublox/UbloxStreamParserTest.kt`:

```kotlin
package org.rtkcollector.receiver.ublox

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UbloxStreamParserTest {
    @Test
    fun `extracts ubx frame from noise`() {
        val frame = UbloxFrame.build(0x01, 0x07, ByteArray(92))
        val records = UbloxStreamParser().accept(byteArrayOf(0x55) + frame + byteArrayOf(0x33))

        assertEquals(listOf("noise", "ubx", "noise"), records.map { it.kind })
        assertEquals(frame.toList(), records[1].bytes.toList())
    }

    @Test
    fun `holds incomplete ubx frame until complete`() {
        val frame = UbloxFrame.build(0x01, 0x07, ByteArray(92))
        val parser = UbloxStreamParser()

        assertEquals(emptyList<UbloxStreamRecord>(), parser.accept(frame.copyOfRange(0, 20)))
        val records = parser.accept(frame.copyOfRange(20, frame.size))

        assertEquals(1, records.size)
        assertEquals("ubx", records.single().kind)
    }

    @Test
    fun `extracts nmea line separately`() {
        val records = UbloxStreamParser().accept("\$GNGGA,120000,5000.0,N,01400.0,E,1,12,0.8,300.0,M,0.0,M,,*00\r\n".encodeToByteArray())

        assertEquals("nmea", records.single().kind)
    }
}
```

- [ ] **Step 2: Add telemetry model and stream parser**

Create `receiver/ublox-m8/src/main/kotlin/org/rtkcollector/receiver/ublox/UbloxTelemetry.kt`:

```kotlin
package org.rtkcollector.receiver.ublox

import org.rtkcollector.core.solution.FixClass
import org.rtkcollector.core.solution.SolutionCandidate
import org.rtkcollector.core.solution.SolutionEngine

data class UbloxTelemetry(
    val source: String,
    val updatedAtMillis: Long,
    val fixClass: FixClass? = null,
    val latDeg: Double? = null,
    val lonDeg: Double? = null,
    val ellipsoidalHeightM: Double? = null,
    val mslAltitudeM: Double? = null,
    val horizontalAccuracyM: Double? = null,
    val verticalAccuracyM: Double? = null,
    val satellitesUsed: Int? = null,
    val rawObservationsPresent: Boolean = false,
) {
    fun toSolutionCandidate(): SolutionCandidate? {
        val lat = latDeg ?: return null
        val lon = lonDeg ?: return null
        val fix = fixClass ?: return null
        return SolutionCandidate(
            sourceId = source,
            receiverFamily = "ublox-m8",
            engine = SolutionEngine.DEVICE_INTERNAL,
            fixClass = fix,
            updatedAtMillis = updatedAtMillis,
            latDeg = lat,
            lonDeg = lon,
            ellipsoidalHeightM = ellipsoidalHeightM,
            mslAltitudeM = mslAltitudeM,
            horizontalAccuracyM = horizontalAccuracyM,
            verticalAccuracyM = verticalAccuracyM,
            satellitesUsed = satellitesUsed,
            satellitesInView = null,
        )
    }
}
```

Create `receiver/ublox-m8/src/main/kotlin/org/rtkcollector/receiver/ublox/UbloxStreamParser.kt`:

```kotlin
package org.rtkcollector.receiver.ublox

data class UbloxStreamRecord(
    val kind: String,
    val bytes: ByteArray,
    val text: String? = null,
)

class UbloxStreamParser {
    private var pending = ByteArray(0)

    fun accept(input: ByteArray): List<UbloxStreamRecord> {
        val data = pending + input
        pending = ByteArray(0)
        val records = mutableListOf<UbloxStreamRecord>()
        var index = 0
        while (index < data.size) {
            when {
                hasUbxSync(data, index) -> {
                    val end = frameEnd(data, index)
                    if (end == INCOMPLETE) {
                        pending = data.copyOfRange(index, data.size)
                        index = data.size
                    } else if (end > 0 && UbloxFrame.isValid(data.copyOfRange(index, end))) {
                        records += UbloxStreamRecord("ubx", data.copyOfRange(index, end))
                        index = end
                    } else {
                        records += UbloxStreamRecord("noise", data.copyOfRange(index, index + 1))
                        index += 1
                    }
                }
                data[index] == '$'.code.toByte() -> {
                    val end = findLineEnd(data, index)
                    if (end < 0) {
                        pending = data.copyOfRange(index, data.size)
                        index = data.size
                    } else {
                        val bytes = data.copyOfRange(index, end)
                        records += UbloxStreamRecord("nmea", bytes, bytes.decodeToString())
                        index = end
                    }
                }
                else -> {
                    records += UbloxStreamRecord("noise", data.copyOfRange(index, index + 1))
                    index += 1
                }
            }
        }
        return records
    }

    private fun hasUbxSync(data: ByteArray, index: Int): Boolean =
        index + 1 < data.size && data[index] == 0xB5.toByte() && data[index + 1] == 0x62.toByte()

    private fun frameEnd(data: ByteArray, start: Int): Int {
        if (start + 6 > data.size) return INCOMPLETE
        val length = (data[start + 4].toInt() and 0xff) or ((data[start + 5].toInt() and 0xff) shl 8)
        if (length > MAX_PAYLOAD_LENGTH) return INVALID
        val end = start + 8 + length
        return if (end <= data.size) end else INCOMPLETE
    }

    private fun findLineEnd(input: ByteArray, start: Int): Int {
        for (i in start until input.size) {
            if (input[i] == '\n'.code.toByte()) return i + 1
        }
        return -1
    }

    private companion object {
        const val INCOMPLETE = -1
        const val INVALID = -2
        const val MAX_PAYLOAD_LENGTH = 8192
    }
}
```

- [ ] **Step 3: Add NAV-PVT parser tests**

Create `receiver/ublox-m8/src/test/kotlin/org/rtkcollector/receiver/ublox/UbloxNavPvtParserTest.kt`:

```kotlin
package org.rtkcollector.receiver.ublox

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.rtkcollector.core.solution.FixClass
import java.nio.ByteBuffer
import java.nio.ByteOrder

class UbloxNavPvtParserTest {
    @Test
    fun `parses nav pvt position`() {
        val payload = ByteArray(92)
        ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(0, 123456)
            put(20, 0x03)
            put(23, 14)
            putInt(24, (14.4212534 * 1e7).toInt())
            putInt(28, (50.0874512 * 1e7).toInt())
            putInt(32, 287_423)
            putInt(36, 243_812)
            putInt(40, 800)
            putInt(44, 1200)
        }
        val frame = UbloxFrame.build(0x01, 0x07, payload)

        val telemetry = UbloxNavPvtParser.parse(frame, nowMillis = 10_000L)

        assertNotNull(telemetry)
        assertEquals(FixClass.DGPS, telemetry!!.fixClass)
        assertEquals(50.0874512, telemetry.latDeg!!, 0.0000001)
        assertEquals(14.4212534, telemetry.lonDeg!!, 0.0000001)
        assertEquals(287.423, telemetry.ellipsoidalHeightM!!, 0.001)
        assertEquals(243.812, telemetry.mslAltitudeM!!, 0.001)
        assertEquals(0.8, telemetry.horizontalAccuracyM!!, 0.001)
        assertEquals(1.2, telemetry.verticalAccuracyM!!, 0.001)
        assertEquals(14, telemetry.satellitesUsed)
    }
}
```

- [ ] **Step 4: Add NAV-PVT parser implementation**

Create `receiver/ublox-m8/src/main/kotlin/org/rtkcollector/receiver/ublox/UbloxNavPvtParser.kt`:

```kotlin
package org.rtkcollector.receiver.ublox

import org.rtkcollector.core.solution.FixClass
import java.nio.ByteBuffer
import java.nio.ByteOrder

object UbloxNavPvtParser {
    fun parse(frame: ByteArray, nowMillis: Long): UbloxTelemetry? {
        if (!UbloxFrame.isValid(frame)) return null
        if ((frame[2].toInt() and 0xff) != 0x01 || (frame[3].toInt() and 0xff) != 0x07) return null
        val length = (frame[4].toInt() and 0xff) or ((frame[5].toInt() and 0xff) shl 8)
        if (length < 92) return null
        val payload = ByteBuffer.wrap(frame, 6, length).order(ByteOrder.LITTLE_ENDIAN)
        val fixType = payload.get(20).toInt() and 0xff
        val flags = payload.get(21).toInt() and 0xff
        val fixClass = fixClass(fixType, flags)
        val satellites = payload.get(23).toInt() and 0xff
        return UbloxTelemetry(
            source = "UBX-NAV-PVT",
            updatedAtMillis = nowMillis,
            fixClass = fixClass,
            lonDeg = payload.getInt(24) / 1e7,
            latDeg = payload.getInt(28) / 1e7,
            ellipsoidalHeightM = payload.getInt(32) / 1000.0,
            mslAltitudeM = payload.getInt(36) / 1000.0,
            horizontalAccuracyM = payload.getInt(40) / 1000.0,
            verticalAccuracyM = payload.getInt(44) / 1000.0,
            satellitesUsed = satellites,
        )
    }

    private fun fixClass(fixType: Int, flags: Int): FixClass {
        val differential = flags and 0x02 != 0
        return when {
            fixType < 2 -> FixClass.NONE
            differential -> FixClass.DGPS
            fixType >= 3 -> FixClass.SINGLE
            else -> FixClass.NONE
        }
    }
}
```

- [ ] **Step 5: Run parser tests**

Run:

```sh
sh gradlew :receiver:ublox-m8:test --tests org.rtkcollector.receiver.ublox.UbloxStreamParserTest --tests org.rtkcollector.receiver.ublox.UbloxNavPvtParserTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```sh
git add receiver/ublox-m8/src/main/kotlin/org/rtkcollector/receiver/ublox/UbloxTelemetry.kt receiver/ublox-m8/src/main/kotlin/org/rtkcollector/receiver/ublox/UbloxStreamParser.kt receiver/ublox-m8/src/main/kotlin/org/rtkcollector/receiver/ublox/UbloxNavPvtParser.kt receiver/ublox-m8/src/test/kotlin/org/rtkcollector/receiver/ublox/UbloxStreamParserTest.kt receiver/ublox-m8/src/test/kotlin/org/rtkcollector/receiver/ublox/UbloxNavPvtParserTest.kt
git commit -m "Parse u-blox M8T NAV-PVT telemetry"
```

---

### Task 5: Add u-blox Frequency Tracking And Driver Integration

**Files:**
- Create: `receiver/ublox-m8/src/main/kotlin/org/rtkcollector/receiver/ublox/UbloxMessageFrequencyTracker.kt`
- Create: `receiver/ublox-m8/src/test/kotlin/org/rtkcollector/receiver/ublox/UbloxMessageFrequencyTrackerTest.kt`
- Modify: `receiver/ublox-m8/src/main/kotlin/org/rtkcollector/receiver/ublox/UbloxM8Driver.kt`

- [ ] **Step 1: Add frequency tracker tests**

Create `receiver/ublox-m8/src/test/kotlin/org/rtkcollector/receiver/ublox/UbloxMessageFrequencyTrackerTest.kt`:

```kotlin
package org.rtkcollector.receiver.ublox

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UbloxMessageFrequencyTrackerTest {
    @Test
    fun `formats compact frequency line`() {
        val tracker = UbloxMessageFrequencyTracker()
        tracker.record(UbloxMessageKind.RAWX, 1_000L)
        tracker.record(UbloxMessageKind.RAWX, 2_000L)
        tracker.record(UbloxMessageKind.NAV_PVT, 1_500L)
        tracker.record(UbloxMessageKind.NAV_PVT, 2_500L)

        assertEquals("Frequency RAWX/SFRBX/TM2/NAV-PVT/GGA 1/-/-/1/- Hz", tracker.display(3_000L))
    }
}
```

- [ ] **Step 2: Add frequency tracker implementation**

Create `receiver/ublox-m8/src/main/kotlin/org/rtkcollector/receiver/ublox/UbloxMessageFrequencyTracker.kt`:

```kotlin
package org.rtkcollector.receiver.ublox

enum class UbloxMessageKind(val label: String) {
    RAWX("RAWX"),
    SFRBX("SFRBX"),
    TM2("TM2"),
    NAV_PVT("NAV-PVT"),
    GGA("GGA"),
}

class UbloxMessageFrequencyTracker {
    private val recent = mutableMapOf<UbloxMessageKind, MutableList<Long>>()

    fun record(kind: UbloxMessageKind, timestampMillis: Long) {
        val values = recent.getOrPut(kind) { mutableListOf() }
        values += timestampMillis
        values.removeAll { timestampMillis - it > WINDOW_MILLIS }
    }

    fun display(nowMillis: Long): String {
        val values = UbloxMessageKind.entries.joinToString("/") { kind ->
            val count = recent[kind].orEmpty().count { nowMillis - it <= WINDOW_MILLIS }
            if (count == 0) "-" else count.toString()
        }
        return "Frequency RAWX/SFRBX/TM2/NAV-PVT/GGA $values Hz"
    }

    private companion object {
        const val WINDOW_MILLIS = 1_500L
    }
}
```

- [ ] **Step 3: Make driver compile scripts**

Modify `UbloxM8Driver.buildInitCommands(...)` so receiver API callers compile the explicit `ReceiverProfile.initScript`:

```kotlin
override fun buildInitCommands(profile: ReceiverProfile): List<ReceiverCommand> =
    UbloxScriptCompiler.compile(profile.initScript.orEmpty())
```

- [ ] **Step 4: Run tests**

Run:

```sh
sh gradlew :receiver:ublox-m8:test
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```sh
git add receiver/ublox-m8/src/main/kotlin/org/rtkcollector/receiver/ublox/UbloxMessageFrequencyTracker.kt receiver/ublox-m8/src/test/kotlin/org/rtkcollector/receiver/ublox/UbloxMessageFrequencyTrackerTest.kt receiver/ublox-m8/src/main/kotlin/org/rtkcollector/receiver/ublox/UbloxM8Driver.kt
git commit -m "Track u-blox receiver message frequencies"
```

---

### Task 6: Add Mock Provider Recording Policy And Publisher Adapter

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/profile/ProfileModels.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/profile/SettingsSetModels.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/profile/ActiveRecordingConfig.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileScreens.kt`
- Create: `app/src/main/kotlin/org/rtkcollector/app/mocklocation/MockLocationPublisher.kt`
- Create: `app/src/test/kotlin/org/rtkcollector/app/mocklocation/MockLocationPublisherTest.kt`

- [ ] **Step 1: Add mock publisher tests**

Create `app/src/test/kotlin/org/rtkcollector/app/mocklocation/MockLocationPublisherTest.kt`:

```kotlin
package org.rtkcollector.app.mocklocation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.rtkcollector.core.solution.BestSolutionSnapshot
import org.rtkcollector.core.solution.FixClass
import org.rtkcollector.core.solution.SolutionEngine

class MockLocationPublisherTest {
    @Test
    fun `publishes fresh snapshot when enabled`() {
        val sink = FakeMockLocationSink()
        val publisher = MockLocationPublisher(sink)
        val result = publisher.publish(snapshot(), enabled = true)

        assertEquals(MockLocationPublishResult.PUBLISHED, result)
        assertEquals(1, sink.locations.size)
    }

    @Test
    fun `does not publish when disabled`() {
        val sink = FakeMockLocationSink()
        val publisher = MockLocationPublisher(sink)
        val result = publisher.publish(snapshot(), enabled = false)

        assertEquals(MockLocationPublishResult.DISABLED, result)
        assertEquals(0, sink.locations.size)
    }

    private fun snapshot(): BestSolutionSnapshot =
        BestSolutionSnapshot(
            sourceId = "UBX-NAV-PVT",
            receiverFamily = "ublox-m8",
            engine = SolutionEngine.DEVICE_INTERNAL,
            fixClass = FixClass.SINGLE,
            updatedAtMillis = 1_000L,
            utcTime = null,
            latDeg = 50.0,
            lonDeg = 14.0,
            ellipsoidalHeightM = 300.0,
            mslAltitudeM = 250.0,
            horizontalAccuracyM = 1.2,
            verticalAccuracyM = 2.4,
            satellitesUsed = 12,
            satellitesInView = null,
            ageMillis = 500L,
        )
}
```

- [ ] **Step 2: Add fakeable mock publisher**

Create `app/src/main/kotlin/org/rtkcollector/app/mocklocation/MockLocationPublisher.kt`:

```kotlin
package org.rtkcollector.app.mocklocation

import org.rtkcollector.core.solution.BestSolutionSnapshot

data class MockLocationUpdate(
    val provider: String,
    val latDeg: Double,
    val lonDeg: Double,
    val altitudeM: Double?,
    val horizontalAccuracyM: Float?,
    val timeMillis: Long,
)

interface MockLocationSink {
    fun publish(update: MockLocationUpdate)
}

enum class MockLocationPublishResult {
    DISABLED,
    STALE,
    PUBLISHED,
    FAILED,
}

class MockLocationPublisher(
    private val sink: MockLocationSink,
) {
    fun publish(snapshot: BestSolutionSnapshot?, enabled: Boolean): MockLocationPublishResult {
        if (!enabled) return MockLocationPublishResult.DISABLED
        val current = snapshot ?: return MockLocationPublishResult.STALE
        if (!current.isFresh) return MockLocationPublishResult.STALE
        return runCatching {
            sink.publish(
                MockLocationUpdate(
                    provider = "gps",
                    latDeg = current.latDeg,
                    lonDeg = current.lonDeg,
                    altitudeM = current.mslAltitudeM ?: current.ellipsoidalHeightM,
                    horizontalAccuracyM = current.horizontalAccuracyM?.toFloat(),
                    timeMillis = current.updatedAtMillis,
                ),
            )
        }.fold(
            onSuccess = { MockLocationPublishResult.PUBLISHED },
            onFailure = { MockLocationPublishResult.FAILED },
        )
    }
}

class FakeMockLocationSink : MockLocationSink {
    val locations = mutableListOf<MockLocationUpdate>()
    override fun publish(update: MockLocationUpdate) {
        locations += update
    }
}
```

- [ ] **Step 3: Add recording policy fields**

Modify `RecordingPolicyProfile` in `ProfileModels.kt`:

```kotlin
val enableMockLocation: Boolean = false,
```

Add to `toJson()`:

```kotlin
.put("enableMockLocation", enableMockLocation)
```

Add to `fromJson()`:

```kotlin
enableMockLocation = json.optBoolean("enableMockLocation", false),
```

Modify `RecordingPolicyOverride` and `ActiveRecordingConfig.Recording` similarly:

```kotlin
val enableMockLocation: Boolean? = null
```

and:

```kotlin
enableMockLocation = recordingOverride?.enableMockLocation
    ?: recordingPolicyProfile.enableMockLocation
```

- [ ] **Step 4: Expose checkbox in recording policy editor**

Modify the recording-policy field list in `MainActivity.kt` or `ProfileScreens.kt` where `exportNmea` is displayed:

```kotlin
EditableProfileField("enableMockLocation", "Publish Android mock location while recording", profile.enableMockLocation.toString(), boolean = true)
```

Modify save parsing:

```kotlin
enableMockLocation = values.optional("enableMockLocation").toBooleanStrictOrFalse()
```

- [ ] **Step 5: Run compile**

Run:

```sh
sh gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```sh
git add app/src/main/kotlin/org/rtkcollector/app/profile/ProfileModels.kt app/src/main/kotlin/org/rtkcollector/app/profile/SettingsSetModels.kt app/src/main/kotlin/org/rtkcollector/app/profile/ActiveRecordingConfig.kt app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileScreens.kt app/src/main/kotlin/org/rtkcollector/app/mocklocation/MockLocationPublisher.kt app/src/test/kotlin/org/rtkcollector/app/mocklocation/MockLocationPublisherTest.kt
git commit -m "Add recording-scoped mock location option"
```

---

### Task 7: Integrate u-blox Parser And Best Solution Into Recording Service

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `receiver/ublox-m8/build.gradle.kts`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingServiceState.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardModels.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardServiceMapper.kt`

- [ ] **Step 1: Add module dependencies**

Modify `app/build.gradle.kts`:

```kotlin
implementation(project(":core:solution"))
implementation(project(":receiver:ublox-m8"))
```

Modify `receiver/ublox-m8/build.gradle.kts`:

```kotlin
implementation(project(":core:solution"))
```

- [ ] **Step 2: Extend service state**

Modify `RecordingServiceState.kt`:

```kotlin
val bestSolutionSource: String = "n/a",
val bestSolutionFix: String = "n/a",
val bestSolutionAgeMs: Long? = null,
val mockLocationState: String = "Disabled",
val ubloxFrequency: String = "Frequency RAWX/SFRBX/TM2/NAV-PVT/GGA -/-/-/-/- Hz",
```

- [ ] **Step 3: Compile u-blox scripts before sending**

In `RecordingForegroundService`, where command profile lines are translated to bytes, add receiver-family dispatch:

```kotlin
private fun compileReceiverCommands(receiverFamily: String, script: String): List<ReceiverCommand> =
    if (receiverFamily.startsWith("ublox", ignoreCase = true)) {
        UbloxScriptCompiler.compile(script)
    } else {
        script.lineSequence()
            .map(String::trim)
            .filter { it.isNotBlank() }
            .map { ReceiverCommand(label = it.take(40), payload = "$it\r\n".encodeToByteArray()) }
            .toList()
    }
```

Use this function before any command bytes are sent. If it throws for u-blox, set `RecordingErrorCategory.RECEIVER_COMMAND`, show the line-numbered error, and do not start sending partial command bytes.

- [ ] **Step 4: Feed u-blox parser from advisory parser path**

In `RecordingForegroundService`, add fields:

```kotlin
private val ubloxStreamParser = UbloxStreamParser()
private val ubloxFrequencyTracker = UbloxMessageFrequencyTracker()
private val solutionCandidates = mutableMapOf<String, SolutionCandidate>()
```

In the RX processing method after raw bytes are written:

```kotlin
private fun parseUbloxAdvisory(bytes: ByteArray, nowMillis: Long) {
    ubloxStreamParser.accept(bytes).forEach { record ->
        if (record.kind == "ubx") {
            when {
                record.bytes.getOrNull(2) == 0x01.toByte() && record.bytes.getOrNull(3) == 0x07.toByte() -> {
                    ubloxFrequencyTracker.record(UbloxMessageKind.NAV_PVT, nowMillis)
                    UbloxNavPvtParser.parse(record.bytes, nowMillis)?.toSolutionCandidate()?.let {
                        solutionCandidates[it.sourceId] = it
                    }
                }
                record.bytes.getOrNull(2) == 0x02.toByte() && record.bytes.getOrNull(3) == 0x15.toByte() ->
                    ubloxFrequencyTracker.record(UbloxMessageKind.RAWX, nowMillis)
                record.bytes.getOrNull(2) == 0x02.toByte() && record.bytes.getOrNull(3) == 0x13.toByte() ->
                    ubloxFrequencyTracker.record(UbloxMessageKind.SFRBX, nowMillis)
                record.bytes.getOrNull(2) == 0x0D.toByte() && record.bytes.getOrNull(3) == 0x03.toByte() ->
                    ubloxFrequencyTracker.record(UbloxMessageKind.TM2, nowMillis)
            }
        }
    }
    updateBestSolution(nowMillis)
}
```

Call this only when the selected receiver family starts with `ublox`; keep UM980 parsing path unchanged.

- [ ] **Step 5: Update best solution and mock state**

Add:

```kotlin
private fun updateBestSolution(nowMillis: Long) {
    val best = BestSolutionSelector.select(solutionCandidates.values, nowMillis)
    state = state.copy(
        bestSolutionSource = best?.sourceId ?: "n/a",
        bestSolutionFix = best?.fixClass?.name ?: "n/a",
        bestSolutionAgeMs = best?.ageMillis,
        latDeg = best?.latDeg ?: state.latDeg,
        lonDeg = best?.lonDeg ?: state.lonDeg,
        latLon = best?.let { "${it.latDeg}, ${it.lonDeg}" } ?: state.latLon,
        ellipsoidalHeight = best?.ellipsoidalHeightM?.let { formatMeters(it) } ?: state.ellipsoidalHeight,
        altitude = best?.mslAltitudeM?.let { formatMeters(it) } ?: state.altitude,
        horizontalAccuracy = best?.horizontalAccuracyM?.let { formatMeters(it) } ?: state.horizontalAccuracy,
        verticalAccuracy = best?.verticalAccuracyM?.let { formatMeters(it) } ?: state.verticalAccuracy,
        satellitesUsed = best?.satellitesUsed ?: state.satellitesUsed,
        ubloxFrequency = ubloxFrequencyTracker.display(nowMillis),
    )
}
```

Adapt names to the existing service-state mutation style. Do not introduce a second raw read loop.

- [ ] **Step 6: Map dashboard fields**

Modify `DashboardModels.FixCardState` to add:

```kotlin
val bestSolution: String = "n/a",
val mockLocation: String = "Disabled",
```

Modify `DashboardServiceMapper`:

```kotlin
bestSolution = "${intent.getStringExtra(EXTRA_STATE_BEST_SOLUTION_FIX) ?: "n/a"} from ${intent.getStringExtra(EXTRA_STATE_BEST_SOLUTION_SOURCE) ?: "n/a"}",
mockLocation = intent.getStringExtra(EXTRA_STATE_MOCK_LOCATION_STATE) ?: "Disabled",
```

Add extras in `RecordingForegroundService` broadcasts:

```kotlin
const val EXTRA_STATE_BEST_SOLUTION_SOURCE = "bestSolutionSource"
const val EXTRA_STATE_BEST_SOLUTION_FIX = "bestSolutionFix"
const val EXTRA_STATE_MOCK_LOCATION_STATE = "mockLocationState"
const val EXTRA_STATE_UBLOX_FREQUENCY = "ubloxFrequency"
```

- [ ] **Step 7: Run compile**

Run:

```sh
sh gradlew :receiver:ublox-m8:test :core:solution:test :app:compileDebugKotlin
```

Expected: all listed tasks complete successfully in Termux.

- [ ] **Step 8: Commit**

```sh
git add app/build.gradle.kts receiver/ublox-m8/build.gradle.kts app/src/main/kotlin/org/rtkcollector/app/recording/RecordingServiceState.kt app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardModels.kt app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardServiceMapper.kt
git commit -m "Feed u-blox solutions into dashboard state"
```

---

### Task 8: Add Android Mock Location Sink

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/mocklocation/MockLocationPublisher.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt`
- Test: `app/src/test/kotlin/org/rtkcollector/app/mocklocation/MockLocationPublisherTest.kt`

- [ ] **Step 1: Add Android sink abstraction**

Append to `MockLocationPublisher.kt`:

```kotlin
class AndroidMockLocationSink(
    private val locationManager: android.location.LocationManager,
    private val providerName: String = android.location.LocationManager.GPS_PROVIDER,
) : MockLocationSink {
    override fun publish(update: MockLocationUpdate) {
        val location = android.location.Location(providerName).apply {
            latitude = update.latDeg
            longitude = update.lonDeg
            update.altitudeM?.let { altitude = it }
            update.horizontalAccuracyM?.let { accuracy = it }
            time = update.timeMillis
            elapsedRealtimeNanos = android.os.SystemClock.elapsedRealtimeNanos()
        }
        locationManager.setTestProviderLocation(providerName, location)
    }
}
```

- [ ] **Step 2: Add mock-location permission**

Modify `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.ACCESS_MOCK_LOCATION" />
```

This is a development/mock-provider permission. Runtime behaviour still depends
on the user selecting RtkCollector as the mock-location app in Android developer
settings.

- [ ] **Step 3: Instantiate publisher in service**

In `RecordingForegroundService`, create the publisher only when the recording policy enables mock location:

```kotlin
private var mockLocationPublisher: MockLocationPublisher? = null

private fun configureMockLocation(enabled: Boolean) {
    mockLocationPublisher = if (enabled) {
        val manager = getSystemService(android.location.LocationManager::class.java)
        MockLocationPublisher(AndroidMockLocationSink(manager))
    } else {
        null
    }
}
```

Call `configureMockLocation(activeConfig.recording.enableMockLocation)` during recording start.

- [ ] **Step 4: Publish selected solution after update**

After `BestSolutionSelector.select(...)` in service:

```kotlin
val publishResult = mockLocationPublisher?.publish(best, enabled = activeConfig.recording.enableMockLocation)
    ?: MockLocationPublishResult.DISABLED
state = state.copy(mockLocationState = publishResult.name)
```

If result is `FAILED`, set a degraded parser/export error but continue recording:

```kotlin
if (publishResult == MockLocationPublishResult.FAILED) {
    state = state.copy(
        lastError = "Android mock-location update failed. Check Developer options mock-location app setting.",
        errorCategory = RecordingErrorCategory.PARSER_EXPORT,
        errorSeverity = RecordingErrorSeverity.DEGRADED,
    )
}
```

- [ ] **Step 5: Compile**

Run:

```sh
sh gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```sh
git add app/src/main/AndroidManifest.xml app/src/main/kotlin/org/rtkcollector/app/mocklocation/MockLocationPublisher.kt app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt app/src/test/kotlin/org/rtkcollector/app/mocklocation/MockLocationPublisherTest.kt
git commit -m "Publish best solution as Android mock location"
```

---

### Task 9: Update Documentation And Final Verification

**Files:**
- Modify: `docs/receiver-profiles.md`
- Modify: `docs/user-workflows.md`
- Modify: `docs/android-background-operation.md`
- Modify: `README.md`

- [ ] **Step 1: Update receiver profiles documentation**

Add to `docs/receiver-profiles.md` under u-blox M8T:

```markdown
V1 practical support starts with M8T raw/timing recording. Built-in profiles
enable UBX `RXM-RAWX`, `RXM-SFRBX`, `TIM-TM2` and, where selected, `NAV-PVT`
for live dashboard and mock-location output. M8T is not treated as an internal
RTK float/fix rover.
```

- [ ] **Step 2: Update user workflow documentation**

Add to `docs/user-workflows.md`:

```markdown
Android mock-location output is a recording-scoped option. When enabled, the
foreground recording service publishes the current best fresh RtkCollector
solution to Android's mock GPS provider. The app must be selected as the mock
location app in Android Developer options. Mock-provider failure degrades only
mock output; raw receiver recording continues.
```

- [ ] **Step 3: Update background-operation documentation**

Add to `docs/android-background-operation.md`:

```markdown
Mock-location publishing, when enabled, is owned by the same foreground service
as recording. It is not an Activity-owned feature and must stop when the
recording/session stops.
```

- [ ] **Step 4: Update README status**

Add one concise bullet in README development status or receiver target section:

```markdown
- Experimental u-blox M8T support configures UBX raw/timing output and can feed
  receiver-derived positions into Android mock location while recording.
```

- [ ] **Step 5: Run final Termux-safe validation**

Run:

```sh
git diff --check
sh gradlew :core:solution:test :receiver:ublox-m8:test :app:compileDebugKotlin
```

Expected:

- `git diff --check` passes.
- Pure module tests pass.
- `:app:compileDebugKotlin` passes.

Do not run `assembleDebug` or Android app unit tests in this Termux/aarch64 environment unless native Android resource tooling has changed.

- [ ] **Step 6: Request code review**

Use `superpowers:requesting-code-review` with this scope:

```text
Review u-blox M8T support and Android mock provider integration.

Focus:
- UBX script compiler safety and binary TX recording implications.
- M8T not being modelled as internal RTK.
- Raw capture remaining authoritative.
- BestSolutionSelector serving both dashboard and mock provider.
- Mock provider failures not stopping recording.
- Termux validation limitations documented.
```

- [ ] **Step 7: Commit docs**

```sh
git add docs/receiver-profiles.md docs/user-workflows.md docs/android-background-operation.md README.md
git commit -m "Document u-blox M8T and mock provider workflows"
```

---

## Self-Review

Spec coverage:

- UBX frame/checksum: Task 2.
- `!UBX ...` compiler: Task 2.
- M8T built-in profiles: Task 3.
- Byte-level UBX parser and NAV-PVT: Task 4.
- Frequency tracking: Task 5.
- Best solution model: Task 1.
- Dashboard/service use of best solution: Task 7.
- Mock provider during recording only: Tasks 6 and 8.
- M8P/F9P not overclaimed: Task 9 docs and Task 1/7 capability separation hooks.
- Error handling and tests: Tasks 2, 4, 6, 7, 8.
- Termux validation boundary: Task 9.

Known implementation risks:

- The `UbloxScriptCompiler` uses command-specific payload packers for `CFG-MSG`, `CFG-GNSS`, `CFG-NAV5` and `CFG-RATE`; reviewers must compare those packers against u-blox protocol documentation before hardware testing.
- Android mock-location behaviour needs real-device validation because Developer options and vendor Android builds differ.
- M8P/F9P support remains architectural reuse only until devices or sample captures are available.
