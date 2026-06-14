# Device Console Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an idle-only USB receiver console for manual ASCII command/debug interaction with a GNSS device.

**Architecture:** Add a small app-layer console package that owns a short-lived USB transport only while recording is inactive. Compose renders console state and sends actions; `RecordingForegroundService` remains the sole owner of receiver USB during recording.

**Tech Stack:** Kotlin, Android Compose Material3, existing `AndroidUsbSerialTransport`, existing `UsbBaudProfile` and `CommandProfile` stores, JUnit 5 for pure Kotlin tests.

---

## File Structure

- Create `app/src/main/kotlin/org/rtkcollector/app/console/DeviceConsoleModels.kt`
  - Pure state, line-ending, rolling-buffer, binary-safe formatter and transition helpers.
- Create `app/src/test/kotlin/org/rtkcollector/app/console/DeviceConsoleModelsTest.kt`
  - Unit tests for line endings, binary display, buffer trimming and recording-active rejection state.
- Create `app/src/main/kotlin/org/rtkcollector/app/console/DeviceConsoleController.kt`
  - Idle USB console runtime with injectable `SerialTransport` factory.
- Create `app/src/test/kotlin/org/rtkcollector/app/console/DeviceConsoleControllerTest.kt`
  - Unit tests with fake transport for connect/send/read/disconnect and recording-active blocking.
- Create `app/src/main/kotlin/org/rtkcollector/app/ui/console/DeviceConsoleScreen.kt`
  - Compose UI for the console screen.
- Modify `app/src/main/kotlin/org/rtkcollector/app/ui/settings/SettingsHub.kt`
  - Rename `Command scripts` to `Init/shutdown scripts`.
  - Add final `Device tools` group with `Device console`.
- Modify `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`
  - Add `AppScreen.DEVICE_CONSOLE`, create controller, wire USB profile/script selectors, permission/open flow and navigation.
- Update `docs/user-workflows.md`
  - Add a short user-facing note for the idle device console.
- Update `docs/superpowers/specs/2026-06-14-device-console-design.md` only if implementation discovers a necessary design correction.

---

### Task 1: Pure Console Models

**Files:**
- Create: `app/src/main/kotlin/org/rtkcollector/app/console/DeviceConsoleModels.kt`
- Test: `app/src/test/kotlin/org/rtkcollector/app/console/DeviceConsoleModelsTest.kt`

- [ ] **Step 1: Write failing tests for line endings, buffer trimming and display formatting**

Create `DeviceConsoleModelsTest.kt`:

```kotlin
package org.rtkcollector.app.console

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DeviceConsoleModelsTest {
    @Test
    fun `line endings render bytes`() {
        assertArrayEquals(byteArrayOf(13, 10), DeviceConsoleLineEnding.CRLF.bytes)
        assertArrayEquals(byteArrayOf(10), DeviceConsoleLineEnding.LF.bytes)
        assertArrayEquals(byteArrayOf(13), DeviceConsoleLineEnding.CR.bytes)
        assertArrayEquals(byteArrayOf(), DeviceConsoleLineEnding.NONE.bytes)
    }

    @Test
    fun `formatter preserves text and marks binary bytes`() {
        val rendered = DeviceConsoleOutputFormatter.render(byteArrayOf(0x41, 0x0A, 0x00, 0x7F, 0x42))
        assertEquals("A\n<00><7F>B", rendered)
    }

    @Test
    fun `rolling buffer keeps newest content`() {
        val buffer = DeviceConsoleRollingBuffer(maxChars = 8)
            .append("abc")
            .append("def")
            .append("ghi")

        assertEquals("bcdefghi", buffer.text)
    }

    @Test
    fun `recording active disables console connect`() {
        val idle = DeviceConsoleAvailability.fromRecordingState(recordingActive = false)
        val recording = DeviceConsoleAvailability.fromRecordingState(recordingActive = true)

        assertTrue(idle.canConnect)
        assertFalse(recording.canConnect)
        assertEquals("Stop recording before opening the device console.", recording.message)
    }
}
```

- [ ] **Step 2: Run the tests and verify they fail because types do not exist**

Run:

```bash
ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK ANDROID_SDK_ROOT=/storage/3830-3863/Termux/AndroidSDK sh gradlew --no-daemon -Pandroid.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2 :app:compileDebugUnitTestKotlin
```

Expected in this Termux environment: this may fail before compiling tests due the known Android resource/AAPT unit-test blocker. If it reaches Kotlin test compilation, expected failure is unresolved references for the new console model types.

- [ ] **Step 3: Implement pure models**

Create `DeviceConsoleModels.kt`:

```kotlin
package org.rtkcollector.app.console

enum class DeviceConsoleLineEnding(
    val label: String,
    val bytes: ByteArray,
) {
    CRLF("CRLF", byteArrayOf(13, 10)),
    LF("LF", byteArrayOf(10)),
    CR("CR", byteArrayOf(13)),
    NONE("None", byteArrayOf());
}

data class DeviceConsoleAvailability(
    val canConnect: Boolean,
    val message: String? = null,
) {
    companion object {
        fun fromRecordingState(recordingActive: Boolean): DeviceConsoleAvailability =
            if (recordingActive) {
                DeviceConsoleAvailability(
                    canConnect = false,
                    message = "Stop recording before opening the device console.",
                )
            } else {
                DeviceConsoleAvailability(canConnect = true)
            }
    }
}

data class DeviceConsoleRollingBuffer(
    val maxChars: Int,
    val text: String = "",
) {
    init {
        require(maxChars > 0) { "Console buffer length must be positive." }
    }

    fun append(chunk: String): DeviceConsoleRollingBuffer {
        if (chunk.isEmpty()) return this
        val combined = text + chunk
        return copy(text = if (combined.length <= maxChars) combined else combined.takeLast(maxChars))
    }

    fun clear(): DeviceConsoleRollingBuffer = copy(text = "")
}

object DeviceConsoleOutputFormatter {
    fun render(bytes: ByteArray): String =
        buildString(bytes.size) {
            bytes.forEach { raw ->
                val value = raw.toInt() and 0xFF
                when (value) {
                    0x0A -> append('\n')
                    0x0D -> append('\r')
                    in 0x20..0x7E -> append(value.toChar())
                    else -> append("<%02X>".format(value))
                }
            }
        }
}

enum class DeviceConsoleConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
}

data class DeviceConsoleState(
    val status: DeviceConsoleConnectionStatus = DeviceConsoleConnectionStatus.DISCONNECTED,
    val output: String = "",
    val input: String = "",
    val paused: Boolean = false,
    val lineEnding: DeviceConsoleLineEnding = DeviceConsoleLineEnding.CRLF,
    val bufferLimitBytes: Int = DEFAULT_BUFFER_LIMIT_BYTES,
    val selectedUsbProfileId: String? = null,
    val selectedCommandProfileId: String? = null,
    val lastError: String? = null,
) {
    val connected: Boolean
        get() = status == DeviceConsoleConnectionStatus.CONNECTED

    companion object {
        const val DEFAULT_BUFFER_LIMIT_BYTES = 1_048_576
    }
}
```

- [ ] **Step 4: Run focused verification**

Run:

```bash
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector diff --check
ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK ANDROID_SDK_ROOT=/storage/3830-3863/Termux/AndroidSDK sh gradlew --no-daemon -Pandroid.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2 :app:compileDebugKotlin
```

Expected: `diff --check` passes and `:app:compileDebugKotlin` passes.

- [ ] **Step 5: Commit**

```bash
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector add app/src/main/kotlin/org/rtkcollector/app/console/DeviceConsoleModels.kt app/src/test/kotlin/org/rtkcollector/app/console/DeviceConsoleModelsTest.kt
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector commit -m "Add device console state models"
```

---

### Task 2: Console Controller

**Files:**
- Create: `app/src/main/kotlin/org/rtkcollector/app/console/DeviceConsoleController.kt`
- Test: `app/src/test/kotlin/org/rtkcollector/app/console/DeviceConsoleControllerTest.kt`

- [ ] **Step 1: Write controller tests with fake transport**

Create `DeviceConsoleControllerTest.kt`:

```kotlin
package org.rtkcollector.app.console

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rtkcollector.core.transport.SerialTransport
import java.util.concurrent.CopyOnWriteArrayList

class DeviceConsoleControllerTest {
    @Test
    fun `connect is rejected while recording is active`() {
        val transport = FakeTransport()
        val states = CopyOnWriteArrayList<DeviceConsoleState>()
        val controller = DeviceConsoleController(
            recordingActive = { true },
            transportFactory = { transport },
            stateListener = states::add,
        )

        val result = controller.connect()

        assertFalse(result.isSuccess)
        assertFalse(transport.opened)
        assertEquals("Stop recording before opening the device console.", controller.state.lastError)
    }

    @Test
    fun `send writes input plus selected line ending`() {
        val transport = FakeTransport()
        val controller = DeviceConsoleController(
            recordingActive = { false },
            transportFactory = { transport },
            stateListener = {},
        )

        assertTrue(controller.connect().isSuccess)
        assertTrue(controller.send("VERSION", DeviceConsoleLineEnding.CRLF).isSuccess)

        assertEquals("VERSION\r\n", transport.written.decodeToString())
    }

    @Test
    fun `disconnect closes transport`() {
        val transport = FakeTransport()
        val controller = DeviceConsoleController(
            recordingActive = { false },
            transportFactory = { transport },
            stateListener = {},
        )

        controller.connect()
        controller.disconnect()

        assertFalse(transport.isOpen)
    }

    private class FakeTransport : SerialTransport {
        var opened = false
        var written = ByteArray(0)
        override val isOpen: Boolean
            get() = opened

        override fun open() {
            opened = true
        }

        override fun close() {
            opened = false
        }

        override fun readAvailable(maxBytes: Int): ByteArray = byteArrayOf()

        override fun write(bytes: ByteArray) {
            written += bytes
        }
    }
}
```

- [ ] **Step 2: Implement controller**

Create `DeviceConsoleController.kt`:

```kotlin
package org.rtkcollector.app.console

import org.rtkcollector.core.transport.SerialTransport
import java.util.concurrent.atomic.AtomicBoolean

class DeviceConsoleController(
    private val recordingActive: () -> Boolean,
    private val transportFactory: () -> SerialTransport,
    private val stateListener: (DeviceConsoleState) -> Unit,
) {
    @Volatile private var transport: SerialTransport? = null
    @Volatile private var readerThread: Thread? = null
    private val keepReading = AtomicBoolean(false)
    private var buffer = DeviceConsoleRollingBuffer(DeviceConsoleState.DEFAULT_BUFFER_LIMIT_BYTES)

    @Volatile var state: DeviceConsoleState = DeviceConsoleState()
        private set

    fun setBufferLimit(bytes: Int) {
        buffer = DeviceConsoleRollingBuffer(bytes, buffer.text.takeLast(bytes))
        update { it.copy(bufferLimitBytes = bytes, output = buffer.text) }
    }

    fun setPaused(paused: Boolean) {
        update { it.copy(paused = paused) }
    }

    fun connect(): Result<Unit> {
        if (recordingActive()) {
            val message = "Stop recording before opening the device console."
            update { it.copy(lastError = message, status = DeviceConsoleConnectionStatus.DISCONNECTED) }
            return Result.failure(IllegalStateException(message))
        }
        if (transport?.isOpen == true) return Result.success(Unit)
        update { it.copy(status = DeviceConsoleConnectionStatus.CONNECTING, lastError = null) }
        return runCatching {
            val opened = transportFactory()
            opened.open()
            transport = opened
            keepReading.set(true)
            startReader(opened)
            update { it.copy(status = DeviceConsoleConnectionStatus.CONNECTED, lastError = null) }
        }.onFailure { error ->
            update {
                it.copy(
                    status = DeviceConsoleConnectionStatus.DISCONNECTED,
                    lastError = error.message ?: "Device console could not connect.",
                )
            }
            runCatching { transport?.close() }
            transport = null
        }
    }

    fun send(text: String, lineEnding: DeviceConsoleLineEnding): Result<Unit> =
        runCatching {
            val opened = transport ?: error("Device console is not connected.")
            opened.write(text.encodeToByteArray() + lineEnding.bytes)
        }.onFailure { error ->
            update { it.copy(lastError = error.message ?: "Device console send failed.") }
        }

    fun disconnect() {
        keepReading.set(false)
        update { it.copy(status = DeviceConsoleConnectionStatus.DISCONNECTING) }
        runCatching { transport?.close() }
        transport = null
        readerThread = null
        update { it.copy(status = DeviceConsoleConnectionStatus.DISCONNECTED) }
    }

    fun clearOutput() {
        buffer = buffer.clear()
        update { it.copy(output = "") }
    }

    private fun startReader(opened: SerialTransport) {
        readerThread = Thread {
            while (keepReading.get() && opened.isOpen) {
                runCatching {
                    val bytes = opened.readAvailable(4096)
                    if (bytes.isNotEmpty()) {
                        appendOutput(DeviceConsoleOutputFormatter.render(bytes))
                    }
                }.onFailure { error ->
                    update { it.copy(lastError = error.message ?: "Device console read failed.") }
                    keepReading.set(false)
                }
            }
        }.apply {
            name = "rtkcollector-device-console-reader"
            isDaemon = true
            start()
        }
    }

    private fun appendOutput(chunk: String) {
        buffer = buffer.append(chunk)
        if (!state.paused) {
            update { it.copy(output = buffer.text) }
        }
    }

    private fun update(change: (DeviceConsoleState) -> DeviceConsoleState) {
        state = change(state)
        stateListener(state)
    }
}
```

