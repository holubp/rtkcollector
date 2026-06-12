# Android JSON Settings Import Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let Android offer RtkCollector when a user opens or shares a JSON settings backup, then import only after validated confirmation.

**Architecture:** Add testable import-domain helpers for intent URI extraction and backup validation/summary. Wire those helpers into `MainActivity` with pending-import UI state and manifest filters; reuse the existing settings import writer only after the user taps `Import`.

**Tech Stack:** Kotlin, Android intents, Jetpack Compose, `org.json`, existing `SettingsBackupFile`, JUnit tests.

---

## Current Worktree Note

The worktree already contains uncommitted clipboard-copy UI changes in `MainActivity` and dashboard/session files. Do not revert them. When editing `MainActivity`, preserve the existing clipboard callback changes and add the settings-import flow around them.

## Files

- Create `app/src/main/kotlin/org/rtkcollector/app/profile/SettingsImportModels.kt`
  - `SettingsImportSummary`, `SettingsImportValidationResult`, `validateSettingsImportJson`, `readSettingsImportText`.
- Create `app/src/test/kotlin/org/rtkcollector/app/profile/SettingsImportModelsTest.kt`
  - Unit tests for validation, password detection and oversized input.
- Create `app/src/main/kotlin/org/rtkcollector/app/ui/imports/SettingsImportIntentReader.kt`
  - `settingsImportUriFromIntent(intent: Intent): Uri?`.
- Create `app/src/test/kotlin/org/rtkcollector/app/ui/imports/SettingsImportIntentReaderTest.kt`
  - Unit tests for `ACTION_VIEW`, `ACTION_SEND`, ignored launcher and missing URI.
- Create `app/src/main/kotlin/org/rtkcollector/app/ui/imports/SettingsImportScreen.kt`
  - Compose confirmation/error screen.
- Modify `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`
  - Hold latest external intent state, parse pending imports, show confirmation, import on confirmation.
- Modify `app/src/main/AndroidManifest.xml`
  - Add JSON `ACTION_VIEW` and `ACTION_SEND` filters to `.ui.MainActivity`.
- Create `app/src/test/kotlin/org/rtkcollector/app/ui/AndroidManifestJsonImportTest.kt`
  - Source-level XML inspection that JSON view/send filters exist.
- Modify `docs/superpowers/specs/2026-06-12-android-json-settings-import-design.md`
  - Clarify password persistence wording.

---

### Task 1: Validation And Summary Model

**Files:**
- Create: `app/src/main/kotlin/org/rtkcollector/app/profile/SettingsImportModels.kt`
- Create: `app/src/test/kotlin/org/rtkcollector/app/profile/SettingsImportModelsTest.kt`

- [ ] **Step 1: Write failing tests**

Create `SettingsImportModelsTest.kt`:

```kotlin
package org.rtkcollector.app.profile

import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsImportModelsTest {
    @Test
    fun `valid backup produces summary counts and password warning`() {
        val backup = sampleBackup(includePassword = true)

        val result = validateSettingsImportJson(backup.toJson().toString())

        assertTrue(result is SettingsImportValidationResult.Valid)
        assertEquals(1, result.summary.commandProfileCount)
        assertEquals(1, result.summary.usbBaudProfileCount)
        assertEquals(1, result.summary.ntripCasterProfileCount)
        assertEquals(1, result.summary.ntripMountpointProfileCount)
        assertEquals(1, result.summary.recordingPolicyProfileCount)
        assertEquals(1, result.summary.storageProfileCount)
        assertEquals(1, result.summary.settingsSetCount)
        assertTrue(result.summary.containsPlaintextPasswords)
        assertEquals("settings", result.backup.selectedSettingsSetId)
    }

    @Test
    fun `valid backup without passwords has no password warning`() {
        val result = validateSettingsImportJson(sampleBackup(includePassword = false).toJson().toString())

        assertTrue(result is SettingsImportValidationResult.Valid)
        assertFalse(result.summary.containsPlaintextPasswords)
    }

    @Test
    fun `invalid json is rejected`() {
        val result = validateSettingsImportJson("{not-json")

        assertTrue(result is SettingsImportValidationResult.Invalid)
        assertEquals("This JSON file is not a RtkCollector settings backup.", result.message)
    }

    @Test
    fun `missing required array is rejected`() {
        val json = sampleBackup(includePassword = false).toJson()
        json.remove("commandProfiles")

        val result = validateSettingsImportJson(json.toString())

        assertTrue(result is SettingsImportValidationResult.Invalid)
        assertEquals("Settings backup is missing commandProfiles.", result.message)
    }

    @Test
    fun `unsupported version is rejected`() {
        val json = sampleBackup(includePassword = false).toJson().put("formatVersion", 999)

        val result = validateSettingsImportJson(json.toString())

        assertTrue(result is SettingsImportValidationResult.Invalid)
        assertEquals("Unsupported settings backup format version.", result.message)
    }

    @Test
    fun `non string plaintext password is rejected`() {
        val json = sampleBackup(includePassword = false).toJson()
        json.put("plaintextPasswords", JSONObject().put("secret", 12))

        val result = validateSettingsImportJson(json.toString())

        assertTrue(result is SettingsImportValidationResult.Invalid)
        assertEquals("Settings backup contains invalid NTRIP password data.", result.message)
    }

    @Test
    fun `oversized backup text is rejected before parsing`() {
        val text = " ".repeat(MAX_SETTINGS_IMPORT_BYTES + 1)

        val result = validateSettingsImportJson(text)

        assertTrue(result is SettingsImportValidationResult.Invalid)
        assertEquals("Settings backup is too large.", result.message)
    }

    private fun sampleBackup(includePassword: Boolean): SettingsBackupFile =
        SettingsBackupFile.fromProfiles(
            commandProfiles = listOf(CommandProfile(id = "command", name = "Command")),
            usbBaudProfiles = listOf(UsbBaudProfile(id = "usb", name = "USB")),
            ntripCasterProfiles = listOf(NtripCasterProfile(id = "caster", name = "Caster", secretId = "secret")),
            ntripMountpointProfiles = listOf(NtripMountpointProfile(id = "mount", name = "Mount", casterProfileId = "caster")),
            recordingPolicyProfiles = listOf(RecordingPolicyProfile(id = "policy", name = "Policy")),
            storageProfiles = listOf(StorageProfile(id = "storage", name = "Storage")),
            settingsSets = listOf(RecordingSettingsSet.builtInRoverNtrip()),
            selectedSettingsSetId = "settings",
            selectedWorkflowId = "rover-ntrip",
            lastActiveNtripMountpointProfileId = "mount",
            passwordsBySecretId = if (includePassword) mapOf("secret" to "secret-password") else emptyMap(),
            options = SettingsSetExportOptions(includePlaintextPasswords = includePassword),
        )
}
```

- [ ] **Step 2: Verify RED**

Run:

```bash
sh gradlew :app:compileDebugUnitTestKotlin
```

Expected: fails because `validateSettingsImportJson`, `SettingsImportValidationResult` and `MAX_SETTINGS_IMPORT_BYTES` do not exist. If Termux hits `aapt2` first, record the blocker and proceed with `:app:compileDebugKotlin` as the feasible compile check after implementation.

- [ ] **Step 3: Implement validation model**

Create `SettingsImportModels.kt`:

```kotlin
package org.rtkcollector.app.profile

import android.content.ContentResolver
import android.net.Uri
import org.json.JSONException
import org.json.JSONObject

const val MAX_SETTINGS_IMPORT_BYTES: Int = 2 * 1024 * 1024

data class SettingsImportSummary(
    val commandProfileCount: Int,
    val usbBaudProfileCount: Int,
    val ntripCasterProfileCount: Int,
    val ntripMountpointProfileCount: Int,
    val recordingPolicyProfileCount: Int,
    val storageProfileCount: Int,
    val settingsSetCount: Int,
    val selectedSettingsSetId: String?,
    val selectedWorkflowId: String?,
    val lastActiveNtripMountpointProfileId: String?,
    val containsPlaintextPasswords: Boolean,
)

sealed class SettingsImportValidationResult {
    data class Valid(
        val backup: SettingsBackupFile,
        val summary: SettingsImportSummary,
    ) : SettingsImportValidationResult()

    data class Invalid(val message: String) : SettingsImportValidationResult()
}

fun validateSettingsImportJson(text: String): SettingsImportValidationResult {
    if (text.toByteArray(Charsets.UTF_8).size > MAX_SETTINGS_IMPORT_BYTES) {
        return SettingsImportValidationResult.Invalid("Settings backup is too large.")
    }
    val json = try {
        JSONObject(text)
    } catch (_: JSONException) {
        return SettingsImportValidationResult.Invalid("This JSON file is not a RtkCollector settings backup.")
    }
    if (json.optInt("formatVersion", 0) != SettingsBackupFile.CURRENT_FORMAT_VERSION) {
        return SettingsImportValidationResult.Invalid("Unsupported settings backup format version.")
    }
    val requiredArrays = listOf(
        "commandProfiles",
        "usbBaudProfiles",
        "ntripCasterProfiles",
        "ntripMountpointProfiles",
        "recordingPolicyProfiles",
        "storageProfiles",
        "settingsSets",
    )
    requiredArrays.forEach { key ->
        if (!json.has(key) || json.optJSONArray(key) == null) {
            return SettingsImportValidationResult.Invalid("Settings backup is missing $key.")
        }
    }
    json.optJSONObject("plaintextPasswords")?.let { passwords ->
        val keys = passwords.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (passwords.optString(key, null) == null) {
                return SettingsImportValidationResult.Invalid("Settings backup contains invalid NTRIP password data.")
            }
        }
    }
    val backup = runCatching { SettingsBackupFile.fromJson(json) }
        .getOrElse { return SettingsImportValidationResult.Invalid(it.message ?: "This JSON file is not a RtkCollector settings backup.") }
    return SettingsImportValidationResult.Valid(
        backup = backup,
        summary = SettingsImportSummary(
            commandProfileCount = backup.commandProfiles.size,
            usbBaudProfileCount = backup.usbBaudProfiles.size,
            ntripCasterProfileCount = backup.ntripCasterProfiles.size,
            ntripMountpointProfileCount = backup.ntripMountpointProfiles.size,
            recordingPolicyProfileCount = backup.recordingPolicyProfiles.size,
            storageProfileCount = backup.storageProfiles.size,
            settingsSetCount = backup.settingsSets.size,
            selectedSettingsSetId = backup.selectedSettingsSetId,
            selectedWorkflowId = backup.selectedWorkflowId,
            lastActiveNtripMountpointProfileId = backup.lastActiveNtripMountpointProfileId,
            containsPlaintextPasswords = backup.plaintextPasswordsBySecretId.isNotEmpty(),
        ),
    )
}

fun readSettingsImportText(
    resolver: ContentResolver,
    uri: Uri,
    maxBytes: Int = MAX_SETTINGS_IMPORT_BYTES,
): String {
    val bytes = resolver.openInputStream(uri)?.use { input ->
        val buffer = ByteArray(maxBytes + 1)
        val count = input.read(buffer)
        if (count > maxBytes) error("Settings backup is too large.")
        buffer.copyOf(count.coerceAtLeast(0))
    } ?: error("Settings backup could not be read.")
    return bytes.toString(Charsets.UTF_8)
}
```

- [ ] **Step 4: Verify GREEN for model compile**

Run:

```bash
sh gradlew :app:compileDebugKotlin
```

Expected: production compile passes.

---

### Task 2: Intent Reader And Manifest Registration

**Files:**
- Create: `app/src/main/kotlin/org/rtkcollector/app/ui/imports/SettingsImportIntentReader.kt`
- Create: `app/src/test/kotlin/org/rtkcollector/app/ui/imports/SettingsImportIntentReaderTest.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/test/kotlin/org/rtkcollector/app/ui/AndroidManifestJsonImportTest.kt`

- [ ] **Step 1: Write failing tests**

Create `SettingsImportIntentReaderTest.kt`:

```kotlin
package org.rtkcollector.app.ui.imports

import android.content.Intent
import android.net.Uri
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SettingsImportIntentReaderTest {
    @Test
    fun `extracts action view data uri`() {
        val uri = Uri.parse("content://example/settings.json")
        val intent = Intent(Intent.ACTION_VIEW, uri).setType("application/json")

        assertEquals(uri, settingsImportUriFromIntent(intent))
    }

    @Test
    fun `extracts action send stream uri`() {
        val uri = Uri.parse("content://example/settings.json")
        val intent = Intent(Intent.ACTION_SEND)
            .setType("application/json")
            .putExtra(Intent.EXTRA_STREAM, uri)

        assertEquals(uri, settingsImportUriFromIntent(intent))
    }

    @Test
    fun `ignores launcher intent`() {
        assertNull(settingsImportUriFromIntent(Intent(Intent.ACTION_MAIN)))
    }

    @Test
    fun `ignores action send without stream`() {
        assertNull(settingsImportUriFromIntent(Intent(Intent.ACTION_SEND).setType("application/json")))
    }
}
```

