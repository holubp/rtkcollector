# Dashboard Upload And Fixed-Base Gap Closure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the remaining user-visible gaps for the main-screen NTRIP upload selector, explicit fixed-base profile materialisation, built-in settings coverage, averaging regression coverage, text-field consistency and status/spec alignment.

**Architecture:** Keep recording owned by `RecordingForegroundService`; these changes are profile/UI/model changes and must not put new logic on the raw capture path. Add small testable model seams for upload selector rows and fixed-base command materialisation instead of embedding the behaviour only in Compose. Keep NTRIP source upload disabled unless the selected settings set explicitly enables it or the main-screen selector enables a concrete upload profile.

**Tech Stack:** Kotlin, Android Compose, app profile stores, JUnit/Jupiter unit tests, Markdown docs, `git diff --check`, `sh gradlew :app:compileDebugKotlin`.

---

## Scope

Fix now:

- Main-screen `Upload` setup selector with explicit `Off`.
- Explicit temporary-base to fixed-base profile materialisation before recording start.
- Focused built-in settings sets for UM980 rover/base workflows.
- Regression coverage that coordinate averaging continues across NTRIP caster/mountpoint changes.
- Consistent one-line settings/profile text fields.
- Formal docs and plan status alignment.

Out of scope for this plan:

- The postponed satellite-frequency "visible but not used" debugging for GLONASS L3, Galileo L6 and BeiDou L5/L7.
- Any change that makes RTKLIB real-time processing required for V1 recording.
- Any change that contaminates `receiver-rx.raw`, `correction-input.raw` or `tx-to-receiver.raw`.

## File Structure

Modify these files:

```text
app/src/main/kotlin/org/rtkcollector/app/base/FixedBaseProfileMaterializer.kt
app/src/main/kotlin/org/rtkcollector/app/profile/ProfileStores.kt
app/src/main/kotlin/org/rtkcollector/app/profile/SettingsSetModels.kt
app/src/main/kotlin/org/rtkcollector/app/recording/CoordinateAveragingController.kt
app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt
app/src/main/kotlin/org/rtkcollector/app/ui/common/ProfileTextFields.kt
app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardModels.kt
app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardServiceMapper.kt
app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/HomeDashboard.kt
app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileListModels.kt
app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileScreens.kt
docs/superpowers/plan-status.md
docs/user-workflows.md
docs/workflows.md
docs/specification/ui-requirements.md
docs/specification/verification-matrix.md
docs/specification/workflows.md
```

Create these files if absent:

```text
app/src/main/kotlin/org/rtkcollector/app/base/FixedBaseProfileMaterializer.kt
app/src/main/kotlin/org/rtkcollector/app/ui/common/ProfileTextFields.kt
app/src/test/kotlin/org/rtkcollector/app/base/FixedBaseProfileMaterializerTest.kt
app/src/test/kotlin/org/rtkcollector/app/profile/BuiltInSettingsSetTest.kt
app/src/test/kotlin/org/rtkcollector/app/ui/dashboard/DashboardUploadSelectorTest.kt
```

Update these tests:

```text
app/src/test/kotlin/org/rtkcollector/app/profile/ActiveRecordingConfigCasterUploadTest.kt
app/src/test/kotlin/org/rtkcollector/app/profile/ProfileStoresTest.kt
app/src/test/kotlin/org/rtkcollector/app/profile/SettingsSetModelsTest.kt
app/src/test/kotlin/org/rtkcollector/app/recording/CoordinateAveragingControllerTest.kt
app/src/test/kotlin/org/rtkcollector/app/ui/dashboard/DashboardLayoutModelsTest.kt
app/src/test/kotlin/org/rtkcollector/app/ui/dashboard/DashboardServiceMapperTest.kt
app/src/test/kotlin/org/rtkcollector/app/ui/dashboard/DashboardStateTest.kt
app/src/test/kotlin/org/rtkcollector/app/ui/profiles/ProfileListModelsTest.kt
```

## Task 1: Main-Screen Upload Selector With Explicit Off

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardModels.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardServiceMapper.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/HomeDashboard.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`
- Modify tests: `app/src/test/kotlin/org/rtkcollector/app/ui/dashboard/DashboardLayoutModelsTest.kt`
- Modify tests: `app/src/test/kotlin/org/rtkcollector/app/ui/dashboard/DashboardStateTest.kt`
- Create tests: `app/src/test/kotlin/org/rtkcollector/app/ui/dashboard/DashboardUploadSelectorTest.kt`

- [ ] **Step 1: Write failing dashboard model tests**

Add these tests:

```kotlin
@Test
fun `default setup strip includes upload selector`() {
    assertEquals(
        listOf("Workflow", "Mountpoint", "Receiver", "Upload", "Storage"),
        defaultDashboardSetupItems.map { it.label },
    )
}

@Test
fun `planned dashboard status carries upload label`() {
    val state = DashboardState.planned(
        workflow = "Fixed base",
        mountpoint = "n/a",
        receiver = "UM980",
        upload = "Off",
        storage = "App-private external storage",
    )

    assertEquals("Off", state.status.upload)
}

@Test
fun `stopped service state updates planned upload label`() {
    val serviceState = DashboardState.planned(
        workflow = "n/a",
        mountpoint = "n/a",
        receiver = "n/a",
        upload = "Off",
        storage = "n/a",
    )
    val planned = DashboardState.planned(
        workflow = "Fixed base",
        mountpoint = "n/a",
        receiver = "UM980",
        upload = "RTK2go BASE01",
        storage = "App-private external storage",
    )

    val merged = serviceState.withPlannedConfiguration(planned)

    assertEquals("RTK2go BASE01", merged.status.upload)
}
```

Create `DashboardUploadSelectorTest.kt`:

```kotlin
package org.rtkcollector.app.ui.dashboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rtkcollector.app.profile.NtripCasterUploadProfile
import org.rtkcollector.app.profile.ProfileReference
import org.rtkcollector.app.profile.RecordingSettingsSet

