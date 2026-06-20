# Active Setup, RTKLIB, And Profile Compatibility Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make settings sets, profile activation, RTKLIB configuration, best-solution/mock policy and dashboard/profile UI behaviour explicit, compatible and field-safe.

**Architecture:** Add small Kotlin model/resolver units for active setup, per-option policy, profile compatibility, profile grouping and solution-source policy. Wire those models into settings-set persistence, profile list/selector UI, active recording config, dashboard state and session metadata without moving advisory processing onto the raw capture path.

**Tech Stack:** Kotlin/JVM model tests, Android Compose UI models, Android foreground-service start validation, existing profile stores, `core:solution`, `core:workflow`, `core:session`, app Kotlin compile checks.

---

## File Structure

- Create `app/src/main/kotlin/org/rtkcollector/app/profile/ActiveSetupModels.kt`
  - Per-option settings-set policy, active setup option keys, effective option state and active setup validation messages.
- Create `app/src/test/kotlin/org/rtkcollector/app/profile/ActiveSetupModelsTest.kt`
  - Policy defaults, missing required state and `ASK_EVERY_TIME` clearing rules.
- Modify `app/src/main/kotlin/org/rtkcollector/app/profile/SettingsSetModels.kt`
  - Persist option policies and solution/mock policy reference or override.
- Modify `app/src/test/kotlin/org/rtkcollector/app/profile/SettingsSetModelsTest.kt`
  - JSON round-trip for option policies and backward compatibility for older settings backups.
- Create `app/src/main/kotlin/org/rtkcollector/app/profile/ProfileCompatibility.kt`
  - Receiver-family compatibility, RTKLIB route compatibility and baud recommendation status.
- Create `app/src/test/kotlin/org/rtkcollector/app/profile/ProfileCompatibilityTest.kt`
  - UM980/u-blox mismatch, editable-but-not-activatable behaviour and baud guidance.
- Create `app/src/main/kotlin/org/rtkcollector/app/profile/ActiveSetupResolver.kt`
  - Resolve selected settings set, remembered overrides and transient `ASK_EVERY_TIME` choices into an effective setup for UI and Start.
- Create `app/src/test/kotlin/org/rtkcollector/app/profile/ActiveSetupResolverTest.kt`
  - Default/locked/choose-once/ask-every-time resolution and override reset/save behaviour.
- Modify `app/src/main/kotlin/org/rtkcollector/app/profile/ProfileModels.kt`
  - Add grouping/order fields to profile models and add `SolutionPolicyProfile`.
- Modify `app/src/main/kotlin/org/rtkcollector/app/profile/ProfileStores.kt`
  - Store solution policy profiles, migrate default profile names, preserve built-in groups and expose active setup override helpers.
- Modify `app/src/test/kotlin/org/rtkcollector/app/profile/ProfileDefaultsTest.kt`
  - Built-in naming, protection and grouping assertions.
- Create `app/src/test/kotlin/org/rtkcollector/app/profile/ProfileStoresActiveSetupTest.kt`
  - Active settings set, override persistence and imported mismatch handling.
- Modify `core/solution/src/main/kotlin/org/rtkcollector/core/solution/BestSolutionSelector.kt`
  - Add explicit source policy support.
- Modify `core/solution/src/main/kotlin/org/rtkcollector/core/solution/SolutionModels.kt`
  - Add `SolutionSourcePolicy` and selected-source diagnostics if not app-local.
- Modify `core/solution/src/test/kotlin/org/rtkcollector/core/solution/BestSolutionSelectorTest.kt`
  - Automatic vs manual device/RTKLIB/off selection.
- Modify `app/src/main/kotlin/org/rtkcollector/app/profile/ActiveRecordingConfig.kt`
  - Use resolved active setup, solution policy and RTKLIB profile compatibility.
- Modify `app/src/test/kotlin/org/rtkcollector/app/profile/ActiveRecordingConfigTest.kt`
  - Start rejects mismatched receiver/command/RTKLIB route and records solution policy.
- Modify `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt`
  - Pass solution/mock policy to best-solution/mock path and session metadata.
- Modify `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingServiceState.kt`
  - Expose selected solution/mock source policy and actual source.
- Modify `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardModels.kt`
  - Add active setup status and solution/mock source fields.
- Modify `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardServiceMapper.kt`
  - Map service extras for selected solution/mock source.
- Modify `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/HomeDashboard.kt`
  - Keep dashboard simple, show mock source clearly, add content-fit fallback model if not already sufficient.
- Modify `app/src/test/kotlin/org/rtkcollector/app/ui/dashboard/DashboardServiceMapperTest.kt`
  - Mock source display and RTKLIB/device solution separation.
- Modify `app/src/main/kotlin/org/rtkcollector/app/ui/settings/SettingsHub.kt`
  - Add top Active settings set section.
- Create `app/src/main/kotlin/org/rtkcollector/app/ui/settings/ActiveSetupPanel.kt`
  - Compact active setup summary and actions.
- Create `app/src/test/kotlin/org/rtkcollector/app/ui/settings/ActiveSetupPanelModelsTest.kt`
  - Active setup labels, modified state and missing/invalid state.
- Modify `app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileListModels.kt`
  - Add action model, compatibility labels and grouping/order rows.
- Modify `app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileScreens.kt`
  - Separate Activate, View/Edit, Rename, Copy, Delete and group movement UI.
- Modify `app/src/test/kotlin/org/rtkcollector/app/ui/profiles/ProfileListModelsTest.kt`
  - Action labels and grouping/order logic.
- Create `app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ConsistentProfileTextField.kt`
  - Shared thin-border text field with hardware-keyboard behaviour.
- Modify profile editor usage in `app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileScreens.kt`
  - Replace ad-hoc fields with shared text field.
- Modify `app/src/test/kotlin/org/rtkcollector/app/ui/profiles/ProfileEditorModelsTest.kt`
  - Field metadata and policy mini-selector model tests.
- Modify `core/session/src/main/kotlin/org/rtkcollector/core/session/SessionMetadata.kt`
  - Add selected RTKLIB profile, solution/mock policy and actual source metadata.
- Modify `core/session/src/test/kotlin/org/rtkcollector/core/session/SessionMetadataTest.kt`
  - JSON field round-trip with redacted/no-secret metadata.
- Modify documentation:
  - `docs/specification/ui-requirements.md`
  - `docs/specification/workflows.md`
  - `docs/specification/receiver-behaviour.md`
  - `docs/specification/session-artifacts.md`
  - `docs/specification/verification-matrix.md`
  - `docs/user-workflows.md`
  - `docs/contributor-onboarding.md`
  - `docs/superpowers/plan-status.md`

---

## Task 1: Add Active Setup Policy Models

**Files:**
- Create: `app/src/main/kotlin/org/rtkcollector/app/profile/ActiveSetupModels.kt`
- Create: `app/src/test/kotlin/org/rtkcollector/app/profile/ActiveSetupModelsTest.kt`

- [ ] **Step 1: Write failing tests**

Create `ActiveSetupModelsTest.kt`:

```kotlin
package org.rtkcollector.app.profile

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ActiveSetupModelsTest {
    @Test
    fun `default policies are overridable for all major options`() {
        val policies = SettingsSetOptionPolicies.defaults()

        ActiveSetupOptionKey.entries.forEach { key ->
            assertEquals(SettingsSetOptionPolicy.DEFAULT_OVERRIDABLE, policies.policyFor(key))
        }
    }

    @Test
    fun `ask every time option is missing until transient value is supplied`() {
        val state = EffectiveSetupOption(
            key = ActiveSetupOptionKey.NTRIP_MOUNTPOINT,
            label = "NTRIP mountpoint",
            defaultValueId = null,
            rememberedOverrideValueId = null,
            transientValueId = null,
            policy = SettingsSetOptionPolicy.ASK_EVERY_TIME,
            compatible = true,
        )

        assertFalse(state.canStart)
        assertTrue(state.requiresUserSelection)
        assertEquals("NTRIP mountpoint must be selected for this recording.", state.problem)
    }

    @Test
    fun `locked option ignores remembered override`() {
        val state = EffectiveSetupOption(
            key = ActiveSetupOptionKey.RECEIVER_COMMAND,
            label = "Receiver/init profile",
            defaultValueId = "um980-binary",
            rememberedOverrideValueId = "ublox-m8t",
            transientValueId = null,
            policy = SettingsSetOptionPolicy.LOCKED,
            compatible = true,
        )

        assertEquals("um980-binary", state.effectiveValueId)
        assertFalse(state.isOverridden)
    }
}
```

- [ ] **Step 2: Run failing tests**

Run:

```bash
ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK ANDROID_SDK_ROOT=/storage/3830-3863/Termux/AndroidSDK sh gradlew --no-daemon :app:testDebugUnitTest --tests org.rtkcollector.app.profile.ActiveSetupModelsTest
```

Expected before implementation: compile failure for missing model types. If local
Termux resource processing blocks app unit tests, run the nearest feasible
compile check after implementation and keep the test for CI/Android Studio.

- [ ] **Step 3: Add models**

Create `ActiveSetupModels.kt`:

```kotlin
package org.rtkcollector.app.profile

enum class ActiveSetupOptionKey {
    WORKFLOW,
    RECEIVER_COMMAND,
    USB_BAUD,
    NTRIP_CASTER,
    NTRIP_MOUNTPOINT,
    NTRIP_CASTER_UPLOAD,
    RTKLIB,
    SOLUTION_POLICY,
    RECORDING_OUTPUT,
    STORAGE,
    BASE_COORDINATE,
}

enum class SettingsSetOptionPolicy {
    DEFAULT_OVERRIDABLE,
    LOCKED,
    CHOOSE_ONCE_REMEMBER,
    ASK_EVERY_TIME,
}

data class SettingsSetOptionPolicies(
    val values: Map<ActiveSetupOptionKey, SettingsSetOptionPolicy> = defaultsMap(),
) {
    fun policyFor(key: ActiveSetupOptionKey): SettingsSetOptionPolicy =
        values[key] ?: SettingsSetOptionPolicy.DEFAULT_OVERRIDABLE

    fun withPolicy(
        key: ActiveSetupOptionKey,
        policy: SettingsSetOptionPolicy,
    ): SettingsSetOptionPolicies =
        copy(values = values + (key to policy))

    companion object {
        fun defaults(): SettingsSetOptionPolicies = SettingsSetOptionPolicies()

        fun defaultsMap(): Map<ActiveSetupOptionKey, SettingsSetOptionPolicy> =
            ActiveSetupOptionKey.entries.associateWith {
                SettingsSetOptionPolicy.DEFAULT_OVERRIDABLE
            }
    }
}

data class EffectiveSetupOption(
    val key: ActiveSetupOptionKey,
    val label: String,
    val defaultValueId: String?,
    val rememberedOverrideValueId: String?,
    val transientValueId: String?,
    val policy: SettingsSetOptionPolicy,
    val compatible: Boolean,
    val incompatibilityReason: String? = null,
) {
    val effectiveValueId: String?
        get() = when (policy) {
            SettingsSetOptionPolicy.LOCKED -> defaultValueId
            SettingsSetOptionPolicy.DEFAULT_OVERRIDABLE -> rememberedOverrideValueId ?: defaultValueId
            SettingsSetOptionPolicy.CHOOSE_ONCE_REMEMBER -> rememberedOverrideValueId ?: defaultValueId
            SettingsSetOptionPolicy.ASK_EVERY_TIME -> transientValueId
        }

    val isOverridden: Boolean
        get() = policy != SettingsSetOptionPolicy.LOCKED &&
            !rememberedOverrideValueId.isNullOrBlank() &&
            rememberedOverrideValueId != defaultValueId

    val requiresUserSelection: Boolean
        get() = effectiveValueId.isNullOrBlank() &&
            (policy == SettingsSetOptionPolicy.CHOOSE_ONCE_REMEMBER ||
                policy == SettingsSetOptionPolicy.ASK_EVERY_TIME)

    val canStart: Boolean
        get() = !requiresUserSelection && compatible

    val problem: String?
        get() = when {
            requiresUserSelection -> "$label must be selected for this recording."
            !compatible -> incompatibilityReason ?: "$label is incompatible with the active setup."
            else -> null
        }
}
```

- [ ] **Step 4: Run tests**

Run the same test command. Expected: `ActiveSetupModelsTest` passes where app
unit tests are supported.

- [ ] **Step 5: Checkpoint commit**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/profile/ActiveSetupModels.kt app/src/test/kotlin/org/rtkcollector/app/profile/ActiveSetupModelsTest.kt
git commit -m "Add active setup policy models"
```

---

## Task 2: Persist Settings-Set Policies And Solution Policy Reference

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/profile/SettingsSetModels.kt`
- Modify: `app/src/test/kotlin/org/rtkcollector/app/profile/SettingsSetModelsTest.kt`

- [ ] **Step 1: Write JSON round-trip tests**

Add to `SettingsSetModelsTest.kt`:

```kotlin
@Test
fun `settings set persists option policies and solution policy reference`() {
    val set = RecordingSettingsSet.builtInRoverNtrip().copy(
        optionPolicies = SettingsSetOptionPolicies.defaults()
            .withPolicy(ActiveSetupOptionKey.NTRIP_MOUNTPOINT, SettingsSetOptionPolicy.CHOOSE_ONCE_REMEMBER)
            .withPolicy(ActiveSetupOptionKey.RTKLIB, SettingsSetOptionPolicy.LOCKED),
        solutionPolicyProfileRef = ProfileReference("solution-auto", "Automatic best solution"),
    )

    val restored = RecordingSettingsSet.fromJson(set.toJson())

    assertEquals(SettingsSetOptionPolicy.CHOOSE_ONCE_REMEMBER, restored.optionPolicies.policyFor(ActiveSetupOptionKey.NTRIP_MOUNTPOINT))
    assertEquals(SettingsSetOptionPolicy.LOCKED, restored.optionPolicies.policyFor(ActiveSetupOptionKey.RTKLIB))
    assertEquals("solution-auto", restored.solutionPolicyProfileRef?.id)
}

@Test
fun `older settings set json defaults missing option policies`() {
    val json = RecordingSettingsSet.builtInRoverNtrip().toJson()
    json.remove("optionPolicies")
    json.remove("solutionPolicyProfile")

    val restored = RecordingSettingsSet.fromJson(json)

    assertEquals(SettingsSetOptionPolicy.DEFAULT_OVERRIDABLE, restored.optionPolicies.policyFor(ActiveSetupOptionKey.WORKFLOW))
    assertEquals(null, restored.solutionPolicyProfileRef)
}
```

- [ ] **Step 2: Run tests to verify failure**

Run:

```bash
ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK ANDROID_SDK_ROOT=/storage/3830-3863/Termux/AndroidSDK sh gradlew --no-daemon :app:testDebugUnitTest --tests org.rtkcollector.app.profile.SettingsSetModelsTest
```

Expected before implementation: compile failure for missing fields.

- [ ] **Step 3: Add fields to `RecordingSettingsSet`**

Add fields after `rtklibProfileRef`:

```kotlin
val solutionPolicyProfileRef: ProfileReference? = null,
val optionPolicies: SettingsSetOptionPolicies = SettingsSetOptionPolicies.defaults(),
```

In `validate()` add:

```kotlin
solutionPolicyProfileRef?.validate()
```

- [ ] **Step 4: Persist JSON**

In `SettingsSetJson.toJson`, add:

```kotlin
.putNullable("solutionPolicyProfile", settingsSet.solutionPolicyProfileRef?.toJson())
.put(
    "optionPolicies",
    JSONObject().also { json ->
        settingsSet.optionPolicies.values.forEach { (key, policy) ->
            json.put(key.name, policy.name)
        }
    },
)
```

In `SettingsSetJson.fromJson`, add:

```kotlin
solutionPolicyProfileRef = json.optJSONObject("solutionPolicyProfile")?.let(ProfileReference::fromJson),
optionPolicies = json.optJSONObject("optionPolicies")?.let { policiesJson ->
    SettingsSetOptionPolicies(
        values = ActiveSetupOptionKey.entries.associateWith { key ->
            runCatching {
                SettingsSetOptionPolicy.valueOf(
                    policiesJson.optString(key.name, SettingsSetOptionPolicy.DEFAULT_OVERRIDABLE.name),
                )
            }.getOrDefault(SettingsSetOptionPolicy.DEFAULT_OVERRIDABLE)
        },
    )
} ?: SettingsSetOptionPolicies.defaults(),
```

- [ ] **Step 5: Run tests**

Run the same test command. Expected: pass where app unit tests are supported.

- [ ] **Step 6: Checkpoint commit**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/profile/SettingsSetModels.kt app/src/test/kotlin/org/rtkcollector/app/profile/SettingsSetModelsTest.kt
git commit -m "Persist settings set option policies"
```

---

## Task 3: Add Solution Policy Profile And Best-Solution Source Selection

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/profile/ProfileModels.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/profile/ProfileStores.kt`
- Modify: `core/solution/src/main/kotlin/org/rtkcollector/core/solution/SolutionModels.kt`
- Modify: `core/solution/src/main/kotlin/org/rtkcollector/core/solution/BestSolutionSelector.kt`
- Modify: `core/solution/src/test/kotlin/org/rtkcollector/core/solution/BestSolutionSelectorTest.kt`

- [ ] **Step 1: Write source-policy tests**

Add to `BestSolutionSelectorTest.kt`:

```kotlin
@Test
fun `auto best chooses highest fresh fix class`() {
    val now = 10_000L
    val device = candidate("device", SolutionEngine.DEVICE_INTERNAL, FixClass.DGPS, now)
    val rtklib = candidate("rtklib", SolutionEngine.RTKLIB_REALTIME, FixClass.RTK_FLOAT, now)

    val selected = BestSolutionSelector.select(
        candidates = listOf(device, rtklib),
        nowMillis = now,
        policy = SolutionSourcePolicy.AUTO_BEST,
    )

    assertEquals("rtklib", selected?.sourceId)
}

@Test
fun `device only ignores better rtklib solution`() {
    val now = 10_000L
    val device = candidate("device", SolutionEngine.DEVICE_INTERNAL, FixClass.DGPS, now)
    val rtklib = candidate("rtklib", SolutionEngine.RTKLIB_REALTIME, FixClass.RTK_FIXED, now)

    val selected = BestSolutionSelector.select(
        candidates = listOf(device, rtklib),
        nowMillis = now,
        policy = SolutionSourcePolicy.DEVICE_INTERNAL_ONLY,
    )

    assertEquals("device", selected?.sourceId)
}

@Test
fun `off policy returns no solution`() {
    val now = 10_000L

    val selected = BestSolutionSelector.select(
        candidates = listOf(candidate("device", SolutionEngine.DEVICE_INTERNAL, FixClass.RTK_FIXED, now)),
        nowMillis = now,
        policy = SolutionSourcePolicy.OFF,
    )

    assertEquals(null, selected)
}

private fun candidate(
    source: String,
    engine: SolutionEngine,
    fix: FixClass,
    now: Long,
): SolutionCandidate =
    SolutionCandidate(
        sourceId = source,
        receiverFamily = "test",
        engine = engine,
        fixClass = fix,
        updatedAtMillis = now,
        latDeg = 50.0,
        lonDeg = 14.0,
    )
```

- [ ] **Step 2: Run tests to verify failure**

Run:

```bash
sh gradlew --no-daemon :core:solution:test --tests org.rtkcollector.core.solution.BestSolutionSelectorTest
```

Expected before implementation: compile failure for missing `SolutionSourcePolicy`.

