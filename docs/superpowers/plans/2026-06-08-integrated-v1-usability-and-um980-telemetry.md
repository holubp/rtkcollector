# Integrated V1 Usability And UM980 Telemetry Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the partial Compose flow with a usable V1 field workflow: saved settings sets, real profile editing, Compose-owned start/stop/monitoring, robust UM980 binary telemetry, live NTRIP mountpoint switching and post-stop session sharing.

**Architecture:** Keep the foreground service as the only owner of capture, USB, wake lock, NTRIP and session writers. Compose resolves profiles/settings into an explicit start intent, then observes service broadcasts; parsers, ZIP sharing and UI state remain advisory and cannot mutate `receiver-rx.raw`.

**Tech Stack:** Android Kotlin, Jetpack Compose Material 3, `SharedPreferences` + JSON profile stores, existing `:receiver:unicore-n4`, existing `:core:capture`, `:core:correction`, `:core:session`, JUnit 5 JVM tests.

---

## File Structure

Create:

- `app/src/main/kotlin/org/rtkcollector/app/profile/SettingsSetModels.kt` — settings-set domain model, local override model and JSON serialization.
- `app/src/test/kotlin/org/rtkcollector/app/profile/SettingsSetModelsTest.kt` — pure JVM tests for references, overrides, renaming/copying and secret redaction.
- `app/src/main/kotlin/org/rtkcollector/app/profile/ActiveRecordingConfig.kt` — resolves settings set + profiles + secrets into `RecordingForegroundService` intents.
- `app/src/test/kotlin/org/rtkcollector/app/profile/ActiveRecordingConfigTest.kt` — no-NTRIP, rover+NTRIP, local override and secret-reference tests.
- `app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileListModels.kt` — pure UI model functions for list/detail/edit screens.
- `app/src/test/kotlin/org/rtkcollector/app/ui/profiles/ProfileListModelsTest.kt` — model tests for protected built-ins, copy/rename/delete and local override labels.
- `app/src/main/kotlin/org/rtkcollector/app/ui/usb/UsbSelectionModels.kt` — pure models for device label and baud selector state.
- `app/src/test/kotlin/org/rtkcollector/app/ui/usb/UsbSelectionModelsTest.kt` — model tests for allowed baud values and selected device labels.
- `app/src/main/kotlin/org/rtkcollector/app/recording/SessionZipExporter.kt` — on-demand ZIP plan/progress model and path-based export worker.
- `app/src/test/kotlin/org/rtkcollector/app/recording/SessionZipExporterTest.kt` — ZIP preset, progress and source-file preservation tests.
- `receiver/unicore-n4/src/main/kotlin/org/rtkcollector/receiver/unicore/Um980NmeaExporter.kt` — generated GGA/RMC/VTG from decoded BESTNAVB telemetry.
- `receiver/unicore-n4/src/test/kotlin/org/rtkcollector/receiver/unicore/Um980NmeaExporterTest.kt` — generated NMEA checksum/content tests.

Modify:

- `app/src/main/kotlin/org/rtkcollector/app/profile/ProfileModels.kt` — keep existing profile models stable while settings sets reference them by `id` and `name`.
- `app/src/main/kotlin/org/rtkcollector/app/profile/ProfileStores.kt` — add settings-set persistence and selected settings-set key.
- `app/src/test/kotlin/org/rtkcollector/app/profile/ProfileStoresTest.kt` — add store coverage for settings sets and defaults.
- `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt` — Compose navigation, selected settings set, start service directly, no launch of legacy Activity.
- `app/src/main/kotlin/org/rtkcollector/app/ui/settings/SettingsHub.kt` — add Settings Sets and USB/Baud entries, route to concrete screens.
- `app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileScreens.kt` — replace current stub screens with reusable list/detail/edit screens.
- `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardModels.kt` — add profile/settings summary state and click targets.
- `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/HomeDashboard.kt` — dense tiled layout, profile tile, fixed start/stop/menu, click routing.
- `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardServiceMapper.kt` — map richer service state into dashboard fields.
- `app/src/test/kotlin/org/rtkcollector/app/ui/dashboard/DashboardStateTest.kt` — profile tile and clickable-action model tests.
- `app/src/test/kotlin/org/rtkcollector/app/ui/dashboard/DashboardServiceMapperTest.kt` — BESTNAV/STADOP/NTRIP mapping tests.
- `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt` — consume resolved settings extras, richer telemetry, generated NMEA export, NTRIP rates/metadata, ZIP state broadcast hooks.
- `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingServiceState.kt` — add field-level telemetry and NTRIP byte-rate/base metadata state.
- `app/src/test/kotlin/org/rtkcollector/app/recording/Um980BaudTransitionTest.kt` — expand default command profile and baud-transition coverage.
- `receiver/unicore-n4/src/main/kotlin/org/rtkcollector/receiver/unicore/Um980BinaryParser.kt` — parse full BESTNAVB fields and STADOPB.
- `receiver/unicore-n4/src/main/kotlin/org/rtkcollector/receiver/unicore/Um980Telemetry.kt` — add typed telemetry fields for DOP, tracked/used, sigmas, status/type, UTC and source.
- `receiver/unicore-n4/src/main/kotlin/org/rtkcollector/receiver/unicore/Um980RuntimeProfiles.kt` — update default binary rover command sequence.
- `receiver/unicore-n4/src/test/kotlin/org/rtkcollector/receiver/unicore/Um980BinaryParserTest.kt` — full field extraction tests.
- `receiver/unicore-n4/src/test/kotlin/org/rtkcollector/receiver/unicore/Um980RuntimeProfileTest.kt` — command sequence tests.
- `core/correction/src/main/kotlin/org/rtkcollector/core/correction/NtripClient.kt` — ensure NTRIP v2-preferred requests and terminal auth handling are explicit.
- `core/correction/src/test/kotlin/org/rtkcollector/core/correction/NtripClientTest.kt` — auth, sourcetable and v2 request tests.
- `docs/user-workflows.md` and `README.md` — user documentation for settings sets, profiles, dashboard tiles and session sharing.

---

### Task 1: Settings Set Domain Model

**Files:**

- Create: `app/src/main/kotlin/org/rtkcollector/app/profile/SettingsSetModels.kt`
- Create: `app/src/test/kotlin/org/rtkcollector/app/profile/SettingsSetModelsTest.kt`

- [ ] **Step 1: Write failing settings-set model tests**

