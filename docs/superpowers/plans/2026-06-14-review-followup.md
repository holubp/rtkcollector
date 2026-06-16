# Review Follow-up Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Address the post-merge reviewer findings on the u-blox M8T + mock-location branch: feed UM980 telemetry into the Best Solution selector, run the selector + Android mock-location publish on a 1 Hz tick, register/teardown the Android test provider, and clean up several smaller protocol / error-handling / thread-safety issues.

**Architecture:** Two-clock model. Per-chunk advisory consumers write `SolutionCandidate` entries (one per source) into a `ConcurrentHashMap`. A 1 Hz `ScheduledExecutorService` tick evicts stale entries, runs `BestSolutionSelector.select`, updates dashboard state, broadcasts, and publishes to the Android test provider at most once per change.

**Tech Stack:** Kotlin/JVM modules, Android foreground service, Android `LocationManager` test-provider API, `java.util.concurrent.{ConcurrentHashMap, Executors, ScheduledExecutorService}`, JUnit 5 tests.

**Spec:** `docs/superpowers/specs/2026-06-14-review-followup-design.md`.

---

## File Structure

Create:

- `receiver/unicore-n4/src/main/kotlin/org/rtkcollector/receiver/unicore/Um980SolutionAdapter.kt` — pure-Kotlin adapters from UM980 telemetry / NMEA GGA to `SolutionCandidate`.
- `receiver/unicore-n4/src/test/kotlin/org/rtkcollector/receiver/unicore/Um980SolutionAdapterTest.kt` — covers every documented `positionType` mapping plus null/`NONE` cases.
- `receiver/unicore-n4/src/test/kotlin/org/rtkcollector/receiver/unicore/NmeaGgaCandidateTest.kt` — covers GGA fix-quality → `FixClass` matrix.
- `app/src/main/kotlin/org/rtkcollector/app/recording/BestSolutionTickLogic.kt` — pure tick body returning a `BestSolutionTickOutput` (state delta + publish action). No Android imports.
- `app/src/test/kotlin/org/rtkcollector/app/recording/BestSolutionTickLogicTest.kt` — branch coverage for empty / fresh / stale / mock-disabled / no-change / FAILED debounce / NOT_PERMITTED.

Modify:

- `receiver/unicore-n4/build.gradle.kts` — add `implementation(project(":core:solution"))`.
- `core/solution/src/test/kotlin/org/rtkcollector/core/solution/BestSolutionSelectorTest.kt` — add future-timestamp, recency-tiebreak, custom-maxAge, SBAS-vs-DGPS-tiebreak cases (carry-overs).
- `receiver/ublox-m8/src/main/kotlin/org/rtkcollector/receiver/ublox/UbloxScriptCompiler.kt` — accept negative tokens; fix misleading error message.
- `receiver/ublox-m8/src/test/kotlin/org/rtkcollector/receiver/ublox/UbloxScriptCompilerTest.kt` — add byte-content assertion (CFG-RATE `1000 1 1` → `E8 03 01 00 01 00`) and negative-`i32` round-trip.
- `receiver/ublox-m8/src/main/kotlin/org/rtkcollector/receiver/ublox/UbloxMessageFrequencyTracker.kt` — `WINDOW_MILLIS = 1_000L`; header uses `UbloxMessageKind.label`.
- `receiver/ublox-m8/src/test/kotlin/org/rtkcollector/receiver/ublox/UbloxMessageFrequencyTrackerTest.kt` — update expected math for 1 s window.
- `receiver/ublox-m8/src/main/kotlin/org/rtkcollector/receiver/ublox/UbloxStreamParser.kt` — KDoc thread-safety note.
- `app/src/main/kotlin/org/rtkcollector/app/mocklocation/MockLocationPublisher.kt` — add `NOT_PERMITTED` enum value; add `lastFailure: Throwable?` field; drop `MockLocationUpdate.provider`; drop MSL→ellipsoidal fallback.
- `app/src/test/kotlin/org/rtkcollector/app/mocklocation/MockLocationPublisherTest.kt` — update for the removed `provider` field.
- `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt` — major service-side wiring (split across Tasks 8–11).

Validation:

- `./gradlew :core:solution:test :receiver:unicore-n4:test :receiver:ublox-m8:test` runs the new + existing pure-Kotlin tests on every host.
- `:app:test` + `:app:compileDebugKotlin` need an Android SDK host; document the deferral as a `DONE_WITH_CONCERNS` if running where the SDK is missing.
- No `assembleDebug` requirement.

---

### Task 1: Wire receiver:unicore-n4 → :core:solution and Backfill BestSolutionSelector Tests

**Files:**
- Modify: `receiver/unicore-n4/build.gradle.kts`
- Modify: `core/solution/src/test/kotlin/org/rtkcollector/core/solution/BestSolutionSelectorTest.kt`

- [ ] **Step 1: Add the core:solution dependency**

Edit `receiver/unicore-n4/build.gradle.kts`. The current file looks like:

```kotlin
plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":receiver:api"))
    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
```

Add a single line after the existing `:receiver:api` `implementation` line so the dependencies block reads:

```kotlin
plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":receiver:api"))
    implementation(project(":core:solution"))
    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
```

- [ ] **Step 2: Add the four new selector test cases**

Append the following tests to `core/solution/src/test/kotlin/org/rtkcollector/core/solution/BestSolutionSelectorTest.kt` inside the existing `BestSolutionSelectorTest` class (before the trailing `}`), keeping the existing `candidate(...)` helper as the constructor:

```kotlin
    @Test
    fun `ignores candidate dated in the future`() {
        val now = 10_000L
        val future = candidate("future", FixClass.RTK_FIXED, updatedAtMillis = now + 1_000)

        val selected = BestSolutionSelector.select(listOf(future), nowMillis = now)

        assertNull(selected)
    }

    @Test
    fun `prefers more recent candidate when rank and accuracy tie`() {
        val now = 10_000L
        val older = candidate(
            "older",
            FixClass.SINGLE,
            updatedAtMillis = now - 1_500,
            horizontalAccuracyM = 1.0,
        )
        val newer = candidate(
            "newer",
            FixClass.SINGLE,
            updatedAtMillis = now - 500,
            horizontalAccuracyM = 1.0,
        )

        val selected = BestSolutionSelector.select(listOf(older, newer), nowMillis = now)

        assertEquals("newer", selected?.sourceId)
    }

    @Test
    fun `respects custom maxAgeMillis`() {
        val now = 10_000L
        val recent = candidate("recent", FixClass.RTK_FIXED, updatedAtMillis = now - 3_000)

        val selectedDefault = BestSolutionSelector.select(listOf(recent), nowMillis = now)
        val selectedTight = BestSolutionSelector.select(listOf(recent), nowMillis = now, maxAgeMillis = 1_000)

        assertEquals("recent", selectedDefault?.sourceId)
        assertNull(selectedTight)
    }

    @Test
    fun `sbas and dgps share rank so accuracy breaks the tie`() {
        val now = 10_000L
        val sbas = candidate(
            "sbas",
            FixClass.SBAS,
            updatedAtMillis = now - 100,
            horizontalAccuracyM = 2.0,
        )
        val dgps = candidate(
            "dgps",
            FixClass.DGPS,
            updatedAtMillis = now - 100,
            horizontalAccuracyM = 0.4,
        )

        val selected = BestSolutionSelector.select(listOf(sbas, dgps), nowMillis = now)

        assertEquals("dgps", selected?.sourceId)
    }
```

- [ ] **Step 3: Run the selector tests**

Run:

```sh
export JAVA_HOME='/c/Program Files/Eclipse Adoptium/jdk-21.0.9.10-hotspot'
export GRADLE_OPTS='-Djavax.net.ssl.trustStoreType=Windows-ROOT'
./gradlew :core:solution:test :receiver:unicore-n4:test
```

Expected: `BUILD SUCCESSFUL`. The selector test now has 8 cases (the existing 4 plus the 4 new ones).

- [ ] **Step 4: Commit**

```sh
git add receiver/unicore-n4/build.gradle.kts core/solution/src/test/kotlin/org/rtkcollector/core/solution/BestSolutionSelectorTest.kt
git commit -m "Backfill BestSolutionSelector coverage and wire unicore-n4 to core:solution"
```

---

### Task 2: Add Um980SolutionAdapter

**Files:**
- Create: `receiver/unicore-n4/src/main/kotlin/org/rtkcollector/receiver/unicore/Um980SolutionAdapter.kt`
- Create: `receiver/unicore-n4/src/test/kotlin/org/rtkcollector/receiver/unicore/Um980SolutionAdapterTest.kt`

This adapter turns existing UM980 telemetry into `SolutionCandidate`s. `Um980Telemetry` (binary BESTNAV / PPP) and `Um980AsciiSolution` (ASCII BESTNAVA / PPPNAVA) live in `receiver/unicore-n4/.../Um980LiveParsers.kt` and `Um980Telemetry.kt`. Both share `positionType` strings that map to UM980 documentation.

- [ ] **Step 1: Add the failing adapter tests**

Create `receiver/unicore-n4/src/test/kotlin/org/rtkcollector/receiver/unicore/Um980SolutionAdapterTest.kt`:

```kotlin
package org.rtkcollector.receiver.unicore

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.rtkcollector.core.solution.FixClass
import org.rtkcollector.core.solution.SolutionEngine

class Um980SolutionAdapterTest {
    @Test
    fun `narrow int maps to RTK_FIXED on DEVICE_INTERNAL engine`() {
        val telemetry = bestnavTelemetry(positionType = "NARROW_INT")

        val candidate = telemetry.toBestnavCandidate(nowMillis = 7_000L)

        assertEquals(FixClass.RTK_FIXED, candidate?.fixClass)
        assertEquals(SolutionEngine.DEVICE_INTERNAL, candidate?.engine)
        assertEquals("UM980-BESTNAV", candidate?.sourceId)
        assertEquals("um980", candidate?.receiverFamily)
        assertEquals(7_000L, candidate?.updatedAtMillis)
    }

    @Test
    fun `narrow float maps to RTK_FLOAT`() {
        val candidate = bestnavTelemetry(positionType = "NARROW_FLOAT").toBestnavCandidate(0L)
        assertEquals(FixClass.RTK_FLOAT, candidate?.fixClass)
    }

    @Test
    fun `psrdiff maps to DGPS`() {
        val candidate = bestnavTelemetry(positionType = "PSRDIFF").toBestnavCandidate(0L)
        assertEquals(FixClass.DGPS, candidate?.fixClass)
    }

    @Test
    fun `single maps to SINGLE`() {
        val candidate = bestnavTelemetry(positionType = "SINGLE").toBestnavCandidate(0L)
        assertEquals(FixClass.SINGLE, candidate?.fixClass)
    }

    @Test
    fun `none position type returns null`() {
        val candidate = bestnavTelemetry(positionType = "NONE").toBestnavCandidate(0L)
        assertNull(candidate)
    }

    @Test
    fun `blank position type returns null`() {
        val candidate = bestnavTelemetry(positionType = null).toBestnavCandidate(0L)
        assertNull(candidate)
    }

    @Test
    fun `missing lat or lon returns null`() {
        val candidate = bestnavTelemetry(positionType = "SINGLE", latDeg = null)
            .toBestnavCandidate(0L)
        assertNull(candidate)
    }

    @Test
    fun `pppnav with PPP maps to PPP_CONVERGED on RECEIVER_PPP engine`() {
        val candidate = pppTelemetry(positionType = "PPP").toPppCandidate(0L)
        assertEquals(FixClass.PPP_CONVERGED, candidate?.fixClass)
        assertEquals(SolutionEngine.RECEIVER_PPP, candidate?.engine)
        assertEquals("UM980-PPP", candidate?.sourceId)
    }

    @Test
    fun `pppnav with PPP_CONVERGING maps to PPP_CONVERGING`() {
        val candidate = pppTelemetry(positionType = "PPP_CONVERGING").toPppCandidate(0L)
        assertEquals(FixClass.PPP_CONVERGING, candidate?.fixClass)
    }

    @Test
    fun `pppnav with non ppp type returns null`() {
        val candidate = pppTelemetry(positionType = "SINGLE").toPppCandidate(0L)
        assertNull(candidate)
    }

    @Test
    fun `ascii BESTNAV solution maps via positionType`() {
        val solution = Um980AsciiSolution(
            logName = "BESTNAVA",
            solutionStatus = "SOL_COMPUTED",
            positionType = "WIDE_INT",
            latDeg = 50.0,
            lonDeg = 14.0,
            heightM = 300.0,
        )

        val candidate = solution.toBestnavCandidate(0L)

        assertEquals(FixClass.RTK_FIXED, candidate?.fixClass)
        assertEquals(SolutionEngine.DEVICE_INTERNAL, candidate?.engine)
        assertEquals(300.0, candidate?.ellipsoidalHeightM)
    }

    private fun bestnavTelemetry(
        positionType: String?,
        latDeg: Double? = 50.0,
        lonDeg: Double? = 14.0,
    ): Um980Telemetry = Um980Telemetry(
        source = "BESTNAVB",
        positionType = positionType,
        latDeg = latDeg,
        lonDeg = lonDeg,
        altitudeM = 243.812,
        ellipsoidalHeightM = 287.423,
        latErrorM = 0.008,
        lonErrorM = 0.010,
        verticalAccuracyM = 0.015,
        satellitesUsed = 14,
        satellitesInView = 22,
    )

    private fun pppTelemetry(positionType: String?): Um980Telemetry = Um980Telemetry(
        source = "PPPNAVB",
        positionType = positionType,
        latDeg = 50.0,
        lonDeg = 14.0,
        altitudeM = 240.0,
        ellipsoidalHeightM = 290.0,
        latErrorM = 0.05,
        lonErrorM = 0.05,
        verticalAccuracyM = 0.1,
        satellitesUsed = 18,
    )
}
```

- [ ] **Step 2: Run the failing tests**

```sh
./gradlew :receiver:unicore-n4:test
```

Expected: compile errors `Unresolved reference 'toBestnavCandidate'` and `'toPppCandidate'`.

- [ ] **Step 3: Add the adapter implementation**

Create `receiver/unicore-n4/src/main/kotlin/org/rtkcollector/receiver/unicore/Um980SolutionAdapter.kt`:

```kotlin
package org.rtkcollector.receiver.unicore

import org.rtkcollector.core.solution.FixClass
import org.rtkcollector.core.solution.SolutionCandidate
import org.rtkcollector.core.solution.SolutionEngine

private const val UM980_FAMILY = "um980"

fun Um980Telemetry.toBestnavCandidate(nowMillis: Long): SolutionCandidate? {
    val lat = latDeg ?: return null
    val lon = lonDeg ?: return null
    val fix = bestnavFixClass(positionType) ?: return null
    val horizontal = horizontalAccuracyEstimateM()
    return SolutionCandidate(
        sourceId = "UM980-BESTNAV",
        receiverFamily = UM980_FAMILY,
        engine = SolutionEngine.DEVICE_INTERNAL,
        fixClass = fix,
        updatedAtMillis = nowMillis,
        utcTime = utcTime,
        latDeg = lat,
        lonDeg = lon,
        ellipsoidalHeightM = ellipsoidalHeightM,
        mslAltitudeM = altitudeM,
        horizontalAccuracyM = horizontal,
        verticalAccuracyM = verticalAccuracyM,
        satellitesUsed = satellitesUsed,
        satellitesInView = satellitesInView,
    )
}

fun Um980Telemetry.toPppCandidate(nowMillis: Long): SolutionCandidate? {
    val lat = latDeg ?: return null
    val lon = lonDeg ?: return null
    val fix = pppFixClass(positionType) ?: return null
    val horizontal = horizontalAccuracyEstimateM()
    return SolutionCandidate(
        sourceId = "UM980-PPP",
        receiverFamily = UM980_FAMILY,
        engine = SolutionEngine.RECEIVER_PPP,
        fixClass = fix,
        updatedAtMillis = nowMillis,
        utcTime = utcTime,
        latDeg = lat,
        lonDeg = lon,
        ellipsoidalHeightM = ellipsoidalHeightM,
        mslAltitudeM = altitudeM,
        horizontalAccuracyM = horizontal,
        verticalAccuracyM = verticalAccuracyM,
        satellitesUsed = satellitesUsed,
        satellitesInView = satellitesInView,
    )
}

fun Um980AsciiSolution.toBestnavCandidate(nowMillis: Long): SolutionCandidate? {
    val lat = latDeg ?: return null
    val lon = lonDeg ?: return null
    val fix = bestnavFixClass(positionType) ?: return null
    return SolutionCandidate(
        sourceId = "UM980-BESTNAV",
        receiverFamily = UM980_FAMILY,
        engine = SolutionEngine.DEVICE_INTERNAL,
        fixClass = fix,
        updatedAtMillis = nowMillis,
        latDeg = lat,
        lonDeg = lon,
        ellipsoidalHeightM = heightM,
        mslAltitudeM = null,
        horizontalAccuracyM = null,
        verticalAccuracyM = null,
    )
}

private fun bestnavFixClass(positionType: String?): FixClass? =
    when (positionType?.uppercase()) {
        null, "", "NONE" -> null
        "NARROW_INT", "WIDE_INT", "L1_INT", "INS_RTKFIXED" -> FixClass.RTK_FIXED
        "NARROW_FLOAT", "IONOFREE_FLOAT", "L1_FLOAT", "INS_RTKFLOAT" -> FixClass.RTK_FLOAT
        "PSRDIFF", "SBAS", "INS_PSRDIFF" -> FixClass.DGPS
        "SINGLE", "INS_PSRSP" -> FixClass.SINGLE
        "PPP" -> FixClass.PPP_CONVERGED
        "PPP_CONVERGING" -> FixClass.PPP_CONVERGING
        else -> FixClass.SINGLE
    }

private fun pppFixClass(positionType: String?): FixClass? =
    when (positionType?.uppercase()) {
        "PPP" -> FixClass.PPP_CONVERGED
        "PPP_CONVERGING" -> FixClass.PPP_CONVERGING
        else -> null
    }

private fun Um980Telemetry.horizontalAccuracyEstimateM(): Double? {
    val lat = latErrorM
    val lon = lonErrorM
    if (lat == null && lon == null) return null
    if (lat == null) return lon
    if (lon == null) return lat
    return kotlin.math.sqrt(lat * lat + lon * lon)
}
```

- [ ] **Step 4: Run the adapter tests**

```sh
./gradlew :receiver:unicore-n4:test
```

Expected: `BUILD SUCCESSFUL`. The new test class adds 11 passing test methods.

- [ ] **Step 5: Commit**

```sh
git add receiver/unicore-n4/src/main/kotlin/org/rtkcollector/receiver/unicore/Um980SolutionAdapter.kt \
        receiver/unicore-n4/src/test/kotlin/org/rtkcollector/receiver/unicore/Um980SolutionAdapterTest.kt
git commit -m "Add UM980 telemetry to SolutionCandidate adapters"
```

---

### Task 3: Add NmeaGgaFix.toCandidate

**Files:**
- Modify: `receiver/unicore-n4/src/main/kotlin/org/rtkcollector/receiver/unicore/Um980SolutionAdapter.kt`
- Create: `receiver/unicore-n4/src/test/kotlin/org/rtkcollector/receiver/unicore/NmeaGgaCandidateTest.kt`

`NmeaGgaFix` already lives in `Um980LiveParsers.kt` with fields `fixQuality: Int?`, `latDeg: Double?`, `lonDeg: Double?`, `altitudeM: Double?`, `geoidSeparationM: Double?`, `hdop: Double?`, `satelliteCount: Int?`, and a computed `ellipsoidalHeightM`. We add an extension that produces a GENERIC_NMEA candidate keyed by `"NMEA-GGA"`.

- [ ] **Step 1: Add the failing GGA candidate tests**

Create `receiver/unicore-n4/src/test/kotlin/org/rtkcollector/receiver/unicore/NmeaGgaCandidateTest.kt`:

```kotlin
package org.rtkcollector.receiver.unicore

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.rtkcollector.core.solution.FixClass
import org.rtkcollector.core.solution.SolutionEngine

class NmeaGgaCandidateTest {
    @Test
    fun `quality 0 returns null`() {
        assertNull(gga(quality = 0).toCandidate(receiverFamily = "um980", nowMillis = 0L))
    }

    @Test
    fun `quality 1 maps to SINGLE`() {
        assertEquals(FixClass.SINGLE, gga(quality = 1).toCandidate("um980", 0L)?.fixClass)
    }

    @Test
    fun `quality 2 maps to DGPS`() {
        assertEquals(FixClass.DGPS, gga(quality = 2).toCandidate("um980", 0L)?.fixClass)
    }

    @Test
    fun `quality 4 maps to RTK_FIXED`() {
        assertEquals(FixClass.RTK_FIXED, gga(quality = 4).toCandidate("um980", 0L)?.fixClass)
    }

    @Test
    fun `quality 5 maps to RTK_FLOAT`() {
        assertEquals(FixClass.RTK_FLOAT, gga(quality = 5).toCandidate("um980", 0L)?.fixClass)
    }

    @Test
    fun `quality 6 maps to PPP_CONVERGED`() {
        assertEquals(FixClass.PPP_CONVERGED, gga(quality = 6).toCandidate("um980", 0L)?.fixClass)
    }

    @Test
    fun `unknown quality maps to SINGLE`() {
        assertEquals(FixClass.SINGLE, gga(quality = 9).toCandidate("um980", 0L)?.fixClass)
    }

    @Test
    fun `missing lat or lon returns null`() {
        val fix = gga(quality = 1).copy(latDeg = null)
        assertNull(fix.toCandidate("um980", 0L))
    }

    @Test
    fun `engine is GENERIC_NMEA and source label is NMEA-GGA`() {
        val candidate = gga(quality = 1).toCandidate("ublox", 99L)

        assertEquals(SolutionEngine.GENERIC_NMEA, candidate?.engine)
        assertEquals("NMEA-GGA", candidate?.sourceId)
        assertEquals("ublox", candidate?.receiverFamily)
        assertEquals(99L, candidate?.updatedAtMillis)
    }

    private fun gga(quality: Int): NmeaGgaFix = NmeaGgaFix(
        talker = "GN",
        utcTime = "120000",
        latDeg = 50.0,
        lonDeg = 14.0,
        fixQuality = quality,
        satelliteCount = 12,
        hdop = 0.8,
        altitudeM = 300.0,
        geoidSeparationM = -42.0,
        differentialAgeS = null,
        stationId = null,
    )
}
```

- [ ] **Step 2: Run the failing tests**

```sh
./gradlew :receiver:unicore-n4:test
```

Expected: compile error `Unresolved reference 'toCandidate'`.

- [ ] **Step 3: Add the extension function**

Append to `receiver/unicore-n4/src/main/kotlin/org/rtkcollector/receiver/unicore/Um980SolutionAdapter.kt` (do not edit the existing functions):

```kotlin
fun NmeaGgaFix.toCandidate(receiverFamily: String, nowMillis: Long): SolutionCandidate? {
    val lat = latDeg ?: return null
    val lon = lonDeg ?: return null
    val fix = ggaFixClass(fixQuality) ?: return null
    return SolutionCandidate(
        sourceId = "NMEA-GGA",
        receiverFamily = receiverFamily,
        engine = SolutionEngine.GENERIC_NMEA,
        fixClass = fix,
        updatedAtMillis = nowMillis,
        utcTime = utcTime,
        latDeg = lat,
        lonDeg = lon,
        ellipsoidalHeightM = ellipsoidalHeightM,
        mslAltitudeM = altitudeM,
        horizontalAccuracyM = null,
        verticalAccuracyM = null,
        satellitesUsed = satelliteCount,
        satellitesInView = null,
    )
}

private fun ggaFixClass(quality: Int?): FixClass? =
    when (quality) {
        null, 0 -> null
        1 -> FixClass.SINGLE
        2 -> FixClass.DGPS
        4 -> FixClass.RTK_FIXED
        5 -> FixClass.RTK_FLOAT
        6 -> FixClass.PPP_CONVERGED
        else -> FixClass.SINGLE
    }
```

- [ ] **Step 4: Run the GGA tests**

```sh
./gradlew :receiver:unicore-n4:test
```

Expected: `BUILD SUCCESSFUL`. The new test class adds 9 passing test methods.

- [ ] **Step 5: Commit**

```sh
git add receiver/unicore-n4/src/main/kotlin/org/rtkcollector/receiver/unicore/Um980SolutionAdapter.kt \
        receiver/unicore-n4/src/test/kotlin/org/rtkcollector/receiver/unicore/NmeaGgaCandidateTest.kt
git commit -m "Add NMEA GGA fix to SolutionCandidate adapter"
```

---

### Task 4: UbloxScriptCompiler — Negatives, Byte-content Tests, Error Wording

**Files:**
- Modify: `receiver/ublox-m8/src/main/kotlin/org/rtkcollector/receiver/ublox/UbloxScriptCompiler.kt`
- Modify: `receiver/ublox-m8/src/test/kotlin/org/rtkcollector/receiver/ublox/UbloxScriptCompilerTest.kt`

- [ ] **Step 1: Add failing tests for negative int32 and byte-content**

In `receiver/ublox-m8/src/test/kotlin/org/rtkcollector/receiver/ublox/UbloxScriptCompilerTest.kt`, add three new tests immediately before the trailing `private fun org.rtkcollector.receiver.api.ReceiverCommand.payloadLength(): Int` declaration:

```kotlin
    @Test
    fun `compiles cfg rate command byte for byte`() {
        val commands = UbloxScriptCompiler.compile("!UBX CFG-RATE 1000 1 1")
        val frame = commands.single().payload

        // 0xB5 0x62 06 08 06 00 | E8 03 01 00 01 00 | ck_a=0x01 ck_b=0x39
        val expected = byteArrayOf(
            0xB5.toByte(), 0x62, 0x06, 0x08, 0x06, 0x00,
            0xE8.toByte(), 0x03, 0x01, 0x00, 0x01, 0x00,
            0x01, 0x39,
        )
        assertArrayEquals(expected, frame)
    }

    @Test
    fun `compiles cfg nav5 with negative minElev`() {
        val script = "!UBX CFG-NAV5 1 -5 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0"
        val commands = UbloxScriptCompiler.compile(script)
        val payload = commands.single().payload

        // The packer stores minElev as u8(arg2). -5 should round-trip as 0xFB
        // via two's-complement at the u8 layer (the packer takes Long and we
        // explicitly relax parseInteger).
        assertEquals(0xFB.toByte(), payload[6 + 2])
    }

    @Test
    fun `missing payload reports actionable line message`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            UbloxScriptCompiler.compile("!UBX CFG-RATE")
        }
        assertTrue(error.message!!.contains("line 1"))
        assertTrue(error.message!!.contains("payload"))
    }
```

You will need to also add `import org.junit.jupiter.api.Assertions.assertArrayEquals` at the top of the test file if it isn't already there.

- [ ] **Step 2: Run the test and confirm both new behaviour tests fail**

```sh
./gradlew :receiver:ublox-m8:test --tests org.rtkcollector.receiver.ublox.UbloxScriptCompilerTest
```

Expected: `compiles cfg nav5 with negative minElev` fails (`parseInteger` rejects negatives with "out of range"); `missing payload reports actionable line message` fails (current message says "missing command name"). The byte-content test should already pass — it pins existing behaviour.

Note for CFG-NAV5: the spec's intent is that `parseInteger` accepts negatives, and the per-field packer (`u8`/`i32` etc.) does its own range check. For `args[1]` (minElev, a `u8`) this means `-5` would still fail. Adjust the test before re-running if you'd rather assert on `args[3]` which is the int32 `fixedAlt`. The test below uses arg index 3 (the int32 field) to avoid that edge:

Replace the negative-test with:

```kotlin
    @Test
    fun `compiles cfg nav5 with negative fixedAlt int32`() {
        val script = "!UBX CFG-NAV5 1 3 0 -1 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0"
        val commands = UbloxScriptCompiler.compile(script)
        val payload = commands.single().payload

        // fixedAlt is the i32 at byte offset 4 of the payload (after u16 + u8 + u8)
        val fixedAltBytes = payload.copyOfRange(6 + 4, 6 + 8)
        assertArrayEquals(
            byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()),
            fixedAltBytes,
        )
    }
```

The byte offsets above reference the UBX frame layout: 2 sync + 2 class/id + 2 length = 6 header bytes, then payload byte 0 is `mask` (u16, 2 bytes), byte 2 is `dynModel` (u8), byte 3 is `fixMode` (u8), byte 4 starts the `fixedAlt` int32.

- [ ] **Step 3: Implement the compiler changes**

Edit `receiver/ublox-m8/src/main/kotlin/org/rtkcollector/receiver/ublox/UbloxScriptCompiler.kt`. Change the line:

```kotlin
    require(parts.size >= 3) { "Malformed !UBX line $lineNumber: missing command name." }
```

to:

```kotlin
    require(parts.size >= 2) { "Malformed !UBX line $lineNumber: missing command name." }
    require(parts.size >= 3) { "Malformed !UBX line $lineNumber: missing payload arguments." }
```

Change the `parseInteger` function:

```kotlin
    private fun parseInteger(lineNumber: Int, token: String): Long {
        val value = token.toLongOrNull()
            ?: throw IllegalArgumentException("Malformed !UBX payload on line $lineNumber: '$token' is not an integer.")
        require(value in 0..4_294_967_295L) { "Malformed !UBX payload on line $lineNumber: '$token' is out of range." }
        return value
    }
```

to:

```kotlin
    private fun parseInteger(lineNumber: Int, token: String): Long {
        val value = token.toLongOrNull()
            ?: throw IllegalArgumentException("Malformed !UBX payload on line $lineNumber: '$token' is not an integer.")
        require(value in Int.MIN_VALUE.toLong()..0xFFFF_FFFFL) {
            "Malformed !UBX payload on line $lineNumber: '$token' is out of range."
        }
        return value
    }
```