- [ ] **Step 3: Add `SolutionSourcePolicy`**

In `SolutionModels.kt`, add:

```kotlin
enum class SolutionSourcePolicy {
    AUTO_BEST,
    DEVICE_INTERNAL_ONLY,
    RTKLIB_ONLY,
    OFF,
}
```

- [ ] **Step 4: Update selector**

Change `BestSolutionSelector.select` signature to:

```kotlin
fun select(
    candidates: Iterable<SolutionCandidate>,
    nowMillis: Long,
    maxAgeMillis: Long = DEFAULT_MAX_AGE_MILLIS,
    policy: SolutionSourcePolicy = SolutionSourcePolicy.AUTO_BEST,
): BestSolutionSnapshot?
```

Before ranking, filter by policy:

```kotlin
val policyFiltered = when (policy) {
    SolutionSourcePolicy.AUTO_BEST -> candidates
    SolutionSourcePolicy.DEVICE_INTERNAL_ONLY -> candidates.filter {
        it.engine == SolutionEngine.DEVICE_INTERNAL ||
            it.engine == SolutionEngine.RECEIVER_PPP ||
            it.engine == SolutionEngine.GENERIC_NMEA
    }
    SolutionSourcePolicy.RTKLIB_ONLY -> candidates.filter {
        it.engine == SolutionEngine.RTKLIB_REALTIME
    }
    SolutionSourcePolicy.OFF -> emptyList()
}
```

Then rank `policyFiltered`.

- [ ] **Step 5: Add `SolutionPolicyProfile`**

In `ProfileModels.kt`, add:

```kotlin
data class SolutionPolicyProfile(
    val id: String,
    val name: String,
    val mockSourcePolicy: String = SolutionSourcePolicy.AUTO_BEST.name,
    val dashboardBestPolicy: String = SolutionSourcePolicy.AUTO_BEST.name,
    val isProtected: Boolean = false,
) {
    fun validate() {
        require(id.isNotBlank()) { "Solution policy profile id must not be blank." }
        require(name.isNotBlank()) { "Solution policy profile name must not be blank." }
        require(mockSourcePolicy in SolutionSourcePolicy.entries.map { it.name }) {
            "Mock source policy is invalid."
        }
        require(dashboardBestPolicy in SolutionSourcePolicy.entries.map { it.name }) {
            "Dashboard best-solution policy is invalid."
        }
    }
}
```

Add JSON helpers matching existing profile model style.

- [ ] **Step 6: Add default solution policies to `ProfileStores`**

Add:

```kotlin
fun solutionPolicyProfiles(): List<SolutionPolicyProfile> = ...
fun saveSolutionPolicyProfiles(profiles: List<SolutionPolicyProfile>) = ...
```

Defaults:

```kotlin
SolutionPolicyProfile(
    id = "solution-auto-best",
    name = "Automatic best solution",
    isProtected = true,
)
SolutionPolicyProfile(
    id = "solution-device-only",
    name = "Device solution only",
    mockSourcePolicy = SolutionSourcePolicy.DEVICE_INTERNAL_ONLY.name,
    dashboardBestPolicy = SolutionSourcePolicy.DEVICE_INTERNAL_ONLY.name,
    isProtected = true,
)
SolutionPolicyProfile(
    id = "solution-rtklib-only",
    name = "RTKLIB solution only",
    mockSourcePolicy = SolutionSourcePolicy.RTKLIB_ONLY.name,
    dashboardBestPolicy = SolutionSourcePolicy.RTKLIB_ONLY.name,
    isProtected = true,
)
SolutionPolicyProfile(
    id = "solution-mock-off",
    name = "Mock provider off",
    mockSourcePolicy = SolutionSourcePolicy.OFF.name,
    dashboardBestPolicy = SolutionSourcePolicy.AUTO_BEST.name,
    isProtected = true,
)
```

- [ ] **Step 7: Run tests**

Run:

```bash
sh gradlew --no-daemon :core:solution:test
```

Expected: pass.

- [ ] **Step 8: Checkpoint commit**

```bash
git add core/solution app/src/main/kotlin/org/rtkcollector/app/profile app/src/test/kotlin/org/rtkcollector/app/profile
git commit -m "Add solution source policy profiles"
```

---

## Task 4: Add Profile Compatibility And Activation Filtering

**Files:**
- Create: `app/src/main/kotlin/org/rtkcollector/app/profile/ProfileCompatibility.kt`
- Create: `app/src/test/kotlin/org/rtkcollector/app/profile/ProfileCompatibilityTest.kt`

- [ ] **Step 1: Write compatibility tests**

Create `ProfileCompatibilityTest.kt`:

```kotlin
package org.rtkcollector.app.profile

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProfileCompatibilityTest {
    @Test
    fun `matching receiver family is activatable`() {
        val profile = CommandProfile(id = "um980", name = "UM980 profile", receiverFamily = "um980-n4")

        val result = ProfileCompatibility.commandProfile(profile, activeReceiverFamily = "um980-n4")

        assertTrue(result.activatable)
        assertEquals(ProfileCompatibilityLevel.COMPATIBLE, result.level)
    }

    @Test
    fun `mismatched receiver family remains editable but not activatable`() {
        val profile = CommandProfile(id = "ublox", name = "u-blox profile", receiverFamily = "ublox-m8t")

        val result = ProfileCompatibility.commandProfile(profile, activeReceiverFamily = "um980-n4")

        assertFalse(result.activatable)
        assertTrue(result.editable)
        assertEquals(ProfileCompatibilityLevel.INCOMPATIBLE, result.level)
        assertEquals("u-blox M8T profile cannot be activated while receiver family is UM980 / N4.", result.reason)
    }

    @Test
    fun `baud profiles are selectable with guidance`() {
        val result = ProfileCompatibility.baudProfile(
            profile = UsbBaudProfile(id = "baud", name = "921600", serialBaud = 921600),
            activeReceiverFamily = "um980-n4",
        )

        assertTrue(result.activatable)
        assertEquals(ProfileCompatibilityLevel.UNUSUAL, result.level)
    }
}
```

- [ ] **Step 2: Run tests to verify failure**

Run:

```bash
ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK ANDROID_SDK_ROOT=/storage/3830-3863/Termux/AndroidSDK sh gradlew --no-daemon :app:testDebugUnitTest --tests org.rtkcollector.app.profile.ProfileCompatibilityTest
```

Expected before implementation: compile failure for missing compatibility model.

- [ ] **Step 3: Implement compatibility model**

Create `ProfileCompatibility.kt`:

```kotlin
package org.rtkcollector.app.profile

enum class ProfileCompatibilityLevel {
    COMPATIBLE,
    RECOMMENDED,
    KNOWN_WORKING,
    UNTESTED,
    UNUSUAL,
    INCOMPATIBLE,
}

data class ProfileCompatibilityResult(
    val activatable: Boolean,
    val editable: Boolean = true,
    val level: ProfileCompatibilityLevel,
    val reason: String? = null,
)

object ProfileCompatibility {
    fun commandProfile(
        profile: CommandProfile,
        activeReceiverFamily: String,
    ): ProfileCompatibilityResult {
        if (profile.receiverFamily == activeReceiverFamily) {
            return ProfileCompatibilityResult(
                activatable = true,
                level = ProfileCompatibilityLevel.COMPATIBLE,
            )
        }
        return ProfileCompatibilityResult(
            activatable = false,
            level = ProfileCompatibilityLevel.INCOMPATIBLE,
            reason = "${profile.receiverFamily.displayReceiverFamily()} profile cannot be activated while receiver family is ${activeReceiverFamily.displayReceiverFamily()}.",
        )
    }

    fun baudProfile(
        profile: UsbBaudProfile,
        activeReceiverFamily: String,
    ): ProfileCompatibilityResult =
        ProfileCompatibilityResult(
            activatable = true,
            level = when {
                activeReceiverFamily == "um980-n4" && profile.serialBaud == 230400 -> ProfileCompatibilityLevel.RECOMMENDED
                activeReceiverFamily == "ublox-m8t" && profile.serialBaud == 230400 -> ProfileCompatibilityLevel.RECOMMENDED
                profile.serialBaud in setOf(115200, 230400, 460800) -> ProfileCompatibilityLevel.KNOWN_WORKING
                profile.serialBaud == 921600 -> ProfileCompatibilityLevel.UNUSUAL
                else -> ProfileCompatibilityLevel.UNTESTED
            },
        )
}

fun String.displayReceiverFamily(): String =
    when (this) {
        "um980-n4" -> "UM980 / N4"
        "ublox-m8t" -> "u-blox M8T"
        "ublox-m8p0" -> "u-blox M8P-0"
        "ublox-m8p2" -> "u-blox M8P-2"
        "generic-nmea-rtcm" -> "Generic NMEA/RTCM"
        else -> this
    }
```

- [ ] **Step 4: Run tests**

Run the same targeted test. Expected: pass where app unit tests are supported.

