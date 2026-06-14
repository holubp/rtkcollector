# Session NMEA Export, Error Expiry And Dashboard Overflow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add direct temporary NMEA sharing, make session selection toggle between select/unselect all, expire stale dashboard errors, and prevent dashboard card content clipping on narrow portrait devices.

**Architecture:** Keep raw capture and session writing unchanged. Add small pure Kotlin model helpers for session selection, NMEA share planning, and dashboard error visibility; then wire those helpers into existing Compose screens and Android share intents. Dashboard layout changes should remove clipping by letting metric-heavy cards grow inside the existing scrollable content.

**Tech Stack:** Android/Kotlin, Jetpack Compose, FileProvider share intents, JUnit 5, existing `app` and `core/session` modules.

---

## File Structure

Create:

- `app/src/main/kotlin/org/rtkcollector/app/recording/SessionNmeaExporter.kt` - pure Kotlin planner/copy helper for temporary NMEA share files.
- `app/src/test/kotlin/org/rtkcollector/app/recording/SessionNmeaExporterTest.kt` - tests for NMEA eligibility, filename and copy behaviour.
- `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardErrorVisibility.kt` - pure Kotlin error visibility policy.
- `app/src/test/kotlin/org/rtkcollector/app/ui/dashboard/DashboardErrorVisibilityTest.kt` - tests for 15-second expiry and fatal persistence.

Modify:

- `app/src/main/kotlin/org/rtkcollector/app/sessions/SessionBrowserModels.kt` - add select/unselect-all model helpers.
- `app/src/test/kotlin/org/rtkcollector/app/sessions/SessionBrowserModelsTest.kt` - cover selection toggle and label.
- `app/src/main/kotlin/org/rtkcollector/app/ui/sessions/SessionsScreen.kt` - add `Share NMEA` button and dynamic `Select all` label.
- `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt` - wire NMEA share action, cleanup and session task progress.
- `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/HomeDashboard.kt` - apply error expiry and replace clipping card heights with minimum/growing heights.
- `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardModels.kt` - keep clipboard text compatible with visible error policy if needed.
- `app/src/test/kotlin/org/rtkcollector/app/ui/dashboard/DashboardStateTest.kt` or `DashboardFormattersTest.kt` - add any extracted sizing-policy tests if sizing is modelled.
- `docs/user-workflows.md` - document direct NMEA sharing.
- `docs/session-format.md` - document that `receiver-solution.nmea` is the share source.

Do not modify:

- `receiver-rx.raw` writing.
- `tx-to-receiver.raw` writing.
- `correction-input.raw` / `.rtcm3` writing.
- USB read/write loops.
- NTRIP protocol behaviour.
- UM980 parsing.
- Google Play readiness plan.

---

### Task 1: Session Selection Toggle Model

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/sessions/SessionBrowserModels.kt`
- Modify: `app/src/test/kotlin/org/rtkcollector/app/sessions/SessionBrowserModelsTest.kt`

- [ ] **Step 1: Write failing tests for select/unselect-all**

Append these tests to `SessionBrowserModelsTest`:

```kotlin
@Test
fun `select all button label changes when all selectable entries are selected`() {
    val state = sessionBrowserStateOf(
        listOf(
            entry("active", SessionEntryKind.CURRENT_ACTIVE, 30),
            entry("stopped", SessionEntryKind.CURRENT_STOPPED, 20),
            entry("recording", SessionEntryKind.RECORDING, 10),
        ),
    )

    assertEquals("Select all", state.selectAllButtonLabel)

    val selected = state.selectAll()

    assertEquals(setOf("stopped", "recording"), selected.selectedIds)
    assertEquals("Unselect all", selected.selectAllButtonLabel)
}

