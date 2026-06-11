# Field Hardening, Settings Transfer and UM980 Diagnostics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make RtkCollector more reliable in field use by improving start failure reporting, navigation-bar-safe dashboard layout, remembered NTRIP mountpoints, settings transfer, and compact UM980 stream diagnostics.

**Architecture:** Keep the recording service as the owner of capture and runtime state. Add small pure Kotlin helpers for validation, persistence, backup serialization, frequency tracking and command-script parsing, then wire their outputs into the existing Compose dashboard and service broadcasts. Do not make parser or UI logic part of the byte-exact raw capture path.

**Tech Stack:** Kotlin, Android foreground service, Jetpack Compose Material 3, SharedPreferences/AndroidKeyStore, org.json, existing Gradle/JUnit tests.

---

## Reference Spec

Implement `docs/superpowers/specs/2026-06-12-field-hardening-settings-telemetry-design.md`.

## File Map

- `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingStartPreflight.kt`: new pure Kotlin start validation result and helpers.
- `app/src/test/kotlin/org/rtkcollector/app/recording/RecordingStartPreflightTest.kt`: new tests for validation categories and workflow/NTRIP logic.
- `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt`: wire preflight/open errors, add diagnostics extras, update broadcast state.
- `app/src/main/kotlin/org/rtkcollector/app/profile/ProfileStores.kt`: persist last active NTRIP mountpoint id.
- `app/src/test/kotlin/org/rtkcollector/app/profile/ProfileStoresTest.kt`: add mountpoint persistence tests.
- `app/src/main/kotlin/org/rtkcollector/app/profile/SettingsBackupModels.kt`: new backup JSON model for all profiles and optional plaintext passwords.
- `app/src/test/kotlin/org/rtkcollector/app/profile/SettingsBackupModelsTest.kt`: new backup/import tests.
- `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`: wire selected mountpoint persistence, settings import/export entry points, and dashboard error visibility.
- `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardModels.kt`: add frequency and receiver mode fields.
- `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardServiceMapper.kt`: map service extras into dashboard fields.
- `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/HomeDashboard.kt`: add navigation bar padding and lat/lon wrapping display.
- `app/src/test/kotlin/org/rtkcollector/app/ui/dashboard/DashboardStateTest.kt`: add formatting/persistence tests where possible.
- `receiver/unicore-n4/src/main/kotlin/org/rtkcollector/receiver/unicore/Um980MessageFrequencyTracker.kt`: new sliding-window message frequency tracker.
- `receiver/unicore-n4/src/main/kotlin/org/rtkcollector/receiver/unicore/Um980ModeParser.kt`: new command-script mode parser.
- `receiver/unicore-n4/src/test/kotlin/org/rtkcollector/receiver/unicore/Um980MessageFrequencyTrackerTest.kt`: new frequency tests.
- `receiver/unicore-n4/src/test/kotlin/org/rtkcollector/receiver/unicore/Um980ModeParserTest.kt`: new mode parser tests.
- `docs/user-workflows.md`, `docs/android-background-operation.md`, `docs/ntrip-and-corrections.md`: focused docs updates.

## Task 1: Start Preflight Model And Tests

**Files:**
- Create: `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingStartPreflight.kt`
- Create: `app/src/test/kotlin/org/rtkcollector/app/recording/RecordingStartPreflightTest.kt`
- Modify later: `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt`

- [ ] **Step 1: Write the failing tests**

Create `RecordingStartPreflightTest.kt`:

```kotlin
package org.rtkcollector.app.recording

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecordingStartPreflightTest {
    @Test
    fun `plain rover does not require ntrip mountpoint`() {
        val result = RecordingStartPreflight.validate(
            RecordingStartPreflight.Input(
                workflowUsesNtrip = false,
                usbProfileSelected = true,
                usbDeviceConnected = true,
                usbPermissionGranted = true,
                serialDriverAvailable = true,
                serialOpenSucceeded = true,
                storageWritable = true,
                ntripMountpointConfigured = false,
            ),
        )

        assertTrue(result.canStart)
        assertEquals(RecordingErrorCategory.NONE, result.category)
    }

    @Test
    fun `ntrip workflow requires configured mountpoint`() {
        val result = RecordingStartPreflight.validate(
            RecordingStartPreflight.Input(
                workflowUsesNtrip = true,
                usbProfileSelected = true,
                usbDeviceConnected = true,
                usbPermissionGranted = true,
                serialDriverAvailable = true,
                serialOpenSucceeded = true,
                storageWritable = true,
                ntripMountpointConfigured = false,
            ),
        )

        assertFalse(result.canStart)
        assertEquals(RecordingErrorCategory.NTRIP, result.category)
        assertEquals(RecordingErrorSeverity.FATAL, result.severity)
        assertTrue(result.message.contains("NTRIP mountpoint", ignoreCase = true))
    }

    @Test
    fun `missing connected usb device is reported before ntrip`() {
        val result = RecordingStartPreflight.validate(
            RecordingStartPreflight.Input(
                workflowUsesNtrip = true,
                usbProfileSelected = true,
                usbDeviceConnected = false,
                usbPermissionGranted = false,
                serialDriverAvailable = false,
                serialOpenSucceeded = false,
                storageWritable = true,
                ntripMountpointConfigured = false,
            ),
        )

        assertFalse(result.canStart)
        assertEquals(RecordingErrorCategory.USB, result.category)
        assertTrue(result.message.contains("USB receiver is not connected", ignoreCase = true))
    }

    @Test
    fun `serial open failure is visible`() {
        val result = RecordingStartPreflight.validate(
            RecordingStartPreflight.Input(
                workflowUsesNtrip = false,
                usbProfileSelected = true,
                usbDeviceConnected = true,
                usbPermissionGranted = true,
                serialDriverAvailable = true,
                serialOpenSucceeded = false,
                storageWritable = true,
                ntripMountpointConfigured = false,
            ),
        )

        assertFalse(result.canStart)
        assertEquals(RecordingErrorCategory.USB, result.category)
        assertTrue(result.message.contains("open", ignoreCase = true))
    }
}
```