```kotlin
package org.rtkcollector.app.profile

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SettingsSetModelsTest {
    @Test
    fun `settings set round trip preserves profile references and command overrides`() {
        val settingsSet = RecordingSettingsSet(
            id = "field-car-roof",
            name = "Car roof rover",
            workflowId = "rover-ntrip",
            receiverProfileId = "um980-n4",
            commandProfileRef = ProfileReference("um980-default-commands", "UM980 default commands"),
            usbBaudProfileRef = ProfileReference("um980-230400", "UM980 230400"),
            ntripCasterProfileRef = ProfileReference("caster-euref", "EUREF"),
            ntripMountpointProfileRef = ProfileReference("mount-tubo", "TUBO00CZE0"),
            recordingOutputProfileRef = ProfileReference("default-record-everything", "Default V1 recording policy"),
            storageProfileRef = ProfileReference("field-saf", "Field SD card"),
            overrides = SettingsSetOverrides(
                command = CommandProfileOverride(initScript = "UNLOG COM1", shutdownScript = "UNLOG COM1"),
                ntripMountpoint = NtripMountpointOverride(mountpoint = "TUBO00CZE0"),
            ),
        )

        val decoded = RecordingSettingsSet.fromJson(settingsSet.toJson())

        assertEquals("field-car-roof", decoded.id)
        assertEquals("rover-ntrip", decoded.workflowId)
        assertEquals("um980-default-commands", decoded.commandProfileRef.id)
        assertEquals("UNLOG COM1", decoded.overrides.command?.initScript)
        assertTrue(decoded.hasLocalOverrides)
    }

    @Test
    fun `settings set rejects plaintext ntrip password override`() {
        assertThrows(IllegalArgumentException::class.java) {
            RecordingSettingsSet(
                id = "bad",
                name = "Bad",
                workflowId = "rover-ntrip",
                receiverProfileId = "um980-n4",
                commandProfileRef = ProfileReference("commands", "Commands"),
                usbBaudProfileRef = ProfileReference("baud", "Baud"),
                ntripCasterProfileRef = ProfileReference("caster", "Caster"),
                ntripMountpointProfileRef = ProfileReference("mount", "Mount"),
                recordingOutputProfileRef = ProfileReference("record", "Record"),
                storageProfileRef = ProfileReference("storage", "Storage"),
                overrides = SettingsSetOverrides(ntripCaster = NtripCasterOverride(password = "plain-text")),
            ).validate()
        }
    }

    @Test
    fun `copy settings set creates editable non protected set`() {
        val copied = RecordingSettingsSet.builtInRoverNtrip().copySet(id = "copy", name = "Copy")

        assertEquals("copy", copied.id)
        assertEquals("Copy", copied.name)
        assertFalse(copied.isProtected)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.profile.SettingsSetModelsTest`

Expected: FAIL because `RecordingSettingsSet` and override types do not exist.

- [ ] **Step 3: Add settings-set models and JSON serialization**

Implement `SettingsSetModels.kt` with these public types:

```kotlin
package org.rtkcollector.app.profile

import org.json.JSONObject

data class ProfileReference(
    val id: String,
    val name: String,
) {
    fun validate() {
        require(id.isNotBlank()) { "Profile reference id must not be blank." }
        require(name.isNotBlank()) { "Profile reference name must not be blank." }
    }

    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name)

    companion object {
        fun fromJson(json: JSONObject): ProfileReference =
            ProfileReference(id = json.getString("id"), name = json.getString("name")).also(ProfileReference::validate)
    }
}

data class CommandProfileOverride(
    val initScript: String? = null,
    val shutdownScript: String? = null,
) {
    fun hasChanges(): Boolean = initScript != null || shutdownScript != null
}

data class UsbBaudProfileOverride(
    val profileBaud: Int? = null,
    val serialBaud: Int? = null,
    val usbVid: Int? = null,
    val usbPid: Int? = null,
    val usbDeviceName: String? = null,
) {
    fun validate() {
        profileBaud?.let { require(it in 9600..921600) { "Profile baud override must be 9600..921600." } }
        serialBaud?.let { require(it in 9600..921600) { "Serial baud override must be 9600..921600." } }
    }
}

data class NtripCasterOverride(
    val host: String? = null,
    val port: Int? = null,
    val username: String? = null,
    val secretId: String? = null,
    val password: String? = null,
) {
    fun validate() {
        port?.let { require(it in 1..65535) { "NTRIP port override must be 1..65535." } }
        require(password == null) { "Settings sets must not contain plaintext NTRIP passwords." }
    }
}

data class NtripMountpointOverride(
    val mountpoint: String? = null,
    val stationId: String? = null,
    val baseLatDeg: Double? = null,
    val baseLonDeg: Double? = null,
)

data class RecordingOutputOverride(
    val recordTxToReceiver: Boolean? = null,
    val recordNtripCorrectionInput: Boolean? = null,
    val exportNmea: Boolean? = null,
    val exportJsonSolution: Boolean? = null,
    val exportGpx: Boolean? = null,
    val recordRemoteBaseRaw: Boolean? = null,
)

data class StorageProfileOverride(
    val kind: String? = null,
    val treeUri: String? = null,
) {
    fun validate() {
        kind?.let { require(it == "APP_PRIVATE" || it == "SAF_TREE") { "Storage kind override must be APP_PRIVATE or SAF_TREE." } }
    }
}

data class SettingsSetOverrides(
    val command: CommandProfileOverride? = null,
    val usbBaud: UsbBaudProfileOverride? = null,
    val ntripCaster: NtripCasterOverride? = null,
    val ntripMountpoint: NtripMountpointOverride? = null,
    val recordingOutput: RecordingOutputOverride? = null,
    val storage: StorageProfileOverride? = null,
) {
    fun validate() {
        usbBaud?.validate()
        ntripCaster?.validate()
        storage?.validate()
    }

    val hasChanges: Boolean
        get() = command?.hasChanges() == true ||
            usbBaud != null ||
            ntripCaster != null ||
            ntripMountpoint != null ||
            recordingOutput != null ||
            storage != null
}

data class RecordingSettingsSet(
    val id: String,
    val name: String,
    val workflowId: String,
    val receiverProfileId: String,
    val commandProfileRef: ProfileReference,
    val usbBaudProfileRef: ProfileReference,
    val ntripCasterProfileRef: ProfileReference? = null,
    val ntripMountpointProfileRef: ProfileReference? = null,
    val recordingOutputProfileRef: ProfileReference,
    val storageProfileRef: ProfileReference,
    val basePositionProfileRef: ProfileReference? = null,
    val overrides: SettingsSetOverrides = SettingsSetOverrides(),
    val isProtected: Boolean = false,
) {
    val hasLocalOverrides: Boolean get() = overrides.hasChanges

    fun validate() {
        require(id.isNotBlank()) { "Settings set id must not be blank." }
        require(name.isNotBlank()) { "Settings set name must not be blank." }
        require(workflowId.isNotBlank()) { "Settings set workflow id must not be blank." }
        require(receiverProfileId.isNotBlank()) { "Settings set receiver profile id must not be blank." }
        commandProfileRef.validate()
        usbBaudProfileRef.validate()
        ntripCasterProfileRef?.validate()
        ntripMountpointProfileRef?.validate()
        recordingOutputProfileRef.validate()
        storageProfileRef.validate()
        basePositionProfileRef?.validate()
        overrides.validate()
    }

    fun displayNameWithOverrides(): String =
        if (hasLocalOverrides) "$name + local changes" else name

    fun copySet(id: String, name: String): RecordingSettingsSet =
        copy(id = id, name = name, isProtected = false).also(RecordingSettingsSet::validate)

    fun toJson(): JSONObject {
        validate()
        return SettingsSetJson.toJson(this)
    }

    companion object {
        fun fromJson(json: JSONObject): RecordingSettingsSet =
            SettingsSetJson.fromJson(json).also(RecordingSettingsSet::validate)

        fun builtInRoverNtrip(): RecordingSettingsSet =
            RecordingSettingsSet(
                id = "um980-rover-ntrip",
                name = "UM980 rover + NTRIP",
                workflowId = "rover-ntrip",
                receiverProfileId = "um980-n4",
                commandProfileRef = ProfileReference("um980-default-commands", "UM980 default commands"),
                usbBaudProfileRef = ProfileReference("um980-230400", "UM980 230400"),
                ntripCasterProfileRef = ProfileReference("ntrip-caster-default", "NTRIP caster"),
                ntripMountpointProfileRef = ProfileReference("ntrip-mountpoint-default", "NTRIP mountpoint"),
                recordingOutputProfileRef = ProfileReference("default-record-everything", "Default V1 recording policy"),
                storageProfileRef = ProfileReference("app-private", "App-private external storage"),
                isProtected = true,
            )
    }
}
```