@Test
fun `toggle select all clears when every selectable entry is selected`() {
    val state = sessionBrowserStateOf(
        listOf(
            entry("stopped", SessionEntryKind.CURRENT_STOPPED, 20),
            entry("recording", SessionEntryKind.RECORDING, 10),
            entry("archive", SessionEntryKind.ARCHIVE, 5),
        ),
    ).selectAll()

    assertEquals("Unselect all", state.selectAllButtonLabel)

    val cleared = state.toggleSelectAll()

    assertEquals(emptySet<String>(), cleared.selectedIds)
    assertEquals("Select all", cleared.selectAllButtonLabel)
}

@Test
fun `toggle select all selects when selection is partial`() {
    val state = sessionBrowserStateOf(
        listOf(
            entry("stopped", SessionEntryKind.CURRENT_STOPPED, 20),
            entry("recording", SessionEntryKind.RECORDING, 10),
            entry("archive", SessionEntryKind.ARCHIVE, 5),
        ),
        selectedIds = setOf("stopped"),
    )

    assertEquals("Select all", state.selectAllButtonLabel)

    val selected = state.toggleSelectAll()

    assertEquals(setOf("stopped", "recording", "archive"), selected.selectedIds)
    assertEquals("Unselect all", selected.selectAllButtonLabel)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run on a desktop/CI host when available:

```bash
./gradlew :app:testDebugUnitTest --tests 'org.rtkcollector.app.sessions.SessionBrowserModelsTest'
```

Expected: fails because `selectAllButtonLabel` and `toggleSelectAll()` do not exist.

In Termux, skip repeated app unit-test retries if Android resource processing fails with the known `aapt2` problem.

- [ ] **Step 3: Implement selection helpers**

In `SessionBrowserState`, add:

```kotlin
val hasSelectableEntries: Boolean
    get() = selectableEntries().isNotEmpty()

val allSelectableSelected: Boolean
    get() {
        val selectable = selectableEntries().map { it.id }.toSet()
        return selectable.isNotEmpty() && selectedIds.containsAll(selectable)
    }

val selectAllButtonLabel: String
    get() = if (allSelectableSelected) "Unselect all" else "Select all"
```

Add:

```kotlin
fun toggleSelectAll(): SessionBrowserState =
    if (allSelectableSelected) {
        clearSelection()
    } else {
        selectAll()
    }
```

Keep `selectableEntries()` scoped inside the model and continue using the existing `canDelete` definition for selectable entries.

- [ ] **Step 4: Run tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'org.rtkcollector.app.sessions.SessionBrowserModelsTest'
```

Expected: passes on desktop/CI.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/sessions/SessionBrowserModels.kt app/src/test/kotlin/org/rtkcollector/app/sessions/SessionBrowserModelsTest.kt
git commit -m "Add session browser select-all toggle model"
```

---

### Task 2: Direct NMEA Share Planner

**Files:**
- Create: `app/src/main/kotlin/org/rtkcollector/app/recording/SessionNmeaExporter.kt`
- Create: `app/src/test/kotlin/org/rtkcollector/app/recording/SessionNmeaExporterTest.kt`

- [ ] **Step 1: Write failing NMEA share planner tests**

Create `SessionNmeaExporterTest.kt`:

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
    fun `plan for session uses receiver solution nmea and nmea suffix`() {
        val session = Files.createDirectory(tempDir.resolve("session-2026-06-14T12-00-00Z-abc"))
        Files.writeString(session.resolve("receiver-solution.nmea"), "$GPGGA,data\n")
        val cache = Files.createDirectory(tempDir.resolve("cache"))

        val plan = SessionNmeaSharePlan.fromSessionDirectory(session, cache)

        assertEquals(session.resolve("receiver-solution.nmea"), plan.sourceNmea)
        assertEquals(cache.resolve("session-2026-06-14T12-00-00Z-abc.nmea"), plan.outputNmea)
    }

    @Test
    fun `selection skips sessions without receiver solution nmea`() {
        val withNmea = Files.createDirectory(tempDir.resolve("with-nmea"))
        Files.writeString(withNmea.resolve("receiver-solution.nmea"), "$GPGGA,data\n")
        val withoutNmea = Files.createDirectory(tempDir.resolve("without-nmea"))
        val cache = Files.createDirectory(tempDir.resolve("cache"))

        val selection = SessionNmeaShareSelection.fromSessionDirectories(
            sessionDirectories = listOf(withNmea, withoutNmea),
            outputDirectory = cache,
        )

        assertEquals(1, selection.plans.size)
        assertEquals(1, selection.skippedCount)
        assertTrue(selection.hasShareableNmea)
    }

    @Test
    fun `selection reports no shareable nmea when all sessions are missing sidecar`() {
        val session = Files.createDirectory(tempDir.resolve("session"))
        val cache = Files.createDirectory(tempDir.resolve("cache"))

        val selection = SessionNmeaShareSelection.fromSessionDirectories(listOf(session), cache)

        assertEquals(emptyList<SessionNmeaSharePlan>(), selection.plans)
        assertEquals(1, selection.skippedCount)
        assertFalse(selection.hasShareableNmea)
    }

    @Test
    fun `export copies nmea bytes without altering source`() {
        val session = Files.createDirectory(tempDir.resolve("session"))
        val source = session.resolve("receiver-solution.nmea")
        Files.writeString(source, "$GPGGA,data\n$GPRMC,data\n")
        val cache = Files.createDirectory(tempDir.resolve("cache"))
        val plan = SessionNmeaSharePlan.fromSessionDirectory(session, cache)

        val output = SessionNmeaExporter.export(plan)

        assertEquals("$GPGGA,data\n$GPRMC,data\n", Files.readString(output))
        assertEquals("$GPGGA,data\n$GPRMC,data\n", Files.readString(source))
    }
}
```

- [ ] **Step 2: Run tests to verify failure**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'org.rtkcollector.app.recording.SessionNmeaExporterTest'
```

