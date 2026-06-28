# Base Workflow Upload And Profile Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make fixed-base coordinate use explicit and reliable, add a main-screen NTRIP source-upload Off/Profile selector, clean up built-in settings, keep temporary-base averaging alive across NTRIP mountpoint changes, and update user-facing workflow documentation.

**Architecture:** Keep raw recording owned by `RecordingForegroundService` and keep upload/profile/UI failures advisory. Move fixed-base command materialisation into a small model/service that can be tested without Compose, then wire the dashboard setup controls and profile stores through existing settings-set patterns. Preserve user-created profiles while updating built-ins and documentation.

**Tech Stack:** Kotlin, Android Compose, app profile stores, foreground service state broadcasts, JUnit tests, Markdown docs, `git diff --check`, targeted Gradle tests, `sh gradlew :app:compileDebugKotlin`.

---

## Agent Allocation

- Use lighter codex-sparks-style agents for self-contained model/tests/docs tasks: height model tests, upload selector model tests, profile default tests, documentation edits.
- Use stronger reasoning for the fixed-base start path, migration/profile safety, and final review because those affect hardware commands and recording start semantics.
- Do not run Gradle compiles in parallel in this Android shared-storage workspace. Kotlin incremental caches can collide.

## File Structure

Modify these files:

```text
app/src/main/kotlin/org/rtkcollector/app/base/AcceptedBaseCoordinate.kt
app/src/main/kotlin/org/rtkcollector/app/base/BaseCoordinateForm.kt
app/src/main/kotlin/org/rtkcollector/app/base/BasePositionJsonCodec.kt
app/src/main/kotlin/org/rtkcollector/app/profile/ProfileStores.kt
app/src/main/kotlin/org/rtkcollector/app/profile/SettingsSetModels.kt
app/src/main/kotlin/org/rtkcollector/app/profile/ProfileStoreMigrations.kt
app/src/main/kotlin/org/rtkcollector/app/profile/ActiveRecordingConfig.kt
app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt
app/src/main/kotlin/org/rtkcollector/app/ui/common/HelpOverlay.kt
app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardModels.kt
app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardServiceMapper.kt
app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/HomeDashboard.kt
app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileScreens.kt
app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileListModels.kt
app/src/main/kotlin/org/rtkcollector/app/recording/CoordinateAveragingController.kt
app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt
docs/user-workflows.md
docs/workflows.md
docs/specification/workflows.md
docs/specification/ui-requirements.md
docs/specification/verification-matrix.md
docs/superpowers/plan-status.md
```

Create these focused files if they do not already exist:

```text
app/src/main/kotlin/org/rtkcollector/app/base/FixedBaseProfileMaterializer.kt
app/src/test/kotlin/org/rtkcollector/app/base/FixedBaseProfileMaterializerTest.kt
app/src/test/kotlin/org/rtkcollector/app/ui/dashboard/DashboardUploadSetupTest.kt
app/src/test/kotlin/org/rtkcollector/app/profile/BuiltInSettingsSetTest.kt
```

Update these existing tests:

```text
app/src/test/kotlin/org/rtkcollector/app/base/AcceptedBaseCoordinateTest.kt
app/src/test/kotlin/org/rtkcollector/app/base/BasePositionJsonCodecTest.kt
app/src/test/kotlin/org/rtkcollector/app/profile/ActiveRecordingConfigCasterUploadTest.kt
app/src/test/kotlin/org/rtkcollector/app/profile/ProfileStoresTest.kt
app/src/test/kotlin/org/rtkcollector/app/recording/CoordinateAveragingControllerTest.kt
app/src/test/kotlin/org/rtkcollector/app/ui/dashboard/DashboardLayoutModelsTest.kt
app/src/test/kotlin/org/rtkcollector/app/ui/dashboard/DashboardStateTest.kt
app/src/test/kotlin/org/rtkcollector/app/ui/profiles/ProfileListModelsTest.kt
```

---