class DashboardUploadSelectorTest {
    @Test
    fun `upload selector rows start with off row`() {
        val selected = RecordingSettingsSet.builtInRoverNtrip().copy(
            ntripCasterUploadProfileRef = ProfileReference("upload", "Upload"),
            baseCasterUploadEnabled = true,
        )

        val rows = dashboardUploadSelectorRows(
            profiles = listOf(NtripCasterUploadProfile(id = "upload", name = "Upload")),
            selectedSettingsSet = selected,
        )

        assertEquals("off", rows.first().id)
        assertEquals("Off", rows.first().name)
        assertTrue(rows.any { it.id == "upload" && it.isSelected })
    }

    @Test
    fun `upload selector selects off when upload disabled`() {
        val rows = dashboardUploadSelectorRows(
            profiles = listOf(NtripCasterUploadProfile(id = "upload", name = "Upload")),
            selectedSettingsSet = RecordingSettingsSet.builtInRoverNtrip(),
        )

        assertTrue(rows.first { it.id == "off" }.isSelected)
    }
}
```

- [ ] **Step 2: Run tests to verify failure**

Run:

```sh
sh gradlew :app:testDebugUnitTest --tests 'org.rtkcollector.app.ui.dashboard.*'
```

Expected: FAIL because `DashboardStatus.upload`, `DashboardSetupItem.UPLOAD` and `dashboardUploadSelectorRows` do not exist.

- [ ] **Step 3: Extend dashboard status and setup item**

In `DashboardModels.kt`, change `DashboardState.planned(...)` to accept `upload: String = "Off"` and populate `DashboardStatus(upload = upload)`.

Change `DashboardStatus`:

```kotlin
data class DashboardStatus(
    val settingsSet: String = "n/a",
    val workflow: String = "n/a",
    val mountpoint: String = "n/a",
    val receiver: String = "n/a",
    val upload: String = "Off",
    val storage: String = "n/a",
)
```

Change `DashboardSetupItem`:

```kotlin
internal enum class DashboardSetupItem(val label: String) {
    WORKFLOW("Workflow"),
    MOUNTPOINT("Mountpoint"),
    RECEIVER("Receiver"),
    UPLOAD("Upload"),
    STORAGE("Storage"),
}

internal val defaultDashboardSetupItems: List<DashboardSetupItem> = listOf(
    DashboardSetupItem.WORKFLOW,
    DashboardSetupItem.MOUNTPOINT,
    DashboardSetupItem.RECEIVER,
    DashboardSetupItem.UPLOAD,
    DashboardSetupItem.STORAGE,
)
```

Update `DashboardState.withPlannedConfiguration(...)` by copying the whole `planned.status`, which already includes `upload`.

- [ ] **Step 4: Wire upload label from planned and running state**

In `MainActivity.kt`, add this helper near the other selected-label helpers:

```kotlin
private fun RecordingSettingsSet?.selectedUploadLabel(
    uploadProfiles: List<NtripCasterUploadProfile>,
): String {
    val profile = this?.ntripCasterUploadProfileRef?.id
        ?.let { id -> uploadProfiles.firstOrNull { it.id == id } }
    val enabled = profile != null && (baseCasterUploadEnabled || profile.enabledByDefault)
    return if (enabled) profile!!.name else "Off"
}
```

Pass the label into `DashboardState.planned(...)` in `buildPlannedDashboardState(...)`:

```kotlin
upload = selected.selectedUploadLabel(ntripCasterUploadProfiles()),
```

In `DashboardServiceMapper.kt`, set running status upload from the service upload state:

```kotlin
val uploadState = intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_BASE_CASTER_UPLOAD_STATE)
    ?.takeIf { it.isNotBlank() }
    ?: "Disabled"
val uploadUrl = intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_BASE_CASTER_UPLOAD_URL)
    ?.takeIf { it.isNotBlank() }
