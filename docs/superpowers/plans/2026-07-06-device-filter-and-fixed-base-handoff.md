# Device Filter And Fixed-Base Handoff Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a persistent device filter for profile selection and make the temporary-base to fixed-base handoff explicit, reversible and aligned with fixed-base settings sets.

**Architecture:** Add small domain helpers for device filtering and fixed-base handoff decisions, then thread them through the existing Compose screens and profile stores. Keep raw recording untouched; all changes are UI/profile selection and workflow-preparation logic.

**Tech Stack:** Kotlin, Jetpack Compose, Android SharedPreferences through `ProfileStores`, JUnit 5 tests, existing profile/settings models.

---

## File Structure

- Create `app/src/main/kotlin/org/rtkcollector/app/profile/ProfileDeviceFilter.kt`
  - Owns the `ProfileDeviceFilter` enum and classification helpers for settings sets and command profiles.
- Create `app/src/test/kotlin/org/rtkcollector/app/profile/ProfileDeviceFilterTest.kt`
  - Unit tests for UM980, u-blox M8T, Any and active-outside-filter inclusion.
- Modify `app/src/main/kotlin/org/rtkcollector/app/profile/ProfileStores.kt`
  - Persist selected device filter and last fixed-base settings set per device filter.
- Modify `app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileListModels.kt`
  - Compact `+` modified marker, selected outside-filter row state and reusable filtered row builders.
- Modify `app/src/test/kotlin/org/rtkcollector/app/ui/profiles/ProfileListModelsTest.kt`
  - Tests for compact modified marker, outside-filter rows and Re-apply visibility inputs.
- Modify `app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileScreens.kt`
  - Selector header/subtitle support and optional Re-apply action.
- Modify `app/src/main/kotlin/org/rtkcollector/app/ui/settings/SettingsHub.kt`
  - Add top-right Device filter dropdown and Re-apply action in Active setup.
- Modify `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardModels.kt`
  - Add device value and setup item, plus workflow-aware upload warning state inputs.
- Modify `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/HomeDashboard.kt`
  - Render six compact setup tiles: Settings, Workflow, Device, Receiver, Upload, Storage. Keep bottom-bar NTRIP/mountpoint action.
- Modify `app/src/test/kotlin/org/rtkcollector/app/ui/dashboard/DashboardStateTest.kt`
  - Tests for six setup items and upload warning logic.
- Modify `app/src/main/kotlin/org/rtkcollector/app/ui/console/DeviceConsoleScreen.kt`
  - Show the device filter around the init-profile dropdown.
- Modify `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`
  - Wire persistent filter, filtered selectors, Re-apply, Device selector and fixed-base handoff state machine.
- Create `app/src/main/kotlin/org/rtkcollector/app/base/FixedBaseHandoffPlanner.kt`
  - Pure planner for average/current coordinate choices, `MODE BASE` profile choices, settings-set choices, immutable routing, confirmation and rollback plan.
- Create `app/src/test/kotlin/org/rtkcollector/app/base/FixedBaseHandoffPlannerTest.kt`
  - Tests for coordinate choice, immutable derivation, defaults and cancel rollback.
- Modify `docs/user-workflows.md`
  - User-facing notes for Device filter, Re-apply and fixed-base handoff cancellation.
- Modify `docs/specification/workflows.md` and `docs/specification/verification-matrix.md`
  - Formal requirements and verification entries.

## Task 1: Device Filter Domain And Persistence

**Files:**
- Create: `app/src/main/kotlin/org/rtkcollector/app/profile/ProfileDeviceFilter.kt`
- Create: `app/src/test/kotlin/org/rtkcollector/app/profile/ProfileDeviceFilterTest.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/profile/ProfileStores.kt`

- [ ] **Step 1: Write failing tests for filter classification**

Create `app/src/test/kotlin/org/rtkcollector/app/profile/ProfileDeviceFilterTest.kt`:

```kotlin
package org.rtkcollector.app.profile

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProfileDeviceFilterTest {
    @Test
    fun `Any includes all settings sets and command profiles`() {
        assertTrue(ProfileDeviceFilter.ANY.matchesSettingsSet(settingsSet("um980-rover", "um980-n4")))
        assertTrue(ProfileDeviceFilter.ANY.matchesSettingsSet(settingsSet("m8t-rover", "ublox-m8t")))
        assertTrue(ProfileDeviceFilter.ANY.matchesCommandProfile(commandProfile("um980", "um980-n4")))
        assertTrue(ProfileDeviceFilter.ANY.matchesCommandProfile(commandProfile("m8t", "ublox-m8t")))
    }

    @Test
    fun `UM980 includes UM980 Unicore and N4 profiles only`() {
        assertTrue(ProfileDeviceFilter.UM980.matchesSettingsSet(settingsSet("um980", "um980-n4")))
        assertTrue(ProfileDeviceFilter.UM980.matchesSettingsSet(settingsSet("unicore", "unicore-n4")))
        assertTrue(ProfileDeviceFilter.UM980.matchesCommandProfile(commandProfile("n4", "unicore-n4")))
        assertFalse(ProfileDeviceFilter.UM980.matchesSettingsSet(settingsSet("m8t", "ublox-m8t")))
        assertFalse(ProfileDeviceFilter.UM980.matchesCommandProfile(commandProfile("m8t", "ublox-m8t")))
    }

    @Test
    fun `u-blox M8T includes M8T profiles only`() {
        assertTrue(ProfileDeviceFilter.UBLOX_M8T.matchesSettingsSet(settingsSet("m8t", "ublox-m8t")))
        assertTrue(ProfileDeviceFilter.UBLOX_M8T.matchesCommandProfile(commandProfile("m8t", "ublox-m8t")))
        assertFalse(ProfileDeviceFilter.UBLOX_M8T.matchesSettingsSet(settingsSet("f9p", "ublox-f9p")))
        assertFalse(ProfileDeviceFilter.UBLOX_M8T.matchesCommandProfile(commandProfile("um980", "um980-n4")))
    }

    @Test
    fun `from storage value falls back to Any`() {
        assertEquals(ProfileDeviceFilter.ANY, ProfileDeviceFilter.fromStorageValue(null))
        assertEquals(ProfileDeviceFilter.ANY, ProfileDeviceFilter.fromStorageValue(""))
        assertEquals(ProfileDeviceFilter.UM980, ProfileDeviceFilter.fromStorageValue("um980"))
        assertEquals(ProfileDeviceFilter.UBLOX_M8T, ProfileDeviceFilter.fromStorageValue("ublox-m8t"))
    }

    private fun commandProfile(id: String, receiverFamily: String): CommandProfile =
        CommandProfile(id = id, name = id, receiverFamily = receiverFamily)

    private fun settingsSet(id: String, receiverProfileId: String): RecordingSettingsSet =
        RecordingSettingsSet.builtInRoverNtrip().copy(
            id = id,
            name = id,
            receiverProfileId = receiverProfileId,
        )
}
```