- [ ] **Step 5: Checkpoint commit**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/profile/ProfileCompatibility.kt app/src/test/kotlin/org/rtkcollector/app/profile/ProfileCompatibilityTest.kt
git commit -m "Add profile compatibility model"
```

---

## Task 5: Resolve Active Setup From Settings Set And Overrides

**Files:**
- Create: `app/src/main/kotlin/org/rtkcollector/app/profile/ActiveSetupResolver.kt`
- Create: `app/src/test/kotlin/org/rtkcollector/app/profile/ActiveSetupResolverTest.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/profile/ProfileStores.kt`

- [ ] **Step 1: Write resolver tests**

Create `ActiveSetupResolverTest.kt`:

```kotlin
package org.rtkcollector.app.profile

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ActiveSetupResolverTest {
    @Test
    fun `remembered override replaces overridable default`() {
        val set = RecordingSettingsSet.builtInRoverNtrip()
        val overrides = ActiveSetupOverrides(
            commandProfileId = "ublox-m8t-raw-1hz-safe",
        )

        val resolved = ActiveSetupResolver.resolve(
            settingsSet = set,
            overrides = overrides,
            transientChoices = ActiveSetupTransientChoices(),
        )

        assertEquals("ublox-m8t-raw-1hz-safe", resolved.commandProfileId)
        assertTrue(resolved.modified)
    }

    @Test
    fun `locked option ignores remembered override`() {
        val set = RecordingSettingsSet.builtInRoverNtrip().copy(
            optionPolicies = SettingsSetOptionPolicies.defaults()
                .withPolicy(ActiveSetupOptionKey.RECEIVER_COMMAND, SettingsSetOptionPolicy.LOCKED),
        )
        val overrides = ActiveSetupOverrides(commandProfileId = "ublox-m8t-raw-1hz-safe")

        val resolved = ActiveSetupResolver.resolve(set, overrides, ActiveSetupTransientChoices())

        assertEquals(set.commandProfileRef.id, resolved.commandProfileId)
        assertFalse(resolved.modified)
    }

    @Test
    fun `ask every time requires transient value`() {
        val set = RecordingSettingsSet.builtInRoverNtrip().copy(
            optionPolicies = SettingsSetOptionPolicies.defaults()
                .withPolicy(ActiveSetupOptionKey.NTRIP_MOUNTPOINT, SettingsSetOptionPolicy.ASK_EVERY_TIME),
            ntripMountpointProfileRef = null,
        )

        val resolved = ActiveSetupResolver.resolve(set, ActiveSetupOverrides(), ActiveSetupTransientChoices())

        assertTrue(resolved.validationMessages.any { it.code == "MISSING_NTRIP_MOUNTPOINT" })
    }
}
```

- [ ] **Step 2: Run tests to verify failure**

Run:

```bash
ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK ANDROID_SDK_ROOT=/storage/3830-3863/Termux/AndroidSDK sh gradlew --no-daemon :app:testDebugUnitTest --tests org.rtkcollector.app.profile.ActiveSetupResolverTest
```

Expected before implementation: compile failure.

- [ ] **Step 3: Implement resolver data classes**

Create `ActiveSetupResolver.kt`:

```kotlin
package org.rtkcollector.app.profile

data class ActiveSetupOverrides(
    val workflowId: String? = null,
    val commandProfileId: String? = null,
    val usbBaudProfileId: String? = null,
    val ntripCasterProfileId: String? = null,
    val ntripMountpointProfileId: String? = null,
    val ntripCasterUploadProfileId: String? = null,
    val rtklibProfileId: String? = null,
    val solutionPolicyProfileId: String? = null,
    val recordingOutputProfileId: String? = null,
    val storageProfileId: String? = null,
    val baseCoordinateProfileId: String? = null,
) {
    val hasChanges: Boolean
        get() = listOf(
            workflowId,
            commandProfileId,
            usbBaudProfileId,
            ntripCasterProfileId,
            ntripMountpointProfileId,
            ntripCasterUploadProfileId,
            rtklibProfileId,
            solutionPolicyProfileId,
            recordingOutputProfileId,
            storageProfileId,
            baseCoordinateProfileId,
        ).any { !it.isNullOrBlank() }
}

data class ActiveSetupTransientChoices(
    val workflowId: String? = null,
    val commandProfileId: String? = null,
    val usbBaudProfileId: String? = null,
    val ntripCasterProfileId: String? = null,
    val ntripMountpointProfileId: String? = null,
    val ntripCasterUploadProfileId: String? = null,
    val rtklibProfileId: String? = null,
    val solutionPolicyProfileId: String? = null,
    val recordingOutputProfileId: String? = null,
    val storageProfileId: String? = null,
    val baseCoordinateProfileId: String? = null,
)

data class ActiveSetupValidationMessage(
    val code: String,
    val message: String,
)

data class ResolvedActiveSetup(
    val settingsSetId: String,
    val settingsSetName: String,
    val workflowId: String,
    val receiverProfileId: String,
    val commandProfileId: String,
    val usbBaudProfileId: String,
    val ntripCasterProfileId: String?,
    val ntripMountpointProfileId: String?,
    val ntripCasterUploadProfileId: String?,
    val rtklibProfileId: String?,
    val solutionPolicyProfileId: String?,
    val recordingOutputProfileId: String,
    val storageProfileId: String,
    val baseCoordinateProfileId: String?,
    val modified: Boolean,
    val validationMessages: List<ActiveSetupValidationMessage>,
) {
    val canStart: Boolean get() = validationMessages.isEmpty()
}
```

- [ ] **Step 4: Implement resolver**

Add:

```kotlin
object ActiveSetupResolver {
    fun resolve(
        settingsSet: RecordingSettingsSet,
        overrides: ActiveSetupOverrides,
        transientChoices: ActiveSetupTransientChoices,
    ): ResolvedActiveSetup {
        val workflow = resolveValue(
            key = ActiveSetupOptionKey.WORKFLOW,
            policy = settingsSet.optionPolicies.policyFor(ActiveSetupOptionKey.WORKFLOW),
            defaultValue = settingsSet.workflowId,
            remembered = overrides.workflowId,
            transient = transientChoices.workflowId,
        )
        val command = resolveValue(
            key = ActiveSetupOptionKey.RECEIVER_COMMAND,
            policy = settingsSet.optionPolicies.policyFor(ActiveSetupOptionKey.RECEIVER_COMMAND),
            defaultValue = settingsSet.commandProfileRef.id,
            remembered = overrides.commandProfileId,
            transient = transientChoices.commandProfileId,
        )
        val mountpoint = resolveNullableValue(
            key = ActiveSetupOptionKey.NTRIP_MOUNTPOINT,
            policy = settingsSet.optionPolicies.policyFor(ActiveSetupOptionKey.NTRIP_MOUNTPOINT),
            defaultValue = settingsSet.ntripMountpointProfileRef?.id,
            remembered = overrides.ntripMountpointProfileId,
            transient = transientChoices.ntripMountpointProfileId,
        )
        val messages = mutableListOf<ActiveSetupValidationMessage>()
        if (settingsSet.optionPolicies.policyFor(ActiveSetupOptionKey.NTRIP_MOUNTPOINT) == SettingsSetOptionPolicy.ASK_EVERY_TIME && mountpoint.isNullOrBlank()) {
            messages += ActiveSetupValidationMessage(
                code = "MISSING_NTRIP_MOUNTPOINT",
                message = "NTRIP mountpoint must be selected for this recording.",
            )
        }
        return ResolvedActiveSetup(
            settingsSetId = settingsSet.id,
            settingsSetName = settingsSet.name,
            workflowId = workflow,
            receiverProfileId = settingsSet.receiverProfileId,
            commandProfileId = command,
            usbBaudProfileId = resolveValue(ActiveSetupOptionKey.USB_BAUD, settingsSet.optionPolicies.policyFor(ActiveSetupOptionKey.USB_BAUD), settingsSet.usbBaudProfileRef.id, overrides.usbBaudProfileId, transientChoices.usbBaudProfileId),
            ntripCasterProfileId = resolveNullableValue(ActiveSetupOptionKey.NTRIP_CASTER, settingsSet.optionPolicies.policyFor(ActiveSetupOptionKey.NTRIP_CASTER), settingsSet.ntripCasterProfileRef?.id, overrides.ntripCasterProfileId, transientChoices.ntripCasterProfileId),
            ntripMountpointProfileId = mountpoint,
            ntripCasterUploadProfileId = resolveNullableValue(ActiveSetupOptionKey.NTRIP_CASTER_UPLOAD, settingsSet.optionPolicies.policyFor(ActiveSetupOptionKey.NTRIP_CASTER_UPLOAD), settingsSet.ntripCasterUploadProfileRef?.id, overrides.ntripCasterUploadProfileId, transientChoices.ntripCasterUploadProfileId),
            rtklibProfileId = resolveNullableValue(ActiveSetupOptionKey.RTKLIB, settingsSet.optionPolicies.policyFor(ActiveSetupOptionKey.RTKLIB), settingsSet.rtklibProfileRef?.id, overrides.rtklibProfileId, transientChoices.rtklibProfileId),
            solutionPolicyProfileId = resolveNullableValue(ActiveSetupOptionKey.SOLUTION_POLICY, settingsSet.optionPolicies.policyFor(ActiveSetupOptionKey.SOLUTION_POLICY), settingsSet.solutionPolicyProfileRef?.id, overrides.solutionPolicyProfileId, transientChoices.solutionPolicyProfileId),
            recordingOutputProfileId = resolveValue(ActiveSetupOptionKey.RECORDING_OUTPUT, settingsSet.optionPolicies.policyFor(ActiveSetupOptionKey.RECORDING_OUTPUT), settingsSet.recordingOutputProfileRef.id, overrides.recordingOutputProfileId, transientChoices.recordingOutputProfileId),
            storageProfileId = resolveValue(ActiveSetupOptionKey.STORAGE, settingsSet.optionPolicies.policyFor(ActiveSetupOptionKey.STORAGE), settingsSet.storageProfileRef.id, overrides.storageProfileId, transientChoices.storageProfileId),
            baseCoordinateProfileId = resolveNullableValue(ActiveSetupOptionKey.BASE_COORDINATE, settingsSet.optionPolicies.policyFor(ActiveSetupOptionKey.BASE_COORDINATE), settingsSet.basePositionProfileRef?.id, overrides.baseCoordinateProfileId, transientChoices.baseCoordinateProfileId),
            modified = overrides.hasChanges,
            validationMessages = messages,
        )
    }

    private fun resolveValue(
        key: ActiveSetupOptionKey,
        policy: SettingsSetOptionPolicy,
        defaultValue: String,
        remembered: String?,
        transient: String?,
    ): String =
        resolveNullableValue(key, policy, defaultValue, remembered, transient) ?: defaultValue

