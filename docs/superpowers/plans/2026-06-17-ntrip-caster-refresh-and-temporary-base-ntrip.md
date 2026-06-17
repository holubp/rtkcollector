# NTRIP Caster Refresh And Temporary Base NTRIP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add refresh buttons in both the NTRIP caster editor and NTRIP mountpoint editor to update cached caster mountpoints, validate mountpoint/caster consistency without destroying the typed mountpoint, make the selected mountpoint profile's caster authoritative for the active NTRIP server shown on the dashboard and used at recording start, surface suspect saved mountpoint profiles in selection lists, and allow the Temporary base workflow to use the selected NTRIP caster/mountpoint when configured.

**Architecture:** Reuse the existing `NtripSourcetableClient` and `NtripSourcetableRequest` for caster refresh, persist refreshed mountpoints on the selected `NtripCasterProfile`, and keep mountpoint selection as a separate `NtripMountpointProfile`. A selected mountpoint profile owns both the mountpoint string and its caster reference; `RecordingSettingsSet.ntripCasterProfileRef` is synchronized display/export state, not the source of truth once a mountpoint profile is selected. Runtime start/update paths and dashboard labels must resolve the active caster from the selected `NtripMountpointProfile.casterProfileId`. The same refresh helper is reachable from caster settings and from a mountpoint profile editor; the mountpoint editor refreshes the currently selected caster profile and updates that caster persistently. Changing a mountpoint profile's caster must preserve the current mountpoint text; if the selected caster has a cached sourcetable and the mountpoint is absent from it, the editor shows a red error state and blocks Save until the user selects/types a valid mountpoint or switches to a compatible caster. Saved mountpoint profiles that become inconsistent after a sourcetable refresh are not deleted or rewritten; profile lists show an orange warning marker and "Suspect invalid mountpoint", while the main-screen mountpoint selector shows an orange warning triangle. Keep NTRIP enablement workflow-driven: plain rover stays NTRIP-off, rover+NTRIP stays NTRIP-on, and temporary base becomes NTRIP-on only because the workflow supports deriving base position with CORS/NTRIP corrections.

**Tech Stack:** Android/Kotlin, Compose profile editor, existing profile stores, `core:correction` NTRIP sourcetable client, JUnit tests.

---

## Current Evidence

- Active Compose NTRIP caster editor is built in `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt` inside `ProfileStores.profileEditorData(...)`.
- The caster editor currently shows `Known mountpoints` as a read-only list from `NtripCasterProfile.sourcetableMountpoints`, but there is no refresh action.
- The older non-Compose `app/src/main/kotlin/org/rtkcollector/app/MainActivity.kt` already demonstrates sourcetable fetch logic using `NtripSourcetableClient(NtripSourcetableRequest(...)).fetch()`.
- Recording start decides whether NTRIP is active via `workflowId.workflowUsesNtrip()` in `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`.
- `workflowUsesNtrip()` currently returns true only for `"rover-ntrip"`, so Temporary base cannot feed NTRIP even if a caster and mountpoint are selected.
- `app/src/test/kotlin/org/rtkcollector/app/ui/DashboardMountpointLabelTest.kt` already has a focused test for `workflowUsesNtrip()`.
- The dashboard/start path currently resolves the active caster from `RecordingSettingsSet.ntripCasterProfileRef`, while the mountpoint profile separately stores `casterProfileId`. If a user edits a selected mountpoint profile and changes its caster, the main screen can keep showing and using the stale settings-set caster even after the same mountpoint is re-selected.

## Files

- Modify: `docs/workflows.md`
  - Clarify that Temporary base may use selected CORS/NTRIP correction profiles.
- Modify: `docs/user-workflows.md`
  - Clarify caster mountpoint refresh in the NTRIP caster editor and Temporary base with NTRIP.
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`
  - Add a refresh action to the NTRIP caster editor.
  - Add a small sourcetable refresh helper using `NtripSourcetableClient`.
  - Update `workflowUsesNtrip()` to include Temporary base.
  - Resolve dashboard/start/update NTRIP caster state from the selected mountpoint profile's `casterProfileId`.
- Create: `app/src/main/kotlin/org/rtkcollector/app/ui/NtripProfileResolution.kt`
  - Add a small pure helper that resolves selected caster/mountpoint profiles and synchronizes stale settings-set caster references.
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileEditorModels.kt`
  - Add reusable labels/helpers for NTRIP mountpoint refresh and warning actions if needed.
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileListModels.kt`
  - Add field-level validation/error metadata for editor fields and warning metadata for profile rows.
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileScreens.kt`
  - Render invalid fields in error colour with an exclamation marker and supporting text; block Save while any field has a validation error; render orange warning markers in profile rows.
- Modify: `app/src/test/kotlin/org/rtkcollector/app/ui/DashboardMountpointLabelTest.kt`
  - Extend workflow NTRIP detection tests to cover Temporary base.
  - Cover selected-mountpoint caster resolution so stale settings-set caster references cannot drive the dashboard or recording start.
- Modify: `app/src/test/kotlin/org/rtkcollector/app/ui/profiles/ProfileListModelsTest.kt`
  - Cover field-level error metadata and saved-profile warning metadata for invalid mountpoint/caster combinations.
- Modify or add: `app/src/test/kotlin/org/rtkcollector/app/profile/ActiveRecordingConfigTest.kt`
  - Add explicit Temporary base NTRIP active-config coverage if the current config tests do not already cover it.
- Optional create: `app/src/test/kotlin/org/rtkcollector/app/profile/NtripCasterRefreshTest.kt`
  - Only create if the refresh helper is extracted into a testable non-Android function.