- [ ] **Step 3: Run focused verification**

Run:

```bash
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector diff --check
ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK ANDROID_SDK_ROOT=/storage/3830-3863/Termux/AndroidSDK sh gradlew --no-daemon -Pandroid.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2 :app:compileDebugKotlin
```

Expected: both commands pass. If Android app unit tests still cannot run locally, record the blocker and rely on Kotlin compile plus code review for controller tests until CI/Windows can run them.

- [ ] **Step 4: Commit**

```bash
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector add app/src/main/kotlin/org/rtkcollector/app/console/DeviceConsoleController.kt app/src/test/kotlin/org/rtkcollector/app/console/DeviceConsoleControllerTest.kt
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector commit -m "Add idle device console controller"
```

---

### Task 3: Compose Console Screen

**Files:**
- Create: `app/src/main/kotlin/org/rtkcollector/app/ui/console/DeviceConsoleScreen.kt`

- [ ] **Step 1: Create screen with explicit state and callbacks**

Create `DeviceConsoleScreen.kt`:

```kotlin
package org.rtkcollector.app.ui.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import org.rtkcollector.app.console.DeviceConsoleLineEnding
import org.rtkcollector.app.console.DeviceConsoleState

data class DeviceConsoleOption(
    val id: String,
    val label: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceConsoleScreen(
    state: DeviceConsoleState,
    recordingActive: Boolean,
    usbProfiles: List<DeviceConsoleOption>,
    commandProfiles: List<DeviceConsoleOption>,
    selectedUsbProfileId: String?,
    selectedCommandProfileId: String?,
    inputText: String,
    onInputChange: (String) -> Unit,
    onUsbProfileSelected: (String) -> Unit,
    onCommandProfileSelected: (String) -> Unit,
    onLineEndingSelected: (DeviceConsoleLineEnding) -> Unit,
    onBufferLimitSelected: (Int) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onSend: () -> Unit,
    onSendInit: () -> Unit,
    onClearInput: () -> Unit,
    onPauseToggle: () -> Unit,
    onCopyOutput: () -> Unit,
    onClearOutput: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Device console") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
                actions = {
                    TextButton(onClick = onConnect, enabled = !recordingActive && !state.connected) { Text("Connect") }
                    TextButton(onClick = onDisconnect, enabled = state.connected) { Text("Disconnect") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (recordingActive) {
                Text(
                    text = "Stop recording before opening the device console.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ConsoleDropdown(
                    label = "USB/baud",
                    value = usbProfiles.firstOrNull { it.id == selectedUsbProfileId }?.label ?: "n/a",
                    options = usbProfiles,
                    onSelect = onUsbProfileSelected,
                    modifier = Modifier.weight(1f),
                )
                ConsoleDropdown(
                    label = "Line ending",
                    value = state.lineEnding.label,
                    options = DeviceConsoleLineEnding.entries.map { DeviceConsoleOption(it.name, it.label) },
                    onSelect = { id -> onLineEndingSelected(DeviceConsoleLineEnding.valueOf(id)) },
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ConsoleDropdown(
                    label = "Init script",
                    value = commandProfiles.firstOrNull { it.id == selectedCommandProfileId }?.label ?: "n/a",
                    options = commandProfiles,
                    onSelect = onCommandProfileSelected,
                    modifier = Modifier.weight(1f),
                )
                ConsoleDropdown(
                    label = "Buffer",
                    value = bufferLabel(state.bufferLimitBytes),
                    options = listOf(
                        DeviceConsoleOption("262144", "256 KB"),
                        DeviceConsoleOption("1048576", "1 MB"),
                        DeviceConsoleOption("4194304", "4 MB"),
                    ),
                    onSelect = { id -> onBufferLimitSelected(id.toInt()) },
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = onSendInit, enabled = state.connected && selectedCommandProfileId != null) {
                    Text("Send init")
                }
            }
            SelectionContainer(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                Text(
                    text = state.output,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(8.dp),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputChange,
                label = { Text("Command input") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp),
                minLines = 3,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onSend, enabled = state.connected && inputText.isNotBlank()) { Text("Send") }
                TextButton(onClick = onClearInput) { Text("Clear input") }
                TextButton(onClick = onPauseToggle) { Text(if (state.paused) "Resume output" else "Pause output") }
                TextButton(onClick = onCopyOutput, enabled = state.output.isNotBlank()) { Text("Copy output") }
                TextButton(onClick = onClearOutput, enabled = state.output.isNotBlank()) { Text("Clear output") }
            }
            state.lastError?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConsoleDropdown(
    label: String,
    value: String,
    options: List<DeviceConsoleOption>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onSelect(option.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun bufferLabel(bytes: Int): String =
    when (bytes) {
        262_144 -> "256 KB"
        1_048_576 -> "1 MB"
        4_194_304 -> "4 MB"
        else -> "$bytes B"
    }
```