    private fun resolveNullableValue(
        key: ActiveSetupOptionKey,
        policy: SettingsSetOptionPolicy,
        defaultValue: String?,
        remembered: String?,
        transient: String?,
    ): String? =
        when (policy) {
            SettingsSetOptionPolicy.LOCKED -> defaultValue
            SettingsSetOptionPolicy.DEFAULT_OVERRIDABLE -> remembered ?: defaultValue
            SettingsSetOptionPolicy.CHOOSE_ONCE_REMEMBER -> remembered ?: defaultValue
            SettingsSetOptionPolicy.ASK_EVERY_TIME -> transient
        }
}
```

Remove the unused `key` parameter if the compiler warns and no logging uses it.

- [ ] **Step 5: Add `ProfileStores` persistence helpers**

Add methods to load/save remembered active setup overrides under a key scoped by
settings set id:

```kotlin
fun activeSetupOverrides(settingsSetId: String): ActiveSetupOverrides = ...
fun saveActiveSetupOverrides(settingsSetId: String, overrides: ActiveSetupOverrides) = ...
fun clearActiveSetupOverrides(settingsSetId: String) = ...
```

Use JSON with the same field names as `ActiveSetupOverrides`.

- [ ] **Step 6: Run tests**

Run targeted tests for resolver and stores where supported.

- [ ] **Step 7: Checkpoint commit**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/profile/ActiveSetupResolver.kt app/src/main/kotlin/org/rtkcollector/app/profile/ProfileStores.kt app/src/test/kotlin/org/rtkcollector/app/profile/ActiveSetupResolverTest.kt app/src/test/kotlin/org/rtkcollector/app/profile/ProfileStoresActiveSetupTest.kt
git commit -m "Resolve active setup overrides"
```

---

## Task 6: Add RTKLIB Profile UI And Settings-Set Binding

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/settings/SettingsHub.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileListModels.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileScreens.kt`
- Modify: `app/src/test/kotlin/org/rtkcollector/app/ui/profiles/ProfileListModelsTest.kt`

- [ ] **Step 1: Add failing model test for RTKLIB list rows**

In `ProfileListModelsTest.kt`, add:

```kotlin
@Test
fun `rtklib profile rows distinguish active compatible and inactive compatible`() {
    val rows = profileRowsForRtklib(
        profiles = listOf(
            RtklibProfile(id = "rtklib-disabled", name = "RTKLIB disabled", enabled = false),
            RtklibProfile(id = "rtklib-rover", name = "RTKLIB rover", enabled = true),
        ),
        activeProfileId = "rtklib-disabled",
        compatibleProfileIds = setOf("rtklib-disabled", "rtklib-rover"),
    )

    assertEquals("Active", rows.first { it.id == "rtklib-disabled" }.statusLabel)
    assertTrue(rows.first { it.id == "rtklib-rover" }.actions.any { it.label == "Activate" })
}
```

- [ ] **Step 2: Run test to verify failure**

Run the app profile UI model tests where supported. Expected: compile failure
for missing RTKLIB helper or action model.

- [ ] **Step 3: Add RTKLIB profile kind**

In `MainActivity.kt`, add to `ProfileKind`:

```kotlin
RTKLIB,
SOLUTION_POLICY,
```

Update `backScreen()` to route them to new screens:

```kotlin
ProfileKind.RTKLIB -> AppScreen.RTKLIB
ProfileKind.SOLUTION_POLICY -> AppScreen.SOLUTION_POLICY
```

Add `AppScreen.RTKLIB` and `AppScreen.SOLUTION_POLICY`.

- [ ] **Step 4: Expose menu rows**

In `SettingsHub`, add a `Processing` section:

```kotlin
SettingsSection("Processing") {
    SettingsRow("RTK", "RTKLIB profiles", onRtklibProfiles)
    SettingsDivider()
    SettingsRow("SOL", "Solution and mock policy", onSolutionPolicy)
}
```

Add parameters `onRtklibProfiles` and `onSolutionPolicy`.

- [ ] **Step 5: Add settings-set editor fields**

In `profileEditorData` for `ProfileKind.SETTINGS_SET`, add:

```kotlin
EditableProfileField(
    key = "rtklibProfileId",
    label = "RTKLIB profile",
    value = set.rtklibProfileRef?.id.orEmpty(),
    optionItems = nullableProfileOptions(rtklibProfiles().profileOptions(RtklibProfile::id, RtklibProfile::name)),
),
EditableProfileField(
    key = "solutionPolicyProfileId",
    label = "Solution/mock policy",
    value = set.solutionPolicyProfileRef?.id.orEmpty(),
    optionItems = nullableProfileOptions(solutionPolicyProfiles().profileOptions(SolutionPolicyProfile::id, SolutionPolicyProfile::name)),
),
```

In save logic, add:

```kotlin
rtklibProfileRef = values.optional("rtklibProfileId")?.let {
    reference(it, rtklibProfiles().map { profile -> profile.id to profile.name })
},
solutionPolicyProfileRef = values.optional("solutionPolicyProfileId")?.let {
    reference(it, solutionPolicyProfiles().map { profile -> profile.id to profile.name })
},
```

- [ ] **Step 6: Add RTKLIB profile editor fields**

For `ProfileKind.RTKLIB`, expose:

```kotlin
EditableProfileField("name", "Name", profile.name)
EditableProfileField("enabled", "Enable RTKLIB processing", profile.enabled.toString(), boolean = true)
EditableProfileField("preset", "Preset", profile.preset, optionItems = RtklibProfile.PRESETS.sorted().map { EditableProfileOption(it, it) })
EditableProfileField("outputNmea", "Write RTKLIB NMEA", profile.outputNmea.toString(), boolean = true)
EditableProfileField("outputPos", "Write RTKLIB POS", profile.outputPos.toString(), boolean = true)
EditableProfileField("maxRoverQueueBytes", "Rover queue limit bytes", profile.maxRoverQueueBytes.toString())
EditableProfileField("maxCorrectionQueueBytes", "Correction queue limit bytes", profile.maxCorrectionQueueBytes.toString())
```

Use the same `saveProfileEditorData`, `renameProfileData`, `copyProfileData`
and `deleteProfileData` patterns as existing profile kinds.

- [ ] **Step 7: Add solution policy profile editor fields**

Expose:

```kotlin
EditableProfileField("name", "Name", profile.name)
EditableProfileField("mockSourcePolicy", "Mock provider source", profile.mockSourcePolicy, optionItems = solutionPolicyOptions())
EditableProfileField("dashboardBestPolicy", "Dashboard best source", profile.dashboardBestPolicy, optionItems = solutionPolicyOptions())
```

Where:

```kotlin
private fun solutionPolicyOptions(): List<EditableProfileOption> =
    SolutionSourcePolicy.entries.map { policy ->
        EditableProfileOption(policy.name, policy.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() })
    }
```

- [ ] **Step 8: Run compile**

Run:

```bash
ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK ANDROID_SDK_ROOT=/storage/3830-3863/Termux/AndroidSDK sh gradlew --no-daemon -Pandroid.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2 :app:compileDebugKotlin -x :app:processDebugResources
```

Expected: compile success.

- [ ] **Step 9: Checkpoint commit**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/ui/settings/SettingsHub.kt app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt app/src/main/kotlin/org/rtkcollector/app/ui/profiles app/src/test/kotlin/org/rtkcollector/app/ui/profiles
git commit -m "Expose RTKLIB and solution policy profiles"
```

---

## Task 7: Add Top-Level Active Setup Panel

**Files:**
- Create: `app/src/main/kotlin/org/rtkcollector/app/ui/settings/ActiveSetupPanel.kt`
- Create: `app/src/test/kotlin/org/rtkcollector/app/ui/settings/ActiveSetupPanelModelsTest.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/settings/SettingsHub.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`

- [ ] **Step 1: Write panel model tests**

Create `ActiveSetupPanelModelsTest.kt`:

```kotlin
package org.rtkcollector.app.ui.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ActiveSetupPanelModelsTest {
    @Test
    fun `clean setup shows clean label`() {
        val state = ActiveSetupPanelState(
            settingsSetName = "UM980 rover",
            modified = false,
            invalidMessages = emptyList(),
            summaryLines = listOf("Rover + NTRIP", "UM980 20 Hz BESTNAVB 4 Hz OBSVMCMPB RTK+PPP"),
        )

        assertEquals("Clean", state.statusLabel)
    }

    @Test
    fun `invalid setup takes precedence over modified label`() {
        val state = ActiveSetupPanelState(
            settingsSetName = "Broken",
            modified = true,
            invalidMessages = listOf("Receiver/init profile is incompatible."),
            summaryLines = emptyList(),
        )

        assertEquals("Invalid", state.statusLabel)
    }
}
```

- [ ] **Step 2: Implement state and panel**

Create:

```kotlin
package org.rtkcollector.app.ui.settings

import androidx.compose.runtime.Composable

data class ActiveSetupPanelState(
    val settingsSetName: String,
    val modified: Boolean,
    val invalidMessages: List<String>,
    val summaryLines: List<String>,
) {
    val statusLabel: String
        get() = when {
            invalidMessages.isNotEmpty() -> "Invalid"
            modified -> "Modified"
            else -> "Clean"
        }
}

@Composable
fun ActiveSetupPanel(
    state: ActiveSetupPanelState,
    onActivateSettingsSet: () -> Unit,
    onResetOverrides: () -> Unit,
    onSaveOverrides: () -> Unit,
    onSaveAsNewSet: () -> Unit,
    onEditSettingsSet: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, TidyColors.Divider),
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Active settings set",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = state.settingsSetName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                AssistChip(
                    onClick = onActivateSettingsSet,
                    label = { Text(state.statusLabel) },
                )
            }
            state.summaryLines.take(5).forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (state.invalidMessages.isNotEmpty()) {
                Text(
                    text = state.invalidMessages.joinToString("; "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TextButton(onClick = onResetOverrides) { Text("Reset") }
                TextButton(onClick = onSaveOverrides) { Text("Save") }
                TextButton(onClick = onSaveAsNewSet) { Text("Save as") }
                TextButton(onClick = onEditSettingsSet) { Text("Edit") }
            }
        }
    }
}
```

