# Compose Dashboard UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the approved Compose/Material 3 dashboard UI, profile/settings navigation, UM980 telemetry parsing, controlled baud sequencing, and session file/share workflow without weakening byte-exact recording.

**Architecture:** Compose owns only presentation and user intent. The foreground service remains the recording owner, and reusable telemetry/profile/session logic is moved into testable Kotlin models before UI wiring. UM980 parsing remains advisory and byte-level so mixed binary streams cannot corrupt raw capture.

**Tech Stack:** Android Gradle Plugin 9.2.0, Kotlin 2.3.21, Compose Material 3, pure Kotlin unit tests, existing foreground service and receiver modules.

---

## Scope And Ordering

This plan implements the approved spec in reviewable slices. It intentionally starts with state models, parser tests and small Compose screens before removing the old UI. Do not implement maps, shapefiles, GIS editing, RTKLIB real-time processing, Android-side PPP/static solving or NTRIP caster upload.

Expected branch:

```bash
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector status --short --branch
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector checkout -b codex/compose-dashboard-ui
```

If the branch already exists, use:

```bash
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector checkout codex/compose-dashboard-ui
```

Keep `.superpowers/` untracked. Use `sh gradlew` on Android shared storage.

## File Map

Create:

- `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`: Compose-hosting activity shell.
- `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardModels.kt`: dashboard state and display models.
- `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardFormatters.kt`: adaptive units and fix-label formatting.
- `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/HomeDashboard.kt`: fixed bars, status strip and cards.
- `app/src/main/kotlin/org/rtkcollector/app/ui/settings/SettingsHub.kt`: settings menu surface.
- `app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileEditorModels.kt`: reusable profile editor state/actions.
- `app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileScreens.kt`: profile list/editor Compose screens.
- `app/src/main/kotlin/org/rtkcollector/app/ui/sessions/SessionBrowserModels.kt`: session list/file/share state.
- `app/src/main/kotlin/org/rtkcollector/app/ui/sessions/SessionsScreen.kt`: current/recent/older sessions and detail.
- `app/src/main/kotlin/org/rtkcollector/app/share/SessionShareModels.kt`: share/ZIP eligibility model.
- `app/src/main/kotlin/org/rtkcollector/app/share/SessionShareController.kt`: Android `ACTION_SEND` / `ACTION_SEND_MULTIPLE` intents and ZIP creation.
- `app/src/test/kotlin/org/rtkcollector/app/ui/dashboard/DashboardFormattersTest.kt`.
- `app/src/test/kotlin/org/rtkcollector/app/ui/dashboard/DashboardStateTest.kt`.
- `app/src/test/kotlin/org/rtkcollector/app/profile/ProfileStoresTest.kt`.
- `app/src/test/kotlin/org/rtkcollector/app/ui/profiles/NtripMountpointSelectionTest.kt`.
- `app/src/test/kotlin/org/rtkcollector/app/share/SessionShareModelsTest.kt`.
- `receiver/unicore-n4/src/main/kotlin/org/rtkcollector/receiver/unicore/Um980StreamParser.kt`: mixed stream classifier.
- `receiver/unicore-n4/src/main/kotlin/org/rtkcollector/receiver/unicore/Um980BinaryParser.kt`: binary frame and `BESTNAVB` decoding.
- `receiver/unicore-n4/src/main/kotlin/org/rtkcollector/receiver/unicore/Um980Telemetry.kt`: unified telemetry events.
- `receiver/unicore-n4/src/test/kotlin/org/rtkcollector/receiver/unicore/Um980BinaryParserTest.kt`.
- `receiver/unicore-n4/src/test/kotlin/org/rtkcollector/receiver/unicore/Um980StreamParserTest.kt`.
- `app/src/main/kotlin/org/rtkcollector/app/recording/Um980BaudTransition.kt`.
- `app/src/test/kotlin/org/rtkcollector/app/recording/Um980BaudTransitionTest.kt`.

Modify:

- `app/build.gradle.kts`: add Kotlin Android and Compose dependencies.
- `build.gradle.kts`: add Kotlin Android plugin version.
- `app/src/main/AndroidManifest.xml`: point launcher to new UI activity package if package path changes.
- `app/src/main/kotlin/org/rtkcollector/app/MainActivity.kt`: delete after Compose replacement is wired, or leave as a thin forwarding shim for one commit if needed.
- `app/src/main/kotlin/org/rtkcollector/app/profile/ProfileModels.kt`: add workflow-default models and protected-profile flags.
- `app/src/main/kotlin/org/rtkcollector/app/profile/ProfileStores.kt`: add create/rename/copy/delete/default assignment operations.
- `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingServiceState.kt`: expand service state fields.
- `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt`: broadcast expanded telemetry, active config, NTRIP URL/rates and session file state.
- `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingSessionWriters.kt`: expose file list/byte counters for dashboard/session browser.
- `receiver/unicore-n4/src/main/kotlin/org/rtkcollector/receiver/unicore/Um980LiveParsers.kt`: route existing ASCII/NMEA parsing into unified telemetry.
- `docs/user-workflows.md`: update UI and session sharing instructions.
- `docs/ntrip-and-corrections.md`: mention explicit mountpoint selection semantics in UI.
- `AGENTS.md`: add Compose dashboard and UM980 binary parser guardrails after implementation.

---

### Task 1: Enable Compose And Keep A Compiling Shell

**Files:**
- Modify: `build.gradle.kts`
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Add the Kotlin Android plugin to the root build**

Change the root plugins block to:

```kotlin
plugins {
    id("com.android.application") version "9.2.0" apply false
    kotlin("android") version "2.3.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
    kotlin("jvm") version "2.3.21" apply false
}
```

- [ ] **Step 2: Enable Compose in the app module**

Replace the `app/build.gradle.kts` plugins/dependencies with:

```kotlin
plugins {
    id("com.android.application")
    kotlin("android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "org.rtkcollector.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.rtkcollector.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.05.00"))
    implementation("androidx.activity:activity-compose:1.12.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation(project(":core:capture"))
    implementation(project(":core:correction"))
    implementation(project(":core:session"))
    implementation(project(":core:transport"))
    implementation(project(":core:workflow"))
    implementation(project(":receiver:unicore-n4"))
}
```

- [ ] **Step 3: Create the Compose activity shell**

Create `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`:

```kotlin
package org.rtkcollector.app.ui

import android.app.Activity
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RtkCollectorApp()
        }
    }
}

@Composable
fun RtkCollectorApp() {
    MaterialTheme {
        Surface {
            Text("RtkCollector dashboard")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RtkCollectorAppPreview() {
    RtkCollectorApp()
}
```

- [ ] **Step 4: Point the manifest at the Compose activity**

In `app/src/main/AndroidManifest.xml`, set the launcher activity name to:

```xml
android:name=".ui.MainActivity"
```

Keep the recording service declaration unchanged.

- [ ] **Step 5: Compile the app Kotlin**

Run:

```bash
ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK ANDROID_SDK_ROOT=/storage/3830-3863/Termux/AndroidSDK sh gradlew :app:compileDebugKotlin
```

Expected: Kotlin compile succeeds. If dependency resolution needs network, rerun with approved network access.

- [ ] **Step 6: Commit**

```bash
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector add build.gradle.kts app/build.gradle.kts app/src/main/AndroidManifest.xml app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector commit -m "Add Compose app shell"
```

---

### Task 2: Dashboard State Models And Formatters

**Files:**
- Create: `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardModels.kt`
- Create: `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardFormatters.kt`
- Create: `app/src/test/kotlin/org/rtkcollector/app/ui/dashboard/DashboardFormattersTest.kt`
- Create: `app/src/test/kotlin/org/rtkcollector/app/ui/dashboard/DashboardStateTest.kt`

- [ ] **Step 1: Write formatter tests**

Create `DashboardFormattersTest.kt`:

```kotlin
package org.rtkcollector.app.ui.dashboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DashboardFormattersTest {
    @Test
    fun `distance formatter uses mm cm m and km`() {
        assertEquals("8 mm", formatDistance(0.008))
        assertEquals("3 cm", formatDistance(0.03))
        assertEquals("12.4 m", formatDistance(12.4))
        assertEquals("42.8 km", formatDistance(42_800.0))
    }

    @Test
    fun `bytes formatter uses compact units`() {
        assertEquals("512 B", formatBytes(512))
        assertEquals("1.5 kB", formatBytes(1536))
        assertEquals("2.1 MB", formatBytes(2_100_000))
    }

    @Test
    fun `gga fix quality is interpreted`() {
        assertEquals("Invalid", interpretGgaFixQuality(0))
        assertEquals("Single", interpretGgaFixQuality(1))
        assertEquals("DGPS", interpretGgaFixQuality(2))
        assertEquals("RTK fix", interpretGgaFixQuality(4))
        assertEquals("RTK float", interpretGgaFixQuality(5))
        assertEquals("PPP", interpretGgaFixQuality(9))
        assertEquals("Quality 17", interpretGgaFixQuality(17))
    }
}
```