The per-field helpers (`u8`, `u16`, `u32`, `i32`) keep their existing range checks; `i32` continues to truncate to the low 32 bits via `value and 0xffff_ffffL`.

- [ ] **Step 4: Run the script compiler tests**

```sh
./gradlew :receiver:ublox-m8:test --tests org.rtkcollector.receiver.ublox.UbloxScriptCompilerTest
```

Expected: `BUILD SUCCESSFUL`. The test class now has 8 cases (5 existing + 3 new) and all pass.

- [ ] **Step 5: Commit**

```sh
git add receiver/ublox-m8/src/main/kotlin/org/rtkcollector/receiver/ublox/UbloxScriptCompiler.kt \
        receiver/ublox-m8/src/test/kotlin/org/rtkcollector/receiver/ublox/UbloxScriptCompilerTest.kt
git commit -m "Allow signed int32 tokens and assert script compiler byte content"
```

---

### Task 5: UbloxMessageFrequencyTracker — 1 s Window And Label-driven Header

**Files:**
- Modify: `receiver/ublox-m8/src/main/kotlin/org/rtkcollector/receiver/ublox/UbloxMessageFrequencyTracker.kt`
- Modify: `receiver/ublox-m8/src/test/kotlin/org/rtkcollector/receiver/ublox/UbloxMessageFrequencyTrackerTest.kt`

- [ ] **Step 1: Update the test for the new semantics**

Replace the existing single test in `receiver/ublox-m8/src/test/kotlin/org/rtkcollector/receiver/ublox/UbloxMessageFrequencyTrackerTest.kt` with:

```kotlin
package org.rtkcollector.receiver.ublox

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UbloxMessageFrequencyTrackerTest {
    @Test
    fun `formats compact frequency line for one second window`() {
        val tracker = UbloxMessageFrequencyTracker()
        tracker.record(UbloxMessageKind.RAWX, 2_100L)
        tracker.record(UbloxMessageKind.RAWX, 2_600L)
        tracker.record(UbloxMessageKind.NAV_PVT, 2_500L)

        assertEquals(
            "Frequency RAWX/SFRBX/TM2/NAV-PVT/GGA 2/-/-/1/- Hz",
            tracker.display(nowMillis = 3_000L),
        )
    }

    @Test
    fun `header is built from UbloxMessageKind labels`() {
        // Adding GGA support means the header is derived from enum labels.
        val expectedHeader = UbloxMessageKind.entries.joinToString("/") { it.label }
        val tracker = UbloxMessageFrequencyTracker()

        val line = tracker.display(nowMillis = 0L)

        assertEquals("Frequency $expectedHeader -/-/-/-/- Hz", line)
    }

    @Test
    fun `excludes samples at or beyond window boundary`() {
        val tracker = UbloxMessageFrequencyTracker()
        tracker.record(UbloxMessageKind.GGA, 1_000L)
        tracker.record(UbloxMessageKind.GGA, 2_000L)

        // window=1000; at nowMillis=3000 the only included GGA sample is the one
        // at 2000 (3000 - 2000 = 1000 is NOT < 1000). So GGA count is zero.
        assertEquals(
            "Frequency RAWX/SFRBX/TM2/NAV-PVT/GGA -/-/-/-/- Hz",
            tracker.display(nowMillis = 3_000L),
        )
    }
}
```

- [ ] **Step 2: Run the tests; confirm failures pin the new behaviour**

```sh
./gradlew :receiver:ublox-m8:test --tests org.rtkcollector.receiver.ublox.UbloxMessageFrequencyTrackerTest
```

Expected: the test class fails. The first test expects window=1000 ms so the existing window=1500 gives the wrong count; the header test expects a label-derived header.

- [ ] **Step 3: Update the tracker implementation**

Replace `receiver/ublox-m8/src/main/kotlin/org/rtkcollector/receiver/ublox/UbloxMessageFrequencyTracker.kt` with:

```kotlin
package org.rtkcollector.receiver.ublox

enum class UbloxMessageKind(val label: String) {
    RAWX("RAWX"),
    SFRBX("SFRBX"),
    TM2("TM2"),
    NAV_PVT("NAV-PVT"),
    GGA("GGA"),
}

/**
 * Tracks per-kind sample timestamps over a one-second sliding window and
 * formats them as a compact "X/Y/Z/A/B Hz" line for the dashboard.
 *
 * Not thread-safe. Confine to a single advisory-consumer thread or wrap
 * `record()` and `display()` in external synchronization.
 */
class UbloxMessageFrequencyTracker {
    private val recent = mutableMapOf<UbloxMessageKind, MutableList<Long>>()

    fun record(kind: UbloxMessageKind, timestampMillis: Long) {
        val values = recent.getOrPut(kind) { mutableListOf() }
        values += timestampMillis
        values.removeAll { timestampMillis - it > WINDOW_MILLIS }
    }

    fun display(nowMillis: Long): String {
        val header = UbloxMessageKind.entries.joinToString("/") { it.label }
        val values = UbloxMessageKind.entries.joinToString("/") { kind ->
            val count = recent[kind].orEmpty().count { nowMillis - it < WINDOW_MILLIS }
            if (count == 0) "-" else count.toString()
        }
        return "Frequency $header $values Hz"
    }

    private companion object {
        const val WINDOW_MILLIS = 1_000L
    }
}
```

- [ ] **Step 4: Run the tracker tests**

```sh
./gradlew :receiver:ublox-m8:test --tests org.rtkcollector.receiver.ublox.UbloxMessageFrequencyTrackerTest
```

Expected: `BUILD SUCCESSFUL`, 3 tests pass.

- [ ] **Step 5: Commit**

```sh
git add receiver/ublox-m8/src/main/kotlin/org/rtkcollector/receiver/ublox/UbloxMessageFrequencyTracker.kt \
        receiver/ublox-m8/src/test/kotlin/org/rtkcollector/receiver/ublox/UbloxMessageFrequencyTrackerTest.kt
git commit -m "Use 1s window and label-derived header in u-blox frequency tracker"
```

---

### Task 6: Thread-safety KDocs and MockLocationPublisher Changes

**Files:**
- Modify: `receiver/ublox-m8/src/main/kotlin/org/rtkcollector/receiver/ublox/UbloxStreamParser.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/mocklocation/MockLocationPublisher.kt`
- Modify: `app/src/test/kotlin/org/rtkcollector/app/mocklocation/MockLocationPublisherTest.kt`

- [ ] **Step 1: Add the UbloxStreamParser KDoc**

In `receiver/ublox-m8/src/main/kotlin/org/rtkcollector/receiver/ublox/UbloxStreamParser.kt`, replace `class UbloxStreamParser {` with the documented version:

```kotlin
/**
 * Incremental parser that frames mixed UBX, NMEA and noise bytes.
 *
 * Maintains an internal buffer of bytes spanning calls (`pending`). Not
 * thread-safe. Confine to a single advisory-consumer thread or wrap
 * `accept()` calls in external synchronization.
 */
class UbloxStreamParser {
```

Leave the rest of the file unchanged.

- [ ] **Step 2: Update MockLocationPublisherTest for the new API shape**

Open `app/src/test/kotlin/org/rtkcollector/app/mocklocation/MockLocationPublisherTest.kt`. The current test file constructs `BestSolutionSnapshot` with `sourceId = "UBX-NAV-PVT"`, etc. The publisher's `MockLocationUpdate` is about to drop its `provider` field; nothing in the test currently references that field, but a new test confirms the publisher records the throwable on FAILED.

Replace the entire file with:

```kotlin
package org.rtkcollector.app.mocklocation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
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
        assertNull(publisher.lastFailure)
    }

    @Test
    fun `does not publish when disabled`() {
        val sink = FakeMockLocationSink()
        val publisher = MockLocationPublisher(sink)

        val result = publisher.publish(snapshot(), enabled = false)

        assertEquals(MockLocationPublishResult.DISABLED, result)
        assertEquals(0, sink.locations.size)
    }

    @Test
    fun `returns STALE when snapshot is null`() {
        val publisher = MockLocationPublisher(FakeMockLocationSink())

        assertEquals(MockLocationPublishResult.STALE, publisher.publish(null, enabled = true))
    }

    @Test
    fun `returns STALE when snapshot is not fresh`() {
        val publisher = MockLocationPublisher(FakeMockLocationSink())
        val stale = snapshot().copy(ageMillis = 10_000L)

        assertEquals(MockLocationPublishResult.STALE, publisher.publish(stale, enabled = true))
    }

    @Test
    fun `records lastFailure when sink throws`() {
        val publisher = MockLocationPublisher(ThrowingMockLocationSink(IllegalStateException("boom")))

        val result = publisher.publish(snapshot(), enabled = true)

        assertEquals(MockLocationPublishResult.FAILED, result)
        assertNotNull(publisher.lastFailure)
        assertEquals("boom", publisher.lastFailure?.message)
    }

    @Test
    fun `omits altitude when mslAltitudeM is null`() {
        val sink = FakeMockLocationSink()
        val publisher = MockLocationPublisher(sink)
        val noMsl = snapshot().copy(mslAltitudeM = null, ellipsoidalHeightM = 300.0)

        publisher.publish(noMsl, enabled = true)

        assertNull(sink.locations.single().altitudeM)
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

    private class ThrowingMockLocationSink(private val error: Throwable) : MockLocationSink {
        override fun publish(update: MockLocationUpdate) {
            throw error
        }
    }
}
```

- [ ] **Step 3: Update MockLocationPublisher.kt**

Replace `app/src/main/kotlin/org/rtkcollector/app/mocklocation/MockLocationPublisher.kt` with:

```kotlin
package org.rtkcollector.app.mocklocation

import org.rtkcollector.core.solution.BestSolutionSnapshot

data class MockLocationUpdate(
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
    NOT_PERMITTED,
}

class MockLocationPublisher(
    private val sink: MockLocationSink,
) {
    var lastFailure: Throwable? = null
        private set

    fun publish(snapshot: BestSolutionSnapshot?, enabled: Boolean): MockLocationPublishResult {
        if (!enabled) return MockLocationPublishResult.DISABLED
        val current = snapshot ?: return MockLocationPublishResult.STALE
        if (!current.isFresh) return MockLocationPublishResult.STALE
        return runCatching {
            sink.publish(
                MockLocationUpdate(
                    latDeg = current.latDeg,
                    lonDeg = current.lonDeg,
                    altitudeM = current.mslAltitudeM,
                    horizontalAccuracyM = current.horizontalAccuracyM?.toFloat(),
                    timeMillis = current.updatedAtMillis,
                ),
            )
        }.fold(
            onSuccess = {
                lastFailure = null
                MockLocationPublishResult.PUBLISHED
            },
            onFailure = { error ->
                lastFailure = error
                MockLocationPublishResult.FAILED
            },
        )
    }
}

class FakeMockLocationSink : MockLocationSink {
    val locations = mutableListOf<MockLocationUpdate>()
    override fun publish(update: MockLocationUpdate) {
        locations += update
    }
}

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

Notable changes vs the pre-edit file:
- `MockLocationUpdate` no longer carries `provider: String` (it was dead).
- `MockLocationPublishResult` gains `NOT_PERMITTED`.
- `MockLocationPublisher` exposes `lastFailure` (read-only externally).
- The altitude path is strict MSL only (no `?: ellipsoidalHeightM`).

- [ ] **Step 4: Run the receiver and publisher tests; verify everything else compiles in isolation**

```sh
./gradlew :receiver:ublox-m8:test :core:solution:test :receiver:unicore-n4:test
```

Expected: `BUILD SUCCESSFUL` for all three. The `app` module won't compile yet because callers of `MockLocationUpdate.provider` still exist in `RecordingForegroundService`; that's expected and is fixed in Tasks 8–10. **Do NOT run `:app:compileDebugKotlin` for this task** — the service code still references the removed field and we don't fix that here.

- [ ] **Step 5: Commit**

```sh
git add receiver/ublox-m8/src/main/kotlin/org/rtkcollector/receiver/ublox/UbloxStreamParser.kt \
        app/src/main/kotlin/org/rtkcollector/app/mocklocation/MockLocationPublisher.kt \
        app/src/test/kotlin/org/rtkcollector/app/mocklocation/MockLocationPublisherTest.kt
git commit -m "Strip MockLocationUpdate.provider and surface publisher failure cause"
```

---

### Task 7: BestSolutionTickLogic — Pure Tick Body And Comprehensive Tests

**Files:**
- Create: `app/src/main/kotlin/org/rtkcollector/app/recording/BestSolutionTickLogic.kt`
- Create: `app/src/test/kotlin/org/rtkcollector/app/recording/BestSolutionTickLogicTest.kt`

This is the most important new test surface. The pure logic decides whether a publish should happen, debounces lastError on repeated FAILED, and computes the dashboard state delta. The Android-touching service code in later tasks delegates to this object.

- [ ] **Step 1: Add the failing tick logic tests**

Create `app/src/test/kotlin/org/rtkcollector/app/recording/BestSolutionTickLogicTest.kt`:

```kotlin
package org.rtkcollector.app.recording

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rtkcollector.app.mocklocation.MockLocationPublishResult
import org.rtkcollector.core.solution.FixClass
import org.rtkcollector.core.solution.SolutionCandidate
import org.rtkcollector.core.solution.SolutionEngine

class BestSolutionTickLogicTest {
    @Test
    fun `empty candidate set yields STALE and nothing to publish`() {
        val out = BestSolutionTickLogic.compute(
            input(candidates = emptyList(), mockEnabled = true),
        )

        assertEquals("n/a", out.stateDelta.bestSolutionSource)
        assertEquals("n/a", out.stateDelta.bestSolutionFix)
        assertNull(out.stateDelta.bestSolutionAgeMs)
        assertEquals(MockLocationPublishResult.STALE, out.stateDelta.mockResult)
        assertTrue(out.publishAction is PublishAction.None)
        assertNull(out.newLastMockPublishedAt)
        assertEquals(MockLocationPublishResult.STALE, out.newPreviousMockResult)
    }

    @Test
    fun `fresh candidate yields publish action when mock enabled`() {
        val candidate = candidate("UBX-NAV-PVT", FixClass.SINGLE, updatedAtMillis = 1_000L)

        val out = BestSolutionTickLogic.compute(
            input(candidates = listOf(candidate), nowMillis = 1_500L, mockEnabled = true),
        )

        assertEquals("UBX-NAV-PVT", out.stateDelta.bestSolutionSource)
        assertEquals("SINGLE", out.stateDelta.bestSolutionFix)
        assertEquals(500L, out.stateDelta.bestSolutionAgeMs)
        assertTrue(out.publishAction is PublishAction.Publish)
        assertEquals(1_000L, (out.publishAction as PublishAction.Publish).snapshot.updatedAtMillis)
    }

    @Test
    fun `mock disabled emits DISABLED without publish even with fresh candidate`() {
        val candidate = candidate("UM980-BESTNAV", FixClass.RTK_FIXED, updatedAtMillis = 1_000L)

        val out = BestSolutionTickLogic.compute(
            input(candidates = listOf(candidate), nowMillis = 1_500L, mockEnabled = false),
        )

        assertEquals(MockLocationPublishResult.DISABLED, out.stateDelta.mockResult)
        assertTrue(out.publishAction is PublishAction.None)
        assertNull(out.newLastMockPublishedAt)
    }

    @Test
    fun `same updatedAt as lastPublished does not republish`() {
        val candidate = candidate("UBX-NAV-PVT", FixClass.SINGLE, updatedAtMillis = 1_000L)

        val out = BestSolutionTickLogic.compute(
            input(
                candidates = listOf(candidate),
                nowMillis = 1_500L,
                mockEnabled = true,
                lastMockPublishedAt = 1_000L,
                previousMockResult = MockLocationPublishResult.PUBLISHED,
            ),
        )

        assertTrue(out.publishAction is PublishAction.None)
        assertEquals(MockLocationPublishResult.PUBLISHED, out.stateDelta.mockResult)
        assertEquals(1_000L, out.newLastMockPublishedAt)
    }

    @Test
    fun `transition into FAILED sets lastError once`() {
        val candidate = candidate("UBX-NAV-PVT", FixClass.SINGLE, updatedAtMillis = 1_500L)

        val out = BestSolutionTickLogic.applyPublishResult(
            previous = previousState(
                candidates = listOf(candidate),
                nowMillis = 2_000L,
                mockEnabled = true,
                lastMockPublishedAt = 1_000L,
                previousMockResult = MockLocationPublishResult.PUBLISHED,
            ),
            publishedResult = MockLocationPublishResult.FAILED,
            publishedAtMillis = null,
        )

        assertEquals(MockLocationPublishResult.FAILED, out.mockResult)
        assertEquals(true, out.setLastError)
    }

    @Test
    fun `repeated FAILED does not re-set lastError`() {
        val out = BestSolutionTickLogic.applyPublishResult(
            previous = previousState(
                candidates = emptyList(),
                nowMillis = 0L,
                mockEnabled = true,
                lastMockPublishedAt = null,
                previousMockResult = MockLocationPublishResult.FAILED,
            ),
            publishedResult = MockLocationPublishResult.FAILED,
            publishedAtMillis = null,
        )

        assertEquals(false, out.setLastError)
    }

    @Test
    fun `NOT_PERMITTED preserves enable state and skips publish`() {
        val candidate = candidate("UBX-NAV-PVT", FixClass.SINGLE, updatedAtMillis = 1_000L)
        val out = BestSolutionTickLogic.compute(
            input(
                candidates = listOf(candidate),
                nowMillis = 1_500L,
                mockEnabled = true,
                previousMockResult = MockLocationPublishResult.NOT_PERMITTED,
                mockProviderAvailable = false,
            ),
        )

        assertTrue(out.publishAction is PublishAction.None)
        assertEquals(MockLocationPublishResult.NOT_PERMITTED, out.stateDelta.mockResult)
    }

    @Test
    fun `selector ignores entries older than maxAgeMillis`() {
        val fresh = candidate("UBX-NAV-PVT", FixClass.SINGLE, updatedAtMillis = 4_900L)
        val stale = candidate("UM980-BESTNAV", FixClass.RTK_FIXED, updatedAtMillis = 0L)

        val out = BestSolutionTickLogic.compute(
            input(
                candidates = listOf(fresh, stale),
                nowMillis = 10_000L,
                mockEnabled = false,
            ),
        )

        // BestSolutionSelector filters stale candidates; the fresh one wins
        // even though it ranks lower than the stale RTK_FIXED.
        assertEquals("UBX-NAV-PVT", out.stateDelta.bestSolutionSource)
    }

    private fun input(
        candidates: Collection<SolutionCandidate>,
        nowMillis: Long = 1_000L,
        mockEnabled: Boolean,
        lastMockPublishedAt: Long? = null,
        previousMockResult: MockLocationPublishResult? = null,
        mockProviderAvailable: Boolean = true,
    ): BestSolutionTickInput = BestSolutionTickInput(
        candidates = candidates,
        nowMillis = nowMillis,
        mockEnabled = mockEnabled,
        mockProviderAvailable = mockProviderAvailable,
        lastMockPublishedAt = lastMockPublishedAt,
        previousMockResult = previousMockResult,
    )

    private fun previousState(
        candidates: Collection<SolutionCandidate>,
        nowMillis: Long,
        mockEnabled: Boolean,
        lastMockPublishedAt: Long?,
        previousMockResult: MockLocationPublishResult?,
    ): BestSolutionTickOutput = BestSolutionTickLogic.compute(
        input(candidates, nowMillis, mockEnabled, lastMockPublishedAt, previousMockResult),
    )