- [ ] **Step 2: Run compile verification**

Run:

```bash
ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK ANDROID_SDK_ROOT=/storage/3830-3863/Termux/AndroidSDK sh gradlew --no-daemon -Pandroid.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2 :app:compileDebugKotlin
```

Expected: compile passes. If Compose API import names differ from the current project version, adjust only the dropdown anchor calls to match existing `ProfileScreens.kt` patterns.

- [ ] **Step 3: Commit**

```bash
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector add app/src/main/kotlin/org/rtkcollector/app/ui/console/DeviceConsoleScreen.kt
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector commit -m "Add device console screen"
```

---

### Task 4: Settings Menu Entry and Rename

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/settings/SettingsHub.kt`

- [ ] **Step 1: Add callback and menu row**

Modify the `SettingsHub` signature:

```kotlin
fun SettingsHub(
    onSettingsSets: () -> Unit,
    onWorkflowSelection: () -> Unit,
    dashboardLayoutLabel: String,
    onDashboardLayout: () -> Unit,
    onNtripCaster: () -> Unit,
    onNtripMountpoint: () -> Unit,
    onUsbBaud: () -> Unit,
    onCommands: () -> Unit,
    onReceiverProfile: () -> Unit,
    onRecordingOutputs: () -> Unit,
    onStorage: () -> Unit,
    onSessions: () -> Unit,
    onExportSettings: () -> Unit,
    onImportSettings: () -> Unit,
    onDeviceConsole: () -> Unit,
    onBack: () -> Unit,
)
```

Change the existing receiver section row:

```kotlin
SettingsRow("⌁", "Init/shutdown scripts", onCommands)
```

Add a final group after `Settings transfer`:

```kotlin
SettingsSection("Device tools") {
    SettingsRow("IO", "Device console", onDeviceConsole)
}
```

Update the preview call with `onDeviceConsole = {}`.

- [ ] **Step 2: Compile**

Run:

```bash
ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK ANDROID_SDK_ROOT=/storage/3830-3863/Termux/AndroidSDK sh gradlew --no-daemon -Pandroid.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2 :app:compileDebugKotlin
```

Expected: compile fails until MainActivity call site is updated in Task 5, or passes if Task 5 is executed in the same checkpoint.

- [ ] **Step 3: Commit after Task 5 wiring also compiles**

Stage this file together with Task 5.

---

### Task 5: MainActivity Wiring

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`

