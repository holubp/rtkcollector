# App Logs And Performance Monitoring Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add opt-in app runtime logging and opt-in performance monitoring that can be shared or deleted from `Developer tools > App logs and performance monitoring`.

**Architecture:** Add a small diagnostics package with pure Kotlin redaction, settings, bounded file storage and ZIP export helpers. Wire the UI as one new settings screen and feed it only guarded, advisory events from Activity/service code; when diagnostics are disabled, call sites must avoid building log records or performance samples.

**Tech Stack:** Kotlin, Jetpack Compose, Android app-private files/cache, Android `FileProvider`, JUnit 5, existing Gradle Android app module.

---

## File Structure

- Create `app/src/main/kotlin/org/rtkcollector/app/diagnostics/DiagnosticsModels.kt`
  - Data classes and JSON helpers for runtime records, performance samples, summaries and status.
- Create `app/src/main/kotlin/org/rtkcollector/app/diagnostics/DiagnosticsSettings.kt`
  - SharedPreferences-backed runtime/performance enabled flags.
- Create `app/src/main/kotlin/org/rtkcollector/app/diagnostics/DiagnosticsRedaction.kt`
  - Redaction helpers used before writing records.
- Create `app/src/main/kotlin/org/rtkcollector/app/diagnostics/DiagnosticsStore.kt`
  - Bounded app-private JSONL file storage, deletion and status.
- Create `app/src/main/kotlin/org/rtkcollector/app/diagnostics/DiagnosticsZipExporter.kt`
  - Temporary ZIP creation for runtime logs and performance metrics.
- Create `app/src/main/kotlin/org/rtkcollector/app/diagnostics/RuntimeDiagnostics.kt`
  - Cheap enabled guard plus drop-on-pressure background runtime log writer.
- Create `app/src/main/kotlin/org/rtkcollector/app/diagnostics/PerformanceDiagnostics.kt`
  - Low-rate sampler controller that is inactive when disabled.
- Create `app/src/main/kotlin/org/rtkcollector/app/ui/diagnostics/AppDiagnosticsScreen.kt`
  - Compose screen for toggles, share buttons, delete button and status/errors.
- Modify `app/src/main/kotlin/org/rtkcollector/app/ui/settings/SettingsHub.kt`
  - Rename `Device tools` to `Developer tools`.
  - Add `App logs and performance monitoring` row.
- Modify `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`
  - Add `AppScreen.APP_DIAGNOSTICS`.
  - Share runtime/performance ZIPs using existing `FileProvider` pattern.
  - Start/stop performance sampler from enabled flag and service state snapshots.
  - Guarded runtime logging for visible Activity errors.
- Modify `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt`
  - Guarded runtime logging for start, stop, USB, NTRIP, storage, receiver-command and mock-location errors.
  - Guarded performance sampler updates from existing low-rate state broadcasts only.
- Modify `app/src/main/res/xml/file_paths.xml`
  - Add scoped diagnostics-share cache path.
- Modify formal docs:
  - `docs/specification/security-privacy.md`
  - `docs/specification/android-runtime.md`
  - `docs/specification/verification-matrix.md`
  - `docs/superpowers/plan-status.md`
- Add tests:
  - `app/src/test/kotlin/org/rtkcollector/app/diagnostics/DiagnosticsRedactionTest.kt`
  - `app/src/test/kotlin/org/rtkcollector/app/diagnostics/DiagnosticsSettingsTest.kt`
  - `app/src/test/kotlin/org/rtkcollector/app/diagnostics/DiagnosticsStoreTest.kt`
  - `app/src/test/kotlin/org/rtkcollector/app/diagnostics/DiagnosticsZipExporterTest.kt`
  - `app/src/test/kotlin/org/rtkcollector/app/diagnostics/PerformanceDiagnosticsTest.kt`
  - extend `app/src/test/kotlin/org/rtkcollector/app/ui/FileProviderPathsTest.kt`

## Implementation Tasks

### Task 1: Diagnostics Redaction And Models

**Files:**
- Create: `app/src/main/kotlin/org/rtkcollector/app/diagnostics/DiagnosticsModels.kt`
- Create: `app/src/main/kotlin/org/rtkcollector/app/diagnostics/DiagnosticsRedaction.kt`
- Test: `app/src/test/kotlin/org/rtkcollector/app/diagnostics/DiagnosticsRedactionTest.kt`

- [ ] **Step 1: Write redaction tests**

```kotlin
package org.rtkcollector.app.diagnostics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class DiagnosticsRedactionTest {
    @Test
    fun `redacts authorization headers and password fields`() {
        val input = "Authorization: Basic abc123 password=secret token=abc"
        val redacted = redactDiagnosticText(input)

        assertFalse(redacted.contains("abc123"))
        assertFalse(redacted.contains("secret"))
        assertFalse(redacted.contains("token=abc"))
        assertEquals("Authorization: <redacted> password=<redacted> token=<redacted>", redacted)
    }

    @Test
    fun `redacts ntrip url credentials`() {
        val input = "ntrip://user:pass@example.org:2101/MOUNT"
        val redacted = redactDiagnosticText(input)

        assertEquals("ntrip://<redacted>@example.org:2101/MOUNT", redacted)
    }
}
```

- [ ] **Step 2: Run the redaction test to verify it fails**

Run:

```bash
sh gradlew --no-daemon :app:compileDebugKotlin
```

Expected: compilation fails because `redactDiagnosticText` does not exist.

- [ ] **Step 3: Add models and redaction implementation**

Create `DiagnosticsModels.kt`:

```kotlin
package org.rtkcollector.app.diagnostics

import org.json.JSONObject

enum class DiagnosticCategory {
    APP,
    USB,
    STORAGE,
    NTRIP,
    RECEIVER_COMMAND,
    SERVICE,
    MOCK_LOCATION,
}

data class RuntimeDiagnosticRecord(
    val timestampMillis: Long,
    val category: DiagnosticCategory,
    val severity: String,
    val message: String,
    val attributes: Map<String, String> = emptyMap(),
) {
    fun toJsonLine(): String {
        val json = JSONObject()
            .put("timestampMillis", timestampMillis)
            .put("category", category.name)
            .put("severity", severity)
            .put("message", redactDiagnosticText(message))
        val attrs = JSONObject()
        attributes.forEach { (key, value) -> attrs.put(key, redactDiagnosticText(value)) }
        json.put("attributes", attrs)
        return json.toString()
    }
}

data class PerformanceDiagnosticSample(
    val timestampMillis: Long,
    val receiverRxBytes: Long,
    val correctionInputBytes: Long,
    val txToReceiverBytes: Long,
    val sessionTotalBytes: Long,
    val heapUsedBytes: Long,
    val heapMaxBytes: Long,
    val threadCount: Int,
    val mockLastIntervalMs: Long?,
) {
    fun toJsonLine(): String {
        val json = JSONObject()
            .put("timestampMillis", timestampMillis)
            .put("receiverRxBytes", receiverRxBytes)
            .put("correctionInputBytes", correctionInputBytes)
            .put("txToReceiverBytes", txToReceiverBytes)
            .put("sessionTotalBytes", sessionTotalBytes)
            .put("heapUsedBytes", heapUsedBytes)
            .put("heapMaxBytes", heapMaxBytes)
            .put("threadCount", threadCount)
        if (mockLastIntervalMs != null) {
            json.put("mockLastIntervalMs", mockLastIntervalMs)
        }
        return json.toString()
    }
}

data class DiagnosticsStatus(
    val runtimeLoggingEnabled: Boolean,
    val performanceMonitoringEnabled: Boolean,
    val runtimeBytes: Long,
    val performanceBytes: Long,
    val runtimeFiles: Int,
    val performanceFiles: Int,
)
```

Create `DiagnosticsRedaction.kt`:

```kotlin
package org.rtkcollector.app.diagnostics

private val AUTHORIZATION_REGEX = Regex("(?i)Authorization:\\s*\\S+(?:\\s+\\S+)?")
private val SECRET_ASSIGNMENT_REGEX = Regex("(?i)\\b(password|passwd|pwd|token|secret)=([^\\s,;]+)")
private val NTRIP_URL_CREDENTIALS_REGEX = Regex("(ntrip://)[^:@/\\s]+:[^@/\\s]+@")

fun redactDiagnosticText(text: String): String =
    text
        .replace(AUTHORIZATION_REGEX, "Authorization: <redacted>")
        .replace(SECRET_ASSIGNMENT_REGEX) { match -> "${match.groupValues[1]}=<redacted>" }
        .replace(NTRIP_URL_CREDENTIALS_REGEX) { match -> "${match.groupValues[1]}<redacted>@" }
```

- [ ] **Step 4: Run verification**

Run:

```bash
sh gradlew --no-daemon :app:compileDebugKotlin
```

Expected: compile succeeds.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/diagnostics/DiagnosticsModels.kt \
  app/src/main/kotlin/org/rtkcollector/app/diagnostics/DiagnosticsRedaction.kt \
  app/src/test/kotlin/org/rtkcollector/app/diagnostics/DiagnosticsRedactionTest.kt