- [ ] **Step 2: Run the failing tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.recording.RecordingStartPreflightTest
```

Expected: fail because `RecordingStartPreflight` does not exist.

- [ ] **Step 3: Implement the model**

Create `RecordingStartPreflight.kt`:

```kotlin
package org.rtkcollector.app.recording

object RecordingStartPreflight {
    data class Input(
        val workflowUsesNtrip: Boolean,
        val usbProfileSelected: Boolean,
        val usbDeviceConnected: Boolean,
        val usbPermissionGranted: Boolean,
        val serialDriverAvailable: Boolean,
        val serialOpenSucceeded: Boolean,
        val storageWritable: Boolean,
        val ntripMountpointConfigured: Boolean,
    )

    data class Result(
        val canStart: Boolean,
        val category: RecordingErrorCategory,
        val severity: RecordingErrorSeverity,
        val message: String,
    )

    fun validate(input: Input): Result =
        when {
            !input.usbProfileSelected -> fatal(RecordingErrorCategory.USB, "No USB/baud profile is selected.")
            !input.usbDeviceConnected -> fatal(RecordingErrorCategory.USB, "Selected USB receiver is not connected.")
            !input.usbPermissionGranted -> fatal(RecordingErrorCategory.USB, "USB permission has not been granted.")
            !input.serialDriverAvailable -> fatal(RecordingErrorCategory.USB, "No supported USB serial driver is available for the selected receiver.")
            !input.serialOpenSucceeded -> fatal(RecordingErrorCategory.USB, "USB serial device could not be opened.")
            !input.storageWritable -> fatal(RecordingErrorCategory.STORAGE, "Recording storage is not writable.")
            input.workflowUsesNtrip && !input.ntripMountpointConfigured ->
                fatal(RecordingErrorCategory.NTRIP, "NTRIP mountpoint is required for this workflow.")
            else -> Result(
                canStart = true,
                category = RecordingErrorCategory.NONE,
                severity = RecordingErrorSeverity.NONE,
                message = "Ready to start recording.",
            )
        }

    private fun fatal(category: RecordingErrorCategory, message: String): Result =
        Result(
            canStart = false,
            category = category,
            severity = RecordingErrorSeverity.FATAL,
            message = message,
        )
}
```

- [ ] **Step 4: Verify tests pass**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.recording.RecordingStartPreflightTest
```

Expected: pass.

- [ ] **Step 5: Commit checkpoint**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/recording/RecordingStartPreflight.kt app/src/test/kotlin/org/rtkcollector/app/recording/RecordingStartPreflightTest.kt
git commit -m "Add recording start preflight model"
```

## Task 2: Wire Start Errors Without Empty Successful Sessions

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardServiceMapper.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardModels.kt`
- Test: `app/src/test/kotlin/org/rtkcollector/app/ui/dashboard/DashboardServiceMapperTest.kt`

- [ ] **Step 1: Add dashboard error mapping test**

Add to `DashboardServiceMapperTest.kt`:

```kotlin
@Test
fun `failed service state exposes last error on planned dashboard`() {
    val intent = Intent()
        .putExtra(RecordingForegroundService.EXTRA_STATE_RUNNING, false)
        .putExtra(RecordingForegroundService.EXTRA_STATE_ERROR, "USB serial device could not be opened.")
        .putExtra(RecordingForegroundService.EXTRA_STATE_ERROR_CATEGORY, RecordingErrorCategory.USB.name)
        .putExtra(RecordingForegroundService.EXTRA_STATE_ERROR_SEVERITY, RecordingErrorSeverity.FATAL.name)

    val state = dashboardStateFromRecordingIntent(intent)

    assertEquals("USB serial device could not be opened.", state.lastError)
    assertEquals("USB", state.errorCategory)
    assertEquals("FATAL", state.errorSeverity)
}
```

If the test file does not import these types, add:

```kotlin
import android.content.Intent
import org.rtkcollector.app.recording.RecordingErrorCategory
import org.rtkcollector.app.recording.RecordingErrorSeverity
import org.rtkcollector.app.recording.RecordingForegroundService
import kotlin.test.assertEquals
```

- [ ] **Step 2: Run the failing mapper test**

```bash
./gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.ui.dashboard.DashboardServiceMapperTest
```

Expected: fail because `DashboardState.lastError`, `errorCategory` and `errorSeverity` do not exist.

- [ ] **Step 3: Add fields to `DashboardState`**

In `DashboardModels.kt`, add constructor properties:

```kotlin
val lastError: String? = null,
val errorCategory: String = "NONE",
val errorSeverity: String = "NONE",
```

Update `DashboardState.planned()` and `DashboardState.running()` to accept and pass these values with defaults. Keep existing callers compiling by using default parameters.

- [ ] **Step 4: Map service extras**

In `DashboardServiceMapper.kt`, read:

```kotlin
val lastError = intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_ERROR)
val errorCategory = intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_ERROR_CATEGORY) ?: "NONE"
val errorSeverity = intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_ERROR_SEVERITY) ?: "NONE"
```

Pass these values to `DashboardState.running(...)` and `DashboardState.planned(...)`.

- [ ] **Step 5: Show the error on the dashboard**

In `HomeDashboard.kt`, add a small error strip near the top of `CompactDashboard` and `RailDashboard` content:

```kotlin
@Composable
private fun ErrorStrip(state: DashboardState) {
    val message = state.lastError?.takeIf { it.isNotBlank() } ?: return
    Surface(
        color = TidyColors.MissingBackground,
        contentColor = TidyColors.MissingText,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, TidyColors.MissingText),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = "${state.errorCategory}: $message",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
```

Call `ErrorStrip(state)` below `SetupStrip` in compact layout and below the setup summary in rail layout.

- [ ] **Step 6: Reorder service start to avoid empty sessions on preflight failures**

In `RecordingForegroundService.onStartCommand`, keep permission and selected-device checks before `openSessionWriters(intent)`. Move or add USB serial driver/open validation before durable session creation where possible. If opening the physical transport must stay after session writer creation because TX/RX sidecars are needed, then on pre-recording failure set `lifecycle = FAILED`, `running = false`, `rawRecordingActive = false`, and call `broadcastState()` with the precise message before returning.

