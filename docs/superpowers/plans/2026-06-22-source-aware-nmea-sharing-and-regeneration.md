# Source-Aware NMEA Sharing And Receiver Regeneration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Preserve the existing receiver NMEA regeneration workflow while making NMEA sharing source-aware across receiver, RTKLIB real-time and already-generated RTKLIB postprocessed outputs.

**Architecture:** `Regenerate NMEA` remains a receiver/in-device solution action that writes only `receiver-solution.nmea`. `Share NMEA` discovers non-empty NMEA artifacts, offers only available sources, and applies stable export names that keep receiver, RTKLIB real-time and RTKLIB postprocessed provenance separate. RTKLIB forward/backward computation is not introduced in this plan; this plan only shares postprocessed NMEA artifacts after a separate RTKLIB postprocessing action has created them.

**Tech Stack:** Kotlin, Android Compose, Android SAF `ContentResolver`, existing session artifacts, existing UM980 and u-blox receiver parsers, Gradle/JUnit tests.

---

## File Structure

- Modify `core/session/src/main/kotlin/org/rtkcollector/core/session/SessionArtifacts.kt`
  - Add explicit artifact names for RTKLIB postprocessed NMEA/POS outputs so session UI code does not use raw string literals.
- Modify `app/src/main/kotlin/org/rtkcollector/app/recording/SessionNmeaExporter.kt`
  - Replace receiver-only share planning with source-aware share planning.
  - Keep legacy `<session-name>.nmea` naming for the common receiver-only share.
  - Use suffixed names when sharing RTKLIB output or multiple NMEA sources.
- Modify `app/src/test/kotlin/org/rtkcollector/app/recording/SessionNmeaExporterTest.kt`
  - Cover non-empty source discovery, empty-file filtering, naming rules and byte-preserving export.
- Create `app/src/main/kotlin/org/rtkcollector/app/recording/ReceiverNmeaReexporter.kt`
  - Route receiver NMEA regeneration by receiver family.
  - Use existing UM980 regeneration for UM980/unknown streams.
  - Add u-blox regeneration from `UBX-NAV-PVT` through `UbloxNmeaExporter`, with valid NMEA pass-through for u-blox streams that already contain NMEA.
- Create `app/src/test/kotlin/org/rtkcollector/app/recording/ReceiverNmeaReexporterTest.kt`
  - Verify UM980 delegation, u-blox NAV-PVT generation, NMEA pass-through and atomic replacement.
- Modify `app/src/main/kotlin/org/rtkcollector/app/sessions/SafSessionActions.kt`
  - Add source-aware SAF NMEA share creation.
  - Route SAF receiver regeneration through `ReceiverNmeaReexporter`.
- Modify `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`
  - Use source-aware selection for `Share NMEA`.
  - Show a source chooser only when more than one NMEA source is available.
  - Keep direct sharing when exactly one NMEA source is available.
  - Route `Regenerate NMEA` through `ReceiverNmeaReexporter`.
- Modify `app/src/main/kotlin/org/rtkcollector/app/ui/sessions/SessionsScreen.kt`
  - Keep the existing `Regenerate NMEA` button text.
  - Add short helper text or dialog copy that states regeneration means receiver/in-device solution only.
- Modify documentation for the visible sharing/regeneration semantics:
  - Modify `docs/session-format.md`.
  - Modify `docs/user-workflows.md`.
  - Modify `docs/specification/verification-matrix.md` if formal requirement IDs already cover session NMEA sharing/regeneration.

## Naming Contract

Session artifacts:

- Receiver in-device solution: `receiver-solution.nmea`.
- RTKLIB real-time solution: `rtklib-solution.nmea`.
- RTKLIB postprocessed forward solution: `rtklib-postprocessed-forward.nmea`.
- RTKLIB postprocessed forward/backward solution: `rtklib-postprocessed-combined.nmea`.

Temporary share names:

- If all selected outputs are receiver NMEA and each session contributes only receiver NMEA, preserve the current name: `<session-name>.nmea`.
- If a selected session contributes more than one NMEA source, or the chosen source is not receiver NMEA, use:
  - `<session-name>-receiver-solution.nmea`;
  - `<session-name>-rtklib-realtime.nmea`;
  - `<session-name>-rtklib-postprocessed-forward.nmea`;
  - `<session-name>-rtklib-postprocessed-combined.nmea`.
- Do not export RTKLIB NMEA as plain `<session-name>.nmea`.
- Do not offer a source if its artifact is missing or zero bytes.

## Task 1: Add Explicit Postprocessed Session Artifacts

**Files:**
- Modify: `core/session/src/main/kotlin/org/rtkcollector/core/session/SessionArtifacts.kt`

- [ ] **Step 1: Add postprocessed RTKLIB artifact names**

In `SessionArtifactFile`, add these entries immediately after `RTKLIB_SOLUTION_POS`:

```kotlin
RTKLIB_POSTPROCESSED_FORWARD_NMEA("rtklib-postprocessed-forward.nmea"),
RTKLIB_POSTPROCESSED_FORWARD_POS("rtklib-postprocessed-forward.pos"),
RTKLIB_POSTPROCESSED_COMBINED_NMEA("rtklib-postprocessed-combined.nmea"),
RTKLIB_POSTPROCESSED_COMBINED_POS("rtklib-postprocessed-combined.pos"),
```

- [ ] **Step 2: Run compile check for enum consumers**

Run:

```bash
sh gradlew --no-daemon -Dkotlin.compiler.execution.strategy=in-process :core:session:compileKotlin
```