Imports needed for this body include `Arrangement`, `Column`, `Row`,
`fillMaxWidth`, `padding`, `BorderStroke`, `AssistChip`, `MaterialTheme`,
`Surface`, `Text`, `TextButton`, `Alignment`, `FontWeight`, `TextOverflow`,
`dp` and `TidyColors`.

- [ ] **Step 3: Wire panel into `SettingsHub`**

Add `activeSetupState: ActiveSetupPanelState` and callbacks to `SettingsHub`.
Place `ActiveSetupPanel` before the current `Session setup` section.

- [ ] **Step 4: Wire actions in `MainActivity`**

Callbacks:

- activate another set: open settings-set selector/list;
- reset overrides: call `ProfileStores.clearActiveSetupOverrides(selectedSettingsSetId)`;
- save overrides: merge overrides into the selected settings set and clear overrides;
- save as new set: create a copied settings set from resolved active setup;
- edit settings set: open `ProfileKind.SETTINGS_SET` editor for current set.

- [ ] **Step 5: Run compile**

Run app Kotlin compile command from Task 6.

- [ ] **Step 6: Checkpoint commit**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/ui/settings app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt app/src/test/kotlin/org/rtkcollector/app/ui/settings/ActiveSetupPanelModelsTest.kt
git commit -m "Add active setup panel"
```

---

## Task 8: Separate Activate, Edit, View, Rename, Copy And Delete

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileListModels.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileScreens.kt`
- Modify: `app/src/test/kotlin/org/rtkcollector/app/ui/profiles/ProfileListModelsTest.kt`

- [ ] **Step 1: Write action model tests**

Add tests:

```kotlin
@Test
fun `built in active compatible row has view copy and active label`() {
    val row = ProfileListRow(
        id = "builtin",
        title = "Built in",
        subtitle = "UM980",
        isSelected = true,
        isProtected = true,
        compatibility = ProfileListCompatibility.compatible(),
    )

    val actions = row.availableActions()

    assertEquals(listOf("View", "Copy"), actions.map { it.label })
    assertEquals("Active", row.statusLabel)
}

@Test
fun `inactive compatible user row has activate edit rename copy delete`() {
    val row = ProfileListRow(
        id = "user",
        title = "User",
        subtitle = "UM980",
        isSelected = false,
        isProtected = false,
        compatibility = ProfileListCompatibility.compatible(),
    )

    assertEquals(
        listOf("Activate", "Edit", "Rename", "Copy", "Delete"),
        row.availableActions().map { it.label },
    )
}

@Test
fun `incompatible row cannot activate but can be edited`() {
    val row = ProfileListRow(
        id = "ublox",
        title = "u-blox",
        subtitle = "u-blox M8T",
        isSelected = false,
        isProtected = false,
        compatibility = ProfileListCompatibility.incompatible("Current receiver is UM980 / N4."),
    )

    assertEquals(
        listOf("Edit", "Rename", "Copy", "Delete"),
        row.availableActions().map { it.label },
    )
}
```

- [ ] **Step 2: Implement action model**

Add:

```kotlin
data class ProfileListCompatibility(
    val activatable: Boolean,
    val label: String,
    val reason: String? = null,
) {
    companion object {
        fun compatible(): ProfileListCompatibility = ProfileListCompatibility(true, "Compatible")
        fun incompatible(reason: String): ProfileListCompatibility = ProfileListCompatibility(false, "Incompatible", reason)
    }
}

enum class ProfileRowActionKind { ACTIVATE, VIEW, EDIT, RENAME, COPY, DELETE }

data class ProfileRowAction(
    val kind: ProfileRowActionKind,
    val label: String,
)
```

Add `availableActions()` and `statusLabel` to `ProfileListRow`.

- [ ] **Step 3: Update UI**

In `ProfileScreens.kt`, render compact action buttons from `availableActions()`.
Rename dialog remains separate. Delete confirmation remains separate.

- [ ] **Step 4: Run tests and compile**

Run profile UI model tests where supported and app Kotlin compile.

- [ ] **Step 5: Checkpoint commit**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/ui/profiles app/src/test/kotlin/org/rtkcollector/app/ui/profiles/ProfileListModelsTest.kt
git commit -m "Separate profile activation and editing actions"
```

---

## Task 9: Add Profile Groups And Ordering

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/profile/ProfileModels.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/profile/ProfileStores.kt`
- Create: `app/src/main/kotlin/org/rtkcollector/app/profile/ProfileOrdering.kt`
- Create: `app/src/test/kotlin/org/rtkcollector/app/profile/ProfileOrderingTest.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileListModels.kt`

- [ ] **Step 1: Write ordering tests**

Create `ProfileOrderingTest.kt`:

```kotlin
package org.rtkcollector.app.profile

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ProfileOrderingTest {
    @Test
    fun `profiles sort by group order then profile order`() {
        val groups = listOf(
            ProfileGroup(id = "ublox", name = "u-blox M8T", order = 2),
            ProfileGroup(id = "um980", name = "UM980 / N4", order = 1),
        )
        val profiles = listOf(
            OrderedProfile("b", "ublox", 2),
            OrderedProfile("a", "um980", 1),
            OrderedProfile("c", "ublox", 1),
        )

        assertEquals(listOf("a", "c", "b"), ProfileOrdering.sort(profiles, groups).map { it.id })
    }

    private data class OrderedProfile(
        override val id: String,
        override val groupId: String,
        override val order: Int,
    ) : ProfileOrdering.Ordered
}
```

- [ ] **Step 2: Implement ordering model**

Create `ProfileOrdering.kt`:

```kotlin
package org.rtkcollector.app.profile

data class ProfileGroup(
    val id: String,
    val name: String,
    val order: Int,
    val isProtected: Boolean = false,
    val collapsed: Boolean = false,
)

object ProfileOrdering {
    interface Ordered {
        val id: String
        val groupId: String
        val order: Int
    }

    fun <T : Ordered> sort(
        profiles: List<T>,
        groups: List<ProfileGroup>,
    ): List<T> {
        val groupOrder = groups.associate { it.id to it.order }
        return profiles.sortedWith(
            compareBy<T> { groupOrder[it.groupId] ?: Int.MAX_VALUE }
                .thenBy { it.order }
                .thenBy { it.id },
        )
    }
}
```

- [ ] **Step 3: Add group/order fields**

Add to profile data classes that appear in profile lists:

```kotlin
val groupId: String = "default",
val order: Int = 0,
```

Update JSON round-trips with defaults for old backups.

- [ ] **Step 4: Add default groups**

In `ProfileStores`, add default groups per profile kind:

- command groups: `UM980 / N4`, `u-blox M8T`, `u-blox M8P`, `Generic NMEA/RTCM`, `User experiments`;
- RTKLIB groups: `Disabled`, `Rover`, `Temporary base`, `Advanced / experimental`;
- NTRIP groups: `EUREF / CORS`, `Private casters`, `Test`;
- storage groups: `App storage`, `Shared folders`.

Store groups in SharedPreferences using JSON arrays.

- [ ] **Step 5: Update profile list models**

Group rows by group order. Add group movement actions:

- move group up;
- move group down;
- move profile up within group;
- move profile down within group;
- move profile to another group.

Keep activation dialogs grouped but compact.

- [ ] **Step 6: Run tests and compile**

Run:

```bash
ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK ANDROID_SDK_ROOT=/storage/3830-3863/Termux/AndroidSDK sh gradlew --no-daemon -Pandroid.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2 :app:compileDebugKotlin -x :app:processDebugResources
```

- [ ] **Step 7: Checkpoint commit**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/profile app/src/test/kotlin/org/rtkcollector/app/profile app/src/main/kotlin/org/rtkcollector/app/ui/profiles
git commit -m "Add grouped profile ordering"
```

---

## Task 10: Rename Built-In Command Profiles And Add Output Metadata

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/profile/ProfileModels.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/profile/ProfileStores.kt`
- Modify: `app/src/test/kotlin/org/rtkcollector/app/profile/ProfileDefaultsTest.kt`

- [ ] **Step 1: Add tests for naming and protection**

Add:

```kotlin
@Test
fun `built in command profile names distinguish solution and raw rates`() {
    val profiles = ProfileStores.defaultCommandProfilesForTest()

    assertTrue(profiles.any { it.name == "UM980 20 Hz BESTNAVB 4 Hz OBSVMCMPB RTK+PPP" })
    assertTrue(profiles.any { it.name == "UM980 20 Hz BESTNAVB 4 Hz OBSVMB RTKLIB RTK+PPP" })
    assertTrue(profiles.any { it.name == "u-blox M8T 1 Hz NAV-PVT 1 Hz RAWX" })
    assertTrue(profiles.any { it.name == "u-blox M8T 5 Hz NAV-PVT 5 Hz RAWX RTKLIB" })
    assertFalse(profiles.any { it.name.contains("raw" + " status", ignoreCase = true) })
}

@Test
fun `u-blox built in profiles are protected and copyable`() {
    val profiles = ProfileStores.defaultCommandProfilesForTest()

    assertTrue(profiles.first { it.id == ProfileStores.UBLOX_M8T_RAW_1HZ_PROFILE_ID }.isProtected)
    assertTrue(profiles.first { it.id == ProfileStores.UBLOX_M8T_RAW_5HZ_RTKLIB_EX_PROFILE_ID }.isProtected)
}
```

Expose `defaultCommandProfilesForTest()` as an internal test-visible helper if
needed.

- [ ] **Step 2: Add command output metadata**