## Task 1: Document Intended Behaviour

**Files:**
- Modify: `docs/workflows.md`
- Modify: `docs/user-workflows.md`

- [ ] **Step 1: Update workflow specification text**

In `docs/workflows.md`, update the Temporary base section so it states:

```markdown
Temporary base may run with no corrections or with a selected NTRIP/CORS
caster and mountpoint. When a Temporary base workflow has NTRIP profiles
selected, Android acts as the NTRIP client and feeds RTCM corrections into the
receiver, while raw receiver RX and correction-input artifacts remain separate.
```

- [ ] **Step 2: Update user workflow documentation**

In `docs/user-workflows.md`, update the NTRIP settings and Temporary base sections with:

```markdown
The NTRIP caster editor includes a refresh action for the selected caster. It
connects to the caster sourcetable, replaces the cached mountpoint list on that
caster profile, and leaves the selected mountpoint profile unchanged until the
user selects or edits one.

The NTRIP mountpoint editor includes the same refresh action for the currently
selected caster, so a stale list can be updated without leaving the mountpoint
configuration screen. If the refreshed or selected caster list does not contain
the current mountpoint text, the editor preserves the text, marks the field as
invalid, and blocks saving until a compatible caster or mountpoint is selected.

Mountpoint profile lists show suspect saved profiles with an orange warning
triangle and the text `Suspect invalid mountpoint`. The main-screen mountpoint
selector shows the same profile-level suspect state with a compact orange
warning triangle.

Temporary base can use the same selected NTRIP caster and mountpoint profiles as
rover+NTRIP. This is intended for deriving the temporary base coordinate from a
CORS/EUREF/base correction stream. Plain rover recording remains NTRIP-off.
```

- [ ] **Step 3: Commit docs checkpoint**

Run:

```bash
git diff --check
git add docs/workflows.md docs/user-workflows.md
git commit -m "Document temporary base NTRIP and caster refresh"
```

Expected: commit succeeds with only documentation changes.

## Task 2: Make Temporary Base NTRIP-Aware

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`
- Modify: `app/src/test/kotlin/org/rtkcollector/app/ui/DashboardMountpointLabelTest.kt`
- Modify: `app/src/test/kotlin/org/rtkcollector/app/profile/ActiveRecordingConfigTest.kt`

- [ ] **Step 1: Write the failing workflow detection test**

In `DashboardMountpointLabelTest`, update `ntrip workflow detection is explicit` to:

```kotlin
@Test
fun `ntrip workflow detection is explicit`() {
    assertEquals(false, "plain-rover".workflowUsesNtrip())
    assertEquals(true, "rover-ntrip".workflowUsesNtrip())
    assertEquals(true, "base-calibration".workflowUsesNtrip())
    assertEquals(false, "fixed-base".workflowUsesNtrip())
    assertEquals(false, "nontrip-debug".workflowUsesNtrip())
}
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run if local Android unit tests are available:

```bash
sh gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.ui.DashboardMountpointLabelTest
```

Expected before implementation: failure because `"base-calibration".workflowUsesNtrip()` returns false.

If running in Termux/aarch64 and the command fails in `:app:processDebugResources` with the known `aapt2` error, record that blocker and continue with `sh gradlew :app:compileDebugKotlin` after implementation.

- [ ] **Step 3: Implement the minimal workflow helper change**

In `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`, change:

```kotlin
internal fun String.workflowUsesNtrip(): Boolean =
    this == WORKFLOW_ROVER_NTRIP
```

to:

```kotlin
internal fun String.workflowUsesNtrip(): Boolean =
    this == WORKFLOW_ROVER_NTRIP || this == WORKFLOW_BASE_CALIBRATION
```

- [ ] **Step 4: Add active-config regression coverage**

In `ActiveRecordingConfigTest`, add:

```kotlin
@Test
fun `temporary base config enables ntrip when workflow supports corrections`() {
    val config = ActiveRecordingConfig.resolve(
        settingsSet = RecordingSettingsSet.builtInRoverNtrip().copy(workflowId = "base-calibration"),
        commandProfile = CommandProfile("commands", "Commands", initScript = "UNLOG COM1", runtimeScript = "MODE ROVER"),
        usbBaudProfile = UsbBaudProfile("baud", "Baud", profileBaud = 230400, serialBaud = 921600),
        ntripCasterProfile = NtripCasterProfile(
            id = "caster",
            name = "Caster",
            host = "caster.example.org",
            port = 2101,
            username = "user",
            secretId = "secret",
        ),
        ntripMountpointProfile = NtripMountpointProfile(
            id = "mount",
            name = "Mount",
            casterProfileId = "caster",
            mountpoint = "CORS01",
        ),
        recordingPolicyProfile = RecordingPolicyProfile(
            id = "record",
            name = "Record",
            recordTxToReceiver = true,
            recordNtripCorrectionInput = true,
        ),
        storageProfile = StorageProfile("storage", "Storage"),
        workflowName = "Temporary base",
        workflowUsesNtrip = true,
        passwordLookup = { "password-for-$it" },
    )

    assertTrue(config.ntrip.enabled)
    assertEquals("caster.example.org", config.ntrip.host)
    assertEquals("CORS01", config.ntrip.mountpoint)
    assertTrue(config.recording.expectedSessionArtifacts.contains(SessionArtifact.CORRECTION_INPUT_RAW))
    assertTrue(config.recording.expectedSessionArtifacts.contains(SessionArtifact.CORRECTION_INPUT_RTCM3))
}
```

- [ ] **Step 5: Verify compile and commit**

Run:

```bash
git diff --check
sh gradlew :app:compileDebugKotlin
```