val uploadLabel = if (uploadState.equals("Disabled", ignoreCase = true) && uploadUrl == null) {
    "Off"
} else {
    uploadUrl ?: uploadState
}
```

Then include `upload = uploadLabel` in the `DashboardStatus(...)` constructor.

- [ ] **Step 5: Wire the setup tile callbacks**

In `HomeDashboard(...)`, add:

```kotlin
onUpload: () -> Unit,
```

Pass it through `CompactDashboard(...)`, `RailDashboard(...)` and `SetupStrip(...)`.

In both setup click `when` expressions add:

```kotlin
DashboardSetupItem.UPLOAD -> onUpload
```

In `DashboardStatus.valueFor(...)` add:

```kotlin
DashboardSetupItem.UPLOAD -> upload
```

In `MainActivity.kt`, pass:

```kotlin
onUpload = {
    if (state.isRecording) {
        Toast.makeText(context, "Stop recording before changing NTRIP upload.", Toast.LENGTH_LONG).show()
    } else {
        dashboardSelector = DashboardSelector.UPLOAD
    }
},
```

- [ ] **Step 6: Add upload selector rows and selection handling**

In `MainActivity.kt`, extend `DashboardSelector`:

```kotlin
UPLOAD("Select NTRIP upload"),
```

Add a top-level constant near selector helpers:

```kotlin
private const val DASHBOARD_UPLOAD_OFF_ID = "off"
```

Add a testable helper:

```kotlin
internal fun dashboardUploadSelectorRows(
    profiles: List<NtripCasterUploadProfile>,
    selectedSettingsSet: RecordingSettingsSet?,
): List<ProfileListRow> {
    val selectedProfileId = selectedSettingsSet?.ntripCasterUploadProfileRef?.id
    val selectedProfile = profiles.firstOrNull { it.id == selectedProfileId }
    val uploadEnabled = selectedProfile != null &&
        (selectedSettingsSet.baseCasterUploadEnabled || selectedProfile.enabledByDefault)
    return listOf(
        ProfileListRow(
            id = DASHBOARD_UPLOAD_OFF_ID,
            name = "Off",
            isProtected = false,
            hasLocalOverrides = false,
            isSelected = !uploadEnabled,
            summary = "Do not upload base RTCM to an NTRIP caster",
        ),
    ) + profiles.map { profile ->
        profile.profileRow(isSelected = uploadEnabled && profile.id == selectedProfileId)
    }
}
```

In `dashboardSelectorRows(...)`, add:

```kotlin
DashboardSelector.UPLOAD -> dashboardUploadSelectorRows(
    profiles = profileStore.ntripCasterUploadProfiles(),
    selectedSettingsSet = selectedSettingsSet,
)
```

In `ProfileSelectorDialog` handling, add:

```kotlin
DashboardSelector.UPLOAD -> {
    settingsSets = settingsSets.updateSelected(selectedSettingsSetId) { set ->
        if (id == DASHBOARD_UPLOAD_OFF_ID) {
            set.copy(baseCasterUploadEnabled = false)
        } else {
            profileStore.ntripCasterUploadProfiles().firstOrNull { it.id == id }?.let { profile ->
                set.copy(
                    ntripCasterUploadProfileRef = ProfileReference(profile.id, profile.name),
                    baseCasterUploadEnabled = true,
                )
            } ?: set
        }
    }
    profileStore.saveSettingsSets(settingsSets)
    refreshProfileUi(settingsSets)
}
```

- [ ] **Step 7: Run dashboard tests**

Run:

```sh
sh gradlew :app:testDebugUnitTest --tests 'org.rtkcollector.app.ui.dashboard.*'
```

Expected: PASS.

- [ ] **Step 8: Commit checkpoint**

```sh
git add app/src/main/kotlin/org/rtkcollector/app/ui/dashboard app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt app/src/test/kotlin/org/rtkcollector/app/ui/dashboard
git commit -m "Add dashboard NTRIP upload selector"
```

## Task 2: Explicit Fixed-Base Profile Materialisation

**Files:**
- Create: `app/src/main/kotlin/org/rtkcollector/app/base/FixedBaseProfileMaterializer.kt`
- Create tests: `app/src/test/kotlin/org/rtkcollector/app/base/FixedBaseProfileMaterializerTest.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`
- Modify tests: `app/src/test/kotlin/org/rtkcollector/app/profile/ActiveRecordingConfigTest.kt`

- [ ] **Step 1: Write failing materializer tests**

Create `FixedBaseProfileMaterializerTest.kt`:

```kotlin
package org.rtkcollector.app.base

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FixedBaseProfileMaterializerTest {
    @Test
    fun `replaces existing mode base line`() {
        val result = FixedBaseProfileMaterializer.materialize(
            runtimeScript = "UNLOG COM1\nMODE BASE TIME 120 2.5\nGNGGA 1",
            modeBaseCommand = "MODE BASE 49.4637593130 15.4512544790 707.8000",
        )

        assertEquals("MODE BASE TIME 120 2.5", result.replacedLine)
        assertEquals(
            "UNLOG COM1\nMODE BASE 49.4637593130 15.4512544790 707.8000\nGNGGA 1",
            result.runtimeScript,
        )
    }

    @Test
    fun `inserts mode base after unlog when no mode line exists`() {
        val result = FixedBaseProfileMaterializer.materialize(
            runtimeScript = "UNLOG COM1\nGNGGA 1",
            modeBaseCommand = "MODE BASE 49.4637593130 15.4512544790 707.8000",
        )

        assertEquals(null, result.replacedLine)
        assertEquals(
            "UNLOG COM1\nMODE BASE 49.4637593130 15.4512544790 707.8000\nGNGGA 1",
            result.runtimeScript,
        )
    }

    @Test
    fun `inserts mode base at top when script has no unlog`() {
        val result = FixedBaseProfileMaterializer.materialize(
            runtimeScript = "GNGGA 1",
            modeBaseCommand = "MODE BASE 49.4637593130 15.4512544790 707.8000",
        )

        assertEquals(
            "MODE BASE 49.4637593130 15.4512544790 707.8000\nGNGGA 1",
            result.runtimeScript,
        )
    }
}
```

- [ ] **Step 2: Run tests to verify failure**

Run:

```sh
sh gradlew :app:testDebugUnitTest --tests 'org.rtkcollector.app.base.FixedBaseProfileMaterializerTest'
```

Expected: FAIL because `FixedBaseProfileMaterializer` does not exist.

- [ ] **Step 3: Implement materializer**

Create `FixedBaseProfileMaterializer.kt`:

```kotlin
package org.rtkcollector.app.base

data class FixedBaseMaterializationResult(
    val runtimeScript: String,
    val replacedLine: String?,
)