Use `RecordingStartPreflight.Result` for failure state:

```kotlin
private fun failBeforeRecording(result: RecordingStartPreflight.Result) {
    state = state.copy(
        running = false,
        lifecycle = RecordingLifecycleState.FAILED,
        lastError = result.message,
        errorCategory = result.category,
        errorSeverity = result.severity,
        rawRecordingActive = false,
        correctionsActive = false,
    )
    broadcastState()
    stopForeground(STOP_FOREGROUND_REMOVE)
    stopSelf()
}
```

If `STOP_FOREGROUND_REMOVE` is unavailable in the configured compile SDK, use the existing stop-foreground pattern already present in the service.

- [ ] **Step 7: Verify service mapper tests**

```bash
./gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.ui.dashboard.DashboardServiceMapperTest
```

Expected: pass.

- [ ] **Step 8: Commit checkpoint**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardModels.kt app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardServiceMapper.kt app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/HomeDashboard.kt app/src/test/kotlin/org/rtkcollector/app/ui/dashboard/DashboardServiceMapperTest.kt
git commit -m "Show recording start failures on dashboard"
```

## Task 3: Dashboard Insets And Latitude/Longitude Wrapping

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/HomeDashboard.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardModels.kt`
- Test: `app/src/test/kotlin/org/rtkcollector/app/ui/dashboard/DashboardStateTest.kt`

- [ ] **Step 1: Add pure formatting test**

Add to `DashboardStateTest.kt`:

```kotlin
@Test
fun `position display splits lat and lon for narrow layout`() {
    val lines = PositionCardState(latLon = "50.087451234, 14.421253456").latLonLinesForNarrowLayout()

    assertEquals(listOf("Lat 50.087451234", "Lon 14.421253456"), lines)
}

@Test
fun `position display leaves missing value single line`() {
    assertEquals(listOf("n/a"), PositionCardState().latLonLinesForNarrowLayout())
}
```

- [ ] **Step 2: Run failing test**

```bash
./gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.ui.dashboard.DashboardStateTest
```

Expected: fail because `latLonLinesForNarrowLayout` does not exist.

- [ ] **Step 3: Add formatting helper**

In `DashboardModels.kt`:

```kotlin
fun PositionCardState.latLonLinesForNarrowLayout(): List<String> {
    val parts = latLon.split(",", limit = 2).map { it.trim() }
    return if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
        listOf("Lat ${parts[0]}", "Lon ${parts[1]}")
    } else {
        listOf(latLon)
    }
}
```

- [ ] **Step 4: Apply navigation bar padding**

In `HomeDashboard.kt`, import:

```kotlin
import androidx.compose.foundation.layout.navigationBarsPadding
```

Change `BottomActionBar` row modifier to:

```kotlin
modifier = Modifier
    .fillMaxWidth()
    .navigationBarsPadding()
    .padding(horizontal = 10.dp, vertical = 8.dp)
```

- [ ] **Step 5: Use wrapped position display**

Replace `MajorValue(state.position.latLon)` in `PositionCard` with a helper:

```kotlin
PositionMajorValue(state.position)
```

Add:

```kotlin
@Composable
private fun PositionMajorValue(position: PositionCardState) {
    Column(
        modifier = Modifier.height(DashboardMajorValueHeight),
        verticalArrangement = Arrangement.Center,
    ) {
        position.latLonLinesForNarrowLayout().forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
```

If two lines do not fit with `DashboardMajorValueHeight`, increase `PositionDashboardCardHeight` by the smallest necessary amount and keep fixed dimensions.

- [ ] **Step 6: Verify tests**

```bash
./gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.ui.dashboard.DashboardStateTest
```

Expected: pass.

- [ ] **Step 7: Commit checkpoint**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/HomeDashboard.kt app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardModels.kt app/src/test/kotlin/org/rtkcollector/app/ui/dashboard/DashboardStateTest.kt
git commit -m "Keep dashboard controls and position visible"
```

## Task 4: Remember Last Active NTRIP Mountpoint

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/profile/ProfileStores.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`
- Test: `app/src/test/kotlin/org/rtkcollector/app/profile/ProfileStoresTest.kt`
- Test: `app/src/test/kotlin/org/rtkcollector/app/ui/DashboardMountpointLabelTest.kt`

- [ ] **Step 1: Add persistence tests**

Add to `ProfileStoresTest.kt`:

```kotlin
@Test
fun `last active mountpoint id can be saved and cleared`() {
    val store = ProfileStores(context)

    store.saveLastActiveNtripMountpointProfileId("mount-a")
    assertEquals("mount-a", store.lastActiveNtripMountpointProfileId())

    store.saveLastActiveNtripMountpointProfileId(null)
    assertEquals(null, store.lastActiveNtripMountpointProfileId())
}
```

Add to `DashboardMountpointLabelTest.kt`:

```kotlin
@Test
fun `missing remembered mountpoint is displayed as missing`() {
    val label = selectedMountpointLabelFromProfileId("missing", emptyList())

    assertEquals("n/a", label)
}
```

- [ ] **Step 2: Run failing tests**

```bash
./gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.profile.ProfileStoresTest --tests org.rtkcollector.app.ui.DashboardMountpointLabelTest
```

Expected: fail because the new functions do not exist.

- [ ] **Step 3: Add store methods**

In `ProfileStores.kt`:

```kotlin
fun lastActiveNtripMountpointProfileId(): String? =
    preferences.getString("lastActiveNtripMountpointProfileId", null)
        ?.takeIf { it.isNotBlank() && !it.equals("a", ignoreCase = true) }

fun saveLastActiveNtripMountpointProfileId(id: String?) {
    preferences.edit().apply {
        if (id.isNullOrBlank()) {
            remove("lastActiveNtripMountpointProfileId")
        } else {
            putString("lastActiveNtripMountpointProfileId", id)
        }
    }.apply()
}
```

- [ ] **Step 4: Add label helper**