## Task 1: Height Semantics For Accepted Base Coordinates

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/base/AcceptedBaseCoordinate.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/base/BaseCoordinateForm.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/base/BasePositionJsonCodec.kt`
- Modify tests: `app/src/test/kotlin/org/rtkcollector/app/base/AcceptedBaseCoordinateTest.kt`
- Modify tests: `app/src/test/kotlin/org/rtkcollector/app/base/BasePositionJsonCodecTest.kt`

- [ ] **Step 1: Add failing tests for dual height fields**

Add tests that construct an accepted coordinate with both `mslAltitudeM` and
`ellipsoidalHeightM`, assert UM980 command generation uses `mslAltitudeM`, and
assert JSON round-trips both values:

```kotlin
@Test
fun `um980 fixed base command uses msl altitude`() {
    val coordinate = coordinate(
        latDeg = 49.463759313,
        lonDeg = 15.451254479,
        mslAltitudeM = 707.8,
        ellipsoidalHeightM = 752.9215,
        geoidSeparationM = 45.1215,
    )

    assertEquals(
        "MODE BASE 49.4637593130 15.4512544790 707.8000",
        coordinate.toUm980FixedBaseModeCommand(),
    )
}

@Test
fun `json round trip preserves msl altitude ellipsoidal height and geoid separation`() {
    val original = coordinate(
        mslAltitudeM = 707.8,
        ellipsoidalHeightM = 752.9215,
        geoidSeparationM = 45.1215,
    )

    val decoded = BasePositionJsonCodec.decode(BasePositionJsonCodec.encode(original))

    assertEquals(707.8, decoded.mslAltitudeM)
    assertEquals(752.9215, decoded.ellipsoidalHeightM)
    assertEquals(45.1215, decoded.geoidSeparationM)
}
```

- [ ] **Step 2: Run failing tests**

Run:

```sh
sh gradlew :app:testDebugUnitTest --tests 'org.rtkcollector.app.base.*'
```

Expected: failures for missing `mslAltitudeM`, `geoidSeparationM`, or
`toUm980FixedBaseModeCommand`.

- [ ] **Step 3: Extend `AcceptedBaseCoordinate`**

Change the model to include:

```kotlin
val mslAltitudeM: Double?,
val ellipsoidalHeightM: Double?,
val geoidSeparationM: Double?,
```

Keep legacy constructor call sites compiling by updating every creation site in
`MainActivity.kt`, `DashboardModels.kt`, `BaseCoordinateForm.kt`,
`BasePositionJsonCodec.kt`, and tests. Validation requires at least one of
`mslAltitudeM` or `ellipsoidalHeightM` to be finite. Optional height fields must
be finite when present.

- [ ] **Step 4: Add explicit command methods**

Replace ambiguous command generation with:

```kotlin
fun toUm980FixedBaseModeCommand(): String {
    val altitude = mslAltitudeM
        ?: throw IllegalArgumentException("UM980 fixed base requires MSL altitude.")
    validate()
    return "MODE BASE %.10f %.10f %.4f".format(Locale.US, latDeg, lonDeg, altitude)
}
```

Keep a deprecated internal compatibility wrapper only if existing tests require
it:

```kotlin
fun toFixedBaseModeCommand(comPort: String = "COM1"): String =
    toUm980FixedBaseModeCommand()