- [ ] **Step 2: Run the new test and verify it fails**

Run:

```bash
sh gradlew :app:compileDebugUnitTestKotlin
```

Expected: FAIL because `ProfileDeviceFilter` does not exist in the unit-test
compile classpath.

- [ ] **Step 3: Implement `ProfileDeviceFilter`**

Create `app/src/main/kotlin/org/rtkcollector/app/profile/ProfileDeviceFilter.kt`:

```kotlin
package org.rtkcollector.app.profile

enum class ProfileDeviceFilter(
    val storageValue: String,
    val displayName: String,
) {
    ANY("any", "Any"),
    UM980("um980", "UM980"),
    UBLOX_M8T("ublox-m8t", "u-blox M8T"),
    ;

    fun matchesSettingsSet(settingsSet: RecordingSettingsSet): Boolean =
        matchesReceiverId(settingsSet.receiverProfileId)

    fun matchesCommandProfile(commandProfile: CommandProfile): Boolean =
        matchesReceiverId(commandProfile.receiverFamily)

    fun matchesReceiverId(value: String): Boolean {
        val normalized = value.lowercase()
        return when (this) {
            ANY -> true
            UM980 -> normalized.startsWith("um980") ||
                normalized.startsWith("unicore") ||
                normalized.contains("n4")
            UBLOX_M8T -> normalized.startsWith("ublox-m8t")
        }
    }

    companion object {
        fun fromStorageValue(value: String?): ProfileDeviceFilter =
            entries.firstOrNull { it.storageValue.equals(value.orEmpty(), ignoreCase = true) } ?: ANY
    }
}
```

- [ ] **Step 4: Persist filter in `ProfileStores`**

Modify `app/src/main/kotlin/org/rtkcollector/app/profile/ProfileStores.kt` near selected workflow methods:

```kotlin
    fun selectedDeviceFilter(): ProfileDeviceFilter =
        ProfileDeviceFilter.fromStorageValue(preferences.getString("selectedDeviceFilter", null))

    fun saveSelectedDeviceFilter(filter: ProfileDeviceFilter) {
        preferences.edit().putString("selectedDeviceFilter", filter.storageValue).apply()
    }

    fun lastFixedBaseSettingsSetId(filter: ProfileDeviceFilter): String? =
        preferences.getString("lastFixedBaseSettingsSetId.${filter.storageValue}", null)
            ?.takeIf(String::isNotBlank)

    fun saveLastFixedBaseSettingsSetId(filter: ProfileDeviceFilter, id: String?) {
        preferences.edit().apply {
            val key = "lastFixedBaseSettingsSetId.${filter.storageValue}"
            if (id.isNullOrBlank()) remove(key) else putString(key, id)
        }.apply()
    }
```

- [ ] **Step 5: Run verification**

Run:

```bash
git diff --check
sh gradlew :app:compileDebugKotlin :app:compileDebugUnitTestKotlin
```

Expected: both pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/profile/ProfileDeviceFilter.kt \
  app/src/test/kotlin/org/rtkcollector/app/profile/ProfileDeviceFilterTest.kt \
  app/src/main/kotlin/org/rtkcollector/app/profile/ProfileStores.kt
git commit -m "Add persistent profile device filter"
```

## Task 2: Filtered Profile Rows And Modified Marker

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileListModels.kt`
- Modify: `app/src/test/kotlin/org/rtkcollector/app/ui/profiles/ProfileListModelsTest.kt`

- [ ] **Step 1: Write failing tests for compact modified marker and outside-filter rows**

Append to `ProfileListModelsTest`:

```kotlin
    @Test
    fun `modified settings set uses compact plus marker`() {
        val row = ProfileListRow(
            id = "settings",
            name = "Temporary base",
            isProtected = false,
            hasLocalOverrides = true,
        )

        assertEquals("Temporary base +", row.displayName)
        assertEquals(ProfileRowTone.MODIFIED, row.tone)
    }

    @Test
    fun `clean selected settings set uses applied tone`() {
        val row = ProfileListRow(
            id = "settings",
            name = "Fixed base",
            isProtected = false,
            hasLocalOverrides = false,
            isSelected = true,
        )

        assertEquals("Fixed base", row.displayName)
        assertEquals(ProfileRowTone.APPLIED, row.tone)
    }

    @Test
    fun `outside filter subtitle is visible`() {
        val row = ProfileListRow(
            id = "active",
            name = "u-blox M8T raw",
            isProtected = false,
            hasLocalOverrides = false,
            isSelected = true,
            outsideFilter = true,
        )

        assertEquals("Outside filter", row.displaySummary)
    }
```

- [ ] **Step 2: Run compile and verify failure**

Run:

```bash
sh gradlew :app:compileDebugUnitTestKotlin
```

Expected: FAIL because `ProfileRowTone` and `outsideFilter` do not exist in the
unit-test compile classpath.

- [ ] **Step 3: Update row model**

Modify `app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileListModels.kt`:

```kotlin
enum class ProfileRowTone {
    DEFAULT,
    APPLIED,
    MODIFIED,
}

data class ProfileListRow(
    val id: String,
    val name: String,
    val isProtected: Boolean,
    val hasLocalOverrides: Boolean,
    val isSelected: Boolean = false,
    val summary: String = "",
    val warningText: String? = null,
    val outsideFilter: Boolean = false,
) {
    val displayName: String = if (hasLocalOverrides) "$name +" else name
    val tone: ProfileRowTone
        get() = when {
            hasLocalOverrides -> ProfileRowTone.MODIFIED
            isSelected -> ProfileRowTone.APPLIED
            else -> ProfileRowTone.DEFAULT
        }
    val displaySummary: String = if (outsideFilter) {
        listOf("Outside filter", summary).filter(String::isNotBlank).joinToString(" · ")
    } else {
        summary
    }
    val canEdit: Boolean = !isProtected
    val canViewDetails: Boolean = true
    val editActionLabel: String = if (isProtected) "View" else "Edit"
    val canRename: Boolean = !isProtected
    val canCopy: Boolean = true
    val canDelete: Boolean = !isProtected || hasLocalOverrides
    val hasWarning: Boolean get() = !warningText.isNullOrBlank()
}
```

Use `displaySummary` in profile rows where `summary` is rendered.

- [ ] **Step 4: Run verification**

Run:

```bash
git diff --check
sh gradlew :app:compileDebugKotlin :app:compileDebugUnitTestKotlin
```

Expected: both pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileListModels.kt \
  app/src/test/kotlin/org/rtkcollector/app/ui/profiles/ProfileListModelsTest.kt
git commit -m "Show compact settings set modification state"
```

## Task 3: Filtered Settings And Command Profile Selectors

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileScreens.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`

- [ ] **Step 1: Add selector header support**

Modify `ProfileSelectorDialog` signature:

```kotlin
fun ProfileSelectorDialog(
    title: String,
    rows: List<ProfileListRow>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    subtitle: String = "",
    emptyText: String = "No selectable profiles.",
)
```

Inside `text`, render:

```kotlin
Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    if (subtitle.isNotBlank()) {
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (rows.isEmpty()) {
        Text(emptyText)
    } else {
        LazyColumn(
            modifier = Modifier.heightIn(max = 420.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(rows, key = { it.id }) { row ->
                ProfileSelectorRow(row = row, onSelect = onSelect)
            }
        }
    }
}
```

- [ ] **Step 2: Add helper functions in `MainActivity.kt`**

Add private helpers near `dashboardSelectorRows`:

```kotlin
private fun filteredSettingsSetRows(
    settingsSets: List<RecordingSettingsSet>,
    selectedSettingsSetId: String,
    filter: ProfileDeviceFilter,
): List<ProfileListRow> {
    val visible = settingsSets.filter { filter.matchesSettingsSet(it) }
    val selected = settingsSets.firstOrNull { it.id == selectedSettingsSetId }
    val withSelected = if (selected != null && visible.none { it.id == selected.id }) visible + selected else visible
    return SettingsSetListState.from(withSelected, selectedSettingsSetId).rows.map { row ->
        if (row.id == selected?.id && !filter.matchesSettingsSet(selected)) row.copy(outsideFilter = true) else row
    }
}

private fun filteredCommandProfileRows(
    profiles: List<CommandProfile>,
    selectedCommandProfileId: String?,
    filter: ProfileDeviceFilter,
): List<ProfileListRow> {
    val visible = profiles.filter { filter.matchesCommandProfile(it) }
    val selected = profiles.firstOrNull { it.id == selectedCommandProfileId }
    val withSelected = if (selected != null && visible.none { it.id == selected.id }) visible + selected else visible
    return withSelected.map { profile ->
        profile.profileRow(
            isSelected = profile.id == selectedCommandProfileId,
        ).copy(outsideFilter = selected?.id == profile.id && !filter.matchesCommandProfile(profile))
    }
}
```

- [ ] **Step 3: Wire full Menu screens**

In `MainActivity`, create state:

```kotlin
var selectedDeviceFilter by rememberSaveable { mutableStateOf(profileStore.selectedDeviceFilter()) }
```

Use `filteredSettingsSetRows(...)` in `AppScreen.SETTINGS_SETS`.

Use `filteredCommandProfileRows(...)` in `AppScreen.COMMANDS`.

Change user-facing title from `Init/shutdown scripts` to `Init/shutdown profiles`.

- [ ] **Step 4: Wire Home quick selectors**

Change `dashboardSelectorRows(...)` to accept `deviceFilter: ProfileDeviceFilter` and use:

```kotlin
DashboardSelector.SETTINGS_SET -> filteredSettingsSetRows(settingsSets, selectedSettingsSetId, deviceFilter)
DashboardSelector.RECEIVER -> filteredCommandProfileRows(
    profileStore.commandProfiles(),
    selectedSettingsSet?.commandProfileRef?.id,
    deviceFilter,
)
```

Pass `subtitle = "Device filter: ${selectedDeviceFilter.displayName}"` when selector is `SETTINGS_SET` or `RECEIVER`.

Use empty text:

```kotlin
"No ${selectedDeviceFilter.displayName} profiles. Switch Device to Any or create/copy a matching profile."
```

- [ ] **Step 5: Run verification**

Run:

```bash
git diff --check
sh gradlew :app:compileDebugKotlin :app:compileDebugUnitTestKotlin
```

Expected: both pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileScreens.kt \
  app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt
git commit -m "Filter settings and command profile selectors"
```

## Task 4: Device Control In Settings Hub And Home Dashboard

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/settings/SettingsHub.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardModels.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/HomeDashboard.kt`
- Modify: `app/src/test/kotlin/org/rtkcollector/app/ui/dashboard/DashboardStateTest.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`

- [ ] **Step 1: Write failing dashboard test for six setup items**

Add to `DashboardStateTest`:

```kotlin
    @Test
    fun `dashboard setup items include compact device selector`() {
        assertEquals(
            listOf(
                DashboardSetupItem.SETTINGS,
                DashboardSetupItem.WORKFLOW,
                DashboardSetupItem.DEVICE,
                DashboardSetupItem.RECEIVER,
                DashboardSetupItem.UPLOAD,
                DashboardSetupItem.STORAGE,
            ),
            defaultDashboardSetupItems,
        )
    }
```

- [ ] **Step 2: Add device to dashboard model**

Modify `DashboardStatus`:

```kotlin
data class DashboardStatus(
    val settingsSet: String = "n/a",
    val workflow: String = "n/a",
    val device: String = "Any",
    val mountpoint: String = "n/a",
    val receiver: String = "n/a",
    val upload: String = "Off",
    val storage: String = "n/a",
)
```

Modify `DashboardSetupItem` and `defaultDashboardSetupItems`:

```kotlin
internal enum class DashboardSetupItem(val label: String) {
    SETTINGS("Settings"),
    WORKFLOW("Workflow"),
    DEVICE("Device"),
    RECEIVER("Receiver"),
    UPLOAD("Upload"),
    STORAGE("Storage"),
}

internal val defaultDashboardSetupItems: List<DashboardSetupItem> = listOf(
    DashboardSetupItem.SETTINGS,
    DashboardSetupItem.WORKFLOW,
    DashboardSetupItem.DEVICE,
    DashboardSetupItem.RECEIVER,
    DashboardSetupItem.UPLOAD,
    DashboardSetupItem.STORAGE,
)
```

Update `DashboardStatus.valueFor` for `SETTINGS` and `DEVICE`.

- [ ] **Step 3: Thread callbacks through Home dashboard**

Add `onDevice: () -> Unit` to `HomeDashboard`, `CompactDashboard`, `RailDashboard`, `SetupStrip`.

Map clicks:

```kotlin
DashboardSetupItem.SETTINGS -> onSettingsSet
DashboardSetupItem.WORKFLOW -> onWorkflow
DashboardSetupItem.DEVICE -> onDevice
DashboardSetupItem.RECEIVER -> onReceiver
DashboardSetupItem.UPLOAD -> onUpload
DashboardSetupItem.STORAGE -> onStorage
```

Keep the bottom-bar NTRIP/mountpoint action unchanged.

- [ ] **Step 4: Add Device dropdown in SettingsHub**

Add parameters:

```kotlin
deviceFilterLabel: String,
onDeviceFilter: () -> Unit,
onReapplySettingsSet: () -> Unit,
canReapplySettingsSet: Boolean,
```

In the `TopAppBar.actions`, render a compact `TextButton` before help:

```kotlin
TextButton(onClick = onDeviceFilter) {
    Text(deviceFilterLabel)
}
```

In Active setup section, render `Re-apply` when `canReapplySettingsSet` is true.

- [ ] **Step 5: Add `DashboardSelector.DEVICE`**

Add:

```kotlin
DEVICE("Select device filter"),
```

Rows:

```kotlin
DashboardSelector.DEVICE -> ProfileDeviceFilter.entries.map { filter ->
    ProfileListRow(
        id = filter.storageValue,
        name = filter.displayName,
        isProtected = false,
        hasLocalOverrides = false,
        isSelected = filter == selectedDeviceFilter,
    )
}
```

On select:

```kotlin
DashboardSelector.DEVICE -> {
    selectedDeviceFilter = ProfileDeviceFilter.fromStorageValue(id)
    profileStore.saveSelectedDeviceFilter(selectedDeviceFilter)
    refreshProfileUi(settingsSets)
}
```

- [ ] **Step 6: Run verification**

Run:

```bash
git diff --check
sh gradlew :app:compileDebugKotlin :app:compileDebugUnitTestKotlin
```

Expected: both pass.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/ui/settings/SettingsHub.kt \
  app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardModels.kt \
  app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/HomeDashboard.kt \
  app/src/test/kotlin/org/rtkcollector/app/ui/dashboard/DashboardStateTest.kt \
  app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt
git commit -m "Add device filter controls to dashboard"
```

## Task 5: Re-apply Settings Set

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/profile/SettingsSetModels.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/profile/ActiveRecordingConfig.kt`
- Modify: `app/src/test/kotlin/org/rtkcollector/app/profile/SettingsSetModelsTest.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileScreens.kt`
- Modify: `docs/user-workflows.md`

- [ ] **Step 1: Write failing tests for profile-reference overrides**

Add to `SettingsSetModelsTest`:

```kotlin
    @Test
    fun `settings set has local changes when profile reference override is present`() {
        val set = RecordingSettingsSet.builtInRoverNtrip().copy(
            overrides = SettingsSetOverrides(
                commandProfileRef = ProfileReference("custom-command", "Custom command"),
            ),
        )

        assertTrue(set.hasLocalOverrides)
        assertEquals("custom-command", set.effectiveCommandProfileRef().id)
    }

    @Test
    fun `clearing overrides reapplies stored settings set references`() {
        val set = RecordingSettingsSet.builtInRoverNtrip().copy(
            commandProfileRef = ProfileReference("stored-command", "Stored command"),
            overrides = SettingsSetOverrides(
                commandProfileRef = ProfileReference("custom-command", "Custom command"),
                storageProfileRef = ProfileReference("custom-storage", "Custom storage"),
            ),
        )

        val reapplied = set.reapplied()

        assertFalse(reapplied.hasLocalOverrides)
        assertEquals("stored-command", reapplied.effectiveCommandProfileRef().id)
    }
```

- [ ] **Step 2: Add reference override fields**

Modify `SettingsSetOverrides`:

```kotlin
data class SettingsSetOverrides(
    val commandProfileRef: ProfileReference? = null,
    val usbBaudProfileRef: ProfileReference? = null,
    val ntripCasterProfileRef: ProfileReference? = null,
    val ntripMountpointProfileRef: ProfileReference? = null,
    val ntripCasterUploadProfileRef: ProfileReference? = null,
    val recordingOutputProfileRef: ProfileReference? = null,
    val storageProfileRef: ProfileReference? = null,
    val baseCasterUploadEnabled: Boolean? = null,
    val command: CommandProfileOverride? = null,
    val usbBaud: UsbBaudProfileOverride? = null,
    val ntripCaster: NtripCasterOverride? = null,
    val ntripMountpoint: NtripMountpointOverride? = null,
    val ntripCasterUpload: NtripCasterUploadOverride? = null,
    val recordingOutput: RecordingOutputOverride? = null,
    val storage: StorageProfileOverride? = null,
)
```

Update `hasChanges`:

```kotlin
val hasChanges: Boolean
    get() = commandProfileRef != null ||
        usbBaudProfileRef != null ||
        ntripCasterProfileRef != null ||
        ntripMountpointProfileRef != null ||
        ntripCasterUploadProfileRef != null ||
        recordingOutputProfileRef != null ||
        storageProfileRef != null ||
        baseCasterUploadEnabled != null ||
        command?.hasChanges() == true ||
        usbBaud != null ||
        ntripCaster != null ||
        ntripMountpoint != null ||
        ntripCasterUpload != null ||
        recordingOutput != null ||
        storage != null
```

Update JSON encode/decode for `SettingsSetOverrides` to include these nullable
`ProfileReference` fields and `baseCasterUploadEnabled`. In
`SettingsSetOverrides.toJson()`, add:

```kotlin
commandProfileRef?.let { json.put("commandProfileRef", it.toJson()) }
usbBaudProfileRef?.let { json.put("usbBaudProfileRef", it.toJson()) }
ntripCasterProfileRef?.let { json.put("ntripCasterProfileRef", it.toJson()) }
ntripMountpointProfileRef?.let { json.put("ntripMountpointProfileRef", it.toJson()) }
ntripCasterUploadProfileRef?.let { json.put("ntripCasterUploadProfileRef", it.toJson()) }
recordingOutputProfileRef?.let { json.put("recordingOutputProfileRef", it.toJson()) }
storageProfileRef?.let { json.put("storageProfileRef", it.toJson()) }
baseCasterUploadEnabled?.let { json.put("baseCasterUploadEnabled", it) }
```

In `SettingsSetOverridesJson.fromJson(...)`, add constructor arguments:

```kotlin
commandProfileRef = json.optJSONObject("commandProfileRef")?.let(ProfileReference::fromJson),
usbBaudProfileRef = json.optJSONObject("usbBaudProfileRef")?.let(ProfileReference::fromJson),
ntripCasterProfileRef = json.optJSONObject("ntripCasterProfileRef")?.let(ProfileReference::fromJson),
ntripMountpointProfileRef = json.optJSONObject("ntripMountpointProfileRef")?.let(ProfileReference::fromJson),
ntripCasterUploadProfileRef = json.optJSONObject("ntripCasterUploadProfileRef")?.let(ProfileReference::fromJson),
recordingOutputProfileRef = json.optJSONObject("recordingOutputProfileRef")?.let(ProfileReference::fromJson),
storageProfileRef = json.optJSONObject("storageProfileRef")?.let(ProfileReference::fromJson),
baseCasterUploadEnabled = json.optBooleanOrNull("baseCasterUploadEnabled"),
```

- [ ] **Step 3: Add effective reference helpers**

Add extension methods in `SettingsSetModels.kt`:

```kotlin
fun RecordingSettingsSet.effectiveCommandProfileRef(): ProfileReference =
    overrides.commandProfileRef ?: commandProfileRef

fun RecordingSettingsSet.effectiveUsbBaudProfileRef(): ProfileReference =
    overrides.usbBaudProfileRef ?: usbBaudProfileRef

fun RecordingSettingsSet.effectiveNtripCasterProfileRef(): ProfileReference? =
    overrides.ntripCasterProfileRef ?: ntripCasterProfileRef

fun RecordingSettingsSet.effectiveNtripMountpointProfileRef(): ProfileReference? =
    overrides.ntripMountpointProfileRef ?: ntripMountpointProfileRef

fun RecordingSettingsSet.effectiveNtripCasterUploadProfileRef(): ProfileReference? =
    overrides.ntripCasterUploadProfileRef ?: ntripCasterUploadProfileRef

fun RecordingSettingsSet.effectiveRecordingOutputProfileRef(): ProfileReference =
    overrides.recordingOutputProfileRef ?: recordingOutputProfileRef

fun RecordingSettingsSet.effectiveBaseCasterUploadEnabled(): Boolean =
    overrides.baseCasterUploadEnabled ?: baseCasterUploadEnabled

fun RecordingSettingsSet.effectiveStorageProfileRef(): ProfileReference =
    overrides.storageProfileRef ?: storageProfileRef

fun RecordingSettingsSet.reapplied(): RecordingSettingsSet =
    copy(overrides = SettingsSetOverrides())
```

- [ ] **Step 4: Use effective references in active config**

In `ActiveRecordingConfig.from(...)`, replace direct reads such as:

```kotlin
settingsSet.commandProfileRef.id
settingsSet.storageProfileRef.id
settingsSet.ntripCasterProfileRef
settingsSet.ntripMountpointProfileRef
settingsSet.ntripCasterUploadProfileRef
settingsSet.baseCasterUploadEnabled
```

with:

```kotlin
settingsSet.effectiveCommandProfileRef().id
settingsSet.effectiveStorageProfileRef().id
settingsSet.effectiveNtripCasterProfileRef()
settingsSet.effectiveNtripMountpointProfileRef()
settingsSet.effectiveNtripCasterUploadProfileRef()
settingsSet.effectiveRecordingOutputProfileRef().id
settingsSet.effectiveBaseCasterUploadEnabled()
```

Import the helper functions from `SettingsSetModels.kt`.

- [ ] **Step 5: Store quick selector changes as overrides**

In `MainActivity`, update quick selector mutations:

```kotlin
set.copy(commandProfileRef = ProfileReference(profile.id, profile.name))
```

becomes:

```kotlin
set.copy(
    overrides = set.overrides.copy(
        commandProfileRef = ProfileReference(profile.id, profile.name),
    ),
)
```

Similarly use override fields for storage, NTRIP caster/mountpoint and upload
quick selector changes. Full profile editor saves still update the settings set
definition because the user is editing the stored settings set.

- [ ] **Step 6: Add re-apply helper in MainActivity**

Add near settings-set helper functions:

```kotlin
fun reapplySelectedSettingsSet() {
    settingsSets = settingsSets.updateSelected(selectedSettingsSetId) { set ->
        set.reapplied()
    }
    profileStore.saveSettingsSets(settingsSets)
    manualBaseCoordinate = null
    refreshProfileUi(settingsSets)
}
```

- [ ] **Step 7: Wire Re-apply action**

Pass to `SettingsHub`:

```kotlin
canReapplySettingsSet = settingsSets.firstOrNull { it.id == selectedSettingsSetId }?.hasLocalOverrides == true,
onReapplySettingsSet = { showReapplySettingsDialog = true },
```

Add an `AlertDialog`:

```kotlin
AlertDialog(
    onDismissRequest = { showReapplySettingsDialog = false },
    title = { Text("Re-apply settings set?") },
    text = { Text("This discards local changes and restores the selected settings set definition.") },
    confirmButton = {
        TextButton(onClick = {
            reapplySelectedSettingsSet()
            showReapplySettingsDialog = false
        }) { Text("Re-apply") }
    },
    dismissButton = {
        TextButton(onClick = { showReapplySettingsDialog = false }) { Text("Cancel") }
    },
)
```

- [ ] **Step 8: Document Re-apply**

Add to `docs/user-workflows.md` near settings/profile sections:

```markdown
When the active settings set shows `+`, the current setup has local changes.
Use Re-apply to discard those local changes and return to the stored settings
set without switching away and back.
```

- [ ] **Step 9: Run verification and commit**

```bash
git diff --check
sh gradlew :app:compileDebugKotlin :app:compileDebugUnitTestKotlin
git add app/src/main/kotlin/org/rtkcollector/app/profile/SettingsSetModels.kt \
  app/src/main/kotlin/org/rtkcollector/app/profile/ActiveRecordingConfig.kt \
  app/src/test/kotlin/org/rtkcollector/app/profile/SettingsSetModelsTest.kt \
  app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt \
  app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileScreens.kt \
  app/src/main/kotlin/org/rtkcollector/app/ui/settings/SettingsHub.kt \
  docs/user-workflows.md
git commit -m "Add settings set reapply action"
```

- [ ] **Step 10: Review effective-reference call sites**

Run:

```bash
rg -n "settingsSet\\.(commandProfileRef|usbBaudProfileRef|ntripCasterProfileRef|ntripMountpointProfileRef|ntripCasterUploadProfileRef|storageProfileRef|baseCasterUploadEnabled)" app/src/main/kotlin
```

Expected: remaining direct reads are either profile-editor definition reads,
rename/delete reference maintenance, or code intentionally displaying the stored
definition. Any active recording, dashboard planned-state or quick-selector
path must use effective references.

If replacements are needed, make them and amend the commit:

```bash
git add app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt \
  app/src/main/kotlin/org/rtkcollector/app/profile/ActiveRecordingConfig.kt
git commit --amend --no-edit
```

## Task 6: Device Console Filtering And Naming Cleanup

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/console/DeviceConsoleScreen.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`
- Modify: `docs/user-workflows.md`

- [ ] **Step 1: Filter console command profiles**

In `MainActivity` console block, replace:

```kotlin
val commandProfiles = profileStore.commandProfiles()
```

with:

```kotlin
val allCommandProfiles = profileStore.commandProfiles()
val commandProfiles = allCommandProfiles.filter { selectedDeviceFilter.matchesCommandProfile(it) }
val selectedOutsideFilter = allCommandProfiles.firstOrNull { it.id == selectedConsoleCommandProfileId }
    ?.takeUnless { selectedDeviceFilter.matchesCommandProfile(it) }
val consoleCommandProfiles = if (selectedOutsideFilter != null && commandProfiles.none { it.id == selectedOutsideFilter.id }) {
    commandProfiles + selectedOutsideFilter
} else {
    commandProfiles
}
```

Use `consoleCommandProfiles` for dropdown options and selected command lookup.

- [ ] **Step 2: Show device filter in console screen**

Add a small text near the init-profile dropdown:

```kotlin
Text(
    text = "Device filter: ${deviceFilterLabel}",
    style = MaterialTheme.typography.labelSmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
)
```

Pass `deviceFilterLabel = selectedDeviceFilter.displayName`.

- [ ] **Step 3: Rename user-facing strings**

Replace user-facing `Init/shutdown scripts` with `Init/shutdown profiles` in:

- `SettingsHub.kt`
- `MainActivity.kt`
- `docs/user-workflows.md`
- help text if present

Do not rename `CommandProfile` model classes.

- [ ] **Step 4: Run verification and commit**

```bash
git diff --check
sh gradlew :app:compileDebugKotlin :app:compileDebugUnitTestKotlin
git add app/src/main/kotlin/org/rtkcollector/app/ui/console/DeviceConsoleScreen.kt \
  app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt \
  app/src/main/kotlin/org/rtkcollector/app/ui/settings/SettingsHub.kt \
  docs/user-workflows.md
git commit -m "Filter device console init profiles"
```

## Task 7: Fixed-Base Handoff Planner

**Files:**
- Create: `app/src/main/kotlin/org/rtkcollector/app/base/FixedBaseHandoffPlanner.kt`
- Create: `app/src/test/kotlin/org/rtkcollector/app/base/FixedBaseHandoffPlannerTest.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/base/FixedBaseCommandProfileSelection.kt`

- [ ] **Step 1: Write planner tests**

Create `FixedBaseHandoffPlannerTest.kt`:

```kotlin
package org.rtkcollector.app.base

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rtkcollector.app.profile.CommandProfile
import org.rtkcollector.app.profile.ProfileDeviceFilter
import org.rtkcollector.app.profile.ProfileReference
import org.rtkcollector.app.profile.RecordingSettingsSet

class FixedBaseHandoffPlannerTest {
    @Test
    fun `coordinate choices expose average and current explicitly`() {
        val choices = FixedBaseHandoffPlanner.coordinateChoices(hasAverage = true, hasCurrent = true)
        assertEquals(listOf(FixedBaseCoordinateChoice.AVERAGE, FixedBaseCoordinateChoice.CURRENT), choices)
    }

    @Test
    fun `settings set choices prefer last fixed base set for device`() {
        val fixed = settingsSet("fixed", "UM980 fixed", "fixed-base", "um980-n4", "base")
        val other = settingsSet("other", "M8T fixed", "fixed-base", "ublox-m8t", "m8t-base")
        val choices = FixedBaseHandoffPlanner.settingsSetChoices(
            settingsSets = listOf(other, fixed),
            commandProfile = commandProfile("base", "um980-n4"),
            deviceFilter = ProfileDeviceFilter.UM980,
            lastFixedBaseSettingsSetId = "fixed",
        )

        assertEquals("fixed", choices.first().settingsSet.id)
        assertTrue(choices.first().isDefault)
    }