- [ ] **Step 2: Write dashboard state test**

Create `DashboardStateTest.kt`:

```kotlin
package org.rtkcollector.app.ui.dashboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DashboardStateTest {
    @Test
    fun `planned session shows start as primary action`() {
        val state = DashboardState.planned(
            workflow = "Rover + NTRIP",
            mountpoint = "TUBO00CZE0",
            receiver = "UM980",
            storage = "SAF folder",
        )

        assertEquals("Start", state.primaryAction.label)
        assertEquals("Rover + NTRIP", state.status.workflow)
        assertEquals("TUBO00CZE0", state.status.mountpoint)
    }

    @Test
    fun `running session keeps stop visible`() {
        val state = DashboardState.running(
            status = DashboardStatus("Rover + NTRIP", "TUBO00CZE0", "UM980", "SAF folder"),
            position = PositionCardState(latLon = "50.087451234, 14.421253456"),
            fix = FixCardState(fixType = "RTK float"),
            ntrip = NtripCardState(status = "Streaming"),
            files = FilesCardState(sessionLocation = ".../session"),
        )

        assertEquals("Stop", state.primaryAction.label)
        assertTrue(state.isRecording)
    }
}
```

- [ ] **Step 3: Run tests and verify failure**

Run:

```bash
ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK ANDROID_SDK_ROOT=/storage/3830-3863/Termux/AndroidSDK sh gradlew :app:testDebugUnitTest --tests 'org.rtkcollector.app.ui.dashboard.*'
```

Expected: fail because formatter/model symbols do not exist.

- [ ] **Step 4: Add dashboard models**

Create `DashboardModels.kt`:

```kotlin
package org.rtkcollector.app.ui.dashboard

data class DashboardState(
    val isRecording: Boolean,
    val status: DashboardStatus,
    val position: PositionCardState,
    val fix: FixCardState,
    val ntrip: NtripCardState,
    val files: FilesCardState,
    val primaryAction: DashboardAction,
    val secondaryActions: List<DashboardAction>,
) {
    companion object {
        fun planned(workflow: String, mountpoint: String, receiver: String, storage: String): DashboardState =
            DashboardState(
                isRecording = false,
                status = DashboardStatus(workflow, mountpoint, receiver, storage),
                position = PositionCardState(),
                fix = FixCardState(),
                ntrip = NtripCardState(),
                files = FilesCardState(),
                primaryAction = DashboardAction("Start", DashboardActionKind.START),
                secondaryActions = listOf(DashboardAction("Menu", DashboardActionKind.MENU)),
            )

        fun running(
            status: DashboardStatus,
            position: PositionCardState,
            fix: FixCardState,
            ntrip: NtripCardState,
            files: FilesCardState,
        ): DashboardState =
            DashboardState(
                isRecording = true,
                status = status,
                position = position,
                fix = fix,
                ntrip = ntrip,
                files = files,
                primaryAction = DashboardAction("Stop", DashboardActionKind.STOP),
                secondaryActions = listOf(
                    DashboardAction("NTRIP", DashboardActionKind.NTRIP),
                    DashboardAction("Mark", DashboardActionKind.MARK),
                ),
            )
    }
}

data class DashboardStatus(
    val workflow: String = "n/a",
    val mountpoint: String = "n/a",
    val receiver: String = "n/a",
    val storage: String = "n/a",
)

data class PositionCardState(
    val latLon: String = "n/a",
    val ellipsoidalHeight: String = "n/a",
    val altitude: String = "n/a",
    val utcTime: String = "n/a",
    val latError: String = "n/a",
    val lonError: String = "n/a",
)

data class FixCardState(
    val fixType: String = "n/a",
    val satellites: String = "n/a",
    val pdop: String = "n/a",
    val hdopVdop: String = "n/a",
    val horizontalAccuracy: String = "n/a",
    val verticalAccuracy: String = "n/a",
    val differentialAge: String = "n/a",
    val baseline: String = "n/a",
    val pppStatus: String = "n/a",
    val rtklibStatus: String = "Not configured",
)

data class NtripCardState(
    val url: String = "n/a",
    val status: String = "n/a",
    val transferred: String = "n/a",
    val stationId: String = "n/a",
    val baseLatLon: String = "n/a",
    val rates: String = "n/a",
)

data class FilesCardState(
    val sessionLocation: String = "n/a",
    val receiverRxBytes: String = "0 B",
    val txToReceiverBytes: String = "0 B",
    val ntripBytes: String = "0 B",
    val nmeaBytes: String = "0 B",
    val zipShareEnabled: Boolean = false,
)

data class DashboardAction(
    val label: String,
    val kind: DashboardActionKind,
)

enum class DashboardActionKind {
    START,
    STOP,
    NTRIP,
    MARK,
    MENU,
    SHARE_ZIP,
    NEW_SESSION,
}
```

- [ ] **Step 5: Add formatters**

Create `DashboardFormatters.kt`:

```kotlin
package org.rtkcollector.app.ui.dashboard

import java.util.Locale
import kotlin.math.abs

fun formatDistance(meters: Double?): String {
    val value = meters ?: return "n/a"
    val absValue = abs(value)
    return when {
        absValue < 0.01 -> "${(value * 1000).roundInt()} mm"
        absValue < 1.0 -> "${(value * 100).roundInt()} cm"
        absValue < 1000.0 -> "${value.oneDecimal()} m"
        else -> "${(value / 1000.0).oneDecimal()} km"
    }
}

fun formatBytes(bytes: Long?): String {
    val value = bytes ?: return "n/a"
    return when {
        value < 1000 -> "$value B"
        value < 1_000_000 -> "${(value / 1000.0).oneDecimal()} kB"
        value < 1_000_000_000 -> "${(value / 1_000_000.0).oneDecimal()} MB"
        else -> "${(value / 1_000_000_000.0).oneDecimal()} GB"
    }
}

fun formatRate(bytesPerSecond: Double?): String {
    val value = bytesPerSecond ?: return "n/a"
    return when {
        value < 1000 -> "${value.roundInt()} B/s"
        value < 1_000_000 -> "${(value / 1000.0).oneDecimal()} kB/s"
        else -> "${(value / 1_000_000.0).oneDecimal()} MB/s"
    }
}

fun interpretGgaFixQuality(quality: Int?): String =
    when (quality) {
        null -> "n/a"
        0 -> "Invalid"
        1 -> "Single"
        2 -> "DGPS"
        4 -> "RTK fix"
        5 -> "RTK float"
        6 -> "Estimated"
        9 -> "PPP"
        else -> "Quality $quality"
    }

private fun Double.oneDecimal(): String =
    String.format(Locale.US, "%.1f", this)

private fun Double.roundInt(): Int =
    kotlin.math.roundToInt()
```

- [ ] **Step 6: Run tests and commit**

Run:

```bash
ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK ANDROID_SDK_ROOT=/storage/3830-3863/Termux/AndroidSDK sh gradlew :app:testDebugUnitTest --tests 'org.rtkcollector.app.ui.dashboard.*'
```

Expected: pass.

Commit:

```bash
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector add app/src/main/kotlin/org/rtkcollector/app/ui/dashboard app/src/test/kotlin/org/rtkcollector/app/ui/dashboard
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector commit -m "Model Compose dashboard state"
```

---

### Task 3: Build The Compact Compose Dashboard

**Files:**
- Create: `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/HomeDashboard.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`

- [ ] **Step 1: Create the dashboard composable**

Create `HomeDashboard.kt`:

```kotlin
package org.rtkcollector.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeDashboard(
    state: DashboardState,
    onPrimaryAction: () -> Unit,
    onMenu: () -> Unit,
    onNtrip: () -> Unit,
    onMark: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("RtkCollector") },
                actions = {
                    Text(
                        text = if (state.isRecording) "Recording" else "Ready",
                        modifier = Modifier.padding(end = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    TextButton(onClick = onMenu) { Text("Menu") }
                },
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onPrimaryAction,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(state.primaryAction.label)
                }
                state.secondaryActions.filter { it.kind == DashboardActionKind.NTRIP || it.kind == DashboardActionKind.MARK }.forEach { action ->
                    TextButton(
                        onClick = when (action.kind) {
                            DashboardActionKind.NTRIP -> onNtrip
                            DashboardActionKind.MARK -> onMark
                            else -> onMenu
                        },
                    ) {
                        Text(action.label)
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatusStrip(state.status)
            DashboardCards(state)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusStrip(status: DashboardStatus) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        StatusChip("Workflow", status.workflow)
        StatusChip("Mountpoint", status.mountpoint)
        StatusChip("Receiver", status.receiver)
        StatusChip("Storage", status.storage)
    }
}

@Composable
private fun StatusChip(label: String, value: String) {
    AssistChip(
        onClick = {},
        label = { Text("$label: $value") },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DashboardCards(state: DashboardState) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        DashboardCard("Position", modifier = Modifier.widthIn(min = 260.dp, max = 380.dp)) {
            MajorValue(state.position.latLon)
            Metric("UTC", state.position.utcTime)
            Metric("Ell. height", state.position.ellipsoidalHeight)
            Metric("Altitude", state.position.altitude)
            Metric("Lat error", state.position.latError)
            Metric("Lon error", state.position.lonError)
        }
        DashboardCard("Fix", modifier = Modifier.widthIn(min = 260.dp, max = 380.dp)) {
            MajorValue(state.fix.fixType)
            Metric("Sats", state.fix.satellites)
            Metric("PDOP", state.fix.pdop)
            Metric("HDOP / VDOP", state.fix.hdopVdop)
            Metric("H accuracy", state.fix.horizontalAccuracy)
            Metric("V accuracy", state.fix.verticalAccuracy)
            Metric("Diff age", state.fix.differentialAge)
            Metric("Baseline", state.fix.baseline)
            Metric("PPP", state.fix.pppStatus)
            Metric("RTKLIB", state.fix.rtklibStatus)
        }
        DashboardCard("NTRIP", modifier = Modifier.widthIn(min = 260.dp, max = 380.dp)) {
            MajorValue(state.ntrip.status)
            Metric("URL", state.ntrip.url)
            Metric("Transferred", state.ntrip.transferred)
            Metric("Station ID", state.ntrip.stationId)
            Metric("Base lat/lon", state.ntrip.baseLatLon)
            Metric("Rates", state.ntrip.rates)
        }
        DashboardCard("Files", modifier = Modifier.widthIn(min = 260.dp, max = 380.dp)) {
            MajorValue(state.files.sessionLocation)
            Metric("RX raw", state.files.receiverRxBytes)
            Metric("TX rover", state.files.txToReceiverBytes)
            Metric("NTRIP raw", state.files.ntripBytes)
            Metric("NMEA", state.files.nmeaBytes)
        }
    }
}

@Composable
private fun DashboardCard(title: String, modifier: Modifier = Modifier, content: @Composable Column.() -> Unit) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            content = {
                Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(2.dp))
                content()
            },
        )
    }
}

@Composable
private fun MajorValue(value: String) {
    Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@Composable
private fun Metric(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 780)
@Composable
private fun HomeDashboardPreview() {
    MaterialTheme {
        HomeDashboard(
            state = DashboardState.running(
                status = DashboardStatus("Rover + NTRIP", "TUBO00CZE0", "UM980", "SAF folder"),
                position = PositionCardState(
                    latLon = "50.087451234, 14.421253456",
                    ellipsoidalHeight = "287.423 m",
                    altitude = "243.812 m",
                    utcTime = "2026-06-08 15:34:12",
                    latError = "8 mm",
                    lonError = "7 mm",
                ),
                fix = FixCardState(
                    fixType = "RTK float",
                    satellites = "18 / 31",
                    pdop = "1.2",
                    hdopVdop = "0.7 / 1.0",
                    horizontalAccuracy = "3 cm",
                    verticalAccuracy = "6 cm",
                    differentialAge = "0.8 s",
                    baseline = "42.8 km",
                    pppStatus = "Converging",
                ),
                ntrip = NtripCardState(
                    url = "www.euref-ip.be:2101/TUBO00CZE0",
                    status = "Streaming",
                    transferred = "2.1 MB",
                    stationId = "1234",
                    baseLatLon = "49.123456, 14.987654",
                    rates = "1.8 / 1.8 kB/s",
                ),
                files = FilesCardState(".../RtkCollector/2026-06-08", "18.4 MB", "2.1 MB", "2.1 MB", "431 kB"),
            ),
            onPrimaryAction = {},
            onMenu = {},
            onNtrip = {},
            onMark = {},
        )
    }
}
```

- [ ] **Step 2: Wire the dashboard in the app shell**

Update `RtkCollectorApp()` in `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`:

```kotlin
@Composable
fun RtkCollectorApp() {
    val state = DashboardState.planned(
        workflow = "Plain rover recording",
        mountpoint = "n/a",
        receiver = "UM980",
        storage = "App-private",
    )
    MaterialTheme {
        HomeDashboard(
            state = state,
            onPrimaryAction = {},
            onMenu = {},
            onNtrip = {},
            onMark = {},
        )
    }
}
```

Add imports:

```kotlin
import org.rtkcollector.app.ui.dashboard.DashboardState
import org.rtkcollector.app.ui.dashboard.HomeDashboard
```

- [ ] **Step 3: Compile**

Run:

```bash
ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK ANDROID_SDK_ROOT=/storage/3830-3863/Termux/AndroidSDK sh gradlew :app:compileDebugKotlin
```

Expected: pass.

- [ ] **Step 4: Commit**

```bash
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector add app/src/main/kotlin/org/rtkcollector/app/ui
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector commit -m "Render compact Compose dashboard"
```

---

### Task 4: Profile Operations And Workflow Defaults

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/profile/ProfileModels.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/profile/ProfileStores.kt`
- Create: `app/src/test/kotlin/org/rtkcollector/app/profile/ProfileStoresTest.kt`

- [ ] **Step 1: Add failing tests for profile copy, rename and defaults**

Create `ProfileStoresTest.kt` with Robolectric-free tests around pure helper functions. If `ProfileStores` remains Android `SharedPreferences` backed, place pure operations in `ProfileModels.kt` as top-level functions and test those:

```kotlin
package org.rtkcollector.app.profile

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ProfileStoresTest {
    @Test
    fun `rename updates selected user profile`() {
        val profiles = listOf(CommandProfile(id = "p1", name = "Old", protected = false))

        val updated = renameProfile(profiles, "p1", "New") { profile, name -> profile.copy(name = name) }

        assertEquals("New", updated.single().name)
    }

    @Test
    fun `protected profile cannot be renamed`() {
        val profiles = listOf(CommandProfile(id = "p1", name = "Default", protected = true))

        assertThrows(IllegalArgumentException::class.java) {
            renameProfile(profiles, "p1", "New") { profile, name -> profile.copy(name = name) }
        }
    }

    @Test
    fun `workflow defaults carry profile references`() {
        val defaults = WorkflowProfileDefaults(
            workflowId = "rover-ntrip",
            receiverProfileId = "um980",
            commandProfileId = "cmd",
            usbBaudProfileId = "usb",
            ntripCasterProfileId = "caster",
            ntripMountpointProfileId = "mount",
            recordingOutputProfileId = "recording",
            storageProfileId = "storage",
        )

        assertEquals("mount", defaults.ntripMountpointProfileId)
    }
}
```

- [ ] **Step 2: Run tests and verify failure**

Run:

```bash
ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK ANDROID_SDK_ROOT=/storage/3830-3863/Termux/AndroidSDK sh gradlew :app:testDebugUnitTest --tests 'org.rtkcollector.app.profile.ProfileStoresTest'
```

Expected: fail because `protected`, `WorkflowProfileDefaults` and `renameProfile` do not exist.

- [ ] **Step 3: Extend profile models**

Add `protected: Boolean = false` to every profile data class constructor and JSON round-trip:

```kotlin
val protected: Boolean = false,
```

For JSON output add:

```kotlin
.put("protected", protected)
```

For JSON input add:

```kotlin
protected = json.optBoolean("protected", false),
```

Add:

```kotlin
data class WorkflowProfileDefaults(
    val workflowId: String,
    val receiverProfileId: String,
    val commandProfileId: String,
    val usbBaudProfileId: String,
    val ntripCasterProfileId: String?,
    val ntripMountpointProfileId: String?,
    val recordingOutputProfileId: String,
    val storageProfileId: String,
) {
    fun validate() {
        require(workflowId.isNotBlank()) { "Workflow default id must not be blank." }
        require(receiverProfileId.isNotBlank()) { "Workflow default receiver profile id must not be blank." }
        require(commandProfileId.isNotBlank()) { "Workflow default command profile id must not be blank." }
        require(usbBaudProfileId.isNotBlank()) { "Workflow default USB/baud profile id must not be blank." }
        require(recordingOutputProfileId.isNotBlank()) { "Workflow default recording output profile id must not be blank." }
        require(storageProfileId.isNotBlank()) { "Workflow default storage profile id must not be blank." }
    }
}