If Android unit tests are runnable on the current host, also run:

```bash
sh gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.ui.DashboardMountpointLabelTest
sh gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.profile.ActiveRecordingConfigTest
```

Commit:

```bash
git add app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt \
  app/src/test/kotlin/org/rtkcollector/app/ui/DashboardMountpointLabelTest.kt \
  app/src/test/kotlin/org/rtkcollector/app/profile/ActiveRecordingConfigTest.kt
git commit -m "Enable NTRIP for temporary base workflow"
```

## Task 3: Resolve Active NTRIP Caster From Selected Mountpoint

**Files:**
- Create: `app/src/main/kotlin/org/rtkcollector/app/ui/NtripProfileResolution.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`
- Modify: `app/src/test/kotlin/org/rtkcollector/app/ui/DashboardMountpointLabelTest.kt`

- [ ] **Step 1: Write failing active-caster resolution tests**

In `DashboardMountpointLabelTest`, add tests for the bug where the selected
mountpoint profile's caster is changed but the dashboard still shows the old
settings-set caster:

```kotlin
@Test
fun `selected mountpoint profile determines active caster even when settings set caster is stale`() {
    val oldCaster = NtripCasterProfile(id = "old", name = "Old", host = "old.example.org")
    val newCaster = NtripCasterProfile(id = "new", name = "New", host = "new.example.org")
    val mountpoint = NtripMountpointProfile(
        id = "mount",
        name = "Mount",
        casterProfileId = "new",
        mountpoint = "TUBO00CZE0",
    )
    val settingsSet = RecordingSettingsSet.builtInRoverNtrip().copy(
        ntripCasterProfileRef = ProfileReference("old", "Old"),
        ntripMountpointProfileRef = ProfileReference("mount", "Mount"),
    )

    val resolved = settingsSet.resolveNtripProfiles(
        casterProfiles = listOf(oldCaster, newCaster),
        mountpointProfiles = listOf(mountpoint),
    )

    assertEquals("new", resolved.caster?.id)
    assertEquals("new.example.org", resolved.caster?.host)
    assertEquals(ProfileReference("new", "New"), resolved.settingsSet.ntripCasterProfileRef)
}

@Test
fun `active caster falls back to settings set only when no mountpoint profile is selected`() {
    val caster = NtripCasterProfile(id = "caster", name = "Caster", host = "caster.example.org")
    val settingsSet = RecordingSettingsSet.builtInRoverNtrip().copy(
        ntripCasterProfileRef = ProfileReference("caster", "Caster"),
        ntripMountpointProfileRef = null,
    )

    val resolved = settingsSet.resolveNtripProfiles(
        casterProfiles = listOf(caster),
        mountpointProfiles = emptyList(),
    )

    assertEquals("caster", resolved.caster?.id)
    assertEquals(settingsSet, resolved.settingsSet)
}
```

- [ ] **Step 2: Add pure NTRIP profile resolver**

Create `app/src/main/kotlin/org/rtkcollector/app/ui/NtripProfileResolution.kt`:

```kotlin
package org.rtkcollector.app.ui

import org.rtkcollector.app.profile.NtripCasterProfile
import org.rtkcollector.app.profile.NtripMountpointProfile
import org.rtkcollector.app.profile.ProfileReference
import org.rtkcollector.app.profile.RecordingSettingsSet

internal data class ResolvedNtripProfiles(
    val caster: NtripCasterProfile?,
    val mountpoint: NtripMountpointProfile?,
    val settingsSet: RecordingSettingsSet,
)

internal fun RecordingSettingsSet.resolveNtripProfiles(
    casterProfiles: List<NtripCasterProfile>,
    mountpointProfiles: List<NtripMountpointProfile>,
): ResolvedNtripProfiles {
    val mountpoint = ntripMountpointProfileRef?.id
        ?.let { id -> mountpointProfiles.firstOrNull { it.id == id } }
    val casterFromMountpoint = mountpoint?.casterProfileId
        ?.let { casterId -> casterProfiles.firstOrNull { it.id == casterId } }
    if (mountpoint != null) {
        val syncedSettingsSet = if (casterFromMountpoint != null &&
            ntripCasterProfileRef?.id != casterFromMountpoint.id
        ) {
            copy(ntripCasterProfileRef = ProfileReference(casterFromMountpoint.id, casterFromMountpoint.name))
        } else {
            this
        }
        return ResolvedNtripProfiles(
            caster = casterFromMountpoint,
            mountpoint = mountpoint,
            settingsSet = syncedSettingsSet,
        )
    }

    val caster = ntripCasterProfileRef?.id
        ?.let { id -> casterProfiles.firstOrNull { it.id == id } }
    return ResolvedNtripProfiles(caster = caster, mountpoint = null, settingsSet = this)
}
```

This deliberately keeps the typed/selected mountpoint unchanged. If the
mountpoint profile references a missing caster, the resolver returns
`caster = null`; start validation must then fail with a clear NTRIP caster
configuration error instead of silently using an old server.

- [ ] **Step 3: Use the resolver in dashboard labels, start and live NTRIP updates**

In `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`, replace direct
lookups like:

```kotlin
val ntripCaster = settingsSet.ntripCasterProfileRef?.let { reference ->
    profileStore.ntripCasterProfiles().findByReference(reference.id, "NTRIP caster profile")
}
val ntripMountpoint = settingsSet.ntripMountpointProfileRef?.let { reference ->
    profileStore.ntripMountpointProfiles().findByReference(reference.id, "NTRIP mountpoint profile")
}
```

with:

```kotlin
val ntripResolution = settingsSet.resolveNtripProfiles(
    casterProfiles = profileStore.ntripCasterProfiles(),
    mountpointProfiles = profileStore.ntripMountpointProfiles(),
)
val ntripCaster = ntripResolution.caster
val ntripMountpoint = ntripResolution.mountpoint
val resolvedSettingsSet = ntripResolution.settingsSet
```

Use `resolvedSettingsSet` for `ActiveRecordingConfig.resolve(...)`.

Apply this to at least:

- `buildDashboardStartIntent(...)`;
- `buildNtripUpdateIntent(...)`;
- `plannedDashboardState(...)` or the helper feeding its NTRIP server label;
- `selectedNtripCasterProfileLabel(...)`;
- `selectedCasterMountpoints(...)`.

The main screen must update as soon as the selected mountpoint profile's caster
changes; re-selecting the mountpoint must not be required.

- [ ] **Step 4: Synchronize settings-set caster reference when mountpoint selection or profile edit changes it**

When the user selects a mountpoint on the main screen, update both references:

```kotlin
val caster = profileStore.ntripCasterProfiles().firstOrNull { it.id == profile.casterProfileId }
set.copy(
    ntripCasterProfileRef = caster?.let { ProfileReference(it.id, it.name) },
    ntripMountpointProfileRef = ProfileReference(profile.id, profile.name),
    overrides = set.overrides.copy(ntripMountpoint = null),
)
```

When saving an edited `NtripMountpointProfile`, if any settings set currently
selects that mountpoint profile, synchronize that settings set's
`ntripCasterProfileRef` to the edited profile's `casterProfileId`. This
preserves export/display consistency and ensures the dashboard updates without
forcing the user to re-select the mountpoint profile.

- [ ] **Step 5: Add missing-caster and edited-caster regression coverage if seams exist**

If the settings-set update logic is extracted to a pure helper, add tests:

```kotlin
@Test
fun `saving selected mountpoint profile with new caster synchronizes selected settings set`() {
    // Given settings set selects mountpoint M and still references caster A,
    // when M is saved with caster B, the settings set caster ref becomes B.
}

@Test
fun `mountpoint profile with missing caster does not fall back to stale caster`() {
    // Given settings set references old caster A and selected mountpoint M references
    // missing caster B, resolver returns caster null and the same mountpoint.
}
```

Do not create artificial production API only for these tests. The resolver test
from Step 1 is mandatory; persistence synchronization may be covered by
manual checklist if it remains inside Compose state handling.

- [ ] **Step 6: Verify compile and commit**

Run:

```bash
git diff --check
sh gradlew :app:compileDebugKotlin
```

If unit tests are runnable on the current host, run:

```bash
sh gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.ui.DashboardMountpointLabelTest
```

Commit:

```bash
git add app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt \
  app/src/main/kotlin/org/rtkcollector/app/ui/NtripProfileResolution.kt \
  app/src/test/kotlin/org/rtkcollector/app/ui/DashboardMountpointLabelTest.kt
git commit -m "Resolve active NTRIP caster from selected mountpoint"
```

## Task 4: Mark Mountpoints Invalid When Caster Changes

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileListModels.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileScreens.kt`
- Modify: `app/src/test/kotlin/org/rtkcollector/app/ui/profiles/ProfileListModelsTest.kt`

- [ ] **Step 1: Add failing field metadata test**

In `ProfileListModelsTest`, add:

```kotlin
@Test
fun `editable profile field can expose validation error`() {
    val field = EditableProfileField(
        key = "mountpoint",
        label = "Mountpoint",
        value = "OLD",
        errorText = "Mountpoint is not in the selected caster sourcetable.",
    )

    assertTrue(field.hasError)
    assertEquals("Mountpoint is not in the selected caster sourcetable.", field.errorText)
}
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run if local Android unit tests are available:

```bash
sh gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.ui.profiles.ProfileListModelsTest
```

Expected before implementation: compile/test failure because `EditableProfileField.errorText` and `hasError` do not exist.

If the local Termux environment fails in Android resource processing, record the blocker and continue to `sh gradlew :app:compileDebugKotlin` after implementation.

- [ ] **Step 3: Add field-level error metadata**

In `app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileListModels.kt`, change `EditableProfileField` to include:

```kotlin
data class EditableProfileField(
    val key: String,
    val label: String,
    val value: String,
    val multiline: Boolean = false,
    val secret: Boolean = false,
    val boolean: Boolean = false,
    val options: List<String> = emptyList(),
    val optionItems: List<EditableProfileOption> = options.map { EditableProfileOption(it, it) },
    val readOnly: Boolean = false,
    val readOnlyList: List<String> = emptyList(),
    val errorText: String? = null,
) {
    val hasError: Boolean get() = !errorText.isNullOrBlank()
}
```

- [ ] **Step 4: Add saved-profile warning metadata**

In `app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileListModels.kt`,
extend `ProfileListRow`:

```kotlin
data class ProfileListRow(
    val id: String,
    val name: String,
    val isProtected: Boolean,
    val hasLocalOverrides: Boolean,
    val isSelected: Boolean = false,
    val summary: String = "",
    val warningText: String? = null,
) {
    val displayName: String = if (hasLocalOverrides) "$name + local changes" else name
    val canEdit: Boolean = !isProtected
    val canRename: Boolean = !isProtected
    val canCopy: Boolean = true
    val canDelete: Boolean = !isProtected || hasLocalOverrides
    val hasWarning: Boolean get() = !warningText.isNullOrBlank()
}
```

Add a test in `ProfileListModelsTest`:

```kotlin
@Test
fun `profile list row can expose warning state`() {
    val row = ProfileListRow(
        id = "mount",
        name = "Mount",
        isProtected = false,
        hasLocalOverrides = false,
        warningText = "Suspect invalid mountpoint",
    )

    assertTrue(row.hasWarning)
    assertEquals("Suspect invalid mountpoint", row.warningText)
}
```

- [ ] **Step 5: Compute mountpoint validity from selected caster**

In the `ProfileKind.NTRIP_MOUNTPOINT` branch of `ProfileStores.profileEditorData(...)`, keep deriving `selectedCasterMountpoints` from `profile.casterProfileId`, then compute:

```kotlin
val mountpointError = profile.mountpoint
    .takeIf(String::isNotBlank)
    ?.takeIf { selectedCasterMountpoints.isNotEmpty() && it !in selectedCasterMountpoints }
    ?.let { "Mountpoint is not in the selected caster sourcetable." }
```

Pass it to the mountpoint field:

```kotlin
EditableProfileField(
    key = "mountpoint",
    label = "Mountpoint",
    value = profile.mountpoint,
    optionItems = selectedCasterMountpoints.map { EditableProfileOption(it, it) },
    errorText = mountpointError,
)
```

This means:

- if the selected caster has no cached sourcetable, do not mark the mountpoint invalid;
- if the mountpoint field is blank, do not mark it invalid here because start validation already handles required mountpoints for NTRIP workflows;
- if the selected caster has cached mountpoints and the current mountpoint is absent, mark it invalid.

- [ ] **Step 6: Render invalid fields with red error state and exclamation marker**

In `ProfileEditorScreen`, update all editable field render paths to use `field.hasError`:

- for `OutlinedTextField`, set `isError = field.hasError`;
- for dropdown `OutlinedTextField`, set `isError = field.hasError`;
- after each rendered field that has an error, render:

```kotlin
if (field.hasError) {
    Text(
        text = "! ${field.errorText}",
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.labelSmall,
    )
}
```

For boolean rows and read-only lists, place the same error text below the row/list if future callers set an error. Do not add new icons or dependencies; the literal `!` is sufficient for this iteration.

- [ ] **Step 7: Compute saved-profile warnings for mountpoint rows**

`NtripMountpointProfile.profileRow()` currently does not know the selected
caster's cached sourcetable. Replace or overload it with a helper that receives
caster profiles:

```kotlin
private fun NtripMountpointProfile.profileRow(
    casters: List<NtripCasterProfile>,
    isSelected: Boolean = false,
): ProfileListRow {
    val caster = casters.firstOrNull { it.id == casterProfileId }
    val suspect = mountpoint.isNotBlank() &&
        caster?.sourcetableMountpoints?.isNotEmpty() == true &&
        mountpoint !in caster.sourcetableMountpoints
    return ProfileListRow(
        id = id,
        name = name,
        isProtected = isProtected,
        hasLocalOverrides = false,
        isSelected = isSelected,
        summary = listOf(casterProfileId, mountpoint.ifBlank { "mountpoint not set" }).joinToString(" · "),
        warningText = if (suspect) SuspectInvalidMountpointWarning else null,
    )
}
```

Use this helper everywhere mountpoint rows are displayed, including:

- `AppScreen.NTRIP_MOUNTPOINT_PROFILES`;
- `AppScreen.MOUNTPOINT_SELECTOR`;
- any settings-set summaries only if they already show per-profile warning
  metadata without cluttering the UI.

- [ ] **Step 8: Render saved-profile warnings with an orange triangle**

In `ProfileListItem`, if `row.hasWarning`, render a compact warning row under
the profile name/summary:

```kotlin
if (row.hasWarning) {
    Text(
        text = "⚠ ${row.warningText}",
        color = Color(0xFFB26A00),
        style = MaterialTheme.typography.labelSmall,
    )
}
```

Use this in the full NTRIP mountpoint profile list. For the main-screen
mountpoint selector, keep the row compact: rendering only `⚠` near the profile
name is acceptable, but the full profile-management list must show
`Suspect invalid mountpoint`.

- [ ] **Step 9: Preserve mountpoint text and update the error immediately when selecting a different caster**

Current `ProfileEditorScreen` creates `values` from `data.fields` and updates local values as dropdowns change. Because `data.fields` is built from saved profile values, changing `casterProfileId` inside the editor may not recompute the mountpoint field error until save/reopen.

Do not clear, replace or auto-select the mountpoint value when `casterProfileId`
changes. The existing value in `values["mountpoint"]` must stay intact so an
accidental caster change can be undone without retyping the mountpoint.

To support immediate feedback, give the `ProfileEditorScreen` enough context to
derive mountpoint options from the currently selected caster. The smallest
acceptable implementation is:

1. Pass all NTRIP caster choices and their cached mountpoints to the mountpoint
   editor field model.
2. Recompute the mountpoint field's option list and error from
   `values["casterProfileId"]` on every recomposition.
3. Use the current `values["mountpoint"]` unchanged.

Add metadata to `EditableProfileField` if needed:

```kotlin
val optionGroups: Map<String, List<EditableProfileOption>> = emptyMap()
```

For the `mountpoint` field, populate `optionGroups` with caster ID to
mountpoint options in `ProfileStores.profileEditorData(...)`:

```kotlin
val mountpointOptionsByCaster = ntripCasterProfiles().associate { caster ->
    caster.id to caster.sourcetableMountpoints.map { EditableProfileOption(it, it) }
}
```

Then implement only the mountpoint case in `ProfileEditorScreen`:

```kotlin
val runtimeField = if (field.key == "mountpoint") {
    val selectedCasterId = values["casterProfileId"].orEmpty()
    val currentMountpoint = values[field.key].orEmpty()
    val optionItems = field.optionGroups[selectedCasterId] ?: field.optionItems
    val knownMountpoints = optionItems.map { it.value }.filter(String::isNotBlank)
    val runtimeError = currentMountpoint
        .takeIf(String::isNotBlank)
        ?.takeIf { knownMountpoints.isNotEmpty() && it !in knownMountpoints }
        ?.let { "Mountpoint is not in the selected caster sourcetable." }
    field.copy(optionItems = optionItems, errorText = runtimeError)
} else {
    field
}
```

Use `runtimeField` for rendering. This ensures that switching from caster A to
caster B immediately changes the drop-down options and the red error state, but
does not overwrite the mountpoint text.

- [ ] **Step 10: Block Save while mountpoint is invalid**

In `ProfileEditorScreen`, compute the runtime fields before rendering actions:

```kotlin
val runtimeFields = data.fields.map { field -> field.withRuntimeProfileValidation(values) }
val editorHasErrors = runtimeFields.any { it.hasError }
```

Then update `runAction(action)` or the Save button path so profile saving is
blocked when `editorHasErrors` is true:

```kotlin
if (editorHasErrors) {
    return
}
```

The UI should keep the Save button visible but disabled, or show a toast/snackbar
when Save is pressed. Prefer disabling Save if the current button implementation
allows it without broad refactoring. The user-facing reason must remain visible
on the field itself via the red exclamation text.

- [ ] **Step 11: Add regression coverage for save blocking if there is a testable seam**

If `ProfileEditorScreen` save-state logic is extracted into a small pure helper,
add a test in `ProfileListModelsTest`:

```kotlin
@Test
fun `profile editor cannot save while mountpoint field has validation error`() {
    val fields = listOf(
        EditableProfileField(
            key = "mountpoint",
            label = "Mountpoint",
            value = "OLD",
            errorText = "Mountpoint is not in the selected caster sourcetable.",
        ),
    )

    assertFalse(fields.canSaveProfileEditor())
}
```

If no helper is extracted, do not add a fake API only for this assertion; cover
the behaviour in the manual UI checklist and rely on Compose compile.

- [ ] **Step 12: Verify compile and commit**

Run:

```bash
git diff --check
sh gradlew :app:compileDebugKotlin
```

If unit tests are runnable on the current host, run:

```bash
sh gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.ui.profiles.ProfileListModelsTest
```

Commit:

```bash
git add app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt \
  app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileListModels.kt \
  app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileScreens.kt \
  app/src/test/kotlin/org/rtkcollector/app/ui/profiles/ProfileListModelsTest.kt
git commit -m "Mark invalid NTRIP mountpoints in profile editor"
```