Expected: fails because `SessionNmeaExporter.kt` does not exist.

- [ ] **Step 3: Implement NMEA planner and exporter**

Create `SessionNmeaExporter.kt`:

```kotlin
package org.rtkcollector.app.recording

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

private const val RECEIVER_SOLUTION_NMEA = "receiver-solution.nmea"

data class SessionNmeaSharePlan(
    val sourceNmea: Path,
    val outputNmea: Path,
) {
    init {
        require(sourceNmea.fileName.toString() == RECEIVER_SOLUTION_NMEA) {
            "NMEA share source must be receiver-solution.nmea."
        }
        require(outputNmea.fileName.toString().endsWith(".nmea")) {
            "NMEA share output must use .nmea suffix."
        }
    }

    companion object {
        fun fromSessionDirectory(sessionDirectory: Path, outputDirectory: Path): SessionNmeaSharePlan {
            require(Files.isDirectory(sessionDirectory)) { "NMEA share source must be a session directory." }
            val source = sessionDirectory.resolve(RECEIVER_SOLUTION_NMEA)
            require(Files.isRegularFile(source)) { "Session has no receiver-solution.nmea." }
            return SessionNmeaSharePlan(
                sourceNmea = source,
                outputNmea = outputDirectory.resolve("${sessionDirectory.fileName}.nmea"),
            )
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
        ): SessionNmeaShareSelection {
            val plans = mutableListOf<SessionNmeaSharePlan>()
            var skipped = 0
            sessionDirectories.forEach { session ->
                val plan = runCatching {
                    SessionNmeaSharePlan.fromSessionDirectory(session, outputDirectory)
                }.getOrNull()
                if (plan == null) {
                    skipped++
                } else {
                    plans += plan
                }
            }
            return SessionNmeaShareSelection(plans = plans, skippedCount = skipped)
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

- [ ] **Step 4: Run tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'org.rtkcollector.app.recording.SessionNmeaExporterTest'
```

Expected: passes on desktop/CI.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/recording/SessionNmeaExporter.kt app/src/test/kotlin/org/rtkcollector/app/recording/SessionNmeaExporterTest.kt
git commit -m "Add direct session NMEA share planner"
```

---

### Task 3: Wire NMEA Sharing Into Session Browser UI

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/sessions/SessionsScreen.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`