fun <T> renameProfile(
    profiles: List<T>,
    id: String,
    newName: String,
    rename: (T, String) -> T,
    idOf: (T) -> String = { it.profileId() },
    protectedOf: (T) -> Boolean = { it.isProtectedProfile() },
): List<T> {
    require(newName.isNotBlank()) { "Profile name must not be blank." }
    return profiles.map { profile ->
        if (idOf(profile) == id) {
            require(!protectedOf(profile)) { "Protected profile must be copied before editing." }
            rename(profile, newName)
        } else {
            profile
        }
    }
}

private fun Any.profileId(): String =
    when (this) {
        is CommandProfile -> id
        is UsbBaudProfile -> id
        is NtripCasterProfile -> id
        is NtripMountpointProfile -> id
        is RecordingPolicyProfile -> id
        is StorageProfile -> id
        else -> error("Unsupported profile type: ${this::class.java.name}")
    }

private fun Any.isProtectedProfile(): Boolean =
    when (this) {
        is CommandProfile -> protected
        is UsbBaudProfile -> protected
        is NtripCasterProfile -> protected
        is NtripMountpointProfile -> protected
        is RecordingPolicyProfile -> protected
        is StorageProfile -> protected
        else -> error("Unsupported profile type: ${this::class.java.name}")
    }
```

If Kotlin type inference complains about default lambdas, replace calls in tests with explicit `idOf = { it.id }` and `protectedOf = { it.protected }`.

- [ ] **Step 4: Mark built-in defaults as protected**

In `ProfileStores.defaultCommandProfiles()` and other default profile builders, set:

```kotlin
protected = true
```

- [ ] **Step 5: Run tests and commit**

Run:

```bash
ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK ANDROID_SDK_ROOT=/storage/3830-3863/Termux/AndroidSDK sh gradlew :app:testDebugUnitTest --tests 'org.rtkcollector.app.profile.ProfileStoresTest'
```

Expected: pass.

Commit:

```bash
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector add app/src/main/kotlin/org/rtkcollector/app/profile app/src/test/kotlin/org/rtkcollector/app/profile/ProfileStoresTest.kt
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector commit -m "Model editable recording profiles"
```

---

### Task 5: NTRIP Mountpoint Selection State

**Files:**
- Create: `app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileEditorModels.kt`
- Create: `app/src/test/kotlin/org/rtkcollector/app/ui/profiles/NtripMountpointSelectionTest.kt`

- [ ] **Step 1: Write failing mountpoint overwrite tests**

Create `NtripMountpointSelectionTest.kt`:

```kotlin
package org.rtkcollector.app.ui.profiles

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NtripMountpointSelectionTest {
    @Test
    fun `fetching mountpoints does not overwrite typed mountpoint`() {
        val state = NtripMountpointEditorState(mountpointText = "USER_TYPED")

        val updated = state.withFetchedMountpoints(listOf("FIRST", "SECOND"))

        assertEquals("USER_TYPED", updated.mountpointText)
        assertEquals(listOf("FIRST", "SECOND"), updated.availableMountpoints)
    }

    @Test
    fun `explicit selection updates mountpoint text`() {
        val state = NtripMountpointEditorState(
            mountpointText = "USER_TYPED",
            availableMountpoints = listOf("FIRST", "SECOND"),
        )

        val updated = state.selectMountpoint("SECOND")

        assertEquals("SECOND", updated.mountpointText)
    }
}
```

- [ ] **Step 2: Run tests and verify failure**

Run:

```bash
ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK ANDROID_SDK_ROOT=/storage/3830-3863/Termux/AndroidSDK sh gradlew :app:testDebugUnitTest --tests 'org.rtkcollector.app.ui.profiles.NtripMountpointSelectionTest'
```

Expected: fail because `NtripMountpointEditorState` does not exist.

- [ ] **Step 3: Add the state model**

Create `ProfileEditorModels.kt`:

```kotlin
package org.rtkcollector.app.ui.profiles

data class NtripMountpointEditorState(
    val mountpointText: String = "",
    val availableMountpoints: List<String> = emptyList(),
) {
    fun withFetchedMountpoints(mountpoints: List<String>): NtripMountpointEditorState =
        copy(availableMountpoints = mountpoints.filter(String::isNotBlank).distinct())

    fun selectMountpoint(mountpoint: String): NtripMountpointEditorState {
        require(mountpoint.isNotBlank()) { "Selected mountpoint must not be blank." }
        require(availableMountpoints.isEmpty() || mountpoint in availableMountpoints) {
            "Selected mountpoint is not in the fetched list."
        }
        return copy(mountpointText = mountpoint)
    }
}
```

- [ ] **Step 4: Run tests and commit**

Run:

```bash
ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK ANDROID_SDK_ROOT=/storage/3830-3863/Termux/AndroidSDK sh gradlew :app:testDebugUnitTest --tests 'org.rtkcollector.app.ui.profiles.NtripMountpointSelectionTest'
```

Expected: pass.

Commit:

```bash
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector add app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileEditorModels.kt app/src/test/kotlin/org/rtkcollector/app/ui/profiles/NtripMountpointSelectionTest.kt
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector commit -m "Prevent implicit NTRIP mountpoint overwrite"
```

---

### Task 6: Settings Hub And Profile Screens

**Files:**
- Create: `app/src/main/kotlin/org/rtkcollector/app/ui/settings/SettingsHub.kt`
- Create: `app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileScreens.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`

- [ ] **Step 1: Add settings navigation state**

In `MainActivity.kt`, introduce:

```kotlin
private enum class AppScreen {
    HOME,
    SETTINGS,
    NTRIP_CASTER,
    NTRIP_MOUNTPOINT,
    COMMANDS,
    RECORDING_OUTPUTS,
    STORAGE,
    SESSIONS,
}
```

Inside `RtkCollectorApp()` use:

```kotlin
var screen by remember { mutableStateOf(AppScreen.HOME) }
```

Add imports:

```kotlin
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
```

- [ ] **Step 2: Create settings hub**

Create `SettingsHub.kt`:

```kotlin
package org.rtkcollector.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsHub(
    onNtripCaster: () -> Unit,
    onNtripMountpoint: () -> Unit,
    onCommands: () -> Unit,
    onRecordingOutputs: () -> Unit,
    onStorage: () -> Unit,
    onSessions: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        SettingsButton("NTRIP casters", onNtripCaster)
        SettingsButton("NTRIP mountpoints", onNtripMountpoint)
        SettingsButton("Command scripts", onCommands)
        SettingsButton("Recording outputs", onRecordingOutputs)
        SettingsButton("Storage", onStorage)
        SettingsButton("Sessions", onSessions)
        SettingsButton("Back", onBack)
    }
}