## Task 5: Add NTRIP Caster Mountpoint Refresh Action

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileEditorModels.kt`
- Modify: `app/src/test/kotlin/org/rtkcollector/app/ui/profiles/ProfileListModelsTest.kt`

- [ ] **Step 1: Add action label helpers**

In `ProfileEditorModels.kt`, add:

```kotlin
const val RefreshNtripCasterMountpointsLabel = "Refresh mountpoints from caster"
const val SuspectInvalidMountpointWarning = "Suspect invalid mountpoint"
```

- [ ] **Step 2: Add failing profile-editor data test or record why no model seam exists**

In `ProfileListModelsTest`, add or update a test so NTRIP caster editor data has the read-only known mountpoints field and the implementation exposes an action label. If profile actions are not currently exposed through `ProfileEditorData`, write down that finding in the implementation notes for this task and keep the verification as a Compose compile plus manual UI checklist instead of forcing a production seam only for the test.

Suggested test if actions are modelled:

```kotlin
@Test
fun `ntrip caster editor exposes refresh mountpoints action`() {
    val actions = ntripCasterEditorActions()

    assertTrue(actions.any { it.label == RefreshNtripCasterMountpointsLabel })
}
```

If no `ntripCasterEditorActions()` seam exists, do not add a fake seam only for this test; rely on the final Compose compile and manual UI check.

- [ ] **Step 3: Add the refresh action in the NTRIP caster editor branch**

In `AppScreen.PROFILE_EDITOR`, where `actions = buildList { ... }` is built for the selected profile, add a branch for `ProfileKind.NTRIP_CASTER`:

```kotlin
if (target.kind == ProfileKind.NTRIP_CASTER) {
    add(
        ProfileEditorAction(
            label = RefreshNtripCasterMountpointsLabel,
            onClickWithValues = { values ->
                refreshNtripCasterMountpoints(
                    context = context,
                    targetId = target.id,
                    values = values,
                    passwordLookup = secretStore::getPassword,
                    savePassword = secretStore::savePassword,
                    profileStore = profileStore,
                    onSuccess = { count ->
                        refreshProfileUi()
                        Toast.makeText(context, "Fetched $count mountpoints.", Toast.LENGTH_LONG).show()
                    },
                    onFailure = { message ->
                        Toast.makeText(context, "Mountpoint refresh failed: $message", Toast.LENGTH_LONG).show()
                    },
                )
            },
        ),
    )
}
```

Use the existing `ProfileEditorAction.onClickWithValues` path so unsaved host, port, username or password edits in the caster editor are used for the refresh.

- [ ] **Step 4: Add the refresh action in the NTRIP mountpoint editor branch**

In `AppScreen.PROFILE_EDITOR`, add a branch for
`ProfileKind.NTRIP_MOUNTPOINT`. The action must use the current
`casterProfileId` editor value, not only the saved profile value:

```kotlin
if (target.kind == ProfileKind.NTRIP_MOUNTPOINT) {
    add(
        ProfileEditorAction(
            label = RefreshNtripCasterMountpointsLabel,
            onClickWithValues = { values ->
                val casterId = values["casterProfileId"].orEmpty()
                refreshNtripCasterMountpointsForProfileId(
                    context = context,
                    casterProfileId = casterId,
                    profileStore = profileStore,
                    passwordLookup = secretStore::getPassword,
                    onSuccess = { count ->
                        refreshProfileUi()
                        Toast.makeText(context, "Fetched $count mountpoints.", Toast.LENGTH_LONG).show()
                    },
                    onFailure = { message ->
                        Toast.makeText(context, "Mountpoint refresh failed: $message", Toast.LENGTH_LONG).show()
                    },
                )
            },
        ),
    )
}
```

The refresh must update the selected caster profile's `sourcetableMountpoints`
persistently, exactly as if the refresh had been run from caster settings. It
must not edit the current mountpoint profile's `mountpoint` string.

- [ ] **Step 5: Implement the refresh helpers**

Add a private helper in `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt` near other profile helper functions:

```kotlin
private fun refreshNtripCasterMountpoints(
    context: Context,
    targetId: String,
    values: Map<String, String>,
    passwordLookup: (String) -> String?,
    savePassword: (String, String) -> Unit,
    profileStore: ProfileStores,
    onSuccess: (Int) -> Unit,
    onFailure: (String) -> Unit,
) {
    val host = values["host"].orEmpty().trim()
    val port = values["port"]?.toIntOrNull() ?: 2101
    val username = values["username"].orEmpty().trim()
    val password = values["password"].orEmpty()
    if (host.isBlank()) {
        onFailure("NTRIP host is blank.")
        return
    }
    if (port !in 1..65535) {
        onFailure("NTRIP port must be 1..65535.")
        return
    }
    val existing = profileStore.ntripCasterProfiles().firstOrNull { it.id == targetId }
    if (existing == null) {
        onFailure("NTRIP caster profile no longer exists.")
        return
    }
    val secretId = when {
        password.isNotBlank() && existing.secretId.isNotBlank() -> existing.secretId
        password.isNotBlank() -> "ntrip:$host:$targetId:$username"
        else -> existing.secretId
    }
    if (password.isNotBlank()) {
        savePassword(secretId, password)
    }
    val resolvedPassword = password.ifBlank {
        secretId.takeIf(String::isNotBlank)?.let(passwordLookup).orEmpty()
    }
    val credentials = username.takeIf(String::isNotBlank)?.let {
        org.rtkcollector.core.correction.NtripCredentials(it, resolvedPassword)
    }

    Thread(
        {
            runCatching {
                org.rtkcollector.core.correction.NtripSourcetableClient(
                    org.rtkcollector.core.correction.NtripSourcetableRequest(
                        host = host,
                        port = port,
                        credentials = credentials,
                    ),
                ).fetch()
            }.onSuccess { result ->
                runOnMain(context) {
                    val updatedProfiles = profileStore.ntripCasterProfiles().map { profile ->
                        if (profile.id == targetId) {
                            profile.copy(
                                host = host,
                                port = port,
                                username = username,
                                secretId = secretId,
                                sourcetableMountpoints = result.mountpoints,
                            ).also(NtripCasterProfile::validate)
                        } else {
                            profile
                        }
                    }
                    profileStore.saveNtripCasterProfiles(updatedProfiles)
                    onSuccess(result.mountpoints.size)
                }
            }.onFailure { error ->
                runOnMain(context) {
                    onFailure(error.message ?: error.javaClass.simpleName)
                }
            }
        },
        "rtkcollector-ntrip-caster-refresh",
    ).start()
}
```

Adapt import qualification as needed. Do not store plaintext passwords in profile JSON; continue using `NtripSecretStore`.

Also add a wrapper used by the mountpoint editor:

```kotlin
private fun refreshNtripCasterMountpointsForProfileId(
    context: Context,
    casterProfileId: String,
    profileStore: ProfileStores,
    passwordLookup: (String) -> String?,
    onSuccess: (Int) -> Unit,
    onFailure: (String) -> Unit,
) {
    val profile = profileStore.ntripCasterProfiles().firstOrNull { it.id == casterProfileId }
    if (profile == null) {
        onFailure("Select an NTRIP caster profile first.")
        return
    }
    refreshNtripCasterMountpoints(
        context = context,
        targetId = profile.id,
        values = mapOf(
            "host" to profile.host,
            "port" to profile.port.toString(),
            "username" to profile.username,
            "password" to "",
        ),
        passwordLookup = passwordLookup,
        savePassword = { _, _ -> },
        profileStore = profileStore,
        onSuccess = onSuccess,
        onFailure = onFailure,
    )
}
```

If the shared helper requires the stored `secretId`, pass it explicitly or load
it from `profileStore`; do not require the user to re-enter the password from
the mountpoint editor.

- [ ] **Step 6: Ensure refreshed list is visible and does not auto-select**

Verify that after `refreshProfileUi()`, `ProfileStores.profileEditorData(...)` reads the updated `profile.sourcetableMountpoints`. The `NTRIP_MOUNTPOINT` editor should offer those mountpoints as selectable `optionItems`, but the refresh must not overwrite any existing mountpoint profile selection.

If the selected mountpoint profile currently references this caster and its
mountpoint is absent from the refreshed list, the mountpoint editor must show
the red exclamation warning when opened. The refresh action itself must not
rewrite the mountpoint profile.

If the mountpoint editor is open while refresh completes, the refreshed options
should be visible without forcing the user to leave the editor. If this cannot
be done without a broad screen-state refactor, the action must at least
`refreshProfileUi()` and keep the user in the mountpoint editor, with the
current mountpoint text preserved.

- [ ] **Step 7: Verify compile and commit**

Run:

```bash
git diff --check
sh gradlew :app:compileDebugKotlin
```

If unit tests are runnable on the current host, run:

```bash
sh gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.ui.profiles.ProfileListModelsTest
```

Commit:

```bash
git add app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt \
  app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileEditorModels.kt \
  app/src/test/kotlin/org/rtkcollector/app/ui/profiles/ProfileListModelsTest.kt
