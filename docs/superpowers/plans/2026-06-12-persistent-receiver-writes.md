# Persistent Receiver Writes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make UM980 persistent receiver writes work through the active recording service when recording, through a verified maintenance USB connection when idle, and add a USB/baud action that persists the target baud with `SAVECONFIG`.

**Architecture:** Move persistent receiver command construction out of `MainActivity` into a small testable model. Add a foreground-service action for active-recording persistent writes so commands use the existing TX path and are recorded in `tx-to-receiver.raw`. Keep idle writes as a maintenance USB path that opens at the selected initial baud, verifies UM980 communication, and only then sends persistent commands.

**Tech Stack:** Kotlin, Android foreground service, Android USB host API, existing `AndroidUsbSerialTransport`, existing Compose profile editor actions, JUnit tests.

---

## File Structure

- Create `app/src/main/kotlin/org/rtkcollector/app/receiver/PersistentReceiverCommands.kt`
  - Owns persistent command builders and supported baud validation.
- Create `app/src/test/kotlin/org/rtkcollector/app/receiver/PersistentReceiverCommandsTest.kt`
  - Replaces the current UI-package tests for persistent command construction.
- Modify `app/src/test/kotlin/org/rtkcollector/app/ui/PersistentReceiverCommandsTest.kt`
  - Delete this file after moving equivalent tests to the receiver package.
- Create `app/src/main/kotlin/org/rtkcollector/app/receiver/PersistentReceiverWritePolicy.kt`
  - Pure route decision for active service path vs idle maintenance path.
- Create `app/src/test/kotlin/org/rtkcollector/app/receiver/PersistentReceiverWritePolicyTest.kt`
  - Tests route decisions and user-facing rejection messages.
- Create `app/src/main/kotlin/org/rtkcollector/app/receiver/Um980MaintenanceConnectionCheck.kt`
  - Small helper for benign `VERSION` probe and response classification.
- Create `app/src/test/kotlin/org/rtkcollector/app/receiver/Um980MaintenanceConnectionCheckTest.kt`
  - Tests probe command and plausible/implausible response handling.
- Modify `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt`
  - Add active-recording persistent write service action and baud persistence handling.
- Modify `app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileEditorModels.kt`
  - Add USB/baud persistent action model and update warning text.
- Modify `app/src/test/kotlin/org/rtkcollector/app/ui/profiles/ProfileEditorModelsTest.kt`
  - Add tests for command and USB/baud action labels/warnings.
- Modify `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`
  - Route persistent writes to service when recording and maintenance USB when idle.
  - Add USB/baud editor persistent target-baud action.
- Modify `docs/user-workflows.md`
  - Document the active-recording and idle maintenance persistent write paths.

---

### Task 1: Move Persistent Command Builders To Receiver Package

**Files:**
- Create: `app/src/main/kotlin/org/rtkcollector/app/receiver/PersistentReceiverCommands.kt`
- Create: `app/src/test/kotlin/org/rtkcollector/app/receiver/PersistentReceiverCommandsTest.kt`
- Modify: `app/src/test/kotlin/org/rtkcollector/app/ui/PersistentReceiverCommandsTest.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`

- [ ] **Step 1: Write the receiver-package tests**

Create `app/src/test/kotlin/org/rtkcollector/app/receiver/PersistentReceiverCommandsTest.kt`:

```kotlin
package org.rtkcollector.app.receiver

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class PersistentReceiverCommandsTest {
    @Test
    fun `persistent receiver commands strip comments and append saveconfig once`() {
        val commands = persistentReceiverCommands(
            """
            # comment
            MODE ROVER SURVEY

            SAVECONFIG
            BESTNAVB COM1 1
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                "MODE ROVER SURVEY",
                "BESTNAVB COM1 1",
                "SAVECONFIG",
            ),
            commands,
        )
    }

    @Test
    fun `persistent receiver commands reject risky commands except final appended saveconfig`() {
        assertThrows(IllegalArgumentException::class.java) {
            persistentReceiverCommands(
                """
                MODE ROVER SURVEY
                RESET
                """.trimIndent(),
            )
        }
    }

    @Test
    fun `persistent receiver commands reject save variants in script body`() {
        assertThrows(IllegalArgumentException::class.java) {
            persistentReceiverCommands(
                """
                MODE ROVER SURVEY
                SAVE
                """.trimIndent(),
            )
        }
    }

    @Test
    fun `persistent baud commands configure com1 target then saveconfig`() {
        assertEquals(
            listOf("CONFIG COM1 460800", "SAVECONFIG"),
            persistentBaudCommands(460800),
        )
    }

    @Test
    fun `persistent baud commands reject unsupported baud`() {
        assertThrows(IllegalArgumentException::class.java) {
            persistentBaudCommands(123456)
        }
    }
}
```