- [ ] **Step 1: Update `SessionsScreen` API and buttons**

Change the `SessionsScreen` parameters:

```kotlin
onSelectAll: () -> Unit,
onClearSelection: () -> Unit,
onShareSelected: () -> Unit,
onShareNmeaSelected: () -> Unit,
onArchiveSelected: () -> Unit,
```

After `val canShare = ...`, add:

```kotlin
val canShareNmea = selected.any(SessionBrowserEntry::canShareZip)
```

Replace the select-all button:

```kotlin
OutlinedButton(
    onClick = onSelectAll,
    enabled = state.hasSelectableEntries,
) {
    Text(state.selectAllButtonLabel)
}
```

Add the NMEA share button next to ZIP:

```kotlin
Button(onClick = onShareSelected, enabled = canShare) { Text("Share ZIP") }
Button(onClick = onShareNmeaSelected, enabled = canShareNmea) { Text("Share NMEA") }
```

Use `canShareZip` for the first UI enablement because NMEA availability is filesystem-probed in the background task. The background task will report missing NMEA sidecars.

- [ ] **Step 2: Update `MainActivity` imports**

Add imports:

```kotlin
import org.rtkcollector.app.recording.SessionNmeaExporter
import org.rtkcollector.app.recording.SessionNmeaShareSelection
```

- [ ] **Step 3: Wire select toggle**

In the `SessionsScreen` call, replace:

```kotlin
onSelectAll = { sessionBrowserState = sessionBrowserState.selectAll() },
```

with:

```kotlin
onSelectAll = { sessionBrowserState = sessionBrowserState.toggleSelectAll() },
```

- [ ] **Step 4: Add `onShareNmeaSelected` callback**

In the `SessionsScreen` call, add:

```kotlin
onShareNmeaSelected = {
    val selected = sessionBrowserState.selectedEntries.filter(SessionBrowserEntry::canShareZip)
    if (selected.isEmpty()) {
        Toast.makeText(context, "Select at least one completed recording.", Toast.LENGTH_LONG).show()
    } else {
        zipProgressText = "Preparing NMEA..."
        Thread {
            runCatching {
                val cacheRoot = context.cacheDir.resolve("session-share-nmea").toPath()
                cleanupTemporaryNmeaShares(cacheRoot)
                val selection = SessionNmeaShareSelection.fromSessionDirectories(
                    sessionDirectories = selected.map { Paths.get(it.location) },
                    outputDirectory = cacheRoot,
                )
                val outputs = selection.plans.mapIndexed { index, plan ->
                    runOnMain(context) {
                        zipProgressText = "NMEA ${index + 1}/${selection.plans.size}"
                    }
                    SessionNmeaExporter.export(plan)
                }
                selection to outputs
            }.onSuccess { (selection, outputs) ->
                runOnMain(context) {
                    zipProgressText = null
                    if (outputs.isEmpty()) {
                        Toast.makeText(
                            context,
                            "No recorded NMEA file is available for the selected session(s).",
                            Toast.LENGTH_LONG,
                        ).show()
                    } else {
                        shareNmeaFiles(context, outputs.map { it.toFile() })
                        val message = if (selection.skippedCount > 0) {
                            "Shared NMEA for ${outputs.size} session(s); ${selection.skippedCount} selected session(s) had no NMEA export."
                        } else {
                            "Shared NMEA for ${outputs.size} session(s)."
                        }
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                    refreshSessions()
                }
            }.onFailure { error ->
                runOnMain(context) {
                    zipProgressText = null
                    Toast.makeText(context, "Share NMEA failed: ${error.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }
},
```

- [ ] **Step 5: Add NMEA share functions**

Near `shareZipFiles`, add:

```kotlin
private fun shareNmeaFiles(context: Context, nmeaFiles: List<File>) {
    if (nmeaFiles.isEmpty()) return
    if (nmeaFiles.size == 1) {
        shareNmea(context, nmeaFiles.first())
        return
    }
    val uris = ArrayList(
        nmeaFiles.map { nmeaFile ->
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", nmeaFile)
        },
    )
    val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = "text/plain"
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share recording NMEA files"))
    scheduleTemporaryNmeaCleanup(nmeaFiles)
}

private fun shareNmea(context: Context, nmeaFile: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", nmeaFile)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share recording NMEA"))
    scheduleTemporaryNmeaCleanup(listOf(nmeaFile))
}
```

Add cleanup helpers:

```kotlin
private fun scheduleTemporaryNmeaCleanup(nmeaFiles: List<File>) {
    Handler(Looper.getMainLooper()).postDelayed(
        {
            runCatching {
                nmeaFiles.forEach { file ->
                    if (file.parentFile?.name == "session-share-nmea") {
                        file.delete()
                    }
                }
            }
        },
        TEMP_SHARE_ZIP_CLEANUP_DELAY_MILLIS,
    )
}

private fun cleanupTemporaryNmeaShares(cacheRoot: Path) {
    runCatching {
        if (Files.isDirectory(cacheRoot)) {
            Files.list(cacheRoot).use { stream ->
                stream
                    .filter { Files.isRegularFile(it) }
                    .filter { it.fileName.toString().endsWith(".nmea") }
                    .forEach { Files.deleteIfExists(it) }
            }
        }
    }
}
```

Use the existing cleanup delay constant to keep ZIP/NMEA temporary share semantics aligned.

- [ ] **Step 6: Compile**

Run:

```bash
sh gradlew :app:compileDebugKotlin
```

Expected in Termux: passes.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/ui/sessions/SessionsScreen.kt app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt
git commit -m "Wire direct NMEA sharing into sessions UI"
```

---

### Task 4: Dashboard Error Expiry Model

**Files:**
- Create: `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardErrorVisibility.kt`
- Create: `app/src/test/kotlin/org/rtkcollector/app/ui/dashboard/DashboardErrorVisibilityTest.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/HomeDashboard.kt`

- [ ] **Step 1: Write failing tests**

Create `DashboardErrorVisibilityTest.kt`:

```kotlin
package org.rtkcollector.app.ui.dashboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DashboardErrorVisibilityTest {
    @Test
    fun `blank error is hidden immediately`() {
        val snapshot = DashboardErrorSnapshot(category = "NTRIP", severity = "DEGRADED", message = "")

        assertFalse(snapshot.shouldDisplay(ageMillis = 0))
    }

    @Test
    fun `none none stale message is hidden immediately`() {
        val snapshot = DashboardErrorSnapshot(
            category = "NONE",
            severity = "NONE",
            message = "Software caused connection abort",
        )

        assertFalse(snapshot.shouldDisplay(ageMillis = 0))
    }

    @Test
    fun `non fatal error is visible before expiry and hidden after fifteen seconds`() {
        val snapshot = DashboardErrorSnapshot(
            category = "NTRIP",
            severity = "DEGRADED",
            message = "NTRIP connection failed",
        )

        assertTrue(snapshot.shouldDisplay(ageMillis = 14_999))
        assertFalse(snapshot.shouldDisplay(ageMillis = 15_000))
    }

    @Test
    fun `fatal error remains visible beyond expiry`() {
        val snapshot = DashboardErrorSnapshot(
            category = "USB",
            severity = "FATAL",
            message = "USB serial device could not be opened",
        )

        assertTrue(snapshot.shouldDisplay(ageMillis = 120_000))
    }

    @Test
    fun `fingerprint combines category severity and message`() {
        val snapshot = DashboardErrorSnapshot(
            category = "NTRIP",
            severity = "DEGRADED",
            message = "NTRIP connection failed",
        )

        assertEquals("NTRIP|DEGRADED|NTRIP connection failed", snapshot.fingerprint)
    }
}
```

- [ ] **Step 2: Run tests to verify failure**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'org.rtkcollector.app.ui.dashboard.DashboardErrorVisibilityTest'
```