- [ ] **Step 1: Add imports**

Add imports near the existing imports:

```kotlin
import org.rtkcollector.app.console.DeviceConsoleController
import org.rtkcollector.app.console.DeviceConsoleLineEnding
import org.rtkcollector.app.console.DeviceConsoleState
import org.rtkcollector.app.ui.console.DeviceConsoleOption
import org.rtkcollector.app.ui.console.DeviceConsoleScreen
```

- [ ] **Step 2: Add remembered console state**

Inside `RtkCollectorApp`, near other `rememberSaveable` / `remember` state:

```kotlin
var consoleState by remember { mutableStateOf(DeviceConsoleState()) }
var consoleInput by rememberSaveable { mutableStateOf("") }
var selectedConsoleUsbProfileId by rememberSaveable {
    mutableStateOf(profileStore.usbBaudProfiles().firstOrNull()?.id)
}
var selectedConsoleCommandProfileId by rememberSaveable {
    mutableStateOf(profileStore.commandProfiles().firstOrNull()?.id)
}
var consoleLineEnding by rememberSaveable { mutableStateOf(DeviceConsoleLineEnding.CRLF) }
```

- [ ] **Step 3: Add screen enum value**

Modify `AppScreen`:

```kotlin
private enum class AppScreen {
    HOME,
    SETTINGS,
    NTRIP_CASTER,
    NTRIP_MOUNTPOINT,
    NTRIP_MOUNTPOINT_PROFILES,
    COMMANDS,
    USB_BAUD,
    RECORDING_OUTPUTS,
    STORAGE,
    SESSIONS,
    DEVICE_CONSOLE,
    SETTINGS_SETS,
    SETTINGS_SET_SELECTOR,
    MOUNTPOINT_SELECTOR,
    COMMAND_SELECTOR,
    STORAGE_SELECTOR,
    PROFILE_EDITOR,
}
```

- [ ] **Step 4: Add SettingsHub callback**

In the `SettingsHub` call:

```kotlin
onDeviceConsole = { screen = AppScreen.DEVICE_CONSOLE },
```

- [ ] **Step 5: Create console controller factory in MainActivity**

Add helper functions near existing USB helpers:

```kotlin
private fun createDeviceConsoleController(
    context: Context,
    isRecording: () -> Boolean,
    usbProfile: UsbBaudProfile,
    onState: (DeviceConsoleState) -> Unit,
): DeviceConsoleController {
    val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    return DeviceConsoleController(
        recordingActive = isRecording,
        transportFactory = {
            val device = usbManager.selectUsbDevice(usbProfile)
                ?: error("Selected USB receiver is not connected.")
            if (!usbManager.hasPermission(device)) {
                error("USB permission is required before opening the device console.")
            }
            AndroidUsbSerialTransport(
                usbManager = usbManager,
                device = device,
                options = UsbSerialOpenOptions(usbProfile.profileBaud),
            )
        },
        stateListener = onState,
    )
}
```

If `selectUsbDevice` is private and already in `MainActivity.kt`, this helper can call it directly because it lives in the same file.

- [ ] **Step 6: Add DeviceConsoleScreen branch**

Add a `when (screen)` branch:

```kotlin
AppScreen.DEVICE_CONSOLE -> {
    val usbProfiles = profileStore.usbBaudProfiles()
    val commandProfiles = profileStore.commandProfiles()
    val selectedUsbProfile = usbProfiles.firstOrNull { it.id == selectedConsoleUsbProfileId } ?: usbProfiles.firstOrNull()
    val selectedCommandProfile = commandProfiles.firstOrNull { it.id == selectedConsoleCommandProfileId } ?: commandProfiles.firstOrNull()
    var consoleController by remember(selectedUsbProfile?.id) {
        mutableStateOf<DeviceConsoleController?>(null)
    }

    DisposableEffect(screen) {
        onDispose {
            consoleController?.disconnect()
            consoleController = null
        }
    }

    DeviceConsoleScreen(
        state = consoleState.copy(
            lineEnding = consoleLineEnding,
            selectedUsbProfileId = selectedUsbProfile?.id,
            selectedCommandProfileId = selectedCommandProfile?.id,
        ),
        recordingActive = state.isRecording,
        usbProfiles = usbProfiles.map { DeviceConsoleOption(it.id, it.name) },
        commandProfiles = commandProfiles.map { DeviceConsoleOption(it.id, it.name) },
        selectedUsbProfileId = selectedUsbProfile?.id,
        selectedCommandProfileId = selectedCommandProfile?.id,
        inputText = consoleInput,
        onInputChange = { consoleInput = it },
        onUsbProfileSelected = { id ->
            consoleController?.disconnect()
            consoleController = null
            selectedConsoleUsbProfileId = id
            consoleState = DeviceConsoleState(selectedUsbProfileId = id, lineEnding = consoleLineEnding)
        },
        onCommandProfileSelected = { id -> selectedConsoleCommandProfileId = id },
        onLineEndingSelected = { consoleLineEnding = it },
        onBufferLimitSelected = { bytes ->
            consoleController?.setBufferLimit(bytes)
            consoleState = consoleState.copy(bufferLimitBytes = bytes)
        },
        onConnect = {
            val profile = selectedUsbProfile
            if (profile == null) {
                Toast.makeText(context, "No USB/baud profile is available.", Toast.LENGTH_LONG).show()
            } else {
                val controller = consoleController ?: createDeviceConsoleController(
                    context = context,
                    isRecording = { state.isRecording },
                    usbProfile = profile,
                    onState = { consoleState = it },
                ).also { consoleController = it }
                controller.connect().onFailure { error ->
                    Toast.makeText(context, error.message ?: "Device console could not connect.", Toast.LENGTH_LONG).show()
                }
            }
        },
        onDisconnect = { consoleController?.disconnect() },
        onSend = {
            consoleController?.send(consoleInput, consoleLineEnding)?.onFailure { error ->
                Toast.makeText(context, error.message ?: "Device console send failed.", Toast.LENGTH_LONG).show()
            }
        },
        onSendInit = {
            val script = selectedCommandProfile?.runtimeScript.orEmpty()
            if (script.isBlank()) {
                Toast.makeText(context, "Selected init script is empty.", Toast.LENGTH_LONG).show()
            } else {
                consoleController?.send(script, consoleLineEnding)?.onFailure { error ->
                    Toast.makeText(context, error.message ?: "Init script send failed.", Toast.LENGTH_LONG).show()
                }
            }
        },
        onClearInput = { consoleInput = "" },
        onPauseToggle = { consoleController?.setPaused(!consoleState.paused) },
        onCopyOutput = { context.copyToClipboard("Device console output", consoleState.output) },
        onClearOutput = { consoleController?.clearOutput() },
        onBack = {
            consoleController?.disconnect()
            consoleController = null
            screen = AppScreen.SETTINGS
        },
    )
}
```

Use the repository’s existing clipboard helper if one exists. If not, add this helper near other UI helpers:

```kotlin
private fun Context.copyToClipboard(label: String, text: String) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    clipboard.setPrimaryClip(android.content.ClipData.newPlainText(label, text))
    Toast.makeText(this, "Copied to clipboard.", Toast.LENGTH_SHORT).show()
}
```

- [ ] **Step 7: Request USB permission from console errors**

If connect fails with `USB permission is required before opening the device console.`, call:

```kotlin
requestUsbPermissionForProfile(context, selectedUsbProfile?.id)
```

Then show:

```kotlin
Toast.makeText(context, "Approve USB access, then tap Connect again.", Toast.LENGTH_LONG).show()
```

- [ ] **Step 8: Compile**

Run:

```bash
ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK ANDROID_SDK_ROOT=/storage/3830-3863/Termux/AndroidSDK sh gradlew --no-daemon -Pandroid.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2 :app:compileDebugKotlin
```

Expected: compile passes. Fix only import/API mismatches caused by exact Compose or existing helper names.