object FixedBaseProfileMaterializer {
    fun materialize(
        runtimeScript: String,
        modeBaseCommand: String,
    ): FixedBaseMaterializationResult {
        require(modeBaseCommand.startsWith("MODE BASE ")) {
            "Fixed-base materialization requires a MODE BASE command."
        }
        val lines = runtimeScript.lineSequence().toMutableList()
        val modeIndex = lines.indexOfFirst { it.trimStart().startsWith("MODE BASE ", ignoreCase = true) }
        if (modeIndex >= 0) {
            val before = lines[modeIndex]
            lines[modeIndex] = modeBaseCommand
            return FixedBaseMaterializationResult(lines.joinToString("\n"), before)
        }
        val unlogIndex = lines.indexOfFirst { it.trim().equals("UNLOG COM1", ignoreCase = true) }
        val insertAt = if (unlogIndex >= 0) unlogIndex + 1 else 0
        lines.add(insertAt, modeBaseCommand)
        return FixedBaseMaterializationResult(lines.joinToString("\n"), null)
    }
}
```

- [ ] **Step 4: Remove silent start-time replacement**

In `MainActivity.kt`, remove `fixedBaseModeCommand` injection from `buildStartIntent(...)`.

Change:

```kotlin
ArrayList(activeConfig.modeCommands.withFixedBaseModeCommand(fixedBaseModeCommand))
```

to:

```kotlin
ArrayList(activeConfig.modeCommands)
```

Delete the helper:

```kotlin
private fun List<String>.withFixedBaseModeCommand(command: String?): List<String>
```

Add start validation before `ActiveRecordingConfig.resolve(...)` for fixed-base workflow:

```kotlin
if (workflowId == WORKFLOW_FIXED_BASE) {
    val hasModeBase = resolvedProfiles.commandProfile.runtimeScript.commandLines()
        .any { it.trimStart().startsWith("MODE BASE ", ignoreCase = true) }
    if (!hasModeBase) {
        showCannotStart(context, "fixed base command profile must contain MODE BASE from an accepted coordinate.")
        return null
    }
}
```

- [ ] **Step 5: Add explicit materialisation UI actions**

In the dashboard `Base` coordinate action path, keep the existing accepted-coordinate save, then show a materialisation dialog before switching to fixed-base workflow.

Add state:

```kotlin
var pendingFixedBaseCoordinate by remember { mutableStateOf<AcceptedBaseCoordinate?>(null) }
```

When the user chooses `Base`, set:

```kotlin
pendingFixedBaseCoordinate = acceptedCoordinate
```

Render an `AlertDialog` with:

```kotlin
AlertDialog(
    onDismissRequest = { pendingFixedBaseCoordinate = null },
    title = { Text("Use coordinate for fixed base") },
    text = {
        Text(
            "Choose where the MODE BASE line should be written. Recording start will use the selected command profile as-is."
        )
    },
    confirmButton = {
        TextButton(onClick = { materializeIntoSelectedCommandProfile(pendingFixedBaseCoordinate!!) }) {
            Text("Overwrite selected profile")
        }
    },
    dismissButton = {
        TextButton(onClick = { createFixedBaseCommandProfile(pendingFixedBaseCoordinate!!) }) {
            Text("Create new profile")
        }
    },
)
```

Implement `materializeIntoSelectedCommandProfile(...)` so it rejects protected profiles with a toast:

```kotlin
if (profile.isProtected) {
    Toast.makeText(context, "Copy the built-in command profile before overwriting MODE BASE.", Toast.LENGTH_LONG).show()
    return
}
```

For editable profiles, call `FixedBaseProfileMaterializer.materialize(...)`, save the command profile with the updated `runtimeScript`, switch workflow to `fixed-base`, save settings sets and refresh UI.

Implement `createFixedBaseCommandProfile(...)` by copying the selected command profile to a new non-protected profile with name:

```kotlin
"Fixed base ${coordinate.name}"
```

Then materialize its `runtimeScript`, save it, point the selected settings set at the new command profile, switch workflow to `fixed-base`, save settings sets and refresh UI.

- [ ] **Step 6: Add start-path regression test**

In `ActiveRecordingConfigTest.kt`, add:

```kotlin
@Test
fun `fixed base mode commands are not mutated by start config`() {
    val config = ActiveRecordingConfig.resolve(
        settingsSet = RecordingSettingsSet.builtInRoverNtrip().copy(workflowId = "fixed-base"),
        commandProfile = CommandProfile(
            id = "fixed",
            name = "Fixed",
            runtimeScript = "UNLOG COM1\nMODE BASE 49.4637593130 15.4512544790 707.8000\nGNGGA 1",
        ),
        usbBaudProfile = UsbBaudProfile("baud", "Baud"),
        ntripCasterProfile = null,
        ntripMountpointProfile = null,
        ntripCasterUploadProfile = null,
        recordingPolicyProfile = RecordingPolicyProfile("record", "Record"),
        storageProfile = StorageProfile("storage", "Storage"),
        workflowName = "Fixed base",
        workflowUsesNtrip = false,
        hasAcceptedBaseCoordinate = true,
        passwordLookup = { null },
    )

    assertEquals(
        listOf(
            "UNLOG COM1",
            "MODE BASE 49.4637593130 15.4512544790 707.8000",
            "GNGGA 1",
        ),
        config.modeCommands,
    )
}
```

- [ ] **Step 7: Run fixed-base tests**

Run:

```sh
sh gradlew :app:testDebugUnitTest --tests 'org.rtkcollector.app.base.*' --tests 'org.rtkcollector.app.profile.ActiveRecordingConfigTest'
```

Expected: PASS.

- [ ] **Step 8: Commit checkpoint**

```sh
git add app/src/main/kotlin/org/rtkcollector/app/base app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt app/src/test/kotlin/org/rtkcollector/app/base app/src/test/kotlin/org/rtkcollector/app/profile/ActiveRecordingConfigTest.kt
git commit -m "Make fixed-base profile materialisation explicit"
```

## Task 3: Built-In Settings Sets And Upload Defaults

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/profile/SettingsSetModels.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/profile/ProfileStores.kt`
- Create tests: `app/src/test/kotlin/org/rtkcollector/app/profile/BuiltInSettingsSetTest.kt`
- Modify tests: `app/src/test/kotlin/org/rtkcollector/app/profile/ProfileStoresTest.kt`
- Modify tests: `app/src/test/kotlin/org/rtkcollector/app/profile/SettingsSetModelsTest.kt`