Near existing mountpoint label helpers in `MainActivity.kt`, add:

```kotlin
internal fun selectedMountpointLabelFromProfileId(
    profileId: String?,
    mountpointProfiles: List<NtripMountpointProfile>,
): String =
    profileId
        ?.takeIf { it.isNotBlank() && !it.equals("a", ignoreCase = true) }
        ?.let { id -> mountpointProfiles.firstOrNull { it.id == id } }
        ?.displayMountpoint()
        ?.takeUnless { it.equals("a", ignoreCase = true) }
        ?: "n/a"
```

Import `NtripMountpointProfile` if needed.

- [ ] **Step 5: Wire selection persistence**

In `MainActivity.kt`, when the user selects a NTRIP mountpoint from the dashboard overlay or editor save path, call:

```kotlin
profileStore.saveLastActiveNtripMountpointProfileId(selected.id)
```

On startup planned dashboard creation, use selected settings set mountpoint first; if it is missing and the workflow/settings policy leaves mountpoint intact, fall back to:

```kotlin
selectedMountpointLabelFromProfileId(
    profileStore.lastActiveNtripMountpointProfileId(),
    profileStore.ntripMountpointProfiles(),
)
```

Do not use the remembered mountpoint to force a NTRIP connection for non-NTRIP workflows.

- [ ] **Step 6: Verify tests**

```bash
./gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.profile.ProfileStoresTest --tests org.rtkcollector.app.ui.DashboardMountpointLabelTest
```

Expected: pass.

- [ ] **Step 7: Commit checkpoint**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/profile/ProfileStores.kt app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt app/src/test/kotlin/org/rtkcollector/app/profile/ProfileStoresTest.kt app/src/test/kotlin/org/rtkcollector/app/ui/DashboardMountpointLabelTest.kt
git commit -m "Remember active NTRIP mountpoint"
```

## Task 5: Settings Backup Model With Optional Password Export

**Files:**
- Create: `app/src/main/kotlin/org/rtkcollector/app/profile/SettingsBackupModels.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/profile/SettingsSetExportModels.kt`
- Test: `app/src/test/kotlin/org/rtkcollector/app/profile/SettingsBackupModelsTest.kt`

- [ ] **Step 1: Write failing backup tests**

Create `SettingsBackupModelsTest.kt`:

```kotlin
package org.rtkcollector.app.profile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsBackupModelsTest {
    @Test
    fun `export excludes plaintext passwords by default`() {
        val backup = SettingsBackupFile.fromProfiles(
            commandProfiles = emptyList(),
            usbBaudProfiles = emptyList(),
            ntripCasterProfiles = listOf(NtripCasterProfile(id = "caster", name = "Caster", secretId = "secret")),
            ntripMountpointProfiles = emptyList(),
            recordingPolicyProfiles = emptyList(),
            storageProfiles = emptyList(),
            settingsSets = emptyList(),
            selectedSettingsSetId = "settings",
            selectedWorkflowId = "rover-ntrip",
            lastActiveNtripMountpointProfileId = "mount",
            passwordsBySecretId = mapOf("secret" to "secret-password"),
            options = SettingsSetExportOptions(includePlaintextPasswords = false),
        )

        val json = backup.toJson().toString()

        assertFalse(json.contains("secret-password"))
        assertFalse(json.contains("plaintextPasswords"))
    }

    @Test
    fun `export includes plaintext passwords only when requested`() {
        val backup = SettingsBackupFile.fromProfiles(
            commandProfiles = emptyList(),
            usbBaudProfiles = emptyList(),
            ntripCasterProfiles = listOf(NtripCasterProfile(id = "caster", name = "Caster", secretId = "secret")),
            ntripMountpointProfiles = emptyList(),
            recordingPolicyProfiles = emptyList(),
            storageProfiles = emptyList(),
            settingsSets = emptyList(),
            selectedSettingsSetId = "settings",
            selectedWorkflowId = "rover-ntrip",
            lastActiveNtripMountpointProfileId = "mount",
            passwordsBySecretId = mapOf("secret" to "secret-password"),
            options = SettingsSetExportOptions(includePlaintextPasswords = true),
        )

        val parsed = SettingsBackupFile.fromJson(backup.toJson())

        assertEquals("secret-password", parsed.plaintextPasswordsBySecretId["secret"])
        assertTrue(backup.toJson().toString().contains("plaintextPasswords"))
    }
}
```

- [ ] **Step 2: Run failing backup tests**

```bash
./gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.profile.SettingsBackupModelsTest
```

Expected: fail because `SettingsBackupFile` does not exist.

- [ ] **Step 3: Implement backup model**

Create `SettingsBackupModels.kt` with:

```kotlin
package org.rtkcollector.app.profile

import org.json.JSONArray
import org.json.JSONObject