Expected: fails because `DashboardErrorVisibility.kt` does not exist.

- [ ] **Step 3: Implement visibility model**

Create `DashboardErrorVisibility.kt`:

```kotlin
package org.rtkcollector.app.ui.dashboard

data class DashboardErrorSnapshot(
    val category: String,
    val severity: String,
    val message: String?,
) {
    val fingerprint: String
        get() = "${category.trim()}|${severity.trim()}|${message.orEmpty().trim()}"

    fun shouldDisplay(ageMillis: Long, expiryMillis: Long = DASHBOARD_ERROR_EXPIRY_MILLIS): Boolean {
        val text = message?.trim().orEmpty()
        if (text.isBlank()) return false
        if (category.equals("NONE", ignoreCase = true) && severity.equals("NONE", ignoreCase = true)) return false
        if (severity.equals("FATAL", ignoreCase = true)) return true
        return ageMillis < expiryMillis
    }
}

const val DASHBOARD_ERROR_EXPIRY_MILLIS: Long = 15_000
```

- [ ] **Step 4: Wire expiry into `HomeDashboard`**

In `HomeDashboard`, add:

```kotlin
var errorFingerprint by remember { mutableStateOf<String?>(null) }
var errorFirstSeenAtMillis by remember { mutableStateOf(0L) }
var errorClockTick by remember { mutableStateOf(0L) }
```

Add imports:

```kotlin
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
```

Build the snapshot near `copyErrorToClipboard`:

```kotlin
val errorSnapshot = DashboardErrorSnapshot(
    category = state.errorCategory,
    severity = state.errorSeverity,
    message = state.lastError,
)
val currentFingerprint = errorSnapshot.fingerprint
val now = System.currentTimeMillis()
if (errorFingerprint != currentFingerprint) {
    errorFingerprint = currentFingerprint
    errorFirstSeenAtMillis = now
}
LaunchedEffect(currentFingerprint) {
    while (true) {
        delay(1_000)
        errorClockTick = System.currentTimeMillis()
    }
}
val displayedError = if (errorSnapshot.shouldDisplay((errorClockTick.takeIf { it > 0 } ?: now) - errorFirstSeenAtMillis)) {
    errorSnapshot
} else {
    null
}
```

Change `ErrorStrip` calls from:

```kotlin
ErrorStrip(state = state, onCopy = onCopyError)
```

to:

```kotlin
ErrorStrip(snapshot = displayedError, onCopy = onCopyError)
```

Update `ErrorStrip`:

```kotlin
private fun ErrorStrip(
    snapshot: DashboardErrorSnapshot?,
    onCopy: () -> Unit,
) {
    val text = snapshot?.message?.takeIf { it.isNotBlank() }?.let { "${snapshot.category}: $it" } ?: return
    ...
}
```

Keep `state.errorClipboardText()` for clipboard copy so the copied text matches the most recent state while visible.

- [ ] **Step 5: Run tests and compile**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'org.rtkcollector.app.ui.dashboard.DashboardErrorVisibilityTest'
sh gradlew :app:compileDebugKotlin
```

Expected: targeted test passes on desktop/CI; Kotlin compile passes in Termux.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardErrorVisibility.kt app/src/test/kotlin/org/rtkcollector/app/ui/dashboard/DashboardErrorVisibilityTest.kt app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/HomeDashboard.kt
git commit -m "Expire stale dashboard errors"
```

---