```

- [ ] **Step 5: Update JSON codec with backward compatibility**

Encode new files with:

```json
{
  "mslAltitudeM": 707.8,
  "ellipsoidalHeightM": 752.9215,
  "geoidSeparationM": 45.1215
}
```

Decode legacy `heightM` as `ellipsoidalHeightM` and leave `mslAltitudeM` null
unless the legacy JSON also has `altitudeM`, `mslAltitudeM`, or
`geoidSeparationM` sufficient to derive it. Do not silently use legacy
ellipsoidal height for UM980 `MODE BASE`.

- [ ] **Step 6: Update base form validation**

`BaseCoordinateForm.validateForSave()` must label height fields clearly:

```text
MSL altitude meters
Ellipsoidal height meters
Geoid separation meters
```

Require at least one height. UM980 fixed-base generation requires MSL altitude.

- [ ] **Step 7: Re-run base tests**

Run:

```sh
sh gradlew :app:testDebugUnitTest --tests 'org.rtkcollector.app.base.*'
```

Expected: PASS.

- [ ] **Step 8: Commit checkpoint**

```sh
git add app/src/main/kotlin/org/rtkcollector/app/base app/src/test/kotlin/org/rtkcollector/app/base
git commit -m "Model fixed-base height semantics"
```

---

## Task 2: Fixed-Base Profile Materialisation Without Silent Replacement

**Files:**
- Create: `app/src/main/kotlin/org/rtkcollector/app/base/FixedBaseProfileMaterializer.kt`
- Create tests: `app/src/test/kotlin/org/rtkcollector/app/base/FixedBaseProfileMaterializerTest.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`
- Modify tests: `app/src/test/kotlin/org/rtkcollector/app/profile/ActiveRecordingConfigTest.kt`

- [ ] **Step 1: Write failing materializer tests**

Create tests for:

```kotlin
@Test
fun `replaces existing mode base line in editable profile preview`() {
    val result = FixedBaseProfileMaterializer.previewOverwrite(
        profileName = "Field base",
        runtimeScript = "UNLOG COM1\nMODE BASE TIME 120 2.5\nGNGGA 1",
        modeBaseCommand = "MODE BASE 49.4637593130 15.4512544790 707.8000",
    )

    assertEquals("MODE BASE TIME 120 2.5", result.beforeLine)
    assertEquals("MODE BASE 49.4637593130 15.4512544790 707.8000", result.afterLine)
    assertEquals(
        "UNLOG COM1\nMODE BASE 49.4637593130 15.4512544790 707.8000\nGNGGA 1",
        result.updatedRuntimeScript,
    )
}

@Test
fun `inserts mode base after unlog when no mode base line exists`() {
    val result = FixedBaseProfileMaterializer.previewOverwrite(
        profileName = "Field base",
        runtimeScript = "UNLOG COM1\nGNGGA 1",
        modeBaseCommand = "MODE BASE 49.4637593130 15.4512544790 707.8000",
    )

    assertEquals(
        "UNLOG COM1\nMODE BASE 49.4637593130 15.4512544790 707.8000\nGNGGA 1",
        result.updatedRuntimeScript,
    )
}
```

- [ ] **Step 2: Run failing tests**

Run:

```sh
sh gradlew :app:testDebugUnitTest --tests 'org.rtkcollector.app.base.FixedBaseProfileMaterializerTest'
```

Expected: FAIL because the materializer does not exist.

- [ ] **Step 3: Implement materializer**

Implement:

```kotlin
data class FixedBaseProfilePreview(
    val profileName: String,
    val beforeLine: String?,
    val afterLine: String,
    val updatedRuntimeScript: String,
)

object FixedBaseProfileMaterializer {
    fun previewOverwrite(
        profileName: String,
        runtimeScript: String,
        modeBaseCommand: String,
    ): FixedBaseProfilePreview
}
```

Rules:

- replace the first line whose trimmed text starts with `MODE BASE`;
- if no such line exists, insert after leading `UNLOG` and `CONFIG COM` lines;
- preserve other lines and line order;
- never modify init/shutdown scripts.

- [ ] **Step 4: Remove silent start-time replacement**

In `buildDashboardStartIntent(...)`, remove the call that wraps
`activeConfig.modeCommands.withFixedBaseModeCommand(fixedBaseModeCommand)`.
Fixed-base start should send `activeConfig.modeCommands` exactly as selected
unless a profile was explicitly materialised before start.

Change the fixed-base preflight error from “fixed base coordinate needs
ellipsoidal height” to a message that points to explicit profile preparation:

```text
Fixed base requires a command profile with an explicit MODE BASE coordinate.
Use Base to create or update a fixed-base profile first.
```

- [ ] **Step 5: Add start-path regression test**

Add or update a test proving fixed-base start does not replace the selected
profile line. If the current start intent builder is not directly testable,
extract a small pure function:

```kotlin
internal fun fixedBaseModeCommandsForStart(activeModeCommands: List<String>): List<String> =
    activeModeCommands