@Composable
private fun SettingsButton(label: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(label)
    }
}
```

- [ ] **Step 3: Create initial profile screens as real navigation targets**

Create `ProfileScreens.kt`:

```kotlin
package org.rtkcollector.app.ui.profiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun NtripMountpointScreen(
    initialState: NtripMountpointEditorState,
    onBack: () -> Unit,
) {
    var state by remember { mutableStateOf(initialState) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("NTRIP mountpoint", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = state.mountpointText,
            onValueChange = { state = state.copy(mountpointText = it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Mountpoint") },
        )
        Button(
            onClick = { state = state.withFetchedMountpoints(listOf("TUBO00CZE0", "DRES00DEU0")) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Load sample fetched list")
        }
        state.availableMountpoints.forEach { mountpoint ->
            Button(
                onClick = { state = state.selectMountpoint(mountpoint) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(mountpoint)
            }
        }
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Back")
        }
    }
}

@Composable
fun SimpleSettingsScreen(title: String, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text("Profile editor screen for $title")
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Back")
        }
    }
}
```

- [ ] **Step 4: Wire screens in `RtkCollectorApp()`**

Use this `when` body:

```kotlin
when (screen) {
    AppScreen.HOME -> HomeDashboard(
        state = state,
        onPrimaryAction = {},
        onMenu = { screen = AppScreen.SETTINGS },
        onNtrip = { screen = AppScreen.NTRIP_MOUNTPOINT },
        onMark = {},
    )
    AppScreen.SETTINGS -> SettingsHub(
        onNtripCaster = { screen = AppScreen.NTRIP_CASTER },
        onNtripMountpoint = { screen = AppScreen.NTRIP_MOUNTPOINT },
        onCommands = { screen = AppScreen.COMMANDS },
        onRecordingOutputs = { screen = AppScreen.RECORDING_OUTPUTS },
        onStorage = { screen = AppScreen.STORAGE },
        onSessions = { screen = AppScreen.SESSIONS },
        onBack = { screen = AppScreen.HOME },
    )
    AppScreen.NTRIP_MOUNTPOINT -> NtripMountpointScreen(
        initialState = NtripMountpointEditorState(mountpointText = "TUBO00CZE0"),
        onBack = { screen = AppScreen.SETTINGS },
    )
    AppScreen.NTRIP_CASTER -> SimpleSettingsScreen("NTRIP caster", onBack = { screen = AppScreen.SETTINGS })
    AppScreen.COMMANDS -> SimpleSettingsScreen("Command scripts", onBack = { screen = AppScreen.SETTINGS })
    AppScreen.RECORDING_OUTPUTS -> SimpleSettingsScreen("Recording outputs", onBack = { screen = AppScreen.SETTINGS })
    AppScreen.STORAGE -> SimpleSettingsScreen("Storage", onBack = { screen = AppScreen.SETTINGS })
    AppScreen.SESSIONS -> SimpleSettingsScreen("Sessions", onBack = { screen = AppScreen.SETTINGS })
}
```

Add imports for `SettingsHub`, `NtripMountpointEditorState`, `NtripMountpointScreen` and `SimpleSettingsScreen`.

- [ ] **Step 5: Compile and commit**

Run:

```bash
ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK ANDROID_SDK_ROOT=/storage/3830-3863/Termux/AndroidSDK sh gradlew :app:compileDebugKotlin
```

Expected: pass.

Commit:

```bash
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector add app/src/main/kotlin/org/rtkcollector/app/ui
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector commit -m "Add Compose settings navigation"
```

---

### Task 7: UM980 Binary Frame And BESTNAVB Parser

**Files:**
- Create: `receiver/unicore-n4/src/main/kotlin/org/rtkcollector/receiver/unicore/Um980Telemetry.kt`
- Create: `receiver/unicore-n4/src/main/kotlin/org/rtkcollector/receiver/unicore/Um980BinaryParser.kt`
- Create: `receiver/unicore-n4/src/test/kotlin/org/rtkcollector/receiver/unicore/Um980BinaryParserTest.kt`

- [ ] **Step 1: Write binary parser tests**

Create `Um980BinaryParserTest.kt`:

```kotlin
package org.rtkcollector.receiver.unicore

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Um980BinaryParserTest {
    @Test
    fun `parses documented BESTNAVB solution fields`() {
        val frame = bestnavbFrame()

        val telemetry = Um980BinaryParser.parseBestnavb(frame)

        requireNotNull(telemetry)
        assertEquals("BESTNAVB", telemetry.source)
        assertEquals("SOL_COMPUTED", telemetry.solutionStatus)
        assertEquals("NARROW_INT", telemetry.positionType)
        assertEquals(50.087451234, telemetry.latDeg)
        assertEquals(14.421253456, telemetry.lonDeg)
        assertEquals(243.812, telemetry.altitudeM)
        assertEquals(287.423, telemetry.ellipsoidalHeightM)
        assertEquals(0.008, telemetry.latErrorM)
        assertEquals(0.007, telemetry.lonErrorM)
        assertEquals(18, telemetry.satellitesUsed)
        assertEquals(31, telemetry.satellitesInView)
        assertEquals(0.8, telemetry.differentialAgeS)
        assertEquals("1234", telemetry.stationId)
    }

    @Test
    fun `returns null for non BESTNAVB message id`() {
        val frame = bestnavbFrame(messageId = 999)

        assertNull(Um980BinaryParser.parseBestnavb(frame))
    }

    private fun bestnavbFrame(messageId: Int = 2118): ByteArray {
        val payloadLength = 120
        val frame = ByteArray(24 + payloadLength + 4)
        frame[0] = 0xAA.toByte()
        frame[1] = 0x44
        frame[2] = 0xB5.toByte()
        frame[3] = 24
        putU16(frame, 4, messageId)
        putU16(frame, 6, payloadLength)
        putU16(frame, 10, 2419)
        putU32(frame, 12, 132_572_000)
        val payload = ByteBuffer.wrap(frame, 24, payloadLength).order(ByteOrder.LITTLE_ENDIAN)
        payload.putInt(0, 0)
        payload.putInt(4, 50)
        payload.putDouble(8, 50.087451234)
        payload.putDouble(16, 14.421253456)
        payload.putDouble(24, 243.812)
        payload.putFloat(32, 43.611f)
        payload.putFloat(40, 0.008f)
        payload.putFloat(44, 0.007f)
        payload.putFloat(48, 0.06f)
        "1234".encodeToByteArray().copyInto(frame, destinationOffset = 24 + 52)
        payload.putFloat(56, 0.8f)
        payload.put(64, 31)
        payload.put(65, 18)
        payload.putInt(72, 0)
        payload.putInt(76, 50)
        return frame
    }

    private fun putU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value and 0xff).toByte()
        bytes[offset + 1] = ((value ushr 8) and 0xff).toByte()
    }

    private fun putU32(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value and 0xff).toByte()
        bytes[offset + 1] = ((value ushr 8) and 0xff).toByte()
        bytes[offset + 2] = ((value ushr 16) and 0xff).toByte()
        bytes[offset + 3] = ((value ushr 24) and 0xff).toByte()
    }
}
```

- [ ] **Step 2: Run tests and verify failure**

Run:

```bash
ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK ANDROID_SDK_ROOT=/storage/3830-3863/Termux/AndroidSDK sh gradlew :receiver:unicore-n4:test --tests 'org.rtkcollector.receiver.unicore.Um980BinaryParserTest'
```

Expected: fail because parser/model do not exist.

- [ ] **Step 3: Add telemetry model**

Create `Um980Telemetry.kt`:

```kotlin
package org.rtkcollector.receiver.unicore

data class Um980Telemetry(
    val source: String,
    val utcTime: String? = null,
    val solutionStatus: String? = null,
    val positionType: String? = null,
    val latDeg: Double? = null,
    val lonDeg: Double? = null,
    val altitudeM: Double? = null,
    val ellipsoidalHeightM: Double? = null,
    val latErrorM: Double? = null,
    val lonErrorM: Double? = null,
    val verticalAccuracyM: Double? = null,
    val satellitesInView: Int? = null,
    val satellitesUsed: Int? = null,
    val pdop: Double? = null,
    val hdop: Double? = null,
    val vdop: Double? = null,
    val differentialAgeS: Double? = null,
    val baselineLengthM: Double? = null,
    val stationId: String? = null,
)
```

- [ ] **Step 4: Add BESTNAVB parser**

Create `Um980BinaryParser.kt`:

```kotlin
package org.rtkcollector.receiver.unicore

import java.nio.ByteBuffer
import java.nio.ByteOrder

object Um980BinaryParser {
    private const val BESTNAVB_MESSAGE_ID = 2118
    private const val BINARY_HEADER_LENGTH = 24
    private const val BESTNAVB_MIN_PAYLOAD_LENGTH = 120

    private val solutionStatusNames = mapOf(0 to "SOL_COMPUTED")
    private val positionTypeNames = mapOf(50 to "NARROW_INT")

    fun parseBestnavb(frame: ByteArray): Um980Telemetry? {
        if (frame.size < BINARY_HEADER_LENGTH + BESTNAVB_MIN_PAYLOAD_LENGTH + 4) return null
        if (frame[0] != 0xAA.toByte() || frame[1] != 0x44.toByte() || frame[2] != 0xB5.toByte()) return null
        val messageId = u16(frame, 4)
        if (messageId != BESTNAVB_MESSAGE_ID) return null
        val payloadLength = u16(frame, 6)
        if (payloadLength < BESTNAVB_MIN_PAYLOAD_LENGTH) return null
        val payload = ByteBuffer.wrap(frame, BINARY_HEADER_LENGTH, payloadLength).order(ByteOrder.LITTLE_ENDIAN)
        val altitudeM = payload.getDouble(24)
        val undulationM = payload.getFloat(32).toDouble()
        return Um980Telemetry(
            source = "BESTNAVB",
            solutionStatus = solutionStatusNames[payload.getInt(0)] ?: "STATUS_${payload.getInt(0)}",
            positionType = positionTypeNames[payload.getInt(4)] ?: "TYPE_${payload.getInt(4)}",
            latDeg = payload.getDouble(8),
            lonDeg = payload.getDouble(16),
            altitudeM = altitudeM,
            ellipsoidalHeightM = altitudeM + undulationM,
            latErrorM = payload.getFloat(40).toDouble(),
            lonErrorM = payload.getFloat(44).toDouble(),
            verticalAccuracyM = payload.getFloat(48).toDouble(),
            stationId = stationId(frame.copyOfRange(BINARY_HEADER_LENGTH + 52, BINARY_HEADER_LENGTH + 56)),
            differentialAgeS = payload.getFloat(56).toDouble(),
            satellitesInView = payload.get(64).toInt() and 0xff,
            satellitesUsed = payload.get(65).toInt() and 0xff,
        )
    }