    @Test
    fun `immutable settings set routes to derivation`() {
        val builtIn = settingsSet("fixed", "UM980 fixed", "fixed-base", "um980-n4", "base", isProtected = true)
        val choice = FixedBaseHandoffPlanner.settingsSetChoices(
            settingsSets = listOf(builtIn),
            commandProfile = commandProfile("base-new", "um980-n4"),
            deviceFilter = ProfileDeviceFilter.UM980,
            lastFixedBaseSettingsSetId = null,
        ).single()

        assertEquals(FixedBaseSettingsSetAction.DERIVE_NEW_SET, choice.action)
        assertEquals("Immutable settings set: derive a new set", choice.reason)
    }

    private fun commandProfile(id: String, receiverFamily: String): CommandProfile =
        CommandProfile(id = id, name = id, receiverFamily = receiverFamily, runtimeScript = "MODE BASE TIME 120 2.5")

    private fun settingsSet(
        id: String,
        name: String,
        workflowId: String,
        receiverProfileId: String,
        commandProfileId: String,
        isProtected: Boolean = false,
    ): RecordingSettingsSet =
        RecordingSettingsSet.builtInRoverNtrip().copy(
            id = id,
            name = name,
            workflowId = workflowId,
            receiverProfileId = receiverProfileId,
            commandProfileRef = ProfileReference(commandProfileId, commandProfileId),
            isProtected = isProtected,
        )
}
```

- [ ] **Step 2: Implement planner**

Create `FixedBaseHandoffPlanner.kt`:

```kotlin
package org.rtkcollector.app.base

import org.rtkcollector.app.profile.CommandProfile
import org.rtkcollector.app.profile.ProfileDeviceFilter
import org.rtkcollector.app.profile.RecordingSettingsSet

enum class FixedBaseCoordinateChoice {
    AVERAGE,
    CURRENT,
}

enum class FixedBaseSettingsSetAction {
    USE_EXISTING,
    DERIVE_NEW_SET,
}

data class FixedBaseSettingsSetChoice(
    val settingsSet: RecordingSettingsSet,
    val action: FixedBaseSettingsSetAction,
    val isDefault: Boolean,
    val reason: String = "",
)

object FixedBaseHandoffPlanner {
    fun coordinateChoices(hasAverage: Boolean, hasCurrent: Boolean): List<FixedBaseCoordinateChoice> =
        buildList {
            if (hasAverage) add(FixedBaseCoordinateChoice.AVERAGE)
            if (hasCurrent) add(FixedBaseCoordinateChoice.CURRENT)
        }

    fun settingsSetChoices(
        settingsSets: List<RecordingSettingsSet>,
        commandProfile: CommandProfile,
        deviceFilter: ProfileDeviceFilter,
        lastFixedBaseSettingsSetId: String?,
    ): List<FixedBaseSettingsSetChoice> {
        val candidates = settingsSets
            .filter { it.workflowId.contains("fixed", ignoreCase = true) && deviceFilter.matchesSettingsSet(it) }
        val preferredId = lastFixedBaseSettingsSetId?.takeIf { id -> candidates.any { it.id == id } }
            ?: candidates.firstOrNull()?.id
        return candidates.map { set ->
            val immutable = set.isProtected
            FixedBaseSettingsSetChoice(
                settingsSet = set,
                action = if (immutable) FixedBaseSettingsSetAction.DERIVE_NEW_SET else FixedBaseSettingsSetAction.USE_EXISTING,
                isDefault = set.id == preferredId,
                reason = if (immutable) "Immutable settings set: derive a new set" else "",
            )
        }.sortedWith(compareByDescending<FixedBaseSettingsSetChoice> { it.isDefault }.thenBy { it.settingsSet.name })
    }
}
```

- [ ] **Step 3: Run verification and commit**

```bash
git diff --check
sh gradlew :app:compileDebugKotlin :app:compileDebugUnitTestKotlin
git add app/src/main/kotlin/org/rtkcollector/app/base/FixedBaseHandoffPlanner.kt \
  app/src/test/kotlin/org/rtkcollector/app/base/FixedBaseHandoffPlannerTest.kt \
  app/src/main/kotlin/org/rtkcollector/app/base/FixedBaseCommandProfileSelection.kt
git commit -m "Plan fixed-base handoff choices"
```

## Task 8: Implement Fixed-Base Handoff UI State Machine

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`
- Modify: `docs/specification/workflows.md`
- Modify: `docs/specification/verification-matrix.md`

- [ ] **Step 1: Add handoff state**

In `MainActivity`, replace the single `pendingFixedBaseCoordinate` flow with a small state:

```kotlin
private sealed interface FixedBaseHandoffUiState {
    data object None : FixedBaseHandoffUiState
    data class ChooseCoordinate(val average: AcceptedBaseCoordinate?, val current: AcceptedBaseCoordinate?) : FixedBaseHandoffUiState
    data class ChooseInitProfile(val coordinate: AcceptedBaseCoordinate) : FixedBaseHandoffUiState
    data class ChooseSettingsSet(val coordinate: AcceptedBaseCoordinate, val commandProfile: CommandProfile) : FixedBaseHandoffUiState
    data class Confirm(val coordinate: AcceptedBaseCoordinate, val commandProfile: CommandProfile, val settingsSet: RecordingSettingsSet, val deriveSettingsSet: Boolean) : FixedBaseHandoffUiState
}
```

- [ ] **Step 2: Show coordinate source dialog**

When `Base` is clicked, create both candidates when available:

```kotlin
fixedBaseHandoffUiState = FixedBaseHandoffUiState.ChooseCoordinate(
    average = averageCandidate?.toAcceptedBaseCoordinate(...),
    current = currentCandidate?.toAcceptedBaseCoordinate(...),
)
```

Dialog title: `Use coordinate for fixed base`.

Buttons: `Use average`, `Use current`, `Cancel`, with unavailable options hidden.