### Task 5: Dashboard Card Overflow Fix

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/HomeDashboard.kt`
- Optional modify: `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardModels.kt`
- Optional test: `app/src/test/kotlin/org/rtkcollector/app/ui/dashboard/DashboardLayoutModelsTest.kt`

- [ ] **Step 1: Replace fixed card height with minimum height**

In `HomeDashboard.kt`, update imports:

```kotlin
import androidx.compose.foundation.layout.heightIn
```

In `DashboardCard`, replace:

```kotlin
modifier = modifier.fillMaxWidth().height(cardHeight),
```

with:

```kotlin
modifier = modifier.fillMaxWidth().heightIn(min = cardHeight),
```

This keeps current visual minimums but lets the Fix card grow when rows need more vertical space.

- [ ] **Step 2: Let metric rows grow if text or font scaling needs it**

Replace `Metric`:

```kotlin
private fun Metric(label: String, value: String) {
    TidyMetricRow(
        label = label,
        value = value,
        modifier = Modifier.height(DashboardMetricRowHeight),
    )
}
```

with:

```kotlin
private fun Metric(label: String, value: String) {
    TidyMetricRow(
        label = label,
        value = value,
        modifier = Modifier.heightIn(min = DashboardMetricRowHeight),
    )
}
```

Apply the same `heightIn(min = DashboardMetricRowHeight)` pattern in `ClickableMetric` if it passes a fixed row height.

- [ ] **Step 3: Keep scroll boundaries unchanged**

Confirm `CompactDashboard` still has:

```kotlin
.fillMaxSize()
.verticalScroll(rememberScrollState())
.padding(10.dp)
```

Do not move Start/Stop or Menu into this scrollable middle content.

- [ ] **Step 4: Compile**

Run:

```bash
sh gradlew :app:compileDebugKotlin
```

Expected in Termux: passes.

- [ ] **Step 5: Manual visual check**

On Huawei P30 Pro or a similarly narrow portrait device:

```text
1. Open the main dashboard in portrait.
2. Use a state with populated Fix card rows including RTKLIB, receiver frequency and receiver mode.
3. Confirm no row is half visible or clipped.
4. Scroll the middle dashboard and confirm all Fix rows are reachable.
5. Rotate to landscape and confirm rail layout still renders without overlap.
```

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/HomeDashboard.kt
git commit -m "Prevent dashboard metric clipping"
```

---

### Task 6: Documentation Updates

**Files:**
- Modify: `docs/user-workflows.md`
- Modify: `docs/session-format.md`

- [ ] **Step 1: Update user workflows**

Add this section to `docs/user-workflows.md` near the recorded sessions/export text:

```markdown
### Direct NMEA sharing

When a completed filesystem-backed session contains `receiver-solution.nmea`,
the Recorded sessions screen can share that NMEA file directly without creating
a full session ZIP. The app copies the existing sidecar to a temporary cache
file named like the session with a `.nmea` suffix and opens Android's share
sheet. It does not regenerate or reparse NMEA during sharing. Selected sessions
without `receiver-solution.nmea` are skipped and reported to the user.
```

- [ ] **Step 2: Update session format docs**

In `docs/session-format.md`, add after the `receiver-solution.nmea` sidecar explanation:

```markdown
Direct NMEA sharing uses the existing `receiver-solution.nmea` sidecar. The
Android app must not regenerate NMEA from `receiver-rx.raw` during sharing; if
the sidecar is absent, the session is skipped for direct NMEA share.
```

- [ ] **Step 3: Verify docs**

Run:

```bash
rg -n "Direct NMEA sharing|receiver-solution.nmea.*sidecar|\\.nmea suffix" docs/user-workflows.md docs/session-format.md
```

Expected: both docs contain the new behaviour.

- [ ] **Step 4: Commit**

```bash
git add docs/user-workflows.md docs/session-format.md
git commit -m "Document direct NMEA session sharing"
```

---

### Task 7: Final Verification And Review

**Files:**
- Inspect all files changed by Tasks 1-6.

- [ ] **Step 1: Check raw-capture boundaries**

Run:

```bash
git diff -- app/src/main/kotlin/org/rtkcollector/app/recording app/src/main/kotlin/org/rtkcollector/app/ui app/src/main/kotlin/org/rtkcollector/app/sessions docs | rg -n "receiver-rx.raw|tx-to-receiver.raw|correction-input.raw|SessionWriter|CaptureRuntime|UsbSerial|NtripClient"
```