- [ ] **Step 1: Write failing default settings tests**

Create `BuiltInSettingsSetTest.kt`:

```kotlin
package org.rtkcollector.app.profile

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BuiltInSettingsSetTest {
    @Test
    fun `built in settings cover rover and base workflows`() {
        val ids = RecordingSettingsSet.builtIns().map { it.id }

        assertEquals(
            listOf(
                "um980-rover-plain",
                "um980-rover-ntrip",
                "um980-temporary-base",
                "um980-fixed-base",
                "um980-fixed-base-upload",
            ),
            ids,
        )
    }

    @Test
    fun `default rover profiles do not enable source upload`() {
        val roverSets = RecordingSettingsSet.builtIns().filter { it.workflowId.startsWith("rover") }

        assertTrue(roverSets.isNotEmpty())
        assertTrue(roverSets.all { !it.baseCasterUploadEnabled })
        assertTrue(roverSets.all { it.ntripCasterUploadProfileRef == null })
    }

    @Test
    fun `fixed base upload preset is explicit`() {
        val uploadSet = RecordingSettingsSet.builtIns().single { it.id == "um980-fixed-base-upload" }

        assertEquals("fixed-base", uploadSet.workflowId)
        assertTrue(uploadSet.baseCasterUploadEnabled)
        assertEquals("ntrip-source-upload-template", uploadSet.ntripCasterUploadProfileRef?.id)
    }

    @Test
    fun `generic upload profile is not enabled by default`() {
        val profile = NtripCasterUploadProfile.builtInTemplate()

        assertEquals("ntrip-source-upload-template", profile.id)
        assertFalse(profile.enabledByDefault)
        assertFalse(profile.effectiveSafetyRulesEnabled)
    }
}
```

- [ ] **Step 2: Run tests to verify failure**

Run:

```sh
sh gradlew :app:testDebugUnitTest --tests 'org.rtkcollector.app.profile.BuiltInSettingsSetTest'
```

Expected: FAIL because `RecordingSettingsSet.builtIns()` and `NtripCasterUploadProfile.builtInTemplate()` do not exist.

- [ ] **Step 3: Add built-in settings set factories**

In `SettingsSetModels.kt`, keep `builtInRoverNtrip()` for compatibility and add:

```kotlin
fun builtInRoverPlain(): RecordingSettingsSet =
    builtInRoverNtrip().copy(
        id = "um980-rover-plain",
        name = "UM980 rover",
        workflowId = "plain-rover",
        ntripCasterProfileRef = null,
        ntripMountpointProfileRef = null,
        ntripCasterUploadProfileRef = null,
        baseCasterUploadEnabled = false,
        isProtected = true,
    )

fun builtInTemporaryBase(): RecordingSettingsSet =
    builtInRoverNtrip().copy(
        id = "um980-temporary-base",
        name = "UM980 temporary base",
        workflowId = "temporary-base",
        ntripCasterProfileRef = ProfileReference("ntrip-caster-default", "NTRIP caster"),
        ntripMountpointProfileRef = null,
        ntripCasterUploadProfileRef = null,
        baseCasterUploadEnabled = false,
        isProtected = true,
    )

fun builtInFixedBase(): RecordingSettingsSet =
    builtInRoverNtrip().copy(
        id = "um980-fixed-base",
        name = "UM980 fixed base",
        workflowId = "fixed-base",
        commandProfileRef = ProfileReference("um980-base-config", "UM980 base RTCM output"),
        ntripCasterProfileRef = null,
        ntripMountpointProfileRef = null,
        ntripCasterUploadProfileRef = null,
        baseCasterUploadEnabled = false,
        isProtected = true,
    )

fun builtInFixedBaseUpload(): RecordingSettingsSet =
    builtInFixedBase().copy(
        id = "um980-fixed-base-upload",
        name = "UM980 fixed base + NTRIP upload",
        ntripCasterUploadProfileRef = ProfileReference(
            "ntrip-source-upload-template",
            "NTRIP source upload template",
        ),
        baseCasterUploadEnabled = true,
        isProtected = true,
    )

fun builtIns(): List<RecordingSettingsSet> =
    listOf(
        builtInRoverPlain(),
        builtInRoverNtrip(),
        builtInTemporaryBase(),
        builtInFixedBase(),
        builtInFixedBaseUpload(),
    )
```

- [ ] **Step 4: Add built-in upload profile template**

In the `NtripCasterUploadProfile` companion object, add:

```kotlin
fun builtInTemplate(): NtripCasterUploadProfile =
    NtripCasterUploadProfile(
        id = "ntrip-source-upload-template",
        name = "NTRIP source upload template",
        host = "",
        port = 2101,
        mountpoint = "",
        username = "",
        enabledByDefault = false,
        isProtected = true,
    )
```