Create `AndroidManifestJsonImportTest.kt`:

```kotlin
package org.rtkcollector.app.ui

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class AndroidManifestJsonImportTest {
    @Test
    fun `manifest registers json view and send import filters`() {
        val manifest = Files.readString(sourceFile("src/main/AndroidManifest.xml"))

        assertTrue(manifest.contains("android.intent.action.VIEW"))
        assertTrue(manifest.contains("android.intent.action.SEND"))
        assertTrue(manifest.contains("android:mimeType=\"application/json\""))
        assertTrue(manifest.contains("android:mimeType=\"text/json\""))
        assertTrue(manifest.contains("android:mimeType=\"text/plain\""))
        assertTrue(manifest.contains("android:scheme=\"content\""))
    }

    private fun sourceFile(relative: String): Path {
        val candidates = listOf(Path.of(relative), Path.of("app").resolve(relative))
        return candidates.firstOrNull(Files::exists)
            ?: error("Cannot locate source file $relative from ${Path.of("").toAbsolutePath()}")
    }
}
```

- [ ] **Step 2: Verify RED**

Run:

```bash
sh gradlew :app:compileDebugUnitTestKotlin
```

Expected: fails because `settingsImportUriFromIntent` does not exist.

- [ ] **Step 3: Implement intent reader**

Create `SettingsImportIntentReader.kt`:

```kotlin
package org.rtkcollector.app.ui.imports

import android.content.Intent
import android.net.Uri

fun settingsImportUriFromIntent(intent: Intent?): Uri? {
    if (intent == null) return null
    return when (intent.action) {
        Intent.ACTION_VIEW -> intent.data
        Intent.ACTION_SEND -> intent.getParcelableExtra(Intent.EXTRA_STREAM)
        else -> null
    }
}
```

- [ ] **Step 4: Register manifest filters**

Add these intent filters inside `.ui.MainActivity`:

```xml
<intent-filter>
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data android:scheme="content" />
    <data android:scheme="file" />
    <data android:mimeType="application/json" />
    <data android:mimeType="text/json" />
    <data android:mimeType="text/plain" />
</intent-filter>
<intent-filter>
    <action android:name="android.intent.action.SEND" />
    <category android:name="android.intent.category.DEFAULT" />
    <data android:mimeType="application/json" />
    <data android:mimeType="text/json" />
    <data android:mimeType="text/plain" />
</intent-filter>
```

- [ ] **Step 5: Verify production compile**

Run:

```bash
sh gradlew :app:compileDebugKotlin
```

Expected: PASS.

---

### Task 3: Confirmation UI And MainActivity Integration

**Files:**
- Create: `app/src/main/kotlin/org/rtkcollector/app/ui/imports/SettingsImportScreen.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`

- [ ] **Step 1: Create confirmation screen**

Create `SettingsImportScreen.kt`:

```kotlin
package org.rtkcollector.app.ui.imports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.rtkcollector.app.profile.SettingsImportSummary
import org.rtkcollector.app.profile.SettingsImportValidationResult

@Composable
fun SettingsImportScreen(
    source: String,
    result: SettingsImportValidationResult,
    recordingActive: Boolean,
    onImport: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Import settings backup", style = MaterialTheme.typography.headlineSmall)
        Text(source, style = MaterialTheme.typography.bodySmall)
        when (result) {
            is SettingsImportValidationResult.Valid -> {
                Summary(result.summary)
                if (result.summary.containsPlaintextPasswords) {
                    Warning("This backup contains plaintext NTRIP passwords. Import only if you trust the source.")
                }
                if (recordingActive) {
                    Warning("Stop recording before importing settings.")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onImport, enabled = !recordingActive) { Text("Import") }
                    OutlinedButton(onClick = onCancel) { Text("Cancel") }
                }
            }
            is SettingsImportValidationResult.Invalid -> {
                Warning(result.message)
                OutlinedButton(onClick = onCancel) { Text("Dismiss") }
            }
        }
    }
}

@Composable
private fun Summary(summary: SettingsImportSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Command profiles: ${summary.commandProfileCount}")
        Text("USB/baud profiles: ${summary.usbBaudProfileCount}")
        Text("NTRIP casters: ${summary.ntripCasterProfileCount}")
        Text("NTRIP mountpoints: ${summary.ntripMountpointProfileCount}")
        Text("Recording outputs: ${summary.recordingPolicyProfileCount}")
        Text("Storage locations: ${summary.storageProfileCount}")
        Text("Settings sets: ${summary.settingsSetCount}")
        Text("Selected settings: ${summary.selectedSettingsSetId ?: "n/a"}")
        Text("Selected workflow: ${summary.selectedWorkflowId ?: "n/a"}")
        Text("Last mountpoint: ${summary.lastActiveNtripMountpointProfileId ?: "n/a"}")
    }
}

@Composable
private fun Warning(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFFFFEBEE),
        contentColor = Color(0xFF7F1D1D),
        shape = MaterialTheme.shapes.small,
    ) {
        Text(text, modifier = Modifier.padding(10.dp), style = MaterialTheme.typography.bodyMedium)
    }
}
```

- [ ] **Step 2: Integrate pending import state**

In `MainActivity.kt`:

- Add imports for `SettingsImportValidationResult`, `readSettingsImportText`, `validateSettingsImportJson`, `SettingsImportScreen`, and `settingsImportUriFromIntent`.
- In `MainActivity`, hold an Activity-level Compose state:

```kotlin
private var latestImportIntent by mutableStateOf<Intent?>(null)
```

- In `onCreate`, set `latestImportIntent = intent` before `setContent`.
- Override `onNewIntent(intent: Intent)` and update `latestImportIntent`.
- Change `RtkCollectorApp()` to `RtkCollectorApp(externalIntent = latestImportIntent, onExternalIntentConsumed = { latestImportIntent = null })`.
- Add `externalIntent: Intent? = null` and `onExternalIntentConsumed: () -> Unit = {}` parameters to `RtkCollectorApp`.
- Define:

```kotlin
private data class PendingSettingsImport(
    val source: String,
    val result: SettingsImportValidationResult,
)
```

- Inside `RtkCollectorApp`, add:

```kotlin
var pendingSettingsImport by remember { mutableStateOf<PendingSettingsImport?>(null) }
```

- Use `DisposableEffect(externalIntent)` to extract URI, read capped text, validate it and set `pendingSettingsImport`.
- In the screen switch, if `pendingSettingsImport != null`, render `SettingsImportScreen` before normal `when (screen)`.
- On Import, call a new overload:

```kotlin
private fun importSettingsBackup(context: Context, backup: SettingsBackupFile)
```

and let the existing URI-based importer read/validate and delegate to this overload.

- [ ] **Step 3: Verify production compile**

Run:

```bash
sh gradlew :app:compileDebugKotlin
```

Expected: PASS.

---

## Final Verification

- [ ] Run:

```bash
git diff --check
```

Expected: no output.

- [ ] Run:

```bash
sh gradlew :app:compileDebugKotlin
```

Expected: PASS.

- [ ] Run targeted tests where the environment supports Android test resources:

```bash
sh gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.profile.SettingsImportModelsTest --tests org.rtkcollector.app.ui.imports.SettingsImportIntentReaderTest --tests org.rtkcollector.app.ui.AndroidManifestJsonImportTest
```

Expected on a normal Android SDK host: PASS. On this Termux environment, `aapt2` may fail before tests execute; report that exact blocker.

## Self-Review

Spec coverage:

- Android JSON registration: Task 2.
- Explicit confirmation and no auto-import: Task 3.
- Structural validation and password warnings: Task 1 and Task 3.
- Reuse existing import writer only after confirmation: Task 3.
- Reject import while recording: Task 3.

Placeholder scan:

- No placeholder markers or unspecified implementation placeholders are used.

Type consistency:

- `validateSettingsImportJson` returns `SettingsImportValidationResult`.
- `SettingsImportScreen` consumes the same validation result.
- `settingsImportUriFromIntent` is the only intent extraction helper.