    private fun candidate(
        sourceId: String,
        fixClass: FixClass,
        updatedAtMillis: Long,
        horizontalAccuracyM: Double? = null,
    ): SolutionCandidate = SolutionCandidate(
        sourceId = sourceId,
        receiverFamily = "test",
        engine = SolutionEngine.DEVICE_INTERNAL,
        fixClass = fixClass,
        updatedAtMillis = updatedAtMillis,
        latDeg = 50.0,
        lonDeg = 14.0,
        ellipsoidalHeightM = 300.0,
        mslAltitudeM = 250.0,
        horizontalAccuracyM = horizontalAccuracyM,
        verticalAccuracyM = null,
        satellitesUsed = 12,
        satellitesInView = null,
    )
}
```

- [ ] **Step 2: Add the tick logic implementation**

Create `app/src/main/kotlin/org/rtkcollector/app/recording/BestSolutionTickLogic.kt`:

```kotlin
package org.rtkcollector.app.recording

import org.rtkcollector.app.mocklocation.MockLocationPublishResult
import org.rtkcollector.core.solution.BestSolutionSelector
import org.rtkcollector.core.solution.BestSolutionSnapshot
import org.rtkcollector.core.solution.SolutionCandidate

data class BestSolutionTickInput(
    val candidates: Collection<SolutionCandidate>,
    val nowMillis: Long,
    val mockEnabled: Boolean,
    val mockProviderAvailable: Boolean = true,
    val lastMockPublishedAt: Long? = null,
    val previousMockResult: MockLocationPublishResult? = null,
    val maxAgeMillis: Long = BestSolutionSelector.DEFAULT_MAX_AGE_MILLIS,
)

data class BestSolutionStateDelta(
    val bestSolutionSource: String,
    val bestSolutionFix: String,
    val bestSolutionAgeMs: Long?,
    val mockResult: MockLocationPublishResult,
)

sealed class PublishAction {
    data object None : PublishAction()
    data class Publish(val snapshot: BestSolutionSnapshot) : PublishAction()
}

data class BestSolutionTickOutput(
    val stateDelta: BestSolutionStateDelta,
    val publishAction: PublishAction,
    val newLastMockPublishedAt: Long?,
    val newPreviousMockResult: MockLocationPublishResult?,
)

data class PublishResultApplication(
    val mockResult: MockLocationPublishResult,
    val setLastError: Boolean,
    val newLastMockPublishedAt: Long?,
)

object BestSolutionTickLogic {
    fun compute(input: BestSolutionTickInput): BestSolutionTickOutput {
        val best = BestSolutionSelector.select(
            input.candidates,
            input.nowMillis,
            input.maxAgeMillis,
        )

        val mockResult = when {
            !input.mockEnabled -> MockLocationPublishResult.DISABLED
            !input.mockProviderAvailable -> MockLocationPublishResult.NOT_PERMITTED
            best == null -> MockLocationPublishResult.STALE
            best.updatedAtMillis == input.lastMockPublishedAt ->
                input.previousMockResult ?: MockLocationPublishResult.PUBLISHED
            else -> MockLocationPublishResult.PUBLISHED // tentative; caller updates via applyPublishResult
        }

        val publishAction: PublishAction = if (
            input.mockEnabled &&
            input.mockProviderAvailable &&
            best != null &&
            best.updatedAtMillis != input.lastMockPublishedAt
        ) {
            PublishAction.Publish(best)
        } else {
            PublishAction.None
        }

        val newLastPublishedAt = when (publishAction) {
            is PublishAction.Publish -> publishAction.snapshot.updatedAtMillis
            PublishAction.None -> input.lastMockPublishedAt
        }

        return BestSolutionTickOutput(
            stateDelta = BestSolutionStateDelta(
                bestSolutionSource = best?.sourceId ?: "n/a",
                bestSolutionFix = best?.fixClass?.name ?: "n/a",
                bestSolutionAgeMs = best?.ageMillis,
                mockResult = mockResult,
            ),
            publishAction = publishAction,
            newLastMockPublishedAt = newLastPublishedAt,
            newPreviousMockResult = mockResult,
        )
    }

    /**
     * Folds a publish result back into the tick output. Caller invokes the sink
     * with the snapshot from `publishAction`, then calls this with the result.
     */
    fun applyPublishResult(
        previous: BestSolutionTickOutput,
        publishedResult: MockLocationPublishResult,
        publishedAtMillis: Long?,
    ): PublishResultApplication {
        val transitionedIntoFailed =
            publishedResult == MockLocationPublishResult.FAILED &&
                previous.newPreviousMockResult != MockLocationPublishResult.FAILED
        val newLastPublishedAt = when (publishedResult) {
            MockLocationPublishResult.PUBLISHED -> publishedAtMillis
            else -> previous.newLastMockPublishedAt
        }
        return PublishResultApplication(
            mockResult = publishedResult,
            setLastError = transitionedIntoFailed,
            newLastMockPublishedAt = newLastPublishedAt,
        )
    }
}
```

- [ ] **Step 3: Verify the tests pass**

The host can't currently run `:app:test` because the app module's Android plugin needs an SDK. The tick logic is pure JVM Kotlin and has no Android dependencies, but the test classpath uses the app module's existing JUnit 5 setup. Try:

```sh
./gradlew :app:compileDebugKotlin
```

If this fails with "SDK location not found", record the failure and proceed. The tick-logic tests will be exercised when the user runs the suite on an Android-SDK host.

If `./gradlew :app:test` IS available (no SDK error), run:

```sh
./gradlew :app:test --tests org.rtkcollector.app.recording.BestSolutionTickLogicTest
```

Expected: `BUILD SUCCESSFUL`, 8 tests pass.

- [ ] **Step 4: Commit**

```sh
git add app/src/main/kotlin/org/rtkcollector/app/recording/BestSolutionTickLogic.kt \
        app/src/test/kotlin/org/rtkcollector/app/recording/BestSolutionTickLogicTest.kt
git commit -m "Extract pure BestSolutionTickLogic for selector + mock publish gating"
```

---

### Task 8: Service Wiring — Tick Executor, ConcurrentHashMap, @Volatile, runBestSolutionTick

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt`

This task replaces the per-advisory-chunk `updateBestSolution(System.currentTimeMillis())` call with a 1 Hz scheduled tick that delegates to `BestSolutionTickLogic`. It also tightens the existing concurrency hazards (mutable map → `ConcurrentHashMap`; `activeReceiverFamily` → `@Volatile`).

Steps modify the existing file in place. The implementer should grep for each anchor before editing because line numbers may shift between tasks. Anchors below use the symbols that exist in the file.

- [ ] **Step 1: Replace the `solutionCandidates` field declaration**

Find:

```kotlin
    private val solutionCandidates = mutableMapOf<String, org.rtkcollector.core.solution.SolutionCandidate>()
```

Replace with:

```kotlin
    private val solutionCandidates =
        java.util.concurrent.ConcurrentHashMap<String, org.rtkcollector.core.solution.SolutionCandidate>()
```

- [ ] **Step 2: Mark `activeReceiverFamily` volatile**

Find:

```kotlin
    private var activeReceiverFamily: String = ""
```

Replace with:

```kotlin
    @Volatile
    private var activeReceiverFamily: String = ""
```

- [ ] **Step 3: Add the ticker executor, last-published, and previous-result fields**

Add the following fields next to `private var activeReceiverFamily` (use the same indentation as surrounding `private var` fields):

```kotlin
    private val bestSolutionExecutor: java.util.concurrent.ScheduledExecutorService =
        java.util.concurrent.Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "rtkcollector-best").apply { isDaemon = true }
        }
    private var bestSolutionTicker: java.util.concurrent.ScheduledFuture<*>? = null
    private var lastMockPublishedAt: Long? = null
    private var previousMockResult: org.rtkcollector.app.mocklocation.MockLocationPublishResult? = null
    private var mockLocationRequested: Boolean = false
```

- [ ] **Step 4: Add `runBestSolutionTick`**

Add this private method anywhere in the class (next to the existing `updateBestSolution` / `parseUbloxAdvisory` methods is the natural spot):

```kotlin
    private fun runBestSolutionTick() {
        runCatching {
            val now = System.currentTimeMillis()
            val ageLimit = org.rtkcollector.core.solution.BestSolutionSelector.DEFAULT_MAX_AGE_MILLIS
            // Evict stale entries proactively so the map stays bounded.
            solutionCandidates.entries.removeIf { entry ->
                now - entry.value.updatedAtMillis > ageLimit
            }
            val tickInput = BestSolutionTickInput(
                candidates = solutionCandidates.values,
                nowMillis = now,
                mockEnabled = mockLocationRequested,
                mockProviderAvailable = mockLocationPublisher != null,
                lastMockPublishedAt = lastMockPublishedAt,
                previousMockResult = previousMockResult,
            )
            val tick = BestSolutionTickLogic.compute(tickInput)

            val best = (tick.publishAction as? PublishAction.Publish)?.snapshot
            // Update derived dashboard fields up front so the UI sees them
            // even if the publish attempt below fails.
            applyTickStateDelta(tick.stateDelta, best, now)

            when (val action = tick.publishAction) {
                PublishAction.None -> {
                    lastMockPublishedAt = tick.newLastMockPublishedAt
                    previousMockResult = tick.newPreviousMockResult
                }
                is PublishAction.Publish -> {
                    val publisher = mockLocationPublisher
                    if (publisher == null) {
                        previousMockResult = org.rtkcollector.app.mocklocation.MockLocationPublishResult.NOT_PERMITTED
                        state = state.copy(mockLocationState = previousMockResult!!.name)
                    } else {
                        val published = publisher.publish(action.snapshot, enabled = true)
                        val applied = BestSolutionTickLogic.applyPublishResult(
                            previous = tick,
                            publishedResult = published,
                            publishedAtMillis = action.snapshot.updatedAtMillis,
                        )
                        lastMockPublishedAt = applied.newLastMockPublishedAt
                        previousMockResult = published
                        state = state.copy(mockLocationState = published.name)
                        if (applied.setLastError) {
                            state = state.copy(
                                lastError = "Android mock-location update failed. " +
                                    "Check Developer options mock-location app setting.",
                                errorCategory = RecordingErrorCategory.PARSER_EXPORT,
                                errorSeverity = RecordingErrorSeverity.DEGRADED,
                            )
                        }
                    }
                }
            }
            broadcastState()
        }
    }

    private fun applyTickStateDelta(
        delta: BestSolutionStateDelta,
        best: org.rtkcollector.core.solution.BestSolutionSnapshot?,
        nowMillis: Long,
    ) {
        state = state.copy(
            bestSolutionSource = delta.bestSolutionSource,
            bestSolutionFix = delta.bestSolutionFix,
            bestSolutionAgeMs = delta.bestSolutionAgeMs,
            mockLocationState = delta.mockResult.name,
            latDeg = best?.latDeg ?: state.latDeg,
            lonDeg = best?.lonDeg ?: state.lonDeg,
            latLon = best?.let { latLonDisplay(it.latDeg, it.lonDeg) } ?: state.latLon,
            ellipsoidalHeight = best?.ellipsoidalHeightM?.let(::metersDisplay) ?: state.ellipsoidalHeight,
            altitude = best?.mslAltitudeM?.let(::metersDisplay) ?: state.altitude,
            horizontalAccuracy = best?.horizontalAccuracyM?.let(::metersDisplay) ?: state.horizontalAccuracy,
            verticalAccuracy = best?.verticalAccuracyM?.let(::metersDisplay) ?: state.verticalAccuracy,
            satellitesUsed = best?.satellitesUsed ?: state.satellitesUsed,
            ubloxFrequency = ubloxFrequencyTracker.display(nowMillis),
        )
    }
```