data class SettingsBackupFile(
    val formatVersion: Int,
    val exportedAtEpochMillis: Long,
    val commandProfiles: List<CommandProfile>,
    val usbBaudProfiles: List<UsbBaudProfile>,
    val ntripCasterProfiles: List<NtripCasterProfile>,
    val ntripMountpointProfiles: List<NtripMountpointProfile>,
    val recordingPolicyProfiles: List<RecordingPolicyProfile>,
    val storageProfiles: List<StorageProfile>,
    val settingsSets: List<RecordingSettingsSet>,
    val selectedSettingsSetId: String?,
    val selectedWorkflowId: String?,
    val lastActiveNtripMountpointProfileId: String?,
    val plaintextPasswordsBySecretId: Map<String, String>,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("formatVersion", formatVersion)
        .put("exportedAtEpochMillis", exportedAtEpochMillis)
        .put("commandProfiles", commandProfiles.toJsonArray { it.toJson() })
        .put("usbBaudProfiles", usbBaudProfiles.toJsonArray { it.toJson() })
        .put("ntripCasterProfiles", ntripCasterProfiles.toJsonArray { it.toJson() })
        .put("ntripMountpointProfiles", ntripMountpointProfiles.toJsonArray { it.toJson() })
        .put("recordingPolicyProfiles", recordingPolicyProfiles.toJsonArray { it.toJson() })
        .put("storageProfiles", storageProfiles.toJsonArray { it.toJson() })
        .put("settingsSets", settingsSets.toJsonArray { it.toJson() })
        .putNullable("selectedSettingsSetId", selectedSettingsSetId)
        .putNullable("selectedWorkflowId", selectedWorkflowId)
        .putNullable("lastActiveNtripMountpointProfileId", lastActiveNtripMountpointProfileId)
        .also { json ->
            if (plaintextPasswordsBySecretId.isNotEmpty()) {
                json.put(
                    "plaintextPasswords",
                    JSONObject().also { passwords ->
                        plaintextPasswordsBySecretId.forEach { (secretId, password) ->
                            passwords.put(secretId, password)
                        }
                    },
                )
            }
        }

    companion object {
        const val CURRENT_FORMAT_VERSION = 1

        fun fromProfiles(
            commandProfiles: List<CommandProfile>,
            usbBaudProfiles: List<UsbBaudProfile>,
            ntripCasterProfiles: List<NtripCasterProfile>,
            ntripMountpointProfiles: List<NtripMountpointProfile>,
            recordingPolicyProfiles: List<RecordingPolicyProfile>,
            storageProfiles: List<StorageProfile>,
            settingsSets: List<RecordingSettingsSet>,
            selectedSettingsSetId: String?,
            selectedWorkflowId: String?,
            lastActiveNtripMountpointProfileId: String?,
            passwordsBySecretId: Map<String, String>,
            options: SettingsSetExportOptions,
            exportedAtEpochMillis: Long = System.currentTimeMillis(),
        ): SettingsBackupFile =
            SettingsBackupFile(
                formatVersion = CURRENT_FORMAT_VERSION,
                exportedAtEpochMillis = exportedAtEpochMillis,
                commandProfiles = commandProfiles,
                usbBaudProfiles = usbBaudProfiles,
                ntripCasterProfiles = ntripCasterProfiles,
                ntripMountpointProfiles = ntripMountpointProfiles,
                recordingPolicyProfiles = recordingPolicyProfiles,
                storageProfiles = storageProfiles,
                settingsSets = settingsSets,
                selectedSettingsSetId = selectedSettingsSetId,
                selectedWorkflowId = selectedWorkflowId,
                lastActiveNtripMountpointProfileId = lastActiveNtripMountpointProfileId,
                plaintextPasswordsBySecretId = if (options.includePlaintextPasswords) passwordsBySecretId else emptyMap(),
            )

        fun fromJson(json: JSONObject): SettingsBackupFile {
            require(json.optInt("formatVersion", 0) == CURRENT_FORMAT_VERSION) {
                "Unsupported settings backup format version."
            }
            val passwords = json.optJSONObject("plaintextPasswords")
            return SettingsBackupFile(
                formatVersion = json.getInt("formatVersion"),
                exportedAtEpochMillis = json.optLong("exportedAtEpochMillis", 0L),
                commandProfiles = json.getJSONArray("commandProfiles").mapObjects(CommandProfile::fromJson),
                usbBaudProfiles = json.getJSONArray("usbBaudProfiles").mapObjects(UsbBaudProfile::fromJson),
                ntripCasterProfiles = json.getJSONArray("ntripCasterProfiles").mapObjects(NtripCasterProfile::fromJson),
                ntripMountpointProfiles = json.getJSONArray("ntripMountpointProfiles").mapObjects(NtripMountpointProfile::fromJson),
                recordingPolicyProfiles = json.getJSONArray("recordingPolicyProfiles").mapObjects(RecordingPolicyProfile::fromJson),
                storageProfiles = json.getJSONArray("storageProfiles").mapObjects(StorageProfile::fromJson),
                settingsSets = json.getJSONArray("settingsSets").mapObjects(RecordingSettingsSet::fromJson),
                selectedSettingsSetId = json.optNullableString("selectedSettingsSetId"),
                selectedWorkflowId = json.optNullableString("selectedWorkflowId"),
                lastActiveNtripMountpointProfileId = json.optNullableString("lastActiveNtripMountpointProfileId"),
                plaintextPasswordsBySecretId = passwords?.keys()?.asSequence()?.associateWith { passwords.getString(it) }.orEmpty(),
            )
        }
    }
}

private fun <T> List<T>.toJsonArray(encode: (T) -> JSONObject): JSONArray =
    JSONArray().also { array -> forEach { array.put(encode(it)) } }

private fun <T> JSONArray.mapObjects(decode: (JSONObject) -> T): List<T> =
    (0 until length()).map { index -> decode(getJSONObject(index)) }
```

If `putNullable` or `optNullableString` are private in another file, add local private equivalents in this file:

```kotlin
private fun JSONObject.putNullable(key: String, value: String?): JSONObject =
    if (value == null) put(key, JSONObject.NULL) else put(key, value)

private fun JSONObject.optNullableString(key: String): String? =
    optString(key).takeIf { has(key) && !isNull(key) && it.isNotBlank() }
```

- [ ] **Step 4: Verify backup tests**

```bash
./gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.profile.SettingsBackupModelsTest
```

Expected: pass.

- [ ] **Step 5: Commit checkpoint**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/profile/SettingsBackupModels.kt app/src/main/kotlin/org/rtkcollector/app/profile/SettingsSetExportModels.kt app/src/test/kotlin/org/rtkcollector/app/profile/SettingsBackupModelsTest.kt
git commit -m "Add settings backup model"
```

## Task 6: Settings Backup UI Wiring

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/settings/SettingsHub.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/secrets/NtripSecretStore.kt`

- [ ] **Step 1: Add secret listing support**

In `NtripSecretStore.kt`, add:

```kotlin
fun knownSecretIds(): Set<String> =
    preferences.all.keys
        .filter { it.endsWith(".iv") }
        .map { it.removeSuffix(".iv") }
        .toSet()