```

Test that input `MODE BASE 1 2 3` returns `MODE BASE 1 2 3`.

- [ ] **Step 6: Run fixed-base tests**

Run:

```sh
sh gradlew :app:testDebugUnitTest --tests 'org.rtkcollector.app.base.FixedBaseProfileMaterializerTest' --tests 'org.rtkcollector.app.profile.ActiveRecordingConfigTest'
```

Expected: PASS.

- [ ] **Step 7: Commit checkpoint**

```sh
git add app/src/main/kotlin/org/rtkcollector/app/base app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt app/src/test/kotlin/org/rtkcollector/app/base app/src/test/kotlin/org/rtkcollector/app/profile
git commit -m "Make fixed-base command materialisation explicit"
```

---

## Task 3: Temporary-Base Averaging Across NTRIP Mountpoints

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/recording/CoordinateAveragingController.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt`
- Modify tests: `app/src/test/kotlin/org/rtkcollector/app/recording/CoordinateAveragingControllerTest.kt`
- Modify tests: `app/src/test/kotlin/org/rtkcollector/app/ui/dashboard/DashboardStateTest.kt`

- [ ] **Step 1: Add regression test**

Add:

```kotlin
@Test
fun `averaging continues across ntrip mountpoint changes`() {
    val controller = CoordinateAveragingController()
    controller.start(requiredFixClass = FixClass.RTK_FIXED)
    assertTrue(controller.onSelectedSolution(candidate(50.0, 14.0, 302.0, FixClass.RTK_FIXED)).accepted)

    controller.onNtripSourceChanged("EUREF/TUBO00CZE0")
    assertTrue(controller.active)
    assertEquals(1, controller.summary()?.sampleCount)

    assertTrue(controller.onSelectedSolution(candidate(50.2, 14.2, 304.0, FixClass.RTK_FIXED)).accepted)
    assertEquals(2, controller.summary()?.sampleCount)
}
```

- [ ] **Step 2: Run failing test**

Run:

```sh
sh gradlew :app:testDebugUnitTest --tests 'org.rtkcollector.app.recording.CoordinateAveragingControllerTest'
```

Expected: FAIL because `onNtripSourceChanged` does not exist or because current
service logic stops averaging elsewhere.

- [ ] **Step 3: Implement no-op source-change handler**

Add:

```kotlin
fun onNtripSourceChanged(sourceLabel: String) {
    if (!active) return
    lastSourceLabel = sourceLabel.takeIf { it.isNotBlank() }
}
```

with a private `lastSourceLabel` used only for diagnostics. Do not stop the
controller from this method.

- [ ] **Step 4: Check service stop conditions**

Search `RecordingForegroundService.kt` for averaging stop paths. Keep stop on:

- user stop;
- recording stop;
- invalid fix class;
- missing required height;
- session/workflow reset.

Remove any stop triggered only by NTRIP caster or mountpoint selection changes.

- [ ] **Step 5: Update help text**

In `HelpOverlay.kt`, change coordinate averaging help to say averaging can span
multiple NTRIP mountpoints while the physical base receiver stays stationary.

- [ ] **Step 6: Run tests**

Run:

```sh
sh gradlew :app:testDebugUnitTest --tests 'org.rtkcollector.app.recording.CoordinateAveragingControllerTest' --tests 'org.rtkcollector.app.ui.dashboard.DashboardStateTest'
```

Expected: PASS.

- [ ] **Step 7: Commit checkpoint**

```sh
git add app/src/main/kotlin/org/rtkcollector/app/recording app/src/main/kotlin/org/rtkcollector/app/ui/common app/src/test/kotlin/org/rtkcollector/app/recording app/src/test/kotlin/org/rtkcollector/app/ui/dashboard
git commit -m "Keep base averaging across NTRIP changes"
```

---

## Task 4: Main-Screen Upload Selector

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardModels.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/HomeDashboard.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`
- Create tests: `app/src/test/kotlin/org/rtkcollector/app/ui/dashboard/DashboardUploadSetupTest.kt`
- Modify tests: `app/src/test/kotlin/org/rtkcollector/app/ui/dashboard/DashboardLayoutModelsTest.kt`

- [ ] **Step 1: Add failing dashboard model tests**

Add tests that assert `Upload` appears after `Mountpoint` and can display
`Off`:

```kotlin
@Test
fun `setup items include upload selector`() {
    assertEquals(
        listOf("Workflow", "Mountpoint", "Upload", "Receiver", "Storage"),
        defaultDashboardSetupItems.map { it.label },
    )
}