Expected:

- `receiver-rx.raw`, `tx-to-receiver.raw`, and `correction-input.raw` only appear in docs/UI labels.
- No changes to USB serial read/write loops.
- No changes to `NtripClient`.
- No changes to raw session writer byte paths.

- [ ] **Step 2: Run focused tests on desktop/CI**

Run where Android app unit tests can execute:

```bash
./gradlew :app:testDebugUnitTest --tests 'org.rtkcollector.app.sessions.SessionBrowserModelsTest'
./gradlew :app:testDebugUnitTest --tests 'org.rtkcollector.app.recording.SessionNmeaExporterTest'
./gradlew :app:testDebugUnitTest --tests 'org.rtkcollector.app.ui.dashboard.DashboardErrorVisibilityTest'
```

Expected: all pass.

- [ ] **Step 3: Run Termux-safe checks**

Run in this checkout:

```bash
git diff --check
sh gradlew :app:compileDebugKotlin
```

Expected: both pass.

- [ ] **Step 4: Manual field checks**

Run on device:

```text
1. Record a session with NMEA export enabled.
2. Stop recording.
3. Open Menu > Recorded sessions.
4. Select the session and tap Share NMEA.
5. Confirm Android share sheet receives a .nmea file.
6. Select all selectable sessions and confirm the button label changes to Unselect all.
7. Tap Unselect all and confirm selection clears.
8. Trigger a recoverable NTRIP/USB error and confirm it is not visible after recovery for more than 15 seconds.
9. Confirm a fatal start error remains visible until the service/app state changes.
10. On Huawei P30 Pro portrait, confirm the Fix card does not clip the RTKLIB/frequency/mode rows.
```

- [ ] **Step 5: Request code review**

Use `superpowers:requesting-code-review` with this scope:

```text
Review direct NMEA session sharing, session selection toggle, dashboard stale-error expiry and dashboard card overflow fix. Focus on raw-capture isolation, temporary share-file safety, Compose state correctness and narrow portrait UI clipping.
```

- [ ] **Step 6: Fix review findings**

For any must-fix review finding, apply the smallest patch and rerun:

```bash
git diff --check
sh gradlew :app:compileDebugKotlin
```

Also rerun the targeted test for the changed area on a desktop/CI host if local Termux app tests are blocked.

- [ ] **Step 7: Final commit**

If review fixes changed files, commit them:

```bash
git add app/src/main/kotlin app/src/test/kotlin docs/user-workflows.md docs/session-format.md
git commit -m "Harden session sharing and dashboard alerts"
```

If no files changed after review, do not create an empty commit.

---

## Self-Review

Spec coverage:

- Direct NMEA sharing: Tasks 2, 3 and 6.
- Select all / unselect all: Task 1 and Task 3.
- Error expiry after 15 seconds: Task 4.
- Dashboard overflow on narrow portrait: Task 5.
- Tests and documentation: Tasks 1, 2, 4, 6 and 7.
- Raw-capture isolation: Task 7 verification.

Placeholder scan:

- The plan uses concrete file paths, concrete code snippets, explicit commands and expected results.
- No task asks the implementer to fill in unspecified logic.

Type consistency:

- `SessionNmeaSharePlan`, `SessionNmeaShareSelection`, `SessionNmeaExporter`, `DashboardErrorSnapshot`, `toggleSelectAll`, `selectAllButtonLabel` and `allSelectableSelected` are introduced before they are used.
- Existing `SessionBrowserEntry.canShareZip` is reused only as the broad completed-filesystem-session eligibility gate; NMEA sidecar presence is checked by `SessionNmeaShareSelection`.

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-06-14-session-nmea-export-error-expiry-and-dashboard-overflow.md`. Two execution options:

**1. Subagent-Driven (recommended)** - Dispatch focused workers for model/export, UI wiring, dashboard expiry/layout and docs, with review checkpoints.

**2. Inline Execution** - Execute tasks in this session using executing-plans, with commits after each task.

Which approach?