- [ ] **Step 2: Run the moved tests to verify RED**

Run:

```bash
sh gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.receiver.PersistentReceiverCommandsTest
```

Expected on a normal Android SDK host: compile failure because `persistentReceiverCommands` and `persistentBaudCommands` do not exist in `org.rtkcollector.app.receiver`.

Expected in this Termux environment: the command may stop before test execution at Maven `aapt2`; if so, record the `aapt2` blocker and use `sh gradlew :app:compileDebugKotlin` after implementation.

- [ ] **Step 3: Implement the command builders**

Create `app/src/main/kotlin/org/rtkcollector/app/receiver/PersistentReceiverCommands.kt`:

```kotlin
package org.rtkcollector.app.receiver

import org.rtkcollector.receiver.unicore.Um980RuntimeCommandValidator

val SupportedPersistentBaudRates: Set<Int> = setOf(
    4800,
    9600,
    14400,
    19200,
    38400,
    57600,
    115200,
    128000,
    230400,
    256000,
    460800,
    921600,
)

fun persistentReceiverCommands(initScript: String): List<String> =
    initScript
        .lineSequence()
        .map(String::trim)
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .filterNot { it.equals("SAVECONFIG", ignoreCase = true) }
        .onEach(Um980RuntimeCommandValidator::validateRuntimeCommand)
        .toList() + "SAVECONFIG"

fun persistentBaudCommands(targetBaud: Int): List<String> {
    require(targetBaud in SupportedPersistentBaudRates) {
        "Target baud must be one of ${SupportedPersistentBaudRates.sorted().joinToString()}."
    }
    return listOf("CONFIG COM1 $targetBaud", "SAVECONFIG")
}
```

- [ ] **Step 4: Update `MainActivity` imports and remove the old helper**

In `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`, add:

```kotlin
import org.rtkcollector.app.receiver.persistentBaudCommands
import org.rtkcollector.app.receiver.persistentReceiverCommands
```

Delete the existing `internal fun persistentReceiverCommands(runtimeScript: String): List<String>` from `MainActivity`.

- [ ] **Step 5: Delete the obsolete UI-package command test**

Delete `app/src/test/kotlin/org/rtkcollector/app/ui/PersistentReceiverCommandsTest.kt`.

- [ ] **Step 6: Run verification**

Run:

```bash
sh gradlew :app:compileDebugKotlin
sh gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.receiver.PersistentReceiverCommandsTest
```

Expected on a normal Android SDK host: test passes.

Expected in this Termux environment: `compileDebugKotlin` passes; test command may be blocked by Maven `aapt2`.

- [ ] **Step 7: Commit Task 1**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/receiver/PersistentReceiverCommands.kt \
  app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt \
  app/src/test/kotlin/org/rtkcollector/app/receiver/PersistentReceiverCommandsTest.kt
git rm app/src/test/kotlin/org/rtkcollector/app/ui/PersistentReceiverCommandsTest.kt
git commit -m "Move persistent receiver command builders"
```

---

### Task 2: Add Pure Persistent Write Routing Policy

**Files:**
- Create: `app/src/main/kotlin/org/rtkcollector/app/receiver/PersistentReceiverWritePolicy.kt`
- Create: `app/src/test/kotlin/org/rtkcollector/app/receiver/PersistentReceiverWritePolicyTest.kt`

- [ ] **Step 1: Write the routing tests**

Create `app/src/test/kotlin/org/rtkcollector/app/receiver/PersistentReceiverWritePolicyTest.kt`:

```kotlin
package org.rtkcollector.app.receiver

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PersistentReceiverWritePolicyTest {
    @Test
    fun `active recording uses service path`() {
        assertEquals(
            PersistentReceiverWriteRoute.ActiveRecordingService,
            persistentReceiverWriteRoute(
                recordingActive = true,
                usbProfileAvailable = true,
                receiverConnected = true,
                usbPermissionGranted = true,
            ),
        )
    }

    @Test
    fun `active recording always uses service path before idle checks`() {
        assertEquals(
            PersistentReceiverWriteRoute.ActiveRecordingService,
            persistentReceiverWriteRoute(
                recordingActive = true,
                usbProfileAvailable = false,
                receiverConnected = false,
                usbPermissionGranted = false,
            ),
        )
    }

    @Test
    fun `idle with selected connected permitted usb uses maintenance path`() {
        assertEquals(
            PersistentReceiverWriteRoute.IdleMaintenanceConnection,
            persistentReceiverWriteRoute(
                recordingActive = false,
                usbProfileAvailable = true,
                receiverConnected = true,
                usbPermissionGranted = true,
            ),
        )
    }

    @Test
    fun `idle without usb profile is rejected`() {
        assertEquals(
            PersistentReceiverWriteRoute.Rejected(
                reason = PersistentReceiverWriteRejectionReason.USB_PROFILE_MISSING,
                message = "USB/baud profile is not available.",
            ),
            persistentReceiverWriteRoute(
                recordingActive = false,
                usbProfileAvailable = false,
                receiverConnected = true,
                usbPermissionGranted = true,
            ),
        )
    }

    @Test
    fun `idle without connected receiver is rejected`() {
        assertEquals(
            PersistentReceiverWriteRoute.Rejected(
                reason = PersistentReceiverWriteRejectionReason.RECEIVER_DISCONNECTED,
                message = "Selected USB receiver is not connected.",
            ),
            persistentReceiverWriteRoute(
                recordingActive = false,
                usbProfileAvailable = true,
                receiverConnected = false,
                usbPermissionGranted = true,
            ),
        )
    }

    @Test
    fun `idle without usb permission is rejected`() {
        assertEquals(
            PersistentReceiverWriteRoute.Rejected(
                reason = PersistentReceiverWriteRejectionReason.USB_PERMISSION_MISSING,
                message = "USB permission is required before writing receiver configuration.",
            ),
            persistentReceiverWriteRoute(
                recordingActive = false,
                usbProfileAvailable = true,
                receiverConnected = true,
                usbPermissionGranted = false,
            ),
        )
    }
}
```

- [ ] **Step 2: Run the routing tests to verify RED**

Run:

```bash
sh gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.receiver.PersistentReceiverWritePolicyTest
```

Expected on a normal Android SDK host: compile failure because the policy does not exist.

- [ ] **Step 3: Implement the routing model**

Create `app/src/main/kotlin/org/rtkcollector/app/receiver/PersistentReceiverWritePolicy.kt`:

```kotlin
package org.rtkcollector.app.receiver