```

- [ ] **Step 2: Add export action**

In `MainActivity.kt`, add functions:

```kotlin
private fun buildSettingsBackup(
    context: Context,
    includePlaintextPasswords: Boolean,
): SettingsBackupFile {
    val profileStore = ProfileStores(context)
    val secretStore = NtripSecretStore(context)
    val passwords = if (includePlaintextPasswords) {
        secretStore.knownSecretIds().mapNotNull { id ->
            secretStore.getPassword(id)?.let { password -> id to password }
        }.toMap()
    } else {
        emptyMap()
    }
    return SettingsBackupFile.fromProfiles(
        commandProfiles = profileStore.commandProfiles(),
        usbBaudProfiles = profileStore.usbBaudProfiles(),
        ntripCasterProfiles = profileStore.ntripCasterProfiles(),
        ntripMountpointProfiles = profileStore.ntripMountpointProfiles(),
        recordingPolicyProfiles = profileStore.recordingPolicyProfiles(),
        storageProfiles = profileStore.storageProfiles(),
        settingsSets = profileStore.settingsSets(),
        selectedSettingsSetId = profileStore.selectedSettingsSetId(),
        selectedWorkflowId = profileStore.selectedWorkflowId(),
        lastActiveNtripMountpointProfileId = profileStore.lastActiveNtripMountpointProfileId(),
        passwordsBySecretId = passwords,
        options = SettingsSetExportOptions(includePlaintextPasswords),
    )
}
```

Add a simple share intent that writes this JSON to `cacheDir/settings-backups/rtkcollector-settings.json` and shares with the existing app `FileProvider` authority. Reuse the file-provider pattern used by session ZIP sharing.

- [ ] **Step 3: Add import action**

Add a document picker for `application/json` and parse the returned content into `SettingsBackupFile.fromJson(JSONObject(text))`. Save profile collections via `ProfileStores.save...` methods, save selected ids, and write `backup.plaintextPasswordsBySecretId` through `NtripSecretStore.putPassword`.

- [ ] **Step 4: Add settings menu entries**

In `SettingsHub.kt`, add two entries:

```kotlin
SettingsHubEntry("Export settings backup")
SettingsHubEntry("Import settings backup")
```

Wire them in `MainActivity.kt` to the export/import handlers. The export path must show a confirmation dialog with checkbox `Include plaintext NTRIP passwords` and warning text from `SettingsSetExportOptions(includePlaintextPasswords = true).passwordWarning`.

- [ ] **Step 5: Manual verify**

Run the app and verify:

- export without checkbox does not contain a known password string;
- export with checkbox contains the password string;
- import restores profiles and passwords on the same device after deleting a test profile.

- [ ] **Step 6: Compile check**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: pass.

- [ ] **Step 7: Commit checkpoint**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt app/src/main/kotlin/org/rtkcollector/app/ui/settings/SettingsHub.kt app/src/main/kotlin/org/rtkcollector/app/secrets/NtripSecretStore.kt
git commit -m "Wire settings backup import and export"
```

## Task 7: UM980 Frequency Tracker And Mode Parser

**Files:**
- Create: `receiver/unicore-n4/src/main/kotlin/org/rtkcollector/receiver/unicore/Um980MessageFrequencyTracker.kt`
- Create: `receiver/unicore-n4/src/main/kotlin/org/rtkcollector/receiver/unicore/Um980ModeParser.kt`
- Create: `receiver/unicore-n4/src/test/kotlin/org/rtkcollector/receiver/unicore/Um980MessageFrequencyTrackerTest.kt`
- Create: `receiver/unicore-n4/src/test/kotlin/org/rtkcollector/receiver/unicore/Um980ModeParserTest.kt`

- [ ] **Step 1: Write frequency tests**

Create `Um980MessageFrequencyTrackerTest.kt`:

```kotlin
package org.rtkcollector.receiver.unicore

import kotlin.test.Test
import kotlin.test.assertEquals

class Um980MessageFrequencyTrackerTest {
    @Test
    fun `reports observed rates and missing sources`() {
        val tracker = Um980MessageFrequencyTracker(windowMillis = 1_000)
        repeat(20) { tracker.record(Um980MessageKind.GGA, timestampMillis = 1_000L + it * 50L) }
        tracker.record(Um980MessageKind.BESTNAV, timestampMillis = 1_100L)

        val display = tracker.display(timestampMillis = 2_000L)

        assertEquals("Frequency BESTNAV/GGA/PPPNAV/ADRNAV/RTKSTATUS/OBSVM 1/20/-/-/-/- Hz", display)
    }

    @Test
    fun `old samples age out`() {
        val tracker = Um980MessageFrequencyTracker(windowMillis = 1_000)
        tracker.record(Um980MessageKind.GGA, timestampMillis = 0L)

        assertEquals("Frequency BESTNAV/GGA/PPPNAV/ADRNAV/RTKSTATUS/OBSVM -/-/-/-/-/- Hz", tracker.display(2_000L))
    }
}
```

- [ ] **Step 2: Write mode parser tests**

Create `Um980ModeParserTest.kt`:

```kotlin
package org.rtkcollector.receiver.unicore

import kotlin.test.Test
import kotlin.test.assertEquals

class Um980ModeParserTest {
    @Test
    fun `parses rover survey command`() {
        assertEquals("ROVER SURVEY", Um980ModeParser.configuredMode("UNLOG COM1\nMODE ROVER SURVEY\nBESTNAVB COM1 1"))
    }

    @Test
    fun `parses rover automotive command`() {
        assertEquals("ROVER AUTOMOTIVE", Um980ModeParser.configuredMode("MODE ROVER AUTOMOTIVE"))
    }

    @Test
    fun `parses plain rover command`() {
        assertEquals("ROVER", Um980ModeParser.configuredMode("MODE ROVER"))
    }
}
```

- [ ] **Step 3: Run failing tests**

```bash
./gradlew :receiver:unicore-n4:test --tests org.rtkcollector.receiver.unicore.Um980MessageFrequencyTrackerTest --tests org.rtkcollector.receiver.unicore.Um980ModeParserTest
```

Expected: fail because classes do not exist.

- [ ] **Step 4: Implement tracker**

Create `Um980MessageFrequencyTracker.kt`:

```kotlin
package org.rtkcollector.receiver.unicore

enum class Um980MessageKind(val label: String) {
    BESTNAV("BESTNAV"),
    GGA("GGA"),
    PPPNAV("PPPNAV"),
    ADRNAV("ADRNAV"),
    RTKSTATUS("RTKSTATUS"),
    OBSVM("OBSVM"),
}

class Um980MessageFrequencyTracker(private val windowMillis: Long = 10_000L) {
    private val samples = mutableMapOf<Um980MessageKind, ArrayDeque<Long>>()
    private val order = listOf(
        Um980MessageKind.BESTNAV,
        Um980MessageKind.GGA,
        Um980MessageKind.PPPNAV,
        Um980MessageKind.ADRNAV,
        Um980MessageKind.RTKSTATUS,
        Um980MessageKind.OBSVM,
    )

    fun record(kind: Um980MessageKind, timestampMillis: Long) {
        val queue = samples.getOrPut(kind) { ArrayDeque() }
        queue.addLast(timestampMillis)
        prune(timestampMillis)
    }

    fun display(timestampMillis: Long): String {
        prune(timestampMillis)
        val values = order.joinToString("/") { kind ->
            val count = samples[kind]?.size ?: 0
            if (count == 0) "-" else formatHz(count * 1000.0 / windowMillis)
        }
        return "Frequency BESTNAV/GGA/PPPNAV/ADRNAV/RTKSTATUS/OBSVM $values Hz"
    }

    private fun prune(now: Long) {
        val cutoff = now - windowMillis
        samples.values.forEach { queue ->
            while (queue.isNotEmpty() && queue.first() <= cutoff) {
                queue.removeFirst()
            }
        }
    }

    private fun formatHz(value: Double): String =
        if (value >= 10.0 || value % 1.0 == 0.0) {
            value.toInt().toString()
        } else {
            String.format(java.util.Locale.US, "%.1f", value)
        }
}
```

- [ ] **Step 5: Implement mode parser**

Create `Um980ModeParser.kt`:

```kotlin
package org.rtkcollector.receiver.unicore

object Um980ModeParser {
    fun configuredMode(script: String): String? =
        script
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .mapNotNull(::parseModeLine)
            .lastOrNull()

    private fun parseModeLine(line: String): String? {
        val tokens = line.split(Regex("\\s+"))
        if (tokens.isEmpty() || !tokens[0].equals("MODE", ignoreCase = true)) return null
        return tokens.drop(1).takeIf { it.isNotEmpty() }?.joinToString(" ") { it.uppercase() }
    }
}
```

- [ ] **Step 6: Verify tests**

```bash
./gradlew :receiver:unicore-n4:test --tests org.rtkcollector.receiver.unicore.Um980MessageFrequencyTrackerTest --tests org.rtkcollector.receiver.unicore.Um980ModeParserTest
```

Expected: pass.

- [ ] **Step 7: Commit checkpoint**

```bash
git add receiver/unicore-n4/src/main/kotlin/org/rtkcollector/receiver/unicore/Um980MessageFrequencyTracker.kt receiver/unicore-n4/src/main/kotlin/org/rtkcollector/receiver/unicore/Um980ModeParser.kt receiver/unicore-n4/src/test/kotlin/org/rtkcollector/receiver/unicore/Um980MessageFrequencyTrackerTest.kt receiver/unicore-n4/src/test/kotlin/org/rtkcollector/receiver/unicore/Um980ModeParserTest.kt
git commit -m "Add UM980 diagnostics helpers"
```

## Task 8: Wire UM980 Frequency And Mode To Service And Dashboard

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingServiceState.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardModels.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardServiceMapper.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/HomeDashboard.kt`
- Test: `app/src/test/kotlin/org/rtkcollector/app/ui/dashboard/DashboardServiceMapperTest.kt`

- [ ] **Step 1: Add mapper test**

Add to `DashboardServiceMapperTest.kt`:

```kotlin
@Test
fun `maps receiver diagnostics frequency and mode`() {
    val intent = Intent()
        .putExtra(RecordingForegroundService.EXTRA_STATE_RUNNING, true)
        .putExtra(RecordingForegroundService.EXTRA_STATE_UM980_FREQUENCY, "Frequency BESTNAV/GGA/PPPNAV/ADRNAV/RTKSTATUS/OBSVM 20/1/1/1/1/4 Hz")
        .putExtra(RecordingForegroundService.EXTRA_STATE_UM980_MODE, "Commanded ROVER SURVEY")

    val state = dashboardStateFromRecordingIntent(intent)

    assertEquals("Frequency BESTNAV/GGA/PPPNAV/ADRNAV/RTKSTATUS/OBSVM 20/1/1/1/1/4 Hz", state.fix.receiverFrequency)
    assertEquals("Commanded ROVER SURVEY", state.fix.receiverMode)
}
```

- [ ] **Step 2: Run failing test**

```bash
./gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.ui.dashboard.DashboardServiceMapperTest
```

Expected: fail because fields/extras do not exist.

- [ ] **Step 3: Add state fields and extras**

In `RecordingServiceState.kt`, add:

```kotlin
val um980Frequency: String = "Frequency BESTNAV/GGA/PPPNAV/ADRNAV/RTKSTATUS/OBSVM -/-/-/-/-/- Hz",
val um980Mode: String = "n/a",
```

In `RecordingForegroundService` companion object, add:

```kotlin
const val EXTRA_STATE_UM980_FREQUENCY = "um980Frequency"
const val EXTRA_STATE_UM980_MODE = "um980Mode"
```

Add these to `broadcastState()`.

- [ ] **Step 4: Record message kinds in parser fanout**

In `RecordingForegroundService.parseReceiverStream`, instantiate:

```kotlin
val frequencyTracker = Um980MessageFrequencyTracker()
```

When each source is parsed, call:

```kotlin
frequencyTracker.record(Um980MessageKind.GGA, System.currentTimeMillis())
frequencyTracker.record(Um980MessageKind.BESTNAV, System.currentTimeMillis())
frequencyTracker.record(Um980MessageKind.PPPNAV, System.currentTimeMillis())
frequencyTracker.record(Um980MessageKind.ADRNAV, System.currentTimeMillis())
frequencyTracker.record(Um980MessageKind.RTKSTATUS, System.currentTimeMillis())
frequencyTracker.record(Um980MessageKind.OBSVM, System.currentTimeMillis())
```

Map `OBSVMCMPB` by binary message id/source in the existing `Um980StreamParser.Record.Binary` branch. If only frame identification exists and no payload parser is required, record the kind when the binary frame message id is OBSVMCMPB.

After handling a record, update:

```kotlin
state = state.copy(um980Frequency = frequencyTracker.display(System.currentTimeMillis()))
```

- [ ] **Step 5: Set commanded mode at recording start**

When building state from active command profile in `RecordingForegroundService`, parse:

```kotlin
val commandedMode = Um980ModeParser.configuredMode(initScript)
```

Set:

```kotlin
um980Mode = commandedMode?.let { "Commanded $it" } ?: "n/a"
```

If a future receiver-reported mode parser exists, it may overwrite this with `Reported ...`; do not invent a query.

- [ ] **Step 6: Map dashboard fields**

In `FixCardState`, add:

```kotlin
val receiverFrequency: String = "Frequency BESTNAV/GGA/PPPNAV/ADRNAV/RTKSTATUS/OBSVM -/-/-/-/-/- Hz",
val receiverMode: String = "n/a",
```

In `DashboardServiceMapper.kt`, populate from extras.

In `HomeDashboard.kt`, show both in the lower portion of the Fix card as small rows:

```kotlin
TidyMetricRow("Mode", state.fix.receiverMode)
Text(
    text = state.fix.receiverFrequency,
    style = MaterialTheme.typography.labelSmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    maxLines = 2,
    overflow = TextOverflow.Ellipsis,
)
```

- [ ] **Step 7: Verify tests**

```bash
./gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.ui.dashboard.DashboardServiceMapperTest
```

Expected: pass.

- [ ] **Step 8: Commit checkpoint**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/recording/RecordingServiceState.kt app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardModels.kt app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardServiceMapper.kt app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/HomeDashboard.kt app/src/test/kotlin/org/rtkcollector/app/ui/dashboard/DashboardServiceMapperTest.kt
git commit -m "Show UM980 stream diagnostics on dashboard"
```