Expected: PASS. If the module name differs in this checkout, run:

```bash
sh gradlew --no-daemon -Dkotlin.compiler.execution.strategy=in-process :core:session:test
```

Expected: PASS or no-op if the module has no tests.

- [ ] **Step 3: Commit checkpoint**

```bash
git add core/session/src/main/kotlin/org/rtkcollector/core/session/SessionArtifacts.kt
git commit -m "feat: name rtklib postprocessed session artifacts"
```

## Task 2: Make Filesystem NMEA Sharing Source-Aware

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/recording/SessionNmeaExporter.kt`
- Modify: `app/src/test/kotlin/org/rtkcollector/app/recording/SessionNmeaExporterTest.kt`

- [ ] **Step 1: Replace the receiver-only tests with source-aware tests**

Replace `SessionNmeaExporterTest.kt` with:

```kotlin
package org.rtkcollector.app.recording

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class SessionNmeaExporterTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `receiver only share keeps legacy session nmea name`() {
        val session = Files.createDirectory(tempDir.resolve("session-2026-06-14T12-00-00Z-abc"))
        Files.writeString(session.resolve("receiver-solution.nmea"), "\$GPGGA,data\n")
        val cache = Files.createDirectory(tempDir.resolve("cache"))

        val selection = SessionNmeaShareSelection.fromSessionDirectories(
            sessionDirectories = listOf(session),
            outputDirectory = cache,
        )

        assertEquals(1, selection.plans.size)
        assertEquals(SessionNmeaSource.RECEIVER_SOLUTION, selection.plans.single().source)
        assertEquals(session.resolve("receiver-solution.nmea"), selection.plans.single().sourceNmea)
        assertEquals(cache.resolve("session-2026-06-14T12-00-00Z-abc.nmea"), selection.plans.single().outputNmea)
        assertEquals(0, selection.skippedCount)
    }

    @Test
    fun `rtklib realtime share uses source suffix`() {
        val session = Files.createDirectory(tempDir.resolve("session-a"))
        Files.writeString(session.resolve("rtklib-solution.nmea"), "\$GNGGA,rtklib\n")
        val cache = Files.createDirectory(tempDir.resolve("cache"))

        val selection = SessionNmeaShareSelection.fromSessionDirectories(
            sessionDirectories = listOf(session),
            outputDirectory = cache,
        )

        assertEquals(1, selection.plans.size)
        assertEquals(SessionNmeaSource.RTKLIB_REALTIME, selection.plans.single().source)
        assertEquals(cache.resolve("session-a-rtklib-realtime.nmea"), selection.plans.single().outputNmea)
    }

    @Test
    fun `multiple nmea sources use source suffixes`() {
        val session = Files.createDirectory(tempDir.resolve("session-a"))
        Files.writeString(session.resolve("receiver-solution.nmea"), "\$GNGGA,receiver\n")
        Files.writeString(session.resolve("rtklib-solution.nmea"), "\$GNGGA,rtklib\n")
        Files.writeString(session.resolve("rtklib-postprocessed-forward.nmea"), "\$GNGGA,postforward\n")
        val cache = Files.createDirectory(tempDir.resolve("cache"))

        val selection = SessionNmeaShareSelection.fromSessionDirectories(
            sessionDirectories = listOf(session),
            outputDirectory = cache,
        )

        assertEquals(
            listOf(
                cache.resolve("session-a-receiver-solution.nmea"),
                cache.resolve("session-a-rtklib-realtime.nmea"),
                cache.resolve("session-a-rtklib-postprocessed-forward.nmea"),
            ),
            selection.plans.map(SessionNmeaSharePlan::outputNmea),
        )
    }

    @Test
    fun `empty nmea files are not offered`() {
        val session = Files.createDirectory(tempDir.resolve("session-a"))
        Files.writeString(session.resolve("receiver-solution.nmea"), "")
        Files.writeString(session.resolve("rtklib-solution.nmea"), "\$GNGGA,rtklib\n")
        val cache = Files.createDirectory(tempDir.resolve("cache"))

        val selection = SessionNmeaShareSelection.fromSessionDirectories(
            sessionDirectories = listOf(session),
            outputDirectory = cache,
        )

        assertEquals(listOf(SessionNmeaSource.RTKLIB_REALTIME), selection.plans.map(SessionNmeaSharePlan::source))
        assertEquals(0, selection.skippedCount)
        assertTrue(selection.hasShareableNmea)
    }

    @Test
    fun `selection reports skipped session when no nmea sources exist`() {
        val session = Files.createDirectory(tempDir.resolve("session"))
        val cache = Files.createDirectory(tempDir.resolve("cache"))

        val selection = SessionNmeaShareSelection.fromSessionDirectories(listOf(session), cache)

        assertEquals(emptyList<SessionNmeaSharePlan>(), selection.plans)
        assertEquals(1, selection.skippedCount)
        assertFalse(selection.hasShareableNmea)
    }

    @Test
    fun `export copies selected nmea bytes without altering source`() {
        val session = Files.createDirectory(tempDir.resolve("session"))
        val source = session.resolve("rtklib-solution.nmea")
        Files.writeString(source, "\$GPGGA,data\n\$GPRMC,data\n")
        val cache = Files.createDirectory(tempDir.resolve("cache"))
        val plan = SessionNmeaSharePlan(
            source = SessionNmeaSource.RTKLIB_REALTIME,
            sourceNmea = source,
            outputNmea = cache.resolve("session-rtklib-realtime.nmea"),
        )

        val output = SessionNmeaExporter.export(plan)

        assertEquals("\$GPGGA,data\n\$GPRMC,data\n", Files.readString(output))
        assertEquals("\$GPGGA,data\n\$GPRMC,data\n", Files.readString(source))
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run:

```bash
sh gradlew --no-daemon -Dkotlin.compiler.execution.strategy=in-process :app:testDebugUnitTest --tests org.rtkcollector.app.recording.SessionNmeaExporterTest
```

Expected before implementation: compilation fails because `SessionNmeaSource` and the new `SessionNmeaSharePlan` constructor do not exist. In Termux, if Android resource tooling fails before Kotlin tests run, record the environment limitation and use `:app:compileDebugKotlin` after implementation.

- [ ] **Step 3: Implement source-aware sharing model**

Replace `SessionNmeaExporter.kt` with:

```kotlin
package org.rtkcollector.app.recording

import org.rtkcollector.core.session.SessionArtifactFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

enum class SessionNmeaSource(
    val artifactFileName: String,
    val exportSuffix: String,
    val displayName: String,
) {
    RECEIVER_SOLUTION(
        artifactFileName = SessionArtifactFile.RECEIVER_SOLUTION_NMEA.fileName,
        exportSuffix = "receiver-solution",
        displayName = "Receiver solution",
    ),
    RTKLIB_REALTIME(
        artifactFileName = SessionArtifactFile.RTKLIB_SOLUTION_NMEA.fileName,
        exportSuffix = "rtklib-realtime",
        displayName = "RTKLIB real-time",
    ),
    RTKLIB_POSTPROCESSED_FORWARD(
        artifactFileName = SessionArtifactFile.RTKLIB_POSTPROCESSED_FORWARD_NMEA.fileName,
        exportSuffix = "rtklib-postprocessed-forward",
        displayName = "RTKLIB postprocessed forward",
    ),
    RTKLIB_POSTPROCESSED_COMBINED(
        artifactFileName = SessionArtifactFile.RTKLIB_POSTPROCESSED_COMBINED_NMEA.fileName,
        exportSuffix = "rtklib-postprocessed-combined",
        displayName = "RTKLIB postprocessed forward/backward",
    ),
}

data class SessionNmeaSharePlan(
    val source: SessionNmeaSource,
    val sourceNmea: Path,
    val outputNmea: Path,
) {
    init {
        require(sourceNmea.fileName.toString() == source.artifactFileName) {
            "NMEA share source ${source.displayName} must use ${source.artifactFileName}."
        }
        require(outputNmea.fileName.toString().endsWith(".nmea")) {
            "NMEA share output must use .nmea suffix."
        }
    }
}

data class SessionNmeaShareSelection(
    val plans: List<SessionNmeaSharePlan>,
    val skippedCount: Int,
) {
    val hasShareableNmea: Boolean
        get() = plans.isNotEmpty()

    companion object {
        fun fromSessionDirectories(
            sessionDirectories: List<Path>,
            outputDirectory: Path,
            requestedSources: Set<SessionNmeaSource>? = null,
        ): SessionNmeaShareSelection {
            val availableBySession = sessionDirectories.map { session ->
                session to availableSources(session, requestedSources)
            }
            val receiverOnlyAcrossSelection = availableBySession
                .filter { (_, sources) -> sources.isNotEmpty() }
                .all { (_, sources) -> sources == listOf(SessionNmeaSource.RECEIVER_SOLUTION) }
            val plans = mutableListOf<SessionNmeaSharePlan>()
            var skipped = 0
            availableBySession.forEach { (session, sources) ->
                if (sources.isEmpty()) {
                    skipped++
                } else {
                    sources.forEach { source ->
                        val outputName = exportName(
                            sessionName = session.fileName.toString(),
                            source = source,
                            useLegacyReceiverName = receiverOnlyAcrossSelection && source == SessionNmeaSource.RECEIVER_SOLUTION,
                        )
                        plans += SessionNmeaSharePlan(
                            source = source,
                            sourceNmea = session.resolve(source.artifactFileName),
                            outputNmea = outputDirectory.resolve(outputName),
                        )
                    }
                }
            }
            return SessionNmeaShareSelection(plans = plans, skippedCount = skipped)
        }

        fun availableSources(
            sessionDirectory: Path,
            requestedSources: Set<SessionNmeaSource>? = null,
        ): List<SessionNmeaSource> {
            require(Files.isDirectory(sessionDirectory)) { "NMEA share source must be a session directory." }
            val allowed = requestedSources ?: SessionNmeaSource.entries.toSet()
            return SessionNmeaSource.entries.filter { source ->
                source in allowed &&
                    sessionDirectory.resolve(source.artifactFileName).let { Files.isRegularFile(it) && Files.size(it) > 0L }
            }
        }

        fun exportName(
            sessionName: String,
            source: SessionNmeaSource,
            useLegacyReceiverName: Boolean,
        ): String =
            if (useLegacyReceiverName) {
                "$sessionName.nmea"
            } else {
                "$sessionName-${source.exportSuffix}.nmea"
            }
    }
}

object SessionNmeaExporter {
    fun export(plan: SessionNmeaSharePlan): Path {
        plan.outputNmea.parent?.let(Files::createDirectories)
        Files.copy(plan.sourceNmea, plan.outputNmea, StandardCopyOption.REPLACE_EXISTING)
        return plan.outputNmea
    }
}
```

- [ ] **Step 4: Run focused tests**

Run:

```bash
sh gradlew --no-daemon -Dkotlin.compiler.execution.strategy=in-process :app:testDebugUnitTest --tests org.rtkcollector.app.recording.SessionNmeaExporterTest
```

Expected: PASS where app unit tests can run. In Termux, if the app test task fails before executing tests because of Android tooling, run:

```bash
sh gradlew --no-daemon -Dkotlin.compiler.execution.strategy=in-process :app:compileDebugKotlin
```

Expected: PASS.

- [ ] **Step 5: Commit checkpoint**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/recording/SessionNmeaExporter.kt \
  app/src/test/kotlin/org/rtkcollector/app/recording/SessionNmeaExporterTest.kt
git commit -m "feat: share nmea by available solution source"
```

## Task 3: Add Receiver-Family NMEA Regeneration

**Files:**
- Create: `app/src/main/kotlin/org/rtkcollector/app/recording/ReceiverNmeaReexporter.kt`
- Create: `app/src/test/kotlin/org/rtkcollector/app/recording/ReceiverNmeaReexporterTest.kt`
- Modify: `app/build.gradle.kts` only if the app test source set does not already see receiver modules.

- [ ] **Step 1: Add tests for receiver regeneration routing**

Create `ReceiverNmeaReexporterTest.kt`:

```kotlin
package org.rtkcollector.app.recording

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.rtkcollector.receiver.ublox.UbloxFrame
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path

class ReceiverNmeaReexporterTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `ublox nav pvt raw regenerates receiver solution nmea`() {
        val raw = tempDir.resolve("receiver-rx.raw")
        Files.write(raw, ubloxNavPvtFrame())
        val output = tempDir.resolve("receiver-solution.nmea")

        val result = ReceiverNmeaReexporter.reexportReceiverRxRaw(
            receiverRxRaw = raw,
            outputNmea = output,
            receiverFamily = "ublox-m8t",
        )

        val text = Files.readString(output)
        assertEquals(1L, result.sentencesWritten)
        assertTrue(text.startsWith("\$GNGGA,120000.000,"))
        assertTrue(text.contains(",2,12,"))
    }

    @Test
    fun `ublox stream passes through existing nmea when no nav pvt is available`() {
        val raw = tempDir.resolve("receiver-rx.raw")
        Files.writeString(raw, "\$GNGGA,120000,5000.0,N,01400.0,E,1,12,0.8,300.0,M,0.0,M,,*00\r\n")
        val output = tempDir.resolve("receiver-solution.nmea")

        val result = ReceiverNmeaReexporter.reexportReceiverRxRaw(
            receiverRxRaw = raw,
            outputNmea = output,
            receiverFamily = "ublox-m8t",
        )

        assertEquals(1L, result.sentencesWritten)
        assertEquals(
            "\$GNGGA,120000,5000.0,N,01400.0,E,1,12,0.8,300.0,M,0.0,M,,*00\r\n",
            Files.readString(output),
        )
    }

    @Test
    fun `empty regeneration leaves previous nmea intact`() {
        val raw = tempDir.resolve("receiver-rx.raw")
        Files.write(raw, byteArrayOf(0x01, 0x02, 0x03))
        val output = tempDir.resolve("receiver-solution.nmea")
        Files.writeString(output, "\$GNGGA,old\n")

        val result = ReceiverNmeaReexporter.reexportReceiverRxRaw(
            receiverRxRaw = raw,
            outputNmea = output,
            receiverFamily = "ublox-m8t",
        )

        assertEquals(0L, result.sentencesWritten)
        assertEquals("\$GNGGA,old\n", Files.readString(output))
    }

    private fun ubloxNavPvtFrame(): ByteArray {
        val payload = ByteArray(92)
        val buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(0, 43_200_000)
        buffer.putShort(4, 2026.toShort())
        buffer.put(6, 6)
        buffer.put(7, 22)
        buffer.put(8, 12)
        buffer.put(9, 0)
        buffer.put(10, 0)
        buffer.put(11, 0x07)
        buffer.putInt(12, 0)
        buffer.putInt(16, 0)
        buffer.put(20, 0x03)
        buffer.put(21, 0x01)
        buffer.put(23, 12)
        buffer.putInt(24, (14.4212534 * 1e7).toInt())
        buffer.putInt(28, (50.0874512 * 1e7).toInt())
        buffer.putInt(32, (337.4 * 1000.0).toInt())
        buffer.putInt(36, (287.4 * 1000.0).toInt())
        buffer.putInt(40, 800)
        buffer.putInt(44, 1200)
        return UbloxFrame.build(0x01, 0x07, payload)
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run:

```bash
sh gradlew --no-daemon -Dkotlin.compiler.execution.strategy=in-process :app:testDebugUnitTest --tests org.rtkcollector.app.recording.ReceiverNmeaReexporterTest
```

Expected before implementation: compilation fails because `ReceiverNmeaReexporter` does not exist.

- [ ] **Step 3: Implement `ReceiverNmeaReexporter`**

Create `ReceiverNmeaReexporter.kt`:

```kotlin
package org.rtkcollector.app.recording

import org.rtkcollector.receiver.ublox.UbloxNavPvtParser
import org.rtkcollector.receiver.ublox.UbloxNmeaExporter
import org.rtkcollector.receiver.ublox.UbloxStreamParser
import org.rtkcollector.receiver.unicore.Um980NmeaExportOptions
import org.rtkcollector.receiver.unicore.Um980NmeaReexportProgress
import org.rtkcollector.receiver.unicore.Um980NmeaReexporter
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

data class ReceiverNmeaReexportResult(
    val outputNmea: Path,
    val sentencesWritten: Long,
)

object ReceiverNmeaReexporter {
    fun reexportReceiverRxRaw(
        receiverRxRaw: Path,
        outputNmea: Path,
        receiverFamily: String?,
        options: Um980NmeaExportOptions = Um980NmeaExportOptions(),
        onProgress: (Um980NmeaReexportProgress) -> Unit = {},
    ): ReceiverNmeaReexportResult {
        require(Files.isRegularFile(receiverRxRaw)) { "receiver-rx.raw is required for NMEA regeneration." }
        outputNmea.parent?.let(Files::createDirectories)
        val temporaryOutput = outputNmea.resolveSibling("${outputNmea.fileName}.tmp")
        val sentencesWritten = Files.newInputStream(receiverRxRaw).use { input ->
            Files.newOutputStream(
                temporaryOutput,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE,
            ).use { output ->
                reexportReceiverRxRaw(
                    input = input,
                    output = output,
                    receiverFamily = receiverFamily,
                    totalBytes = Files.size(receiverRxRaw),
                    options = options,
                    onProgress = onProgress,
                )
            }
        }
        if (sentencesWritten > 0L) {
            Files.move(temporaryOutput, outputNmea, StandardCopyOption.REPLACE_EXISTING)
        } else {
            Files.deleteIfExists(temporaryOutput)
        }
        return ReceiverNmeaReexportResult(outputNmea = outputNmea, sentencesWritten = sentencesWritten)
    }

    fun reexportReceiverRxRaw(
        input: InputStream,
        output: OutputStream,
        receiverFamily: String?,
        totalBytes: Long? = null,
        options: Um980NmeaExportOptions = Um980NmeaExportOptions(),
        onProgress: (Um980NmeaReexportProgress) -> Unit = {},
    ): Long =
        if (receiverFamily.orEmpty().startsWith("ublox", ignoreCase = true)) {
            reexportUblox(input, output, totalBytes, onProgress)
        } else {
            Um980NmeaReexporter.reexportReceiverRxRaw(
                input = input,
                output = output,
                totalBytes = totalBytes,
                options = options,
                onProgress = onProgress,
            ).sentencesWritten
        }

    private fun reexportUblox(
        input: InputStream,
        output: OutputStream,
        totalBytes: Long?,
        onProgress: (Um980NmeaReexportProgress) -> Unit,
    ): Long {
        val parser = UbloxStreamParser()
        val buffer = ByteArray(64 * 1024)
        var bytesRead = 0L
        var sentencesWritten = 0L
        onProgress(Um980NmeaReexportProgress(bytesRead, totalBytes, sentencesWritten))
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            bytesRead += read.toLong()
            parser.accept(buffer.copyOf(read)).forEach { record ->
                when (record.kind) {
                    "ubx" -> {
                        if (record.bytes.getOrNull(2) == 0x01.toByte() && record.bytes.getOrNull(3) == 0x07.toByte()) {
                            UbloxNavPvtParser.parse(record.bytes, nowMillis = 0L)
                                ?.let(UbloxNmeaExporter::exportGga)
                                ?.let { sentence ->
                                    output.write(sentence.toByteArray(Charsets.US_ASCII))
                                    sentencesWritten++
                                }
                        }
                    }
                    "nmea" -> {
                        record.text?.takeIf { it.startsWith("\$") }?.let { sentence ->
                            val normalized = if (sentence.endsWith("\n")) sentence else "$sentence\r\n"
                            output.write(normalized.toByteArray(Charsets.US_ASCII))
                            sentencesWritten++
                        }
                    }
                }
            }
            onProgress(Um980NmeaReexportProgress(bytesRead, totalBytes, sentencesWritten))
        }
        output.flush()
        return sentencesWritten
    }
}
```

- [ ] **Step 4: Run focused tests**

Run:

```bash
sh gradlew --no-daemon -Dkotlin.compiler.execution.strategy=in-process :app:testDebugUnitTest --tests org.rtkcollector.app.recording.ReceiverNmeaReexporterTest
```

Expected: PASS where app unit tests can run. If Termux blocks app unit tests, run:

```bash
sh gradlew --no-daemon -Dkotlin.compiler.execution.strategy=in-process :app:compileDebugKotlin
```

Expected: PASS.

- [ ] **Step 5: Commit checkpoint**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/recording/ReceiverNmeaReexporter.kt \
  app/src/test/kotlin/org/rtkcollector/app/recording/ReceiverNmeaReexporterTest.kt
git commit -m "feat: regenerate receiver nmea by receiver family"
```

## Task 4: Update SAF NMEA Sharing And Regeneration

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/sessions/SafSessionActions.kt`

- [ ] **Step 1: Replace receiver-only SAF NMEA sharing**

Add imports:

```kotlin
import org.rtkcollector.app.recording.ReceiverNmeaReexporter
import org.rtkcollector.app.recording.SessionNmeaShareSelection
import org.rtkcollector.app.recording.SessionNmeaSource
```

Replace `createTemporaryNmeaShare(...)` with this source-aware function:

```kotlin
fun createTemporaryNmeaShares(
    resolver: ContentResolver,
    sessionUri: Uri,
    cacheRoot: Path,
    requestedSources: Set<SessionNmeaSource>? = null,
    useLegacyReceiverName: Boolean = true,
): List<Path> {
    Files.createDirectories(cacheRoot)
    val sessionName = sessionUri.displayName(resolver) ?: "saf-session"
    val allowed = requestedSources ?: SessionNmeaSource.entries.toSet()
    val availableSources = SessionNmeaSource.entries.filter { source ->
        source in allowed &&
            sessionUri.findChild(resolver, source.artifactFileName)?.let { child -> child.sizeBytes > 0L } == true
    }
    val receiverOnly = availableSources == listOf(SessionNmeaSource.RECEIVER_SOLUTION)
    return availableSources.mapNotNull { source ->
        val child = sessionUri.findChild(resolver, source.artifactFileName) ?: return@mapNotNull null
        val outputName = SessionNmeaShareSelection.exportName(
            sessionName = sessionName,
            source = source,
            useLegacyReceiverName = useLegacyReceiverName && receiverOnly && source == SessionNmeaSource.RECEIVER_SOLUTION,
        )
        val output = cacheRoot.resolve(outputName)
        resolver.openInputStream(child.uri)?.use { input ->
            Files.newOutputStream(output).use { fileOutput -> input.copyTo(fileOutput) }
        } ?: return@mapNotNull null
        output
    }
}
```

- [ ] **Step 2: Route SAF regeneration through receiver-family re-exporter**

Change `reexportNmea(...)` signature to:

```kotlin
fun reexportNmea(
    resolver: ContentResolver,
    sessionUri: Uri,
    receiverFamily: String?,
    options: Um980NmeaExportOptions = Um980NmeaExportOptions(),
    onProgress: (Um980NmeaReexportProgress) -> Unit = {},
): Long {
```

Inside the function, replace `Um980NmeaReexporter.reexportReceiverRxRaw(...)` with:

```kotlin
ReceiverNmeaReexporter.reexportReceiverRxRaw(
    input = input,
    output = output,
    receiverFamily = receiverFamily,
    totalBytes = receiverRxRaw.sizeBytes,
    options = options,
    onProgress = onProgress,
)
```

Keep the existing temporary SAF file and rename flow. Keep deleting the temporary file on failure.

- [ ] **Step 3: Compile app**

Run:

```bash
sh gradlew --no-daemon -Dkotlin.compiler.execution.strategy=in-process :app:compileDebugKotlin
```

Expected: PASS.

- [ ] **Step 4: Commit checkpoint**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/sessions/SafSessionActions.kt
git commit -m "feat: share and regenerate saf nmea by source"
```

## Task 5: Wire Source-Aware Sharing Into Sessions UI

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/sessions/SessionsScreen.kt`

- [ ] **Step 1: Add imports in `MainActivity.kt`**

Add:

```kotlin
import org.rtkcollector.app.recording.ReceiverNmeaReexporter
import org.rtkcollector.app.recording.SessionNmeaSource
```

Remove direct use of `Um980NmeaReexporter` from filesystem regeneration after Task 5 is complete.

- [ ] **Step 2: Add state for NMEA source choices**

Near the existing session/share UI state in `RtkCollectorApp`, add:

```kotlin
var pendingNmeaSources by remember { mutableStateOf<List<SessionNmeaSource>>(emptyList()) }
var pendingNmeaEntries by remember { mutableStateOf<List<SessionBrowserEntry>>(emptyList()) }
```

- [ ] **Step 3: Add source chooser dialog**

In the Compose tree near other dialogs, add:

```kotlin
if (pendingNmeaSources.isNotEmpty()) {
    AlertDialog(
        onDismissRequest = {
            pendingNmeaSources = emptyList()
            pendingNmeaEntries = emptyList()
        },
        title = { Text("Share NMEA") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Choose which available NMEA output to share.")
                pendingNmeaSources.forEach { source ->
                    TextButton(
                        onClick = {
                            val chosen = setOf(source)
                            val entries = pendingNmeaEntries
                            pendingNmeaSources = emptyList()
                            pendingNmeaEntries = emptyList()
                            shareNmeaFromEntries(
                                context = context,
                                entries = entries,
                                requestedSources = chosen,
                                refreshSessions = ::refreshSessions,
                                setProgress = { text, fraction ->
                                    zipProgressText = text
                                    sessionProgressFraction = fraction
                                },
                            )
                        },
                    ) {
                        Text(source.displayName)
                    }
                }
                if (pendingNmeaSources.size > 1) {
                    TextButton(
                        onClick = {
                            val chosen = pendingNmeaSources.toSet()
                            val entries = pendingNmeaEntries
                            pendingNmeaSources = emptyList()
                            pendingNmeaEntries = emptyList()
                            shareNmeaFromEntries(
                                context = context,
                                entries = entries,
                                requestedSources = chosen,
                                refreshSessions = ::refreshSessions,
                                setProgress = { text, fraction ->
                                    zipProgressText = text
                                    sessionProgressFraction = fraction
                                },
                            )
                        },
                    ) {
                        Text("All available")
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = {
                    pendingNmeaSources = emptyList()
                    pendingNmeaEntries = emptyList()
                },
            ) {
                Text("Cancel")
            }
        },
    )
}
```

- [ ] **Step 4: Extract current share logic into a helper**

Below `RtkCollectorApp`, add:

```kotlin
private fun shareNmeaFromEntries(
    context: android.content.Context,
    entries: List<SessionBrowserEntry>,
    requestedSources: Set<SessionNmeaSource>?,
    refreshSessions: () -> Unit,
    setProgress: (String?, Float?) -> Unit,
) {
    setProgress("Preparing NMEA...", 0f)
    Thread {
        runCatching {
            val cacheRoot = context.cacheDir.resolve("session-share-nmea").toPath()
            cleanupTemporaryNmeaShares(cacheRoot)
            val filesystemEntries = entries.filterNot { it.isSafLocation }
            val safEntries = entries.filter { it.isSafLocation }
            val filesystemSelection = SessionNmeaShareSelection.fromSessionDirectories(
                sessionDirectories = filesystemEntries.map { Paths.get(it.location) },
                outputDirectory = cacheRoot,
                requestedSources = requestedSources,
            )
            val outputs = filesystemSelection.plans.mapIndexed { index, plan ->
                runOnMain(context) {
                    setProgress("NMEA ${index + 1}/${entries.size}", (index + 1).toFloat() / entries.size.toFloat())
                }
                SessionNmeaExporter.export(plan)
            }.toMutableList()
            var skipped = filesystemSelection.skippedCount
            safEntries.forEachIndexed { index, entry ->
                val safOutputs = SafSessionActions.createTemporaryNmeaShares(
                    resolver = context.contentResolver,
                    sessionUri = Uri.parse(entry.location),
                    cacheRoot = cacheRoot,
                    requestedSources = requestedSources,
                    useLegacyReceiverName = requestedSources == null || requestedSources == setOf(SessionNmeaSource.RECEIVER_SOLUTION),
                )
                if (safOutputs.isEmpty()) skipped++ else outputs.addAll(safOutputs)
                runOnMain(context) {
                    val completed = filesystemEntries.size + index + 1
                    setProgress("NMEA $completed/${entries.size}", completed.toFloat() / entries.size.toFloat())
                }
            }
            skipped to outputs
        }.onSuccess { (skipped, outputs) ->
            runOnMain(context) {
                setProgress(null, null)
                if (outputs.isEmpty()) {
                    Toast.makeText(
                        context,
                        "No recorded NMEA file is available for the selected session(s).",
                        Toast.LENGTH_LONG,
                    ).show()
                } else {
                    shareNmeaFiles(context, outputs.map { it.toFile() })
                    val message = if (skipped > 0) {
                        "Shared NMEA for ${outputs.size} file(s); $skipped selected session(s) had no matching NMEA export."
                    } else {
                        "Shared NMEA for ${outputs.size} file(s)."
                    }
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
                refreshSessions()
            }
        }.onFailure { error ->
            runOnMain(context) {
                setProgress(null, null)
                Toast.makeText(context, "Share NMEA failed: ${error.message}", Toast.LENGTH_LONG).show()
            }
        }
    }.start()
}
```

- [ ] **Step 5: Replace `onShareNmeaSelected` body**

Change the handler so it first discovers available source kinds:

```kotlin
onShareNmeaSelected = {
    val selected = sessionBrowserState.selectedEntries.filter(SessionBrowserEntry::canShareNmea)
    if (selected.isEmpty()) {
        Toast.makeText(context, "Select at least one completed recording.", Toast.LENGTH_LONG).show()
    } else {
        val filesystemSources = selected
            .filterNot { it.isSafLocation }
            .flatMap { entry ->
                runCatching {
                    SessionNmeaShareSelection.availableSources(Paths.get(entry.location))
                }.getOrDefault(emptyList())
            }
        val safSources = selected
            .filter { it.isSafLocation }
            .flatMap { entry ->
                SessionNmeaSource.entries.filter { source ->
                    Uri.parse(entry.location)
                        .findChild(context.contentResolver, source.artifactFileName)
                        ?.let { child -> child.sizeBytes > 0L } == true
                }
            }
        val sources = (filesystemSources + safSources).distinct()
        when {
            sources.isEmpty() -> Toast.makeText(
                context,
                "No recorded NMEA file is available for the selected session(s).",
                Toast.LENGTH_LONG,
            ).show()
            sources.size == 1 -> shareNmeaFromEntries(
                context = context,
                entries = selected,
                requestedSources = sources.toSet(),
                refreshSessions = ::refreshSessions,
                setProgress = { text, fraction ->
                    zipProgressText = text
                    sessionProgressFraction = fraction
                },
            )
            else -> {
                pendingNmeaEntries = selected
                pendingNmeaSources = sources
            }
        }
    }
}
```

If `findChild` is private to `SafSessionActions.kt`, add a small public helper in `SafSessionActions`:

```kotlin
fun hasNonEmptyChild(resolver: ContentResolver, sessionUri: Uri, fileName: String): Boolean =
    sessionUri.findChild(resolver, fileName)?.let { it.sizeBytes > 0L } == true
```

Then use that helper in `MainActivity.kt`.

- [ ] **Step 6: Update `Regenerate NMEA` route**

In `onReexportNmeaSelected`, replace filesystem `Um980NmeaReexporter.reexportReceiverRxRaw(...)` with:

```kotlin
ReceiverNmeaReexporter.reexportReceiverRxRaw(
    receiverRxRaw = receiverRxRaw,
    outputNmea = sessionDirectory.resolve("receiver-solution.nmea"),
    receiverFamily = entry.receiverFamily,
    options = options,
)
```

For SAF, call:

```kotlin
SafSessionActions.reexportNmea(
    resolver = context.contentResolver,
    sessionUri = Uri.parse(entry.location),
    receiverFamily = entry.receiverFamily,
    options = options,
) { progress -> ... }
```

If `SessionBrowserEntry` does not expose `receiverFamily`, add nullable `receiverFamily: String?` to `SessionBrowserEntry` and populate it from `session.json` in filesystem and SAF browser code. Use `null` when the value is not present.

- [ ] **Step 7: Keep button text and clarify meaning**

In `SessionsScreen.kt`, keep:

```kotlin
Button(onClick = onReexportNmeaSelected, enabled = canReexportNmea) { Text("Regenerate NMEA") }
```

Add a compact caption below the action row:

```kotlin
Text(
    text = "Regenerate NMEA updates receiver-solution.nmea from the in-device receiver solution only.",
    style = MaterialTheme.typography.labelSmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
)
```

- [ ] **Step 8: Compile app**

Run:

```bash
sh gradlew --no-daemon -Dkotlin.compiler.execution.strategy=in-process :app:compileDebugKotlin
```

Expected: PASS.

- [ ] **Step 9: Commit checkpoint**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt \
  app/src/main/kotlin/org/rtkcollector/app/ui/sessions/SessionsScreen.kt
git commit -m "feat: choose available nmea sources when sharing"
```

## Task 6: Document NMEA Provenance And Sharing Rules

**Files:**
- Modify: `docs/session-format.md`
- Modify: `docs/user-workflows.md`
- Modify: `docs/specification/verification-matrix.md` only if existing requirement IDs mention NMEA session artifacts.

- [ ] **Step 1: Update session artifact documentation**

In `docs/session-format.md`, add or update the NMEA artifacts section with:

```markdown
### NMEA Solution Artifacts

`receiver-solution.nmea` contains NMEA generated from the receiver's in-device
solution. Regenerating NMEA updates this file only. It must not contain RTKLIB
real-time or postprocessed solutions.

`rtklib-solution.nmea` contains RTKLIB real-time solution output when RTKLIB is
enabled and NMEA output is requested.

`rtklib-postprocessed-forward.nmea` and
`rtklib-postprocessed-combined.nmea` are optional completed-session
postprocessing outputs. They are shareable only after a postprocessing action
has generated them.
```

- [ ] **Step 2: Update user workflow documentation**

In `docs/user-workflows.md`, add:

```markdown
When sharing NMEA from recorded sessions, RtkCollector offers only NMEA files
that already exist and are non-empty. Receiver NMEA, RTKLIB real-time NMEA and
RTKLIB postprocessed NMEA are separate outputs. `Regenerate NMEA` regenerates
only the receiver/in-device solution NMEA.
```

- [ ] **Step 3: Update formal verification matrix if applicable**

Run:

```bash
rg -n "NMEA|receiver-solution|rtklib-solution|session artifact" docs/specification docs/session-format.md docs/user-workflows.md
```

If `docs/specification/verification-matrix.md` already lists requirements for NMEA export/share/regeneration, add verification rows naming:

```markdown
`SessionNmeaExporterTest` verifies source-aware NMEA sharing names and available-source filtering.
`ReceiverNmeaReexporterTest` verifies receiver-family NMEA regeneration does not overwrite existing receiver NMEA when no sentences are produced.
```

Do not invent new requirement IDs in this task; if requirement IDs are missing, record the missing formal requirement as a note in the final report and leave a separate spec task.

- [ ] **Step 4: Commit checkpoint**

```bash
git add docs/session-format.md docs/user-workflows.md docs/specification/verification-matrix.md
git commit -m "docs: define nmea source sharing semantics"
```

If `docs/specification/verification-matrix.md` is unchanged, omit it from `git add`.

## Task 7: Final Verification

**Files:**
- No source edits expected.

- [ ] **Step 1: Check formatting and whitespace**

Run:

```bash
git diff --check
```

Expected: no output.

- [ ] **Step 2: Run receiver module tests**

Run:

```bash
sh gradlew --no-daemon -Dkotlin.compiler.execution.strategy=in-process :receiver:ublox-m8:test :receiver:unicore-n4:test
```

Expected: PASS.

- [ ] **Step 3: Run app Kotlin compile**

Run:

```bash
sh gradlew --no-daemon -Dkotlin.compiler.execution.strategy=in-process :app:compileDebugKotlin
```

Expected: PASS.

- [ ] **Step 4: Run targeted app tests where supported**

Run:

```bash
sh gradlew --no-daemon -Dkotlin.compiler.execution.strategy=in-process :app:testDebugUnitTest --tests org.rtkcollector.app.recording.SessionNmeaExporterTest --tests org.rtkcollector.app.recording.ReceiverNmeaReexporterTest
```

Expected on a normal Android build host: PASS.

Known Termux caveat: if this fails before test execution because Android unit-test resource tooling invokes an incompatible native `aapt2`, record the failure as an environment limitation and rely on `:app:compileDebugKotlin` plus receiver module tests for local verification.

- [ ] **Step 5: Inspect final diff**

Run:

```bash
git status --short
git diff --stat HEAD
```

Expected: only intentional code/docs changes are present. `samples/` and `samples/debug/` must not be staged.

## Self-Review

- Spec coverage:
  - Receiver NMEA regeneration remains present and receiver-only.
  - u-blox receiver NMEA regeneration is explicitly routed through u-blox NAV-PVT/NMEA parsing.
  - RTKLIB real-time NMEA remains separate.
  - RTKLIB postprocessed outputs are shareable only if files already exist.
  - Share names preserve current receiver-only behaviour and add suffixes for non-receiver or multi-source sharing.
  - SAF and filesystem paths are both covered.
- Placeholder scan:
  - This plan contains no placeholder implementation steps. RTKLIB postprocessing computation is explicitly out of scope for this source-aware sharing plan because the current codebase has real-time RTKLIB worker plumbing, not a completed-session forward/backward postprocessing backend.
- Type consistency:
  - `SessionNmeaSource`, `SessionNmeaSharePlan`, `SessionNmeaShareSelection`, `SessionNmeaExporter` and `ReceiverNmeaReexporter` names are used consistently across tasks.
  - Artifact names match the naming contract and new `SessionArtifactFile` entries.