If `NtripCasterUploadProfile` has no companion object, add one.

- [ ] **Step 5: Wire profile-store defaults**

In `ProfileStores.kt`, change:

```kotlin
private fun defaultNtripCasterUploadProfiles(): List<NtripCasterUploadProfile> =
    emptyList()
```

to:

```kotlin
private fun defaultNtripCasterUploadProfiles(): List<NtripCasterUploadProfile> =
    listOf(NtripCasterUploadProfile.builtInTemplate())
```

Change:

```kotlin
private fun defaultSettingsSets(): List<RecordingSettingsSet> =
    listOf(RecordingSettingsSet.builtInRoverNtrip())
```

to:

```kotlin
private fun defaultSettingsSets(): List<RecordingSettingsSet> =
    RecordingSettingsSet.builtIns()
```

- [ ] **Step 6: Preserve existing users through sync semantics**

Do not delete user-created settings sets. The existing profile-store default sync should add missing built-ins and update protected same-id built-ins. Add or update a `ProfileStoresTest` assertion:

```kotlin
@Test
fun `settings set defaults include all protected built ins`() {
    val defaults = RecordingSettingsSet.builtIns()

    assertTrue(defaults.all { it.isProtected })
    assertTrue(defaults.any { it.id == "um980-rover-ntrip" })
    assertTrue(defaults.any { it.id == "um980-fixed-base-upload" })
}
```

- [ ] **Step 7: Run profile tests**

Run:

```sh
sh gradlew :app:testDebugUnitTest --tests 'org.rtkcollector.app.profile.*'
```

Expected: PASS.

- [ ] **Step 8: Commit checkpoint**

```sh
git add app/src/main/kotlin/org/rtkcollector/app/profile app/src/test/kotlin/org/rtkcollector/app/profile
git commit -m "Add focused UM980 built-in settings sets"
```

## Task 4: Coordinate Averaging Regression Across NTRIP Changes

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/recording/CoordinateAveragingController.kt`
- Modify tests: `app/src/test/kotlin/org/rtkcollector/app/recording/CoordinateAveragingControllerTest.kt`

- [ ] **Step 1: Add regression test**

Add:

```kotlin
@Test
fun `ntrip source changes do not stop active averaging`() {
    val controller = CoordinateAveragingController()
    controller.start(requiredFixClass = FixClass.RTK_FIXED)
    assertTrue(controller.onSelectedSolution(candidate(50.0, 14.0, 302.0, FixClass.RTK_FIXED)).accepted)

    controller.onNtripSourceChanged("caster-a", "MOUNT-A")
    controller.onNtripSourceChanged("caster-b", "MOUNT-B")

    assertTrue(controller.active)
    assertEquals(null, controller.lastStopReason)
    assertEquals(1, controller.summary()?.sampleCount)
}
```

- [ ] **Step 2: Run test to verify failure**

Run:

```sh
sh gradlew :app:testDebugUnitTest --tests 'org.rtkcollector.app.recording.CoordinateAveragingControllerTest'
```

Expected: FAIL because `onNtripSourceChanged(...)` does not exist.

- [ ] **Step 3: Implement explicit no-op handler**

In `CoordinateAveragingController.kt`, add:

```kotlin
fun onNtripSourceChanged(
    casterLabel: String?,
    mountpointLabel: String?,
) {
    // Averaging is tied to the stationary local receiver and selected solution
    // fix class. Changing the upstream correction source is intentionally not
    // a stop condition.
    casterLabel.hashCode()
    mountpointLabel.hashCode()
}
```

Use the parameters in the method body as shown to avoid unused-parameter warnings without storing advisory NTRIP state in the averaging controller.

- [ ] **Step 4: Wire service update path for clarity**

In `RecordingForegroundService.updateNtrip(...)`, after the accepted NTRIP update is parsed and before or after reconnect scheduling, call:

```kotlin
coordinateAveragingController.onNtripSourceChanged(
    casterLabel = ntripHost,
    mountpointLabel = ntripMountpoint,
)
```

Use the existing local variable names from `updateNtrip(...)`; do not stop averaging from this path.

- [ ] **Step 5: Run recording tests**

Run:

```sh
sh gradlew :app:testDebugUnitTest --tests 'org.rtkcollector.app.recording.CoordinateAveragingControllerTest' --tests 'org.rtkcollector.app.recording.NtripUpdatePolicyTest'
```

Expected: PASS.

- [ ] **Step 6: Commit checkpoint**

```sh
git add app/src/main/kotlin/org/rtkcollector/app/recording/CoordinateAveragingController.kt app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt app/src/test/kotlin/org/rtkcollector/app/recording/CoordinateAveragingControllerTest.kt
git commit -m "Keep base averaging active across NTRIP changes"
```

## Task 5: Consistent One-Line Text Field Editing

**Files:**
- Create: `app/src/main/kotlin/org/rtkcollector/app/ui/common/ProfileTextFields.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileScreens.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`
- Modify tests: `app/src/test/kotlin/org/rtkcollector/app/ui/profiles/ProfileListModelsTest.kt`

- [ ] **Step 1: Inventory remaining editable one-line `OutlinedTextField` usage**

Run:

```sh
rg -n "OutlinedTextField\\(" app/src/main/kotlin/org/rtkcollector/app/ui app/src/main/kotlin/org/rtkcollector/app/base
```

Classify each hit in the commit message body:

```text
ProfileScreens.kt one-line profile editor fields: replace.
ProfileScreens.kt multiline command editor fallback: keep AndroidView editor.
DeviceConsoleScreen.kt command input: keep because it is a console, not profile/settings text.
MainActivity.kt one-line dialog/input fields: replace when user-editable.
```

- [ ] **Step 2: Create reusable profile text field**

Create `ProfileTextFields.kt`:

```kotlin
package org.rtkcollector.app.ui.common