## Task 9: Documentation Updates

**Files:**
- Modify: `docs/user-workflows.md`
- Modify: `docs/android-background-operation.md`
- Modify: `docs/ntrip-and-corrections.md`

- [ ] **Step 1: Update `docs/user-workflows.md`**

Add a section:

```markdown
## Settings Backup

RtkCollector can export a user-initiated settings backup for transferring
profiles between phones. The backup contains receiver command profiles,
USB/baud profiles, NTRIP caster and mountpoint profiles, recording outputs,
storage profiles, settings sets and active selections.

NTRIP passwords are excluded by default. The export dialog may include a
separate checkbox to include plaintext passwords. That option is intended only
for trusted phone-to-phone transfer and the resulting file must be handled as a
secret.
```

- [ ] **Step 2: Update `docs/android-background-operation.md`**

Add:

```markdown
Start failures must be reported before or at recording start with a specific
category such as USB, storage or NTRIP. A failed USB open or missing serial
driver must not appear to the user as a silent empty recording.
```

- [ ] **Step 3: Update `docs/ntrip-and-corrections.md`**

Add:

```markdown
The last active NTRIP mountpoint profile may be remembered for convenience, but
NTRIP must only connect when the selected workflow uses NTRIP corrections.
Session metadata must not contain NTRIP passwords; only explicit settings
backup exports may include plaintext credentials after user confirmation.
```

- [ ] **Step 4: Commit docs**

```bash
git add docs/user-workflows.md docs/android-background-operation.md docs/ntrip-and-corrections.md
git commit -m "Document settings backup and start diagnostics"
```

## Task 10: Final Verification And Review

**Files:**
- No planned source changes unless review finds defects.

- [ ] **Step 1: Run focused tests**

```bash
./gradlew :receiver:unicore-n4:test --tests org.rtkcollector.receiver.unicore.Um980MessageFrequencyTrackerTest --tests org.rtkcollector.receiver.unicore.Um980ModeParserTest
./gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.recording.RecordingStartPreflightTest --tests org.rtkcollector.app.profile.SettingsBackupModelsTest --tests org.rtkcollector.app.profile.ProfileStoresTest --tests org.rtkcollector.app.ui.DashboardMountpointLabelTest --tests org.rtkcollector.app.ui.dashboard.DashboardStateTest --tests org.rtkcollector.app.ui.dashboard.DashboardServiceMapperTest
```

Expected: pass.

- [ ] **Step 2: Run broader tests**

```bash
./gradlew test
```

Expected: pass. If Termux/Android SDK native tooling blocks Android packaging, report the exact blocker and keep Kotlin/JVM test results separate.

- [ ] **Step 3: Compile Android Kotlin**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: pass.

- [ ] **Step 4: Request code review**

Use `superpowers:requesting-code-review` with:

- description: Field hardening, settings backup and UM980 diagnostics.
- requirements: this plan and the design spec.
- base SHA: commit before Task 1.
- head SHA: current HEAD.

Fix all critical and important findings, then re-run affected tests.

- [ ] **Step 5: Push**

```bash
git status --short
git push origin main
```

Expected: branch pushed with only intended source, test and docs changes.

## Plan Self-Review

- Spec coverage: Tasks 1-2 cover start/USB errors; Task 3 covers navigation bar and lat/lon wrapping; Task 4 covers remembered mountpoint; Tasks 5-6 cover settings backup and passwords; Tasks 7-8 cover frequency and mode diagnostics; Task 9 covers docs; Task 10 covers review.
- Placeholder scan: no unresolved markers or unspecified edge handling remains in required implementation steps.
- Type consistency: `RecordingStartPreflight`, `SettingsBackupFile`, `Um980MessageFrequencyTracker`, `Um980ModeParser`, `receiverFrequency`, `receiverMode`, `um980Frequency`, and `um980Mode` are named consistently across tasks.