@Test
fun `dashboard status returns upload off label`() {
    val status = DashboardStatus(upload = "Off")
    assertEquals("Off", status.valueFor(DashboardSetupItem.UPLOAD))
}
```

- [ ] **Step 2: Run failing tests**

Run:

```sh
sh gradlew :app:testDebugUnitTest --tests 'org.rtkcollector.app.ui.dashboard.*'
```

Expected: FAIL because `UPLOAD` does not exist.

- [ ] **Step 3: Extend dashboard status and setup item**

Add:

```kotlin
data class DashboardStatus(
    val settingsSet: String = "n/a",
    val workflow: String = "n/a",
    val mountpoint: String = "n/a",
    val upload: String = "Off",
    val receiver: String = "n/a",
    val storage: String = "n/a",
)

internal enum class DashboardSetupItem(val label: String) {
    WORKFLOW("Workflow"),
    MOUNTPOINT("Mountpoint"),
    UPLOAD("Upload"),
    RECEIVER("Receiver"),
    STORAGE("Storage"),
}
```

Update `DashboardState.planned(...)`, `DashboardState.running(...)`,
`DashboardServiceMapper`, and preview states to supply upload labels.

- [ ] **Step 4: Wire click handling**

In `HomeDashboard.kt`, add `onUpload: () -> Unit` to setup control call sites
and route `DashboardSetupItem.UPLOAD -> onUpload`.

In `MainActivity.kt`, add a picker action using the same pattern as workflow,
receiver, mountpoint and storage selection. The picker choices are:

```text
Off
<each NTRIP caster upload profile name>
Edit upload profiles
```

Selecting `Off` clears `baseCasterUploadEnabled` and preserves
`ntripCasterUploadProfileRef` so switching Off and back On does not forget the
last upload profile. Selecting a profile sets `ntripCasterUploadProfileRef` and
`baseCasterUploadEnabled = true`.

- [ ] **Step 5: Keep monitoring card semantics**

Leave `CasterUploadCard` visibility tied to configured and enabled upload.
Planned state should show the upload card only when upload is selected/enabled.

- [ ] **Step 6: Run dashboard tests**

Run:

```sh
sh gradlew :app:testDebugUnitTest --tests 'org.rtkcollector.app.ui.dashboard.*'
```

Expected: PASS.

- [ ] **Step 7: Commit checkpoint**

```sh
git add app/src/main/kotlin/org/rtkcollector/app/ui app/src/test/kotlin/org/rtkcollector/app/ui/dashboard
git commit -m "Add dashboard upload selector"
```

---

## Task 5: Built-In Settings And Upload Defaults

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/profile/ProfileStores.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/profile/SettingsSetModels.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/profile/ProfileStoreMigrations.kt`
- Create tests: `app/src/test/kotlin/org/rtkcollector/app/profile/BuiltInSettingsSetTest.kt`
- Modify tests: `app/src/test/kotlin/org/rtkcollector/app/profile/ProfileStoresTest.kt`
- Modify tests: `app/src/test/kotlin/org/rtkcollector/app/profile/ActiveRecordingConfigCasterUploadTest.kt`

- [ ] **Step 1: Add failing tests for defaults**

Add:

```kotlin
@Test
fun `default um980 rover settings do not enable caster upload`() {
    val set = RecordingSettingsSet.builtInRoverNtrip()

    assertNull(set.ntripCasterUploadProfileRef)
    assertFalse(set.baseCasterUploadEnabled)
}

@Test
fun `built in settings contain explicit base upload scenario only`() {
    val sets = ProfileStores.defaultSettingsSetsForTests()

    assertTrue(sets.any { it.name.contains("fixed base", ignoreCase = true) && it.name.contains("upload", ignoreCase = true) })
    assertTrue(sets.filter { it.baseCasterUploadEnabled }.all { it.workflowId == "fixed-base" })
}
```