import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.VisualTransformation

@Composable
fun ProfileSingleLineTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    supportingText: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier,
        singleLine = true,
        enabled = enabled,
        readOnly = readOnly,
        visualTransformation = visualTransformation,
        supportingText = supportingText?.let { text -> { Text(text) } },
    )
}
```

This wrapper is intentionally small. The multiline command editor keeps the existing Android `EditText` path because that is where hardware-keyboard arrow semantics are already handled.

- [ ] **Step 3: Replace profile editor one-line fields**

In `ProfileScreens.kt`, replace one-line editable `OutlinedTextField` calls with `ProfileSingleLineTextField(...)`.

For password fields, pass:

```kotlin
visualTransformation = if (field.secret) PasswordVisualTransformation() else VisualTransformation.None
```

For runtime source-upload username fields, pass:

```kotlin
readOnly = renderedField.readOnly,
supportingText = renderedField.helperText ?: renderedField.errorText,
```

Do not replace the AndroidView multiline command script editor.

- [ ] **Step 4: Replace remaining settings/dialog one-line fields**

In `MainActivity.kt`, replace user-editable one-line `OutlinedTextField` instances with `ProfileSingleLineTextField(...)`. Keep read-only text and non-profile controls unchanged.

After replacement, this command must show only console or multiline/editor-specific exceptions:

```sh
rg -n "OutlinedTextField\\(" app/src/main/kotlin/org/rtkcollector/app/ui app/src/main/kotlin/org/rtkcollector/app/base
```

- [ ] **Step 5: Keep username and RTK2go dynamic-state tests green**

The existing tests in `ProfileListModelsTest.kt` must keep passing:

```kotlin
`ntrip source upload username is preserved but disabled for v1`
`rtk2go safety state follows unsaved host value`
```

If a field wrapper change breaks these semantics, fix the wrapper call site so `value` is never cleared when `readOnly = true`.

- [ ] **Step 6: Run profile UI tests**

Run:

```sh
sh gradlew :app:testDebugUnitTest --tests 'org.rtkcollector.app.ui.profiles.*'
```

Expected: PASS.

- [ ] **Step 7: Commit checkpoint**

```sh
git add app/src/main/kotlin/org/rtkcollector/app/ui/common app/src/main/kotlin/org/rtkcollector/app/ui/profiles app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt app/src/test/kotlin/org/rtkcollector/app/ui/profiles
git commit -m "Unify profile text field editing"
```

## Task 6: Documentation, Formal Requirements And Plan Status

**Files:**
- Modify: `docs/user-workflows.md`
- Modify: `docs/workflows.md`
- Modify: `docs/specification/workflows.md`
- Modify: `docs/specification/ui-requirements.md`
- Modify: `docs/specification/verification-matrix.md`
- Modify: `docs/superpowers/plan-status.md`

- [ ] **Step 1: Update user workflow documentation**

In `docs/user-workflows.md`, add or update sections with these exact operational points:

```text
Plain rover records the receiver stream without an upstream correction source.

Rover with NTRIP downloads corrections from a caster and sends those correction
bytes to the receiver. This is separate from NTRIP source upload.

Temporary base means a stationary receiver used to determine a coordinate. It
can average positions while using one or more upstream NTRIP mountpoints. The
operator may continue averaging after changing mountpoint, provided the local
receiver has not moved.

Fixed base means the receiver is configured with an accepted coordinate. For
UM980, the MODE BASE command uses MSL altitude, not ellipsoidal height.

NTRIP source upload is for fixed-base RTCM output. The main dashboard Upload
selector has an explicit Off state. Upload failure must not stop local receiver
recording.
```

- [ ] **Step 2: Update formal workflow requirements**

In `docs/specification/workflows.md`, add requirement rows or bullets with stable IDs matching the local style. Include these behaviours:

```text
Fixed-base start shall not silently replace or inject MODE BASE commands.
The app shall offer explicit creation of a command profile or explicit overwrite
of an editable command profile before using an accepted base coordinate.

The main dashboard shall expose NTRIP source upload selection with an explicit
Off state.

Temporary-base coordinate averaging shall not stop solely because the upstream
NTRIP caster or mountpoint changes.