- [ ] **Step 3: Reuse existing MODE BASE profile selector**

After coordinate choice, show:

- derive from `FixedBaseCommandProfileSelection.templateProfiles(...)`;
- overwrite from `FixedBaseCommandProfileSelection.overwriteProfiles(...)`.

Keep current materializer behaviour: only `MODE BASE` line changes.

- [ ] **Step 4: Add settings-set selector**

Use `FixedBaseHandoffPlanner.settingsSetChoices(...)` filtered by `selectedDeviceFilter`.

Rows show:

- settings set name;
- `Default` for default row;
- `Immutable settings set: derive a new set` when applicable.

- [ ] **Step 5: Final OK/Cancel confirmation**

Final dialog text:

```text
Switching to fixed-base settings set "<settings set>" with init/shutdown profile "<profile>".
```

On OK:

- stop recording via `RecordingForegroundService.stopIntent(context)`;
- create derived settings set if required;
- save selected settings set id;
- save last fixed-base settings set id for `selectedDeviceFilter`;
- select fixed-base workflow;
- save chosen command profile reference;
- leave recording stopped.

On Cancel:

- if nothing persisted yet, return to `None`;
- if anything has been persisted, show cleanup dialog `Discard prepared fixed-base changes?`.

- [ ] **Step 6: Cleanup dialog**

For persisted artifacts, offer:

- `Discard`: delete derived command profile/settings set or restore previous profile/settings copies;
- `Keep`: leave artifacts in profile lists but keep active recording setup unchanged.

Keep default action as `Discard`.

- [ ] **Step 7: Update formal docs**

Add/update workflow requirement in `docs/specification/workflows.md`:

```markdown
Temporary-base to fixed-base handoff MUST ask whether to use averaged or current
coordinates when both exist, MUST select a fixed-base settings set explicitly,
and MUST leave the active setup unchanged when final confirmation is cancelled.
```

Update verification matrix with planner/UI tests and manual field validation.

- [ ] **Step 8: Run verification and commit**

```bash
git diff --check
sh gradlew :app:compileDebugKotlin :app:compileDebugUnitTestKotlin
git add app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt \
  docs/specification/workflows.md \
  docs/specification/verification-matrix.md
git commit -m "Implement fixed-base handoff confirmation flow"
```

## Task 9: Upload Warning State

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardModels.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/HomeDashboard.kt`
- Modify: `app/src/test/kotlin/org/rtkcollector/app/ui/dashboard/DashboardStateTest.kt`

- [ ] **Step 1: Add tests for workflow-aware upload warning**

Add tests:

```kotlin
    @Test
    fun `rover workflow does not warn for upload off`() {
        val state = DashboardStatus(workflow = "Rover", upload = "Off")
        assertFalse(uploadSetupWarning(state, commandEmitsRtcm = false))
    }

    @Test
    fun `fixed base warns for upload off`() {
        val state = DashboardStatus(workflow = "Fixed base", upload = "Off")
        assertTrue(uploadSetupWarning(state, commandEmitsRtcm = true))
    }

    @Test
    fun `temporary base warns only when command emits rtcm`() {
        assertFalse(uploadSetupWarning(DashboardStatus(workflow = "Temporary base", upload = "Off"), commandEmitsRtcm = false))
        assertTrue(uploadSetupWarning(DashboardStatus(workflow = "Temporary base", upload = "Off"), commandEmitsRtcm = true))
    }
```

- [ ] **Step 2: Implement helper**

Add:

```kotlin
internal fun uploadSetupWarning(status: DashboardStatus, commandEmitsRtcm: Boolean): Boolean {
    val uploadOff = status.upload.isBlank() ||
        status.upload.equals("Off", ignoreCase = true) ||
        status.upload.equals("n/a", ignoreCase = true)
    if (!uploadOff) return false
    val workflow = status.workflow.lowercase()
    return workflow.contains("fixed") ||
        (workflow.contains("temporary") && commandEmitsRtcm)
}
```

- [ ] **Step 3: Use helper in setup tile**

Thread `commandEmitsRtcm` from planned command profile into `HomeDashboard` or derive from a `DashboardStatus` flag.

For muted disabled state, use a tile subtitle or tooltip text `No RTCM output`.

- [ ] **Step 4: Run verification and commit**

```bash
git diff --check
sh gradlew :app:compileDebugKotlin :app:compileDebugUnitTestKotlin
git add app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardModels.kt \
  app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/HomeDashboard.kt \
  app/src/test/kotlin/org/rtkcollector/app/ui/dashboard/DashboardStateTest.kt
git commit -m "Make upload warning workflow aware"
```

## Task 10: Final Documentation And Verification

**Files:**
- Modify: `docs/user-workflows.md`
- Modify: `docs/specification/verification-matrix.md`
- Modify: `docs/superpowers/plan-status.md`

- [ ] **Step 1: Update docs**

Document:

- Device filter defaults to Any and affects settings/init-profile selectors.
- `+` means local settings-set modifications.
- Re-apply restores stored settings set.
- Temporary-base to fixed-base asks average/current, settings set, init profile and final OK/Cancel.

- [ ] **Step 2: Run final checks**

Run:

```bash
git diff --check
sh gradlew :app:compileDebugKotlin :app:compileDebugUnitTestKotlin
sh gradlew :app:unitTestClasses :app:androidTestClasses --dry-run
```

Expected:

- `git diff --check`: no output, exit 0.
- `:app:compileDebugKotlin :app:compileDebugUnitTestKotlin`: `BUILD SUCCESSFUL`.
- dry-run includes `:app:unitTestClasses` and `:app:androidTestClasses`, `BUILD SUCCESSFUL`.

Do not run full Android app unit tests in Termux if they trigger the known non-runnable `aapt2` binary failure. Run full tests on Windows Android Studio or CI.

- [ ] **Step 3: Commit docs and verification updates**

```bash
git add docs/user-workflows.md docs/specification/verification-matrix.md docs/superpowers/plan-status.md
git commit -m "Document device filter workflow"
```

- [ ] **Step 4: Push**

```bash
git push
```

Expected: branch pushes to `origin/main`.