    private fun u16(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)

    private fun stationId(bytes: ByteArray): String? =
        bytes.takeWhile { it != 0.toByte() }
            .toByteArray()
            .decodeToString()
            .filter { it.isLetterOrDigit() || it == '_' || it == '-' }
            .takeIf(String::isNotBlank)
}
```

- [ ] **Step 5: Run tests and commit**

Run:

```bash
ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK ANDROID_SDK_ROOT=/storage/3830-3863/Termux/AndroidSDK sh gradlew :receiver:unicore-n4:test --tests 'org.rtkcollector.receiver.unicore.Um980BinaryParserTest'
```

Expected: pass.

Commit:

```bash
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector add receiver/unicore-n4/src/main/kotlin/org/rtkcollector/receiver/unicore/Um980Telemetry.kt receiver/unicore-n4/src/main/kotlin/org/rtkcollector/receiver/unicore/Um980BinaryParser.kt receiver/unicore-n4/src/test/kotlin/org/rtkcollector/receiver/unicore/Um980BinaryParserTest.kt
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector commit -m "Parse UM980 BESTNAVB telemetry"
```

---

### Task 8: UM980 Mixed Stream Parser

**Files:**
- Create: `receiver/unicore-n4/src/main/kotlin/org/rtkcollector/receiver/unicore/Um980StreamParser.kt`
- Create: `receiver/unicore-n4/src/test/kotlin/org/rtkcollector/receiver/unicore/Um980StreamParserTest.kt`

- [ ] **Step 1: Write mixed-stream tests**

Create `Um980StreamParserTest.kt`:

```kotlin
package org.rtkcollector.receiver.unicore

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class Um980StreamParserTest {
    @Test
    fun `classifies nmea ascii binary and noise records`() {
        val binary = byteArrayOf(0xAA.toByte(), 0x44, 0xB5.toByte(), 24, 0, 0, 0, 0)
        val bytes = "xx\$GPGGA,123519,4807.038,N,01131.000,E,4,12,0.8,545.4,M,46.9,M,,*47\r\n".encodeToByteArray() +
            "#BESTNAVA,COM1,0,80.0,FINE,2300,1000,0,0,18,0;SOL_COMPUTED,NARROW_INT,50.0,14.0,300.0,0.1,0.1,0.2,12,12*00000000\r\n".encodeToByteArray() +
            binary

        val records = Um980StreamParser().accept(bytes)

        assertEquals(listOf("noise", "nmea", "unicore_ascii", "unicore_binary"), records.map { it.kind })
    }
}
```

- [ ] **Step 2: Run test and verify failure**

Run:

```bash
ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK ANDROID_SDK_ROOT=/storage/3830-3863/Termux/AndroidSDK sh gradlew :receiver:unicore-n4:test --tests 'org.rtkcollector.receiver.unicore.Um980StreamParserTest'
```

Expected: fail because stream parser does not exist.

- [ ] **Step 3: Add stream parser**

Create `Um980StreamParser.kt`:

```kotlin
package org.rtkcollector.receiver.unicore

data class Um980StreamRecord(
    val kind: String,
    val bytes: ByteArray,
    val text: String? = null,
)

class Um980StreamParser {
    fun accept(input: ByteArray): List<Um980StreamRecord> {
        val records = mutableListOf<Um980StreamRecord>()
        var index = 0
        while (index < input.size) {
            when {
                input[index] == '$'.code.toByte() -> {
                    val end = findLineEnd(input, index)
                    if (end > index) {
                        val bytes = input.copyOfRange(index, end)
                        records += Um980StreamRecord("nmea", bytes, bytes.decodeToString())
                        index = end
                    } else {
                        records += Um980StreamRecord("noise", input.copyOfRange(index, input.size))
                        index = input.size
                    }
                }
                input[index] == '#'.code.toByte() -> {
                    val end = findLineEnd(input, index)
                    if (end > index) {
                        val bytes = input.copyOfRange(index, end)
                        records += Um980StreamRecord("unicore_ascii", bytes, bytes.decodeToString())
                        index = end
                    } else {
                        records += Um980StreamRecord("noise", input.copyOfRange(index, input.size))
                        index = input.size
                    }
                }
                hasBinarySync(input, index) -> {
                    val end = (index + 8).coerceAtMost(input.size)
                    records += Um980StreamRecord("unicore_binary", input.copyOfRange(index, end))
                    index = end
                }
                else -> {
                    val next = nextKnownStart(input, index + 1)
                    records += Um980StreamRecord("noise", input.copyOfRange(index, next))
                    index = next
                }
            }
        }
        return records
    }

    private fun findLineEnd(input: ByteArray, start: Int): Int {
        for (i in start until input.size) {
            if (input[i] == '\n'.code.toByte()) return i + 1
        }
        return -1
    }

    private fun nextKnownStart(input: ByteArray, start: Int): Int {
        for (i in start until input.size) {
            if (input[i] == '$'.code.toByte() || input[i] == '#'.code.toByte() || hasBinarySync(input, i)) return i
        }
        return input.size
    }

    private fun hasBinarySync(input: ByteArray, index: Int): Boolean =
        index + 2 < input.size &&
            input[index] == 0xAA.toByte() &&
            input[index + 1] == 0x44.toByte() &&
            input[index + 2] == 0xB5.toByte()
}
```

This first parser classifies records. A later step can replace the fixed 8-byte binary slice with length/CRC-aware framing while preserving the public record shape.

- [ ] **Step 4: Run tests and commit**

Run:

```bash
ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK ANDROID_SDK_ROOT=/storage/3830-3863/Termux/AndroidSDK sh gradlew :receiver:unicore-n4:test --tests 'org.rtkcollector.receiver.unicore.Um980StreamParserTest'
```

Expected: pass.

Commit:

```bash
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector add receiver/unicore-n4/src/main/kotlin/org/rtkcollector/receiver/unicore/Um980StreamParser.kt receiver/unicore-n4/src/test/kotlin/org/rtkcollector/receiver/unicore/Um980StreamParserTest.kt
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector commit -m "Classify mixed UM980 streams"
```

---

### Task 9: Recording Service Telemetry State

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingServiceState.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`

- [ ] **Step 1: Expand service state**

Add fields to `RecordingServiceState`:

```kotlin
val workflowLabel: String = "n/a",
val receiverLabel: String = "n/a",
val storageLabel: String = "n/a",
val ntripUrl: String = "n/a",
val latLon: String = "n/a",
val ellipsoidalHeight: String = "n/a",
val altitude: String = "n/a",
val utcTime: String = "n/a",
val satellites: String = "n/a",
val pdop: String = "n/a",
val hdopVdop: String = "n/a",
val horizontalAccuracy: String = "n/a",
val verticalAccuracy: String = "n/a",
val differentialAge: String = "n/a",
val baseline: String = "n/a",
val ntripTransferred: String = "0 B",
val ntripRates: String = "n/a",
val ntripStationId: String = "n/a",
val ntripBaseLatLon: String = "n/a",
val nmeaBytes: Long = 0,
```

- [ ] **Step 2: Broadcast the fields**

Add constants in `RecordingForegroundService.Companion`:

```kotlin
const val EXTRA_STATE_WORKFLOW_LABEL = "workflowLabel"
const val EXTRA_STATE_RECEIVER_LABEL = "receiverLabel"
const val EXTRA_STATE_STORAGE_LABEL = "storageLabel"
const val EXTRA_STATE_NTRIP_URL = "ntripUrl"
const val EXTRA_STATE_LAT_LON = "latLon"
const val EXTRA_STATE_ELLIPSOIDAL_HEIGHT = "ellipsoidalHeight"
const val EXTRA_STATE_ALTITUDE = "altitude"
const val EXTRA_STATE_UTC_TIME = "utcTime"
const val EXTRA_STATE_SATELLITES = "satellites"
const val EXTRA_STATE_PDOP = "pdop"
const val EXTRA_STATE_HDOP_VDOP = "hdopVdop"
const val EXTRA_STATE_HORIZONTAL_ACCURACY = "horizontalAccuracy"
const val EXTRA_STATE_VERTICAL_ACCURACY = "verticalAccuracy"
const val EXTRA_STATE_DIFFERENTIAL_AGE = "differentialAge"
const val EXTRA_STATE_BASELINE = "baseline"
const val EXTRA_STATE_NTRIP_TRANSFERRED = "ntripTransferred"
const val EXTRA_STATE_NTRIP_RATES = "ntripRates"
const val EXTRA_STATE_NTRIP_STATION_ID = "ntripStationId"
const val EXTRA_STATE_NTRIP_BASE_LAT_LON = "ntripBaseLatLon"
const val EXTRA_STATE_NMEA_BYTES = "nmeaBytes"
```