sealed class PersistentReceiverWriteRoute {
    data object ActiveRecordingService : PersistentReceiverWriteRoute()
    data object IdleMaintenanceConnection : PersistentReceiverWriteRoute()
    data class Rejected(
        val reason: PersistentReceiverWriteRejectionReason,
        val message: String,
    ) : PersistentReceiverWriteRoute()
}

enum class PersistentReceiverWriteRejectionReason {
    USB_PROFILE_MISSING,
    RECEIVER_DISCONNECTED,
    USB_PERMISSION_MISSING,
}

fun persistentReceiverWriteRoute(
    recordingActive: Boolean,
    usbProfileAvailable: Boolean,
    receiverConnected: Boolean,
    usbPermissionGranted: Boolean,
): PersistentReceiverWriteRoute {
    if (recordingActive) return PersistentReceiverWriteRoute.ActiveRecordingService
    if (!usbProfileAvailable) {
        return PersistentReceiverWriteRoute.Rejected(
            reason = PersistentReceiverWriteRejectionReason.USB_PROFILE_MISSING,
            message = "USB/baud profile is not available.",
        )
    }
    if (!receiverConnected) {
        return PersistentReceiverWriteRoute.Rejected(
            reason = PersistentReceiverWriteRejectionReason.RECEIVER_DISCONNECTED,
            message = "Selected USB receiver is not connected.",
        )
    }
    if (!usbPermissionGranted) {
        return PersistentReceiverWriteRoute.Rejected(
            reason = PersistentReceiverWriteRejectionReason.USB_PERMISSION_MISSING,
            message = "USB permission is required before writing receiver configuration.",
        )
    }
    return PersistentReceiverWriteRoute.IdleMaintenanceConnection
}
```

- [ ] **Step 4: Run verification**

Run:

```bash
sh gradlew :app:compileDebugKotlin
sh gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.receiver.PersistentReceiverWritePolicyTest
```

Expected on a normal Android SDK host: test passes.

- [ ] **Step 5: Commit Task 2**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/receiver/PersistentReceiverWritePolicy.kt \
  app/src/test/kotlin/org/rtkcollector/app/receiver/PersistentReceiverWritePolicyTest.kt
git commit -m "Add persistent receiver write routing policy"
```

---

### Task 3: Add UM980 Maintenance Connection Check

**Files:**
- Create: `app/src/main/kotlin/org/rtkcollector/app/receiver/Um980MaintenanceConnectionCheck.kt`
- Create: `app/src/test/kotlin/org/rtkcollector/app/receiver/Um980MaintenanceConnectionCheckTest.kt`

- [ ] **Step 1: Write tests for the benign probe and response classifier**

Create `app/src/test/kotlin/org/rtkcollector/app/receiver/Um980MaintenanceConnectionCheckTest.kt`:

```kotlin
package org.rtkcollector.app.receiver

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test

class Um980MaintenanceConnectionCheckTest {
    @Test
    fun `version probe is ascii command with crlf`() {
        assertArrayEquals("VERSION\r\n".toByteArray(Charsets.US_ASCII), um980VersionProbeBytes())
    }

    @Test
    fun `classifies unicore version response as live`() {
        assertTrue(isPlausibleUm980MaintenanceResponse("UM980 firmware 11833\r\n".toByteArray(Charsets.US_ASCII)))
        assertTrue(isPlausibleUm980MaintenanceResponse("Unicore GNSS Receiver\r\n".toByteArray(Charsets.US_ASCII)))
        assertTrue(isPlausibleUm980MaintenanceResponse("#VERSIONA,COM1,0,62.0,FINESTEERING".toByteArray(Charsets.US_ASCII)))
    }

    @Test
    fun `classifies nmea streaming as live`() {
        assertTrue(isPlausibleUm980MaintenanceResponse("$GNGGA,123519,5000.0,N,01400.0,E,1,12,0.9,287.0,M,0.0,M,,*00\r\n".toByteArray(Charsets.US_ASCII)))
    }

    @Test
    fun `rejects empty and random bytes`() {
        assertFalse(isPlausibleUm980MaintenanceResponse(ByteArray(0)))
        assertFalse(isPlausibleUm980MaintenanceResponse(byteArrayOf(0x00, 0x01, 0x02, 0x03)))
    }
}
```

- [ ] **Step 2: Run the tests to verify RED**

Run:

```bash
sh gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.receiver.Um980MaintenanceConnectionCheckTest
```

Expected on a normal Android SDK host: compile failure because the helper does not exist.

- [ ] **Step 3: Implement the check helpers**

Create `app/src/main/kotlin/org/rtkcollector/app/receiver/Um980MaintenanceConnectionCheck.kt`:

```kotlin
package org.rtkcollector.app.receiver

fun um980VersionProbeBytes(): ByteArray =
    "VERSION\r\n".toByteArray(Charsets.US_ASCII)

fun isPlausibleUm980MaintenanceResponse(bytes: ByteArray): Boolean {
    if (bytes.isEmpty()) return false
    val ascii = bytes.toString(Charsets.US_ASCII)
    return ascii.contains("UM980", ignoreCase = true) ||
        ascii.contains("Unicore", ignoreCase = true) ||
        ascii.contains("#VERSION", ignoreCase = true) ||
        ascii.startsWith("$GN") ||
        ascii.startsWith("$GP")
}
```

- [ ] **Step 4: Run verification**

Run:

```bash
sh gradlew :app:compileDebugKotlin
sh gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.receiver.Um980MaintenanceConnectionCheckTest
```

Expected on a normal Android SDK host: test passes.

- [ ] **Step 5: Commit Task 3**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/receiver/Um980MaintenanceConnectionCheck.kt \
  app/src/test/kotlin/org/rtkcollector/app/receiver/Um980MaintenanceConnectionCheckTest.kt
git commit -m "Add UM980 maintenance connection check"
```

---

### Task 4: Add Active Recording Persistent Write Service Action

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt`

- [ ] **Step 1: Add service constants**

In `RecordingForegroundService.Companion`, add:

```kotlin
const val ACTION_WRITE_PERSISTENT_RECEIVER_CONFIG =
    "org.rtkcollector.app.recording.WRITE_PERSISTENT_RECEIVER_CONFIG"
const val EXTRA_PERSISTENT_COMMANDS = "persistentCommands"
const val EXTRA_PERSISTENT_WRITE_LABEL = "persistentWriteLabel"
const val EXTRA_PERSISTENT_TARGET_BAUD = "persistentTargetBaud"
```

- [ ] **Step 2: Route the new action**

In `onStartCommand`, add:

```kotlin
ACTION_WRITE_PERSISTENT_RECEIVER_CONFIG -> writePersistentReceiverConfig(intent)
```

- [ ] **Step 3: Add the service handler**

Add this method near `updateNtrip`:

```kotlin
private fun writePersistentReceiverConfig(intent: Intent) {
    if (!running.get()) {
        state = state.copy(
            lastError = "Cannot write receiver configuration: recording service is not connected.",
            errorCategory = RecordingErrorCategory.RECEIVER_COMMAND,
            errorSeverity = RecordingErrorSeverity.DEGRADED,
        )
        broadcastState()
        return
    }
    val captureRuntime = runtime
    val usbTransport = transport
    if (captureRuntime == null || usbTransport == null) {
        state = state.copy(
            lastError = "Cannot write receiver configuration: active receiver connection is unavailable.",
            errorCategory = RecordingErrorCategory.RECEIVER_COMMAND,
            errorSeverity = RecordingErrorSeverity.DEGRADED,
        )
        broadcastState()
        return
    }
    val commands = intent.getStringArrayListExtra(EXTRA_PERSISTENT_COMMANDS)
        .orEmpty()
        .map(String::trim)
        .filter(String::isNotBlank)
    if (commands.isEmpty()) {
        state = state.copy(
            lastError = "Cannot write receiver configuration: no commands were provided.",
            errorCategory = RecordingErrorCategory.RECEIVER_COMMAND,
            errorSeverity = RecordingErrorSeverity.DEGRADED,
        )
        broadcastState()
        return
    }
    val label = intent.getStringExtra(EXTRA_PERSISTENT_WRITE_LABEL)?.takeIf(String::isNotBlank)
        ?: "persistent receiver configuration"
    runCatching {
        writers?.appendEventJson("""{"type":"persistent-receiver-write-started","label":"${label.jsonEscape()}","commandCount":${commands.size}}""")
        synchronized(txLock) {
            commands.forEachIndexed { index, command ->
                captureRuntime.sendToReceiver("$command\r\n".toByteArray(Charsets.US_ASCII))
                Thread.sleep(100L)
            }
        }
        writers?.appendEventJson("""{"type":"persistent-receiver-write-succeeded","label":"${label.jsonEscape()}"}""")
        state = state.copy(
            lastError = null,
            errorCategory = RecordingErrorCategory.NONE,
            errorSeverity = RecordingErrorSeverity.NONE,
        )
        broadcastState()
    }.onFailure { error ->
        runCatching { writers?.appendEventJson("""{"type":"persistent-receiver-write-failed","label":"${label.jsonEscape()}","message":"${(error.message ?: error.javaClass.simpleName).jsonEscape()}"}""") }
        state = state.copy(
            lastError = "Persistent receiver configuration failed: ${error.message ?: error.javaClass.simpleName}",
            errorCategory = RecordingErrorCategory.RECEIVER_COMMAND,
            errorSeverity = RecordingErrorSeverity.DEGRADED,
        )
        broadcastState()
    }
}
```

Use the existing private `jsonEscape()` extension already present in `RecordingForegroundService.kt`; if it is scoped below this method, Kotlin still resolves it.

- [ ] **Step 4: Run compile verification**

Run:

```bash
sh gradlew :app:compileDebugKotlin
```

Expected: build succeeds.

- [ ] **Step 5: Commit Task 4**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt
git commit -m "Add active persistent receiver write service action"
```

---

### Task 5: Add USB/Baud Persistent Write UI Action Model

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileEditorModels.kt`
- Modify: `app/src/test/kotlin/org/rtkcollector/app/ui/profiles/ProfileEditorModelsTest.kt`

- [ ] **Step 1: Add tests for action labels and warnings**

Append to `ProfileEditorModelsTest`:

```kotlin
@Test
fun `usb baud editor exposes persistent target baud write action with warning text`() {
    val action = persistentBaudWriteAction(
        initialBaud = 230400,
        targetBaud = 460800,
        usbDeviceLabel = "FTDI UM980 0403:6015",
        onClick = {},
    )

    assertEquals("Write target baud persistently to device", action.label)
    assertTrue(action.warningTitle.orEmpty().contains("baud", ignoreCase = true))
    assertTrue(action.warningBody.orEmpty().contains("230400"))
    assertTrue(action.warningBody.orEmpty().contains("460800"))
    assertTrue(action.warningBody.orEmpty().contains("FTDI UM980 0403:6015"))
    assertTrue(action.warningBody.orEmpty().contains("UM980"))
    assertEquals("Write persistently", action.confirmLabel)
}

@Test
fun `command persistent warning mentions active recording connection`() {
    val action = persistentReceiverWriteAction(onClick = {})

    assertTrue(action.warningBody.orEmpty().contains("active recording connection", ignoreCase = true))
}
```

- [ ] **Step 2: Run tests to verify RED**

Run:

```bash
sh gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.ui.profiles.ProfileEditorModelsTest
```

Expected on a normal Android SDK host: failure because `persistentBaudWriteAction` does not exist and command warning text does not mention active recording.

- [ ] **Step 3: Implement action model changes**

In `ProfileEditorModels.kt`, update `PersistentReceiverWriteWarningBody`:

```kotlin
const val PersistentReceiverWriteWarningBody =
    "This sends the current init script and SAVECONFIG to the receiver. " +
        "If recording is active, it uses the active recording connection and records the transmitted commands. " +
        "It writes receiver non-volatile memory and can affect other apps, tools and future receiver sessions until manually changed again."
```

Add:

```kotlin
fun persistentBaudWriteAction(
    initialBaud: Int,
    targetBaud: Int,
    usbDeviceLabel: String,
    onClick: () -> Unit = {},
): ProfileEditorAction =
    ProfileEditorAction(
        label = "Write target baud persistently to device",
        onClick = onClick,
        warningTitle = "Write receiver baud persistently?",
        warningBody = "This opens the selected UM980 receiver at initial baud $initialBaud, " +
            "writes target baud $targetBaud for $usbDeviceLabel, then sends SAVECONFIG. " +
            "After success, future connections and power cycles may need baud $targetBaud.",
        confirmLabel = "Write persistently",
    )
```

- [ ] **Step 4: Run verification**

Run:

```bash
sh gradlew :app:compileDebugKotlin
sh gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.ui.profiles.ProfileEditorModelsTest
```

Expected on a normal Android SDK host: test passes.

- [ ] **Step 5: Commit Task 5**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileEditorModels.kt \
  app/src/test/kotlin/org/rtkcollector/app/ui/profiles/ProfileEditorModelsTest.kt
git commit -m "Add persistent baud write profile action"
```

---

### Task 6: Wire `MainActivity` To Service Path While Recording

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`

- [ ] **Step 1: Add imports**

Add:

```kotlin
import org.rtkcollector.app.receiver.PersistentReceiverWriteRoute
import org.rtkcollector.app.receiver.persistentReceiverWriteRoute
import org.rtkcollector.app.ui.profiles.persistentBaudWriteAction
```

- [ ] **Step 2: Add service intent helper**

Add near `buildNtripUpdateIntent`:

```kotlin
private fun persistentReceiverServiceIntent(
    context: Context,
    label: String,
    commands: List<String>,
): Intent =
    Intent(context, RecordingForegroundService::class.java).apply {
        action = RecordingForegroundService.ACTION_WRITE_PERSISTENT_RECEIVER_CONFIG
        putExtra(RecordingForegroundService.EXTRA_PERSISTENT_WRITE_LABEL, label)
        putStringArrayListExtra(RecordingForegroundService.EXTRA_PERSISTENT_COMMANDS, ArrayList(commands))
    }
```

- [ ] **Step 3: Replace recording block in command-profile persistent write**

In `writeCommandProfilePersistentlyToDevice`, delete the existing early block:

```kotlin
if (isRecording) {
    Toast.makeText(context, "Stop recording before writing receiver configuration persistently.", Toast.LENGTH_LONG).show()
    return
}
```

After the `commandProfile == null` guard, add:

```kotlin
val commands = persistentReceiverCommands(runtimeScript)
when (
    persistentReceiverWriteRoute(
        recordingActive = isRecording,
        usbProfileAvailable = true,
        receiverConnected = true,
        usbPermissionGranted = true,
    )
) {
    PersistentReceiverWriteRoute.ActiveRecordingService -> {
        context.startService(
            persistentReceiverServiceIntent(
                context = context,
                label = "Command profile persistent write",
                commands = commands,
            ),
        )
        Toast.makeText(
            context,
            "Writing receiver configuration through active recording connection...",
            Toast.LENGTH_SHORT,
        ).show()
        return
    }
    PersistentReceiverWriteRoute.IdleMaintenanceConnection -> Unit
    is PersistentReceiverWriteRoute.Rejected -> Unit
}
```

Then remove the later duplicate line:

```kotlin
val commands = persistentReceiverCommands(runtimeScript)
```

- [ ] **Step 4: Run compile verification**

Run:

```bash
sh gradlew :app:compileDebugKotlin
```

Expected: build succeeds.

- [ ] **Step 5: Commit Task 6**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt
git commit -m "Route active persistent writes through recording service"
```

---

### Task 7: Add Idle Maintenance Connection Verification

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`

- [ ] **Step 1: Add imports**

Add:

```kotlin
import org.rtkcollector.app.receiver.isPlausibleUm980MaintenanceResponse
import org.rtkcollector.app.receiver.um980VersionProbeBytes
```

- [ ] **Step 2: Add maintenance live check helper**

Add near `writeCommandProfilePersistentlyToDevice`:

```kotlin
private fun verifyMaintenanceReceiverConnection(transport: AndroidUsbSerialTransport) {
    val initialBytes = transport.readAvailable(4096)
    if (isPlausibleUm980MaintenanceResponse(initialBytes)) return
    transport.write(um980VersionProbeBytes())
    Thread.sleep(300)
    val response = transport.readAvailable(4096)
    require(isPlausibleUm980MaintenanceResponse(response)) {
        "Receiver did not respond at the selected initial baud."
    }
}
```

- [ ] **Step 3: Call live check before idle persistent writes**

Inside the idle maintenance thread in `writeCommandProfilePersistentlyToDevice`, after:

```kotlin
transport.open()
```

add:

```kotlin
verifyMaintenanceReceiverConnection(transport)
```

before any persistent command is sent.

- [ ] **Step 4: Run compile verification**

Run:

```bash
sh gradlew :app:compileDebugKotlin
```