Built-in rover settings sets shall not enable NTRIP source upload by default.
```

- [ ] **Step 3: Update UI requirements**

In `docs/specification/ui-requirements.md`, add requirements for:

```text
Dashboard setup strip includes Workflow, Mountpoint, Receiver, Upload and Storage.
Upload selector lists Off first.
Source-upload v1 username field is visibly not used and keeps its value.
Editable single-line profile/settings text fields use the shared field behaviour.
```

- [ ] **Step 4: Update verification matrix**

In `docs/specification/verification-matrix.md`, add mappings:

```text
Dashboard upload Off/Profile selector -> DashboardUploadSelectorTest, DashboardLayoutModelsTest
Fixed-base explicit materialisation -> FixedBaseProfileMaterializerTest, manual UM980 tx-to-receiver.raw check
Built-in settings coverage -> BuiltInSettingsSetTest
Averaging across NTRIP changes -> CoordinateAveragingControllerTest
Text-field consistency -> ProfileListModelsTest plus manual profile editor keyboard check
```

- [ ] **Step 5: Update plan status**

In `docs/superpowers/plan-status.md`, set:

```text
Temporary-base to fixed-base workflow: Implemented, not field-tested
```

only after Tasks 1-5 are committed. The note must mention explicit profile materialisation and that UM980 field validation remains needed.

Set:

```text
Base RTCM NTRIP caster upload: Implemented, not field-tested
```

only after the dashboard upload selector and Off path are committed. The note must mention main-screen Off/Profile selection and live caster/base validation still needed.

Keep:

```text
Google Play readiness: Open
```

unless a separate publishing review is performed.

- [ ] **Step 6: Run docs checks**

Run:

```sh
git diff --check
rg -n "Upload|NTRIP source upload|MODE BASE|temporary base|fixed base|MSL altitude|Off" docs/user-workflows.md docs/workflows.md docs/specification docs/superpowers/plan-status.md
```

Expected: no whitespace errors; search output shows the new workflow and UI requirements.

- [ ] **Step 7: Commit checkpoint**

```sh
git add docs/user-workflows.md docs/workflows.md docs/specification docs/superpowers/plan-status.md
git commit -m "Document upload selector and fixed-base workflows"
```

## Task 7: Integration Verification And Push

**Files:**
- Review all changed files.

- [ ] **Step 1: Inspect worktree**

Run:

```sh
git status --short
git diff --stat
```

Expected: only source, test and docs files from this plan are modified. `samples/`, `.superpowers/`, local debug captures and local IDE/cache files are not staged.

- [ ] **Step 2: Run targeted tests sequentially**

Run:

```sh
sh gradlew :app:testDebugUnitTest --tests 'org.rtkcollector.app.ui.dashboard.*'
sh gradlew :app:testDebugUnitTest --tests 'org.rtkcollector.app.base.*'
sh gradlew :app:testDebugUnitTest --tests 'org.rtkcollector.app.profile.*'
sh gradlew :app:testDebugUnitTest --tests 'org.rtkcollector.app.recording.CoordinateAveragingControllerTest'
sh gradlew :app:testDebugUnitTest --tests 'org.rtkcollector.app.ui.profiles.*'
```

Expected: PASS. If Termux hits the known Android resource or AGP task issue, record the exact failing task and continue with compile checks below; do not retry the same failing packaging path repeatedly.

- [ ] **Step 3: Run compile and lightweight checks**

Run:

```sh
git diff --check
sh gradlew :app:unitTestClasses :app:androidTestClasses --dry-run
sh gradlew :app:compileDebugKotlin
```

Expected: `git diff --check` clean; Gradle task selection dry-run succeeds; Kotlin compile succeeds. If local Termux cannot run a Google native Android SDK tool, document that Windows Android Studio or CI must run the release/package validation.

- [ ] **Step 4: Manual Android smoke checks**

On an Android build from Windows Android Studio or CI, verify:

```text
Dashboard setup strip shows Workflow, Mountpoint, Receiver, Upload, Storage.
Upload opens a selector with Off first.
Selecting Off changes dashboard upload label to Off and keeps local recording start possible.
Selecting a source-upload profile changes dashboard upload label to that profile.
Base coordinate action offers create-new-profile and overwrite-selected-profile paths.
Protected built-in command profile cannot be overwritten directly.
Created fixed-base profile contains a visible MODE BASE line with MSL altitude.
Start fixed-base recording sends the profile command as visible in the editor.
```

For UM980 hardware smoke, inspect `tx-to-receiver.raw` after fixed-base start and confirm it contains the materialised `MODE BASE ...` command exactly once.

- [ ] **Step 5: Final review**

Before pushing, review:

```sh
git diff --cached --stat
git diff --cached -- app/src/main/kotlin/org/rtkcollector/app/recording app/src/main/kotlin/org/rtkcollector/app/profile app/src/main/kotlin/org/rtkcollector/app/ui docs
```

Check these invariants:

```text
receiver-rx.raw path remains untouched.
NTRIP upload changes do not affect correction download/client GET logic.
No password or Authorization values are logged or written to session.json.
Dashboard upload Off only changes settings/profile state, not active raw recording.
Fixed-base start no longer mutates modeCommands at start time.
```

- [ ] **Step 6: Commit remaining integration changes**

If previous tasks were batched instead of committed individually, create one integration commit:

```sh
git add app/src/main/kotlin app/src/test/kotlin docs
git commit -m "Close dashboard upload and fixed-base workflow gaps"
```

- [ ] **Step 7: Push**

Run:

```sh
git push
```

Expected: push succeeds.

## Self-Review

Spec coverage:

- Item 1, main-screen upload selector, is covered by Task 1.
- Item 2, fixed-base explicit materialisation, is covered by Task 2.
- Item 3, built-in settings cleanup, is covered by Task 3.
- Item 4, averaging regression coverage, is covered by Task 4.
- Item 5, text-field consistency, is covered by Task 5.
- Item 6, docs/status/spec alignment, is covered by Task 6.

Placeholder scan:

- The plan contains no unresolved placeholder steps.
- Every code-changing task includes concrete Kotlin snippets or exact replacement instructions.

Type consistency:

- `DashboardStatus.upload`, `DashboardSetupItem.UPLOAD`, `DashboardSelector.UPLOAD` and `dashboardUploadSelectorRows(...)` are introduced in Task 1 before later references.
- `FixedBaseProfileMaterializer.materialize(...)` and `FixedBaseMaterializationResult` are introduced in Task 2 before UI wiring uses them.
- `RecordingSettingsSet.builtIns()` and `NtripCasterUploadProfile.builtInTemplate()` are introduced in Task 3 before default store wiring uses them.