In `broadcastState()`, add matching `putExtra(...)` calls.

- [ ] **Step 3: Map service state to dashboard state**

In `MainActivity.kt`, create a mapper:

```kotlin
private fun dashboardStateFrom(intent: Intent): DashboardState {
    val running = intent.getBooleanExtra(RecordingForegroundService.EXTRA_STATE_RUNNING, false)
    val status = DashboardStatus(
        workflow = intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_WORKFLOW_LABEL) ?: "n/a",
        mountpoint = intent.getStringExtra(RecordingForegroundService.EXTRA_NTRIP_MOUNTPOINT) ?: "n/a",
        receiver = intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_RECEIVER_LABEL) ?: "n/a",
        storage = intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_STORAGE_LABEL) ?: "n/a",
    )
    val position = PositionCardState(
        latLon = intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_LAT_LON) ?: "n/a",
        ellipsoidalHeight = intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_ELLIPSOIDAL_HEIGHT) ?: "n/a",
        altitude = intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_ALTITUDE) ?: "n/a",
        utcTime = intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_UTC_TIME) ?: "n/a",
    )
    val fix = FixCardState(
        fixType = interpretGgaFixQuality(intent.getIntExtra(RecordingForegroundService.EXTRA_STATE_GGA_FIX_QUALITY, -1).takeIf { it >= 0 }),
        satellites = intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_SATELLITES) ?: "n/a",
        pdop = intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_PDOP) ?: "n/a",
        hdopVdop = intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_HDOP_VDOP) ?: "n/a",
        horizontalAccuracy = intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_HORIZONTAL_ACCURACY) ?: "n/a",
        verticalAccuracy = intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_VERTICAL_ACCURACY) ?: "n/a",
        differentialAge = intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_DIFFERENTIAL_AGE) ?: "n/a",
        baseline = intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_BASELINE) ?: "n/a",
        pppStatus = intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_PPP_STATUS) ?: "n/a",
    )
    val ntrip = NtripCardState(
        url = intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_NTRIP_URL) ?: "n/a",
        status = intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_NTRIP) ?: "n/a",
        transferred = intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_NTRIP_TRANSFERRED) ?: "0 B",
        stationId = intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_NTRIP_STATION_ID) ?: "n/a",
        baseLatLon = intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_NTRIP_BASE_LAT_LON) ?: "n/a",
        rates = intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_NTRIP_RATES) ?: "n/a",
    )
    val files = FilesCardState(
        sessionLocation = intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_SESSION_PATH) ?: "n/a",
        receiverRxBytes = formatBytes(intent.getLongExtra(RecordingForegroundService.EXTRA_STATE_RX_BYTES, 0)),
        txToReceiverBytes = formatBytes(intent.getLongExtra(RecordingForegroundService.EXTRA_STATE_TX_BYTES, 0)),
        ntripBytes = formatBytes(intent.getLongExtra(RecordingForegroundService.EXTRA_STATE_CORRECTION_BYTES, 0)),
        nmeaBytes = formatBytes(intent.getLongExtra(RecordingForegroundService.EXTRA_STATE_NMEA_BYTES, 0)),
        zipShareEnabled = !running,
    )
    return if (running) DashboardState.running(status, position, fix, ntrip, files) else DashboardState.planned(
        workflow = status.workflow,
        mountpoint = status.mountpoint,
        receiver = status.receiver,
        storage = status.storage,
    )
}
```

- [ ] **Step 4: Compile**

Run:

```bash
ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK ANDROID_SDK_ROOT=/storage/3830-3863/Termux/AndroidSDK sh gradlew :app:compileDebugKotlin
```

Expected: pass.

- [ ] **Step 5: Commit**

```bash
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector add app/src/main/kotlin/org/rtkcollector/app/recording/RecordingServiceState.kt app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector commit -m "Broadcast dashboard recording telemetry"
```

---

### Task 10: Controlled UM980 Baud Transition Model

**Files:**
- Create: `app/src/main/kotlin/org/rtkcollector/app/recording/Um980BaudTransition.kt`
- Create: `app/src/test/kotlin/org/rtkcollector/app/recording/Um980BaudTransitionTest.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt`

- [ ] **Step 1: Write transition ordering tests**

Create `Um980BaudTransitionTest.kt`:

```kotlin
package org.rtkcollector.app.recording

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Um980BaudTransitionTest {
    @Test
    fun `different profile and serial baud uses device first host second ordering`() {
        val plan = Um980BaudTransitionPlan.build(
            profileBaud = 230400,
            serialBaud = 921600,
            initCommands = listOf("CONFIG COM1 921600"),
            modeCommands = listOf("BESTNAVB COM1 0.05"),
        )

        assertEquals(
            listOf(
                Um980BaudStep.OpenHostAtProfileBaud(230400),
                Um980BaudStep.SendCommands(listOf("CONFIG COM1 921600")),
                Um980BaudStep.PauseAfterDeviceBaudCommand,
                Um980BaudStep.ReconfigureHostBaud(921600),
                Um980BaudStep.DrainTransitionalRx,
                Um980BaudStep.SendCommands(listOf("BESTNAVB COM1 0.05")),
            ),
            plan.steps,
        )
    }

    @Test
    fun `same baud sends init and mode without host reconfigure`() {
        val plan = Um980BaudTransitionPlan.build(
            profileBaud = 230400,
            serialBaud = 230400,
            initCommands = listOf("GPGGA COM1 1"),
            modeCommands = listOf("BESTNAVB COM1 1"),
        )

        assertTrue(plan.steps.none { it is Um980BaudStep.ReconfigureHostBaud })
    }
}
```

- [ ] **Step 2: Run tests and verify failure**

Run:

```bash
ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK ANDROID_SDK_ROOT=/storage/3830-3863/Termux/AndroidSDK sh gradlew :app:testDebugUnitTest --tests 'org.rtkcollector.app.recording.Um980BaudTransitionTest'
```

Expected: fail because transition model does not exist.

- [ ] **Step 3: Add transition model**

Create `Um980BaudTransition.kt`:

```kotlin
package org.rtkcollector.app.recording

data class Um980BaudTransitionPlan(
    val steps: List<Um980BaudStep>,
) {
    companion object {
        fun build(
            profileBaud: Int,
            serialBaud: Int,
            initCommands: List<String>,
            modeCommands: List<String>,
        ): Um980BaudTransitionPlan {
            require(profileBaud > 0) { "Profile baud must be positive." }
            require(serialBaud > 0) { "Serial baud must be positive." }
            val steps = mutableListOf<Um980BaudStep>()
            steps += Um980BaudStep.OpenHostAtProfileBaud(profileBaud)
            if (initCommands.isNotEmpty()) {
                steps += Um980BaudStep.SendCommands(initCommands)
            }
            if (profileBaud != serialBaud) {
                steps += Um980BaudStep.PauseAfterDeviceBaudCommand
                steps += Um980BaudStep.ReconfigureHostBaud(serialBaud)
                steps += Um980BaudStep.DrainTransitionalRx
            }
            if (modeCommands.isNotEmpty()) {
                steps += Um980BaudStep.SendCommands(modeCommands)
            }
            return Um980BaudTransitionPlan(steps)
        }
    }
}

sealed class Um980BaudStep {
    data class OpenHostAtProfileBaud(val baud: Int) : Um980BaudStep()
    data class SendCommands(val commands: List<String>) : Um980BaudStep()
    data object PauseAfterDeviceBaudCommand : Um980BaudStep()
    data class ReconfigureHostBaud(val baud: Int) : Um980BaudStep()
    data object DrainTransitionalRx : Um980BaudStep()
}
```

- [ ] **Step 4: Wire service to use the plan**

In `RecordingForegroundService.startRecording`, replace ad-hoc command sequencing with:

```kotlin
val baudPlan = Um980BaudTransitionPlan.build(
    profileBaud = profileBaud,
    serialBaud = serialBaud,
    initCommands = initCommands,
    modeCommands = modeCommands,
)
```

Then execute steps in order:

```kotlin
baudPlan.steps.forEach { step ->
    when (step) {
        is Um980BaudStep.OpenHostAtProfileBaud -> Unit
        is Um980BaudStep.SendCommands -> sendCommands(captureRuntime, step.commands)
        Um980BaudStep.PauseAfterDeviceBaudCommand -> Thread.sleep(500)
        is Um980BaudStep.ReconfigureHostBaud -> captureRuntime.reconfigureBaud(step.baud)
        Um980BaudStep.DrainTransitionalRx -> drainAfterProfile(captureRuntime)
    }
}
```