- [ ] **Step 2: Expose test defaults**

If needed, add `internal fun defaultSettingsSetsForTests()` in
`ProfileStores.Companion` mirroring the existing command-profile test helper.

- [ ] **Step 3: Implement focused built-ins**

Keep built-in settings small and named by scenario:

```text
UM980 rover + NTRIP
UM980 rover plain / PPP
UM980 temporary base
UM980 fixed base
UM980 fixed base + upload
u-blox M8T raw rover
u-blox M8T rover + RTKLIB
u-blox M8T raw + mock
```

Set `baseCasterUploadEnabled = false` for every rover and temporary-base
default. Set it true only for the explicit fixed-base upload settings set, and
only if an upload profile is selected by the user or provided by an import.

- [ ] **Step 4: Add migration repair**

In `ProfileStoreMigrations`, repair same-id built-in rover settings that have
`baseCasterUploadEnabled = true` by setting it to false and clearing
`ntripCasterUploadProfileRef` unless the set is user-created with a non-built-in
id. Do not delete user-created settings sets.

- [ ] **Step 5: Run profile tests**

Run:

```sh
sh gradlew :app:testDebugUnitTest --tests 'org.rtkcollector.app.profile.*'
```

Expected: PASS.

- [ ] **Step 6: Commit checkpoint**

```sh
git add app/src/main/kotlin/org/rtkcollector/app/profile app/src/test/kotlin/org/rtkcollector/app/profile
git commit -m "Clarify built-in recording settings"
```

---