git commit -m "Refresh NTRIP caster mountpoints from profile editors"
```

## Task 6: End-To-End Review And Manual Test Checklist

**Files:**
- Modify if needed: `AGENTS.md`
- No production files unless a review gap is found.

- [ ] **Step 1: Run source-level verification**

Run:

```bash
git diff --check
sh gradlew :app:compileDebugKotlin
```

Expected: both pass in the local Termux environment.

- [ ] **Step 2: Run unit tests where environment supports Android resources**

On Windows Android Studio, CI, or another host with runnable Android SDK build tools, run:

```bash
sh gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.ui.DashboardMountpointLabelTest
sh gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.profile.ActiveRecordingConfigTest
sh gradlew :core:correction:test
```

Expected: tests pass.

- [ ] **Step 3: Manual UI test for caster refresh**

On a device/emulator with network:

1. Open Menu.
2. Open NTRIP caster profiles.
3. Edit a caster profile.
4. Set host, port, username and password.
5. Tap `Refresh mountpoints from caster`.
6. Confirm the known mountpoints list updates.
7. Back out and edit an NTRIP mountpoint profile.
8. Tap `Refresh mountpoints from caster` inside the mountpoint editor.
9. Confirm the selected caster's cached mountpoint list updates persistently.
10. Confirm mountpoint can be selected from the refreshed list.
11. Confirm an existing selected mountpoint is not silently overwritten by the first refreshed entry.
12. Change the caster on a mountpoint profile to a caster whose cached list does
    not contain the current mountpoint.
13. Confirm the Mountpoint field becomes red and shows an exclamation warning.
14. Confirm the mountpoint text is still the original typed value.
15. Try to Save and confirm the profile is not saved while the red warning is present.
16. Confirm the NTRIP mountpoint profile list shows an orange warning triangle
    and `Suspect invalid mountpoint`.
17. Confirm the main-screen mountpoint selector shows a compact orange warning
    triangle for the suspect profile.
18. Switch back to a caster whose cached list contains the mountpoint and confirm
    the warning disappears without retyping the mountpoint.
19. Select a valid mountpoint for the incompatible caster and confirm the red
    warning disappears.
20. Edit the currently selected mountpoint profile and change its caster to a
    different valid caster.
21. Return to the main dashboard and confirm the NTRIP server label changes to
    the new caster immediately, without re-selecting the mountpoint.
22. Start recording or update NTRIP while recording and confirm the new caster
    host/port are used, not the stale settings-set caster.

- [ ] **Step 4: Manual temporary-base NTRIP start test**

With UM980 connected:

1. Select workflow `Temporary base`.
2. Select an NTRIP caster and mountpoint profile.
3. Start recording.
4. Confirm NTRIP status becomes connecting/streaming.
5. Confirm `correction-input.raw` grows when corrections flow.
6. Confirm `tx-to-receiver.raw` grows when corrections are fed to receiver.
7. Confirm raw capture continues if NTRIP fails or reconnects.

- [ ] **Step 5: Final review**

Check:

```bash
git status --short
git log --oneline -5
```

Review the diff against the requirements:

- NTRIP caster editor has a refresh button.
- NTRIP mountpoint editor has a refresh button for the selected caster.
- Refreshed mountpoint list is persisted on the caster profile.
- NTRIP mountpoint profile selection remains explicit and is not auto-overwritten.
- Selected mountpoint profile's `casterProfileId` is the source of truth for
  the active dashboard/start/update caster; stale settings-set caster references
  are synchronized and never silently used when a mountpoint profile is selected.
- If a mountpoint profile points at a caster whose cached sourcetable does not
  contain the selected mountpoint, the field is marked red with an exclamation
  warning.
- Invalid mountpoint/caster combinations cannot be saved, and changing casters
  never deletes or overwrites the typed mountpoint value.
- Suspect saved mountpoint profiles are marked with an orange warning triangle
  in profile lists and main-screen mountpoint selectors.
- Temporary base enables NTRIP when selected profiles are set.
- Plain rover remains NTRIP-off.
- Fixed base remains NTRIP-off for now.
- Passwords remain stored through `NtripSecretStore`, not profile JSON.

- [ ] **Step 6: Final commit or push**

If all previous commits are already made, only push:

```bash
git push origin main
```

If final doc or AGENTS tweaks were needed:

```bash
git add AGENTS.md docs/workflows.md docs/user-workflows.md
git commit -m "Document NTRIP refresh workflow guardrails"
git push origin main
```

## Self-Review

- Spec coverage: The plan covers caster mountpoint refresh from caster and mountpoint editors, Temporary base NTRIP enablement, invalid mountpoint blocking in the editor, preservation of typed mountpoints during caster changes, and orange suspect-profile warnings when saved mountpoint profiles no longer match the selected caster sourcetable.
- Safety: Plain rover remains NTRIP-off, fixed base remains unchanged, and password persistence stays in `NtripSecretStore`.
- Tests: Plan adds focused workflow detection coverage and active-config coverage; UI refresh is compile-verified and manually tested unless a clean test seam already exists.
- No placeholders: All steps include exact files, commands and expected behaviour. The only optional test seam is explicitly conditional to avoid creating artificial production APIs just for the test.