Implement `SettingsSetJson` in the same file with explicit nullable JSON helpers. The JSON writer must not write `password`; the reader must reject a `password` key under `ntripCaster`.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.profile.SettingsSetModelsTest`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/profile/SettingsSetModels.kt app/src/test/kotlin/org/rtkcollector/app/profile/SettingsSetModelsTest.kt
git commit -m "Add settings set profile model"
```

---

### Task 2: Settings Set Store And Active Selection

**Files:**

- Modify: `app/src/main/kotlin/org/rtkcollector/app/profile/ProfileStores.kt`
- Modify: `app/src/test/kotlin/org/rtkcollector/app/profile/ProfileStoresTest.kt`

- [ ] **Step 1: Add failing store tests**

Append these tests to `ProfileStoresTest.kt`:

```kotlin
@Test
fun `settings set rename rejects protected built in`() {
    val profiles = listOf(RecordingSettingsSet.builtInRoverNtrip())

    assertThrows(IllegalArgumentException::class.java) {
        renameProfile(
            profiles = profiles,
            profileId = "um980-rover-ntrip",
            newName = "Renamed",
            idOf = RecordingSettingsSet::id,
            isProtectedOf = RecordingSettingsSet::isProtected,
        ) { profile, name -> profile.copy(name = name) }
    }
}

@Test
fun `settings set json redacts secret material`() {
    val json = RecordingSettingsSet.builtInRoverNtrip()
        .copy(overrides = SettingsSetOverrides(ntripCaster = NtripCasterOverride(secretId = "secret-1")))
        .toJson()
        .toString()

    assertTrue(json.contains("secret-1"))
    assertFalse(json.contains("password"))
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.profile.ProfileStoresTest`

Expected: FAIL until store methods and imports compile.

- [ ] **Step 3: Add store methods**

In `ProfileStores.kt`, add:

```kotlin
fun settingsSets(): List<RecordingSettingsSet> =
    readProfiles("settingsSets", ::defaultSettingsSets, RecordingSettingsSet::fromJson)

fun saveSettingsSets(settingsSets: List<RecordingSettingsSet>) =
    writeProfiles("settingsSets", settingsSets.onEach(RecordingSettingsSet::validate).map(RecordingSettingsSet::toJson))

fun selectedSettingsSetId(): String =
    preferences.getString("selectedSettingsSetId", null) ?: defaultSettingsSets().first().id

fun saveSelectedSettingsSetId(id: String) {
    require(id.isNotBlank()) { "Selected settings set id must not be blank." }
    preferences.edit().putString("selectedSettingsSetId", id).apply()
}

private fun defaultSettingsSets(): List<RecordingSettingsSet> =
    listOf(RecordingSettingsSet.builtInRoverNtrip())
```

- [ ] **Step 4: Run tests**