- [ ] **Step 9: Commit Task 4 and Task 5 together**

```bash
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector add app/src/main/kotlin/org/rtkcollector/app/ui/settings/SettingsHub.kt app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector commit -m "Wire idle device console into settings"
```

---

### Task 6: User Documentation

**Files:**
- Modify: `docs/user-workflows.md`

- [ ] **Step 1: Add console documentation**

Add this section near the settings or receiver-control parts of `docs/user-workflows.md`:

```markdown
## Device Console

The Device console is an idle-only manual receiver console. It is available from
Menu > Device console and is disabled while recording is active.

Use it for short diagnostics or maintenance commands:

1. Stop recording.
2. Open Menu > Device console.
3. Select the USB/baud profile.
4. Tap Connect.
5. Select the line ending, normally CRLF.
6. Type an ASCII receiver command and tap Send.
7. Optionally select an init/shutdown script and tap Send init.
8. Tap Disconnect or leave the screen to close the USB connection.

Console output is temporary. It is not saved into session folders and does not
modify receiver recording artifacts. Non-printable bytes are shown as compact
hex markers so mixed binary/text receiver streams do not break the display.
```

- [ ] **Step 2: Run markdown and compile checks**

Run:

```bash
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector diff --check
ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK ANDROID_SDK_ROOT=/storage/3830-3863/Termux/AndroidSDK sh gradlew --no-daemon -Pandroid.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2 :app:compileDebugKotlin
```

Expected: both commands pass.

- [ ] **Step 3: Commit**

```bash
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector add docs/user-workflows.md
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector commit -m "Document idle device console"
```

---

### Task 7: Final Review and Push

**Files:**
- Review all files changed by Tasks 1-6.

- [ ] **Step 1: Inspect final status**

Run:

```bash
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector status --short
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector log --oneline -6
```

Expected: only local artifact folders such as `.codex-tmp/`, `.superpowers/`, or `samples/` remain untracked.

- [ ] **Step 2: Run final feasible local validation**

Run:

```bash
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector diff --check
ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK ANDROID_SDK_ROOT=/storage/3830-3863/Termux/AndroidSDK sh gradlew --no-daemon -Pandroid.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2 :app:compileDebugKotlin
```

Expected: both commands pass.

- [ ] **Step 3: Request code review**

Use `superpowers:requesting-code-review` with these review focuses:

- idle console cannot open while recording is active;
- console USB ownership is separate from `RecordingForegroundService`;
- leaving console closes the transport;
- output formatter is binary-safe enough for mixed receiver streams;
- no console bytes are written into recording session artifacts.

- [ ] **Step 4: Address reviewer findings**

For any must-fix review finding, apply the smallest patch, rerun:

```bash
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector diff --check
ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK ANDROID_SDK_ROOT=/storage/3830-3863/Termux/AndroidSDK sh gradlew --no-daemon -Pandroid.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2 :app:compileDebugKotlin
```

Then commit the fix with:

```bash
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector add <changed-files>
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector commit -m "Harden idle device console"
```

- [ ] **Step 5: Push**

Run:

```bash
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector push origin main
```

Expected: remote `main` advances.

---

## Self-Review

Spec coverage:

- Idle-only console: Tasks 2, 5.
- Menu entry and command-menu rename: Task 4.
- Connect/Disconnect and USB profile selector: Tasks 3, 5.
- Line-ending selector defaulting to CRLF: Tasks 1, 3, 5.
- Init-script selector plus explicit Send init: Tasks 3, 5.
- Temporary output with buffer limit and pause: Tasks 1, 2, 3.
- Multiline output/input and Send/Clear/Copy controls: Task 3.
- Disconnect on exit: Tasks 2, 5.
- No recording artifact mutation: Tasks 2, 7 review focus.
- Tests and verification: Tasks 1, 2, 6, 7.

Plan quality checks:

- No implementation step requires maps, GIS, NTRIP networking, RTKLIB or recording-service changes.
- Public state and controller types are explicit and testable.
- Android-specific USB selection remains in `MainActivity.kt`, close to existing USB helper functions.
- The plan uses local Termux validation commands that are known to work in this repository.