If `CaptureRuntime.reconfigureBaud` does not exist, add it as a no-op interface method first and implement it in the USB transport task. Do not fake a baud switch silently for real USB.

- [ ] **Step 5: Run tests and compile**

Run:

```bash
ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK ANDROID_SDK_ROOT=/storage/3830-3863/Termux/AndroidSDK sh gradlew :app:testDebugUnitTest --tests 'org.rtkcollector.app.recording.Um980BaudTransitionTest'
ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK ANDROID_SDK_ROOT=/storage/3830-3863/Termux/AndroidSDK sh gradlew :app:compileDebugKotlin
```

Expected: both pass.

- [ ] **Step 6: Commit**

```bash
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector add app/src/main/kotlin/org/rtkcollector/app/recording app/src/test/kotlin/org/rtkcollector/app/recording/Um980BaudTransitionTest.kt
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector commit -m "Model UM980 baud transition ordering"
```

---

### Task 11: Session Browser And Share Model

**Files:**
- Create: `app/src/main/kotlin/org/rtkcollector/app/ui/sessions/SessionBrowserModels.kt`
- Create: `app/src/main/kotlin/org/rtkcollector/app/ui/sessions/SessionsScreen.kt`
- Create: `app/src/main/kotlin/org/rtkcollector/app/share/SessionShareModels.kt`
- Create: `app/src/test/kotlin/org/rtkcollector/app/share/SessionShareModelsTest.kt`

- [ ] **Step 1: Write share eligibility tests**

Create `SessionShareModelsTest.kt`:

```kotlin
package org.rtkcollector.app.share

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SessionShareModelsTest {
    @Test
    fun `zip sharing is disabled while recording unless partial snapshot is explicit`() {
        assertFalse(SessionShareState(isRecording = true, allowPartialSnapshot = false).zipEnabled)
        assertTrue(SessionShareState(isRecording = true, allowPartialSnapshot = true).zipEnabled)
        assertTrue(SessionShareState(isRecording = false, allowPartialSnapshot = false).zipEnabled)
    }
}
```

- [ ] **Step 2: Run test and verify failure**

Run:

```bash
ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK ANDROID_SDK_ROOT=/storage/3830-3863/Termux/AndroidSDK sh gradlew :app:testDebugUnitTest --tests 'org.rtkcollector.app.share.SessionShareModelsTest'
```

Expected: fail because `SessionShareState` does not exist.

- [ ] **Step 3: Add share model**

Create `SessionShareModels.kt`:

```kotlin
package org.rtkcollector.app.share

data class SessionShareState(
    val isRecording: Boolean,
    val allowPartialSnapshot: Boolean,
) {
    val zipEnabled: Boolean
        get() = !isRecording || allowPartialSnapshot
}
```

- [ ] **Step 4: Add session browser models**

Create `SessionBrowserModels.kt`:

```kotlin
package org.rtkcollector.app.ui.sessions

data class SessionListItem(
    val sessionId: String,
    val title: String,
    val subtitle: String,
    val isActive: Boolean,
)

data class SessionFileItem(
    val name: String,
    val location: String,
    val sizeText: String,
    val shareable: Boolean,
)

data class SessionDetailState(
    val sessionId: String,
    val location: String,
    val files: List<SessionFileItem>,
    val canShareZip: Boolean,
)
```

- [ ] **Step 5: Add sessions screen**

Create `SessionsScreen.kt`:

```kotlin
package org.rtkcollector.app.ui.sessions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SessionsScreen(
    sessions: List<SessionListItem>,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Sessions", style = MaterialTheme.typography.headlineSmall)
        sessions.forEach { session ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(session.title, style = MaterialTheme.typography.titleMedium)
                    Text(session.subtitle, style = MaterialTheme.typography.bodyMedium)
                    if (session.isActive) Text("Active recording", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Back")
        }
    }
}
```

- [ ] **Step 6: Run tests and compile**

Run:

```bash
ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK ANDROID_SDK_ROOT=/storage/3830-3863/Termux/AndroidSDK sh gradlew :app:testDebugUnitTest --tests 'org.rtkcollector.app.share.SessionShareModelsTest'
ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK ANDROID_SDK_ROOT=/storage/3830-3863/Termux/AndroidSDK sh gradlew :app:compileDebugKotlin
```

Expected: both pass.

- [ ] **Step 7: Commit**

```bash
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector add app/src/main/kotlin/org/rtkcollector/app/ui/sessions app/src/main/kotlin/org/rtkcollector/app/share app/src/test/kotlin/org/rtkcollector/app/share/SessionShareModelsTest.kt
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector commit -m "Model session browser sharing"
```

---

### Task 12: Documentation, Validation And Final Review

**Files:**
- Modify: `README.md`
- Modify: `docs/user-workflows.md`
- Modify: `docs/ntrip-and-corrections.md`
- Modify: `AGENTS.md`

- [ ] **Step 1: Update user docs**

In `docs/user-workflows.md`, update `Experimental V1 Android UI` to say:

```markdown
The Android UI uses a Compose dashboard. The Home screen shows the effective
session configuration and live telemetry in compact cards: Position, Fix, NTRIP
and Files. Detailed profile editing is reached from Menu.
```

Add:

```markdown
The Files card shows the active session location and recorded files. After
recording stops, the user can copy the session location, copy file locations,
share selected files through Android send-to apps, or create and share a ZIP.
```

- [ ] **Step 2: Update NTRIP docs**

In `docs/ntrip-and-corrections.md`, add:

```markdown
Fetching caster mountpoints must update the cached list only. The current
mountpoint text changes only when the user types or explicitly selects an item
from the fetched list.
```

- [ ] **Step 3: Update agent guidance**

In `AGENTS.md`, add:

```markdown
- Compose UI work must keep Menu and Start/Stop outside scrollable content.
- UM980 live parsers must be byte-level for mixed NMEA/ASCII/binary streams;
  do not feed line parsers with arbitrary binary bytes.
```

- [ ] **Step 4: Run focused tests**

Run:

```bash
ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK ANDROID_SDK_ROOT=/storage/3830-3863/Termux/AndroidSDK sh gradlew :app:testDebugUnitTest
ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK ANDROID_SDK_ROOT=/storage/3830-3863/Termux/AndroidSDK sh gradlew :receiver:unicore-n4:test
ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK ANDROID_SDK_ROOT=/storage/3830-3863/Termux/AndroidSDK sh gradlew :app:compileDebugKotlin
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector diff --check
```

Expected: all pass locally except Android resource packaging tasks that require full host SDK tooling.

- [ ] **Step 5: Try full debug assembly on capable host**

On Windows/Android Studio or CI:

```bash
./gradlew clean assembleDebug test
```

Expected: pass. If Termux runs this and fails at Android 36 `aapt2` resource linking, record it as an environment limitation and keep the Windows/CI command as the release validation.

- [ ] **Step 6: Manual UI validation**

Run on emulator/device and capture or inspect:

- 360 x 640 portrait: Menu and Start/Stop visible;
- 390 x 844 portrait: status strip and first dashboard content visible;
- 800 x 1280 portrait tablet: two-column cards allowed;
- 1280 x 800 landscape tablet: two or three columns;
- during recording: Stop visible while dashboard scrolls;
- settings page: changed NTRIP shows apply-now semantics;
- mountpoint fetch: typed mountpoint remains unchanged until explicit selection.

- [ ] **Step 7: Final commit**

```bash
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector add README.md docs/user-workflows.md docs/ntrip-and-corrections.md AGENTS.md
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector commit -m "Document Compose dashboard operation"
```

- [ ] **Step 8: Review and push**

Use:

```bash
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector status --short --branch
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector log --oneline -12
```

Then run `$review-and-commit and push` if the user has approved final integration.

---

## Self-Review

Spec coverage:

- Compose/Material UI direction: Tasks 1, 3, 6.
- Persistent top/bottom controls and responsive dashboard cards: Tasks 2, 3.
- Profiles and workflow defaults: Task 4.
- NTRIP mountpoint overwrite prevention: Task 5.
- Dashboard telemetry and service state: Tasks 2, 7, 9.
- UM980 binary/mixed-stream parsing: Tasks 7, 8.
- Controlled baud sequencing: Task 10.
- Session browser and file/share/ZIP: Task 11.
- Documentation and final validation: Task 12.

Known planned limitations in this plan:

- Full Android `assembleDebug` may need Windows/Android Studio or CI because local Termux Android 36 resource linking can fail.
- The first mixed-stream parser task classifies records; length/CRC-aware binary framing is introduced through `Um980BinaryParser` and should be tightened further during implementation review if frames are not fully bounded in the stream classifier.
- RTKLIB remains displayed as `Not configured`; no RTKLIB runtime engine is added.