Add a lightweight metadata field to `CommandProfile`:

```kotlin
val solutionOutputSummary: String = "",
val rawOutputSummary: String = "",
val featureSummary: String = "",
```

Persist with JSON defaults.

- [ ] **Step 3: Rename defaults**

Rename built-ins:

- `UM980 multi-Hz binary RTK+PPP` -> `UM980 20 Hz BESTNAVB 4 Hz OBSVMCMPB RTK+PPP`;
- `UM980 multi-Hz binary RTKLIB OBSVMB` -> `UM980 20 Hz BESTNAVB 4 Hz OBSVMB RTKLIB RTK+PPP`;
- `UM980 multi-Hz ASCII RTK+PPP` -> `UM980 20 Hz NMEA no raw RTK+PPP`;
- `UM980 1 Hz ASCII RTK+PPP` -> `UM980 1 Hz NMEA no raw RTK+PPP`;
- `UM980 base config` -> `UM980 fixed base 1 Hz RTCM+OBSVMCMPB`;
- `u-blox M8T raw 1 Hz safe` -> `u-blox M8T 1 Hz NAV-PVT 1 Hz RAWX`;
- `u-blox M8T raw 5 Hz RTKLIB-EX` -> `u-blox M8T 5 Hz NAV-PVT 5 Hz RAWX RTKLIB`;
- `u-blox M8T raw + status/mock` -> `u-blox M8T 1 Hz NAV-PVT 1 Hz RAWX mock-ready`.

- [ ] **Step 4: Add migration**

In profile read migration, if a protected built-in id matches a current
built-in id, replace name, protection, receiver family, script and metadata
with current built-in values while preserving user-created copies.

- [ ] **Step 5: Run tests**

Run profile defaults tests where supported.

- [ ] **Step 6: Checkpoint commit**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/profile app/src/test/kotlin/org/rtkcollector/app/profile/ProfileDefaultsTest.kt
git commit -m "Normalize built-in command profile names"
```

---

## Task 11: Add Settings-Set Policy Mini-Selectors

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileListModels.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileScreens.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`
- Modify: `app/src/test/kotlin/org/rtkcollector/app/ui/profiles/ProfileEditorModelsTest.kt`

- [ ] **Step 1: Add field model tests**

Add:

```kotlin
@Test
fun `settings set major option field carries compact policy options`() {
    val field = EditableProfileField(
        key = "ntripMountpointProfileId",
        label = "NTRIP mountpoint profile",
        value = "",
        optionItems = listOf(EditableProfileOption("", "No mountpoint")),
        policy = SettingsSetOptionPolicy.CHOOSE_ONCE_REMEMBER,
    )

    assertEquals(SettingsSetOptionPolicy.CHOOSE_ONCE_REMEMBER, field.policy)
}
```

- [ ] **Step 2: Add policy support to editable field model**

Add:

```kotlin
val policy: SettingsSetOptionPolicy? = null,
val policyKey: ActiveSetupOptionKey? = null,
```

to `EditableProfileField`.

- [ ] **Step 3: Add compact policy options**

Use four compact labels:

- `D` for default/overridable;
- `L` for locked;
- `R` for choose once/remember;
- `A` for ask every time.

In field help text, show:

- `D: default can be changed`;
- `L: locked by settings set`;
- `R: choose once and remember`;
- `A: ask every recording`.

Use text labels rather than emoji to keep ASCII and accessibility simple.

- [ ] **Step 4: Wire settings-set editor**

For each major settings-set field, set `policyKey` and `policy`.

On save, write the updated policies to `RecordingSettingsSet.optionPolicies`.

- [ ] **Step 5: Run compile**

Run app Kotlin compile.

- [ ] **Step 6: Checkpoint commit**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/ui/profiles app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt app/src/test/kotlin/org/rtkcollector/app/ui/profiles/ProfileEditorModelsTest.kt
git commit -m "Add compact settings set policy selectors"
```

---

## Task 12: Apply Active Setup To Start And Quick Selectors

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/profile/ActiveRecordingConfig.kt`
- Modify: `app/src/test/kotlin/org/rtkcollector/app/profile/ActiveRecordingConfigTest.kt`

- [ ] **Step 1: Add start validation tests**

Add tests:

```kotlin
@Test
fun `start config rejects command profile from different receiver family`() {
    val settingsSet = RecordingSettingsSet.builtInRoverNtrip().copy(receiverProfileId = "um980-n4")
    val error = assertThrows<IllegalArgumentException> {
        ActiveRecordingConfig.resolve(
            settingsSet = settingsSet,
            commandProfile = CommandProfile(id = "ublox", name = "u-blox", receiverFamily = "ublox-m8t"),
            usbBaudProfile = UsbBaudProfile(id = "baud", name = "baud"),
            ntripCasterProfile = null,
            ntripMountpointProfile = null,
            ntripCasterUploadProfile = null,
            recordingPolicyProfile = RecordingPolicyProfile(id = "record", name = "record"),
            storageProfile = StorageProfile(id = "storage", name = "storage"),
            rtklibProfile = null,
            solutionPolicyProfile = null,
            workflowName = "Plain rover",
            workflowUsesNtrip = false,
            hasAcceptedBaseCoordinate = false,
            passwordLookup = { null },
        )
    }

    assertTrue(error.message!!.contains("receiver family"))
}
```

Adjust the call signature to match the current `ActiveRecordingConfig.resolve`
after adding `solutionPolicyProfile`.

- [ ] **Step 2: Update active config**

Add `solutionPolicyProfile: SolutionPolicyProfile?` to
`ActiveRecordingConfig.resolve`.

Validate:

- `settingsSet.receiverProfileId == commandProfile.receiverFamily`;
- RTKLIB enabled requires compatible route;
- locked/ask policies are already resolved before this stage;
- NTRIP required workflows still require valid mountpoint.

- [ ] **Step 3: Update quick selectors**

Main-screen selectors call a single helper:

```kotlin
fun applyQuickOverride(
    optionKey: ActiveSetupOptionKey,
    valueId: String,
)
```

Rules:

- `DEFAULT_OVERRIDABLE`: save remembered override.
- `LOCKED`: show toast "Locked by active settings set."
- `CHOOSE_ONCE_REMEMBER`: save remembered override.
- `ASK_EVERY_TIME`: save transient per-run choice only.

- [ ] **Step 4: Clear ask-every-time choices**

When recording stops or start fails, clear transient choices for the active
settings set. Keep remembered overrides.

- [ ] **Step 5: Run compile and tests**

Run targeted tests where supported and app Kotlin compile.

- [ ] **Step 6: Checkpoint commit**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt app/src/main/kotlin/org/rtkcollector/app/profile/ActiveRecordingConfig.kt app/src/test/kotlin/org/rtkcollector/app/profile/ActiveRecordingConfigTest.kt
git commit -m "Apply active setup policies at start"
```

---

## Task 13: Wire Solution Policy Into Recording And Dashboard

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingServiceState.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardModels.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardServiceMapper.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/HomeDashboard.kt`
- Modify: `app/src/test/kotlin/org/rtkcollector/app/ui/dashboard/DashboardServiceMapperTest.kt`

- [ ] **Step 1: Add dashboard mapper test**

Add:

```kotlin
@Test
fun `mock source policy and actual source are displayed`() {
    val intent = Intent(RecordingForegroundService.ACTION_STATE).apply {
        putExtra(RecordingForegroundService.EXTRA_STATE_RUNNING, true)
        putExtra(RecordingForegroundService.EXTRA_STATE_MOCK_LOCATION_STATE, "PUBLISHED")
        putExtra(RecordingForegroundService.EXTRA_STATE_SOLUTION_SOURCE_POLICY, "AUTO_BEST")
        putExtra(RecordingForegroundService.EXTRA_STATE_MOCK_LOCATION_SOURCE, "RTKLIB_REALTIME")
    }

    val state = dashboardStateFromRecordingIntent(intent)

    assertTrue(state.fix.mockLocation.contains("AUTO_BEST"))
    assertTrue(state.fix.mockLocation.contains("RTKLIB_REALTIME"))
}
```

- [ ] **Step 2: Add service extras**

Add constants:

```kotlin
EXTRA_SOLUTION_SOURCE_POLICY
EXTRA_STATE_SOLUTION_SOURCE_POLICY
EXTRA_STATE_MOCK_LOCATION_SOURCE
EXTRA_STATE_BEST_SOLUTION_SOURCE_ENGINE
```

- [ ] **Step 3: Use policy in mock publish path**

When selecting snapshot for mock publishing, call:

```kotlin
BestSolutionSelector.select(
    candidates = latestCandidates,
    nowMillis = now,
    policy = activeSolutionSourcePolicy,
)
```

Keep screen display of device and RTKLIB tiles separate.

- [ ] **Step 4: Update dashboard text**

Show compact text such as:

```text
Mock AUTO_BEST -> RTKLIB_REALTIME, 1000 ms
```

or:

```text
Mock DEVICE_INTERNAL_ONLY -> stale
```

- [ ] **Step 5: Run tests and compile**

Run dashboard mapper tests where supported and app Kotlin compile.

- [ ] **Step 6: Checkpoint commit**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/recording app/src/main/kotlin/org/rtkcollector/app/ui/dashboard app/src/test/kotlin/org/rtkcollector/app/ui/dashboard/DashboardServiceMapperTest.kt
git commit -m "Show solution source policy in dashboard"
```

---

## Task 14: Add Consistent Profile Text Field Component

**Files:**
- Create: `app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ConsistentProfileTextField.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileScreens.kt`

- [ ] **Step 1: Create shared component**

Create:

```kotlin
package org.rtkcollector.app.ui.profiles

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp

@Composable
fun ConsistentProfileTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    enabled: Boolean = true,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shape = MaterialTheme.shapes.small,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = singleLine,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(start = 8.dp)
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 4.dp),
        )
    }
}
```

Add missing import for `androidx.compose.foundation.background`.

- [ ] **Step 2: Replace profile editor text fields**

In `ProfileScreens.kt`, use `ConsistentProfileTextField` for text and multiline
fields. Preserve existing dropdown, boolean and read-only list behaviour.

- [ ] **Step 3: Manual verification checklist**

On a hardware/Bluetooth keyboard:

- arrow left/right/up/down moves inside field;
- Ctrl/Alt/Shift-arrow remains field-local where Android supports it;
- Tab moves to next field;
- Shift+Tab moves to previous field;
- cursor position is preserved when returning to a field;
- no purple filled background appears.

- [ ] **Step 4: Run compile**

Run app Kotlin compile.

- [ ] **Step 5: Checkpoint commit**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ConsistentProfileTextField.kt app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileScreens.kt
git commit -m "Use consistent profile text fields"
```

---

## Task 15: Add Content-Fit Dashboard Layout Fallback

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardModels.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/HomeDashboard.kt`
- Modify: `app/src/test/kotlin/org/rtkcollector/app/ui/dashboard/DashboardModelsTest.kt`

- [ ] **Step 1: Add layout decision tests**

Add:

```kotlin
@Test
fun `portrait two column falls back when content overflows`() {
    val decision = compactDashboardCardColumnCount(
        availableWidthDp = 700,
        orientation = DashboardOrientation.PORTRAIT,
        fontScale = 1.0f,
        criticalContentFits = false,
    )

    assertEquals(1, decision)
}
```

Adapt existing `compactDashboardCardColumnCount` signature to include
`criticalContentFits: Boolean = true`.

- [ ] **Step 2: Implement model fallback**

In `DashboardModels.kt`, ensure:

```kotlin
if (!criticalContentFits) return 1
```

before returning two columns.

- [ ] **Step 3: Add UI measurement hook**

In `HomeDashboard.kt`, use `onTextLayout { if (it.hasVisualOverflow) ... }`
for critical text values. Feed a debounced `criticalContentFits` state into the
column-count decision. Add hysteresis by requiring two consecutive fit failures
before switching layout, and only switching back on orientation/size change.

- [ ] **Step 4: Run tests and compile**

Run dashboard model tests where supported and app Kotlin compile.

- [ ] **Step 5: Checkpoint commit**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/ui/dashboard app/src/test/kotlin/org/rtkcollector/app/ui/dashboard/DashboardModelsTest.kt
git commit -m "Add dashboard content fit fallback"
```

---

## Task 16: Session Metadata And Documentation

**Files:**
- Modify: `core/session/src/main/kotlin/org/rtkcollector/core/session/SessionMetadata.kt`
- Modify: `core/session/src/test/kotlin/org/rtkcollector/core/session/SessionMetadataTest.kt`
- Modify: `docs/specification/ui-requirements.md`
- Modify: `docs/specification/workflows.md`
- Modify: `docs/specification/receiver-behaviour.md`
- Modify: `docs/specification/session-artifacts.md`
- Modify: `docs/specification/verification-matrix.md`
- Modify: `docs/user-workflows.md`
- Modify: `docs/contributor-onboarding.md`
- Modify: `docs/superpowers/plan-status.md`

- [ ] **Step 1: Add metadata test**

Add:

```kotlin
@Test
fun `session metadata records solution policies without secrets`() {
    val metadata = SessionMetadata(
        sessionId = "session",
        workflowId = "rover-ntrip",
        rtklibProfileId = "rtklib-rover",
        solutionSourcePolicy = "AUTO_BEST",
        mockSourcePolicy = "DEVICE_INTERNAL_ONLY",
        activeSettingsSetId = "settings",
        activeSettingsSetName = "UM980 rover",
        activeSetupModified = true,
    )

    val json = metadata.toJson()

    assertEquals("rtklib-rover", json.getString("rtklibProfileId"))
    assertEquals("AUTO_BEST", json.getString("solutionSourcePolicy"))
    assertFalse(json.toString().contains("password", ignoreCase = true))
}
```

Adjust field names to match the existing `SessionMetadata` constructor.

- [ ] **Step 2: Add metadata fields**

Add nullable/defaulted fields:

```kotlin
val activeSettingsSetId: String? = null,
val activeSettingsSetName: String? = null,
val activeSetupModified: Boolean = false,
val rtklibProfileId: String? = null,
val solutionSourcePolicy: String? = null,
val mockSourcePolicy: String? = null,
val actualMockSource: String? = null,
```

Persist them in JSON without secrets.

- [ ] **Step 3: Update formal specs**

Add normative requirements:

- `UI-ACTIVESETUP-001`: active settings set visible at top of menu.
- `UI-PROFILE-ACTIONS-001`: Activate/Edit/View/Rename/Copy/Delete are distinct.
- `UI-PROFILE-GROUPS-001`: profile grouping and ordering.
- `UI-TEXTFIELD-001`: consistent hardware-keyboard text fields.
- `WF-ACTIVESETUP-001`: Start validates effective active setup.
- `WF-SOLUTIONPOLICY-001`: automatic/manual solution source policy.
- `SESSION-SOLUTIONPOLICY-001`: session metadata records RTKLIB and solution policy without secrets.

Update `verification-matrix.md` rows for those IDs.

- [ ] **Step 4: Update user docs**

In `docs/user-workflows.md`, add a section:

```markdown
## Active Settings Set

The top of Settings shows the active settings set. It is the reusable template
for the current situation. Quick dashboard changes are remembered temporary
overrides and do not edit the settings set until you choose Save overrides.
```

Document solution/mock policy and RTKLIB/device separation.

- [ ] **Step 5: Update plan status**

Add row:

```markdown
| Active setup, RTKLIB/profile compatibility and solution policy | `2026-06-20-active-setup-rtklib-and-profile-compatibility.md` | Open | Design and implementation plan exist. Implementation has not started. |
```

- [ ] **Step 6: Run docs checks**

Run:

```bash
rg -n "\bTB""D\b|\bTO""DO\b|\bPLACE""HOLDER\b|raw"" status|receiver"" TX|During implementation,"" fill|Similar"" to|appropriate"" error handling|handle"" edge cases" docs/superpowers/specs/2026-06-20-active-setup-rtklib-and-profile-compatibility-design.md docs/superpowers/plans/2026-06-20-active-setup-rtklib-and-profile-compatibility.md docs/specification docs/user-workflows.md
```

Expected: no unintended placeholders or banned terminology in new/changed docs.

- [ ] **Step 7: Checkpoint commit**

```bash
git add core/session docs
git commit -m "Document active setup and solution policy"
```

---

## Task 17: Final Verification And Review

**Files:**
- All changed files from previous tasks.

- [ ] **Step 1: Run whitespace check**

Run:

```bash
git diff --check
```

Expected: no output and exit code 0.

- [ ] **Step 2: Run pure Kotlin tests**

Run at minimum:

```bash
sh gradlew --no-daemon :core:solution:test :core:session:test :core:workflow:test :receiver:ublox-m8:test :receiver:unicore-n4:test
```

Expected: build success.

- [ ] **Step 3: Run app Kotlin compile**

Run:

```bash
ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK ANDROID_SDK_ROOT=/storage/3830-3863/Termux/AndroidSDK sh gradlew --no-daemon -Pandroid.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2 :app:compileDebugKotlin -x :app:processDebugResources
```

Expected in this Termux environment: build success.

- [ ] **Step 4: Android Studio/host validation**

On Windows or another host with working Android SDK/NDK native tools:

```bash
gradlew clean assembleDebug test
```

Expected: full APK build and tests pass. If RTKLIB native is enabled, verify the
native `.so` build too.

- [ ] **Step 5: Manual smoke checklist**

Verify manually on device:

- active settings set visible at top of Settings;
- quick selector creates remembered override;
- reset overrides returns to settings-set defaults;
- locked profile cannot be quick-changed;
- choose-once profile is remembered;
- ask-every-time value clears after stop;
- incompatible u-blox/UM980 command profile cannot be activated;
- built-ins are View + Copy;
- Rename is separate from Edit;
- profile groups can move;
- RTKLIB profile visible and selectable;
- dashboard shows device and RTKLIB separately;
- mock source shows actual source;
- hardware keyboard arrow keys and Tab/Shift+Tab work in text fields;
- portrait small screen falls back to single-column telemetry when content clips.

- [ ] **Step 6: Request code review**

Use `superpowers:requesting-code-review`.

Reviewer scopes:

- Kotlin/domain-model maintainer;
- Android UI/profile workflow reviewer;
- GNSS/RTK workflow reviewer.

- [ ] **Step 7: Final commit or squash**

If tasks were committed one by one, keep the history if it is reviewable. If
the branch became noisy, squash only after user approval.

---

## Self-Review Checklist

- Spec coverage:
  - Active setup visibility: Task 7 and Task 16.
  - Per-option policy: Tasks 1, 2, 5, 11 and 12.
  - Profile compatibility: Tasks 4, 6 and 12.
  - RTKLIB config UI: Tasks 3, 6, 12 and 13.
  - Device/RTKLIB solution separation: Tasks 3, 13 and 16.
  - Profile actions/grouping: Tasks 8 and 9.
  - Naming: Task 10.
  - Text fields: Task 14.
  - Adaptive dashboard: Task 15.
  - Docs/session metadata: Task 16.
- Placeholder scan:
  - This plan intentionally contains no unresolved marker text. Where a test
    command may be locally blocked by Termux Android resource processing, the
    plan gives the fallback compile check.
- Type consistency:
  - `ActiveSetupOptionKey`, `SettingsSetOptionPolicy`,
    `SettingsSetOptionPolicies`, `ActiveSetupOverrides`,
    `ActiveSetupTransientChoices`, `SolutionSourcePolicy`,
    `SolutionPolicyProfile` and `ProfileCompatibilityResult` are introduced
    before later tasks reference them.