Expected: build succeeds. `AndroidUsbSerialTransport` already exposes `readAvailable(maxBytes)`.

- [ ] **Step 5: Commit Task 7**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt
git commit -m "Verify idle receiver connection before persistent writes"
```

---

### Task 8: Add USB/Baud Target-Baud Persistence Action

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`

- [ ] **Step 1: Add USB/baud editor action**

In the `ProfileEditorScreen` actions builder, inside:

```kotlin
if (target.kind == ProfileKind.USB_BAUD) {
```

after `Request USB permission`, add:

```kotlin
val usbProfile = profileStore.usbBaudProfiles().firstOrNull { it.id == target.id }
if (usbProfile != null) {
    add(
        persistentBaudWriteAction(
            initialBaud = usbProfile.profileBaud,
            targetBaud = usbProfile.serialBaud,
            usbDeviceLabel = usbProfile.usbProductName
                ?: usbProfile.usbDeviceName
                ?: "selected USB receiver",
            onClick = {
                writeUsbBaudPersistentlyToDevice(
                    context = context,
                    usbProfileId = target.id,
                    isRecording = state.isRecording,
                )
            },
        ),
    )
}
```

- [ ] **Step 2: Add persistent baud write function**

Add near `writeCommandProfilePersistentlyToDevice`:

```kotlin
private fun writeUsbBaudPersistentlyToDevice(
    context: Context,
    usbProfileId: String,
    isRecording: Boolean,
) {
    val profileStore = ProfileStores(context)
    val usbProfile = profileStore.usbBaudProfiles().firstOrNull { it.id == usbProfileId }
    if (usbProfile == null) {
        Toast.makeText(context, "USB/baud profile is not available.", Toast.LENGTH_LONG).show()
        return
    }
    val commands = persistentBaudCommands(usbProfile.serialBaud)
    if (isRecording) {
        context.startService(
            persistentReceiverServiceIntent(
                context = context,
                label = "USB target baud persistent write",
                commands = commands,
            ),
        )
        Toast.makeText(context, "Writing receiver target baud through active recording connection...", Toast.LENGTH_SHORT).show()
        return
    }
    writePersistentCommandsViaMaintenanceConnection(
        context = context,
        usbProfile = usbProfile,
        commandProfileName = "USB target baud ${usbProfile.serialBaud}",
        commands = commands,
    )
}
```

- [ ] **Step 3: Extract maintenance writer helper**

Refactor the existing idle part of `writeCommandProfilePersistentlyToDevice` into:

```kotlin
private fun writePersistentCommandsViaMaintenanceConnection(
    context: Context,
    usbProfile: UsbBaudProfile,
    commandProfileName: String,
    commands: List<String>,
) {
    val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    val device = usbManager.selectUsbDevice(usbProfile)
    when (
        val route = persistentReceiverWriteRoute(
            recordingActive = false,
            usbProfileAvailable = true,
            receiverConnected = device != null,
            usbPermissionGranted = device?.let(usbManager::hasPermission) == true,
        )
    ) {
        PersistentReceiverWriteRoute.ActiveRecordingService -> error("Maintenance writer cannot use active service route.")
        PersistentReceiverWriteRoute.IdleMaintenanceConnection -> Unit
        is PersistentReceiverWriteRoute.Rejected -> {
            Toast.makeText(context, route.message, Toast.LENGTH_LONG).show()
            return
        }
    }
    if (!persistentReceiverWriteInProgress.compareAndSet(false, true)) {
        Toast.makeText(context, "Persistent receiver configuration write is already in progress.", Toast.LENGTH_LONG).show()
        return
    }
    Toast.makeText(context, "Writing persistent receiver configuration...", Toast.LENGTH_SHORT).show()
    Thread {
        val transport = AndroidUsbSerialTransport(
            usbManager = usbManager,
            device = requireNotNull(device),
            options = UsbSerialOpenOptions(baudRate = usbProfile.profileBaud),
        )
        try {
            runCatching {
                transport.open()
                verifyMaintenanceReceiverConnection(transport)
                commands.forEach { command ->
                    transport.write("$command\r\n".toByteArray(Charsets.US_ASCII))
                    Thread.sleep(PERSISTENT_RECEIVER_COMMAND_DELAY_MILLIS)
                }
            }.onSuccess {
                runOnMain(context) {
                    Toast.makeText(
                        context,
                        "Persistent receiver configuration written for $commandProfileName.",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }.onFailure { error ->
                runOnMain(context) {
                    Toast.makeText(
                        context,
                        "Persistent receiver configuration failed: ${error.message}",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        } finally {
            runCatching { transport.close() }
            persistentReceiverWriteInProgress.set(false)
        }
    }.start()
}
```

Then reduce the idle branch in `writeCommandProfilePersistentlyToDevice` to call this helper:

```kotlin
writePersistentCommandsViaMaintenanceConnection(
    context = context,
    usbProfile = usbProfile,
    commandProfileName = commandProfile.name,
    commands = commands,
)
```

- [ ] **Step 4: Run compile verification**

Run:

```bash
sh gradlew :app:compileDebugKotlin
```

Expected: build succeeds.

- [ ] **Step 5: Commit Task 8**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt
git commit -m "Add persistent target baud write action"
```

---

### Task 9: Document Persistent Write Behaviour

**Files:**
- Modify: `docs/user-workflows.md`

- [ ] **Step 1: Update user workflow docs**

In `docs/user-workflows.md`, replace the current persistent-write paragraph with:

```markdown
The normal recording start path sends runtime UM980 commands only. It does not
write receiver non-volatile memory. Persistent writes are explicit warned
maintenance actions:

- Menu > Command scripts > Edit > Write init config persistently to device:
  sends the visible Init script and then `SAVECONFIG`.
- Menu > USB device and baud > Edit > Write target baud persistently to device:
  sends `CONFIG COM1 <target baud>` and then `SAVECONFIG`.

If recording is active, persistent writes use the foreground recording service's
existing receiver connection and are recorded in `tx-to-receiver.raw`. If
recording is not active, RtkCollector opens the selected USB receiver at the
USB/baud profile's initial receiver baud, verifies communication with the
receiver, and only then sends persistent commands. Normal recording startup and
shutdown never append `SAVECONFIG` automatically.
```

- [ ] **Step 2: Run docs whitespace check**

Run:

```bash
git diff --check docs/user-workflows.md
```

Expected: no output.

- [ ] **Step 3: Commit Task 9**

```bash
git add docs/user-workflows.md
git commit -m "Document persistent receiver write behaviour"
```

---

### Task 10: Final Verification And Review

**Files:**
- Review all files changed by Tasks 1-9.

- [ ] **Step 1: Run final source checks**

Run:

```bash
git diff --check
sh gradlew :app:compileDebugKotlin
```

Expected: whitespace check passes and Kotlin compile succeeds.

- [ ] **Step 2: Run targeted tests**

Run:

```bash
sh gradlew :app:testDebugUnitTest \
  --tests org.rtkcollector.app.receiver.PersistentReceiverCommandsTest \
  --tests org.rtkcollector.app.receiver.PersistentReceiverWritePolicyTest \
  --tests org.rtkcollector.app.receiver.Um980MaintenanceConnectionCheckTest \
  --tests org.rtkcollector.app.ui.profiles.ProfileEditorModelsTest
```

Expected on a normal Android SDK host: all targeted tests pass.

Expected in this Termux environment: this may fail before test execution at Maven `aapt2`; if so, record the exact `aapt2` message and do not call it a source failure.

- [ ] **Step 3: Manual review checklist**

Verify in the diff:

- `receiver-rx.raw` is not touched by persistent-write commands.
- Active service writes call `CaptureRuntime.sendToReceiver`, so bytes go to
  `tx-to-receiver.raw`.
- Normal `ACTION_START` still does not append `SAVECONFIG` except when the user
  explicitly requested a persistent write action.
- Idle maintenance writes call `verifyMaintenanceReceiverConnection` before
  sending persistent commands.
- USB/baud persistent write sends `CONFIG COM1 <serialBaud>` and `SAVECONFIG`.
- The Start button guard against `persistentReceiverWriteInProgress` still
  exists.

- [ ] **Step 4: Commit any final fixes**

If Step 3 finds any final doc or code fix:

```bash
git add app/src/main/kotlin/org/rtkcollector/app/receiver \
  app/src/test/kotlin/org/rtkcollector/app/receiver \
  app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt \
  app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt \
  app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileEditorModels.kt \
  app/src/test/kotlin/org/rtkcollector/app/ui/profiles/ProfileEditorModelsTest.kt \
  docs/user-workflows.md
git commit -m "Polish persistent receiver write flow"
```

- [ ] **Step 5: Request code review**

Use `superpowers:requesting-code-review` with this scope:

```text
Review the persistent receiver write implementation for GNSS/Android correctness.
Focus on: active recording service TX path, idle USB connection verification,
target baud persistence sequence, SAVECONFIG only on explicit warned actions,
raw RX isolation, and Android/vendor USB compatibility.
```

- [ ] **Step 6: Apply required review fixes**

If the reviewer reports must-fix issues, use `superpowers:receiving-code-review`, verify each issue against the code, fix it, and rerun:

```bash
git diff --check
sh gradlew :app:compileDebugKotlin
```

- [ ] **Step 7: Final commit/push gate**

Use `review-and-commit` if there are uncommitted fixes. Push after validation:

```bash
git push origin main
```