git commit -m "Add diagnostics redaction models"
```

### Task 2: Diagnostics Settings And Bounded Store

**Files:**
- Create: `app/src/main/kotlin/org/rtkcollector/app/diagnostics/DiagnosticsSettings.kt`
- Create: `app/src/main/kotlin/org/rtkcollector/app/diagnostics/DiagnosticsStore.kt`
- Test: `app/src/test/kotlin/org/rtkcollector/app/diagnostics/DiagnosticsStoreTest.kt`

- [ ] **Step 1: Write store tests**

```kotlin
package org.rtkcollector.app.diagnostics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class DiagnosticsStoreTest {
    @TempDir lateinit var tempDir: Path

    @Test
    fun `disabled runtime logging writes nothing`() {
        val store = DiagnosticsStore(tempDir.toFile(), maxRuntimeBytes = 1024, maxPerformanceBytes = 1024)
        store.appendRuntime(enabled = false, RuntimeDiagnosticRecord(1, DiagnosticCategory.USB, "FATAL", "USB failed"))

        assertEquals(0, store.status(runtimeEnabled = false, performanceEnabled = false).runtimeBytes)
    }

    @Test
    fun `runtime logging writes jsonl when enabled`() {
        val store = DiagnosticsStore(tempDir.toFile(), maxRuntimeBytes = 1024, maxPerformanceBytes = 1024)
        store.appendRuntime(enabled = true, RuntimeDiagnosticRecord(1, DiagnosticCategory.NTRIP, "DEGRADED", "password=secret"))

        val file = tempDir.resolve("diagnostics/runtime/runtime.jsonl").toFile()
        assertTrue(file.exists())
        assertFalse(file.readText().contains("secret"))
    }

    @Test
    fun `delete removes diagnostics only`() {
        val store = DiagnosticsStore(tempDir.toFile(), maxRuntimeBytes = 1024, maxPerformanceBytes = 1024)
        val unrelated = tempDir.resolve("sessions/session-a/receiver-rx.raw").toFile()
        unrelated.parentFile.mkdirs()
        unrelated.writeText("raw")

        store.appendPerformance(enabled = true, PerformanceDiagnosticSample(1, 1, 2, 3, 4, 5, 6, 7, null))
        store.deleteDiagnostics()

        assertTrue(unrelated.exists())
        assertEquals(0, store.status(runtimeEnabled = false, performanceEnabled = false).performanceBytes)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
sh gradlew --no-daemon :app:compileDebugKotlin
```

Expected: compilation fails because `DiagnosticsStore` does not exist.

- [ ] **Step 3: Implement settings and store**

Create `DiagnosticsSettings.kt`:

```kotlin
package org.rtkcollector.app.diagnostics

import android.content.Context

class DiagnosticsSettings(context: Context) {
    private val preferences = context.getSharedPreferences("rtkcollector-diagnostics", Context.MODE_PRIVATE)

    var runtimeLoggingEnabled: Boolean
        get() = preferences.getBoolean(KEY_RUNTIME, false)
        set(value) {
            preferences.edit().putBoolean(KEY_RUNTIME, value).apply()
        }

    var performanceMonitoringEnabled: Boolean
        get() = preferences.getBoolean(KEY_PERFORMANCE, false)
        set(value) {
            preferences.edit().putBoolean(KEY_PERFORMANCE, value).apply()
        }

    companion object {
        private const val KEY_RUNTIME = "runtimeLoggingEnabled"
        private const val KEY_PERFORMANCE = "performanceMonitoringEnabled"
    }
}
```

Create `DiagnosticsStore.kt`:

```kotlin
package org.rtkcollector.app.diagnostics

import java.io.File

class DiagnosticsStore(
    private val appFilesDir: File,
    private val maxRuntimeBytes: Long = 1_000_000L,
    private val maxPerformanceBytes: Long = 1_000_000L,
) {
    private val diagnosticsDir: File = appFilesDir.resolve("diagnostics")
    private val runtimeDir: File = diagnosticsDir.resolve("runtime")
    private val performanceDir: File = diagnosticsDir.resolve("performance")
    private val runtimeFile: File = runtimeDir.resolve("runtime.jsonl")
    private val performanceFile: File = performanceDir.resolve("performance.jsonl")

    @Synchronized
    fun appendRuntime(enabled: Boolean, record: RuntimeDiagnosticRecord) {
        if (!enabled) return
        appendBounded(runtimeDir, runtimeFile, record.toJsonLine(), maxRuntimeBytes)
    }

    @Synchronized
    fun appendPerformance(enabled: Boolean, sample: PerformanceDiagnosticSample) {
        if (!enabled) return
        appendBounded(performanceDir, performanceFile, sample.toJsonLine(), maxPerformanceBytes)
    }

    @Synchronized
    fun status(runtimeEnabled: Boolean, performanceEnabled: Boolean): DiagnosticsStatus =
        DiagnosticsStatus(
            runtimeLoggingEnabled = runtimeEnabled,
            performanceMonitoringEnabled = performanceEnabled,
            runtimeBytes = runtimeDir.totalBytes(),
            performanceBytes = performanceDir.totalBytes(),
            runtimeFiles = runtimeDir.fileCount(),
            performanceFiles = performanceDir.fileCount(),
        )

    @Synchronized
    fun runtimeFiles(): List<File> = runtimeDir.listFiles()?.filter(File::isFile).orEmpty()

    @Synchronized
    fun performanceFiles(): List<File> = performanceDir.listFiles()?.filter(File::isFile).orEmpty()

    @Synchronized
    fun deleteDiagnostics() {
        runtimeDir.deleteRecursively()
        performanceDir.deleteRecursively()
    }

    private fun appendBounded(directory: File, file: File, line: String, maxBytes: Long) {
        directory.mkdirs()
        if (file.length() > maxBytes) {
            file.writeText("", Charsets.UTF_8)
        }
        file.appendText(line)
        file.appendText("\n")
        if (file.length() > maxBytes) {
            val text = file.readText(Charsets.UTF_8)
            val keepFrom = (text.length / 2).coerceAtMost(text.length)
            file.writeText(text.substring(keepFrom), Charsets.UTF_8)
        }
    }
}

private fun File.totalBytes(): Long =
    walkExistingFiles().sumOf(File::length)

private fun File.fileCount(): Int =
    walkExistingFiles().count()

private fun File.walkExistingFiles(): Sequence<File> =
    if (exists()) walkTopDown().filter(File::isFile) else emptySequence()
```

- [ ] **Step 4: Run verification**

Run:

```bash
sh gradlew --no-daemon :app:compileDebugKotlin
```

Expected: compile succeeds.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/diagnostics/DiagnosticsSettings.kt \
  app/src/main/kotlin/org/rtkcollector/app/diagnostics/DiagnosticsStore.kt \
  app/src/test/kotlin/org/rtkcollector/app/diagnostics/DiagnosticsStoreTest.kt
git commit -m "Add diagnostics settings and storage"
```

### Task 3: ZIP Export And FileProvider Path

**Files:**
- Create: `app/src/main/kotlin/org/rtkcollector/app/diagnostics/DiagnosticsZipExporter.kt`
- Modify: `app/src/main/res/xml/file_paths.xml`
- Test: `app/src/test/kotlin/org/rtkcollector/app/diagnostics/DiagnosticsZipExporterTest.kt`
- Test: `app/src/test/kotlin/org/rtkcollector/app/ui/FileProviderPathsTest.kt`

- [ ] **Step 1: Write ZIP and FileProvider tests**

```kotlin
package org.rtkcollector.app.diagnostics

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.zip.ZipFile

class DiagnosticsZipExporterTest {
    @TempDir lateinit var tempDir: Path

    @Test
    fun `runtime zip contains summary readme and runtime jsonl`() {
        val store = DiagnosticsStore(tempDir.resolve("files").toFile())
        store.appendRuntime(true, RuntimeDiagnosticRecord(1, DiagnosticCategory.USB, "FATAL", "USB failed"))
        val exporter = DiagnosticsZipExporter(tempDir.resolve("cache").toFile())

        val zip = exporter.exportRuntimeLogs(store, DiagnosticsStatus(true, false, 10, 0, 1, 0))

        ZipFile(zip).use { opened ->
            val names = opened.entries().asSequence().map { it.name }.toSet()
            assertTrue("diagnostic-summary.json" in names)
            assertTrue("README.txt" in names)
            assertTrue(names.any { it.startsWith("runtime/") && it.endsWith(".jsonl") })
        }
    }
}
```

Extend `FileProviderPathsTest` with:

```kotlin
@Test
fun `file provider exposes temporary diagnostics share cache directory`() {
    val xml = sourceFile("src/main/res/xml/file_paths.xml")
    val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(Files.newInputStream(xml))
    val nodes = document.getElementsByTagName("cache-path")

    val exposesDiagnosticsShareCache = (0 until nodes.length).any { index ->
        val node = nodes.item(index)
        val attributes = node.attributes
        attributes.getNamedItem("android:name")?.nodeValue == "diagnostic_share" &&
            attributes.getNamedItem("android:path")?.nodeValue == "diagnostic-share/"
    }

    assertTrue(exposesDiagnosticsShareCache, "FileProvider must expose cache/diagnostic-share for diagnostics sharing.")
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run:

```bash
sh gradlew --no-daemon :app:compileDebugKotlin
```

Expected: compile fails because `DiagnosticsZipExporter` does not exist and the FileProvider path is absent.

- [ ] **Step 3: Implement ZIP export and FileProvider path**

Create `DiagnosticsZipExporter.kt`:

```kotlin
package org.rtkcollector.app.diagnostics

import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DiagnosticsZipExporter(private val cacheDir: File) {
    private val shareDir: File = cacheDir.resolve("diagnostic-share")

    fun exportRuntimeLogs(store: DiagnosticsStore, status: DiagnosticsStatus): File =
        export(
            namePrefix = "rtkcollector-runtime-logs",
            summaryName = "diagnostic-summary.json",
            summary = status.toSummaryJson("runtime"),
            files = store.runtimeFiles().map { "runtime/${it.name}" to it },
        )

    fun exportPerformanceLogs(store: DiagnosticsStore, status: DiagnosticsStatus): File =
        export(
            namePrefix = "rtkcollector-performance",
            summaryName = "performance-summary.json",
            summary = status.toSummaryJson("performance"),
            files = store.performanceFiles().map { "performance/${it.name}" to it },
        )

    fun deleteTemporaryShares() {
        shareDir.deleteRecursively()
    }

    private fun export(
        namePrefix: String,
        summaryName: String,
        summary: String,
        files: List<Pair<String, File>>,
    ): File {
        shareDir.mkdirs()
        val zip = shareDir.resolve("$namePrefix-${System.currentTimeMillis()}.zip")
        ZipOutputStream(zip.outputStream().buffered()).use { out ->
            out.putTextEntry(summaryName, summary)
            out.putTextEntry(
                "README.txt",
                "RtkCollector diagnostics are opt-in. Secrets are redacted before records are written.\n",
            )
            files.forEach { (entryName, file) ->
                if (file.isFile) {
                    out.putNextEntry(ZipEntry(entryName))
                    file.inputStream().use { input -> input.copyTo(out) }
                    out.closeEntry()
                }
            }
        }
        return zip
    }
}

private fun DiagnosticsStatus.toSummaryJson(kind: String): String =
    JSONObject()
        .put("kind", kind)
        .put("runtimeLoggingEnabled", runtimeLoggingEnabled)
        .put("performanceMonitoringEnabled", performanceMonitoringEnabled)
        .put("runtimeBytes", runtimeBytes)
        .put("performanceBytes", performanceBytes)
        .put("runtimeFiles", runtimeFiles)
        .put("performanceFiles", performanceFiles)
        .toString(2)

private fun ZipOutputStream.putTextEntry(name: String, text: String) {
    putNextEntry(ZipEntry(name))
    write(text.toByteArray(Charsets.UTF_8))
    closeEntry()
}
```

Modify `file_paths.xml`:

```xml
<cache-path
    name="diagnostic_share"
    path="diagnostic-share/" />
```

- [ ] **Step 4: Run verification**

Run:

```bash
sh gradlew --no-daemon :app:compileDebugKotlin
```

Expected: compile succeeds.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/diagnostics/DiagnosticsZipExporter.kt \
  app/src/main/res/xml/file_paths.xml \
  app/src/test/kotlin/org/rtkcollector/app/diagnostics/DiagnosticsZipExporterTest.kt \
  app/src/test/kotlin/org/rtkcollector/app/ui/FileProviderPathsTest.kt
git commit -m "Add diagnostics ZIP sharing support"
```

### Task 4: Runtime And Performance Controllers

**Files:**
- Create: `app/src/main/kotlin/org/rtkcollector/app/diagnostics/RuntimeDiagnostics.kt`
- Create: `app/src/main/kotlin/org/rtkcollector/app/diagnostics/PerformanceDiagnostics.kt`
- Test: `app/src/test/kotlin/org/rtkcollector/app/diagnostics/PerformanceDiagnosticsTest.kt`

- [ ] **Step 1: Write controller tests**

```kotlin
package org.rtkcollector.app.diagnostics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class PerformanceDiagnosticsTest {
    @TempDir lateinit var tempDir: Path

    @Test
    fun `disabled performance recorder writes no samples`() {
        val store = DiagnosticsStore(tempDir.toFile())
        val recorder = PerformanceDiagnostics(store) { false }

        recorder.recordIfDue(nowMillis = 10_000L) {
            PerformanceDiagnosticSample(10_000L, 1, 2, 3, 4, 5, 6, 7, null)
        }

        assertEquals(0, store.status(false, false).performanceBytes)
    }

    @Test
    fun `enabled performance recorder samples only after interval`() {
        val store = DiagnosticsStore(tempDir.toFile())
        var enabled = true
        val recorder = PerformanceDiagnostics(store, sampleIntervalMillis = 5_000L) { enabled }

        recorder.recordIfDue(nowMillis = 1_000L) { PerformanceDiagnosticSample(1_000L, 1, 2, 3, 4, 5, 6, 7, null) }
        recorder.recordIfDue(nowMillis = 2_000L) { PerformanceDiagnosticSample(2_000L, 9, 9, 9, 9, 9, 9, 9, null) }
        recorder.recordIfDue(nowMillis = 6_100L) { PerformanceDiagnosticSample(6_100L, 8, 8, 8, 8, 8, 8, 8, null) }
        enabled = false
        recorder.recordIfDue(nowMillis = 12_000L) { PerformanceDiagnosticSample(12_000L, 7, 7, 7, 7, 7, 7, 7, null) }

        val text = tempDir.resolve("diagnostics/performance/performance.jsonl").toFile().readText()
        assertEquals(2, text.lineSequence().filter(String::isNotBlank).count())
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
sh gradlew --no-daemon :app:compileDebugKotlin
```

Expected: compile fails because `PerformanceDiagnostics` does not exist.

- [ ] **Step 3: Implement controllers**

Create `RuntimeDiagnostics.kt`:

```kotlin
package org.rtkcollector.app.diagnostics

class RuntimeDiagnostics(
    private val store: DiagnosticsStore,
    private val enabled: () -> Boolean,
) {
    val isEnabled: Boolean
        get() = enabled()

    fun record(record: RuntimeDiagnosticRecord) {
        if (!enabled()) return
        runCatching { store.appendRuntime(enabled = true, record = record) }
    }
}
```

Create `PerformanceDiagnostics.kt`:

```kotlin
package org.rtkcollector.app.diagnostics

class PerformanceDiagnostics(
    private val store: DiagnosticsStore,
    private val sampleIntervalMillis: Long = 5_000L,
    private val enabled: () -> Boolean,
) {
    private var lastSampleAtMillis: Long = Long.MIN_VALUE

    val isEnabled: Boolean
        get() = enabled()

    fun recordIfDue(nowMillis: Long, sample: () -> PerformanceDiagnosticSample) {
        if (!enabled()) return
        if (lastSampleAtMillis != Long.MIN_VALUE && nowMillis - lastSampleAtMillis < sampleIntervalMillis) return
        lastSampleAtMillis = nowMillis
        runCatching { store.appendPerformance(enabled = true, sample = sample()) }
    }
}
```

- [ ] **Step 4: Run verification**

Run:

```bash
sh gradlew --no-daemon :app:compileDebugKotlin
```

Expected: compile succeeds.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/diagnostics/RuntimeDiagnostics.kt \
  app/src/main/kotlin/org/rtkcollector/app/diagnostics/PerformanceDiagnostics.kt \
  app/src/test/kotlin/org/rtkcollector/app/diagnostics/PerformanceDiagnosticsTest.kt
git commit -m "Add diagnostics runtime controllers"
```

### Task 5: App Diagnostics UI And Menu Wiring

**Files:**
- Create: `app/src/main/kotlin/org/rtkcollector/app/ui/diagnostics/AppDiagnosticsScreen.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/settings/SettingsHub.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`

- [ ] **Step 1: Add UI screen**

Create `AppDiagnosticsScreen.kt`:

```kotlin
package org.rtkcollector.app.ui.diagnostics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.rtkcollector.app.diagnostics.DiagnosticsStatus
import org.rtkcollector.app.ui.common.TidyColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDiagnosticsScreen(
    status: DiagnosticsStatus,
    lastError: String?,
    onRuntimeLoggingChange: (Boolean) -> Unit,
    onPerformanceMonitoringChange: (Boolean) -> Unit,
    onShareRuntimeLogs: () -> Unit,
    onSharePerformanceLogs: () -> Unit,
    onDeleteLogs: () -> Unit,
    onBack: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App logs and performance monitoring") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DiagnosticsCard("Runtime logging") {
                ToggleRow("Runtime logging", status.runtimeLoggingEnabled, onRuntimeLoggingChange)
                Text("Stored: ${status.runtimeFiles} files, ${status.runtimeBytes} bytes")
                Button(onClick = onShareRuntimeLogs, enabled = status.runtimeBytes > 0L) {
                    Text("Share runtime logs")
                }
            }
            DiagnosticsCard("Performance monitoring") {
                ToggleRow("Performance monitoring", status.performanceMonitoringEnabled, onPerformanceMonitoringChange)
                Text("Stored: ${status.performanceFiles} files, ${status.performanceBytes} bytes")
                Button(onClick = onSharePerformanceLogs, enabled = status.performanceBytes > 0L) {
                    Text("Share performance logs")
                }
            }
            DiagnosticsCard("Maintenance") {
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    enabled = status.runtimeBytes > 0L || status.performanceBytes > 0L,
                ) {
                    Text("Delete logs")
                }
                if (!lastError.isNullOrBlank()) {
                    Text(lastError, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete diagnostic logs?") },
            text = { Text("This removes app logs and performance metrics. Recording sessions are not deleted.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDeleteLogs()
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun DiagnosticsCard(title: String, content: @Composable Column.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, TidyColors.Divider),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
```

- [ ] **Step 2: Wire settings menu**

Modify `SettingsHub` signature:

```kotlin
onAppDiagnostics: () -> Unit,
```

Replace the current device tools section with:

```kotlin
SettingsSection("Developer tools") {
    SettingsRow("IO", "Device console", onDeviceConsole)
    SettingsDivider()
    SettingsRow("LOG", "App logs and performance monitoring", onAppDiagnostics)
}
```

- [ ] **Step 3: Wire navigation and sharing in MainActivity**

Add import:

```kotlin
import org.rtkcollector.app.diagnostics.DiagnosticsSettings
import org.rtkcollector.app.diagnostics.DiagnosticsStore
import org.rtkcollector.app.diagnostics.DiagnosticsZipExporter
import org.rtkcollector.app.ui.diagnostics.AppDiagnosticsScreen
```

Add remembered objects near existing `context`/state setup:

```kotlin
val diagnosticsSettings = remember(context) { DiagnosticsSettings(context) }
val diagnosticsStore = remember(context) { DiagnosticsStore(context.filesDir) }
val diagnosticsZipExporter = remember(context) { DiagnosticsZipExporter(context.cacheDir) }
var diagnosticsRevision by remember { mutableStateOf(0) }
var diagnosticsError by remember { mutableStateOf<String?>(null) }
val diagnosticsStatus = remember(diagnosticsRevision) {
    diagnosticsStore.status(
        runtimeEnabled = diagnosticsSettings.runtimeLoggingEnabled,
        performanceEnabled = diagnosticsSettings.performanceMonitoringEnabled,
    )
}
```

Add screen enum value:

```kotlin
APP_DIAGNOSTICS,
```

Pass callback into `SettingsHub`:

```kotlin
onAppDiagnostics = { screen = AppScreen.APP_DIAGNOSTICS },
```

Add screen case:

```kotlin
AppScreen.APP_DIAGNOSTICS -> AppDiagnosticsScreen(
    status = diagnosticsStatus,
    lastError = diagnosticsError,
    onRuntimeLoggingChange = { enabled ->
        diagnosticsSettings.runtimeLoggingEnabled = enabled
        diagnosticsRevision++
    },
    onPerformanceMonitoringChange = { enabled ->
        diagnosticsSettings.performanceMonitoringEnabled = enabled
        diagnosticsRevision++
    },
    onShareRuntimeLogs = {
        runCatching {
            shareDiagnosticZip(context, diagnosticsZipExporter.exportRuntimeLogs(diagnosticsStore, diagnosticsStatus))
        }.onFailure { diagnosticsError = it.message ?: it.javaClass.simpleName }
    },
    onSharePerformanceLogs = {
        runCatching {
            shareDiagnosticZip(context, diagnosticsZipExporter.exportPerformanceLogs(diagnosticsStore, diagnosticsStatus))
        }.onFailure { diagnosticsError = it.message ?: it.javaClass.simpleName }
    },
    onDeleteLogs = {
        runCatching {
            diagnosticsStore.deleteDiagnostics()
            diagnosticsZipExporter.deleteTemporaryShares()
            diagnosticsRevision++
            diagnosticsError = null
        }.onFailure { diagnosticsError = it.message ?: it.javaClass.simpleName }
    },
    onBack = { screen = AppScreen.SETTINGS },
)
```

Add helper beside existing share helpers:

```kotlin
private fun shareDiagnosticZip(context: Context, zipFile: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zipFile)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/zip"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share RtkCollector diagnostics"))
}
```

- [ ] **Step 4: Run verification**

Run:

```bash
sh gradlew --no-daemon :app:compileDebugKotlin
```

Expected: compile succeeds.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/ui/diagnostics/AppDiagnosticsScreen.kt \
  app/src/main/kotlin/org/rtkcollector/app/ui/settings/SettingsHub.kt \
  app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt
git commit -m "Add app diagnostics menu screen"
```

### Task 6: Service And Activity Diagnostic Hooks

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt`

- [ ] **Step 1: Add guarded Activity runtime logging**

In `MainActivity`, create a `RuntimeDiagnostics` instance:

```kotlin
val runtimeDiagnostics = remember(context) {
    RuntimeDiagnostics(diagnosticsStore) { diagnosticsSettings.runtimeLoggingEnabled }
}
```

When importing settings fails or diagnostics sharing fails, guard before building records:

```kotlin
if (runtimeDiagnostics.isEnabled) {
    runtimeDiagnostics.record(
        RuntimeDiagnosticRecord(
            timestampMillis = System.currentTimeMillis(),
            category = DiagnosticCategory.APP,
            severity = "DEGRADED",
            message = "Settings import failed: ${error.message ?: error.javaClass.simpleName}",
        ),
    )
}
```

Use the same guard pattern for diagnostics share/delete failures. Do not build the message unless `runtimeDiagnostics.isEnabled` is true.

- [ ] **Step 2: Add service diagnostics objects**

In `RecordingForegroundService`, add lazy fields:

```kotlin
private val diagnosticsSettings by lazy { DiagnosticsSettings(this) }
private val diagnosticsStore by lazy { DiagnosticsStore(filesDir) }
private val runtimeDiagnostics by lazy {
    RuntimeDiagnostics(diagnosticsStore) { diagnosticsSettings.runtimeLoggingEnabled }
}
private val performanceDiagnostics by lazy {
    PerformanceDiagnostics(diagnosticsStore) { diagnosticsSettings.performanceMonitoringEnabled }
}
```

- [ ] **Step 3: Add small helper methods in service**

```kotlin
private fun recordRuntimeDiagnostic(
    category: DiagnosticCategory,
    severity: String,
    message: () -> String,
    attributes: () -> Map<String, String> = { emptyMap() },
) {
    if (!runtimeDiagnostics.isEnabled) return
    runtimeDiagnostics.record(
        RuntimeDiagnosticRecord(
            timestampMillis = System.currentTimeMillis(),
            category = category,
            severity = severity,
            message = message(),
            attributes = attributes(),
        ),
    )
}

private fun recordPerformanceDiagnosticIfDue() {
    if (!performanceDiagnostics.isEnabled) return
    val now = System.currentTimeMillis()
    performanceDiagnostics.recordIfDue(now) {
        val runtime = Runtime.getRuntime()
        PerformanceDiagnosticSample(
            timestampMillis = now,
            receiverRxBytes = state.receiverRxBytes,
            correctionInputBytes = state.correctionInputBytes,
            txToReceiverBytes = state.txToReceiverBytes,
            sessionTotalBytes = state.sessionTotalBytes,
            heapUsedBytes = runtime.totalMemory() - runtime.freeMemory(),
            heapMaxBytes = runtime.maxMemory(),
            threadCount = Thread.activeCount(),
            mockLastIntervalMs = state.mockLocationLastIntervalMs,
        )
    }
}
```

- [ ] **Step 4: Call service hooks only after state changes**

Add `recordPerformanceDiagnosticIfDue()` at the end of `broadcastState()` after the `sendBroadcast(...)` call. This samples from already-built low-rate state, not the raw capture path.

For key error blocks where `state = state.copy(lastError = ...)` already happens, add guarded runtime records immediately after the state update:

```kotlin
recordRuntimeDiagnostic(RecordingErrorCategory.USB.toDiagnosticCategory(), "DEGRADED", { state.lastError ?: "USB degraded" })
```

Add mapper:

```kotlin
private fun RecordingErrorCategory.toDiagnosticCategory(): DiagnosticCategory =
    when (this) {
        RecordingErrorCategory.USB -> DiagnosticCategory.USB
        RecordingErrorCategory.STORAGE -> DiagnosticCategory.STORAGE
        RecordingErrorCategory.NTRIP -> DiagnosticCategory.NTRIP
        RecordingErrorCategory.RECEIVER_COMMAND -> DiagnosticCategory.RECEIVER_COMMAND
        RecordingErrorCategory.PARSER_EXPORT -> DiagnosticCategory.SERVICE
        RecordingErrorCategory.SERVICE_LIFECYCLE -> DiagnosticCategory.SERVICE
        RecordingErrorCategory.NONE -> DiagnosticCategory.APP
    }
```

Use broad hooks at existing consolidated points first:

- start failure catch block around `classifyStartError`;
- USB degraded reconnect/open failures;
- NTRIP update policy failure;
- persistent receiver config failure;
- close/storage report failure;
- mock-location setup/update failure.

Do not add logging inside receiver byte read/write loops.

- [ ] **Step 5: Run verification**

Run:

```bash
sh gradlew --no-daemon :app:compileDebugKotlin
```

Expected: compile succeeds.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt \
  app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt
git commit -m "Wire opt-in diagnostics into app runtime"
```

### Task 7: Formal Docs And Plan Status

**Files:**
- Modify: `docs/specification/security-privacy.md`
- Modify: `docs/specification/android-runtime.md`
- Modify: `docs/specification/verification-matrix.md`
- Modify: `docs/superpowers/plan-status.md`

- [ ] **Step 1: Add formal security/privacy requirement**

Add to `security-privacy.md`:

```markdown
## Diagnostics

### SEC-DIAGNOSTICS-001: Diagnostics Are Opt-In And Redacted

Status: Normative

Runtime diagnostics and performance monitoring MUST be disabled by default.
When enabled, diagnostic records MUST redact NTRIP passwords, authorization
headers, tokens and credential-like fields before writing or sharing.

Verification:
- Automated: diagnostics redaction and disabled-state tests.
- Review: diagnostic call sites use guarded construction for hot-path records.
```

- [ ] **Step 2: Add Android runtime requirement**

Add to `android-runtime.md`:

```markdown
### ANDROID-DIAGNOSTICS-001: Diagnostics Do Not Block Recording

Status: Normative

Runtime logging and performance monitoring MUST NOT run on the raw receiver
capture path. When disabled, they MUST avoid timers, file I/O, JSON
construction and metric aggregation. When enabled, diagnostic failures MUST NOT
stop recording or mutate session artifacts.

Verification:
- Automated: diagnostics controller disabled-state tests.
- Review: service hooks are after state updates and outside receiver byte loops.
```

- [ ] **Step 3: Add verification matrix rows**

Add rows for:

```markdown
| SEC-DIAGNOSTICS-001 | Automated | Diagnostics redaction and disabled-state tests | `DiagnosticsRedactionTest`, `DiagnosticsStoreTest` |
| ANDROID-DIAGNOSTICS-001 | Automated + review | Diagnostics controller tests and service call-site review | `PerformanceDiagnosticsTest`; review `RecordingForegroundService` hooks |
```

- [ ] **Step 4: Update plan status**

Add or update a row in `docs/superpowers/plan-status.md`:

```markdown
| App logs and performance monitoring | `2026-06-21-diagnostics-share-logs-design.md`, `2026-06-21-app-logs-and-performance-monitoring.md` | Implemented, not field-tested | App-private opt-in runtime logs and performance metrics can be shared/deleted; field validation on Android devices remains recommended. |
```

- [ ] **Step 5: Run documentation check**

Run:

```bash
git diff --check
```

Expected: no output.

- [ ] **Step 6: Commit**

```bash
git add docs/specification/security-privacy.md docs/specification/android-runtime.md \
  docs/specification/verification-matrix.md docs/superpowers/plan-status.md
git commit -m "Document app diagnostics requirements"
```

### Task 8: Final Verification And Push

**Files:**
- Inspect all changed files.

- [ ] **Step 1: Run final diff hygiene**

Run:

```bash
git diff --check
git status --short
```

Expected: no whitespace errors; only intended files changed or clean after task commits.

- [ ] **Step 2: Run compile gate**

Run:

```bash
sh gradlew --no-daemon :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Run feasible targeted tests if environment supports them**

Run:

```bash
sh gradlew --no-daemon :app:compileDebugUnitTestKotlin
```

Expected in Termux may be blocked by the known AAPT2 native binary limitation. If blocked by AAPT2, record the exact failure and do not keep retrying.

- [ ] **Step 4: Review for architecture violations**

Check:

```bash
rg -n "Diagnostics|RuntimeDiagnostics|PerformanceDiagnostics|recordRuntimeDiagnostic|recordPerformanceDiagnosticIfDue" app/src/main/kotlin/org/rtkcollector/app
rg -n "receiver-rx.raw|correction-input.raw|tx-to-receiver.raw" app/src/main/kotlin/org/rtkcollector/app/diagnostics app/src/main/kotlin/org/rtkcollector/app/ui/diagnostics
```

Expected:

- diagnostics do not write to session artifacts;
- no diagnostic code is inside raw receiver read/write loops;
- diagnostic sharing uses `cache/diagnostic-share`;
- call sites guard message/sample construction before enabled diagnostics.

- [ ] **Step 5: Commit any final review fixes**

If final review changes files, commit them:

```bash
git add <changed-files>
git commit -m "Polish app diagnostics implementation"
```

- [ ] **Step 6: Push**

Run:

```bash
git push
```

Expected: remote `main` updated.

## Subagent Model Guidance

- Use a cheaper/fast model for Tasks 1-4 because they are pure Kotlin, isolated and testable.
- Use a standard model for Task 5 because it touches Compose and navigation.
- Use the strongest available reasoning model for Task 6 and final review because these require architecture judgment about capture-path isolation.
- Do not dispatch implementation subagents in parallel against the same files.

## Self-Review

- Spec coverage: Tasks 1-4 cover redaction, settings, bounded storage, ZIP sharing and disabled-state controllers. Task 5 covers menu/UI. Task 6 covers Activity/service integration and performance sampling. Task 7 covers formal requirements and verification matrix. Task 8 covers validation and push.
- Placeholder scan: this plan does not use TBD/TODO placeholders.
- Type consistency: model names, function names and file paths are consistent across tasks.