- [ ] **Step 5: Remove the per-advisory `updateBestSolution` call**

Find inside `parseUbloxAdvisory`:

```kotlin
        updateBestSolution(nowMillis)
```

Delete the entire line. The tick now owns selector + publish responsibilities.

Delete the old `private fun updateBestSolution(nowMillis: Long)` method entirely (it's no longer called). Be careful: the new `runBestSolutionTick` above intentionally has a similar name pattern; do not delete *that* one.

- [ ] **Step 6: Start and stop the tick around recording**

Find the existing `intent.getBooleanExtra(EXTRA_ENABLE_MOCK_LOCATION, false)` read in `startRecording`. The current code calls `configureMockLocation(enableMockLocation)` immediately after. Replace the two-line sequence:

```kotlin
            val enableMockLocation = intent.getBooleanExtra(EXTRA_ENABLE_MOCK_LOCATION, false)
            configureMockLocation(enableMockLocation)
```

with:

```kotlin
            val enableMockLocation = intent.getBooleanExtra(EXTRA_ENABLE_MOCK_LOCATION, false)
            mockLocationRequested = enableMockLocation
            configureMockLocation(enableMockLocation)
```

Then, just after the final `broadcastState()` that confirms `running = true`, add:

```kotlin
        lastMockPublishedAt = null
        previousMockResult = null
        bestSolutionTicker = bestSolutionExecutor.scheduleAtFixedRate(
            { runBestSolutionTick() },
            1, 1, java.util.concurrent.TimeUnit.SECONDS,
        )
```

Inside `stopRecording`, immediately before the existing `runCatching { ntripController?.stop() }` line, add:

```kotlin
        bestSolutionTicker?.cancel(false)
        bestSolutionTicker = null
```

Inside the same `stopRecording`, in the same block that already clears `solutionCandidates` and resets `bestSolutionSource`/etc, add resets for the two new fields. Find the existing block:

```kotlin
        solutionCandidates.clear()
        releaseWakeLock()
        state = state.copy(
            ...
            mockLocationState = "Disabled",
            ubloxFrequency = "Frequency RAWX/SFRBX/TM2/NAV-PVT/GGA -/-/-/-/- Hz",
        )
```

Replace with:

```kotlin
        solutionCandidates.clear()
        lastMockPublishedAt = null
        previousMockResult = null
        mockLocationRequested = false
        releaseWakeLock()
        state = state.copy(
            ...
            mockLocationState = "Disabled",
            ubloxFrequency = "Frequency RAWX/SFRBX/TM2/NAV-PVT/GGA -/-/-/-/- Hz",
        )
```

(Keep the `...` block exactly as it is in your tree; only add the two new lines.)

- [ ] **Step 7: Shut the executor down in `onDestroy`**

Find the existing `override fun onDestroy()` (if absent, add it). Insert:

```kotlin
    override fun onDestroy() {
        bestSolutionTicker?.cancel(false)
        bestSolutionTicker = null
        bestSolutionExecutor.shutdownNow()
        super.onDestroy()
    }
```

If an `onDestroy` already exists with other cleanup, add only the three new lines (cancel ticker, set to null, shutdown executor) before the existing `super.onDestroy()` call.

- [ ] **Step 8: Run pure-Kotlin regression tests**

```sh
./gradlew :core:solution:test :receiver:unicore-n4:test :receiver:ublox-m8:test
```

Expected: `BUILD SUCCESSFUL`. App-side compile will fail (still references the removed publisher fields and pre-tick code) — that's expected; Task 10 + Task 11 finish the wiring.

- [ ] **Step 9: Commit**

```sh
git add app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt
git commit -m "Run best-solution selector and mock publish on a 1 Hz ticker"
```

---

### Task 9: Service Wiring — Android Test Provider Registration

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt`

This task makes the mock-location feature actually work on a real device by registering the GPS test provider when `configureMockLocation(true)` is called and removing it on stop.

- [ ] **Step 1: Replace `configureMockLocation`**

Find:

```kotlin
    private fun configureMockLocation(enabled: Boolean) {
        mockLocationPublisher = if (enabled) {
            val manager = getSystemService(android.location.LocationManager::class.java)
            if (manager == null) {
                null
            } else {
                MockLocationPublisher(AndroidMockLocationSink(manager))
            }
        } else {
            null
        }
    }
```

Replace with:

```kotlin
    private fun configureMockLocation(enabled: Boolean) {
        if (!enabled) {
            teardownMockLocation()
            return
        }
        val manager = getSystemService(android.location.LocationManager::class.java)
        if (manager == null) {
            mockLocationPublisher = null
            state = state.copy(
                mockLocationState =
                    org.rtkcollector.app.mocklocation.MockLocationPublishResult.NOT_PERMITTED.name,
            )
            return
        }
        val outcome = runCatching {
            manager.addTestProvider(
                android.location.LocationManager.GPS_PROVIDER,
                /* requiresNetwork = */ false,
                /* requiresSatellite = */ false,
                /* requiresCell = */ false,
                /* hasMonetaryCost = */ false,
                /* supportsAltitude = */ true,
                /* supportsSpeed = */ true,
                /* supportsBearing = */ true,
                android.location.provider.ProviderProperties.POWER_USAGE_LOW,
                android.location.provider.ProviderProperties.ACCURACY_FINE,
            )
            manager.setTestProviderEnabled(
                android.location.LocationManager.GPS_PROVIDER,
                true,
            )
        }
        if (outcome.isFailure) {
            mockLocationPublisher = null
            previousMockResult =
                org.rtkcollector.app.mocklocation.MockLocationPublishResult.NOT_PERMITTED
            state = state.copy(
                mockLocationState = previousMockResult!!.name,
                lastError = "RtkCollector is not the selected mock location app. " +
                    "Enable it in Developer Options.",
                errorCategory = RecordingErrorCategory.PARSER_EXPORT,
                errorSeverity = RecordingErrorSeverity.DEGRADED,
            )
            return
        }
        mockLocationPublisher = org.rtkcollector.app.mocklocation.MockLocationPublisher(
            org.rtkcollector.app.mocklocation.AndroidMockLocationSink(
                manager,
                android.location.LocationManager.GPS_PROVIDER,
            ),
        )
    }

    private fun teardownMockLocation() {
        if (mockLocationPublisher == null) return
        mockLocationPublisher = null
        runCatching {
            val manager = getSystemService(android.location.LocationManager::class.java) ?: return
            manager.setTestProviderEnabled(
                android.location.LocationManager.GPS_PROVIDER,
                false,
            )
            manager.removeTestProvider(android.location.LocationManager.GPS_PROVIDER)
        }
    }
```

- [ ] **Step 2: Call `teardownMockLocation()` from `stopRecording`**

Find the existing `mockLocationPublisher = null` line in `stopRecording`. Replace it with:

```kotlin
        teardownMockLocation()
```

- [ ] **Step 3: Run pure-Kotlin regression tests**

```sh
./gradlew :core:solution:test :receiver:unicore-n4:test :receiver:ublox-m8:test
```

Expected: `BUILD SUCCESSFUL`. App-side compile is still expected to fail.

- [ ] **Step 4: Commit**

```sh
git add app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt
git commit -m "Register and tear down GPS test provider with the recording session"
```

---

### Task 10: Service Wiring — compileReceiverCommands Phase + Charset

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt`

This task makes shutdown-script compile failures DEGRADED instead of FATAL, broadens the catch to `Exception`, and uses an explicit `US_ASCII` charset in the non-UBX branch.

- [ ] **Step 1: Add the CompilePhase enum**

Add this declaration just inside the `class RecordingForegroundService` opening brace (top of the class body):

```kotlin
    private enum class CompilePhase { START, STOP }
```

- [ ] **Step 2: Update `compileReceiverCommands` signature and body**

Find the current `compileReceiverCommands(receiverFamily: String, script: String): List<ReceiverCommand>` implementation (it currently catches `IllegalArgumentException` only and signals errors). Replace it with:

```kotlin
    private fun compileReceiverCommands(
        phase: CompilePhase,
        receiverFamily: String,
        script: String,
    ): List<org.rtkcollector.receiver.api.ReceiverCommand> {
        return try {
            if (receiverFamily.startsWith("ublox", ignoreCase = true)) {
                org.rtkcollector.receiver.ublox.UbloxScriptCompiler.compile(script)
            } else {
                script.lineSequence()
                    .map(String::trim)
                    .filter { it.isNotBlank() }
                    .map {
                        org.rtkcollector.receiver.api.ReceiverCommand(
                            label = it.take(40),
                            payload = "$it\r\n".toByteArray(Charsets.US_ASCII),
                        )
                    }
                    .toList()
            }
        } catch (error: Exception) {
            val severity = when (phase) {
                CompilePhase.START -> RecordingErrorSeverity.FATAL
                CompilePhase.STOP -> RecordingErrorSeverity.DEGRADED
            }
            state = state.copy(
                lastError = error.message ?: error.javaClass.simpleName,
                errorCategory = RecordingErrorCategory.RECEIVER_COMMAND,
                errorSeverity = severity,
            )
            broadcastState()
            if (phase == CompilePhase.START) {
                throw error
            }
            emptyList()
        }
    }
```

Key behaviour: START failures re-throw (existing call sites already abort on the throw); STOP failures return an empty list so the shutdown path continues cleanly without partial command bytes.

- [ ] **Step 3: Update both call sites**

Find every call to `compileReceiverCommands(activeReceiverFamily, ...)`. There should be exactly two:

1. Inside `sendCommandLines(captureRuntime, commands)` — used for init + mode + baud-switch + shutdown commands. Replace `compileReceiverCommands(activeReceiverFamily, script)` with `compileReceiverCommands(phase, activeReceiverFamily, script)` and propagate the new `phase` parameter into `sendCommandLines`.

Change `sendCommandLines` signature from:

```kotlin
    private fun sendCommandLines(captureRuntime: CaptureRuntime, commands: List<String>) {
```

to:

```kotlin
    private fun sendCommandLines(
        captureRuntime: CaptureRuntime,
        commands: List<String>,
        phase: CompilePhase = CompilePhase.START,
    ) {
```

…and forward `phase` to the compile call.

Every existing call to `sendCommandLines(captureRuntime, …)` keeps its current default (START) except the call inside the shutdown block. Find the shutdown call (the one inside `if (sendShutdown && shutdownSent.compareAndSet(false, true))`) and change it from:

```kotlin
                    sendCommandLines(captureRuntime, shutdownCommands)
```

to:

```kotlin
                    sendCommandLines(captureRuntime, shutdownCommands, CompilePhase.STOP)
```

2. The other call site, if any, lives in `executeBaudTransition` via `Um980BaudStep.SendCommands`. The default `phase = CompilePhase.START` is correct there — leave the call unchanged.

- [ ] **Step 4: Run pure-Kotlin regression tests**

```sh
./gradlew :core:solution:test :receiver:unicore-n4:test :receiver:ublox-m8:test
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```sh
git add app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt
git commit -m "Distinguish start vs stop compile failures and pin US_ASCII"
```

---

### Task 11: Service Wiring — UM980 Adapter Writes + u-blox NMEA Routing

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt`

This task is the final service edit. It plugs the UM980 telemetry → `solutionCandidates` writes into the existing UM980 advisory consumers and routes u-blox NMEA records through the existing `NmeaGgaParser` so GGA candidates work for both families.

- [ ] **Step 1: Add the necessary imports**

Add (alongside the existing imports):

```kotlin
import org.rtkcollector.receiver.unicore.NmeaGgaFix
import org.rtkcollector.receiver.unicore.NmeaGgaParser
import org.rtkcollector.receiver.unicore.toBestnavCandidate
import org.rtkcollector.receiver.unicore.toCandidate
import org.rtkcollector.receiver.unicore.toPppCandidate
```

(`NmeaGgaParser` may already be imported.)

- [ ] **Step 2: Add a shared GGA parser field**

Add next to the other parser fields (e.g. near `private val ubloxStreamParser`):

```kotlin
    private var sharedNmeaGgaParser: NmeaGgaParser = NmeaGgaParser()
```

Reset it in `startRecording` next to the existing `ubloxStreamParser = UbloxStreamParser()` line:

```kotlin
        sharedNmeaGgaParser = NmeaGgaParser()
```

- [ ] **Step 3: Route u-blox NMEA records into the parser and record GGA frequency**

Find the existing `parseUbloxAdvisory` function. Inside its `forEach { record -> ... }` block, locate the `if (record.kind == "ubx") { ... }` branch. Add a sibling block immediately AFTER the ubx branch:

```kotlin
            if (record.kind == "nmea") {
                val fixes = runCatching { sharedNmeaGgaParser.accept(record.bytes) }
                    .getOrDefault(emptyList())
                fixes.forEach { fix ->
                    ubloxFrequencyTracker.record(UbloxMessageKind.GGA, nowMillis)
                    fix.toCandidate("ublox", nowMillis)?.let {
                        solutionCandidates[it.sourceId] = it
                    }
                }
            }
```

(The `runCatching` insulates raw recording from any parser error.)

- [ ] **Step 4: Wire the UM980 advisory consumers**

Before editing, **read** the existing `buildAdvisoryFanout` method in `RecordingForegroundService.kt` and locate the two `AdvisoryConsumer(...)` blocks named for the UM980 path (search for the literal strings `"um980-ascii"` / `"um980_ascii"` / `"unicore_ascii"` and the matching binary one — the exact id depends on the existing service file). Note the local variable names used for the parsed telemetry — they may already be `solution` / `fix` / `bestnav` etc., or differ.

Inside each consumer, after the existing parser calls that produce
`Um980Telemetry` / `Um980AsciiSolution` / `NmeaGgaFix` instances, write
candidates into `solutionCandidates`. Reuse whatever local variable names
already exist; only ADD candidate-write lines.

For the **ascii** consumer, find the existing code shape:

```kotlin
        AdvisoryConsumer("um980-ascii") { bytes ->
            val solutions = um980AsciiSolutionParser.accept(bytes)
            solutions.forEach { solution ->
                // (existing dashboard / telemetry updates)
            }
            val fixes = um980NmeaGgaParser.accept(bytes)
            fixes.forEach { fix ->
                // (existing dashboard updates from GGA)
            }
            ...
        }
```

Inside each `forEach`, append a candidate write. Replace the `solutions.forEach { solution -> ... }` body's existing block with the augmented version that ALSO writes a candidate:

```kotlin
            solutions.forEach { solution ->
                // ...keep all existing dashboard/state mutations exactly as they are...
                val now = System.currentTimeMillis()
                solution.toBestnavCandidate(now)?.let {
                    solutionCandidates[it.sourceId] = it
                }
            }
            fixes.forEach { fix ->
                // ...keep all existing dashboard updates...
                val now = System.currentTimeMillis()
                fix.toCandidate("um980", now)?.let {
                    solutionCandidates[it.sourceId] = it
                }
            }
```

For the **binary** consumer (find the `AdvisoryConsumer("um980-binary") { bytes -> ... }` block):

```kotlin
        AdvisoryConsumer("um980-binary") { bytes ->
            val bestnav = um980BinaryParser.parseBestnavb(bytes)
            // (existing dashboard updates)
            val pppnav = um980BinaryParser.parsePppnavb(bytes)
            // (existing dashboard updates)
            ...
        }
```

After the existing dashboard updates, append candidate writes:

```kotlin
            val now = System.currentTimeMillis()
            bestnav?.toBestnavCandidate(now)?.let {
                solutionCandidates[it.sourceId] = it
            }
            pppnav?.toPppCandidate(now)?.let {
                solutionCandidates[it.sourceId] = it
            }
```

(Replace `um980BinaryParser.parseBestnavb` / `parsePppnavb` with whichever method names the existing consumer uses — the existing service code already calls them. If the actual existing code uses a different variable name like `binaryTelemetry`, reuse that variable.)

- [ ] **Step 5: Run pure-Kotlin regression tests**

```sh
./gradlew :core:solution:test :receiver:unicore-n4:test :receiver:ublox-m8:test
```

Expected: `BUILD SUCCESSFUL`. The u-blox + UM980 tests still pass.

Attempt the app compile:

```sh
./gradlew :app:compileDebugKotlin
```

Expected on a host with Android SDK: `BUILD SUCCESSFUL`. Expected on this host: `FAILED — SDK location not found`. Either is acceptable for the commit; the user will compile-verify on an Android-SDK host.

- [ ] **Step 6: Commit**

```sh
git add app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt
git commit -m "Feed UM980 and shared NMEA candidates into the selector"
```

---

### Task 12: Final Validation

**Files:** none modified.

- [ ] **Step 1: Run every pure-Kotlin test**

```sh
export JAVA_HOME='/c/Program Files/Eclipse Adoptium/jdk-21.0.9.10-hotspot'
export GRADLE_OPTS='-Djavax.net.ssl.trustStoreType=Windows-ROOT'
git diff --check
./gradlew :core:solution:test :receiver:unicore-n4:test :receiver:ublox-m8:test
```

Expected:
- `git diff --check` reports no whitespace errors.
- `BUILD SUCCESSFUL` on the test target. Total test count should be at least:
  - `core:solution`: 8 (4 existing + 4 new in Task 1)
  - `receiver:unicore-n4`: at least 20 new tests (Tasks 2 and 3) on top of existing
  - `receiver:ublox-m8`: 13 (10 existing + 3 new compiler tests in Task 4; tracker test count goes from 1 to 3 in Task 5)

- [ ] **Step 2: Attempt the Android compile gate**

```sh
./gradlew :app:compileDebugKotlin
```

On a host with Android SDK: expect `BUILD SUCCESSFUL`. If the host has no SDK, expect `FAILED — SDK location not found`. Record the result. The user will compile-verify on an Android-SDK host before merging.

- [ ] **Step 3: Manual smoke notes**

Document the manual on-device smoke list in the PR description (do NOT add a docs file — the spec already covers this in the Testing section):

- u-blox M8T session: Best card shows `RTK_FLOAT from UBX-NAV-PVT` when a base is connected; mock GPS visible at 1 Hz; STALE after >5 s without a fix.
- UM980 session: Best card shows `RTK_FIXED from UM980-BESTNAV` when receiver reports `NARROW_INT`; mock GPS publishes UM980 coordinates.
- Mock-location toggle enabled but RtkCollector not selected in Developer Options: `mockLocationState = NOT_PERMITTED`, clear `lastError`.
- Shutdown-script compile error: lifecycle ends as `STOPPED` with a `DEGRADED` event, not `FAILED`.

- [ ] **Step 4: (Optional) Request a final cross-task review**

Use `superpowers:requesting-code-review` to dispatch a fresh code reviewer over the new commits. Pass the spec path (`docs/superpowers/specs/2026-06-14-review-followup-design.md`) as the requirements reference.