## Task 6: Text Field Editor Consistency

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileScreens.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileListModels.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`
- Modify tests: `app/src/test/kotlin/org/rtkcollector/app/ui/profiles/ProfileListModelsTest.kt`

- [ ] **Step 1: Inventory remaining single-line `OutlinedTextField` use**

Run:

```sh
rg -n "OutlinedTextField\\(" app/src/main/kotlin/org/rtkcollector/app/ui app/src/main/kotlin/org/rtkcollector/app/base
```

Classify each hit as:

- profile/settings/base-coordinate editable field;
- console command input;
- read-only display;
- search/filter field.

- [ ] **Step 2: Extract reusable settings text field**

Create or reuse a composable in `ProfileScreens.kt`:

```kotlin
@Composable
internal fun SettingsSingleLineTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    secret: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
)
```

It should wrap the existing profile editor key handling so arrow keys stay
inside the field and Tab / Shift+Tab remain cross-field navigation.

- [ ] **Step 3: Replace settings/profile/base one-line fields**

Replace one-line editable settings/profile/base-coordinate `OutlinedTextField`
instances with `SettingsSingleLineTextField`. Do not replace console command
entry in `DeviceConsoleScreen.kt`.

- [ ] **Step 4: Add model-level regression for disabled username fields**

Add a test around `EditableProfileField.withRuntimeProfileValidation(...)` or
the reusable editor state proving source-upload v1 username disabled state does
not clear the field value:

```kotlin
@Test
fun `v1 source upload disables username without clearing it`() {
    val field = EditableProfileField("username", "Username", "base01")
    val updated = field.withRuntimeProfileValidation(mapOf("protocolPolicy" to "NTRIP_V1_ONLY"))

    assertEquals("base01", updated.value)
    assertTrue(updated.disabled)
}
```

- [ ] **Step 5: Run profile UI tests**

Run:

```sh
sh gradlew :app:testDebugUnitTest --tests 'org.rtkcollector.app.ui.profiles.*'
```

Expected: PASS.

- [ ] **Step 6: Commit checkpoint**

```sh
git add app/src/main/kotlin/org/rtkcollector/app/ui app/src/test/kotlin/org/rtkcollector/app/ui/profiles
git commit -m "Unify settings text field editing"
```

---

## Task 7: User Documentation And Formal Requirements

**Files:**
- Modify: `docs/user-workflows.md`
- Modify: `docs/workflows.md`
- Modify: `docs/specification/workflows.md`
- Modify: `docs/specification/ui-requirements.md`
- Modify: `docs/specification/verification-matrix.md`
- Modify: `docs/superpowers/plan-status.md`

- [ ] **Step 1: Update user workflows**

Add operator-facing sections covering:

```text
Plain rover
Rover with NTRIP
Temporary base coordinate determination
Fixed base with accepted coordinate
Temporary-base to fixed-base transition
NTRIP source upload from fixed base
Temporary-base averaging across multiple NTRIP mountpoints
```

Use this key wording:

```text
Temporary-base averaging can intentionally span multiple upstream NTRIP
mountpoints while the physical receiver remains stationary. This can reduce
dependence on a single upstream base, but it is still an operator judgement
workflow and is not equivalent to a surveyed control point.
```

- [ ] **Step 2: Update formal workflow requirements**

Add requirements that:

- fixed-base start must not silently replace `MODE BASE`;
- UM980 `MODE BASE` uses MSL altitude;
- upload selector must have an explicit Off state;
- temporary-base upload is blocked until fixed-base coordinate acceptance;
- averaging must not stop solely because NTRIP caster or mountpoint changes.

- [ ] **Step 3: Update verification matrix**

Add rows mapping the new requirements to the tests from Tasks 1 to 6 and manual
UM980 smoke test for `tx-to-receiver.raw`.

- [ ] **Step 4: Update plan status**

Reclassify temporary-base to fixed-base workflow from
`Implemented, not field-tested` to `In progress` while these fixes are active,
or to `Implemented, not field-tested` after code/tests land but before UM980
field confirmation.

- [ ] **Step 5: Run documentation checks**

Run:

```sh
git diff --check
rg -n "temporary base|fixed base|Upload|MODE BASE|MSL altitude|ellipsoidal" docs/user-workflows.md docs/specification docs/superpowers/plan-status.md
```

Expected: no whitespace errors; search output shows the updated sections.

- [ ] **Step 6: Commit checkpoint**

```sh
git add docs/user-workflows.md docs/workflows.md docs/specification docs/superpowers/plan-status.md
git commit -m "Document rover and base workflows"
```

---

## Task 8: Integration Verification, Review, Commit And Push

**Files:**
- Review all changed files.

- [ ] **Step 1: Inspect worktree**

Run:

```sh
git status --short
git log --oneline -5
```

Expected: only intended files are changed or all task commits are present.

- [ ] **Step 2: Run targeted tests**

Run sequentially:

```sh
sh gradlew :app:testDebugUnitTest --tests 'org.rtkcollector.app.base.*'
sh gradlew :app:testDebugUnitTest --tests 'org.rtkcollector.app.profile.*'
sh gradlew :app:testDebugUnitTest --tests 'org.rtkcollector.app.recording.CoordinateAveragingControllerTest'
sh gradlew :app:testDebugUnitTest --tests 'org.rtkcollector.app.ui.dashboard.*'
sh gradlew :app:testDebugUnitTest --tests 'org.rtkcollector.app.ui.profiles.*'
```

Expected: PASS. If Android unit-test execution is blocked by local Termux
environment rather than source failures, record the exact failure and run the
compile fallback.

- [ ] **Step 3: Run compile and lightweight checks**

Run:

```sh
git diff --check
sh gradlew :app:compileDebugKotlin
sh gradlew :app:unitTestClasses :app:androidTestClasses --dry-run
```

Expected: whitespace clean, debug Kotlin compile succeeds, Android Studio task
aliases are selectable. In Termux, do not retry full APK packaging if native
`aapt2` is the only blocker.

- [ ] **Step 4: Review against design**

Check the implementation against:

```text
docs/superpowers/specs/2026-06-28-base-workflow-upload-and-profile-cleanup-design.md
```

Confirm:

- no silent fixed-base command replacement remains;
- upload has an explicit Off selector;
- rover defaults do not enable upload;
- UM980 fixed-base command uses MSL altitude;
- averaging continues across mountpoint changes;
- docs describe rover/base workflows.

- [ ] **Step 5: Create final integration commit for remaining fixes**

If verification produced uncommitted fixes:

```sh
git add <changed-files>
git commit -m "Finish base workflow upload cleanup"
```

- [ ] **Step 6: Push**

Run:

```sh
git push
```

Expected: remote branch updated successfully.