Run: `./gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.profile.ProfileStoresTest`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/profile/ProfileStores.kt app/src/test/kotlin/org/rtkcollector/app/profile/ProfileStoresTest.kt
git commit -m "Persist settings sets"
```

---

### Task 3: Resolve Settings Into Recording Start Intents

**Files:**

- Create: `app/src/main/kotlin/org/rtkcollector/app/profile/ActiveRecordingConfig.kt`
- Create: `app/src/test/kotlin/org/rtkcollector/app/profile/ActiveRecordingConfigTest.kt`

- [ ] **Step 1: Write failing resolver tests**

```kotlin
package org.rtkcollector.app.profile

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ActiveRecordingConfigTest {
    @Test
    fun `plain rover config disables ntrip`() {
        val config = ActiveRecordingConfig.resolve(
            settingsSet = RecordingSettingsSet.builtInRoverNtrip().copy(
                workflowId = "plain-rover",
                ntripCasterProfileRef = null,
                ntripMountpointProfileRef = null,
            ),
            commandProfile = CommandProfile("commands", "Commands", initScript = "UNLOG COM1"),
            usbBaudProfile = UsbBaudProfile("baud", "Baud", profileBaud = 230400, serialBaud = 230400),
            ntripCasterProfile = null,
            ntripMountpointProfile = null,
            recordingPolicyProfile = RecordingPolicyProfile("record", "Record"),
            storageProfile = StorageProfile("storage", "Storage"),
            passwordLookup = { error("Password must not be requested") },
        )

        assertFalse(config.ntrip.enabled)
        assertEquals("plain-rover", config.workflowId)
    }

    @Test
    fun `ntrip config uses secret lookup and mountpoint override`() {
        val config = ActiveRecordingConfig.resolve(
            settingsSet = RecordingSettingsSet.builtInRoverNtrip().copy(
                overrides = SettingsSetOverrides(
                    ntripCaster = NtripCasterOverride(host = "www.euref-ip.be", port = 2101, username = "user", secretId = "secret-1"),
                    ntripMountpoint = NtripMountpointOverride(mountpoint = "TUBO00CZE0"),
                ),
            ),
            commandProfile = CommandProfile("commands", "Commands", initScript = "UNLOG COM1"),
            usbBaudProfile = UsbBaudProfile("baud", "Baud", profileBaud = 230400, serialBaud = 921600),
            ntripCasterProfile = NtripCasterProfile("caster", "Caster", host = "old", username = "old", secretId = "old-secret"),
            ntripMountpointProfile = NtripMountpointProfile("mount", "Mount", casterProfileId = "caster", mountpoint = "OLD"),
            recordingPolicyProfile = RecordingPolicyProfile("record", "Record"),
            storageProfile = StorageProfile("storage", "Storage"),
            passwordLookup = { secretId -> "password-for-$secretId" },
        )

        assertTrue(config.ntrip.enabled)
        assertEquals("www.euref-ip.be", config.ntrip.host)
        assertEquals("TUBO00CZE0", config.ntrip.mountpoint)
        assertEquals("password-for-secret-1", config.ntrip.password)
        assertEquals("secret-1", config.ntrip.secretRef)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.profile.ActiveRecordingConfigTest`

Expected: FAIL because `ActiveRecordingConfig` does not exist.

- [ ] **Step 3: Implement resolver**

Create:

```kotlin
package org.rtkcollector.app.profile

data class ActiveRecordingConfig(
    val workflowId: String,
    val workflowName: String,
    val receiverProfileId: String,
    val commandProfileId: String,
    val usbBaudProfileId: String,
    val profileBaud: Int,
    val serialBaud: Int,
    val initCommands: List<String>,
    val shutdownCommands: List<String>,
    val ntrip: ActiveNtripConfig,
    val recording: ActiveRecordingOutputConfig,
    val storage: ActiveStorageConfig,
) {
    companion object {
        fun resolve(
            settingsSet: RecordingSettingsSet,
            commandProfile: CommandProfile,
            usbBaudProfile: UsbBaudProfile,
            ntripCasterProfile: NtripCasterProfile?,
            ntripMountpointProfile: NtripMountpointProfile?,
            recordingPolicyProfile: RecordingPolicyProfile,
            storageProfile: StorageProfile,
            passwordLookup: (String) -> String?,
        ): ActiveRecordingConfig {
            settingsSet.validate()
            val commandOverride = settingsSet.overrides.command
            val usbOverride = settingsSet.overrides.usbBaud
            val casterOverride = settingsSet.overrides.ntripCaster
            val mountOverride = settingsSet.overrides.ntripMountpoint
            val recordingOverride = settingsSet.overrides.recordingOutput
            val storageOverride = settingsSet.overrides.storage
            val secretId = casterOverride?.secretId ?: ntripCasterProfile?.secretId
            val ntripHost = casterOverride?.host ?: ntripCasterProfile?.host.orEmpty()
            val ntripMountpoint = mountOverride?.mountpoint ?: ntripMountpointProfile?.mountpoint.orEmpty()
            val ntripEnabled = settingsSet.workflowId.contains("ntrip", ignoreCase = true)
            return ActiveRecordingConfig(
                workflowId = settingsSet.workflowId,
                workflowName = settingsSet.name,
                receiverProfileId = settingsSet.receiverProfileId,
                commandProfileId = commandProfile.id,
                usbBaudProfileId = usbBaudProfile.id,
                profileBaud = usbOverride?.profileBaud ?: usbBaudProfile.profileBaud,
                serialBaud = usbOverride?.serialBaud ?: usbBaudProfile.serialBaud,
                initCommands = (commandOverride?.initScript ?: commandProfile.initScript).commandLines(),
                shutdownCommands = (commandOverride?.shutdownScript ?: commandProfile.shutdownScript).commandLines(),
                ntrip = ActiveNtripConfig(
                    enabled = ntripEnabled,
                    host = ntripHost,
                    port = casterOverride?.port ?: ntripCasterProfile?.port ?: 2101,
                    mountpoint = ntripMountpoint,
                    username = casterOverride?.username ?: ntripCasterProfile?.username.orEmpty(),
                    secretRef = secretId,
                    password = secretId?.let(passwordLookup),
                    stationId = mountOverride?.stationId,
                    baseLatDeg = mountOverride?.baseLatDeg,
                    baseLonDeg = mountOverride?.baseLonDeg,
                ),
                recording = ActiveRecordingOutputConfig(
                    recordTxToReceiver = recordingOverride?.recordTxToReceiver ?: recordingPolicyProfile.recordTxToReceiver,
                    recordNtripCorrectionInput = recordingOverride?.recordNtripCorrectionInput ?: recordingPolicyProfile.recordNtripCorrectionInput,
                    exportNmea = recordingOverride?.exportNmea ?: recordingPolicyProfile.exportNmea,
                    exportJsonSolution = recordingOverride?.exportJsonSolution ?: recordingPolicyProfile.exportJsonSolution,
                    exportGpx = recordingOverride?.exportGpx ?: recordingPolicyProfile.exportGpx,
                    recordRemoteBaseRaw = recordingOverride?.recordRemoteBaseRaw ?: recordingPolicyProfile.recordRemoteBaseRaw,
                ),
                storage = ActiveStorageConfig(
                    id = storageProfile.id,
                    kind = storageOverride?.kind ?: storageProfile.kind,
                    treeUri = storageOverride?.treeUri ?: storageProfile.treeUri,
                ),
            )
        }
    }
}

data class ActiveNtripConfig(
    val enabled: Boolean,
    val host: String,
    val port: Int,
    val mountpoint: String,
    val username: String,
    val secretRef: String?,
    val password: String?,
    val stationId: String?,
    val baseLatDeg: Double?,
    val baseLonDeg: Double?,
)

data class ActiveRecordingOutputConfig(
    val recordTxToReceiver: Boolean,
    val recordNtripCorrectionInput: Boolean,
    val exportNmea: Boolean,
    val exportJsonSolution: Boolean,
    val exportGpx: Boolean,
    val recordRemoteBaseRaw: Boolean,
)

data class ActiveStorageConfig(
    val id: String,
    val kind: String,
    val treeUri: String?,
)

private fun String.commandLines(): List<String> =
    lineSequence().map(String::trim).filter { it.isNotEmpty() && !it.startsWith("#") }.toList()
```

- [ ] **Step 4: Run tests**

Run: `./gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.profile.ActiveRecordingConfigTest`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/profile/ActiveRecordingConfig.kt app/src/test/kotlin/org/rtkcollector/app/profile/ActiveRecordingConfigTest.kt
git commit -m "Resolve active recording settings"
```

---

### Task 4: Profile And Settings Set UI Models

**Files:**

- Create: `app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileListModels.kt`
- Create: `app/src/test/kotlin/org/rtkcollector/app/ui/profiles/ProfileListModelsTest.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileEditorModels.kt`

- [ ] **Step 1: Write failing UI model tests**

```kotlin
package org.rtkcollector.app.ui.profiles

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rtkcollector.app.profile.RecordingSettingsSet
import org.rtkcollector.app.profile.SettingsSetOverrides
import org.rtkcollector.app.profile.NtripMountpointOverride

class ProfileListModelsTest {
    @Test
    fun `protected profile row is copy only`() {
        val row = ProfileListRow(id = "built-in", name = "Built-in", isProtected = true, hasLocalOverrides = false)

        assertFalse(row.canEdit)
        assertTrue(row.canCopy)
        assertFalse(row.canDelete)
    }

    @Test
    fun `settings set local override label is visible`() {
        val state = SettingsSetListState.from(
            settingsSets = listOf(
                RecordingSettingsSet.builtInRoverNtrip().copy(
                    overrides = SettingsSetOverrides(ntripMountpoint = NtripMountpointOverride(mountpoint = "TUBO00CZE0")),
                ),
            ),
            selectedId = "um980-rover-ntrip",
        )

        assertEquals("UM980 rover + NTRIP + local changes", state.rows.single().displayName)
        assertTrue(state.rows.single().isSelected)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.ui.profiles.ProfileListModelsTest`

Expected: FAIL because model types do not exist.

- [ ] **Step 3: Implement UI model types**

Create:

```kotlin
package org.rtkcollector.app.ui.profiles

import org.rtkcollector.app.profile.RecordingSettingsSet

data class ProfileListRow(
    val id: String,
    val name: String,
    val isProtected: Boolean,
    val hasLocalOverrides: Boolean,
    val isSelected: Boolean = false,
) {
    val displayName: String = if (hasLocalOverrides) "$name + local changes" else name
    val canEdit: Boolean = !isProtected
    val canRename: Boolean = !isProtected
    val canCopy: Boolean = true
    val canDelete: Boolean = !isProtected
}

data class SettingsSetListState(
    val rows: List<ProfileListRow>,
) {
    companion object {
        fun from(settingsSets: List<RecordingSettingsSet>, selectedId: String): SettingsSetListState =
            SettingsSetListState(
                rows = settingsSets.map { set ->
                    ProfileListRow(
                        id = set.id,
                        name = set.name,
                        isProtected = set.isProtected,
                        hasLocalOverrides = set.hasLocalOverrides,
                        isSelected = set.id == selectedId,
                    )
                },
            )
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.ui.profiles.ProfileListModelsTest`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileListModels.kt app/src/test/kotlin/org/rtkcollector/app/ui/profiles/ProfileListModelsTest.kt
git commit -m "Add profile list UI models"
```

---

### Task 5: Concrete Compose Profile Screens

**Files:**

- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileScreens.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/settings/SettingsHub.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`

- [ ] **Step 1: Add Settings Sets navigation entry**

In `SettingsHub.kt`, add a primary entry labelled `Settings sets` before lower-level profile editors. It must route via `onSettingsSets`.

Expected Settings screen sections:

```text
Settings sets
Profiles
  Command scripts
  USB and baud
  NTRIP caster
  NTRIP mountpoint
  Recording/output
  Storage location
Sessions
```

- [ ] **Step 2: Replace stub screen calls**

In `MainActivity.kt`, expand `AppScreen` with:

```kotlin
SETTINGS_SETS,
USB_BAUD,
```

Route Settings Sets to a reusable list screen backed by `ProfileStores.settingsSets()` and selected ID. Route each existing stub to a concrete editor wrapper using current profile lists.

- [ ] **Step 3: Implement reusable Compose list/detail screen**

In `ProfileScreens.kt`, add composables:

```kotlin
@Composable
fun ProfileListScreen(
    title: String,
    rows: List<ProfileListRow>,
    onSelect: (String) -> Unit,
    onEdit: (String) -> Unit,
    onCopy: (String) -> Unit,
    onRename: (String) -> Unit,
    onDelete: (String) -> Unit,
    onBack: () -> Unit,
)
```

Use Material 3 `ListItem`, `OutlinedButton`, `TextButton`, `AlertDialog` and `OutlinedTextField`. Each row must keep labels visually attached to fields by using `OutlinedTextField(label = ...)` for editable data and grouped cards for related fields.

- [ ] **Step 4: Manual compile check**

Run: `./gradlew :app:compileDebugKotlin`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileScreens.kt app/src/main/kotlin/org/rtkcollector/app/ui/settings/SettingsHub.kt app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt
git commit -m "Replace stub profile screens"
```

---

### Task 6: USB/Baud Selection And Compose-Owned Start

**Files:**

- Create: `app/src/main/kotlin/org/rtkcollector/app/ui/usb/UsbSelectionModels.kt`
- Create: `app/src/test/kotlin/org/rtkcollector/app/ui/usb/UsbSelectionModelsTest.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/profile/ActiveRecordingConfig.kt`

- [ ] **Step 1: Write failing USB/baud model tests**

```kotlin
package org.rtkcollector.app.ui.usb

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class UsbSelectionModelsTest {
    @Test
    fun `baud selector exposes only validated v1 values`() {
        assertEquals(listOf(115200, 230400, 460800, 921600), BaudSelectorState.allowedValues)
    }

    @Test
    fun `unsupported baud is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            BaudSelectorState(selectedBaud = 38400)
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.ui.usb.UsbSelectionModelsTest`

Expected: FAIL because `BaudSelectorState` does not exist.

- [ ] **Step 3: Implement USB/baud model**

```kotlin
package org.rtkcollector.app.ui.usb

data class BaudSelectorState(
    val selectedBaud: Int,
) {
    init {
        require(selectedBaud in allowedValues) { "Unsupported V1 baud rate: $selectedBaud." }
    }

    companion object {
        val allowedValues = listOf(115200, 230400, 460800, 921600)
    }
}
```

- [ ] **Step 4: Wire Compose start to service**

In `MainActivity.kt`, remove this branch from the Start action:

```kotlin
context.startActivity(Intent(context, org.rtkcollector.app.MainActivity::class.java))
```

Replace it with a call that resolves the selected settings set and starts `RecordingForegroundService.ACTION_START`. The start path must set `EXTRA_NTRIP_ENABLED=false` when the active workflow does not use NTRIP.

Intent extras must come from `ActiveRecordingConfig`:

```kotlin
putExtra(RecordingForegroundService.EXTRA_WORKFLOW_ID, config.workflowId)
putExtra(RecordingForegroundService.EXTRA_WORKFLOW_NAME, config.workflowName)
putExtra(RecordingForegroundService.EXTRA_RECEIVER_PROFILE_ID, config.receiverProfileId)
putExtra(RecordingForegroundService.EXTRA_PROFILE_BAUD, config.profileBaud)
putExtra(RecordingForegroundService.EXTRA_SERIAL_BAUD, config.serialBaud)
putStringArrayListExtra(RecordingForegroundService.EXTRA_INIT_COMMANDS, ArrayList(config.initCommands))
putStringArrayListExtra(RecordingForegroundService.EXTRA_SHUTDOWN_COMMANDS, ArrayList(config.shutdownCommands))
putExtra(RecordingForegroundService.EXTRA_NTRIP_ENABLED, config.ntrip.enabled)
```

If there is no selected USB device or permission, show an error state on the dashboard and do not start the service.

- [ ] **Step 5: Run focused tests and compile**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.ui.usb.UsbSelectionModelsTest
./gradlew :app:compileDebugKotlin
```

Expected: both PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/ui/usb/UsbSelectionModels.kt app/src/test/kotlin/org/rtkcollector/app/ui/usb/UsbSelectionModelsTest.kt app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt app/src/main/kotlin/org/rtkcollector/app/profile/ActiveRecordingConfig.kt
git commit -m "Start recording from Compose settings"
```

---

### Task 7: Dashboard Profile Tile And Responsive Polishing

**Files:**

- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardModels.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/HomeDashboard.kt`
- Modify: `app/src/test/kotlin/org/rtkcollector/app/ui/dashboard/DashboardStateTest.kt`

- [ ] **Step 1: Add failing dashboard model test**

```kotlin
@Test
fun `planned dashboard contains active settings set and profile summaries`() {
    val state = DashboardState.planned(
        workflow = "Rover + NTRIP",
        mountpoint = "TUBO00CZE0",
        receiver = "UM980",
        storage = "Field SD",
        profiles = ProfilesCardState(
            settingsSet = "Car roof rover + local changes",
            commandProfile = "UM980 default commands",
            baudProfile = "UM980 230400",
            ntripCasterProfile = "EUREF",
            recordingOutputProfile = "Default V1 recording policy",
            storageLocationProfile = "Field SD",
        ),
    )

    assertEquals("Car roof rover + local changes", state.profiles.settingsSet)
    assertEquals(DashboardActionKind.MENU, state.secondaryActions.single().kind)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.ui.dashboard.DashboardStateTest`

Expected: FAIL until `ProfilesCardState` is added.

- [ ] **Step 3: Add profile tile state**

In `DashboardModels.kt`, add:

```kotlin
data class ProfilesCardState(
    val settingsSet: String = "n/a",
    val commandProfile: String = "n/a",
    val baudProfile: String = "n/a",
    val ntripCasterProfile: String = "n/a",
    val recordingOutputProfile: String = "n/a",
    val storageLocationProfile: String = "n/a",
)
```

Add `profiles: ProfilesCardState` to `DashboardState`.

- [ ] **Step 4: Render profile tile and keep controls fixed**

In `HomeDashboard.kt`, add a fifth `DashboardCard("Profiles")` with compact `Metric` rows. Use `Scaffold.bottomBar` for Start/Stop/Menu and keep cards inside the vertical scroll. Use `FlowRow` width constraints already present; adjust `DashboardCard` to `widthIn(min = 230.dp, max = 360.dp)` so landscape fits more cards.

- [ ] **Step 5: Run tests and compile**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.ui.dashboard.DashboardStateTest
./gradlew :app:compileDebugKotlin
```

Expected: both PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardModels.kt app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/HomeDashboard.kt app/src/test/kotlin/org/rtkcollector/app/ui/dashboard/DashboardStateTest.kt
git commit -m "Add profile summary dashboard tile"
```

---

### Task 8: UM980 Default Binary Profile And Baud Plan

**Files:**

- Modify: `receiver/unicore-n4/src/main/kotlin/org/rtkcollector/receiver/unicore/Um980RuntimeProfiles.kt`
- Modify: `receiver/unicore-n4/src/test/kotlin/org/rtkcollector/receiver/unicore/Um980RuntimeProfileTest.kt`
- Modify: `app/src/test/kotlin/org/rtkcollector/app/recording/Um980BaudTransitionTest.kt`

- [ ] **Step 1: Add failing command sequence test**

Expected default mode commands after the baud switch:

```text
UNLOG COM1
MODE ROVER
CONFIG MMP ENABLE
VERSIONB
BESTNAVB COM1 0.1
STADOPB COM1 1
OBSVMCMPB COM1 0.25
GPSEPHB COM1 300
GLOEPHB COM1 300
GALEPHB COM1 300
BDSEPHB COM1 300
BD3EPHB COM1 300
QZSSEPHB COM1 300
GPSIONB ONCHANGED
BDSIONB ONCHANGED
BD3IONB ONCHANGED
GALIONB ONCHANGED
GPSUTCB ONCHANGED
BDSUTCB ONCHANGED
BD3UTCB ONCHANGED
GALUTCB ONCHANGED
```

The generated baud command must be `CONFIG COM1 <selected-baud>` and must not be duplicated inside arbitrary user init text.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :receiver:unicore-n4:test --tests org.rtkcollector.receiver.unicore.Um980RuntimeProfileTest`

Expected: FAIL until profile commands are updated.

- [ ] **Step 3: Update runtime profile**

Update the default UM980 rover profile builder to emit the sequence above, preserving validated command syntax and protected built-in profile behavior. Keep `STADOPB COM1 1` as the default command and add a compatibility profile or command-builder fallback if current validators reject it.

- [ ] **Step 4: Run receiver tests**

Run: `./gradlew :receiver:unicore-n4:test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add receiver/unicore-n4/src/main/kotlin/org/rtkcollector/receiver/unicore/Um980RuntimeProfiles.kt receiver/unicore-n4/src/test/kotlin/org/rtkcollector/receiver/unicore/Um980RuntimeProfileTest.kt app/src/test/kotlin/org/rtkcollector/app/recording/Um980BaudTransitionTest.kt
git commit -m "Update UM980 binary rover profile"
```

---

### Task 9: Full BESTNAVB And STADOPB Telemetry

**Files:**

- Modify: `receiver/unicore-n4/src/main/kotlin/org/rtkcollector/receiver/unicore/Um980Telemetry.kt`
- Modify: `receiver/unicore-n4/src/main/kotlin/org/rtkcollector/receiver/unicore/Um980BinaryParser.kt`
- Modify: `receiver/unicore-n4/src/test/kotlin/org/rtkcollector/receiver/unicore/Um980BinaryParserTest.kt`

- [ ] **Step 1: Add failing parser tests**

Tests must assert:

- BESTNAVB maps `latDeg`, `lonDeg`, MSL height, undulation-derived ellipsoidal height, latitude sigma, longitude sigma, height sigma, differential age, station ID, satellites tracked and satellites used.
- STADOPB maps `gdop`, `pdop`, `tdop`, `vdop`, `hdop`, `ndop`, `edop` and tracked PRNs.
- Unknown position type/status values are rendered as stable `TYPE_<number>` and `STATUS_<number>` strings.

Use the existing binary-frame test helpers in `Um980BinaryParserTest.kt`; add a helper that builds message ID `954` with a valid CRC.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :receiver:unicore-n4:test --tests org.rtkcollector.receiver.unicore.Um980BinaryParserTest`

Expected: FAIL on missing STADOPB and missing fields.

- [ ] **Step 3: Extend telemetry model**

Add fields:

```kotlin
val solutionAgeS: Double? = null,
val heightErrorM: Double? = null,
val satellitesTracked: Int? = null,
val satellitesUsed: Int? = null,
val gdop: Double? = null,
val tdop: Double? = null,
val ndop: Double? = null,
val edop: Double? = null,
val trackedPrns: List<Int> = emptyList(),
```

Keep `satellitesInView` for compatibility, but map BESTNAVB tracked satellites to `satellitesTracked`, not true in-view.

- [ ] **Step 4: Implement STADOPB parser**

Add:

```kotlin
private const val STADOPB_MESSAGE_ID = 954

fun parseStadopb(frame: ByteArray): Um980Telemetry? {
    if (!isValidFrame(frame)) return null
    val headerLength = frame[3].toInt() and 0xff
    if (u16(frame, 4) != STADOPB_MESSAGE_ID) return null
    val payloadLength = u16(frame, 6)
    val payload = ByteBuffer.wrap(frame.copyOfRange(headerLength, headerLength + payloadLength)).order(ByteOrder.LITTLE_ENDIAN)
    return Um980Telemetry(
        source = "STADOPB",
        gdop = payload.safeFloat(0),
        pdop = payload.safeFloat(4),
        tdop = payload.safeFloat(8),
        vdop = payload.safeFloat(12),
        hdop = payload.safeFloat(16),
        ndop = payload.safeFloat(20),
        edop = payload.safeFloat(24),
        trackedPrns = parsePrnList(payload, startOffset = 36),
    )
}
```

Make `safeFloat` return `null` if the payload is too short. `parsePrnList` must stop at payload end and ignore zero PRNs.

- [ ] **Step 5: Run receiver tests**

Run: `./gradlew :receiver:unicore-n4:test`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add receiver/unicore-n4/src/main/kotlin/org/rtkcollector/receiver/unicore/Um980Telemetry.kt receiver/unicore-n4/src/main/kotlin/org/rtkcollector/receiver/unicore/Um980BinaryParser.kt receiver/unicore-n4/src/test/kotlin/org/rtkcollector/receiver/unicore/Um980BinaryParserTest.kt
git commit -m "Parse UM980 BESTNAVB and STADOPB telemetry"
```

---

### Task 10: Generated NMEA Export From BESTNAVB

**Files:**

- Create: `receiver/unicore-n4/src/main/kotlin/org/rtkcollector/receiver/unicore/Um980NmeaExporter.kt`
- Create: `receiver/unicore-n4/src/test/kotlin/org/rtkcollector/receiver/unicore/Um980NmeaExporterTest.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt`

- [ ] **Step 1: Write failing NMEA exporter tests**

```kotlin
package org.rtkcollector.receiver.unicore

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Um980NmeaExporterTest {
    @Test
    fun `bestnav telemetry generates gga sentence with checksum`() {
        val sentences = Um980NmeaExporter.export(
            Um980Telemetry(
                source = "BESTNAVB",
                utcTime = "2026-06-08T12:34:56Z",
                positionType = "NARROW_FLOAT",
                latDeg = 50.087451234,
                lonDeg = 14.421253456,
                altitudeM = 243.812,
                ellipsoidalHeightM = 287.423,
                satellitesUsed = 18,
                hdop = 0.7,
            ),
        )

        assertTrue(sentences.any { it.startsWith("\$GPGGA,123456") })
        assertTrue(sentences.all { it.endsWith("\r\n") })
        assertTrue(sentences.all { "*" in it })
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :receiver:unicore-n4:test --tests org.rtkcollector.receiver.unicore.Um980NmeaExporterTest`

Expected: FAIL because exporter does not exist.

- [ ] **Step 3: Implement exporter**

Generate GGA/RMC/VTG when enough fields are present. For V1, GGA is required. Map fix type from UM980 position type:

```text
NARROW_INT -> 4
NARROW_FLOAT -> 5
PSRDIFF, L1_FLOAT, IONOFREE_FLOAT -> 2
SINGLE, PSRSP -> 1
other or missing -> 0
```

Add a checksum function:

```kotlin
private fun nmea(sentenceBody: String): String {
    val checksum = sentenceBody.fold(0) { acc, char -> acc xor char.code }
    return "\$$sentenceBody*%02X\r\n".format(java.util.Locale.US, checksum)
}
```

- [ ] **Step 4: Feed generated NMEA from binary telemetry**

In `RecordingForegroundService`, when `parseBestnavb()` returns telemetry and `exportNmea` is true, append generated NMEA to `receiver-solution.nmea` and increment `nmeaBytes`.

- [ ] **Step 5: Run tests**

Run:

```bash
./gradlew :receiver:unicore-n4:test --tests org.rtkcollector.receiver.unicore.Um980NmeaExporterTest
./gradlew :app:compileDebugKotlin
```

Expected: both PASS.

- [ ] **Step 6: Commit**

```bash
git add receiver/unicore-n4/src/main/kotlin/org/rtkcollector/receiver/unicore/Um980NmeaExporter.kt receiver/unicore-n4/src/test/kotlin/org/rtkcollector/receiver/unicore/Um980NmeaExporterTest.kt app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt
git commit -m "Export NMEA from UM980 binary telemetry"
```

---

### Task 11: Service Telemetry Mapping And NTRIP Metadata

**Files:**

- Modify: `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingServiceState.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardServiceMapper.kt`
- Modify: `app/src/test/kotlin/org/rtkcollector/app/ui/dashboard/DashboardServiceMapperTest.kt`

- [ ] **Step 1: Add failing mapping tests**

Tests must assert:

- `satellitesTracked=31` and `satellitesUsed=18` display as `18 / 31`.
- `latErrorM=0.008` and `lonErrorM=0.007` display as `8 mm` and `7 mm`.
- `pdop=1.2`, `hdop=0.7`, `vdop=1.0` map to separate dashboard fields.
- NTRIP URL, station ID, base lat/lon, transferred bytes and rates map from service state.

- [ ] **Step 2: Run mapper tests to verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.ui.dashboard.DashboardServiceMapperTest`

Expected: FAIL on missing fields or stale formatting.

- [ ] **Step 3: Update service state and mapper**

Add state fields:

```kotlin
val ntripInputBytesPerSecond: Double = 0.0,
val ntripOutputBytesPerSecond: Double = 0.0,
val ntripStationId: String = "n/a",
val ntripBaseLatLon: String = "n/a",
val satellitesTracked: Int? = null,
val satellitesUsed: Int? = null,
val latErrorM: Double? = null,
val lonErrorM: Double? = null,
```

In `withUm980Telemetry`, map `latErrorM` to latitude error, `lonErrorM` to longitude error, `heightErrorM` to vertical accuracy, `pdop/hdop/vdop` to DOP fields, and `stationId` from BESTNAVB when present. Keep parser errors isolated in advisory fanout.

- [ ] **Step 4: Add byte-rate sampling**

In the NTRIP snapshot or service loop, track previous correction and TX byte counts with elapsed monotonic time. Update `ntripRates` as `in / out`, using existing formatter conventions.

- [ ] **Step 5: Run app tests**

Run: `./gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.ui.dashboard.DashboardServiceMapperTest`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/recording/RecordingServiceState.kt app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardServiceMapper.kt app/src/test/kotlin/org/rtkcollector/app/ui/dashboard/DashboardServiceMapperTest.kt
git commit -m "Map UM980 and NTRIP telemetry to dashboard"
```

---

### Task 12: NTRIP Sourcetable, Mountpoint Editing And Live Switching

**Files:**

- Modify: `core/correction/src/main/kotlin/org/rtkcollector/core/correction/NtripClient.kt`
- Modify: `core/correction/src/test/kotlin/org/rtkcollector/core/correction/NtripClientTest.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileEditorModels.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileScreens.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`

- [ ] **Step 1: Add NTRIP client tests**

Add tests asserting:

- NTRIP v2 request contains `Ntrip-Version: Ntrip/2.0`.
- `401` and `403` produce `AUTH_ERROR`/terminal auth state and do not reconnect in a tight loop.
- Sourcetable parsing extracts mountpoint names from `STR;...` rows and does not replace a typed mountpoint unless the user selects one.

- [ ] **Step 2: Run tests to verify gaps**

Run: `./gradlew :core:correction:test --tests org.rtkcollector.core.correction.NtripClientTest`

Expected: FAIL only where the current implementation lacks explicit guarantees.

- [ ] **Step 3: Fix NTRIP client behavior**

Keep V2-preferred behavior. For auth errors, return terminal state and expose the message to the service; do not sleep/retry indefinitely on 401/403. Network errors must reconnect according to the reconnect policy.

- [ ] **Step 4: Make mountpoint editable and selectable**

In the Compose mountpoint screen:

- keep an `OutlinedTextField` for direct typing;
- show fetched mountpoints as selectable rows;
- never overwrite typed `mountpointText` when fetched list arrives;
- only change it from user selection or user typing.

- [ ] **Step 5: Wire live switch**

When recording and the NTRIP tile mountpoint is tapped, show the mountpoint picker for the active caster. On apply, call `RecordingForegroundService.ACTION_UPDATE_NTRIP` with host, port, typed mountpoint, username, password from `NtripSecretStore`, and secret ref. Do not change other profile fields during recording.

- [ ] **Step 6: Run tests and compile**

Run:

```bash
./gradlew :core:correction:test --tests org.rtkcollector.core.correction.NtripClientTest
./gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.ui.profiles.NtripMountpointSelectionTest
./gradlew :app:compileDebugKotlin
```

Expected: all PASS.

- [ ] **Step 7: Commit**

```bash
git add core/correction/src/main/kotlin/org/rtkcollector/core/correction/NtripClient.kt core/correction/src/test/kotlin/org/rtkcollector/core/correction/NtripClientTest.kt app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileEditorModels.kt app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileScreens.kt app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt
git commit -m "Support editable NTRIP mountpoints"
```

---

### Task 13: On-Demand ZIP And Share Actions

**Files:**

- Create: `app/src/main/kotlin/org/rtkcollector/app/recording/SessionZipExporter.kt`
- Create: `app/src/test/kotlin/org/rtkcollector/app/recording/SessionZipExporterTest.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/share/SessionShareModels.kt`
- Modify: `app/src/test/kotlin/org/rtkcollector/app/share/SessionShareModelsTest.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/sessions/SessionsScreen.kt`

- [ ] **Step 1: Write failing ZIP exporter tests**

Tests must create a temporary session folder with `session.json`, `receiver-rx.raw`, `tx-to-receiver.raw`, `correction-input.rtcm3`, `receiver-solution.nmea`, `events.jsonl` and assert:

- Full session preset includes all files.
- Processing bundle includes raw receiver, TX/corrections, metadata and generated NMEA.
- ZIP output exists outside the source session folder.
- Source files still exist and have the same bytes after ZIP creation.
- Progress callback reaches total bytes.

- [ ] **Step 2: Run tests to verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.recording.SessionZipExporterTest`

Expected: FAIL because exporter does not exist.

- [ ] **Step 3: Implement ZIP exporter**

Implement path-based ZIP for app-private sessions:

```kotlin
data class ZipProgress(val bytesWritten: Long, val totalBytes: Long, val currentFile: String?)

enum class SessionZipPreset { FULL_SESSION, PROCESSING_BUNDLE, DIAGNOSTICS_BUNDLE }

class SessionZipExporter {
    fun createZip(
        sessionDirectory: java.nio.file.Path,
        outputZip: java.nio.file.Path,
        preset: SessionZipPreset,
        progress: (ZipProgress) -> Unit,
    ): java.nio.file.Path
}
```

Use `ZipOutputStream`, never write inside the session directory, and check `Thread.currentThread().isInterrupted` between files.

- [ ] **Step 4: Wire session actions**

In `SessionsScreen`, add actions after stop:

- copy folder path;
- copy file path;
- share selected file;
- create/share ZIP with progress dialog.

While recording, ZIP/share remains disabled and the UI explains `Available after stop`.

- [ ] **Step 5: Run tests and compile**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.recording.SessionZipExporterTest
./gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.share.SessionShareModelsTest
./gradlew :app:compileDebugKotlin
```

Expected: all PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/recording/SessionZipExporter.kt app/src/test/kotlin/org/rtkcollector/app/recording/SessionZipExporterTest.kt app/src/main/kotlin/org/rtkcollector/app/share/SessionShareModels.kt app/src/test/kotlin/org/rtkcollector/app/share/SessionShareModelsTest.kt app/src/main/kotlin/org/rtkcollector/app/ui/sessions/SessionsScreen.kt
git commit -m "Add on-demand session ZIP sharing"
```

---

### Task 14: Documentation And Final Validation

**Files:**

- Modify: `README.md`
- Modify: `docs/user-workflows.md`
- Modify: `docs/session-format.md`
- Modify: `docs/ntrip-and-corrections.md`
- Modify: `docs/testing-plan.md`

- [ ] **Step 1: Update user docs**

Document:

- settings sets versus reusable profiles;
- local overrides and `+ local changes`;
- dashboard tile meanings;
- UM980 default binary profile;
- NTRIP mountpoint typing/selection and live switching;
- ZIP creation only after recording stops;
- NMEA export generated from decoded receiver solution when binary profile is used;
- no maps, shapefiles, GIS editing or field-feature collection.

- [ ] **Step 2: Run full JVM tests**

Run: `./gradlew test`

Expected: PASS.

- [ ] **Step 3: Run debug Kotlin compile**

Run: `./gradlew :app:compileDebugKotlin`

Expected: PASS.

- [ ] **Step 4: Run full Android packaging where supported**

On Windows Android Studio or CI with Android SDK 36 native build tools:

Run: `./gradlew assembleDebug`

Expected: PASS and APK generated at `app/build/outputs/apk/debug/app-debug.apk`.

On Termux, if Android 36 resource linking fails due local native tooling, report the failure as environment-limited and include the successful JVM/compile evidence.

- [ ] **Step 5: Commit docs**

```bash
git add README.md docs/user-workflows.md docs/session-format.md docs/ntrip-and-corrections.md docs/testing-plan.md
git commit -m "Document V1 field workflow usage"
```

- [ ] **Step 6: Final review and push**

Use `superpowers:requesting-code-review`, then apply `review-and-commit` for final repository checks. Push `main` after tests are green or after explicitly documenting a Termux-only packaging limitation.

---

## Self-Review Notes

Spec coverage:

- Settings sets are covered by Tasks 1-5 and Task 14.
- Profile CRUD and real profile screens are covered by Tasks 4-5.
- Compose start replacing the legacy Activity is covered by Task 6.
- Fixed start/stop/menu and dense dashboard profile tile are covered by Task 7.
- UM980 binary profile and baud transition are covered by Task 8.
- BESTNAVB/STADOPB telemetry and generated NMEA are covered by Tasks 9-10.
- NTRIP mountpoint typing, sourcetable selection, auth handling and live switching are covered by Task 12.
- On-demand ZIP/session sharing is covered by Task 13.
- Raw capture authority remains preserved because all tasks keep the service capture path and session writers as the source of truth and keep parser/NTRIP/ZIP failures advisory.

Validation strategy:

- Every non-trivial model gets JVM tests before implementation.
- Receiver binary parsing is tested in `:receiver:unicore-n4`.
- Dashboard formatting/mapping is tested in `:app:testDebugUnitTest`.
- Full local validation is `./gradlew test` plus `./gradlew :app:compileDebugKotlin`.
- Full APK packaging is validated on Windows/CI with Android SDK 36 because this Termux environment may not package Android resources reliably.
